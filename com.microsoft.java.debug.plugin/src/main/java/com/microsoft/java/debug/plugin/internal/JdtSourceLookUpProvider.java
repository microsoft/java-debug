/*******************************************************************************
 * Copyright (c) 2017-2022 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;

import com.microsoft.java.debug.BindingUtils;
import com.microsoft.java.debug.BreakpointLocationLocator;
import com.microsoft.java.debug.LambdaExpressionLocator;
import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.JavaBreakpointLocation;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.protocol.Types.BreakpointLocation;
import com.microsoft.java.debug.core.protocol.Types.SourceBreakpoint;

import com.sun.jdi.StackFrame;

public class JdtSourceLookUpProvider implements ISourceLookUpProvider {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final String JDT_SCHEME = "jdt";
    private static final String PATH_SEPARATOR = "/";
    private ISourceContainer[] sourceContainers = null;

    private HashMap<String, Object> options = new HashMap<String, Object>();
    private String latestJavaVersion = null;
    private int latestASTLevel;

    public JdtSourceLookUpProvider() {
        // Get the latest supported Java version by JDT tooling.
        this.latestJavaVersion = JavaCore.latestSupportedJavaVersion();
        // Get the mapped AST level for the latest Java version.
        Map<String, String> javaOptions = JavaCore.getOptions();
        javaOptions.put(JavaCore.COMPILER_SOURCE, latestJavaVersion);
        this.latestASTLevel = new AST(javaOptions).apiLevel();
    }

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> props) {
        if (props == null) {
            throw new IllegalArgumentException("argument is null");
        }
        options.putAll(props);
        // During initialization, trigger a background job to load the source containers to improve the perf.
        new Thread(() -> {
            getSourceContainers();
        }).start();
    }

    @Override
    public boolean supportsRealtimeBreakpointVerification() {
        return true;
    }

    /**
     * For a given source file and a list of line locations, return the fully
     * qualified names of the type of the line location. If the line location points
     * an empty line or invalid line, it returns a null fully qualified name.
     */
    @Override
    public String[] getFullyQualifiedName(String uri, int[] lines, int[] columns) throws DebugException {
        if (uri == null) {
            throw new IllegalArgumentException("sourceFilePath is null");
        }
        if (lines == null) {
            throw new IllegalArgumentException("lines is null");
        }
        if (columns == null) {
            columns = new int[lines.length];
        } else if (lines.length != columns.length) {
            throw new IllegalArgumentException("the count of lines and columns don't match!");
        }
        if (lines.length == 0) {
            return new String[0];
        }

        SourceBreakpoint[] sourceBreakpoints = new SourceBreakpoint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            sourceBreakpoints[i] = new SourceBreakpoint(lines[i], columns[i]);
        }

        JavaBreakpointLocation[] locations = getBreakpointLocations(uri, sourceBreakpoints);
        return Stream.of(locations).map(location -> {
            if (location.className() != null && location.methodName() != null) {
                return location.className()
                    .concat("#").concat(location.methodName())
                    .concat("#").concat(location.methodSignature());
            }
            return location.className();
        }).toArray(String[]::new);
    }

    @Override
    public JavaBreakpointLocation[] getBreakpointLocations(String sourceUri, SourceBreakpoint[] sourceBreakpoints) throws DebugException {
        if (sourceUri == null) {
            throw new IllegalArgumentException("sourceUri is null");
        }

        if (sourceBreakpoints == null || sourceBreakpoints.length == 0) {
            return new JavaBreakpointLocation[0];
        }

        CompilationUnit astUnit = asCompilationUnit(sourceUri);
        JavaBreakpointLocation[] sourceLocations = Stream.of(sourceBreakpoints)
            .map(sourceBreakpoint -> new JavaBreakpointLocation(sourceBreakpoint.line, sourceBreakpoint.column))
            .toArray(JavaBreakpointLocation[]::new);
        if (astUnit != null) {
            Map<Integer, BreakpointLocation[]> resolvedLocations = new HashMap<>();
            for (JavaBreakpointLocation sourceLocation : sourceLocations) {
                int sourceLine = sourceLocation.lineNumber();
                int sourceColumn = sourceLocation.columnNumber();
                if (sourceColumn > -1) {
                    // if we have a column, try to find the lambda expression at that column
                    LambdaExpressionLocator lambdaExpressionLocator = new LambdaExpressionLocator(astUnit,
                        sourceLine, sourceColumn);
                    astUnit.accept(lambdaExpressionLocator);
                    if (lambdaExpressionLocator.isFound()) {
                        sourceLocation.setClassName(lambdaExpressionLocator.getFullyQualifiedTypeName());
                        sourceLocation.setMethodName(lambdaExpressionLocator.getMethodName());
                        sourceLocation.setMethodSignature(lambdaExpressionLocator.getMethodSignature());
                    }

                    if (resolvedLocations.containsKey(sourceLine)) {
                        sourceLocation.setAvailableBreakpointLocations(resolvedLocations.get(sourceLine));
                    } else {
                        BreakpointLocation[] inlineLocations = getInlineBreakpointLocations(astUnit, sourceLine);
                        sourceLocation.setAvailableBreakpointLocations(inlineLocations);
                        resolvedLocations.put(sourceLine, inlineLocations);
                    }

                    continue;
                }

                // TODO
                // The ValidBreakpointLocationLocator will verify if the current line is a valid
                // location or not.
                // If so, it will return the fully qualified name of the class type that
                // contains the current line.
                // Otherwise, it will try to find a valid location from the next lines and
                // return it's fully qualified name.
                // In current stage, we don't support to move the invalid breakpoint down to the
                // next valid location, and just
                // mark it as "unverified".
                // In future, we could consider supporting to update the breakpoint to a valid
                // location.
                BreakpointLocationLocator locator = new BreakpointLocationLocator(astUnit,
                        sourceLine, true, true);
                astUnit.accept(locator);
                // When the final valid line location is same as the original line, that
                // represents it's a valid breakpoint.
                // Add location type check to avoid breakpoint on method/field which will never
                // be hit in current implementation.
                if (sourceLine == locator.getLineLocation()
                        && locator.getLocationType() == BreakpointLocationLocator.LOCATION_LINE) {
                    sourceLocation.setClassName(locator.getFullyQualifiedTypeName());
                    if (resolvedLocations.containsKey(sourceLine)) {
                        sourceLocation.setAvailableBreakpointLocations(resolvedLocations.get(sourceLine));
                    } else {
                        BreakpointLocation[] inlineLocations = getInlineBreakpointLocations(astUnit, sourceLine);
                        sourceLocation.setAvailableBreakpointLocations(inlineLocations);
                        resolvedLocations.put(sourceLine, inlineLocations);
                    }
                } else if (locator.getLocationType() == BreakpointLocationLocator.LOCATION_METHOD) {
                    sourceLocation.setClassName(locator.getFullyQualifiedTypeName());
                    sourceLocation.setMethodName(locator.getMethodName());
                    sourceLocation.setMethodSignature(locator.getMethodSignature());
                }
            }
        }

        return sourceLocations;
    }

    private BreakpointLocation[] getInlineBreakpointLocations(final CompilationUnit astUnit, int sourceLine) {
        List<BreakpointLocation> locations = new ArrayList<>();
        // The starting position of each line is the default breakpoint location for that line.
        locations.add(new BreakpointLocation(sourceLine, 0));
        astUnit.accept(new ASTVisitor() {
            @Override
            public boolean visit(LambdaExpression node) {
                int lambdaStart = node.getStartPosition();
                int startLine = astUnit.getLineNumber(lambdaStart);
                if (startLine == sourceLine) {
                    int startColumn = astUnit.getColumnNumber(lambdaStart);
                    int lambdaEnd = lambdaStart + node.getLength();
                    int endLine = astUnit.getLineNumber(lambdaEnd);
                    int endColumn = astUnit.getColumnNumber(lambdaEnd);
                    BreakpointLocation location = new BreakpointLocation(startLine, startColumn, endLine, endColumn);
                    locations.add(location);
                }
                return super.visit(node);
            }
        });

        return locations.toArray(BreakpointLocation[]::new);
    }

    private CompilationUnit asCompilationUnit(String uri) {
        final ASTParser parser = ASTParser.newParser(this.latestASTLevel);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        CompilationUnit astUnit = null;
        String filePath = AdapterUtils.toPath(uri);
        // For file uri, read the file contents directly and pass them to the ast
        // parser.
        if (filePath != null && Files.isRegularFile(Paths.get(filePath))) {
            String source = readFile(filePath);
            parser.setSource(source.toCharArray());
            /**
             * See the java doc for { @link ASTParser#setResolveBindings(boolean) }.
             * Binding information is obtained from the Java model. This means that the
             * compilation unit must be located relative to the Java model.
             * This happens automatically when the source code comes from either
             * setSource(ICompilationUnit) or setSource(IClassFile).
             * When source is supplied by setSource(char[]), the location must be
             * established explicitly
             * by setting an environment using setProject(IJavaProject) or
             * setEnvironment(String [], String [], String [], boolean)
             * and a unit name setUnitName(String).
             */
            parser.setEnvironment(new String[0], new String[0], null, true);
            parser.setUnitName(Paths.get(filePath).getFileName().toString());
            /**
             * See the java doc for { @link ASTParser#setSource(char[]) },
             * the user need specify the compiler options explicitly.
             */
            Map<String, String> javaOptions = JavaCore.getOptions();
            javaOptions.put(JavaCore.COMPILER_SOURCE, this.latestJavaVersion);
            javaOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, this.latestJavaVersion);
            javaOptions.put(JavaCore.COMPILER_COMPLIANCE, this.latestJavaVersion);
            javaOptions.put(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
            parser.setCompilerOptions(javaOptions);
            astUnit = (CompilationUnit) parser.createAST(null);
        } else {
            // For non-file uri (e.g. jdt://contents/rt.jar/java.io/PrintStream.class),
            // leverage jdt to load the source contents.
            ITypeRoot typeRoot = resolveClassFile(uri);
            if (typeRoot != null) {
                parser.setSource(typeRoot);
                astUnit = (CompilationUnit) parser.createAST(null);
            }
        }
        return astUnit;
    }

    @Override
    public String getSourceFileURI(String fullyQualifiedName, String sourcePath) {
        if (sourcePath == null) {
            return null;
        }

        Object sourceElement = JdtUtils.findSourceElement(sourcePath, getSourceContainers());
        if (sourceElement instanceof IResource) {
            return getFileURI((IResource) sourceElement);
        } else if (sourceElement instanceof IClassFile) {
            return getFileURI((IClassFile) sourceElement);
        }
        return null;
    }

    @Override
    public String getJavaRuntimeVersion(String projectName) {
        IJavaProject project = JdtUtils.getJavaProject(projectName);
        if (project != null) {
            try {
                IVMInstall vmInstall = JavaRuntime.getVMInstall(project);
                if (vmInstall == null || vmInstall.getInstallLocation() == null) {
                    return null;
                }

                return resolveSystemLibraryVersion(project, vmInstall);
            } catch (CoreException e) {
                logger.log(Level.SEVERE, "Failed to get Java runtime version for project '" + projectName + "': " + e.getMessage(), e);
            }
        }

        return null;
    }

    /**
     * Get the project associated source containers.
     * @return the initialized source container list
     */
    public synchronized ISourceContainer[] getSourceContainers() {
        if (sourceContainers == null) {
            sourceContainers = JdtUtils.getSourceContainers((String) options.get(Constants.PROJECT_NAME));
        }

        return sourceContainers;
    }

    @Override
    public String getSourceContents(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("uri is null");
        }
        IClassFile cf = resolveClassFile(uri);
        return getContents(cf);
    }

    private String getContents(IClassFile cf) {
        String source = null;
        if (cf != null) {
            try {
                IBuffer buffer = cf.getBuffer();
                if (buffer != null) {
                    source = buffer.getContents();
                }
            } catch (JavaModelException e) {
                logger.log(Level.SEVERE, String.format("Failed to parse the source contents of the class file: %s", e.toString()), e);
            }
            if (source == null) {
                source = "";
            }
        }
        return source;
    }

    private static String getFileURI(IClassFile classFile) {
        String packageName = classFile.getParent().getElementName();
        String jarName = classFile.getParent().getParent().getElementName();
        try {
            return new URI(JDT_SCHEME, "contents", PATH_SEPARATOR + jarName + PATH_SEPARATOR + packageName
                    + PATH_SEPARATOR + classFile.getElementName(), classFile.getHandleIdentifier(), null)
                            .toASCIIString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String getFileURI(IResource resource) {
        URI uri = resource.getLocationURI();
        if (uri != null) {
            // If the file path contains non ASCII characters, encode the result.
            String uriString = uri.toASCIIString();
            // Fix uris by adding missing // to single file:/ prefix.
            return uriString.replaceFirst("file:/([^/])", "file:///$1");
        }
        return null;
    }

    private static IClassFile resolveClassFile(String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(uriString);
            if (uri != null && JDT_SCHEME.equals(uri.getScheme()) && "contents".equals(uri.getAuthority())) {
                String handleId = uri.getQuery();
                IJavaElement element = JavaCore.create(handleId);
                IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
                return cf;
            }
        } catch (URISyntaxException e) {
            // ignore
        }
        return null;
    }

    private static String readFile(String filePath) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader bufferReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            final int BUFFER_SIZE = 4096;
            char[] buffer = new char[BUFFER_SIZE];
            while (true) {
                int read = bufferReader.read(buffer, 0, BUFFER_SIZE);
                if (read == -1) {
                    break;
                }
                builder.append(new String(buffer, 0, read));
            }
        } catch (IOException e) {
            // do nothing.
        }
        return builder.toString();
    }

    private static String resolveSystemLibraryVersion(IJavaProject project, IVMInstall vmInstall) throws JavaModelException {
        LibraryLocation[] libraries = JavaRuntime.getLibraryLocations(vmInstall);
        if (libraries != null && libraries.length > 0) {
            IPackageFragmentRoot root = project.findPackageFragmentRoot(libraries[0].getSystemLibraryPath());
            if (!(root instanceof JarPackageFragmentRoot)) {
                return null;
            }
            Manifest manifest = ((JarPackageFragmentRoot) root).getManifest();
            if (manifest == null) {
                return null;
            }
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Implementation-Version");
        }

        return null;
    }

    @Override
    public List<MethodInvocation> findMethodInvocations(StackFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("frame is null");
        }

        IJavaProject project = JdtUtils.findProject(frame, getSourceContainers());
        if (project == null) {
            logger.log(Level.WARNING,
                    String.format("Failed to resolve project for the frame: %s", frame));
            return Collections.emptyList();
        }

        String uri;
        try {
            IType type = project.findType(JdtUtils.getDeclaringTypeName(frame));
            uri = type.getResource().getLocationURI().toURL().toString();
        } catch (JavaModelException | DebugException | MalformedURLException e) {
            logger.log(Level.SEVERE,
                    String.format("Failed to resolve type for the frame: %s", frame));
            return Collections.emptyList();
        }

        CompilationUnit astUnit = asCompilationUnit(uri);
        if (astUnit == null) {
            return Collections.emptyList();
        }

        MethodInvocationLocator locator = new MethodInvocationLocator(frame.location().lineNumber(), astUnit);
        astUnit.accept(locator);

        return locator.getTargets().entrySet().stream().map(entry -> {
            MethodInvocation invocation = new MethodInvocation();
            Expression expression = entry.getKey();
            invocation.expression = expression.toString();
            IMethodBinding binding = entry.getValue();
            invocation.methodName = binding.getName();
            if (binding.getDeclaringClass().isAnonymous()) {
                ITypeBinding superclass = binding.getDeclaringClass().getSuperclass();
                if (superclass != null
                        && !superclass.isEqualTo(astUnit.getAST().resolveWellKnownType("java.lang.Object"))) {
                    invocation.declaringTypeName = superclass.getQualifiedName();
                } else {
                    return null;
                }
            } else {
                invocation.declaringTypeName = binding.getDeclaringClass().getQualifiedName();
            }
            invocation.methodSignature = BindingUtils.toSignature(binding, BindingUtils.getMethodName(binding, true));
            invocation.lineStart = astUnit.getLineNumber(expression.getStartPosition());
            invocation.lineEnd = astUnit.getLineNumber(expression.getStartPosition() + expression.getLength());
            invocation.columnStart = astUnit.getColumnNumber(expression.getStartPosition());
            invocation.columnEnd = astUnit.getColumnNumber(expression.getStartPosition() + expression.getLength());
            return invocation;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
