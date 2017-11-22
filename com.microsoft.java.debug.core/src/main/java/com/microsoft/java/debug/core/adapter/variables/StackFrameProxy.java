/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.variables;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.reflect.MethodUtils;

import com.microsoft.java.debug.core.Configuration;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidStackFrameException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

@SuppressWarnings("unchecked")
public class StackFrameProxy implements StackFrame {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private final int depth;
    private final int hash;
    private final Object timestamp;
    private final ThreadReference thread;
    private final Map<Object, StackFrame[]> cache;


    /**
     * Create a wrapper of JDI stackframe to handle the situation of refresh stackframe when encountering InvalidStackFrameException
     *
     * @param timestamp the timestamp object.
     * @param depth the index of this stackframe inside all frames inside one stopped thread
     * @param cache a map with timestamp object as the key and all stack frames as the value.
     */
    public StackFrameProxy(Object timestamp, ThreadReference thread, int depth, Map<Object, StackFrame[]> cache) {
        if (timestamp == null) {
            throw new NullPointerException("'timestamp' should not be null for StackFrameProxy");
        }

        if (depth < 0) {
            throw new IllegalArgumentException("'depth' should not be zero or an positive integer.");
        }
        this.thread = thread;
        this.timestamp = timestamp;
        this.depth = depth;
        this.cache = cache;
        hash = Long.hashCode(timestamp.hashCode()) + depth;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        StackFrameProxy sf = (StackFrameProxy) obj;
        return timestamp == sf.timestamp && depth == sf.depth;

    }

    public Object getTimestamp() {
        return timestamp;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public VirtualMachine virtualMachine() {
        return thread.virtualMachine();
    }

    @Override
    public Location location() {
        return getProxy().location();
    }

    @Override
    public ThreadReference thread() {
        return thread;
    }

    private Object invokeProxy(String methodName, final Object[] args, final Class<?>[] parameterTypes) {
        StackFrame proxy = getProxy();
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            try {
                return MethodUtils.invokeMethod(proxy, methodName, args, parameterTypes);
            } catch (InvocationTargetException ex) {
                if (!(ex.getTargetException() instanceof InvalidStackFrameException)) {
                    throw ex;
                }
                if (timestamp != null) {
                    synchronized (cache) {
                        StackFrame[] frames = cache.compute(timestamp, (k, v) -> {
                                try {
                                    return thread.frames().toArray(new StackFrame[0]);
                                } catch (IncompatibleThreadStateException e) {
                                    return new StackFrame[0];
                                }
                            }
                        );
                        proxy = frames.length > depth ? frames[depth] : null;
                    }
                    if (proxy == null) {
                        throw ex;
                    }
                    return MethodUtils.invokeMethod(proxy, methodName, args, parameterTypes);
                }
                throw ex;
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException  e) {
            logger.severe(String.format("StackFrameProxy cannot proxy on the method: %s, due to %s", methodName, e.toString()));
        }
        return null;
    }


    @Override
    public List<Value> getArgumentValues() {
        return (List<Value>) invokeProxy("getArgumentValues", null, null);
    }

    @Override
    public Value getValue(LocalVariable arg0) {
        return (Value) invokeProxy("getValue", new Object[] {arg0}, new Class[] {LocalVariable.class});
    }

    @Override
    public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> arg0) {
        return (Map<LocalVariable, Value>) invokeProxy("getValues", new Object[] {arg0}, new Class[] {List.class});
    }

    @Override
    public void setValue(LocalVariable arg0, Value arg1) throws InvalidTypeException, ClassNotLoadedException {
        invokeProxy("setValue", new Object[] {arg0, arg1}, new Class[] {LocalVariable.class, Value.class});
    }

    @Override
    public ObjectReference thisObject() {
        return (ObjectReference) invokeProxy("thisObject", null, null);
    }

    @Override
    public LocalVariable visibleVariableByName(String arg0) throws AbsentInformationException {
        return (LocalVariable) invokeProxy("visibleVariableByName", new Object[] {arg0}, new Class[] {String.class});
    }

    @Override
    public List<LocalVariable> visibleVariables() throws AbsentInformationException {
        return (List<LocalVariable>) invokeProxy("visibleVariables", null, null);
    }

    private StackFrame getProxy() {
        synchronized (cache) {
            StackFrame[] frames = cache.get(timestamp);
            return frames == null || frames.length < depth ? null : frames[depth];
        }
    }

}
