/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Yevgen Kogan - Bug 403475 - Hot Code Replace drops too much frames in some cases
 *******************************************************************************/
/*******************************************************************************
 * Copyright (c) 2017 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - Adapter the code for VSCode Java Debugger
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.util.IClassFileReader;
import org.eclipse.jdt.core.util.ISourceAttribute;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.protocol.Events.DebugEvent;

/**
 * The hot code replace provider listens for changes to class files and notifies
 * the running debug session of the changes.
 */
public class JavaHotCodeReplaceProvider implements IHotCodeReplaceProvider, IResourceChangeListener {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private static final String CLASS_FILE_EXTENSION = "class"; //$NON-NLS-1$

    private AtomicBoolean needHotCodeReplace = new AtomicBoolean(false);

    /**
     * Visitor for resource deltas.
     */
    protected ChangedClassFilesVisitor classFilesVisitor = new ChangedClassFilesVisitor();

    /**
     * A visitor which collects changed class files.
     */
    class ChangedClassFilesVisitor implements IResourceDeltaVisitor {
        /**
         * The collection of changed class files.
         */
        protected List<IResource> changedFiles = null;

        /**
         * Collection of qualified type names, corresponding to class files.
         */
        protected List<String> fullyQualifiedNames = null;

        /**
         * Answers whether children should be visited.
         * <p>
         * If the associated resource is a class file which has been changed, record it.
         * </p>
         */
        @Override
        public boolean visit(IResourceDelta delta) {
            if (delta == null || 0 == (delta.getKind() & IResourceDelta.CHANGED)) {
                return false;
            }
            IResource resource = delta.getResource();
            if (resource != null) {
                switch (resource.getType()) {
                    case IResource.FILE:
                        if (0 == (delta.getFlags() & IResourceDelta.CONTENT)) {
                            return false;
                        }
                        if (CLASS_FILE_EXTENSION.equals(resource.getFullPath().getFileExtension())) {
                            IPath localLocation = resource.getLocation();
                            if (localLocation != null) {
                                String path = localLocation.toOSString();
                                IClassFileReader reader = ToolFactory.createDefaultClassFileReader(path,
                                        IClassFileReader.CLASSFILE_ATTRIBUTES);
                                if (reader != null) {
                                    // this name is slash-delimited
                                    String qualifiedName = new String(reader.getClassName());
                                    boolean hasBlockingErrors = false;
                                    try {
                                        // If the user doesn't want to replace
                                        // classfiles containing
                                        // compilation errors, get the source
                                        // file associated with
                                        // the class file and query it for
                                        // compilation errors
                                        IJavaProject pro = JavaCore.create(resource.getProject());
                                        ISourceAttribute sourceAttribute = reader.getSourceFileAttribute();
                                        String sourceName = null;
                                        if (sourceAttribute != null) {
                                            sourceName = new String(sourceAttribute.getSourceFileName());
                                        }
                                        IResource sourceFile = getSourceFile(pro, qualifiedName, sourceName);
                                        if (sourceFile != null) {
                                            IMarker[] problemMarkers = null;
                                            problemMarkers = sourceFile.findMarkers(
                                                    IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true,
                                                    IResource.DEPTH_INFINITE);
                                            for (IMarker problemMarker : problemMarkers) {
                                                if (problemMarker.getAttribute(IMarker.SEVERITY,
                                                        -1) == IMarker.SEVERITY_ERROR) {
                                                    hasBlockingErrors = true;
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (CoreException e) {
                                        logger.log(Level.SEVERE, "Failed to visit classes: " + e.getMessage(), e);
                                    }
                                    if (!hasBlockingErrors) {
                                        changedFiles.add(resource);
                                        // dot-delimit the name
                                        fullyQualifiedNames.add(qualifiedName.replace('/', '.'));
                                    }
                                }
                            }
                        }
                        return false;

                    default:
                        return true;
                }
            }
            return true;
        }

        /**
         * Resets the file collection to empty.
         */
        public void reset() {
            changedFiles = new ArrayList<>();
            fullyQualifiedNames = new ArrayList<>();
        }

        /**
         * Answers a collection of changed class files or <code>null</code>.
         */
        public List<IResource> getChangedClassFiles() {
            return changedFiles;
        }

        /**
         * Returns a collection of qualified type names corresponding to the changed
         * class files.
         *
         * @return List
         */
        public List<String> getQualifiedNamesList() {
            return fullyQualifiedNames;
        }

        /**
         * Returns the source file associated with the given type, or <code>null</code>
         * if no source file could be found.
         *
         * @param project
         *            the java project containing the classfile
         * @param qualifiedName
         *            fully qualified name of the type, slash delimited
         * @param sourceAttribute
         *            debug source attribute, or <code>null</code> if none
         */
        private IResource getSourceFile(IJavaProject project, String qualifiedName, String sourceAttribute) {
            String name = null;
            IJavaElement element = null;
            try {
                if (sourceAttribute == null) {
                    element = findElement(qualifiedName, project);
                } else {
                    int i = qualifiedName.lastIndexOf('/');
                    if (i > 0) {
                        name = qualifiedName.substring(0, i + 1);
                        name = name + sourceAttribute;
                    } else {
                        name = sourceAttribute;
                    }
                    element = project.findElement(new Path(name));
                }
                if (element instanceof ICompilationUnit) {
                    ICompilationUnit cu = (ICompilationUnit) element;
                    return cu.getCorrespondingResource();
                }
            } catch (CoreException e) {
                logger.log(Level.INFO, "Failed to get source file with exception" + e.getMessage(), e);
            }
            return null;
        }
    }

    class HotCodeReplaceEvent extends DebugEvent {

        public String message;

        /**
         * Constructor.
         */
        public HotCodeReplaceEvent(String message) {
            super("hotCodeReplace");
            this.message = message;
        }
    }

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> options) {
        // Listen to the built file events.
        ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_BUILD);

        // TODO: Change IProvider interface for shutdown event
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        synchronized (needHotCodeReplace) {
            needHotCodeReplace.set(true);
        }

        if (event.getType() == IResourceChangeEvent.POST_BUILD) {
            ChangedClassFilesVisitor visitor = getChangedClassFiles(event);
            if (visitor != null) {
                List<IResource> resources = visitor.getChangedClassFiles();
                List<String> fullyQualifiedName = visitor.getQualifiedNamesList();
                if (resources != null && !resources.isEmpty()) {
                    doHotCodeReplace(resources, fullyQualifiedName);
                } else {
                    synchronized (needHotCodeReplace) {
                        needHotCodeReplace.set(false);
                        needHotCodeReplace.notifyAll();
                    }
                }
            }
        }
    }

    @Override
    public CompletableFuture<List<String>> completed() {
        synchronized (needHotCodeReplace) {
            try {
                needHotCodeReplace.wait(100);
                if (needHotCodeReplace.get()) {
                    needHotCodeReplace.wait();
                }
            } catch (InterruptedException e) {
                logger.log(Level.INFO, "The hotcode replace completion was interrupted by " + e.getMessage(), e);
            }
        }
        return CompletableFuture.completedFuture(new ArrayList<String>());
    }

    private void doHotCodeReplace(List<IResource> resources, List<String> fullyQualifiedName) {
        synchronized (needHotCodeReplace) {
            needHotCodeReplace.set(false);
            needHotCodeReplace.notifyAll();
        }
    }

    /**
     * Returns the class file visitor after visiting the resource change. The
     * visitor contains the changed class files and qualified type names. Returns
     * <code>null</code> if the visitor encounters an exception, or the delta is not
     * a POST_BUILD.
     */
    private ChangedClassFilesVisitor getChangedClassFiles(IResourceChangeEvent event) {
        IResourceDelta delta = event.getDelta();
        if (event.getType() != IResourceChangeEvent.POST_BUILD || delta == null) {
            return null;
        }
        classFilesVisitor.reset();
        try {
            delta.accept(classFilesVisitor);
        } catch (CoreException e) {
            return null; // quiet failure
        }
        return classFilesVisitor;
    }

    /**
     * Returns the class file or compilation unit containing the given fully
     * qualified name in the specified project. All registered java like file
     * extensions are considered.
     *
     * @param qualifiedTypeName
     *            fully qualified type name
     * @param project
     *            project to search in
     * @return class file or compilation unit or <code>null</code>
     * @throws CoreException
     *             if an exception occurs
     */
    public static IJavaElement findElement(String qualifiedTypeName, IJavaProject project) throws CoreException {
        String path = qualifiedTypeName;

        final String[] javaLikeExtensions = JavaCore.getJavaLikeExtensions();
        int pos = path.indexOf('$');
        if (pos != -1) {
            path = path.substring(0, pos);
        }
        path = path.replace('.', IPath.SEPARATOR);
        path += "."; //$NON-NLS-1$
        for (String ext : javaLikeExtensions) {
            IJavaElement element = project.findElement(new Path(path + ext));
            if (element != null) {
                return element;
            }
        }
        return null;
    }
}
