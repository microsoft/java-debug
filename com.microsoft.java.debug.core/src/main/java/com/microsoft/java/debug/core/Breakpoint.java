/*******************************************************************************
* Copyright (c) 2017-2022 Microsoft Corporation and others.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class Breakpoint implements IBreakpoint {
    private VirtualMachine vm = null;
    private IEventHub eventHub = null;
    private JavaBreakpointLocation sourceLocation = null;
    private int hitCount = 0;
    private String condition = null;
    private String logMessage = null;
    private HashMap<Object, Object> propertyMap = new HashMap<>();
    private final boolean suspendAllThreads;

    private boolean async = false;

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, boolean suspendAllThreads) {
        this(vm, eventHub, className, lineNumber, 0, null, suspendAllThreads);
    }

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount, boolean suspendAllThreads) {
        this(vm, eventHub, className, lineNumber, hitCount, null, suspendAllThreads);
    }

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount,
            String condition, boolean suspendAllThreads) {
        this(vm, eventHub, className, lineNumber, hitCount, condition, null, suspendAllThreads);
    }

    Breakpoint(VirtualMachine vm, IEventHub eventHub, String className, int lineNumber, int hitCount,
            String condition, String logMessage, boolean suspendAllThreads) {
        this.vm = vm;
        this.eventHub = eventHub;
        this.suspendAllThreads = suspendAllThreads;
        String contextClass = className;
        String methodName = null;
        String methodSignature = null;
        if (className != null && className.contains("#")) {
            contextClass = className.substring(0, className.indexOf("#"));
            String[] methodInfo = className.substring(className.indexOf("#") + 1).split("#");
            methodName = methodInfo[0];
            methodSignature = methodInfo[1];
        }

        this.sourceLocation = new JavaBreakpointLocation(lineNumber, -1);
        this.sourceLocation.setClassName(contextClass);
        this.sourceLocation.setMethodName(methodName);
        this.sourceLocation.setMethodSignature(methodSignature);
        this.hitCount = hitCount;
        this.condition = condition;
        this.logMessage = logMessage;
    }

    Breakpoint(VirtualMachine vm, IEventHub eventHub, JavaBreakpointLocation sourceLocation, int hitCount,
            String condition, String logMessage, boolean suspendAllThreads) {
        this.vm = vm;
        this.eventHub = eventHub;
        this.sourceLocation = sourceLocation;
        this.hitCount = hitCount;
        this.condition = condition;
        this.logMessage = logMessage;
        this.suspendAllThreads = suspendAllThreads;
    }

    // IDebugResource
    private List<EventRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private List<Disposable> subscriptions = new ArrayList<>();

    @Override
    public List<EventRequest> requests() {
        return requests;
    }

    @Override
    public List<Disposable> subscriptions() {
        return subscriptions;
    }

    // AutoCloseable
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

    // IBreakpoint
    @Override
    public JavaBreakpointLocation sourceLocation() {
        return this.sourceLocation;
    }

    @Override
    public String className() {
        return this.sourceLocation.className();
    }

    @Override
    public int getLineNumber() {
        return this.sourceLocation.lineNumber();
    }

    @Override
    public int getColumnNumber() {
        return this.sourceLocation.columnNumber();
    }

    @Override
    public String getCondition() {
        return condition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceLocation);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Breakpoint)) {
            return false;
        }
        Breakpoint other = (Breakpoint) obj;
        return Objects.equals(sourceLocation, other.sourceLocation);
    }

    @Override
    public int getHitCount() {
        return hitCount;
    }

    @Override
    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;

        Observable.fromIterable(this.requests())
            .filter(request -> request instanceof BreakpointRequest)
            .subscribe(request -> {
                request.addCountFilter(hitCount);
                request.disable();
                request.enable();
            });
    }

    @Override
    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    @Override
    public String getLogMessage() {
        return this.logMessage;
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
    public void setSuspendPolicy(String policy) {
    }

    @Override
    public String getSuspendPolicy() {
        return suspendAllThreads ? "SUSPEND_ALL" : "SUSPEND_EVENT_THREAD";
    }

    protected boolean suspendAllThreads() {
        return suspendAllThreads;
    }

    @Override
    public CompletableFuture<IBreakpoint> install() {
        // It's possible that different class loaders create new class with the same name.
        // Here to listen to future class prepare events to handle such case.
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(className());
        classPrepareRequest.enable();
        requests.add(classPrepareRequest);

        // Local types also needs to be handled
        ClassPrepareRequest localClassPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        localClassPrepareRequest.addClassFilter(className() + "$*");
        localClassPrepareRequest.enable();
        requests.add(localClassPrepareRequest);

        CompletableFuture<IBreakpoint> future = new CompletableFuture<>();

        Disposable subscription = eventHub.events()
                .filter(debugEvent -> debugEvent.event instanceof ClassPrepareEvent
                        && (classPrepareRequest.equals(debugEvent.event.request())
                            || localClassPrepareRequest.equals(debugEvent.event.request())))
                .subscribe(debugEvent -> {
                    ClassPrepareEvent event = (ClassPrepareEvent) debugEvent.event;
                    List<BreakpointRequest> newRequests = AsyncJdwpUtils.await(
                        createBreakpointRequests(event.referenceType(), getLineNumber(), hitCount, false)
                    );
                    requests.addAll(newRequests);
                    if (!newRequests.isEmpty() && !future.isDone()) {
                        this.putProperty("verified", true);
                        future.complete(this);
                    }
                });
        subscriptions.add(subscription);

        Runnable resolveRequestsFromExistingClasses = () -> {
            List<ReferenceType> refTypes = vm.classesByName(className());
            createBreakpointRequests(refTypes, getLineNumber(), hitCount, true)
                .whenComplete((newRequests, ex) -> {
                    if (ex != null) {
                        return;
                    }

                    requests.addAll(newRequests);
                    if (!newRequests.isEmpty() && !future.isDone()) {
                        this.putProperty("verified", true);
                        future.complete(this);
                    }
                });
        };

        if (async()) {
            AsyncJdwpUtils.runAsync(resolveRequestsFromExistingClasses);
        } else {
            resolveRequestsFromExistingClasses.run();
        }

        return future;
    }

    private CompletableFuture<List<Location>> collectLocations(ReferenceType refType, int lineNumber) {
        List<CompletableFuture<List<Location>>> futures = new ArrayList<>();
        Iterator<Method> iter = refType.methods().iterator();
        while (iter.hasNext()) {
            Method method = iter.next();
            if (async()) {
                futures.add(AsyncJdwpUtils.supplyAsync(() -> findLocaitonsOfLine(method, lineNumber)));
            } else {
                futures.add(CompletableFuture.completedFuture(findLocaitonsOfLine(method, lineNumber)));
            }
        }

        return AsyncJdwpUtils.flatAll(futures);
    }

    private CompletableFuture<List<Location>> collectLocations(List<ReferenceType> refTypes, int lineNumber, boolean includeNestedTypes) {
        List<CompletableFuture<List<Location>>> futures = new ArrayList<>();
        refTypes.forEach(refType -> {
            futures.add(collectLocations(refType, lineNumber, includeNestedTypes));
        });

        return AsyncJdwpUtils.flatAll(futures);
    }

    private CompletableFuture<List<Location>> collectLocations(ReferenceType refType, int lineNumber, boolean includeNestedTypes) {
        return collectLocations(refType, lineNumber).thenCompose((newLocations) -> {
            if (!newLocations.isEmpty()) {
                return CompletableFuture.completedFuture(newLocations);
            } else if (includeNestedTypes) {
                // ReferenceType.nestedTypes() will invoke vm.allClasses() to list all loaded classes,
                // should avoid using nestedTypes for performance.
                for (ReferenceType nestedType : refType.nestedTypes()) {
                    CompletableFuture<List<Location>> nestedLocationsFuture = collectLocations(nestedType, lineNumber);
                    List<Location> nestedLocations = nestedLocationsFuture.join();
                    if (!nestedLocations.isEmpty()) {
                        return CompletableFuture.completedFuture(nestedLocations);
                    }
                }
            }

            return CompletableFuture.completedFuture(Collections.emptyList());
        });
    }

    private CompletableFuture<List<Location>> collectLocations(List<ReferenceType> refTypes, String methodName, String methodSiguature) {
        List<CompletableFuture<Location>> futures = new ArrayList<>();
        for (ReferenceType refType : refTypes) {
            if (async()) {
                futures.add(AsyncJdwpUtils.supplyAsync(() -> findMethodLocaiton(refType, methodName, methodSiguature)));
            } else {
                futures.add(CompletableFuture.completedFuture(findMethodLocaiton(refType, methodName, methodSiguature)));
            }
        }

        return AsyncJdwpUtils.all(futures);
    }

    private Location findMethodLocaiton(ReferenceType refType, String methodName, String methodSiguature) {
        List<Method> methods = refType.methods();
        Location location = null;
        for (Method method : methods) {
            if (!method.isAbstract() && !method.isNative()
                    && methodName.equals(method.name())
                    && (methodSiguature.equals(method.genericSignature()) || methodSiguature.equals(method.signature())
                            || toNoneGeneric(methodSiguature).equals(method.signature()))) {
                location = method.location();
                break;
            }
        }

        return location;
    }

    static String toNoneGeneric(String genericSig) {
        StringBuilder builder = new StringBuilder();
        boolean append = true;
        int depth = 0;
        char[] chars = genericSig.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '<') {
                depth++;
                append = (depth == 0);
            }
            if (append) {
                builder.append(c);
            }
            if (c == '>') {
                depth--;
                append = (depth == 0);
            }
        }
        return builder.toString();
    }

    private List<Location> findLocaitonsOfLine(Method method, int lineNumber) {
        try {
            return method.locationsOfLine(lineNumber);
        } catch (AbsentInformationException e) {
            // could be AbsentInformationException or ClassNotPreparedException
            // but both are expected so no need to further handle
        }

        return Collections.emptyList();
    }

    private CompletableFuture<List<BreakpointRequest>> createBreakpointRequests(ReferenceType refType, int lineNumber, int hitCount,
            boolean includeNestedTypes) {
        return createBreakpointRequests(Arrays.asList(refType), lineNumber, hitCount, includeNestedTypes);
    }

    private CompletableFuture<List<BreakpointRequest>> createBreakpointRequests(List<ReferenceType> refTypes, int lineNumber,
            int hitCount, boolean includeNestedTypes) {
        CompletableFuture<List<Location>> locationsFuture;
        if (this.sourceLocation.methodName() != null) {
            locationsFuture = collectLocations(refTypes, this.sourceLocation.methodName(), this.sourceLocation.methodSignature());
        } else {
            locationsFuture = collectLocations(refTypes, lineNumber, includeNestedTypes).thenApply((locations) -> {
                if (locations.isEmpty()) {
                    return locations;
                }

                /**
                 * For a line breakpoint, we default to breaking at the first location
                 * of the line. If you want to break at other locations on the same line,
                 * you can add an inline breakpoint based on the locations returned by
                 * the BreakpointLocation request.
                 */
                return Arrays.asList(locations.get(0));
            });
        }

        return locationsFuture.thenCompose((locations) -> {
            // find out the existing breakpoint locations
            List<Location> existingLocations = new ArrayList<>(requests.size());
            Observable.fromIterable(requests).filter(request -> request instanceof BreakpointRequest)
                    .map(request -> ((BreakpointRequest) request).location()).toList().subscribe(list -> {
                        existingLocations.addAll(list);
                    });

            // remove duplicated locations
            List<Location> newLocations = new ArrayList<>(locations.size());
            Observable.fromIterable(locations).filter(location -> !existingLocations.contains(location)).toList().subscribe(list -> {
                newLocations.addAll(list);
            });

            List<BreakpointRequest> newRequests = new ArrayList<>(newLocations.size());

            newLocations.forEach(location -> {
                BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
                if ("SUSPEND_ALL".equals(getSuspendPolicy())) {
                    request.setSuspendPolicy(BreakpointRequest.SUSPEND_ALL);
                } else {
                    request.setSuspendPolicy(BreakpointRequest.SUSPEND_EVENT_THREAD);
                }
                if (hitCount > 0) {
                    request.addCountFilter(hitCount);
                }
                request.putProperty(IBreakpoint.REQUEST_TYPE, computeRequestType());
                newRequests.add(request);
            });

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (BreakpointRequest request : newRequests) {
                if (async()) {
                    futures.add(AsyncJdwpUtils.runAsync(() -> {
                        try {
                            request.enable();
                        } catch (VMDisconnectedException ex) {
                            // enable breakpoint operation may be executing while JVM is terminating, thus the VMDisconnectedException may be
                            // possible, in case of VMDisconnectedException, this method will return an empty array which turns out a valid
                            // response in vscode, causing no error log in trace.
                        }
                    }));
                } else {
                    try {
                        request.enable();
                    } catch (VMDisconnectedException ex) {
                        // enable breakpoint operation may be executing while JVM is terminating, thus the VMDisconnectedException may be
                        // possible, in case of VMDisconnectedException, this method will return an empty array which turns out a valid
                        // response in vscode, causing no error log in trace.
                    }
                }
            }

            return AsyncJdwpUtils.all(futures).thenApply((res) -> newRequests);
        });
    }

    private Object computeRequestType() {
        if (this.sourceLocation.methodName() == null) {
            return IBreakpoint.REQUEST_TYPE_LINE;
        }

        if (this.sourceLocation.methodName().startsWith("lambda$")) {
            return IBreakpoint.REQUEST_TYPE_LAMBDA;
        } else {
            return IBreakpoint.REQUEST_TYPE_METHOD;
        }
    }

    @Override
    public void putProperty(Object key, Object value) {
        propertyMap.put(key, value);
    }

    @Override
    public Object getProperty(Object key) {
        return propertyMap.get(key);
    }
}
