package com.microsoft.java.debug.core.adapter.variables;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.microsoft.java.debug.core.Configuration;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class StoppedState {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private final long version;
    private final ThreadReference thread;
    private List<StackFrame> stackFrames;
    private static AtomicInteger nextId = new AtomicInteger(1);

    public StoppedState(ThreadReference thread) {
        this.thread = thread;
        version = nextId.getAndIncrement();
        if (thread.isSuspended()) {
            try {
                stackFrames = Collections.unmodifiableList(thread.frames());
            } catch (IncompatibleThreadStateException e) {
                logger.warning("Thread state is changed during retrieving stack frames.");
            }
        }
    }

    public List<StackFrame> getStackFrames() {
        return stackFrames;
    }

    public StackFrame refreshStackFrames(int depth) {
        if (thread.isSuspended()) {
            try {
                stackFrames = Collections.unmodifiableList(thread.frames());
                return stackFrames.size() > depth ? stackFrames.get(depth) : null;
            } catch (IncompatibleThreadStateException e) {
                logger.warning("Thread state is changed during retrieving stack frames.");
            }
        }
        return null;
    }

    public long getVersion() {
        return version;
    }

    public ThreadReference getThread() {
        return thread;
    }
}
