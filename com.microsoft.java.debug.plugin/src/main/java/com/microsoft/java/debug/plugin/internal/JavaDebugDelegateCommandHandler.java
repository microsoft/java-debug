package com.microsoft.java.debug.plugin.internal;

import java.util.List;

import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;


public class JavaDebugDelegateCommandHandler implements  IDelegateCommandHandler {

	public static String DEBUG_STARTSESSION = "vscode.java.startDebugSession";

	public static String RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";

	public static String BUILD_WORKSPACE = "vscode.java.buildWorkspace";

	@Override
	public Object executeCommand(String commandId, List<Object> arguments) {
		if (DEBUG_STARTSESSION.equals(commandId)) {
		    IDebugServer debugServer = JavaDebugServer.getInstance();
		    debugServer.start();
		    return debugServer.getPort();
		} else if (RESOLVE_CLASSPATH.equals(commandId)) {
			ResolveClasspathsHandler handler = new ResolveClasspathsHandler();
			return handler.resolveClasspaths(arguments);
		} else if (BUILD_WORKSPACE.equals(commandId)) {
			
		}
		return "";
	}
	
	public static JavaDebugDelegateCommandHandler  getInstance() {
		return new JavaDebugDelegateCommandHandler();
	}
}
	