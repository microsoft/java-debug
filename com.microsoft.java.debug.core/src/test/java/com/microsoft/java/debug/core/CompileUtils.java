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

package com.microsoft.java.debug.core;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CompileUtils {

    private static final String defaultJavaCompilerName = "com.sun.tools.javac.api.JavacTool";
    private static JavaCompiler compiler;
    static {
        compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            try {
                compiler = (JavaCompiler) Class.forName(defaultJavaCompilerName).newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }


    public static void compileFiles(File projectRoot, List<String> javaFiles) {
        DiagnosticCollector diagnosticCollector = new DiagnosticCollector();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, Locale.ENGLISH, Charset.forName("utf-8"));
        Iterable<? extends JavaFileObject> javaFileObjects = fileManager.getJavaFileObjects(javaFiles.toArray(new String[0]));
        File outputFolder = new File(projectRoot, "bin");
        if (!outputFolder.exists()) {
            outputFolder.mkdir();
        }
        String[] options = new String[] { "-d", outputFolder.getAbsolutePath() , "-g", "-proc:none"};
        final StringWriter output = new StringWriter();
        CompilationTask task = compiler.getTask(output, fileManager, diagnosticCollector, Arrays.asList(options), null, javaFileObjects);
        boolean result = task.call();
        if (!result) {
            throw new IllegalArgumentException(
                "Compilation failed:\n" + output);
        }
        List list = diagnosticCollector.getDiagnostics();
        for (Object object : list) {
            Diagnostic d = (Diagnostic) object;
            System.out.println(d.getMessage(Locale.ENGLISH));
        }
    }


    private CompileUtils() {

    }
}
