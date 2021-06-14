/*******************************************************************************
* Copyright (c) 2021 Microsoft Corporation and others.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ModuleDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class InlineValueHandler {

    /**
     * Find the valid inline variables belonging to the visible view port.
     */
    public static InlineVariable[] resolveInlineVariables(InlineParams params, IProgressMonitor monitor) {
        ITypeRoot root = JDTUtils.resolveTypeRoot(params.uri);
        try {
            if (root == null || root.getBuffer() == null) {
                return new InlineVariable[0];
            }

            Position stoppedLocation = params.stoppedLocation.getStart();
            int stoppedOffset = JsonRpcHelpers.toOffset(root.getBuffer(), stoppedLocation.getLine(), stoppedLocation.getCharacter());
            IMethod enclosingMethod = findEnclosingMethod(root, stoppedOffset);
            if (enclosingMethod == null) {
                return new InlineVariable[0];
            }

            Position startLocation = getPosition(root.getBuffer(), enclosingMethod.getSourceRange().getOffset());
            Range stoppedRange = new Range(startLocation, stoppedLocation);
            if (params.viewPort != null
                && (params.viewPort.getEnd().getLine() < startLocation.getLine() || params.viewPort.getStart().getLine() > stoppedLocation.getLine())) {
                return new InlineVariable[0];
            }

            CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(root, CoreASTProvider.WAIT_YES, monitor);
            VariableVisitor visitor = new VariableVisitor(astRoot, stoppedRange, params.viewPort, Flags.isStatic(enclosingMethod.getFlags()));
            astRoot.accept(visitor);
            InlineVariable[] result = visitor.getInlineVariables();
            return result;
        } catch (JavaModelException e) {
            return new InlineVariable[0];
        }
    }

    private static IMethod findEnclosingMethod(ITypeRoot root, int stoppedOffset) throws JavaModelException {
        IType enclosingType = null;
        if (root instanceof ICompilationUnit) {
            IType[] types = ((ICompilationUnit) root).getAllTypes();
            for (IType type : types) {
                if (isEnclosed(type, stoppedOffset)) {
                    enclosingType = type;
                }
            }
        } else if (root instanceof IClassFile) {
            enclosingType = ((IClassFile) root).getType();
        }

        if (enclosingType == null) {
            return null;
        }

        IMethod enclosingMethod = null;
        for (IMethod method : enclosingType.getMethods()) {
            if (isEnclosed(method, stoppedOffset)) {
                enclosingMethod = method;
                break;
            }
        }

        if (enclosingMethod == null) {
            return null;
        }

        // Deal with the scenario that the stopped location is inside the local types defined in method.
        return findMethodInLocalTypes(enclosingMethod, stoppedOffset);
    }

    private static boolean isEnclosed(ISourceReference sourceReference, int offset) throws JavaModelException {
        ISourceRange sourceRange = sourceReference.getSourceRange();
        return sourceRange != null && offset >= sourceRange.getOffset()
                && offset < sourceRange.getOffset() + sourceRange.getLength();
    }

    private static IMethod findMethodInLocalTypes(IMethod enclosingMethod, int stoppedOffset) throws JavaModelException {
        if (enclosingMethod == null) {
            return null;
        }

        for (IJavaElement element : enclosingMethod.getChildren()) {
            if (element instanceof IType) {
                if (isEnclosed((IType) element, stoppedOffset)) {
                    for (IMethod method : ((IType) element).getMethods()) {
                        if (isEnclosed(method, stoppedOffset)) {
                            IMethod nearerMethod = findMethodInLocalTypes(method, stoppedOffset);
                            return nearerMethod == null ? enclosingMethod : nearerMethod;
                        }
                    }

                    break;
                }
            }
        }

        return enclosingMethod;
    }

    /**
     * Returns the zero based line and column number.
     */
    private static Position getPosition(IBuffer buffer, int offset) {
        int[] result = JsonRpcHelpers.toLine(buffer, offset);
        if (result == null && result.length < 1) {
            return new Position(-1, -1);
        }

        return new Position(result[0], result[1]);
    }

    static class VariableVisitor extends ASTVisitor {
        private CompilationUnit unit = null;
        private Range stoppedSourceRange;
        private Range viewPort;
        private boolean isStoppingAtStaticMethod;
        private int baseLine;
        private Set<Token>[] tokens;
        private List<String> localVarDecls = new ArrayList<>();
        private List<Position> localVarDeclPositions = new ArrayList<>();
        private boolean isStoppingAtLambda = false;
        private Set<String> varDeclsAtLastLine = new HashSet<>();
        private Range visibleInlineRange = null;

        public VariableVisitor(CompilationUnit unit, Range stoppedSourceRange, Range viewPort, boolean stopAtStaticMethod) {
            this.unit = unit;
            this.stoppedSourceRange = stoppedSourceRange;
            this.viewPort = viewPort;
            this.isStoppingAtStaticMethod = stopAtStaticMethod;
            this.baseLine = stoppedSourceRange.getStart().getLine();
            this.tokens = new Set[stoppedSourceRange.getEnd().getLine() - stoppedSourceRange.getStart().getLine() + 1];
            updateVisibleRange();
        }

        private void updateVisibleRange() {
            if (viewPort == null) {
                visibleInlineRange = stoppedSourceRange;
            } else if (compare(viewPort.getStart(), stoppedSourceRange.getEnd()) > 0
                    || compare(viewPort.getEnd(), stoppedSourceRange.getStart()) < 0) {
                visibleInlineRange = null;
            } else {
                Position start = compare(viewPort.getStart(), stoppedSourceRange.getStart()) >= 0 ? viewPort.getStart() : stoppedSourceRange.getStart();
                Position end = compare(viewPort.getEnd(), stoppedSourceRange.getEnd()) <= 0 ? viewPort.getEnd() : stoppedSourceRange.getEnd();
                visibleInlineRange = new Range(start, end);
            }
        }

        /**
         * Handle the variables in the visible source ranges.
         */
        @Override
        public boolean visit(SimpleName node) {
            if (visibleInlineRange == null) {
                return false;
            }

            Position startPosition = getStartPosition(node);
            boolean isAtLastLine = isAtStopLocation(startPosition);
            if (isEnclosed(visibleInlineRange, startPosition) || isAtLastLine) {
                IBinding binding = node.resolveBinding();
                if (!(binding instanceof IVariableBinding)) {
                    return false;
                } else if (isAtLastLine && this.varDeclsAtLastLine.contains(binding.getKey())) {
                    return false;
                }

                String declaringClass = null;
                if (((IVariableBinding) binding).isField()) {
                    ITypeBinding typeBinding = ((IVariableBinding) binding).getDeclaringClass();
                    if (typeBinding == null) {
                        return false;
                    }

                    declaringClass = typeBinding.getBinaryName();
                }

                Token token = new Token(node.getIdentifier(), startPosition, declaringClass);
                int index = startPosition.getLine() - baseLine;
                if (tokens[index] == null) {
                    tokens[index] = new LinkedHashSet<>();
                }

                if (!tokens[index].contains(token)) {
                    tokens[index].add(token);
                }
            }

            return false;
        }

        /**
         * Handle local variable declarations happening in current method.
         */
        @Override
        public boolean visit(VariableDeclarationFragment node) {
            SimpleName name = node.getName();
            Position startPosition = getStartPosition(name);
            if (isEnclosed(stoppedSourceRange, startPosition)) {
                this.localVarDecls.add(name.getIdentifier());
                this.localVarDeclPositions.add(startPosition);
            }

            if (isAtStopLocation(startPosition)) {
                IVariableBinding binding = node.resolveBinding();
                if (binding != null) {
                    this.varDeclsAtLastLine.add(binding.getKey());
                }
            }

            return true;
        }

        /**
         * Handle formal parameter declarations happening in current method.
         */
        @Override
        public boolean visit(SingleVariableDeclaration node) {
            SimpleName name = node.getName();
            Position startPosition = getStartPosition(name);
            if (isEnclosed(stoppedSourceRange, startPosition)) {
                this.localVarDecls.add(name.getIdentifier());
                this.localVarDeclPositions.add(startPosition);
            }

            return false;
        }

        /**
         * Handle the lambda expression containing the stopped location.
         * If the execution instruction stops on a lambda expression, then
         * crop the visible source ranges to the lambda expression body.
         */
        @Override
        public boolean visit(LambdaExpression node) {
            Position startPosition = getStartPosition(node);
            Position endPosition = getEndPosition(node);
            if (compare(startPosition, stoppedSourceRange.getStart()) >= 0
                && isEnclosed(new Range(startPosition, endPosition), stoppedSourceRange.getEnd())) {
                stoppedSourceRange.setStart(startPosition);
                updateVisibleRange();
                isStoppingAtLambda = true;
                localVarDecls.clear();
                localVarDeclPositions.clear();
                return true;
            }

            return super.visit(node);
        }

        /**
         * Handle the method containing the stopped location.
         */
        @Override
        public boolean visit(MethodDeclaration node) {
            Position startPosition = getStartPosition(node);
            Position endPosition = getEndPosition(node);
            if (compare(startPosition, stoppedSourceRange.getStart()) <= 0 && compare(endPosition, stoppedSourceRange.getEnd()) >= 0) {
                return true;
            }

            return false;
        }

        @Override
        public boolean visit(Block node) {
            if (isUnreachableNode(node)) {
                return false;
            }

            return true;
        }

        @Override
        public boolean visit(DoStatement node) {
            if (isUnreachableNode(node) && !isAtStopLocation(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(ForStatement node) {
            if (isUnreachableNode(node) && !isAtStopLocation(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(IfStatement node) {
            if (isUnreachableNode(node) && !isAtStopLocation(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(SwitchStatement node) {
            if (isUnreachableNode(node) && !isAtStopLocation(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(WhileStatement node) {
            if (isUnreachableNode(node) && !isAtStopLocation(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(AnnotationTypeDeclaration node) {
            if (isUnreachableNode(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(AnonymousClassDeclaration node) {
            if (isUnreachableNode(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(TypeDeclarationStatement node) {
            if (isUnreachableNode(node)) {
                return false;
            }

            return super.visit(node);
        }

        @Override
        public boolean visit(ImportDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(ModuleDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(PackageDeclaration node) {
            return false;
        }

        @Override
        public boolean visit(QualifiedName node) {
            return Objects.equals("length", node.getName().getIdentifier());
        }

        @Override
        public boolean visit(QualifiedType node) {
            return false;
        }

        /**
         * Return the valid inline variables in the visible source ranges.
         *
         * <p>There are four typical kinds of variable:
         * - Local variables declared in method body.
         * - Formal parameters declared in method declaration.
         * - Field variables.
         * - Captured variables from outer scope. This includes local type is accessing
         * variables of enclosing method, and lambda expression body is accessing to
         * variables of enclosing method.</p>
         *
         * <p>For the first two kinds such as local variables and formal parameters,
         * we're going to return them with VariableLookup kind since their values are
         * expanded by Variables View by default.</p>
         *
         * <p>For the last two kinds, we're going to return them with Evaluation kind
         * since it requires additional evaluation to get its values.</p>
         */
        public InlineVariable[] getInlineVariables() {
            if (visibleInlineRange == null) {
                return new InlineVariable[0];
            }

            // Adding the local variable declarations to the token list.
            for (int i = 0; i < localVarDecls.size(); i++) {
                String name = localVarDecls.get(i);
                Position position = localVarDeclPositions.get(i);
                if (isEnclosed(visibleInlineRange, position)) {
                    int index = position.getLine() - baseLine;
                    if (tokens[index] == null) {
                        tokens[index] = new LinkedHashSet<>();
                    }

                    Token token = new Token(name, position, null);
                    if (!tokens[index].contains(token)) {
                        tokens[index].add(token);
                    }
                }
            }

            // For lambda expression in non static method, the captured variable 'arg$1'
            // points to 'this' object of the enclosing method, and the index of other
            // captured variables starts with 2.
            int capturedArgIndexInLambda = isStoppingAtStaticMethod ? 1 : 2;
            Map<String, String> capturedVarsInLambda = new HashMap<>();
            List<InlineVariable> result = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                int line = baseLine + i;
                if (tokens[i] == null || line < visibleInlineRange.getStart().getLine()) {
                    continue;
                }

                for (Token token : tokens[i]) {
                    if (!isEnclosed(visibleInlineRange, token.position) && !isAtLastVisibleLine(token.position)) {
                        continue;
                    }

                    // Local Variables
                    if (token.declaringClass == null && localVarDecls.contains(token.name)) {
                        int declIndex = localVarDecls.lastIndexOf(token.name);
                        Position declPosition = localVarDeclPositions.get(declIndex);
                        if (compare(token.position, declPosition) >= 0) {
                            result.add(new InlineVariable(new Range(token.position, token.position), token.name, InlineKind.VariableLookup));
                            continue;
                        }
                    }

                    InlineVariable value = new InlineVariable(
                        new Range(token.position, token.position), token.name, InlineKind.Evaluation, token.declaringClass);
                    // Captured variables by lambda expression
                    if (isStoppingAtLambda && token.declaringClass == null) {
                        /**
                         * When the lambda body accesses variables from its "outer" scope such as
                         * its enclosing method, these variables will be captured as properties of
                         * 'this' object of a synthetic lambda instance by Java runtime. However,
                         * when the compiler parses the lambda expression, it erases the specific
                         * variable name but keeps the captured variable names with format like
                         * 'arg$<index>'. In order to evaluate the correct value from Java runtime,
                         * we have to encode the variable name using the same rule 'arg$<index>' as
                         * the compiler.
                         */
                        if (capturedVarsInLambda.containsKey(token.name)) {
                            value.expression = capturedVarsInLambda.get(token.name);
                        } else {
                            value.expression = "arg$" + capturedArgIndexInLambda++;
                            capturedVarsInLambda.put(token.name, value.expression);
                        }
                    }

                    result.add(value);
                }
            }

            return result.toArray(new InlineVariable[0]);
        }

        private Position getStartPosition(ASTNode node) {
            // Line number returned by AST unit is one based, converts it to zero based.
            int lineNumber = unit.getLineNumber(node.getStartPosition()) - 1;
            int columnNumber = unit.getColumnNumber(node.getStartPosition());
            return new Position(lineNumber, columnNumber);
        }

        private Position getEndPosition(ASTNode node) {
            // Line number returned by AST unit is one based, converts it to zero based.
            int lineNumber = unit.getLineNumber(node.getStartPosition() + node.getLength() - 1) - 1;
            int columnNumber = unit.getColumnNumber(node.getStartPosition() + node.getLength() - 1);
            return new Position(lineNumber, columnNumber);
        }

        private boolean isUnreachableNode(ASTNode node) {
            Position startPosition = getStartPosition(node);
            Position endPosition = getEndPosition(node);
            return compare(startPosition, stoppedSourceRange.getEnd()) > 0
                || compare(endPosition, stoppedSourceRange.getEnd()) < 0;
        }

        private boolean isEnclosed(Range range, Position position) {
            return compare(range.getStart(), position) <= 0 && compare(range.getEnd(), position) >= 0;
        }

        private int compare(Position p1, Position p2) {
            if (p1.getLine() < p2.getLine()) {
                return -1;
            } else if (p1.getLine() == p2.getLine()) {
                return p1.getCharacter() - p2.getCharacter();
            }

            return 1;
        }

        private boolean isAtStopLocation(Position position) {
            return position.getLine() == stoppedSourceRange.getEnd().getLine();
        }

        private boolean isAtStopLocation(ASTNode node) {
            Position startPosition = getStartPosition(node);
            return isAtStopLocation(startPosition);
        }

        private boolean isAtLastVisibleLine(Position position) {
            return visibleInlineRange != null && visibleInlineRange.getEnd().getLine() == position.getLine();
        }
    }

    static class InlineVariable {
        Range range;
        String name;
        InlineKind kind;
        String expression;
        String declaringClass;

        public InlineVariable(Range range, String name, InlineKind kind) {
            this.range = range;
            this.name = name;
            this.kind = kind;
        }

        public InlineVariable(Range range, String name, InlineKind kind, String declaringClass) {
            this.range = range;
            this.name = name;
            this.kind = kind;
            this.declaringClass = declaringClass;
        }
    }

    static enum InlineKind {
        VariableLookup,
        Evaluation
    }

    static class InlineParams {
        String uri;
        Range viewPort;
        Range stoppedLocation;
    }

    static class Token {
        String name;
        Position position;
        String declaringClass = null;

        public Token(String name, Position position) {
            this.name = name;
            this.position = position;
        }

        public Token(String name, Position position, String declaringClass) {
            this.name = name;
            this.position = position;
            this.declaringClass = declaringClass;
        }

        @Override
        public int hashCode() {
            return Objects.hash(declaringClass, name);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Token)) {
                return false;
            }
            Token other = (Token) obj;
            return Objects.equals(declaringClass, other.declaringClass) && Objects.equals(name, other.name);
        }
    }
}
