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
 * Copyright (c) 2017-2019 Microsoft Corporation and others.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
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
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.ls.core.internal.JobHelpers;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.DebugSettings;
import com.microsoft.java.debug.core.DebugUtility;
import com.microsoft.java.debug.core.IDebugSession;
import com.microsoft.java.debug.core.StackFrameUtility;
import com.microsoft.java.debug.core.adapter.AdapterUtils;
import com.microsoft.java.debug.core.adapter.ErrorCode;
import com.microsoft.java.debug.core.adapter.HotCodeReplaceEvent;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.protocol.Events;

import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.StepRequest;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

/**
 * The hot code replace provider listens for changes to class files and notifies
 * the running debug session of the changes.
 */
public class JavaHotCodeReplaceProvider implements IHotCodeReplaceProvider, IResourceChangeListener {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private static final String CLASS_FILE_EXTENSION = "class"; //$NON-NLS-1$

    private IDebugSession currentDebugSession;

    private IDebugAdapterContext context;

    private Map<ThreadReference, List<StackFrame>> threadFrameMap = new HashMap<>();

    private List<Consumer<List<String>>> consumers = new ArrayList<Consumer<List<String>>>();

    private PublishSubject<HotCodeReplaceEvent> eventSubject = PublishSubject.<HotCodeReplaceEvent>create();

    private List<IResource> deltaResources = new ArrayList<>();

    private List<String> deltaClassNames = new ArrayList<>();

    /**
     * Visitor for resource deltas.
     */
    private ChangedClassFilesVisitor classFilesVisitor = new ChangedClassFilesVisitor();

    /**
     * A visitor which collects changed class files.
     */
    class ChangedClassFilesVisitor implements IResourceDeltaVisitor {
        /**
         * The collection of changed class files.
         */
        private List<IResource> changedFiles = null;

