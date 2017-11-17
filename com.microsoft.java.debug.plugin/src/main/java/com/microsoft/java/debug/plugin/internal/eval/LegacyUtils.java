//package com.microsoft.java.debug.plugin.internal.eval;
//
//import java.util.HashMap;
//import java.util.Map;
//
//import org.eclipse.debug.core.DebugException;
//import org.eclipse.jdt.core.IJavaProject;
//import org.eclipse.jdt.debug.core.IJavaVariable;
//import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
//import org.eclipse.jdt.internal.debug.core.model.JDIFieldVariable;
//import org.eclipse.jdt.internal.debug.core.model.JDILocalVariable;
//import org.eclipse.jdt.internal.debug.core.model.JDIReferenceType;
//import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
//import org.eclipse.jdt.internal.debug.core.model.JDIThread;
//import org.eclipse.jdt.internal.debug.core.model.JDIType;
//
//import com.sun.jdi.Field;
//import com.sun.jdi.LocalVariable;
//import com.sun.jdi.ObjectReference;
//import com.sun.jdi.ReferenceType;
//import com.sun.jdi.StackFrame;
//import com.sun.jdi.ThreadReference;
//import com.sun.jdi.VirtualMachine;
//
//public class LegacyUtils {
//    private static Map<ThreadReference, JDIThread> threadMap = new HashMap<>();
//
//    public static StackFrame refreshStackFrames(StackFrame sf) {
////        return EvaluateRequestHandler.refreshStackFrames(sf);
//        return null;
//    }
//
//    public static JDIReferenceType createJDIReferenceType(ReferenceType type) {
//        return (JDIReferenceType) JDIType.createType(null, type);
//    }
//
//    public static IJavaVariable createLocalVariable(StackFrame sf, LocalVariable local, IJavaProject project) {
//        return new JDILocalVariable(createJDIStackFrame(sf, project), local);
//    }
//
//    public static IJavaVariable createFieldVariable(Field field, ObjectReference or) {
//        return new JDIFieldVariable(null, field, or, null);
//    }
//
//    public static JDIStackFrame createJDIStackFrame(StackFrame sf, IJavaProject project) {
//        try {
//            for (Object df : createJDIThread(sf.thread(), project).computeNewStackFrames()) {
//                JDIStackFrame sf2 = (JDIStackFrame) df;
//                if (sf2.getLineNumber() == sf.location().lineNumber() && sf2.getUnderlyingMethod().equals(sf.location().method())) {
//                    return sf2;
//                }
//            }
//            return new JDIStackFrame(createJDIThread(sf.thread(), project), sf, 0);
//        } catch (DebugException e) {
//            e.printStackTrace();
//            return new JDIStackFrame(createJDIThread(sf.thread(), project), sf, 0);
//        }
//
//    }
//
//    public static JDIThread createJDIThread(ThreadReference thread, IJavaProject project) {
//        if (threadMap.containsKey(thread)) {
//            return threadMap.get(thread);
//        }
//        JDIThread newThread = new JDIThread(createJDIDebugTarget(thread.virtualMachine(), project), thread);
//        threadMap.put(thread, newThread);
//        return newThread;
//    }
//
//    public static JDIDebugTarget createJDIDebugTarget(VirtualMachine vm, IJavaProject project) {
//        return new JDIDebugTarget(createLaunch(project), vm, "", false, false, null, false) {
//            @Override
//            protected synchronized void initialize() {
//
//            }
//        };
//    }
//
//
//}
