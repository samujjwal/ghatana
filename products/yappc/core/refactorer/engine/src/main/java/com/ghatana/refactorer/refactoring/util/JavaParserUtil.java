package com.ghatana.refactorer.refactoring.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import java.util.Optional;

/**
 * Utility class for working with JavaParser. 
 * @doc.type class
 * @doc.purpose Handles java parser util operations
 * @doc.layer core
 * @doc.pattern Utility
*/
public final class JavaParserUtil {

    private JavaParserUtil() {
        // Utility class - no instantiation
    }

    /**
 * Finds a method declaration in a compilation unit by name and line number. */
    public static Optional<MethodDeclaration> findMethod(
            CompilationUnit cu, String methodName, int lineNumber) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(methodName))
                .filter(m -> m.getBegin().isPresent() && m.getBegin().get().line == lineNumber)
                .findFirst();
    }

    /**
 * Finds a field declaration in a compilation unit by name and line number. */
    public static Optional<FieldDeclaration> findField(
            CompilationUnit cu, String fieldName, int lineNumber) {
        return cu.findAll(FieldDeclaration.class).stream()
                .filter(
                        f ->
                                f.getVariables().stream()
                                        .anyMatch(v -> v.getNameAsString().equals(fieldName)))
                .filter(f -> f.getBegin().isPresent() && f.getBegin().get().line == lineNumber)
                .findFirst();
    }

    /**
     * Finds a variable declaration in a compilation unit by name and line number. Handles both
     * local variables and fields, with special handling for variables declared in for loops.
     */
    public static Optional<VariableDeclarator> findVariable(
            CompilationUnit cu, String varName, int lineNumber) {
        return cu.findAll(VariableDeclarator.class).stream()
                .filter(v -> v.getNameAsString().equals(varName))
                .filter(
                        v -> {
                            // Check if the variable is on the target line
                            if (v.getBegin().isPresent() && v.getBegin().get().line == lineNumber) {
                                return true;
                            }

                            // For variables in for loops, check the parent node's position
                            if (v.getParentNode().isPresent()) {
                                Node parent = v.getParentNode().get();
                                if (parent.getBegin().isPresent()
                                        && parent.getBegin().get().line == lineNumber) {
                                    return true;
                                }
                            }

                            return false;
                        })
                .findFirst();
    }

    /**
 * Gets the name of a node if it has one. */
    public static Optional<String> getNodeName(Node node) {
        if (node instanceof NodeWithSimpleName) {
            return Optional.of(((NodeWithSimpleName<?>) node).getNameAsString());
        } else if (node instanceof NodeWithName) {
            return Optional.of(((NodeWithName<?>) node).getNameAsString());
        } else if (node instanceof SimpleName) {
            return Optional.of(((SimpleName) node).asString());
        }
        return Optional.empty();
    }

    /**
 * Gets the fully qualified name of a type declaration. */
    public static String getFullyQualifiedName(TypeDeclaration<?> typeDecl) {
        StringBuilder sb = new StringBuilder();

        // Add package name if available
        typeDecl.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .ifPresent(pkg -> sb.append(pkg.getName()).append("."));

        // Add outer class names
        typeDecl.getParentNode()
                .flatMap(
                        parent -> {
                            // Find the first ancestor of type TypeDeclaration<?>
                            Optional<Node> ancestor = parent.getParentNode();
                            while (ancestor.isPresent()) {
                                if (ancestor.get() instanceof TypeDeclaration) {
                                    @SuppressWarnings("unchecked")
                                    TypeDeclaration<?> typeDeclAncestor =
                                            (TypeDeclaration<?>) ancestor.get();
                                    return Optional.of(typeDeclAncestor);
                                }
                                ancestor = ancestor.get().getParentNode();
                            }
                            return Optional.empty();
                        })
                .ifPresent(
                        parentType -> {
                            sb.append(getFullyQualifiedName(parentType)).append(".");
                        });

        // Add the type's own name
        sb.append(typeDecl.getNameAsString());

        return sb.toString();
    }
}
