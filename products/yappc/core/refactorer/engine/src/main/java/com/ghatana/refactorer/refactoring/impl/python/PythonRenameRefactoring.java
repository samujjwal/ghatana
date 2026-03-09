package com.ghatana.refactorer.refactoring.impl.python;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import com.ghatana.refactorer.refactoring.model.PythonElement;
import com.ghatana.refactorer.refactoring.model.PythonElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python implementation of {@link RenameRefactoring}. 
 * @doc.type class
 * @doc.purpose Handles python rename refactoring operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class PythonRenameRefactoring implements RenameRefactoring {
    private static final Logger log = LoggerFactory.getLogger(PythonRenameRefactoring.class);
    private static final String ID = "python.rename";
    private static final String NAME = "Python Rename Refactoring";
    private static final String DESCRIPTION =
            "Renames Python modules, classes, functions, and variables";

    private final PythonInterpreter interpreter;
    private final boolean isPythonAvailable;

    public PythonRenameRefactoring() {
        this.interpreter = createPythonInterpreter();
        this.isPythonAvailable = interpreter != null;

        if (isPythonAvailable) {
            initializePythonEnvironment();
        } else {
            log.warn("Python environment not available. Python refactoring will be disabled.");
        }
    }

    private PythonInterpreter createPythonInterpreter() {
        try {
            return new PythonInterpreter();
        } catch (Exception e) {
            log.warn("Failed to create Python interpreter", e);
            return null;
        }
    }

    private void initializePythonEnvironment() {
        if (!isPythonAvailable) {
            return;
        }

        try {
            // Set up Python path and import required modules
            interpreter.exec("import ast");
            interpreter.exec("import astor");
        } catch (Exception e) {
            log.error("Failed to initialize Python environment", e);
            throw new IllegalStateException("Python environment initialization failed", e);
        }
    }

    private boolean isSupported() {
        return isPythonAvailable && interpreter != null;
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
        return DESCRIPTION;
    }

    @Override
    public Class<Context> getContextType() {
        return Context.class;
    }

    @Override
    public boolean canApply(Context context) {
        if (!isSupported()) {
            log.warn("Python refactoring is not supported on this platform");
            return false;
        }

        try {
            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile) || !sourceFile.toString().endsWith(".py")) {
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
        if (!isSupported()) {
            return RefactoringResult.failure(
                    "Python refactoring is not supported on this platform");
        }

        try {
            Path sourceFile = Path.of(context.getSourceFile());
            if (!Files.exists(sourceFile)) {
                return RefactoringResult.failure("Source file does not exist: " + sourceFile);
            }

            // Read the Python file
            String sourceCode = Files.readString(sourceFile);

            // Parse the Python code to an AST
            interpreter.set("source_code", sourceCode);
            interpreter.exec("tree = ast.parse(source_code)");

            // Find the element to rename
            PythonElement element = findElementToRename(context);
            if (element == null) {
                return RefactoringResult.failure(
                        "Could not find element to rename in " + sourceFile);
            }

            // Execute the refactoring in Python
            String script =
                    String.format(
                            """
                import ast
                import astor

                class RenameVisitor(ast.NodeTransformer):
                    def __init__(self, old_name, new_name, element_type, line_no):
                        self.old_name = old_name
                        self.new_name = new_name
                        self.element_type = element_type
                        self.line_no = line_no
                        self.modified = False

                    def visit_%s(self, node):
                        if (hasattr(node, 'name') and node.name == self.old_name and
                            (self.line_no is None or getattr(node, 'lineno', None) == self.line_no)):
                            node.name = self.new_name
                            self.modified = True
                        return self.generic_visit(node)

                visitor = RenameVisitor('%s', '%s', '%s', %d)
                new_tree = visitor.visit(tree)

                if not isinstance(new_tree, ast.AST):
                    new_tree = tree

                modified_code = astor.to_source(new_tree) if visitor.modified else None
                """,
                            getVisitorMethodName(element.getType()),
                            element.getName().replace("'", "\\'"),
                            context.getNewName().replace("'", "\\'"),
                            element.getType().name().toLowerCase(),
                            context.getLineNumber());

            interpreter.exec(script);

            // Check if the code was modified
            PyObject modifiedCode = interpreter.get("modified_code");
            if (modifiedCode == null || modifiedCode.toString().equals("None")) {
                return RefactoringResult.success(
                        List.of(sourceFile),
                        0,
                        "No changes were made (element not found or name unchanged)");
            }

            // Get the modified code
            String newCode = modifiedCode.toString();

            // Write the changes back to the file
            if (!context.isDryRun()) {
                Files.writeString(
                        sourceFile,
                        newCode,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

            // Return the result
            return RefactoringResult.success(
                    List.of(sourceFile),
                    1, // At least one change was made
                    String.format(
                            "Renamed %s from '%s' to '%s'",
                            element.getType().name().toLowerCase(),
                            element.getName(),
                            context.getNewName()));
        } catch (Exception e) {
            log.error("Error during Python rename refactoring", e);
            return RefactoringResult.failure("Failed to perform rename: " + e.getMessage());
        }
    }

    private String getVisitorMethodName(PythonElementType type) {
        return switch (type) {
            case MODULE -> "Module";
            case CLASS -> "ClassDef";
            case FUNCTION, METHOD -> "FunctionDef";
            case VARIABLE -> "Name";
            case PARAMETER -> "arg";
            case IMPORT -> "alias";
            case ATTRIBUTE -> "Attribute";
            default -> throw new IllegalArgumentException("Unsupported element type: " + type);
        };
    }

    private PythonElement findElementToRename(Context context) {
        PythonElementType type = PythonElementType.fromString(context.getElementType());
        if (type == null) {
            return null;
        }

        // For simplicity, we'll just create a basic element with the name and type
        // In a real implementation, we would parse the Python code and find the actual element
        return new PythonElement(
                context.getOldName(),
                type,
                null // AST node would be set here in a real implementation
                );
    }

    @Override
    public boolean isNewNameValid(String newName) {
        // Basic Python identifier validation
        return newName != null
                && !newName.trim().isEmpty()
                && newName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
