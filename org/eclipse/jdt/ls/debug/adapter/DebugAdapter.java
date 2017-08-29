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

package org.eclipse.jdt.ls.debug.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.ls.debug.DebugEvent;
import org.eclipse.jdt.ls.debug.DebugException;
import org.eclipse.jdt.ls.debug.DebugUtility;
import org.eclipse.jdt.ls.debug.IBreakpoint;
import org.eclipse.jdt.ls.debug.IDebugSession;
import org.eclipse.jdt.ls.debug.adapter.Messages.Response;
import org.eclipse.jdt.ls.debug.adapter.Requests.Arguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.AttachArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.Command;
import org.eclipse.jdt.ls.debug.adapter.Requests.ConfigurationDoneArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.ContinueArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.DisconnectArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.EvaluateArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.LaunchArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.NextArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.PauseArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.ScopesArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetBreakpointArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetExceptionBreakpointsArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetFunctionBreakpointsArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.SetVariableArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.SourceArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.StackTraceArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.StepInArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.StepOutArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.ThreadsArguments;
import org.eclipse.jdt.ls.debug.adapter.Requests.VariablesArguments;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatEnum;
import org.eclipse.jdt.ls.debug.adapter.formatter.NumericFormatter;
import org.eclipse.jdt.ls.debug.adapter.formatter.SimpleTypeFormatter;
import org.eclipse.jdt.ls.debug.adapter.handler.InitializeRequestHandler;
import org.eclipse.jdt.ls.debug.adapter.variables.IVariableFormatter;
import org.eclipse.jdt.ls.debug.adapter.variables.JdiObjectProxy;
import org.eclipse.jdt.ls.debug.adapter.variables.StackFrameScope;
import org.eclipse.jdt.ls.debug.adapter.variables.ThreadObjectReference;
import org.eclipse.jdt.ls.debug.adapter.variables.Variable;
import org.eclipse.jdt.ls.debug.adapter.variables.VariableFormatterFactory;
import org.eclipse.jdt.ls.debug.adapter.variables.VariableUtils;
import org.eclipse.jdt.ls.debug.internal.Logger;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.TypeComponent;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.event.VMStartEvent;

import io.reactivex.disposables.Disposable;

public class DebugAdapter implements IDebugAdapter {
    private BiConsumer<Events.DebugEvent, Boolean> eventConsumer;

    private boolean debuggerLinesStartAt1 = true;
    private boolean debuggerPathsAreUri = true;
    private boolean clientLinesStartAt1 = true;
    private boolean clientPathsAreUri = false;

    private boolean isAttached = false;

    private String[] sourcePath;
    private IDebugSession debugSession;
    private BreakpointManager breakpointManager;
    private List<Disposable> eventSubscriptions;
    private IProviderContext providerContext;
    private VariableRequestHandler variableRequestHandler;
    private IdCollection<String> sourceCollection = new IdCollection<>();

    private IDebugAdapterContext debugContext = null;
    private Map<Command, List<IDebugRequestHandler>> requestHandlers = null;

    /**
     * Constructor.
     */
    public DebugAdapter(BiConsumer<Events.DebugEvent, Boolean> consumer, IProviderContext providerContext) {
        this.eventConsumer = consumer;
        this.breakpointManager = new BreakpointManager();
        this.eventSubscriptions = new ArrayList<>();
        this.providerContext = providerContext;
        this.variableRequestHandler = new VariableRequestHandler(VariableFormatterFactory.createVariableFormatter());
        this.debugContext = new DebugAdapterContext(this);
        this.requestHandlers = new HashMap<>();
        initialize();
    }

    @Override
    public Messages.Response dispatchRequest(Messages.Request request) {
        Messages.Response response = new Messages.Response();
        response.request_seq = request.seq;
        response.command = request.command;
        response.success = true;

        Command command = Command.parse(request.command);
        Arguments cmdArgs = JsonUtils.fromJson(request.arguments, command.getArgumentType());

        try {
            switch (command) {
                case LAUNCH:
                    launch((LaunchArguments) cmdArgs, response);
                    break;

                case ATTACH:
                    attach((AttachArguments) cmdArgs, response);
                    break;

                case DISCONNECT:
                    disconnect((DisconnectArguments) cmdArgs, response);
                    break;

                case CONFIGURATIONDONE:
                    configurationDone((ConfigurationDoneArguments) cmdArgs, response);
                    break;

                case NEXT:
                    next((NextArguments) cmdArgs, response);
                    break;

                case CONTINUE:
                    resume((ContinueArguments) cmdArgs, response);
                    break;

                case STEPIN:
                    stepIn((StepInArguments) cmdArgs, response);
                    break;

                case STEPOUT:
                    stepOut((StepOutArguments) cmdArgs, response);
                    break;

                case PAUSE:
                    pause((PauseArguments) cmdArgs, response);
                    break;

                case STACKTRACE:
                    stackTrace((StackTraceArguments) cmdArgs, response);
                    break;

                case SCOPES:
                    scopes((ScopesArguments) cmdArgs, response);
                    break;

                case VARIABLES:
                    Requests.VariablesArguments varArguments = (VariablesArguments) cmdArgs;
                    if (varArguments.variablesReference == -1) {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "VariablesRequest: property 'variablesReference' is missing, null, or empty");
                    } else {
                        variables(varArguments, response);
                    }
                    break;

                case SETVARIABLE:
                    Requests.SetVariableArguments setVarArguments = (SetVariableArguments) cmdArgs;
                    if (setVarArguments.value == null) {
                        // Just exit out of editing if we're given an empty expression.
                        response.body = new Responses.ResponseBody();
                    } else if (setVarArguments.variablesReference == -1) {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "SetVariablesRequest: property 'variablesReference' is missing, null, or empty");
                    } else if (setVarArguments.name == null) {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "SetVariablesRequest: property 'name' is missing, null, or empty");
                    } else {
                        setVariable(setVarArguments, response);
                    }
                    break;

