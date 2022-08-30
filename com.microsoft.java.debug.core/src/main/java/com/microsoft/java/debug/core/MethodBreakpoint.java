/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Gayan Perera - initial API and implementation
*******************************************************************************/
package com.microsoft.java.debug.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodEntryRequest;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class MethodBreakpoint implements IMethodBreakpoint, IEvaluatableBreakpoint {

    private VirtualMachine vm;
    private IEventHub eventHub;
    private String className;
    private String functionName;
    private String condition;
    private int hitCount;
    private boolean async = false;

    private HashMap<Object, Object> propertyMap = new HashMap<>();
    private Object compiledConditionalExpression = null;
    private Map<Long, Object> compiledExpressions = new ConcurrentHashMap<>();

    private List<EventRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private List<Disposable> subscriptions = new ArrayList<>();

    public MethodBreakpoint(VirtualMachine vm, IEventHub eventHub, String className, String functionName,
            String condition, int hitCount) {
        Objects.requireNonNull(vm);
        Objects.requireNonNull(eventHub);
        Objects.requireNonNull(className);
        Objects.requireNonNull(functionName);
        this.vm = vm;
        this.eventHub = eventHub;
        this.className = className;
        this.functionName = functionName;
        this.condition = condition;
        this.hitCount = hitCount;
    }

    @Override
    public List<EventRequest> requests() {
        return requests;
    }

    @Override
    public List<Disposable> subscriptions() {
        return subscriptions;
    }

    @Override
    public void close() throws Exception {
        try {
            vm.eventRequestManager().deleteEventRequests(requests());
        } catch (VMDisconnectedException ex) {
            // ignore since removing breakpoints is meaningless when JVM is terminated.
        }
        subscriptions().forEach(Disposable::dispose);
        requests.clear();
        subscriptions.clear();
    }

    @Override
    public boolean containsEvaluatableExpression() {
        return containsConditionalExpression() || containsLogpointExpression();
    }

    @Override
    public boolean containsConditionalExpression() {
        return StringUtils.isNotBlank(getCondition());
    }

    @Override
    public boolean containsLogpointExpression() {
        return false;
    }

    @Override
    public String getCondition() {
        return condition;
    }

    @Override
    public void setCondition(String condition) {
        this.condition = condition;
        setCompiledConditionalExpression(null);
        compiledExpressions.clear();
    }

    @Override
    public String getLogMessage() {
        return null;
    }

    @Override
    public void setLogMessage(String logMessage) {
        // for future implementation
    }

    @Override
    public void setCompiledConditionalExpression(Object compiledExpression) {
        this.compiledConditionalExpression = compiledExpression;
    }

    @Override
    public Object getCompiledConditionalExpression() {
        return compiledConditionalExpression;
    }

    @Override
    public void setCompiledLogpointExpression(Object compiledExpression) {
        // for future implementation
    }

    @Override
    public Object getCompiledLogpointExpression() {
        return null;
    }

    @Override
    public void setCompiledExpression(long threadId, Object compiledExpression) {
        compiledExpressions.put(threadId, compiledExpression);
    }

    @Override
    public Object getCompiledExpression(long threadId) {
        return compiledExpressions.get(threadId);
    }

    @Override
    public int getHitCount() {
        return hitCount;
    }

    @Override
    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
        Observable.fromIterable(this.requests())
                .filter(request -> request instanceof MethodEntryRequest)
                .subscribe(request -> {
                    request.addCountFilter(hitCount);
                    request.enable();
                });
    }

    @Override
    public boolean async() {
        return this.async;
    }

    @Override
    public void setAsync(boolean async) {
        this.async = async;
    }

    @Override
    public CompletableFuture<IMethodBreakpoint> install() {
        Disposable subscription = eventHub.events()
                .filter(debugEvent -> debugEvent.event instanceof ThreadDeathEvent)
                .subscribe(debugEvent -> {
                    ThreadReference deathThread = ((ThreadDeathEvent) debugEvent.event).thread();
                    compiledExpressions.remove(deathThread.uniqueID());
                });

        subscriptions.add(subscription);

        // It's possible that different class loaders create new class with the same
        // name.
        // Here to listen to future class prepare events to handle such case.
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(className);
        classPrepareRequest.enable();
        requests.add(classPrepareRequest);

        CompletableFuture<IMethodBreakpoint> future = new CompletableFuture<>();
        subscription = eventHub.events()
                .filter(debugEvent -> debugEvent.event instanceof ClassPrepareEvent
                        && (classPrepareRequest.equals(debugEvent.event.request())))
                .subscribe(debugEvent -> {
                    ClassPrepareEvent event = (ClassPrepareEvent) debugEvent.event;
                    Optional<MethodEntryRequest> createdRequest = AsyncJdwpUtils.await(
                        createMethodEntryRequest(event.referenceType())
                    );
                    if (createdRequest.isPresent()) {
                        MethodEntryRequest methodEntryRequest = createdRequest.get();
                        requests.add(methodEntryRequest);
                        if (!future.isDone()) {
                            this.putProperty("verified", true);
                            future.complete(this);
                        }
                    }
                });
        subscriptions.add(subscription);

        Runnable createRequestsFromLoadedClasses = () -> {
            List<ReferenceType> types = vm.classesByName(className);
            for (ReferenceType type : types) {
                createMethodEntryRequest(type).whenComplete((createdRequest, ex) -> {
                    if (ex != null) {
                        return;
                    }

                    if (createdRequest.isPresent()) {
                        MethodEntryRequest methodEntryRequest = createdRequest.get();
                        requests.add(methodEntryRequest);
                        if (!future.isDone()) {
                            this.putProperty("verified", true);
                            future.complete(this);
                        }
                    }
                });
            }
        };

        if (async()) {
            AsyncJdwpUtils.runAsync(createRequestsFromLoadedClasses);
        } else {
            createRequestsFromLoadedClasses.run();
        }

        return future;
    }

    private CompletableFuture<Optional<MethodEntryRequest>> createMethodEntryRequest(ReferenceType type) {
        if (async()) {
            return CompletableFuture.supplyAsync(() -> createMethodEntryRequest0(type));
        } else {
            return CompletableFuture.completedFuture(createMethodEntryRequest0(type));
        }
    }

    private Optional<MethodEntryRequest> createMethodEntryRequest0(ReferenceType type) {
        return type.methodsByName(functionName).stream().findFirst().map(method -> {
            MethodEntryRequest request = vm.eventRequestManager().createMethodEntryRequest();

            request.addClassFilter(type);
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            if (hitCount > 0) {
                request.addCountFilter(hitCount);
            }
            request.enable();
            return request;
        });
    }

    @Override
    public Object getProperty(Object key) {
        return propertyMap.get(key);
    }

    @Override
    public void putProperty(Object key, Object value) {
        propertyMap.put(key, value);
    }

    @Override
    public String methodName() {
        return functionName;
    }

    @Override
    public String className() {
        return className;
    }

}
