/*******************************************************************************
 * Copyright (c) 2016-2018 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied form org.eclipse.jdt.ls.core.internal.contentassist.CompletionsProvider and modified it for class file completion.
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package com.microsoft.java.debug.plugin.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.contentassist.CompletionProposalDescriptionProvider;
import org.eclipse.jdt.ls.core.internal.contentassist.GetterSetterCompletionProposal;
import org.eclipse.jdt.ls.core.internal.contentassist.SortTextHelper;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResolveHandler;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponse;
import org.eclipse.jdt.ls.core.internal.handlers.CompletionResponses;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.google.common.collect.ImmutableSet;
import com.microsoft.java.debug.core.Configuration;


@SuppressWarnings("restriction")
public final class CompletionProposalRequestor extends CompletionRequestor {

    private static final Logger logger = Logger.getLogger(Configuration.LOGGER_NAME);

    private List<CompletionProposal> proposals = new ArrayList<>();
    private final ITypeRoot typeRoot;
    private CompletionProposalDescriptionProvider descriptionProvider;
    private CompletionResponse response;
    private boolean isTestCodeExcluded;
    private CompletionContext context;

    // Update SUPPORTED_KINDS when mapKind changes
    // @formatter:off
    public static final Set<CompletionItemKind> SUPPORTED_KINDS = ImmutableSet.of(CompletionItemKind.Constructor,
            CompletionItemKind.Class, CompletionItemKind.Module, CompletionItemKind.Field, CompletionItemKind.Keyword,
            CompletionItemKind.Reference, CompletionItemKind.Variable, CompletionItemKind.Function,
            CompletionItemKind.Text);
    // @formatter:on

    /**
     * Constructor.
     * @param typeRoot ITypeRoot
     * @param offset within the source file
     */
    public CompletionProposalRequestor(ITypeRoot typeRoot, int offset) {
        this.typeRoot = typeRoot;
        response = new CompletionResponse();
        response.setOffset(offset);
        if (typeRoot instanceof IClassFile)  {
            isTestCodeExcluded = true;
        } else {
            isTestCodeExcluded = !isTestSource(typeRoot.getJavaProject(), typeRoot);
        }
        setRequireExtendedContext(true);
    }

    private boolean isTestSource(IJavaProject project, ITypeRoot cu) {
        if (project == null) {
            return true;
        }
        try {
            IClasspathEntry[] resolvedClasspath = project.getResolvedClasspath(true);
            final IPath resourcePath = cu.getResource().getFullPath();
            for (IClasspathEntry e : resolvedClasspath) {
                if (e.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                    if (e.isTest()) {
                        if (e.getPath().isPrefixOf(resourcePath)) {
                            return true;
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            logger.log(Level.WARNING, String.format("Failed to judge the cu scope: %s", e.toString()), e);
        }
        return false;
    }

    @Override
    public void accept(CompletionProposal proposal) {
        if (isFiltered(proposal)) {
            return;
        }

        if (!isIgnored(proposal.getKind())) {
            if (proposal.getKind() == CompletionProposal.POTENTIAL_METHOD_DECLARATION) {
                acceptPotentialMethodDeclaration(proposal);
            } else {
                if (proposal.getKind() == CompletionProposal.PACKAGE_REF && typeRoot.getParent() != null
                        && String.valueOf(proposal.getCompletion()).equals(typeRoot.getParent().getElementName())) {
                    // Hacky way to boost relevance of current package, for package completions,
                    // until
                    // https://bugs.eclipse.org/518140 is fixed
                    proposal.setRelevance(proposal.getRelevance() + 1);
                }
                proposals.add(proposal);
            }
        }
    }

    /**
     * Return the completion list requested.
     * @return list of the completion item.
     */
    public List<CompletionItem> getCompletionItems() {
        response.setProposals(proposals);
        CompletionResponses.store(response);
        List<CompletionItem> completionItems = new ArrayList<>(proposals.size());
        for (int i = 0; i < proposals.size(); i++) {
            completionItems.add(toCompletionItem(proposals.get(i), i));
        }
        return completionItems;
    }

    /**
     * To completion item.
     * @param proposal proposal
     * @param index index
     * @return CompletionItem
     */
    public CompletionItem toCompletionItem(CompletionProposal proposal, int index) {
        final CompletionItem $ = new CompletionItem();
        $.setKind(mapKind(proposal.getKind()));
        Map<String, String> data = new HashMap<>();
        data.put(CompletionResolveHandler.DATA_FIELD_REQUEST_ID, String.valueOf(response.getId()));
        data.put(CompletionResolveHandler.DATA_FIELD_PROPOSAL_ID, String.valueOf(index));
        $.setData(data);
        this.descriptionProvider.updateDescription(proposal, $);
        adjustCompleteItem($);
        $.setSortText(SortTextHelper.computeSortText(proposal));
        return $;
    }

    private void adjustCompleteItem(CompletionItem item) {
        if (item.getKind() == CompletionItemKind.Function) {
            String text = item.getInsertText();
            if (StringUtils.isNotBlank(text) && !text.endsWith(")")) {
                item.setInsertText(text + "()");
            }
        }
    }

    @Override
    public void acceptContext(CompletionContext context) {
        super.acceptContext(context);
        this.context = context;
        response.setContext(context);
        this.descriptionProvider = new CompletionProposalDescriptionProvider(context);
    }

    private CompletionItemKind mapKind(final int kind) {
        // When a new CompletionItemKind is added, don't forget to update
        // SUPPORTED_KINDS
        switch (kind) {
            case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
            case CompletionProposal.CONSTRUCTOR_INVOCATION:
                return CompletionItemKind.Constructor;
            case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
            case CompletionProposal.TYPE_REF:
                return CompletionItemKind.Class;
            case CompletionProposal.FIELD_IMPORT:
            case CompletionProposal.METHOD_IMPORT:
            case CompletionProposal.METHOD_NAME_REFERENCE:
            case CompletionProposal.PACKAGE_REF:
            case CompletionProposal.TYPE_IMPORT:
                return CompletionItemKind.Module;
            case CompletionProposal.FIELD_REF:
            case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
                return CompletionItemKind.Field;
            case CompletionProposal.KEYWORD:
                return CompletionItemKind.Keyword;
            case CompletionProposal.LABEL_REF:
                return CompletionItemKind.Reference;
            case CompletionProposal.LOCAL_VARIABLE_REF:
            case CompletionProposal.VARIABLE_DECLARATION:
                return CompletionItemKind.Variable;
            case CompletionProposal.METHOD_DECLARATION:
            case CompletionProposal.METHOD_REF:
            case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
            case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
                return CompletionItemKind.Function;
            // text
            case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
            case CompletionProposal.JAVADOC_BLOCK_TAG:
            case CompletionProposal.JAVADOC_FIELD_REF:
            case CompletionProposal.JAVADOC_INLINE_TAG:
            case CompletionProposal.JAVADOC_METHOD_REF:
            case CompletionProposal.JAVADOC_PARAM_REF:
            case CompletionProposal.JAVADOC_TYPE_REF:
            case CompletionProposal.JAVADOC_VALUE_REF:
            default:
                return CompletionItemKind.Text;
        }
    }

    @Override
    public void setIgnored(int completionProposalKind, boolean ignore) {
        super.setIgnored(completionProposalKind, ignore);
        if (completionProposalKind == CompletionProposal.METHOD_DECLARATION && !ignore) {
            setRequireExtendedContext(true);
        }
    }

    private void acceptPotentialMethodDeclaration(CompletionProposal proposal) {
        try {
            IJavaElement enclosingElement = null;
            if (response.getContext().isExtended()) {
                enclosingElement = response.getContext().getEnclosingElement();
            } else if (typeRoot != null) {
                // kept for backward compatibility: CU is not reconciled at this moment,
                // information is missing (bug 70005)
                enclosingElement = typeRoot.getElementAt(proposal.getCompletionLocation() + 1);
            }
            if (enclosingElement == null) {
                return;
            }
            IType type = (IType) enclosingElement.getAncestor(IJavaElement.TYPE);
            if (type != null) {
                String prefix = String.valueOf(proposal.getName());
                int completionStart = proposal.getReplaceStart();
                int completionEnd = proposal.getReplaceEnd();
                int relevance = proposal.getRelevance() + 6;

                GetterSetterCompletionProposal.evaluateProposals(type, prefix, completionStart,
                        completionEnd - completionStart, relevance, proposals);
            }
        } catch (CoreException e) {
            JavaLanguageServerPlugin.logException("Accept potential method declaration failed for completion ", e);
        }
    }

    @Override
    public boolean isTestCodeExcluded() {
        return isTestCodeExcluded;
    }

    public CompletionContext getContext() {
        return context;
    }

    /**
     * copied from
     * org.eclipse.jdt.ui.text.java.CompletionProposalCollector.isFiltered(CompletionProposal)
     */
    private boolean isFiltered(CompletionProposal proposal) {
        if (isIgnored(proposal.getKind())) {
            return true;
        }

        try {
            // Only filter types and constructors from completion.
            // Methods from already imported types and packages can still be proposed.
            // See https://github.com/eclipse/eclipse.jdt.ls/issues/1212
            switch (proposal.getKind()) {
                case CompletionProposal.CONSTRUCTOR_INVOCATION:
                case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
                case CompletionProposal.JAVADOC_TYPE_REF:
                case CompletionProposal.TYPE_REF: {
                    char[] declaringType = getDeclaringType(proposal);
                    return declaringType != null && org.eclipse.jdt.ls.core.internal.contentassist.TypeFilter.isFiltered(declaringType);
                }
                default: // do nothing
            }
        } catch (Exception e) {
            // do nothing
        }

        return false;
    }

    /**
     * copied from
     * org.eclipse.jdt.ui.text.java.CompletionProposalCollector.getDeclaringType(CompletionProposal)
     */
    private final char[] getDeclaringType(CompletionProposal proposal) {
        switch (proposal.getKind()) {
            case CompletionProposal.METHOD_DECLARATION:
            case CompletionProposal.METHOD_NAME_REFERENCE:
            case CompletionProposal.JAVADOC_METHOD_REF:
            case CompletionProposal.METHOD_REF:
            case CompletionProposal.CONSTRUCTOR_INVOCATION:
            case CompletionProposal.ANONYMOUS_CLASS_CONSTRUCTOR_INVOCATION:
            case CompletionProposal.METHOD_REF_WITH_CASTED_RECEIVER:
            case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
            case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
            case CompletionProposal.ANONYMOUS_CLASS_DECLARATION:
            case CompletionProposal.FIELD_REF:
            case CompletionProposal.FIELD_REF_WITH_CASTED_RECEIVER:
            case CompletionProposal.JAVADOC_FIELD_REF:
            case CompletionProposal.JAVADOC_VALUE_REF:
                char[] declaration = proposal.getDeclarationSignature();
                // special methods may not have a declaring type: methods defined on arrays etc.
                // Currently known: class literals don't have a declaring type - use Object
                if (declaration == null) {
                    return "java.lang.Object".toCharArray(); //$NON-NLS-1$
                }
                return Signature.toCharArray(declaration);
            case CompletionProposal.PACKAGE_REF:
            case CompletionProposal.MODULE_REF:
            case CompletionProposal.MODULE_DECLARATION:
                return proposal.getDeclarationSignature();
            case CompletionProposal.JAVADOC_TYPE_REF:
            case CompletionProposal.TYPE_REF:
                return Signature.toCharArray(proposal.getSignature());
            case CompletionProposal.LOCAL_VARIABLE_REF:
            case CompletionProposal.VARIABLE_DECLARATION:
            case CompletionProposal.KEYWORD:
            case CompletionProposal.LABEL_REF:
            case CompletionProposal.JAVADOC_BLOCK_TAG:
            case CompletionProposal.JAVADOC_INLINE_TAG:
            case CompletionProposal.JAVADOC_PARAM_REF:
                return null;
            default:
                Assert.isTrue(false);
                return null;
        }
    }
}