                case SOURCE:
                    Requests.SourceArguments sourceArguments = (SourceArguments) cmdArgs;
                    if (sourceArguments.sourceReference == -1) {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "SourceRequest: property 'sourceReference' is missing, null, or empty");
                    } else {
                        source(sourceArguments, response);
                    }
                    break;

                case THREADS:
                    threads((ThreadsArguments) cmdArgs, response);
                    break;

                case SETBREAKPOINTS:
                    setBreakpoints((SetBreakpointArguments) cmdArgs, response);
                    break;

                case SETEXCEPTIONBREAKPOINTS:
                    setExceptionBreakpoints((SetExceptionBreakpointsArguments) cmdArgs, response);
                    break;

                case SETFUNCTIONBREAKPOINTS:
                    Requests.SetFunctionBreakpointsArguments setFuncBreakpointArguments = (SetFunctionBreakpointsArguments) cmdArgs;
                    if (setFuncBreakpointArguments.breakpoints != null) {
                        setFunctionBreakpoints(setFuncBreakpointArguments, response);
                    } else {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "SetFunctionBreakpointsRequest: property 'breakpoints' is missing, null, or empty");
                    }
                    break;

                case EVALUATE:
                    Requests.EvaluateArguments evaluateArguments = (EvaluateArguments) cmdArgs;
                    if (evaluateArguments.expression == null) {
                        AdapterUtils.setErrorResponse(response, ErrorCode.ARGUMENT_MISSING,
                                "EvaluateRequest: property 'expression' is missing, null, or empty");
                    } else {
                        evaluate(evaluateArguments, response);
                    }
                    break;

                default:
                    List<IDebugRequestHandler> handlers = requestHandlers.get(command);
                    if (handlers != null && !handlers.isEmpty()) {
                        for (IDebugRequestHandler handler : handlers) {
                            handler.handle(command, cmdArgs, response, this.debugContext);
                        }
                    } else {
                        AdapterUtils.setErrorResponse(response, ErrorCode.UNRECOGNIZED_REQUEST_FAILURE,
                                String.format("Unrecognized request: { _request: %s }", request.command));
                    }
            }
        } catch (Exception e) {
            Logger.logException("DebugSession dispatch exception", e);
            AdapterUtils.setErrorResponse(response, ErrorCode.UNKNOWN_FAILURE,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }

        return response;
    }

    /**
     * Send event to DA immediately.
     *
     * @see ProtocolServer#sendEvent(String, Object)
     */
    public void sendEvent(Events.DebugEvent event) {
        this.eventConsumer.accept(event, false);
    }

    /**
     * Send event to DA after the current dispatching request is resolved.
     *
     * @see ProtocolServer#sendEventLater(String, Object)
     */
    public void sendEventLater(Events.DebugEvent event) {
        this.eventConsumer.accept(event, true);
    }

    public <T extends IProvider> T getProvider(Class<T> clazz) {
        return providerContext.getProvider(clazz);
    }

    private void initialize() {
        // Register request handlers.
        registerHandler(new InitializeRequestHandler());
    }

    private void registerHandler(IDebugRequestHandler handler) {
        for (Command command : handler.getTargetCommands()) {
            List<IDebugRequestHandler> handlerList = requestHandlers.get(command);
            if (handlerList == null) {
                handlerList = new ArrayList<>();
                requestHandlers.put(command, handlerList);
            }
            handlerList.add(handler);
        }
    }

    /* ======================================================*/
    /* Invoke different dispatch logic for different request */
    /* ======================================================*/

    private void launch(Requests.LaunchArguments arguments, Response response) {
        try {
            this.isAttached = false;
            this.launchDebugSession(arguments);
        } catch (DebugException e) {
            // When launching failed, send a TerminatedEvent to tell DA the debugger would exit.
            this.sendEventLater(new Events.TerminatedEvent());
            AdapterUtils.setErrorResponse(response, ErrorCode.LAUNCH_FAILURE, e);
        }
    }

    private void attach(Requests.AttachArguments arguments, Response response) {
        try {
            this.isAttached = true;
            this.attachDebugSession(arguments);
        } catch (DebugException e) {
            // When attaching failed, send a TerminatedEvent to tell DA the debugger would exit.
            this.sendEventLater(new Events.TerminatedEvent());
            AdapterUtils.setErrorResponse(response, ErrorCode.ATTACH_FAILURE, e);
        }
    }

    /**
     * VS Code terminates a debug session with the disconnect request.
     */
    private void disconnect(Requests.DisconnectArguments arguments, Response response) {
        this.shutdownDebugSession(arguments.terminateDebuggee && !this.isAttached);
    }

    /**
     * VS Code sends a configurationDone request to indicate the end of configuration sequence.
     */
    private void configurationDone(Requests.ConfigurationDoneArguments arguments, Response response) {
        this.eventSubscriptions.add(this.debugSession.eventHub().events().subscribe(debugEvent -> {
            handleEvent(debugEvent);
        }));
        this.debugSession.start();
    }

    private void setFunctionBreakpoints(Requests.SetFunctionBreakpointsArguments arguments, Response response) {
        // TODO
    }

    private void setBreakpoints(Requests.SetBreakpointArguments arguments, Response response) {
        String clientPath = arguments.source.path;
        if (AdapterUtils.isWindows()) {
            // VSCode may send drive letters with inconsistent casing which will mess up the key
            // in the BreakpointManager. See https://github.com/Microsoft/vscode/issues/6268
            // Normalize the drive letter casing. Note that drive letters
            // are not localized so invariant is safe here.
            String drivePrefix = FilenameUtils.getPrefix(clientPath);
            if (drivePrefix != null && drivePrefix.length() >= 2
                    && Character.isLowerCase(drivePrefix.charAt(0)) && drivePrefix.charAt(1) == ':') {
                drivePrefix = drivePrefix.substring(0, 2); // d:\ is an illegal regex string, convert it to d:
                clientPath = clientPath.replaceFirst(drivePrefix, drivePrefix.toUpperCase());
            }
        }
        String sourcePath = clientPath;
        if (arguments.source.sourceReference != 0 && this.sourceCollection.get(arguments.source.sourceReference) != null) {
            sourcePath = this.sourceCollection.get(arguments.source.sourceReference);
        } else {
            sourcePath = this.convertClientPathToDebugger(clientPath);
        }

        // When breakpoint source path is null or an invalid file path, send an ErrorResponse back.
        if (sourcePath == null) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_BREAKPOINT_FAILURE,
                    String.format("Failed to setBreakpoint. Reason: '%s' is an invalid path.", arguments.source.path));
            return ;
        }
        try {
            List<Types.Breakpoint> res = new ArrayList<>();
            IBreakpoint[] toAdds = this.convertClientBreakpointsToDebugger(sourcePath, arguments.breakpoints);
            IBreakpoint[] added = this.breakpointManager.setBreakpoints(sourcePath, toAdds, arguments.sourceModified);
            for (int i = 0; i < arguments.breakpoints.length; i++) {
                // For newly added breakpoint, should install it to debuggee first.
                if (toAdds[i] == added[i] && added[i].className() != null) {
                    added[i].install().thenAccept(bp -> {
                        Events.BreakpointEvent bpEvent = new Events.BreakpointEvent("new", this.convertDebuggerBreakpointToClient(bp));
                        sendEventLater(bpEvent);
                    });
                } else if (toAdds[i].hitCount() != added[i].hitCount() && added[i].className() != null) {
                    // Update hitCount condition.
                    added[i].setHitCount(toAdds[i].hitCount());
                }
                res.add(this.convertDebuggerBreakpointToClient(added[i]));
            }
            response.body = new Responses.SetBreakpointsResponseBody(res);
        } catch (DebugException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_BREAKPOINT_FAILURE,
                    String.format("Failed to setBreakpoint. Reason: '%s'", e.getMessage()));
        }
    }

    private void setExceptionBreakpoints(Requests.SetExceptionBreakpointsArguments arguments, Response response) {
        String[] filters = arguments.filters;
        try {
            boolean notifyCaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.CAUGHT_EXCEPTION_FILTER_NAME);
            boolean notifyUncaught = ArrayUtils.contains(filters, Types.ExceptionBreakpointFilter.UNCAUGHT_EXCEPTION_FILTER_NAME);

            this.debugSession.setExceptionBreakpoints(notifyCaught, notifyUncaught);
        } catch (Exception ex) {
            AdapterUtils.setErrorResponse(response, ErrorCode.SET_EXCEPTIONBREAKPOINT_FAILURE,
                    String.format("Failed to setExceptionBreakpoints. Reason: '%s'", ex.getMessage()));
        }
    }

    private void resume(Requests.ContinueArguments arguments, Response response) {
        boolean allThreadsContinued = true;
        ThreadReference thread = getThread(arguments.threadId);
        if (thread != null) {
            allThreadsContinued = false;
            thread.resume();
            checkThreadRunningAndRecycleIds(thread);
        } else {
            this.debugSession.resume();
            this.variableRequestHandler.recyclableAllObject();
        }
        response.body = new Responses.ContinueResponseBody(allThreadsContinued);
    }

    private void next(Requests.NextArguments arguments, Response response) {
        ThreadReference thread = getThread(arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOver(thread, this.debugSession.eventHub());
            checkThreadRunningAndRecycleIds(thread);
        }
    }

    private void stepIn(Requests.StepInArguments arguments, Response response) {
        ThreadReference thread = getThread(arguments.threadId);
        if (thread != null) {
            DebugUtility.stepInto(thread, this.debugSession.eventHub());
            checkThreadRunningAndRecycleIds(thread);
        }
    }

    private void stepOut(Requests.StepOutArguments arguments, Response response) {
        ThreadReference thread = getThread(arguments.threadId);
        if (thread != null) {
            DebugUtility.stepOut(thread, this.debugSession.eventHub());
            checkThreadRunningAndRecycleIds(thread);
        }
    }

    private void pause(Requests.PauseArguments arguments, Response response) {
        ThreadReference thread = getThread(arguments.threadId);
        if (thread != null) {
            thread.suspend();
            this.sendEventLater(new Events.StoppedEvent("pause", arguments.threadId));
        } else {
            this.debugSession.suspend();
            this.sendEventLater(new Events.StoppedEvent("pause", arguments.threadId, true));
        }
    }

    private void threads(Requests.ThreadsArguments arguments, Response response) {
        ArrayList<Types.Thread> threads = new ArrayList<>();
        for (ThreadReference thread : this.safeGetAllThreads()) {
            Types.Thread clientThread = this.convertDebuggerThreadToClient(thread);
            threads.add(clientThread);
        }
        response.body = new Responses.ThreadsResponseBody(threads);
    }

    private void stackTrace(Requests.StackTraceArguments arguments, Response response) {
        try {
            response.body = this.variableRequestHandler.stackTrace(arguments);
        } catch (IncompatibleThreadStateException | AbsentInformationException | URISyntaxException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.GET_STACKTRACE_FAILURE,
                    String.format("Failed to get stackTrace. Reason: '%s'", e.getMessage()));
        }
    }

    private void scopes(Requests.ScopesArguments arguments, Response response) {
        response.body = this.variableRequestHandler.scopes(arguments);
    }

    private void variables(Requests.VariablesArguments arguments, Response response) {
        try {
            response.body = this.variableRequestHandler.variables(arguments);
        } catch (AbsentInformationException e) {
            AdapterUtils.setErrorResponse(response, ErrorCode.GET_VARIABLE_FAILURE,
                    String.format("Failed to get variables. Reason: '%s'", e.getMessage()));
        }
    }

    private void setVariable(Requests.SetVariableArguments arguments, Response response) {
        response.body = this.variableRequestHandler.setVariable(arguments);
    }

    private void source(Requests.SourceArguments arguments, Response response) {
        int sourceReference = arguments.sourceReference;
        String uri = sourceCollection.get(sourceReference);
        String contents = this.convertDebuggerSourceToClient(uri);
        response.body = new Responses.SourceResponseBody(contents);
    }

    private void evaluate(Requests.EvaluateArguments arguments, Response response) {
        try {
            response.body = this.variableRequestHandler.evaluate(arguments);
        } catch (IllegalArgumentException | NullPointerException ex) {
            AdapterUtils.setErrorResponse(response, ErrorCode.EVALUATE_FAILURE,
                    String.format("Failed to evaluate expression %s . Reason: '%s'", arguments.expression, ex.toString()));
        }
    }

    /* ======================================================*/
    /* Dispatch logic End */
    /* ======================================================*/

    // This is a global event handler to handle the JDI Event from Virtual Machine.
    private void handleEvent(DebugEvent debugEvent) {
        Event event = debugEvent.event;
        if (event instanceof VMStartEvent) {
            // do nothing.
        } else if (event instanceof VMDeathEvent) {
            this.sendEventLater(new Events.ExitedEvent(0));
        } else if (event instanceof VMDisconnectEvent) {
            this.sendEventLater(new Events.TerminatedEvent());
            // Terminate eventHub thread.
            try {
                this.debugSession.eventHub().close();
            } catch (Exception e) {
                // do nothing.
            }
        } else if (event instanceof ThreadStartEvent) {
            ThreadReference startThread = ((ThreadStartEvent) event).thread();
            Events.ThreadEvent threadEvent = new Events.ThreadEvent("started", startThread.uniqueID());
            this.sendEventLater(threadEvent);
        } else if (event instanceof ThreadDeathEvent) {
            ThreadReference deathThread = ((ThreadDeathEvent) event).thread();
            Events.ThreadEvent threadDeathEvent = new Events.ThreadEvent("exited", deathThread.uniqueID());
            this.sendEventLater(threadDeathEvent);
        } else if (event instanceof BreakpointEvent) {
            ThreadReference bpThread = ((BreakpointEvent) event).thread();
            this.sendEventLater(new Events.StoppedEvent("breakpoint", bpThread.uniqueID()));
            debugEvent.shouldResume = false;
        } else if (event instanceof StepEvent) {
            ThreadReference stepThread = ((StepEvent) event).thread();
            this.sendEventLater(new Events.StoppedEvent("step", stepThread.uniqueID()));
            debugEvent.shouldResume = false;
        } else if (event instanceof ExceptionEvent) {
            ThreadReference thread = ((ExceptionEvent) event).thread();
            this.sendEventLater(new Events.StoppedEvent("exception", thread.uniqueID()));
            debugEvent.shouldResume = false;
        }
    }

    private void launchDebugSession(Requests.LaunchArguments arguments) throws DebugException {
        String mainClass = arguments.startupClass;
        String classpath = arguments.classpath;
        this.sourcePath = arguments.sourcePath != null ? arguments.sourcePath : new String[0];

        Logger.logInfo("Launch JVM with main class \"" + mainClass + "\", -classpath \"" + classpath + "\"");

        try {
            this.debugSession = DebugUtility.launch(providerContext.getVirtualMachineManagerProvider().getVirtualMachineManager(), mainClass, classpath);
            ProcessConsole debuggeeConsole = new ProcessConsole(this.debugSession.process(), "Debuggee");
            debuggeeConsole.onStdout((output) -> {
                // When DA receives a new OutputEvent, it just shows that on Debug Console and doesn't affect the DA's dispatching workflow.
                // That means the debugger can send OutputEvent to DA at any time.
                sendEvent(Events.OutputEvent.createStdoutOutput(output));
            });
            debuggeeConsole.onStderr((err) -> {
                sendEvent(Events.OutputEvent.createStderrOutput(err));
            });
            debuggeeConsole.start();
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            String errorMessage = String.format("Failed to launch debuggee vm. Reason: \"%s\"", e.toString());
            Logger.logException(errorMessage, e);
            throw new DebugException(errorMessage, e);
        }
    }

    private void attachDebugSession(Requests.AttachArguments arguments) throws DebugException {
        this.sourcePath = arguments.sourcePath != null ? arguments.sourcePath : new String[0];

        try {
            this.debugSession = DebugUtility.attach(providerContext.getVirtualMachineManagerProvider().getVirtualMachineManager(),
                    arguments.hostName, arguments.port, arguments.attachTimeout);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            String errorMessage = String.format("Failed to attach to remote debuggee vm. Reason: \"%s\"", e.toString());
            Logger.logException(errorMessage, e);
            throw new DebugException(errorMessage, e);
        }
    }

    private void shutdownDebugSession(boolean terminateDebuggee) {
        this.eventSubscriptions.clear();
        this.breakpointManager.reset();
        this.variableRequestHandler.recyclableAllObject();
        this.sourceCollection.reset();
        if (this.debugSession != null) {
            if (terminateDebuggee) {
                this.debugSession.terminate();
            } else {
                this.debugSession.detach();
            }
        }
    }

    private ThreadReference getThread(int threadId) {
        for (ThreadReference thread : this.safeGetAllThreads()) {
            if (thread.uniqueID() == threadId) {
                return thread;
            }
        }
        return null;
    }

    private List<ThreadReference> safeGetAllThreads() {
        try {
            return this.debugSession.allThreads();
        } catch (VMDisconnectedException ex) {
            return new ArrayList<>();
        }
    }

    private int convertDebuggerLineToClient(int line) {
        if (this.debuggerLinesStartAt1) {
            return this.clientLinesStartAt1 ? line : line - 1;
        } else {
            return this.clientLinesStartAt1 ? line + 1 : line;
        }
    }

    private int convertClientLineToDebugger(int line) {
        if (this.debuggerLinesStartAt1) {
            return this.clientLinesStartAt1 ? line : line + 1;
        } else {
            return this.clientLinesStartAt1 ? line - 1 : line;
        }
    }

    private int[] convertClientLineToDebugger(int[] lines) {
        int[] newLines = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            newLines[i] = convertClientLineToDebugger(lines[i]);
        }
        return newLines;
    }

    private String convertClientPathToDebugger(String clientPath) {
        if (clientPath == null) {
            return null;
        }

        if (this.debuggerPathsAreUri) {
            if (this.clientPathsAreUri) {
                return clientPath;
            } else {
                try {
                    return Paths.get(clientPath).toUri().toString();
                } catch (InvalidPathException e) {
                    return null;
                }
            }
        } else {
            if (this.clientPathsAreUri) {
                try {
                    return Paths.get(new URI(clientPath)).toString();
                } catch (URISyntaxException | IllegalArgumentException
                        | FileSystemNotFoundException | SecurityException e) {
                    return null;
                }
            } else {
                return clientPath;
            }
        }
    }

    private String convertDebuggerPathToClient(String debuggerPath) {
        if (debuggerPath == null) {
            return null;
        }

        if (this.debuggerPathsAreUri) {
            if (this.clientPathsAreUri) {
                return debuggerPath;
            } else {
                try {
                    return Paths.get(new URI(debuggerPath)).toString();
                } catch (URISyntaxException | IllegalArgumentException
                        | FileSystemNotFoundException | SecurityException e) {
                    return null;
                }
            }
        } else {
            if (this.clientPathsAreUri) {
                try {
                    return Paths.get(debuggerPath).toUri().toString();
                } catch (InvalidPathException e) {
                    return null;
                }
            } else {
                return debuggerPath;
            }
        }
    }

    private Types.Breakpoint convertDebuggerBreakpointToClient(IBreakpoint breakpoint) {
        int id = (int) breakpoint.getProperty("id");
        boolean verified = breakpoint.getProperty("verified") != null ? (boolean) breakpoint.getProperty("verified") : false;
        int lineNumber = this.convertDebuggerLineToClient(breakpoint.lineNumber());
        return new Types.Breakpoint(id, verified, lineNumber, "");
    }

    private IBreakpoint[] convertClientBreakpointsToDebugger(String sourceFile, Types.SourceBreakpoint[] sourceBreakpoints) throws DebugException {
        int[] lines = Arrays.asList(sourceBreakpoints).stream().map(sourceBreakpoint -> {
            return sourceBreakpoint.line;
        }).mapToInt(line -> line).toArray();
        int[] debuggerLines = this.convertClientLineToDebugger(lines);
        String[] fqns = providerContext.getSourceLookUpProvider().getFullyQualifiedName(sourceFile, debuggerLines, null);
        IBreakpoint[] breakpoints = new IBreakpoint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            int hitCount = 0;
            try {
                hitCount = Integer.parseInt(sourceBreakpoints[i].hitCondition);
            } catch (NumberFormatException e) {
                hitCount = 0; // If hitCount is an illegal number, ignore hitCount condition.
            }
            breakpoints[i] = this.debugSession.createBreakpoint(fqns[i], debuggerLines[i], hitCount);
        }
        return breakpoints;
    }

    private Types.Source convertDebuggerSourceToClient(Location location) throws URISyntaxException {
        String fullyQualifiedName = location.declaringType().name();
        String sourceName = "";
        String relativeSourcePath = "";
        try {
            // When the .class file doesn't contain source information in meta data,
            // invoking Location#sourceName() would throw AbsentInformationException.
            sourceName = location.sourceName();
            relativeSourcePath = location.sourcePath();
        } catch (AbsentInformationException e) {
            String enclosingType = AdapterUtils.parseEnclosingType(fullyQualifiedName);
            sourceName = enclosingType.substring(enclosingType.lastIndexOf('.') + 1) + ".java";
            relativeSourcePath = enclosingType.replace('.', '/') + ".java";
        }
        String uri = providerContext.getSourceLookUpProvider().getSourceFileURI(fullyQualifiedName, relativeSourcePath);
        if (uri != null) {
            String clientPath = this.convertDebuggerPathToClient(uri);
            if (uri.startsWith("file:")) {
                return new Types.Source(sourceName, clientPath, 0);
            } else {
                return new Types.Source(sourceName, clientPath, this.sourceCollection.create(uri));
            }
        } else {
            // If the source lookup engine cannot find the source file, then lookup it in the source directories specified by user.
            String absoluteSourcepath = AdapterUtils.sourceLookup(this.sourcePath, relativeSourcePath);
            return new Types.Source(sourceName, absoluteSourcepath, 0);
        }
    }

    private String convertDebuggerSourceToClient(String uri) {
        return providerContext.getSourceLookUpProvider().getSourceContents(uri);
    }

    private Types.Thread convertDebuggerThreadToClient(ThreadReference thread) {
        return new Types.Thread(thread.uniqueID(), "Thread [" + thread.name() + "]");
    }

    private Types.StackFrame convertDebuggerStackFrameToClient(StackFrame stackFrame, int frameId)
            throws URISyntaxException, AbsentInformationException {
        Location location = stackFrame.location();
        Method method = location.method();
        Types.Source clientSource = this.convertDebuggerSourceToClient(location);
        return new Types.StackFrame(frameId, method.name(), clientSource,
                this.convertDebuggerLineToClient(location.lineNumber()), 0);
    }

    private void checkThreadRunningAndRecycleIds(ThreadReference thread) {
        try {
            if (allThreadRunning()) {
                this.variableRequestHandler.recyclableAllObject();
            } else {
                this.variableRequestHandler.recyclableThreads(thread);
            }
        } catch (VMDisconnectedException ex) {
            this.variableRequestHandler.recyclableAllObject();
        }
    }

    private boolean allThreadRunning() {
        return !safeGetAllThreads().stream().anyMatch(ThreadReference::isSuspended);
    }

    private class VariableRequestHandler {
        private static final String PATTERN = "([a-zA-Z_0-9$]+)\\s*\\(([^)]+)\\)";
        private final Pattern simpleExprPattern = Pattern.compile("[A-Za-z0-9_.\\s]+");
        private IVariableFormatter variableFormatter;
        private RecyclableObjectPool<Long, Object> objectPool;

        public VariableRequestHandler(IVariableFormatter variableFormatter) {
            this.objectPool = new RecyclableObjectPool<>();
            this.variableFormatter = variableFormatter;
        }

        public void recyclableAllObject() {
            this.objectPool.removeAllObjects();
        }

        public void recyclableThreads(ThreadReference thread) {
            this.objectPool.removeObjectsByOwner(thread.uniqueID());
        }

        Responses.ResponseBody stackTrace(StackTraceArguments arguments)
                throws IncompatibleThreadStateException, AbsentInformationException, URISyntaxException {
            List<Types.StackFrame> result = new ArrayList<>();
            if (arguments.startFrame < 0 || arguments.levels < 0) {
                return new Responses.StackTraceResponseBody(result, 0);
            }
            ThreadReference thread = getThread(arguments.threadId);
            int totalFrames = 0;
            if (thread != null) {
                totalFrames = thread.frameCount();
                if (totalFrames <= arguments.startFrame) {
                    return new Responses.StackTraceResponseBody(result, totalFrames);
                }
                try {
                    List<StackFrame> stackFrames = arguments.levels == 0
                            ? thread.frames(arguments.startFrame, totalFrames - arguments.startFrame)
                            : thread.frames(arguments.startFrame,
                            Math.min(totalFrames - arguments.startFrame, arguments.levels));
                    for (int i = 0; i < arguments.levels; i++) {
                        StackFrame stackFrame = stackFrames.get(arguments.startFrame + i);
                        int frameId = this.objectPool.addObject(stackFrame.thread().uniqueID(),
                                new JdiObjectProxy<>(stackFrame));
                        Types.StackFrame clientStackFrame = convertDebuggerStackFrameToClient(stackFrame, frameId);
                        result.add(clientStackFrame);
                    }
                } catch (IndexOutOfBoundsException ex) {
                    // ignore if stack frames overflow
                    return new Responses.StackTraceResponseBody(result, totalFrames);
                }
            }
            return new Responses.StackTraceResponseBody(result, totalFrames);
        }

        Responses.ResponseBody scopes(Requests.ScopesArguments arguments) {
            List<Types.Scope> scopes = new ArrayList<>();
            JdiObjectProxy<StackFrame> stackFrameProxy = (JdiObjectProxy<StackFrame>) this.objectPool.getObjectById(arguments.frameId);
            if (stackFrameProxy == null) {
                return new Responses.ScopesResponseBody(scopes);
            }
            StackFrameScope localScope = new StackFrameScope(stackFrameProxy.getProxiedObject(), "Local");
            scopes.add(new Types.Scope(
                    localScope.getScope(), this.objectPool.addObject(stackFrameProxy.getProxiedObject()
                    .thread().uniqueID(), localScope), false));

            return new Responses.ScopesResponseBody(scopes);
        }


        Responses.ResponseBody variables(Requests.VariablesArguments arguments) throws AbsentInformationException {
            Map<String, Object> options = variableFormatter.getDefaultOptions();
            // This should be false by default(currently true for test).
            // User will need to explicitly turn it on by configuring launch.json
            boolean showStaticVariables = true;
            // TODO: when vscode protocol support customize settings of value format, showFullyQualifiedNames should be one of the options.
            boolean showFullyQualifiedNames = true;
            if (arguments.format != null && arguments.format.hex) {
                options.put(NumericFormatter.NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
            }
            if (showFullyQualifiedNames) {
                options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, showFullyQualifiedNames);
            }

            List<Types.Variable> list = new ArrayList<>();
            List<Variable> variables;
            Object obj = this.objectPool.getObjectById(arguments.variablesReference);
            // vscode will always send variables request to a staled scope, return the empty list is ok since the next
            // variable request will contain the right variablesReference.
            if (obj == null) {
                return new Responses.VariablesResponseBody(list);
            }
            ThreadReference thread;
            if (obj instanceof StackFrameScope) {
                StackFrame frame = ((StackFrameScope) obj).getStackFrame();
                thread = frame.thread();
                variables = VariableUtils.listLocalVariables(frame);
                Variable thisVariable = VariableUtils.getThisVariable(frame);
                if (thisVariable != null) {
                    variables.add(thisVariable);
                }
                if (showStaticVariables && frame.location().method().isStatic()) {
                    variables.addAll(VariableUtils.listStaticVariables(frame));
                }
            } else if (obj instanceof ThreadObjectReference) {
                ObjectReference currentObj = ((ThreadObjectReference) obj).getObject();
                thread = ((ThreadObjectReference) obj).getThread();

                if (arguments.count > 0) {
                    variables = VariableUtils.listFieldVariables(currentObj, arguments.start, arguments.count);
                } else {
                    variables = VariableUtils.listFieldVariables(currentObj, showStaticVariables);
                }

            } else {
                throw new IllegalArgumentException(String
                        .format("VariablesRequest: Invalid variablesReference %d.", arguments.variablesReference));
            }
            // find variable name duplicates
            Set<String> duplicateNames = getDuplicateNames(variables.stream().map(var -> var.name)
                    .collect(Collectors.toList()));
            Map<Variable, String> variableNameMap = new HashMap<>();
            if (!duplicateNames.isEmpty()) {
                Map<String, List<Variable>> duplicateVars =
                        variables.stream()
                                .filter(var -> duplicateNames.contains(var.name))
                                .collect(Collectors.groupingBy(var -> var.name, Collectors.toList()));

                duplicateVars.forEach((k, duplicateVariables) -> {
                    Set<String> declarationTypeNames = new HashSet<>();
                    boolean declarationTypeNameConflict = false;
                    // try use type formatter to resolve name conflict
                    for (Variable javaVariable : duplicateVariables) {
                        Type declarationType = javaVariable.getDeclaringType();
                        if (declarationType != null) {
                            String declarationTypeName = this.variableFormatter.typeToString(declarationType, options);
                            String compositeName = String.format("%s (%s)", javaVariable.name, declarationTypeName);
                            if (!declarationTypeNames.add(compositeName)) {
                                declarationTypeNameConflict = true;
                                break;
                            }
                            variableNameMap.put(javaVariable, compositeName);
                        }
                    }
                    // if there are duplicate names on declaration types, use fully qualified name
                    if (declarationTypeNameConflict) {
                        for (Variable javaVariable : duplicateVariables) {
                            Type declarationType = javaVariable.getDeclaringType();
                            if (declarationType != null) {
                                variableNameMap.put(javaVariable, String.format("%s (%s)", javaVariable.name, declarationType.name()));
                            }
                        }
                    }
                });
            }
            for (Variable javaVariable : variables) {
                Value value = javaVariable.value;
                String name = javaVariable.name;
                if (variableNameMap.containsKey(javaVariable)) {
                    name = variableNameMap.get(javaVariable);
                }
                int referenceId = 0;
                if (value instanceof ObjectReference && VariableUtils.hasChildren(value, showStaticVariables)) {
                    ThreadObjectReference threadObjectReference = new ThreadObjectReference(thread, (ObjectReference) value);
                    referenceId = this.objectPool.addObject(thread.uniqueID(), threadObjectReference);
                }
                Types.Variable typedVariables = new Types.Variable(name, variableFormatter.valueToString(value, options),
                        variableFormatter.typeToString(value == null ? null : value.type(), options), referenceId, null);
                if (javaVariable.value instanceof ArrayReference) {
                    typedVariables.indexedVariables = ((ArrayReference) javaVariable.value).length();
                }
                list.add(typedVariables);
            }
            return new Responses.VariablesResponseBody(list);
        }

        Responses.ResponseBody setVariable(Requests.SetVariableArguments arguments) {
            Map<String, Object> options = variableFormatter.getDefaultOptions();
            // This should be false by default(currently true for test).
            // User will need to explicitly turn it on by configuring launch.json
            boolean showStaticVariables = true;
            // TODO: when vscode protocol support customize settings of value format, showFullyQualifiedNames should be one of the options.
            boolean showFullyQualifiedNames = true;
            if (arguments.format != null && arguments.format.hex) {
                options.put(NumericFormatter.NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
            }
            if (showFullyQualifiedNames) {
                options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, showFullyQualifiedNames);
            }

            Object obj = this.objectPool.getObjectById(arguments.variablesReference);

            // obj is null means the stackframe is continued by user manually,
            if (obj == null) {
                return new Responses.ErrorResponseBody(
                        new Types.Message(ErrorCode.SET_VARIABLE_FAILURE.getId(), "Cannot set value because the thread is resumed."));
            }
            ThreadReference thread;
            String name = arguments.name;
            Value newValue;
            String belongToClass = null;

            if (arguments.name.contains("(")) {
                name = arguments.name.replaceFirst(PATTERN, "$1");
                belongToClass = arguments.name.replaceFirst(PATTERN, "$2");
            }

            try {
                if (obj instanceof StackFrameScope) {
                    StackFrameScope frameScope = (StackFrameScope) obj;
                    thread = frameScope.getStackFrame().thread();
                    newValue = handleSetValueForStackFrame(name, belongToClass, arguments.value,
                            showStaticVariables, frameScope.getStackFrame(), options);
                } else if (obj instanceof ThreadObjectReference) {
                    ObjectReference currentObj = ((ThreadObjectReference) obj).getObject();
                    thread = ((ThreadObjectReference) obj).getThread();
                    newValue = handleSetValueForObject(name, belongToClass, arguments.value,
                            currentObj, options);
                } else {
                    throw new IllegalArgumentException(
                            String.format("SetVariableRequest: Variable %s cannot be found.", arguments.variablesReference));
                }
            } catch (IllegalArgumentException | AbsentInformationException | InvalidTypeException
                    | UnsupportedOperationException | ClassNotLoadedException e) {
                return new Responses.ErrorResponseBody(new Types.Message(ErrorCode.SET_VARIABLE_FAILURE.getId(), e.toString()));
            }
            int referenceId = getReferenceId(thread, newValue, showStaticVariables);

            int indexedVariables = 0;
            if (newValue instanceof ArrayReference) {
                indexedVariables = ((ArrayReference) newValue).length();
            }
            return new Responses.SetVariablesResponseBody(
                    this.variableFormatter.typeToString(newValue == null ? null : newValue.type(), options), // type
                    this.variableFormatter.valueToString(newValue, options), // value,
                    referenceId, indexedVariables);

        }

        private Value handleSetValueForObject(String name, String belongToClass, String valueString,
                                              ObjectReference currentObj, Map<String, Object> options)
                throws InvalidTypeException, ClassNotLoadedException {
            Value newValue;
            if (currentObj instanceof ArrayReference) {
                ArrayReference array = (ArrayReference) currentObj;
                Type eleType = ((ArrayType) array.referenceType()).componentType();
                newValue = setArrayValue(array, eleType, Integer.parseInt(name), valueString, options);
            } else {
                if (StringUtils.isBlank(belongToClass)) {
                    Field field = currentObj.referenceType().fieldByName(name);
                    if (field != null) {
                        if (field.isStatic()) {
                            newValue = this.setStaticFieldValue(currentObj.referenceType(), field,
                                    name, valueString, options);
                        } else {
                            newValue = this.setObjectFieldValue(currentObj, field, name,
                                    valueString, options);
                        }
                    } else {
                        throw new IllegalArgumentException(
                                String.format("SetVariableRequest: Variable %s cannot be found.", name));
                    }
                } else {
                    newValue = setFieldValueWithConflict(currentObj, currentObj.referenceType().allFields(),
                            name, belongToClass, valueString, options);
                }
            }
            return newValue;

        }

        private Value handleSetValueForStackFrame(String name, String belongToClass, String valueString,
                                                  boolean showStaticVariables,
                                                  StackFrame frame, Map<String, Object> options)
                throws AbsentInformationException, InvalidTypeException, ClassNotLoadedException {
            Value newValue;
            if (name.equals("this")) {
                throw new UnsupportedOperationException("SetVariableRequest: 'This' variable cannot be changed.");
            }
            LocalVariable variable = frame.visibleVariableByName(name);
            if (StringUtils.isBlank(belongToClass) && variable != null) {
                newValue = this.setFrameValue(frame, variable, valueString, options);
            } else {
                if (showStaticVariables && frame.location().method().isStatic()) {
                    ReferenceType type = frame.location().declaringType();
                    if (StringUtils.isBlank(belongToClass)) {
                        Field field = type.fieldByName(name);
                        newValue = setStaticFieldValue(type, field, name, valueString, options);
                    } else {
                        newValue = setFieldValueWithConflict(null, type.allFields(), name, belongToClass,
                                valueString, options);
                    }

                } else {
                    throw new UnsupportedOperationException(
                            String.format("SetVariableRequest: Variable %s cannot be found.", name));
                }
            }
            return newValue;
        }

        private Responses.ResponseBody evaluate(Requests.EvaluateArguments arguments) {
            // This should be false by default(currently true for test).
            // User will need to explicitly turn it on by configuring launch.json
            final boolean showStaticVariables = true;
            // TODO: when vscode protocol support customize settings of value format, showFullyQualifiedNames should be one of the options.
            boolean showFullyQualifiedNames = true;
            Map<String, Object> options = variableFormatter.getDefaultOptions();
            if (arguments.format != null && arguments.format.hex) {
                options.put(NumericFormatter.NUMERIC_FORMAT_OPTION, NumericFormatEnum.HEX);
            }
            if (showFullyQualifiedNames) {
                options.put(SimpleTypeFormatter.QUALIFIED_CLASS_NAME_OPTION, showFullyQualifiedNames);
            }
            String expression = arguments.expression;

            if (StringUtils.isBlank(expression)) {
                throw new IllegalArgumentException("Empty expression cannot be evaluated.");
            }

            if (!simpleExprPattern.matcher(expression).matches()) {
                throw new IllegalArgumentException("Complicate expression is not supported currently.");
            }

            JdiObjectProxy<StackFrame> stackFrameProxy = (JdiObjectProxy<StackFrame>)this.objectPool.getObjectById(arguments.frameId);
            if (stackFrameProxy == null) {
                // stackFrameProxy is null means the stackframe is continued by user manually,
                return new Responses.ErrorResponseBody(
                        new Types.Message(ErrorCode.EVALUATE_FAILURE.getId(), "Cannot evaluate because the thread is resumed."));
            }

            // split a.b.c => ["a", "b", "c"]
            List<String> referenceExpressions = Arrays.stream(StringUtils.split(expression, '.'))
                    .filter(StringUtils::isNotBlank).map(StringUtils::trim).collect(Collectors.toList());

            // get first level of value from stack frame
            Variable firstLevelValue = null;
            boolean inStaticMethod = !stackFrameProxy.getProxiedObject().location().method().isStatic();
            String firstExpression = referenceExpressions.get(0);
            // handle special case of 'this'
            if (firstExpression.equals("this") && !inStaticMethod) {
                firstLevelValue = VariableUtils.getThisVariable(stackFrameProxy.getProxiedObject());
            }
            if (firstLevelValue == null) {
                try {
                    // local variables first, that means
                    // if both local variable and static variable are found, use local variable
                    List<Variable> localVariables = VariableUtils.listLocalVariables(stackFrameProxy.getProxiedObject());
                    List<Variable> matchedLocal = localVariables.stream()
                            .filter(localVariable -> localVariable.name.equals(firstExpression)).collect(Collectors.toList());
                    if (!matchedLocal.isEmpty()) {
                        firstLevelValue = matchedLocal.get(0);
                    } else {
                        List<Variable> staticVariables = VariableUtils.listStaticVariables(stackFrameProxy.getProxiedObject());
                        List<Variable> matchedStatic = staticVariables.stream()
                                .filter(staticVariable -> staticVariable.name.equals(firstExpression)).collect(Collectors.toList());
                        if (matchedStatic.isEmpty()) {
                            throw new IllegalArgumentException(String.format("Cannot find the variable: %s.", referenceExpressions.get(0)));
                        }
                        firstLevelValue = matchedStatic.get(0);
                    }

                } catch (AbsentInformationException e) {
                    // ignore
                }
            }

            if (firstLevelValue == null) {
                throw new IllegalArgumentException(String.format("Cannot find variable with name '%s'.", referenceExpressions.get(0)));
            }
            ThreadReference thread = stackFrameProxy.getProxiedObject().thread();
            Value currentValue = firstLevelValue.value;

            for (int i = 1; i < referenceExpressions.size(); i++) {
                String fieldName = referenceExpressions.get(i);
                if (currentValue == null) {
                    throw new NullPointerException("Evaluation encounters NPE error.");
                }
                if (currentValue instanceof PrimitiveValue) {
                    throw new IllegalArgumentException(String.format("Cannot find the field: %s.", fieldName));
                }
                if (currentValue instanceof ArrayReference) {
                    throw new IllegalArgumentException(String.format("Evaluating array elements is not supported currently.", fieldName));
                }
                ObjectReference obj = (ObjectReference) currentValue;
                Field field = obj.referenceType().fieldByName(fieldName);
                if (field == null) {
                    throw new IllegalArgumentException(String.format("Cannot find the field: %s.", fieldName));
                }
                if (field.isStatic()) {
                    throw new IllegalArgumentException(String.format("Cannot find the field: %s.", fieldName));
                }
                currentValue = obj.getValue(field);
            }

            int referenceId = 0;
            if (currentValue instanceof ObjectReference && VariableUtils.hasChildren(currentValue, showStaticVariables)) {
                // save the evaluated value in object pool, because like java.lang.String, the evaluated object will have sub structures
                // we need to set up the id map.
                ThreadObjectReference threadObjectReference = new ThreadObjectReference(thread, (ObjectReference) currentValue);
                referenceId = this.objectPool.addObject(thread.uniqueID(), threadObjectReference);
            }
            int indexedVariables = 0;
            if (currentValue instanceof ArrayReference) {
                indexedVariables = ((ArrayReference) currentValue).length();
            }
            return new Responses.EvaluateResponseBody(variableFormatter.valueToString(currentValue, options),
                    referenceId, variableFormatter.typeToString(currentValue == null ? null : currentValue.type(), options),
                    indexedVariables);
        }

        private Value setValueProxy(Type type, String value, SetValueFunction setValueFunc, Map<String, Object> options)
                throws ClassNotLoadedException, InvalidTypeException {
            Value newValue = this.variableFormatter.stringToValue(value, type, options);
            setValueFunc.apply(newValue);
            return newValue;
        }

        private Value setStaticFieldValue(Type declaringType, Field field, String name, String value, Map<String, Object> options)
                throws ClassNotLoadedException, InvalidTypeException {
            if (field.isFinal()) {
                throw new UnsupportedOperationException(
                        String.format("SetVariableRequest: Final field %s cannot be changed.", name));
            }
            if (!(declaringType instanceof ClassType)) {
                throw new UnsupportedOperationException(
                        String.format("SetVariableRequest: Field %s in interface cannot be changed.", name));
            }
            return setValueProxy(field.type(), value, newValue -> ((ClassType) declaringType).setValue(field, newValue), options);
        }

        private Value setFrameValue(StackFrame frame, LocalVariable localVariable, String value, Map<String, Object> options)
                throws ClassNotLoadedException, InvalidTypeException {
            return setValueProxy(localVariable.type(), value, newValue -> frame.setValue(localVariable, newValue), options);
        }

        private Value setObjectFieldValue(ObjectReference obj, Field field, String name, String value, Map<String, Object> options)
                throws ClassNotLoadedException, InvalidTypeException {
            if (field.isFinal()) {
                throw new UnsupportedOperationException(
                        String.format("SetVariableRequest: Final field %s cannot be changed.", name));
            }
            return setValueProxy(field.type(), value, newValue -> obj.setValue(field, newValue), options);
        }

        private Value setArrayValue(ArrayReference array, Type eleType, int index, String value, Map<String, Object> options)
                throws ClassNotLoadedException, InvalidTypeException {
            return setValueProxy(eleType, value, newValue -> array.setValue(index, newValue), options);
        }

        private Value setFieldValueWithConflict(ObjectReference obj, List<Field> fields, String name, String belongToClass,
                                                String value, Map<String, Object> options) throws ClassNotLoadedException, InvalidTypeException {
            Field field;
            // first try to resolve field by fully qualified name
            List<Field> narrowedFields = fields.stream().filter(TypeComponent::isStatic)
                    .filter(t -> t.name().equals(name) && t.declaringType().name().equals(belongToClass))
                    .collect(Collectors.toList());
            if (narrowedFields.isEmpty()) {
                // second try to resolve field by formatted name
                narrowedFields = fields.stream().filter(TypeComponent::isStatic)
                        .filter(t -> t.name().equals(name)
                                && this.variableFormatter.typeToString(t.declaringType(), options).equals(belongToClass))
                        .collect(Collectors.toList());
            }
            if (narrowedFields.size() == 1) {
                field = narrowedFields.get(0);
            } else {
                throw new UnsupportedOperationException(String.format("SetVariableRequest: Name conflicted for %s.", name));
            }
            return field.isStatic() ? setStaticFieldValue(field.declaringType(), field, name, value, options)
                    : this.setObjectFieldValue(obj, field, name, value, options);

        }

        private int getReferenceId(ThreadReference thread, Value value, boolean includeStatic) {
            if (value instanceof ObjectReference && VariableUtils.hasChildren(value, includeStatic)) {
                ThreadObjectReference threadObjectReference = new ThreadObjectReference(thread, (ObjectReference) value);
                return this.objectPool.addObject(thread.uniqueID(), threadObjectReference);
            }
            return 0;
        }


        private Set<String> getDuplicateNames(Collection<String> list) {
            Set<String> result = new HashSet<>();
            Set<String> set = new HashSet<>();

            for (String item : list) {
                if (!set.contains(item)) {
                    set.add(item);
                } else {
                    result.add(item);
                }
            }
            return result;
        }
    }

    @FunctionalInterface
    interface SetValueFunction {
        void apply(Value value) throws InvalidTypeException, ClassNotLoadedException;
    }
}