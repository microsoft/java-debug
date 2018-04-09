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

import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IEvaluationProvider;
import com.microsoft.java.debug.core.adapter.IHotCodeReplaceProvider;
import com.microsoft.java.debug.core.adapter.IProviderContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.adapter.IVirtualMachineManagerProvider;
import com.microsoft.java.debug.core.adapter.ProviderContext;
import com.microsoft.java.debug.plugin.internal.eval.JdtEvaluationProvider;

/**
 * <code>IProviderContext</code> creator using language server.
 */
public abstract class JdtProviderContextFactory {
    /**
     * Create an <code>IProviderContext</code>.
     * @return the <code>IProviderContext</code> instance.
     */
    public static IProviderContext createProviderContext() {
        IProviderContext context = new ProviderContext();
        context.registerProvider(ISourceLookUpProvider.class, new JdtSourceLookUpProvider());
        context.registerProvider(IVirtualMachineManagerProvider.class, new JdtVirtualMachineManagerProvider());
        context.registerProvider(IHotCodeReplaceProvider.class, new JavaHotCodeReplaceProvider());
        context.registerProvider(IEvaluationProvider.class, new JdtEvaluationProvider());
        context.registerProvider(ICompletionsProvider.class, new CompletionsProvider());

        return context;
    }
}
