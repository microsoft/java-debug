package com.microsoft.java.debug.core.adapter.variables;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.reflect.MethodUtils;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotLoadedException;
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
    private final int depth;
    private final int hash;
    private StackFrame proxy;
    private final StoppedState stopState;

    public StackFrameProxy(StoppedState state, StackFrame stackFrame, int depth) {
        stopState = state;
        proxy = stackFrame;
        this.depth = depth;
        hash = Long.hashCode(state.getVersion()) + stackFrame.thread().hashCode() + depth;
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
        return stopState == sf.stopState && depth == sf.depth;

    }

    public StoppedState getStoppedState() {
        return stopState;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public VirtualMachine virtualMachine() {
        return proxy.virtualMachine();
    }

    @Override
    public Location location() {
        return proxy.location();
    }

    @Override
    public ThreadReference thread() {
        return proxy.thread();
    }

    private Object invokeProxy(String methodName, final Object[] args, final Class<?>[] parameterTypes) {
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
                if (stopState != null) {
                    proxy = stopState.refreshStackFrames(depth);
                    if (proxy == null) {
                        throw ex;
                    }
                    return MethodUtils.invokeMethod(proxy, methodName, args, parameterTypes);
                }
                throw ex;
            }
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public List<Value> getArgumentValues() {
        return (List<Value>)invokeProxy("getArgumentValues", null, null);
    }

    @Override
    public Value getValue(LocalVariable arg0) {
        return (Value)invokeProxy("getValue", new Object[] {arg0}, new Class[] {LocalVariable.class});
    }

    @Override
    public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> arg0) {
        return (Map<LocalVariable, Value>)invokeProxy("getValues", new Object[] {arg0}, new Class[] {List.class});
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

}
