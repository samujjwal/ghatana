package com.ghatana.refactorer.refactoring.impl.java;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.model.JavaElement;
import com.ghatana.refactorer.refactoring.model.JavaElementType;
import com.ghatana.refactorer.refactoring.util.JavaParserUtil;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java implementation of {@link RenameRefactoring}. 
 * @doc.type class
 * @doc.purpose Handles java rename refactoring operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class JavaRenameRefactoring implements RenameRefactoring {
    private static final Logger log = LoggerFactory.getLogger(JavaRenameRefactoring.class);
    private static final String ID = "java.rename";
    private static final String NAME = "Java Rename Refactoring";
    private static final String DESCRIPTION =
            "Renames Java classes, methods, fields, and variables";

    private final JavaParser javaParser;

    public JavaRenameRefactoring() {
        this.javaParser = new JavaParser();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Renames a Java class, method, field, or variable";
    }

    @Override
    public Class<Context> getContextType() {
        return Context.class;
    }

    public boolean canApply(Context context) {
        try {
            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile) || !sourceFile.toString().endsWith(".java")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Error checking if rename can be applied", e);
            return false;
        }
    }

    @Override
    public RefactoringResult apply(Context context) {
        try {
            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile)) {
                return RefactoringResult.failure("Source file does not exist: " + sourceFile);
            }

            // Parse the Java file
            CompilationUnit cu =
                    javaParser
                            .parse(sourceFile)
                            .getResult()
                            .orElseThrow(
                                    () ->
                                            new IOException(
                                                    "Failed to parse Java file: " + sourceFile));

            // Find the element to rename
            JavaElement element = findElementToRename(cu, context);
            if (element == null) {
                return RefactoringResult.failure(
                        "Could not find element to rename in " + sourceFile);
            }

            // Create a visitor to rename the element
            RenameVisitor visitor = new RenameVisitor(element, context.getNewName());
            cu.accept(visitor, null);

            // If no changes were made, return early
            if (!visitor.wasChanged()) {
                return RefactoringResult.success(
                        List.of(sourceFile),
                        0,
                        "No changes were made (element not found or name unchanged)");
            }

            // Write the changes back to the file
            if (!context.isDryRun()) {
                String newContent = cu.toString();
                Files.writeString(
                        sourceFile,
                        newContent,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

            // Return the result
            return RefactoringResult.success(
                    List.of(sourceFile),
                    visitor.getChangeCount(),
                    String.format(
                            "Renamed %s from '%s' to '%s'",
                            element.getType().name().toLowerCase(),
                            element.getName(),
                            context.getNewName()));
        } catch (Exception e) {
            log.error("Error during Java rename refactoring", e);
            return RefactoringResult.failure("Failed to perform rename: " + e.getMessage());
        }
    }

    private JavaElement findElementToRename(CompilationUnit cu, Context context) {
        JavaElementType type = JavaElementType.fromString(context.getElementType());
        if (type == null) {
            return null;
        }

        Optional<? extends Node> nodeOpt =
                switch (type) {
                    case CLASS ->
                            cu.getClassByName(context.getOldName())
                                    .or(() -> cu.getInterfaceByName(context.getOldName()))
                                    .map(n -> (Node) n);
                    case METHOD ->
                            JavaParserUtil.findMethod(
                                    cu, context.getOldName(), context.getLineNumber());
                    case FIELD ->
                            JavaParserUtil.findField(
                                    cu, context.getOldName(), context.getLineNumber());
                    case VARIABLE ->
                            JavaParserUtil.findVariable(
                                    cu, context.getOldName(), context.getLineNumber());
                    default -> Optional.empty();
                };

        return nodeOpt.map(node -> new JavaElement(getElementName(node), type, node)).orElse(null);
    }

    private String getElementName(Node node) {
        if (node instanceof ClassOrInterfaceDeclaration) {
            return ((ClassOrInterfaceDeclaration) node).getNameAsString();
        } else if (node instanceof MethodDeclaration) {
            return ((MethodDeclaration) node).getNameAsString();
        } else if (node instanceof FieldDeclaration) {
            return ((FieldDeclaration) node).getVariables().get(0).getNameAsString();
        } else if (node instanceof VariableDeclarator) {
            return ((VariableDeclarator) node).getNameAsString();
        }
        return "";
    }

    @Override
    public boolean isNewNameValid(String newName) {
        // Basic validation - can be enhanced with Java identifier rules
        return newName != null
                && !newName.trim().isEmpty()
                && newName.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    }

    /**
 * Visitor that renames Java elements. */
    private static class RenameVisitor extends ModifierVisitor<Void> {
        private final JavaElement targetElement;
        private final String newName;
        private boolean changed = false;
        private int changeCount = 0;

        public RenameVisitor(JavaElement targetElement, String newName) {
            this.targetElement = targetElement;
            this.newName = newName;
        }

        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            if (isTargetElement(n, JavaElementType.CLASS)) {
                n.setName(newName);
                changed = true;
                changeCount++;
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            if (isTargetElement(n, JavaElementType.METHOD)) {
                n.setName(newName);
                changed = true;
                changeCount++;
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(FieldDeclaration n, Void arg) {
            if (isTargetElement(n, JavaElementType.FIELD)) {
                n.getVariables().get(0).setName(newName);
                changed = true;
                changeCount++;
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(VariableDeclarator n, Void arg) {
            if (isTargetElement(n, JavaElementType.VARIABLE)) {
                // Rename the variable declaration
                n.setName(newName);
                changed = true;
                changeCount++;

                // Also visit the initializer to rename any usages
                if (n.getInitializer().isPresent()) {
                    n.getInitializer().get().accept(this, arg);
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(com.github.javaparser.ast.expr.NameExpr n, Void arg) {
            // Handle variable usages in expressions
            if (targetElement.getType() == JavaElementType.VARIABLE
                    && n.getNameAsString().equals(targetElement.getName())) {
                n.setName(newName);
                changed = true;
                changeCount++;
            }
            return super.visit(n, arg);
        }

        private boolean isTargetElement(Node node, JavaElementType expectedType) {
            if (targetElement.getType() != expectedType) {
                return false;
            }

            // For methods, we need to check the name and parameters
            if (node instanceof MethodDeclaration) {
                MethodDeclaration method = (MethodDeclaration) node;
                return method.getNameAsString().equals(targetElement.getName());
            }

            // For other elements, just check the name
            return getElementName(node).equals(targetElement.getName());
        }

        private String getElementName(Node node) {
            if (node instanceof ClassOrInterfaceDeclaration) {
                return ((ClassOrInterfaceDeclaration) node).getNameAsString();
            } else if (node instanceof MethodDeclaration) {
                return ((MethodDeclaration) node).getNameAsString();
            } else if (node instanceof FieldDeclaration) {
                return ((FieldDeclaration) node).getVariables().get(0).getNameAsString();
            } else if (node instanceof VariableDeclarator) {
                return ((VariableDeclarator) node).getNameAsString();
            }
            return "";
        }

        public boolean wasChanged() {
            return changed;
        }

        public int getChangeCount() {
            return changeCount;
        }
    }
}
