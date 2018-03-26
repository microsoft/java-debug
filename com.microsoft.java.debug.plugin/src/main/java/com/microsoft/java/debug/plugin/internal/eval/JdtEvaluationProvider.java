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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.containers.ProjectSourceContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import org.eclipse.jdt.internal.debug.eval.ast.engine.ASTEvaluationEngine;
import org.eclipse.jdt.internal.launching.JavaSourceLookupDirector;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.IBreakpoint;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.plugin.internal.JdtUtils;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

public class JdtEvaluationProvider implements IEvaluationProvider {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private IJavaProject project;
    private ILaunch launch;
    private JDIDebugTarget debugTarget;
    private Map<ThreadReference, JDIThread> threadMap = new HashMap<>();
    private HashMap<String, Object> options = new HashMap<>();
    private IDebugAdapterContext context;

    private List<IJavaProject> projectCandidates;

    private Set<String> visitedClassNames = new HashSet<>();

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

    @Override
    public CompletableFuture<Value> evaluateForBreakpoint(IBreakpoint breakpoint, ThreadReference thread, Map<IBreakpoint, Object> breakpointExpressionMap) {
        if (breakpoint == null) {
            throw new IllegalArgumentException("breakpoint is null.");
        }

        if (StringUtils.isBlank(breakpoint.getCondition())) {
            throw new IllegalArgumentException("breakpoint is not a conditional breakpoint.");
        }

        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        try  {
            ensureDebugTarget(thread.virtualMachine(), thread, 0);
            JDIThread jdiThread = getMockJDIThread(thread);
            JDIStackFrame stackframe = (JDIStackFrame) jdiThread.getTopStackFrame();

            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            ICompiledExpression ie = (ICompiledExpression) breakpointExpressionMap
                    .computeIfAbsent(breakpoint, bp -> engine.getCompiledExpression(bp.getCondition(), stackframe));

            internalEvaluate(engine, ie, stackframe, completableFuture);
            return completableFuture;
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
            return completableFuture;
        }

    }

    @Override
    public CompletableFuture<Value> evaluate(String expression, ThreadReference thread, int depth) {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        try  {
            ensureDebugTarget(thread.virtualMachine(), thread, depth);
            JDIThread jdiThread = getMockJDIThread(thread);
            JDIStackFrame stackframe = createStackFrame(jdiThread, depth);
            if (stackframe == null) {
                logger.severe("Cannot evaluate because the stackframe is not available.");
                throw new IllegalStateException("Cannot evaluate because the stackframe is not available.");
            }
            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            ICompiledExpression ie = engine.getCompiledExpression(expression, stackframe);

            internalEvaluate(engine, ie, stackframe, completableFuture);
            return completableFuture;
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
            return completableFuture;
        }
    }

    /**
     * Prepare a list of java project candidates in workspace which contains the main class.
     *
     * @param mainclass the main class specified by launch.json for finding project candidates
     */
    private void initializeProjectCandidates(String mainclass) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        List<IJavaProject> projects = Arrays.stream(root.getProjects()).map(JdtUtils::getJavaProject).filter(p -> {
            try {
                return p != null && p.hasBuildState();
            } catch (Exception e) {
                // ignore
            }
            return false;
        }).collect(Collectors.toList());


        if (projects.size() > 1 && StringUtils.isNotBlank(mainclass)) {
            projects = Arrays.stream(root.getProjects()).map(JdtUtils::getJavaProject).filter(p -> {
                try {
                    return p.findType(mainclass) != null;
                } catch (JavaModelException e) {
                 // ignore
                }
                return false;
            }).collect(Collectors.toList());
            visitedClassNames.add(mainclass);
        }

        if (projects.size() == 1) {
            project =  projects.get(0);
        }

        projectCandidates = projects;
    }

    private void findJavaProjectByStackFrame(ThreadReference thread, int depth) {
        if (projectCandidates == null) {
            // initial candidate projects by main class (projects contains this main class)
            initializeProjectCandidates((String) options.get(Constants.MAIN_CLASS));
            if (project != null) {
                return;
            }
        }

        if (projectCandidates.size() == 0) {
            logger.severe("No project is available for evaluation.");
            throw new IllegalStateException("No project is available for evaluation.");
        }

        try {
            StackFrame sf = thread.frame(depth);
            String typeName = sf.location().method().declaringType().name();
            // narrow down candidate projects by current class
            List<IJavaProject> validProjects = visitedClassNames.contains(typeName) ? projectCandidates
                    : projectCandidates.stream().filter(p -> {
                        try {
                            return p.findType(typeName) != null;
                        } catch (Exception e) {
                            // ignore
                        }
                        return false;
                    }).collect(Collectors.toList());
            visitedClassNames.add(typeName);
            if (validProjects.size() == 1) {
                project = validProjects.get(0);
                return;
            } else if (validProjects.size() == 0) {
                logger.severe("No project is available for evaluation.");
                throw new IllegalStateException("No project is available for evaluation, .");
            } else {
                // narrow down projects
                projectCandidates = validProjects;
                logger.severe("Multiple projects are valid for evaluation.");
                throw new IllegalStateException("Multiple projects are found, please specify projectName in launch.json.");
            }

        } catch (Exception ex) {
            logger.severe("Cannot evaluate when the project is not specified.");
            throw new IllegalStateException("Please specify projectName in launch.json.");
        }
    }


    private JDIStackFrame createStackFrame(JDIThread thread, int depth) {
        try {
            IStackFrame[] jdiStackFrames = thread.getStackFrames();
            return jdiStackFrames.length > depth ? (JDIStackFrame) jdiStackFrames[depth] : null;
        } catch (DebugException e) {
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

    private void internalEvaluate(ASTEvaluationEngine engine, ICompiledExpression compiledExpression,
            IJavaStackFrame stackframe, CompletableFuture<Value> completableFuture) {
        try  {
            engine.evaluateExpression(compiledExpression, stackframe, evaluateResult -> {
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

    private void ensureDebugTarget(VirtualMachine vm, ThreadReference thread, int depth) {
        if (debugTarget == null) {
            if (project == null) {
                String projectName = (String) options.get(Constants.PROJECT_NAME);
                if (StringUtils.isBlank(projectName)) {
                    findJavaProjectByStackFrame(thread, depth);
                } else {
                    IJavaProject javaProject = JdtUtils.getJavaProject(projectName);
                    if (javaProject == null) {
                        throw new IllegalStateException(String.format("Project %s cannot be found.", projectName));
                    }
                    project = javaProject;
                }
            }

            if (launch == null) {
                launch = createILaunchMock(project);
            }

            debugTarget = new JDIDebugTarget(launch, vm, "", false, false, null, false) {
                @Override
                protected synchronized void initialize() {
                    // use empty initialize intentionally to avoid to register jdi event listener
                }
            };
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
