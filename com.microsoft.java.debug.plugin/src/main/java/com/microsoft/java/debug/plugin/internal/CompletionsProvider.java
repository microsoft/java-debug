/*******************************************************************************
* Copyright (c) 2018 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Microsoft Corporation - initial API and implementation
*******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalRequestor;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;

import com.microsoft.java.debug.core.Configuration;
import com.microsoft.java.debug.core.DebugException;
import com.microsoft.java.debug.core.adapter.ICompletionsProvider;
import com.microsoft.java.debug.core.adapter.IDebugAdapterContext;
import com.microsoft.java.debug.core.adapter.ISourceLookUpProvider;
import com.microsoft.java.debug.core.protocol.Types.CompletionItem;
import com.sun.jdi.StackFrame;

public class CompletionsProvider implements ICompletionsProvider {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private IDebugAdapterContext context;

    @Override
    public void initialize(IDebugAdapterContext context, Map<String, Object> options) {
        this.context = context;
    }

    @Override
    public List<CompletionItem> codeComplete(StackFrame frame, String snippet, int line, int column) {
        List<CompletionItem> res = new ArrayList<CompletionItem>();

        try {
            IType type = resolveType(frame);
            if (type != null) {
                final int offset = JsonRpcHelpers.toOffset(type.getCompilationUnit().getBuffer(), frame.location().lineNumber(), 0);
                CompletionProposalRequestor collector = new CompletionProposalRequestor(type.getCompilationUnit(), offset);

                collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_REF, true);
                collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.TYPE_IMPORT, true);
                collector.setAllowsRequiredProposals(CompletionProposal.FIELD_REF, CompletionProposal.FIELD_IMPORT, true);

                collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_REF, true);
                collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.TYPE_IMPORT, true);
                collector.setAllowsRequiredProposals(CompletionProposal.METHOD_REF, CompletionProposal.METHOD_IMPORT, true);

                collector.setAllowsRequiredProposals(CompletionProposal.CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);

                collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION, CompletionProposal.TYPE_REF, true);
                collector.setAllowsRequiredProposals(CompletionProposal.ANONYMOUS_CLASS_DECLARATION, CompletionProposal.TYPE_REF, true);

                collector.setAllowsRequiredProposals(CompletionProposal.TYPE_REF, CompletionProposal.TYPE_REF, true);

                type.codeComplete(snippet.toCharArray(), offset, snippet.length(), null, null, null, frame.location().method().isStatic(), collector);

                List<org.eclipse.lsp4j.CompletionItem> items = collector.getCompletionItems();

                if (items != null) {
                    for (org.eclipse.lsp4j.CompletionItem lspItem : items) {
                        res.add(convertFromLsp(lspItem));
                    }
                }
            }

        } catch (DebugException | CoreException e) {
            logger.log(Level.SEVERE, String.format("Failed to code complete because of %s", e.toString()), e);
        }

        return res;
    }

    private IType resolveType(StackFrame frame) throws CoreException, DebugException {
        ISourceLookUpProvider sourceProvider = context.getProvider(ISourceLookUpProvider.class);
        if (sourceProvider instanceof JdtSourceLookUpProvider) {
            IJavaProject project = JdtUtils.findProject(frame, ((JdtSourceLookUpProvider) sourceProvider).getSourceContainers());
            if (project != null) {
                return project.findType(JdtUtils.getDeclaringTypeName(frame));
            }
        }
        return null;
    }

    private CompletionItem convertFromLsp(org.eclipse.lsp4j.CompletionItem lspItem) {
        CompletionItem item = new CompletionItem(lspItem.getLabel(), lspItem.getInsertText());
        if (lspItem.getKind() != null) {
            item.type = lspItem.getKind().name().toLowerCase();
        }
        return item;
    }
}
