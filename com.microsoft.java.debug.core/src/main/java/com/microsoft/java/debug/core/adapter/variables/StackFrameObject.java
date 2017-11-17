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


    public StackFrameObject(StackFrame sf, int depth) {
        proxy = sf;
        location = sf.location();
        thread = sf.thread();
        vm = sf.virtualMachine();
        this.depth = depth;
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
        try {
            return proxy.getArgumentValues();
        } catch (InvalidStackFrameException ex) {
            return null;
        }
    }

    @Override
    public Value getValue(LocalVariable arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<LocalVariable, Value> getValues(List<? extends LocalVariable> arg0) {
        return null;
    }

    @Override
    public void setValue(LocalVariable arg0, Value arg1) throws InvalidTypeException, ClassNotLoadedException {

    }

    @Override
    public ObjectReference thisObject() {
        return null;
    }


    @Override
    public LocalVariable visibleVariableByName(String arg0) throws AbsentInformationException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<LocalVariable> visibleVariables() throws AbsentInformationException {
        // TODO Auto-generated method stub
        return null;
    }

//    private T retryWithValidStackFrame<T>(Action<T> act) {
//
//    }
}
