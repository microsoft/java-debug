/*******************************************************************************
 * Copyright (c) 2026 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.microsoft.java.debug.core.JavaBreakpointLocation;
import com.microsoft.java.debug.core.protocol.Types.SourceBreakpoint;

@RunWith(Parameterized.class)
public class JdtSourceLookUpProviderTest {
    private enum SourceKind {
        EXTERNAL, WORKSPACE, FILE_CHARSET, PROJECT_CHARSET
    }

    private static final String PACKAGE_NAME = "d\u00e9mo"; // A non-ASCII identifier exposes incorrect decoding.
    private static final String SOURCE = String.join("\r\n",
            "package " + PACKAGE_NAME + ";",
            "",
            "public class A {",
            "    public static void main(String[] args) {",
            "        int x = 1;",
            "        System.out.println(x);",
            "    }",
            "}",
            "");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Charset charset;
    private final boolean withBom;
    private final SourceKind sourceKind;
    private IProject project;

    public JdtSourceLookUpProviderTest(Charset charset, boolean withBom, SourceKind sourceKind) {
        this.charset = charset;
        this.withBom = withBom;
        this.sourceKind = sourceKind;
    }

    @Parameters(name = "{index}: {0}, BOM={1}, {2}")
    public static Object[][] encodings() {
        return new Object[][] {
            {StandardCharsets.UTF_16LE, true, SourceKind.EXTERNAL},
            {StandardCharsets.UTF_16BE, true, SourceKind.EXTERNAL},
            {StandardCharsets.UTF_8, true, SourceKind.EXTERNAL},
            {StandardCharsets.UTF_8, false, SourceKind.EXTERNAL},
            {Charset.defaultCharset(), false, SourceKind.EXTERNAL},
            {StandardCharsets.UTF_16LE, true, SourceKind.WORKSPACE},
            {StandardCharsets.UTF_16BE, true, SourceKind.WORKSPACE},
            {StandardCharsets.UTF_8, true, SourceKind.WORKSPACE},
            {StandardCharsets.UTF_16LE, false, SourceKind.FILE_CHARSET},
            {StandardCharsets.UTF_16BE, false, SourceKind.FILE_CHARSET},
            {StandardCharsets.UTF_8, false, SourceKind.FILE_CHARSET},
            {StandardCharsets.ISO_8859_1, false, SourceKind.FILE_CHARSET},
            {StandardCharsets.ISO_8859_1, false, SourceKind.PROJECT_CHARSET},
            {StandardCharsets.UTF_16LE, false, SourceKind.PROJECT_CHARSET}
        };
    }

    @After
    public void deleteProject() throws Exception {
        if (project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    @Test
    public void resolvesBreakpointClassName() throws Exception {
        byte[] contents = (withBom ? "\uFEFF" + SOURCE : SOURCE).getBytes(charset);
        String uri;
        if (sourceKind == SourceKind.EXTERNAL) {
            Path file = temporaryFolder.getRoot().toPath().resolve("A.java");
            Files.write(file, contents);
            uri = file.toUri().toASCIIString();
        } else {
            IFile file = createWorkspaceFile(contents);
            uri = file.getLocationURI().toASCIIString();
        }

        JavaBreakpointLocation[] locations = new JdtSourceLookUpProvider().getBreakpointLocations(uri,
                new SourceBreakpoint[] {new SourceBreakpoint(5, -1)});

        assertEquals(1, locations.length);
        assertEquals(PACKAGE_NAME + ".A", locations[0].className());
        assertEquals(5, locations[0].lineNumber());
        assertEquals(5, locations[0].availableBreakpointLocations()[0].line);
    }

    private IFile createWorkspaceFile(byte[] contents) throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("source-encoding-test");
        project.create(null);
        project.open(null);
        project.setDefaultCharset(StandardCharsets.UTF_8.name(), null);
        IProjectDescription description = project.getDescription();
        description.setNatureIds(new String[] {JavaCore.NATURE_ID});
        project.setDescription(description, null);
        IJavaProject javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(new IClasspathEntry[] {JavaCore.newSourceEntry(project.getFullPath())},
                project.getFullPath().append("bin"), null);

        IFile file = project.getFile("A.java");
        file.create(new ByteArrayInputStream(contents), true, null);
        if (sourceKind == SourceKind.FILE_CHARSET) {
            file.setCharset(charset.name(), null);
        } else if (sourceKind == SourceKind.PROJECT_CHARSET) {
            project.setDefaultCharset(charset.name(), null);
        }
        return file;
    }
}
