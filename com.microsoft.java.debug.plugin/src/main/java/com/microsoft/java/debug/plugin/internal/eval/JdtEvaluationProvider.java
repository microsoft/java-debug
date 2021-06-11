/*******************************************************************************
 * Copyright (c) 2017-2019 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIObjectValue;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import org.eclipse.jdt.internal.debug.core.model.JDIValue;
import org.eclipse.jdt.internal.debug.eval.ast.engine.ASTEvaluationEngine;
import org.eclipse.jdt.internal.launching.JavaSourceLookupDirector;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.IEvaluatableBreakpoint;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.plugin.internal.JdtSourceLookUpProvider;
import com.microsoft.java.debug.plugin.internal.JdtUtils;
import com.sun.jdi.ObjectReference;
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
    public CompletableFuture<Value> evaluateForBreakpoint(IEvaluatableBreakpoint breakpoint, ThreadReference thread) {
        if (breakpoint == null) {
            throw new IllegalArgumentException("The breakpoint is null.");
        }

        if (!breakpoint.containsEvaluatableExpression()) {
            throw new IllegalArgumentException("The breakpoint doesn't contain the evaluatable expression.");
        }

        if (StringUtils.isNotBlank(breakpoint.getLogMessage())) {
            return evaluate(logMessageToExpression(breakpoint.getLogMessage()), thread, 0, breakpoint);
        } else {
            return evaluate(breakpoint.getCondition(), thread, 0, breakpoint);
        }
    }

    @Override
    public CompletableFuture<Value> evaluate(String expression, ThreadReference thread, int depth) {
        return evaluate(expression, thread, depth, null);
    }

    @Override
    public CompletableFuture<Value> evaluate(String expression, ObjectReference thisContext, ThreadReference thread) {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        try  {
            ensureDebugTarget(thisContext.virtualMachine(), thisContext.type().name());
            JDIThread jdiThread = getMockJDIThread(thread);
            JDIObjectValue jdiObject = new JDIObjectValue(debugTarget, thisContext);
            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            ICompiledExpression compiledExpression = engine.getCompiledExpression(expression, jdiObject);
            internalEvaluate(engine, compiledExpression, jdiObject, jdiThread, completableFuture);
            return completableFuture;
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
            return completableFuture;
        }
    }

    private CompletableFuture<Value> evaluate(String expression, ThreadReference thread, int depth, IEvaluatableBreakpoint breakpoint) {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        try  {
            StackFrame sf = thread.frame(depth);
            String typeName = sf.location().method().declaringType().name();
            ensureDebugTarget(thread.virtualMachine(), typeName);
            JDIThread jdiThread = getMockJDIThread(thread);
            JDIStackFrame stackframe = createStackFrame(jdiThread, depth);
            if (stackframe == null) {
                throw new IllegalStateException("Cannot evaluate because the stackframe is not available.");
            }

            ICompiledExpression compiledExpression = null;
            ASTEvaluationEngine engine = new ASTEvaluationEngine(project, debugTarget);
            boolean newExpression = false;
            if (breakpoint != null) {
                long threadId = thread.uniqueID();
                compiledExpression = (ICompiledExpression) breakpoint.getCompiledExpression(threadId);
                if (compiledExpression == null) {
                    newExpression = true;
                    compiledExpression = engine.getCompiledExpression(expression, stackframe);
                    breakpoint.setCompiledExpression(threadId, compiledExpression);
                }
            } else {
                compiledExpression = engine.getCompiledExpression(expression, stackframe);
            }

            if (compiledExpression.hasErrors()) {
                if (!newExpression && breakpoint != null) {
                    if (StringUtils.isNotBlank(breakpoint.getLogMessage())) {
                        // for logpoint with compilation errors, don't send errors if it is already reported
                        Value emptyValue = thread.virtualMachine().mirrorOf("");
                        completableFuture.complete(emptyValue);
                    } else {
                        // for conditional bp, report true to let breakpoint hit
                        Value trueValue = thread.virtualMachine().mirrorOf(true);
                        completableFuture.complete(trueValue);
                    }
                    return completableFuture;
                }
                completableFuture.completeExceptionally(AdapterUtils.createUserErrorDebugException(
                        String.format("Cannot evaluate because of compilation error(s): %s.",
                                StringUtils.join(compiledExpression.getErrorMessages(), "\n")),
                        ErrorCode.EVALUATION_COMPILE_ERROR));
                return completableFuture;
            }
            internalEvaluate(engine, compiledExpression, stackframe, completableFuture);
            return completableFuture;
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
            return completableFuture;
        }
    }

    @Override
    public CompletableFuture<Value> invokeMethod(ObjectReference thisContext, String methodName, String methodSignature,
            Value[] args, ThreadReference thread, boolean invokeSuper) {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        try  {
            ensureDebugTarget(thisContext.virtualMachine(), thisContext.type().name());
            JDIThread jdiThread = getMockJDIThread(thread);
            JDIObjectValue jdiObject = new JDIObjectValue(debugTarget, thisContext);
            List<IJavaValue> arguments = null;
            if (args == null) {
                arguments = Collections.EMPTY_LIST;
            } else {
                arguments = new ArrayList<>(args.length);
                for (Value arg : args) {
                    arguments.add(new JDIValue(debugTarget, arg));
                }
            }
            IJavaValue javaValue = jdiObject.sendMessage(methodName, methodSignature, arguments.toArray(new IJavaValue[0]), jdiThread, invokeSuper);
            // we need to read fValue from the result Value instance implements by JDT
            Value value = (Value) FieldUtils.readField(javaValue, "fValue", true);
            completableFuture.complete(value);
            return completableFuture;
        } catch (Exception ex) {
            completableFuture.completeExceptionally(ex);
            return completableFuture;
        }
    }

    private String logMessageToExpression(String logMessage) {
        final String LOGMESSAGE_VARIABLE_REGEXP = "\\{(.*?)\\}";
        String format = logMessage.replaceAll(LOGMESSAGE_VARIABLE_REGEXP, "%s");

        Pattern pattern = Pattern.compile(LOGMESSAGE_VARIABLE_REGEXP);
        Matcher matcher = pattern.matcher(logMessage);
        List<String> arguments = new ArrayList<>();
        while (matcher.find()) {
            arguments.add("(" + matcher.group(1) + ")");
        }

        if (arguments.size() > 0) {
            return "System.out.println(String.format(\"" + format + "\"," + String.join(",", arguments) + "))";
        } else {
            return "System.out.println(\"" + format + "\")";
        }
    }

    /**
     * Prepare a list of java project candidates in workspace which contains the main class.
     *
     * @param mainclass the main class specified by launch.json for finding project candidates
     */
    private void initializeProjectCandidates(String mainclass) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        projectCandidates = Arrays.stream(root.getProjects()).map(JdtUtils::getJavaProject).filter(p -> {
            try {
                return p != null && p.hasBuildState();
            } catch (Exception e) {
                // ignore
            }
            return false;
        }).collect(Collectors.toList());

        if (StringUtils.isNotBlank(mainclass)) {
            filterProjectCandidatesByClass(mainclass);
        }
    }

    private void filterProjectCandidatesByClass(String className) {
        projectCandidates = visitedClassNames.contains(className) ? projectCandidates
                 : projectCandidates.stream().filter(p -> {
                     try {
                         return p.findType(className) != null;
                     } catch (Exception e) {
                         // ignore
                     }
                     return false;
                 }).collect(Collectors.toList());
        visitedClassNames.add(className);
    }

    private IJavaProject findJavaProjectByType(String typeName) {
        if (projectCandidates == null) {
            // initial candidate projects by main class (projects contains this main class)
            initializeProjectCandidates((String) options.get(Constants.MAIN_CLASS));
        }

        if (projectCandidates.size() == 0) {
            logger.severe("No project is available for evaluation.");
            throw new IllegalStateException("Cannot evaluate, please specify projectName in launch.json.");
        }

        try {
            // narrow down candidate projects by current class
            filterProjectCandidatesByClass(typeName);
        } catch (Exception ex) {
            logger.severe("Cannot evaluate when the project is not specified, due to exception: " + ex.getMessage());
            throw new IllegalStateException("Cannot evaluate, please specify projectName in launch.json.");
        }

        if (projectCandidates.size() == 1) {
            return projectCandidates.get(0);
        }

        if (projectCandidates.size() == 0) {
            logger.severe("No project is available for evaluation.");
            throw new IllegalStateException("Cannot evaluate, please specify projectName in launch.json.");
        } else {
            // narrow down projects
            logger.severe("Multiple projects are valid for evaluation.");
            throw new IllegalStateException("Cannot evaluate, please specify projectName in launch.json.");
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

    private void internalEvaluate(ASTEvaluationEngine engine, ICompiledExpression compiledExpression, IJavaObject object,
            IJavaThread thread, CompletableFuture<Value> completableFuture) {
        try  {
            engine.evaluateExpression(compiledExpression, object, thread, evaluateResult -> {
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
        if (debugTarget == null) {
            return false;
        }

        JDIThread jdiThread = getMockJDIThread(thread);
        return jdiThread != null && (jdiThread.isPerformingEvaluation() || jdiThread.isInvokingMethod());
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
                        logger.warning(String.format("Error stopping evaluation on thread %d: %s", thread.uniqueID(),
                                e.toString()));
                    }
                    threadMap.remove(thread);
                }
            }
        }
    }

    private void ensureDebugTarget(VirtualMachine vm, String typeName) {
        if (debugTarget == null) {
            if (project == null) {
                String projectName = (String) options.get(Constants.PROJECT_NAME);
                if (StringUtils.isBlank(projectName)) {
                    project = findJavaProjectByType(typeName);
                } else {
                    IJavaProject javaProject = JdtUtils.getJavaProject(projectName);
                    if (javaProject == null) {
                        throw new IllegalStateException(String.format("Project %s cannot be found.", projectName));
                    }
                    project = javaProject;
                }
            }

            if (launch == null) {
                ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
                launch = createILaunchMock(project, ((JdtSourceLookUpProvider) sourceProvider).getSourceContainers());
            }

            debugTarget = new JDIDebugTarget(launch, vm, "", false, false, null, false) {
                @Override
                protected synchronized void initialize() {
                    // use empty initialize intentionally to avoid to register jdi event listener
                }
            };
        }
    }

    private static ILaunch createILaunchMock(IJavaProject project, ISourceContainer[] containers) {
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
                    locator.setSourceContainers(containers);
                } catch (Exception e) {
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
