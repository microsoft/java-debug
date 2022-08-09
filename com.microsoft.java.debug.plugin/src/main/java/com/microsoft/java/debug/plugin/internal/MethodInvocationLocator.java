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
package com.microsoft.java.debug.plugin.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

public class MethodInvocationLocator extends ASTVisitor {
    private int line;
    private CompilationUnit unit;
    private Map<Expression, IMethodBinding> targets;

    private boolean collectMethodInvocations = false;

    public MethodInvocationLocator(int line, CompilationUnit unit) {
        super(false);
        this.line = line;
        this.unit = unit;
        this.targets = new HashMap<>();
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        int start = unit.getLineNumber(node.getStartPosition());
        int end = unit.getLineNumber(node.getStartPosition() + node.getLength());

        if (line >= start && line <= end) {
            collectMethodInvocations = true;
        }
        return collectMethodInvocations;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        int lineNumber = unit.getLineNumber(node.getStartPosition());
        if (lineNumber == this.line) {
            targets.put(node, node.resolveMethodBinding());
            return true;
        }
        return false;
    }

    @Override
    public void endVisit(ExpressionStatement node) {
        collectMethodInvocations = false;
    }

    public Map<Expression, IMethodBinding> getTargets() {
        return targets;
    }
}
