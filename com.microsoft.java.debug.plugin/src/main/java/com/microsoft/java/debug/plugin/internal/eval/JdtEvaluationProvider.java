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

package com.microsoft.java.debug.plugin.internal.eval;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
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
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import org.eclipse.jdt.internal.debug.eval.ast.engine.ASTEvaluationEngine;
import org.eclipse.jdt.internal.launching.JavaSourceLookupDirector;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.plugin.internal.JdtUtils;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

public class JdtEvaluationProvider implements IEvaluationProvider {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private IJavaProject project;
    private ILaunch launch;
    private JDIDebugTarget debugTarget;
    private Map<ThreadReference, JDIThread> threadMap = new HashMap<>();

    private HashMap<String, Object> options = new HashMap<>();

    private IDebugAdapterContext context;

    public JdtEvaluationProvider() {
    }

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> props) {
        if (props == null) {
            throw new IllegalArgumentException("argument is null");
        }
        options.putAll(props);
        this.context = context;
    }
    private long timeDuration1 = 0;
    private long timeDuration2 = 0;

    @Override
    public CompletableFuture<Value> evaluate(String expression, ThreadReference thread, int depth) {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        String projectName = (String) options.get(Constants.PROJECTNAME);
        if (debugTarget == null) {
            if (project == null) {
                if (StringUtils.isBlank(projectName)) {
                    logger.severe("Cannot evaluate when project is not specified.");
                    completableFuture.completeExceptionally(new IllegalStateException("Please specify projectName in launch.json."));
                    return completableFuture;
                }
                project = JdtUtils.getJavaProject(projectName);
            }

            if (project == null) {
                completableFuture.completeExceptionally(new IllegalStateException(String.format("Project %s cannot be found.", projectName)));
                return completableFuture;
            }
            if (launch == null) {
                launch = createILaunchMock(project);
            }
        }

        if (debugTarget == null) {
            debugTarget = new JDIDebugTarget(launch, thread.virtualMachine(), "", false, false, null, false) {
                @Override
                protected synchronized void initialize() {
                    // use empty initialize intentionally to avoid to register jdi event listener
                }
            };
        }

        JDIThread jdiThread = getMockJDIThread(thread);
        JDIStackFrame stackframe = createStackFrame(jdiThread, depth);
        if (stackframe == null) {
            logger.severe("Cannot evaluate because the stackframe is not available.");
            completableFuture.completeExceptionally(
                    new IllegalStateException("Cannot evaluate because the stackframe is not available."));
            return completableFuture;
        }
        try  {
            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            long startTime = System.nanoTime();

            ICompiledExpression ie = engine.getCompiledExpression(expression, stackframe);
            long endTime = System.nanoTime();
            timeDuration1+= (endTime - startTime) / 1000000;

            engine.evaluateExpression(ie, stackframe, evaluateResult -> {

                timeDuration2 += (System.nanoTime() - endTime) / 1000000;
                if (evaluateResult == null || evaluateResult.hasErrors()) {
                    Exception ex = evaluateResult.getException() != null ? evaluateResult.getException()
                            : new RuntimeException(StringUtils.join(evaluateResult.getErrorMessages()));
                    completableFuture.completeExceptionally(ex);
                    return;
                }
                try {
                    // we need to read fValue from the result Value instance implements by JDT
                    Value value = (Value) FieldUtils.readField(evaluateResult.getValue(), "fValue", true);
                    completableFuture.complete(value);
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    completableFuture.completeExceptionally(ex);
                }
            }, 0, false);

        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
        }
        return completableFuture;
    }

    private JDIStackFrame createStackFrame(JDIThread thread, int depth) {
        try {
            return new JDIStackFrame(thread, thread.getUnderlyingThread().frame(0), 0);
        } catch (Exception e) {
            return null;
        }

    }

    private JDIThread getMockJDIThread(ThreadReference thread) {
        synchronized (threadMap) {
            return threadMap.computeIfAbsent(thread, threadKey -> new JDIThread(debugTarget, thread) {
                @Override
                protected synchronized void invokeComplete(int restoreTimeout) {
                    super.invokeComplete(restoreTimeout);
                    context.getStackFrameManager().reloadStackFrames(thread);
                }
            });
        }

    }

    @Override
    public boolean isInEvaluation(ThreadReference thread) {
        return debugTarget != null && getMockJDIThread(thread).isPerformingEvaluation();
    }

    @Override
    public void clearState(ThreadReference thread) {
        if (debugTarget != null) {
            synchronized (threadMap) {
                JDIThread jdiThread = threadMap.get(thread);
                if (jdiThread != null) {
                    try {
                        jdiThread.terminateEvaluation();
                    } catch (DebugException e) {
                        logger.warning(String.format("Error stopping evalutoin on thread %d: %s", thread.uniqueID(),
                                e.toString()));
                    }
                    threadMap.remove(thread);
                }
            }
        }
    }

    private static ILaunch createILaunchMock(IJavaProject project) {
        return new ILaunch() {
            private AbstractSourceLookupDirector locator;

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
                if (locator != null) {
                    return locator;
                }
                locator = new JavaSourceLookupDirector();

                try {
                    locator.setSourceContainers(
                            new ProjectSourceContainer(project.getProject(), true).getSourceContainers());
                } catch (CoreException e) {
                    logger.severe(String.format("Cannot initialize JavaSourceLookupDirector: %s", e.toString()));
                }
                locator.initializeParticipants();
                return locator;
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
