package com.microsoft.java.debug.plugin.internal.jdt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
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
//import org.eclipse.jdt.ls.core.internal.JDTUtils;

import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.Logger;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.Constants;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.plugin.internal.JavaDebuggerServerPlugin;

public class JdtSourceLookUpProvider implements ISourceLookUpProvider {
    private HashMap<String, Object> context = new HashMap<String, Object>();

    @Override
    public void initialize(Map<String, Object> props) {
        if (props == null) {
            throw new IllegalArgumentException("argument is null");
        }
        context.putAll(props);
    }

    public boolean supportsRealtimeBreakpointVerification() {
        return true;
    }

    /**
     * For a given source file and a list of line locations,
     * return the fully qualified names of the type of the line location.
     * If the line location points an empty line or invalid line, it returns a null fully qualified name.
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

        String[] fqns = new String[lines.length];
//        ITypeRoot typeRoot = JDTUtils.resolveCompilationUnit(uri);
//        if (typeRoot == null) {
//            typeRoot = JDTUtils.resolveClassFile(uri);
//        }
//
//        if (typeRoot != null && lines.length > 0) {
//            // Currently we only support Java SE 8 Edition (JLS8).
//            final ASTParser parser = ASTParser.newParser(AST.JLS8);
//            parser.setResolveBindings(true);
//            parser.setBindingsRecovery(true);
//            parser.setStatementsRecovery(true);
//            parser.setSource(typeRoot);
//            CompilationUnit cunit = (CompilationUnit) parser.createAST(null);
//            for (int i = 0; i < lines.length; i++) {
//                // TODO
//                // The ValidBreakpointLocationLocator will verify if the current line is a valid location or not.
//                // If so, it will return the fully qualified name of the class type that contains the current line.
//                // Otherwise, it will try to find a valid location from the next lines and return it's fully qualified name.
//                // In current stage, we don't support to move the invalid breakpoint down to the next valid location, and just
//                // mark it as "unverified".
//                // In future, we could consider supporting to update the breakpoint to a valid location.
//                ValidBreakpointLocationLocator locator = new ValidBreakpointLocationLocator(cunit, lines[i], true, true);
//                cunit.accept(locator);
//                // When the final valid line location is same as the original line, that represents it's a valid breakpoint.
//                if (lines[i] == locator.getLineLocation()) {
//                    fqns[i] = locator.getFullyQualifiedTypeName();
//                }
//            }
//        }
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
//        IClassFile cf = JDTUtils.resolveClassFile(uri);
//        return getContents(cf);
        return "";
    }

    private String getContents(IClassFile cf) {
        String source = null;
        if (cf != null) {
            try {
                IBuffer buffer = cf.getBuffer();
                if (buffer != null) {
                    source = buffer.getContents();
                }
                if (source == null) {
//                    source = JDTUtils.disassemble(cf);
                }
            } catch (JavaModelException e) {
                Logger.logException("Failed to parse the source contents of the class file", e);
            }
            if (source == null) {
                source = "";
            }
        }
        return source;
    }

    private IJavaProject getJavaProjectFromName(String projectName) throws CoreException {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        if (!project.exists()) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not an existed project."));
        }
        if (!project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
            throw new CoreException(new Status(IStatus.ERROR, JavaDebuggerServerPlugin.PLUGIN_ID, "Not a project with java nature."));
        }
        IJavaProject javaProject = JavaCore.create(project);
        return javaProject;
    }

    private String searchDeclarationFileByFqn(String fullyQualifiedName) {
//        String projectName = (String) context.get(Constants.PROJECTNAME);
//        try {
//            IJavaSearchScope searchScope = projectName != null
//                ? JDTUtils.createSearchScope(getJavaProjectFromName(projectName))
//                : SearchEngine.createWorkspaceScope();
//            SearchPattern pattern = SearchPattern.createPattern(
//                fullyQualifiedName,
//                IJavaSearchConstants.TYPE,
//                IJavaSearchConstants.DECLARATIONS,
//                SearchPattern.R_EXACT_MATCH);
//            ArrayList<String> uris = new ArrayList<String>();
//            SearchRequestor requestor = new SearchRequestor() {
//                @Override
//                public void acceptSearchMatch(SearchMatch match) {
//                    Object element = match.getElement();
//                    if (element instanceof IType) {
//                        IType type = (IType) element;
//                       uris.add(type.isBinary() ? JDTUtils.getFileURI(type.getClassFile()) : JDTUtils.getFileURI(type.getResource()));
//                    }
//                }
//            };
//            SearchEngine searchEngine = new SearchEngine();
//            searchEngine.search(
//                pattern,
//                new SearchParticipant[]{
//                    SearchEngine.getDefaultSearchParticipant()
//                },
//                searchScope,
//                requestor,
//                null /* progress monitor */);
//            return uris.size() == 0 ? null : uris.get(0);
//        } catch (CoreException e) {
//            Logger.logException("Failed to parse java project", e);
//        }
        return null;
    }
}
