package com.microsoft.java.debug.plugin.internal.eval;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.containers.ProjectSourceContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import org.eclipse.jdt.internal.debug.eval.ast.engine.ASTEvaluationEngine;

import com.microsoft.java.debug.core.adapter.IEvaluationListener;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;

public class JdtEvaluationProvider implements IEvaluationProvider {
    private IJavaProject project;
    private ILaunch launch;
    private JDIDebugTarget debugTarget;
    private Map<ThreadReference, JDIThread> threadMap = new HashMap<>();

    @Override
    public void eval(String projectName, String code, StackFrame sf, IEvaluationListener listener) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        if (project == null) {
            for (IProject proj : root.getProjects()) {
                try {
                    if (proj.isNatureEnabled("org.eclipse.jdt.core.javanature") && proj.getName().equals(projectName)) {
                        project = JavaCore.create(proj);
                        break;
                    }
                } catch (CoreException e) {
                    e.printStackTrace();
                }
            }
        }

        if (project == null) {
            throw new IllegalStateException("Project " + projectName + " cannot be found.");
        }

        if (launch == null) {
            launch = createLaunch(project);
        }

        if (debugTarget == null) {
            debugTarget = new JDIDebugTarget(launch, sf.virtualMachine(), "", false, false, null, false) {
                @Override
                protected synchronized void initialize() {

                }
            };
        }
        try {
            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            ICompiledExpression ie = engine.getCompiledExpression(code, createJDIStackFrame(sf));
            engine.evaluateExpression(ie, createJDIStackFrame(sf), evaluateResult -> {
                if (evaluateResult == null || evaluateResult.getValue() == null) {
                    listener.evaluationComplete("error");
                } else
                    listener.evaluationComplete(evaluateResult.getValue().toString());
            }, 0, false);
        } catch (CoreException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private IJavaStackFrame createJDIStackFrame(StackFrame sf) {
        return new JDIStackFrame(getJDIThread(sf.thread()), sf, 0);
    }

    private JDIThread getJDIThread(ThreadReference thread) {
        if (threadMap.containsKey(thread)) {
            return threadMap.get(thread);
        }
        JDIThread newThread = new JDIThread(debugTarget, thread);
        threadMap.put(thread, newThread);
        return newThread;
    }

    @Override
    public boolean isInEvaluation(ThreadReference thread) {
        return getJDIThread(thread).isPerformingEvaluation();
    }

    private static ILaunch createLaunch(IJavaProject project) {
        return new ILaunch() {

            @Override
            public boolean canTerminate() {

                return false;
            }

            @Override
            public boolean isTerminated() {

                return false;
            }

            @Override
            public void terminate() throws DebugException {

            }

            @Override
            public <T> T getAdapter(Class<T> arg0) {

                return null;
            }

            @Override
            public void addDebugTarget(IDebugTarget arg0) {

            }

            @Override
            public void addProcess(IProcess arg0) {

            }

            @Override
            public String getAttribute(String arg0) {

                return null;
            }

            @Override
            public Object[] getChildren() {

                return null;
            }

            @Override
            public IDebugTarget getDebugTarget() {

                return null;
            }

            @Override
            public IDebugTarget[] getDebugTargets() {

                return null;
            }

            @Override
            public ILaunchConfiguration getLaunchConfiguration() {

                return null;
            }

            @Override
            public String getLaunchMode() {

                return null;
            }

            @Override
            public IProcess[] getProcesses() {

                return null;
            }

            @Override
            public ISourceLocator getSourceLocator() {

                return new AbstractSourceLookupDirector() {

                    @Override
                    public void initializeParticipants() {

                        try {
                            this.setSourceContainers(new ProjectSourceContainer((IProject) project, true).getSourceContainers());
                        } catch (CoreException e) {
                            e.printStackTrace();
                        }
                    }

                };
            }

            @Override
            public boolean hasChildren() {

                return false;
            }

            @Override
            public void removeDebugTarget(IDebugTarget arg0) {

            }

            @Override
            public void removeProcess(IProcess arg0) {

            }

            @Override
            public void setAttribute(String arg0, String arg1) {

            }

            @Override
            public void setSourceLocator(ISourceLocator arg0) {

            }

        };
    }

}
