/*******************************************************************************
 * Copyright (c) 2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.core.adapter.variables;

import java.util.List;
import java.util.Map;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

public class StringReferenceProxy implements StringReference {
    private StringReference delegateStringRef;
    private String value = null;

    public StringReferenceProxy(StringReference sr, String value) {
        this.delegateStringRef = sr;
        this.value = value;
    }

    public String value() {
        if (value != null) {
            return value;
        }

        return delegateStringRef.value();
    }

    public ReferenceType referenceType() {
        return delegateStringRef.referenceType();
    }

    public VirtualMachine virtualMachine() {
        return delegateStringRef.virtualMachine();
    }

    public String toString() {
        return delegateStringRef.toString();
    }

    public Value getValue(Field sig) {
        return delegateStringRef.getValue(sig);
    }

    public Map<Field, Value> getValues(List<? extends Field> fields) {
        return delegateStringRef.getValues(fields);
    }

    public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {
        delegateStringRef.setValue(field, value);
    }

    public Value invokeMethod(ThreadReference thread, Method method, List<? extends Value> arguments, int options)
            throws InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException,
            InvocationException {
        return delegateStringRef.invokeMethod(thread, method, arguments, options);
    }

    public Type type() {
        return delegateStringRef.type();
    }

    public void disableCollection() {
        delegateStringRef.disableCollection();
    }

    public void enableCollection() {
        delegateStringRef.enableCollection();
    }

    public boolean isCollected() {
        return delegateStringRef.isCollected();
    }

    public long uniqueID() {
        return delegateStringRef.uniqueID();
    }

    public List<ThreadReference> waitingThreads() throws IncompatibleThreadStateException {
        return delegateStringRef.waitingThreads();
    }

    public ThreadReference owningThread() throws IncompatibleThreadStateException {
        return delegateStringRef.owningThread();
    }

    public int entryCount() throws IncompatibleThreadStateException {
        return delegateStringRef.entryCount();
    }

    public List<ObjectReference> referringObjects(long maxReferrers) {
        return delegateStringRef.referringObjects(maxReferrers);
    }

    public boolean equals(Object obj) {
        return delegateStringRef.equals(obj);
    }

    public int hashCode() {
        return delegateStringRef.hashCode();
    }
}
