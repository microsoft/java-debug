/*******************************************************************************
* Copyright (c) 2019 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.WatchpointRequest;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class Watchpoint implements IWatchpoint, IEvaluatableBreakpoint {
    private final VirtualMachine vm;
    private final IEventHub eventHub;
    private final String className;
    private final String fieldName;
    private String accessType = null;
    private String condition = null;
    private int hitCount;
    private HashMap<Object, Object> propertyMap = new HashMap<>();
    private Object compiledConditionalExpression = null;
    private Map<Long, Object> compiledExpressions = new HashMap<>();

    // IDebugResource
    private List<EventRequest> requests = new ArrayList<>();
    private List<Disposable> subscriptions = new ArrayList<>();

    Watchpoint(VirtualMachine vm, IEventHub eventHub, String className, String fieldName) {
        this(vm, eventHub, className, fieldName, "write");
    }

    Watchpoint(VirtualMachine vm, IEventHub eventHub, String className, String fieldName, String accessType) {
        this(vm, eventHub, className, fieldName, accessType, null, 0);
    }

    Watchpoint(VirtualMachine vm, IEventHub eventHub, String className, String fieldName, String accessType, String condition, int hitCount) {
        Objects.requireNonNull(vm);
        Objects.requireNonNull(eventHub);
        Objects.requireNonNull(className);
        Objects.requireNonNull(fieldName);
        this.vm = vm;
        this.eventHub = eventHub;
        this.className = className;
        this.fieldName = fieldName;
        this.accessType = accessType;
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
        subscriptions().forEach(subscription -> {
            subscription.dispose();
        });
        requests.clear();
        subscriptions.clear();
    }

    @Override
    public String className() {
        return className;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public String accessType() {
        return accessType;
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
    public int getHitCount() {
        return hitCount;
    }

    @Override
    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;

        Observable.fromIterable(this.requests())
            .filter(request -> request instanceof WatchpointRequest)
            .subscribe(request -> {
                request.addCountFilter(hitCount);
                request.enable();
            });
    }

    @Override
    public void putProperty(Object key, Object value) {
        propertyMap.put(key, value);
    }

    @Override
    public Object getProperty(Object key) {
        return propertyMap.get(key);
    }

    @Override
    public CompletableFuture<IWatchpoint> install() {
        Disposable subscription = eventHub.events()
            .filter(debugEvent -> debugEvent.event instanceof ThreadDeathEvent)
            .subscribe(debugEvent -> {
                ThreadReference deathThread = ((ThreadDeathEvent) debugEvent.event).thread();
                compiledExpressions.remove(deathThread.uniqueID());
            });
        subscriptions.add(subscription);

        // It's possible that different class loaders create new class with the same name.
        // Here to listen to future class prepare events to handle such case.
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(className);
        classPrepareRequest.enable();
        requests.add(classPrepareRequest);

        CompletableFuture<IWatchpoint> future = new CompletableFuture<>();
        subscription = eventHub.events()
            .filter(debugEvent -> debugEvent.event instanceof ClassPrepareEvent && (classPrepareRequest.equals(debugEvent.event.request())))
            .subscribe(debugEvent -> {
                ClassPrepareEvent event = (ClassPrepareEvent) debugEvent.event;
                List<WatchpointRequest> watchpointRequests = createWatchpointRequests(event.referenceType());
                requests.addAll(watchpointRequests);
                if (!watchpointRequests.isEmpty() && !future.isDone()) {
                    this.putProperty("verified", true);
                    future.complete(this);
                }
            });
        subscriptions.add(subscription);

        List<EventRequest> watchpointRequests = new ArrayList<>();
        List<ReferenceType> types = vm.classesByName(className);
        for (ReferenceType type : types) {
            watchpointRequests.addAll(createWatchpointRequests(type));
        }

        requests.addAll(watchpointRequests);
        if (!watchpointRequests.isEmpty() && !future.isDone()) {
            this.putProperty("verified", true);
            future.complete(this);
        }

        return future;
    }

    private List<WatchpointRequest> createWatchpointRequests(ReferenceType type) {
        List<WatchpointRequest> watchpointRequests = new ArrayList<>();
        Field field = type.fieldByName(fieldName);
        if (field != null) {
            if ("read".equals(accessType)) {
                watchpointRequests.add(vm.eventRequestManager().createAccessWatchpointRequest(field));
            } else if ("readWrite".equals(accessType)) {
                watchpointRequests.add(vm.eventRequestManager().createAccessWatchpointRequest(field));
                watchpointRequests.add(vm.eventRequestManager().createModificationWatchpointRequest(field));
            } else {
                watchpointRequests.add(vm.eventRequestManager().createModificationWatchpointRequest(field));
            }
        }

        watchpointRequests.forEach(request -> {
            request.setSuspendPolicy(WatchpointRequest.SUSPEND_EVENT_THREAD);
            if (hitCount > 0) {
                request.addCountFilter(hitCount);
            }
            request.enable();
        });
        return watchpointRequests;
    }

    @Override
    public String getLogMessage() {
        return null;
    }

    @Override
    public void setLogMessage(String logMessage) {
        throw new UnsupportedOperationException("Log message feature is unsupported for watchpoint.");
    }

    @Override
    public boolean containsEvaluatableExpression() {
        return containsConditionalExpression();
    }

    @Override
    public boolean containsConditionalExpression() {
        return StringUtils.isNotBlank(getCondition());
    }

    @Override
    public boolean containsLogpointExpression() {
        return false;
    }

    public void setCompiledConditionalExpression(Object compiledExpression) {
        this.compiledConditionalExpression = compiledExpression;
    }

    public Object getCompiledConditionalExpression() {
        return compiledConditionalExpression;
    }

    @Override
    public void setCompiledLogpointExpression(Object compiledExpression) {
        // do nothing
    }

    @Override
    public Object getCompiledLogpointExpression() {
        return null;
    }

    @Override
    public Object getCompiledExpression(long threadId) {
        return compiledExpressions.get(threadId);
    }

    @Override
    public void setCompiledExpression(long threadId, Object compiledExpression) {
        compiledExpressions.put(threadId, compiledExpression);
    }
}
