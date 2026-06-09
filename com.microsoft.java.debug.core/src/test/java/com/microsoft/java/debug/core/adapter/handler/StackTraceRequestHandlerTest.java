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

package com.microsoft.java.debug.core.adapter.handler;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.microsoft.java.debug.core.adapter.DebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.core.adapter.Source;
import com.microsoft.java.debug.core.adapter.SourceType;
import com.microsoft.java.debug.core.protocol.Types;

public class StackTraceRequestHandlerTest extends EasyMockSupport {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void convertDebuggerSourceShouldPreferSourcePathsOverCachedJdtUri() throws Exception {
        String fullyQualifiedName = "com.example.Foo";
        String sourceName = "Foo.java";
        String relativeSourcePath = Paths.get("com", "example", sourceName).toString();
        String jdtUri = "jdt://contents/foo.jar/com.example/Foo.java?=handle";
        Path sourceRoot = tempFolder.newFolder("sources").toPath();
        Path sourceFile = sourceRoot.resolve(relativeSourcePath);
        Files.createDirectories(sourceFile.getParent());
        Files.write(sourceFile, "package com.example; class Foo {}\n".getBytes(StandardCharsets.UTF_8));

        ISourceLookUpProvider sourceProvider = createMock(ISourceLookUpProvider.class);
        expect(sourceProvider.getSource(fullyQualifiedName, relativeSourcePath))
                .andReturn(new Source(jdtUri, SourceType.LOCAL))
                .once();
        replayAll();

        ProviderContext providerContext = new ProviderContext();
        providerContext.registerProvider(ISourceLookUpProvider.class, sourceProvider);
        DebugAdapterContext context = new DebugAdapterContext(null, providerContext);
        context.setSourcePaths(new String[0]);

        Types.Source jdtSource = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName,
                relativeSourcePath, context);
        assertEquals(jdtUri, jdtSource.path);

        context.setSourcePaths(new String[] { sourceRoot.toString() });
        Types.Source localSource = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName,
                relativeSourcePath, context);

        assertEquals(sourceFile.toString(), localSource.path);
        assertEquals(0, localSource.sourceReference);

        context.setClientPathsAreUri(true);
        Types.Source localUriSource = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName,
                sourceName, relativeSourcePath, context);

        assertEquals(sourceFile.toUri().toString(), localUriSource.path);
        verifyAll();
    }

    @Test
    public void convertDebuggerSourceShouldHandleSourceWithNullType() throws Exception {
        String fullyQualifiedName = "com.example.Bar";
        String sourceName = "Bar.java";
        Path sourceFile = tempFolder.newFile(sourceName).toPath();
        String sourceUri = sourceFile.toUri().toString();

        ISourceLookUpProvider sourceProvider = createMock(ISourceLookUpProvider.class);
        expect(sourceProvider.getSource(fullyQualifiedName, sourceName))
                .andReturn(new Source(sourceUri, null))
                .once();
        replayAll();

        ProviderContext providerContext = new ProviderContext();
        providerContext.registerProvider(ISourceLookUpProvider.class, sourceProvider);
        DebugAdapterContext context = new DebugAdapterContext(null, providerContext);

        Types.Source source = StackTraceRequestHandler.convertDebuggerSourceToClient(fullyQualifiedName, sourceName,
                sourceName, context);

        assertEquals(sourceFile.toString(), source.path);
        assertEquals(0, source.sourceReference);
        verifyAll();
    }
}
