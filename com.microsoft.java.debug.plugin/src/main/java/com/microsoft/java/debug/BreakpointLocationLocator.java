/*******************************************************************************
* Copyright (c) 2022 Microsoft Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     Gayan Perera (gayanper@gmail.com) - initial API and implementation
*******************************************************************************/
package com.microsoft.java.debug;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;

@SuppressWarnings("restriction")
public class BreakpointLocationLocator
        extends org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator {

    private IMethodBinding methodBinding;

    public BreakpointLocationLocator(CompilationUnit compilationUnit, int lineNumber,
            boolean bindingsResolved,
            boolean bestMatch) {
        super(compilationUnit, lineNumber, bindingsResolved, bestMatch);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        boolean result = super.visit(node);
        if (methodBinding == null && getLocationType() == LOCATION_METHOD) {
            this.methodBinding = node.resolveBinding();
        }
        return result;
    }

    /**
     * Returns the signature of method found if the
     * {@link org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator#getLocationType()}
     * is
     * {@link org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator#LOCATION_METHOD}.
     * Otherwise return null.
     */
    public String getMethodSignature() {
        if (this.methodBinding == null) {
            return null;
        }
        return toSignature(this.methodBinding, getMethodName());
    }

    /**
     * Returns the name of method found if the
     * {@link org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator#getLocationType()}
     * is
     * {@link org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator#LOCATION_METHOD}.
     * Otherwise return null.
     */
    public String getMethodName() {
        if (this.methodBinding == null) {
            return null;
        }
        return this.methodBinding.getName();
    }

    @Override
    public String getFullyQualifiedTypeName() {
        if (this.methodBinding != null) {
            return this.methodBinding.getDeclaringClass().getQualifiedName();
        }
        return super.getFullyQualifiedTypeName();
    }

    static String toSignature(IMethodBinding binding, String name) {
        // use key for now until JDT core provides a public API for this.
        // "Ljava/util/Arrays;.asList<T:Ljava/lang/Object;>([TT;)Ljava/util/List<TT;>;"
        // "([Ljava/lang/String;)V|Ljava/lang/InterruptedException;"
        String signatureString = binding.getKey();
        if (signatureString != null) {
            int index = signatureString.indexOf(name);
            if (index > -1) {
                int exceptionIndex = signatureString.indexOf("|", signatureString.lastIndexOf(")"));
                if (exceptionIndex > -1) {
                    return signatureString.substring(index + name.length(), exceptionIndex);
                }
                return signatureString.substring(index + name.length());
            }
        }
        return null;
    }
}
