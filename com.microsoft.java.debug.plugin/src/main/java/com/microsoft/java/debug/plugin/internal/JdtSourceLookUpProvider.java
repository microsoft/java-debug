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

package com.microsoft.java.debug.plugin.internal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;

public class JdtSourceLookUpProvider implements ISourceLookUpProvider {
    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);
    private static final String JDT_SCHEME = "jdt";
    private static final String PATH_SEPARATOR = "/";

    private HashMap<String, Object> options = new HashMap<String, Object>();

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> props) {
        if (props == null) {
            throw new IllegalArgumentException("argument is null");
        }
        options.putAll(props);
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

        // Currently the highest version the debugger supports is Java SE 9 Edition (JLS9).
        final ASTParser parser = ASTParser.newParser(AST.JLS9);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        CompilationUnit astUnit = null;
        String filePath = AdapterUtils.toPath(uri);
        // For file uri, read the file contents directly and pass them to the ast parser.
        if (filePath != null && Files.isRegularFile(Paths.get(filePath))) {
            Charset cs = (Charset) this.options.get(Constants.DEBUGGEE_ENCODING);
            if (cs == null) {
                cs = Charset.defaultCharset();
            }
            String source = readFile(filePath, cs);
            parser.setSource(source.toCharArray());
            /**
             * See the java doc for { @link ASTParser#setResolveBindings(boolean) }.
             * Binding information is obtained from the Java model. This means that the compilation unit must be located relative to the Java model.
             * This happens automatically when the source code comes from either setSource(ICompilationUnit) or setSource(IClassFile).
             * When source is supplied by setSource(char[]), the location must be established explicitly
             * by setting an environment using setProject(IJavaProject) or setEnvironment(String [], String [], String [], boolean)
             * and a unit name setUnitName(String).
             */
            parser.setEnvironment(new String[0], new String[0], null, true);
            parser.setUnitName(Paths.get(filePath).getFileName().toString());
            /**
             * See the java doc for { @link ASTParser#setSource(char[]) },
             * the user need specify the compiler options explicitly.
             */
            Map<String, String> javaOptions = JavaCore.getOptions();
            javaOptions.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_9);
            javaOptions.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_9);
            javaOptions.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_9);
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

        String[] fqns = new String[lines.length];
        if (astUnit != null) {
            for (int i = 0; i < lines.length; i++) {
                // TODO
                // The ValidBreakpointLocationLocator will verify if the current line is a valid location or not.
                // If so, it will return the fully qualified name of the class type that contains the current line.
                // Otherwise, it will try to find a valid location from the next lines and return it's fully qualified name.
                // In current stage, we don't support to move the invalid breakpoint down to the next valid location, and just
                // mark it as "unverified".
                // In future, we could consider supporting to update the breakpoint to a valid location.
                ValidBreakpointLocationLocator locator = new ValidBreakpointLocationLocator(astUnit, lines[i], true, true);
                astUnit.accept(locator);
                // When the final valid line location is same as the original line, that represents it's a valid breakpoint.
                if (lines[i] == locator.getLineLocation()) {
                    fqns[i] = locator.getFullyQualifiedTypeName();
                }
            }
        }
        return fqns;
    }

    @Override
    public String getSourceFileURI(String fullyQualifiedName, String sourcePath) {
        if (fullyQualifiedName == null) {
            throw new IllegalArgumentException("fullyQualifiedName is null");
        }
        // Jdt Search Engine doesn't support searching anonymous class or local type directly.
        // But because the inner class and anonymous class have the same java file as the enclosing type,
        // search their enclosing type instead.
        if (fullyQualifiedName.indexOf("$") >= 0) {
            return searchDeclarationFileByFqn(AdapterUtils.parseEnclosingType(fullyQualifiedName));
        } else {
            return searchDeclarationFileByFqn(fullyQualifiedName);
        }
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

    private String searchDeclarationFileByFqn(String fullyQualifiedName) {
        String projectName = (String) options.get(Constants.PROJECTNAME);
        IJavaProject project = JdtUtils.getJavaProject(projectName);
        IJavaSearchScope searchScope = createSearchScope(project);
        SearchPattern pattern = SearchPattern.createPattern(
                fullyQualifiedName,
                IJavaSearchConstants.TYPE,
                IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH);

        ArrayList<String> uris = new ArrayList<String>();

        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) {
                Object element = match.getElement();
                if (element instanceof IType) {
                    IType type = (IType) element;
                    if (type.isBinary()) {
                        try {
                            // let the search engine to ignore those class files without attached source.
                            if (type.getSource() != null) {
                                uris.add(getFileURI(type.getClassFile()));
                            }
                        } catch (JavaModelException e) {
                            // ignore
                        }
                    } else {
                        uris.add(getFileURI(type.getResource()));
                    }
                }
            }
        };
        SearchEngine searchEngine = new SearchEngine();
        try {
            searchEngine.search(pattern, new SearchParticipant[] {
                    SearchEngine.getDefaultSearchParticipant()
                }, searchScope, requestor, null /* progress monitor */);
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Search engine failed: %s", e.toString()), e);
        }
        return uris.size() == 0 ? null : uris.get(0);
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
            String uriString = uri.toString();
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

    private static IJavaSearchScope createSearchScope(IJavaProject project) {
        if (project == null) {
            return SearchEngine.createWorkspaceScope();
        }
        return SearchEngine.createJavaSearchScope(new IJavaProject[] {project},
                IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES);
    }

    private static String readFile(String filePath, Charset cs) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader bufferReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(filePath), cs))) {
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
}
