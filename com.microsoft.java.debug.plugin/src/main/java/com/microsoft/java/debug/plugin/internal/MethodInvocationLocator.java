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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

public class MethodInvocationLocator extends ASTVisitor {
    private int line;
    private CompilationUnit unit;
    private Map<ASTNode, IMethodBinding> targets;

    public MethodInvocationLocator(int line, CompilationUnit unit) {
        super(false);
        this.line = line;
        this.unit = unit;
        this.targets = new HashMap<>();
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(AssertStatement node) {
        return shouldVisitNode(node);

    }

    @Override
    public boolean visit(BreakStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ContinueStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(DoStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(EmptyStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ForStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(IfStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(LabeledStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ReturnStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(SwitchStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ThrowStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(TryStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(WhileStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(YieldStatement node) {
        return shouldVisitNode(node);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        if (shouldVisitNode(node)) {
            targets.put(node, node.resolveConstructorBinding());
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        if (shouldVisitNode(node)) {
            targets.put(node, node.resolveConstructorBinding());
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        targets.put(node, node.resolveMethodBinding());
        return true;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        targets.put(node, node.resolveConstructorBinding());
        return true;
    }

    private boolean shouldVisitNode(ASTNode node) {
        int start = unit.getLineNumber(node.getStartPosition());
        int end = unit.getLineNumber(node.getStartPosition() + node.getLength() - 1);

        if (line >= start && line <= end) {
            return true;
        }

        return false;
    }

    public Map<ASTNode, IMethodBinding> getTargets() {
        return targets;
    }
}
