package com.microsoft.java.debug.core.adapter.variables;

import java.util.ArrayList;
import java.util.List;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class ThreadProxy {
    private ThreadReference thread;
    private Object lock = new Object();
    private List<StackFrameObject> stackFrames;

    public ThreadProxy(ThreadReference thread) {
        this.thread = thread;
    }

    public void computeStackFrame() {
        synchronized (lock) {
            if (stackFrames == null) {
                stackFrames = new ArrayList<>();
            }
            if (thread.isSuspended()) {
                try {
                    List<StackFrame> rawFrames = thread.frames();
                    for (int i = 0; i < rawFrames.size(); i++) {
                        if (stackFrames.size() > i) {
                            stackFrames.get(i).updateStackFrame(rawFrames.get(i));
                        } else {
                            stackFrames.add(new StackFrameObject(this, rawFrames.get(i), i));
                        }
                    }

                    for (int i = rawFrames.size(); i < stackFrames.size(); i++) {
                        stackFrames.get(i).setDetached();
                    }
                    ((ArrayList<?>)stackFrames).subList(rawFrames.size(), stackFrames.size()).clear();

                } catch (IncompatibleThreadStateException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int getTotalFrames() {
        return stackFrames.size();
    }

    public List<StackFrame> getFrames(int fromIndex, int toIndex) {
        return new ArrayList<>(stackFrames.subList(fromIndex, toIndex));
    }
}