        /**
         * Collection of qualified type names, corresponding to class files.
         */
        private List<String> fullyQualifiedNames = null;

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

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> options) {
        if (DebugSettings.getCurrent().hotCodeReplace != DebugSettings.HotCodeReplace.NEVER) {
            // Listen to the built file events.
            ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_BUILD);
        }
        this.context = context;
        currentDebugSession = context.getDebugSession();
    }

    @Override
    public void close() {
        if (DebugSettings.getCurrent().hotCodeReplace != DebugSettings.HotCodeReplace.NEVER) {
            // Remove the listener.
            ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        }

        eventSubject.onComplete();
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() == IResourceChangeEvent.POST_BUILD) {
            ChangedClassFilesVisitor visitor = getChangedClassFiles(event);
            if (visitor != null) {
                List<IResource> resources = visitor.getChangedClassFiles();
                List<String> classNames = visitor.getQualifiedNamesList();
                synchronized (this) {
                    for (int i = 0; i < classNames.size(); i++) {
                        String className = classNames.get(i);
                        IResource resource = resources.get(i);
                        boolean duplicate = false;
                        for (int j = 0; j < deltaClassNames.size(); j++) {
                            if (Objects.equals(deltaClassNames.get(j), className)
                                && JdtUtils.isSameFile(deltaResources.get(j), resource)) {
                                duplicate = true;
                                break;
                            }
                        }

                        if (!duplicate) {
                            deltaClassNames.add(className);
                            deltaResources.add(resource);
                        }
                    }
                }
                publishEvent(HotCodeReplaceEvent.EventType.BUILD_COMPLETE, "Build completed.");
            }
        }
    }

    @Override
    public void onClassRedefined(Consumer<List<String>> consumer) {
        this.consumers.add(consumer);
    }

    @Override
    public CompletableFuture<List<String>> redefineClasses() {
        JobHelpers.waitForBuildJobs(10 * 1000);
        return CompletableFuture.supplyAsync(() -> {
            List<String> classNames = new ArrayList<>();
            String errorMessage = null;
            synchronized (this) {
                classNames.addAll(deltaClassNames);
                errorMessage = doHotCodeReplace(deltaResources, deltaClassNames);
                deltaResources.clear();
                deltaClassNames.clear();
            }

            if (!classNames.isEmpty() && errorMessage != null) {
                throw AdapterUtils.createCompletionException(errorMessage, ErrorCode.HCR_FAILURE);
            }

            return classNames;
        });
    }

    @Override
    public Observable<HotCodeReplaceEvent> getEventHub() {
        return eventSubject;
    }

    private void publishEvent(HotCodeReplaceEvent.EventType type, String message) {
        eventSubject.onNext(new HotCodeReplaceEvent(type, message));
    }

    private void publishEvent(HotCodeReplaceEvent.EventType type, String message, Object data) {
        eventSubject.onNext(new HotCodeReplaceEvent(type, message, data));
    }

    private String doHotCodeReplace(List<IResource> resourcesToReplace, List<String> qualifiedNamesToReplace) {
        if (context == null || currentDebugSession == null) {
            return null;
        }

        if (resourcesToReplace == null || qualifiedNamesToReplace == null || qualifiedNamesToReplace.isEmpty()
                || resourcesToReplace.isEmpty()) {
            return null;
        }

        filterNotLoadedTypes(resourcesToReplace, qualifiedNamesToReplace);
        if (qualifiedNamesToReplace.isEmpty()) {
            return null;
            // If none of the changed types are loaded, do nothing.
        }

        // Not supported scenario:
        if (!currentDebugSession.getVM().canRedefineClasses()) {
            publishEvent(HotCodeReplaceEvent.EventType.ERROR, "JVM doesn't support hot reload classes");
            return "JVM doesn't support hot reload classes";
        }

        String errorMessage = null;
        publishEvent(HotCodeReplaceEvent.EventType.STARTING, "Start hot code replacement procedure...");

        try {
            List<ThreadReference> poppedThreads = new ArrayList<>();
            boolean framesPopped = false;
            if (this.currentDebugSession.getVM().canPopFrames()) {
                try {
                    attemptPopFrames(resourcesToReplace, qualifiedNamesToReplace, poppedThreads);
                    framesPopped = true; // No exception occurred
                } catch (DebugException e) {
                    logger.log(Level.WARNING, "Failed to pop frames " + e.getMessage(), e);
                }
            }

            redefineTypesJDK(resourcesToReplace, qualifiedNamesToReplace);
            for (Consumer<List<String>> consumer : consumers) {
                consumer.accept(qualifiedNamesToReplace);
            }

            if (containsObsoleteMethods()) {
                publishEvent(HotCodeReplaceEvent.EventType.ERROR, "JVM contains obsolete methods");
                errorMessage = "JVM contains obsolete methods";
            }

            if (currentDebugSession.getVM().canPopFrames() && framesPopped) {
                attemptStepIn(poppedThreads);
            } else {
                attemptDropToFrame(resourcesToReplace, qualifiedNamesToReplace);
            }
        } catch (DebugException e) {
            logger.log(Level.SEVERE, "Failed to complete hot code replace: " + e.getMessage(), e);
            errorMessage = e.getMessage();
        } finally {
            publishEvent(HotCodeReplaceEvent.EventType.END, "Completed hot code replace", qualifiedNamesToReplace);
            threadFrameMap.clear();
        }

        return errorMessage;
    }

    private void filterNotLoadedTypes(List<IResource> resources, List<String> qualifiedNames) {
        for (int i = 0, numElements = qualifiedNames.size(); i < numElements; i++) {
            String name = qualifiedNames.get(i);
            List<ReferenceType> list = getJdiClassesByName(name);
            if (list.isEmpty()) {
                // If no classes with the given name are loaded in the VM, don't
                // waste
                // cycles trying to replace.
                qualifiedNames.remove(i);
                resources.remove(i);
                // Decrement the index and number of elements to compensate for
                // item removal
                i--;
                numElements--;
            }
        }
    }

    /**
     * Returns VirtualMachine.classesByName(String), logging any JDI exceptions.
     *
     * @see com.sun.jdi.VirtualMachine
     */
    private List<ReferenceType> getJdiClassesByName(String className) {
        try {
            VirtualMachine vm = this.currentDebugSession.getVM();
            if (vm != null) {
                return vm.classesByName(className);
            }
        } catch (VMDisconnectedException ex) {
            // Ignore this exception since it will happen when the VM is still running.
        }
        return Collections.emptyList();
    }

    /**
     * Looks for the deepest affected stack frames in the stack and forces pop
     * affected frames. Does this for all of the active stack frames in the session.
     */
    private void attemptPopFrames(List<IResource> resources, List<String> replacedClassNames,
            List<ThreadReference> poppedThreads) throws DebugException {
        List<StackFrame> popFrames = getAffectedFrames(currentDebugSession.getAllThreads(), replacedClassNames);

        for (StackFrame popFrame : popFrames) {
            try {
                popStackFrame(popFrame);
                poppedThreads.add(popFrame.thread());
            } catch (DebugException de) {
                poppedThreads.remove(popFrame.thread());
            }
        }
    }

    /**
     * Performs a "step into" operation on the given threads.
     */
    private void attemptStepIn(List<ThreadReference> threads) {
        for (ThreadReference thread : threads) {
            stepIntoThread(thread);
        }
    }

    /**
     * Looks for the deepest affected stack frame in the stack and forces a drop to
     * frame. Does this for all of the active stack frames in the target.
     */
    private void attemptDropToFrame(List<IResource> resources, List<String> replacedClassNames)
            throws DebugException {
        List<StackFrame> dropFrames = getAffectedFrames(currentDebugSession.getAllThreads(), replacedClassNames);

        // All threads that want to drop to frame are able. Proceed with the
        // drop
        for (StackFrame dropFrame : dropFrames) {
            dropToStackFrame(dropFrame);
        }
    }

    /**
     * Returns a list of frames which should be popped in the given threads.
     */
    private List<StackFrame> getAffectedFrames(List<ThreadReference> threads, List<String> replacedClassNames)
            throws DebugException {
        List<StackFrame> popFrames = new ArrayList<>();
        for (ThreadReference thread : threads) {
            if (thread.isSuspended()) {
                StackFrame affectedFrame = getAffectedFrame(thread, replacedClassNames);
                if (affectedFrame == null) {
                    // No frame to drop to in this thread
                    continue;
                }
                if (supportsDropToFrame(thread, affectedFrame)) {
                    popFrames.add(affectedFrame);
                }
            }
        }
        return popFrames;
    }

    /**
     * Returns the stack frame that should be dropped to in the given thread after a
     * hot code replace. This is calculated by determining if the threads contain
     * stack frames that reside in one of the given replaced class names. If
     * possible, only stack frames whose methods were directly affected (and not
     * simply all frames in affected types) will be returned.
     */
    private StackFrame getAffectedFrame(ThreadReference thread, List<String> replacedClassNames)
            throws DebugException {
        List<StackFrame> frames = getStackFrames(thread, false);
        StackFrame affectedFrame = null;
        for (int i = 0; i < frames.size(); i++) {
            StackFrame frame = frames.get(i);
            if (containsChangedType(frame, replacedClassNames)) {
                if (supportsDropToFrame(thread, frame)) {
                    affectedFrame = frame;
                    break;
                }
                // The frame we wanted to drop to cannot be popped.
                // Set the affected frame to the next lowest pop-able
                // frame on the stack.
                int j = i;
                while (j > 0) {
                    j--;
                    frame = frames.get(j);
                    if (supportsDropToFrame(thread, frame)) {
                        affectedFrame = frame;
                        break;
                    }
                }
                break;
            }
        }
        return affectedFrame;
    }

    private boolean containsChangedType(StackFrame frame, List<String> replacedClassNames) throws DebugException {
        String declaringTypeName = JdtUtils.getDeclaringTypeName(frame);
        // Check if the frame's declaring type was changed
        if (replacedClassNames.contains(declaringTypeName)) {
            return true;
        }
        // Check if one of the frame's declaring type's inner classes have
        // changed
        for (String className : replacedClassNames) {
            int index = className.indexOf('$');
            if (index > -1 && declaringTypeName.equals(className.substring(0, index))) {
                return true;
            }
        }
        return false;
    }

    private boolean supportsDropToFrame(ThreadReference thread, StackFrame frame) {
        List<StackFrame> frames = getStackFrames(thread, false);
        for (int i = 0; i < frames.size(); i++) {
            if (StackFrameUtility.isNative(frames.get(i))) {
                return false;
            }
            if (frames.get(i) == frame) {
                return true;
            }
        }
        return false;
    }

    protected void popStackFrame(StackFrame frame) throws DebugException {
        if (frame != null) {
            ThreadReference thread = frame.thread();
            List<StackFrame> frames = getStackFrames(thread, false);
            int desiredSize = frames.indexOf(frame);
            while (desiredSize >= 0) {
                StackFrameUtility.pop(frames.get(0));
                frames = getStackFrames(thread, true);
                desiredSize--;
            }
        }
    }

    protected void dropToStackFrame(StackFrame frame) throws DebugException {
        // Pop the drop frame and all frames above it
        popStackFrame(frame);
        stepIntoThread(frame.thread());
    }

    private void redefineTypesJDK(List<IResource> resources, List<String> qualifiedNames) throws DebugException {
        Map<ReferenceType, byte[]> typesToBytes = getTypesToBytes(resources, qualifiedNames);
        try {
            currentDebugSession.getVM().redefineClasses(typesToBytes);
        } catch (UnsupportedOperationException | NoClassDefFoundError | VerifyError | ClassFormatError
                | ClassCircularityError e) {
            publishEvent(HotCodeReplaceEvent.EventType.ERROR, e.getMessage());
            throw new DebugException("Failed to redefine classes: " + e.getMessage());
        }
    }

    private void stepIntoThread(ThreadReference thread) {
        StepRequest request = DebugUtility.createStepIntoRequest(thread,
                this.context.getStepFilters().classNameFilters);
        currentDebugSession.getEventHub().stepEvents().filter(debugEvent -> request.equals(debugEvent.event.request()))
                .take(1).subscribe(debugEvent -> {
                    debugEvent.shouldResume = false;
                    // Have to send to events to keep the UI sync with the step in operations:
                    context.getProtocolServer().sendEvent(new Events.StoppedEvent("step", thread.uniqueID()));
                    context.getProtocolServer().sendEvent(new Events.ContinuedEvent(thread.uniqueID()));
                });
        request.enable();
        thread.resume();
    }

    /**
     * Returns a mapping of class files to the bytes that make up those class files.
     *
     * @param resources
     *            the classfiles
     * @param qualifiedNames
     *            the fully qualified type names corresponding to the classfiles.
     *            The typeNames correspond to the resources on a one-to-one basis.
     * @return a mapping of class files to bytes key: class file value: the bytes
     *         which make up that classfile
     */
    private Map<ReferenceType, byte[]> getTypesToBytes(List<IResource> resources, List<String> qualifiedNames) {
        Map<ReferenceType, byte[]> typesToBytes = new HashMap<>(resources.size());
        Iterator<IResource> resourceIter = resources.iterator();
        Iterator<String> nameIter = qualifiedNames.iterator();
        IResource resource;
        String name;
        while (resourceIter.hasNext()) {
            resource = resourceIter.next();
            name = nameIter.next();
            List<ReferenceType> classes = getJdiClassesByName(name);
            byte[] bytes = null;
            try {
                bytes = Util.getResourceContentsAsByteArray((IFile) resource);
            } catch (CoreException e) {
                continue;
            }
            for (ReferenceType type : classes) {
                typesToBytes.put(type, bytes);
            }
        }
        return typesToBytes;
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

    private boolean containsObsoleteMethods() throws DebugException {
        List<ThreadReference> threads = currentDebugSession.getAllThreads();
        for (ThreadReference thread : threads) {
            if (!thread.isSuspended()) {
                continue;
            }
            List<StackFrame> frames = getStackFrames(thread, true);
            if (frames == null || frames.isEmpty()) {
                continue;
            }
            for (StackFrame frame : frames) {
                if (StackFrameUtility.isObsolete(frame)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<StackFrame> getStackFrames(ThreadReference thread, boolean refresh) {
        return threadFrameMap.compute(thread, (key, oldValue) -> {
            try {
                return oldValue == null || refresh ? key.frames() : oldValue;
            } catch (IncompatibleThreadStateException e) {
                logger.log(Level.SEVERE, "Failed to get stack frames: " + e.getMessage(), e);
                return oldValue;
            }
        });
    }
}
