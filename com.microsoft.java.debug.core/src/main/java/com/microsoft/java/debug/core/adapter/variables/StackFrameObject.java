package com.microsoft.java.debug.core.adapter.variables;

import java.util.List;
import java.util.Map;

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

public class StackFrameObject implements StackFrame {
    public Location location;
    public int depth;
    public ThreadReference thread;
    private StackFrame proxy;
    private VirtualMachine vm;
    private ThreadProxy parent;


    public StackFrameObject(ThreadProxy parent, StackFrame sf, int depth) {
        proxy = sf;
        location = sf.location();
        thread = sf.thread();
        vm = sf.virtualMachine();
        this.depth = depth;
        this.parent = parent;
    }


    @Override
    public int hashCode() {
        return thread.hashCode() + depth;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj.getClass() != this.getClass()){
            return false;
        }
        if(this == obj){
            return true;
        }
        StackFrameObject sf = (StackFrameObject) obj;
        return sf.thread.uniqueID() == thread.uniqueID() && depth == sf.depth;

    }


    @Override
    public VirtualMachine virtualMachine() {
        return vm;
    }


    @Override
    public Location location() {
        return location;
    }

    @Override
    public ThreadReference thread() {
        return thread;
    }


    @Override
    public List<Value> getArgumentValues() {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.getArgumentValues();
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.getArgumentValues();
            }
            throw ex;
        }
    }

    @Override
    public Value getValue(LocalVariable arg0) {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.getValue(arg0);
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.getValue(arg0);
            }
            throw ex;
        }
    }

    @Override
    public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> arg0) {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.getValues(arg0);
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.getValues(arg0);
            }
            throw ex;
        }
    }

    @Override
    public void setValue(LocalVariable arg0, Value arg1) throws InvalidTypeException, ClassNotLoadedException {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            proxy.setValue(arg0, arg1);
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                proxy.setValue(arg0, arg1);
            }
            throw ex;
        }
    }

    @Override
    public ObjectReference thisObject() {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.thisObject();
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.thisObject();
            }
            throw ex;
        }
    }


    @Override
    public LocalVariable visibleVariableByName(String arg0) throws AbsentInformationException {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.visibleVariableByName(arg0);
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.visibleVariableByName(arg0);
            }
            throw ex;
        }
    }

    @Override
    public List<LocalVariable> visibleVariables() throws AbsentInformationException {
        if (proxy == null) {
            throw new InvalidStackFrameException();
        }
        try {
            return proxy.visibleVariables();
        } catch (InvalidStackFrameException ex) {
            if (parent != null) {
                parent.computeStackFrame();
                if (proxy == null) {
                    throw ex;
                }
                return proxy.visibleVariables();
            }
            throw ex;
        }
    }

    public void updateStackFrame(StackFrame stackFrame) {
        proxy = stackFrame;
        location = proxy.location();
    }

    public void setDetached() {
        proxy = null;
    }

    public boolean isDetached() {
        return proxy == null;
    }
}
