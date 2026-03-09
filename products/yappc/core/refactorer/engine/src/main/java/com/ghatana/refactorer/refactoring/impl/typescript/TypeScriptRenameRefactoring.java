package com.ghatana.refactorer.refactoring.impl.typescript;

import com.ghatana.refactorer.refactoring.api.RefactoringResult;
import com.ghatana.refactorer.refactoring.api.RenameRefactoring;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TypeScript/JavaScript implementation of {@link RenameRefactoring}. This implementation requires
 * J2V8 native library to be available at runtime. If J2V8 is not available, the refactoring
 * operations will fail gracefully.
 
 * @doc.type class
 * @doc.purpose Handles type script rename refactoring operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public class TypeScriptRenameRefactoring implements RenameRefactoring, AutoCloseable {

    @Override
    public Class<Context> getContextType() {
        return Context.class;
    }

    private static final Logger log = LoggerFactory.getLogger(TypeScriptRenameRefactoring.class);
    private static final String ID = "typescript.rename";
    private static final String NAME = "TypeScript/JavaScript Rename Refactoring";
    private static final String DESCRIPTION =
            "Renames TypeScript/JavaScript variables, functions, classes, and other symbols";

    private static final boolean IS_AVAILABLE = checkV8Availability();

    private static boolean checkV8Availability() {
        try {
            Class.forName("com.eclipsesource.v8.V8");
            log.info("J2V8 found. TypeScript refactoring is available on this platform.");
            return true;
        } catch (ClassNotFoundException e) {
            log.warn("J2V8 not found. TypeScript refactoring will be disabled.");
            return false;
        }
    }

    /**
     * Checks if TypeScript refactoring is supported on this platform.
     *
     * @return true if TypeScript refactoring is available, false otherwise
     */
    public static boolean isSupported() {
        return IS_AVAILABLE;
    }

    /**
 * Constructor for TypeScriptRenameRefactoring. Will log a warning if J2V8 is not available. */
    public TypeScriptRenameRefactoring() {
        if (!IS_AVAILABLE) {
            log.warn("TypeScript refactoring is not available on this platform (J2V8 not found)");
        }
    }

    @Override
    public boolean canApply(Context context) {
        if (!isSupported()) {
            log.warn("TypeScript refactoring is not supported on this platform");
            return false;
        }

        // Check if the file has a TypeScript/JavaScript extension
        Path sourceFile = Path.of(context.getSourceFile());
        String fileName = sourceFile.getFileName().toString();
        return fileName.endsWith(".ts")
                || fileName.endsWith(".js")
                || fileName.endsWith(".tsx")
                || fileName.endsWith(".jsx");
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
    public RefactoringResult preview(Context context) {
        // For now, just return the same result as apply()
        // In a real implementation, this would show a preview of changes
        return apply(context);
    }

    @Override
    public RefactoringResult apply(Context context) {
        if (!isSupported()) {
            return RefactoringResult.failure(
                    "TypeScript refactoring is not supported on this platform");
        }

        Path sourceFile = Path.of(context.getSourceFile());
        if (!Files.exists(sourceFile)) {
            return RefactoringResult.failure("Source file does not exist: " + sourceFile);
        }

        try {
            // In a real implementation, this would use J2V8 to perform the refactoring
            // For now, we'll just return a successful result with no changes
            return RefactoringResult.success(
                    List.of(sourceFile), 0, "TypeScript refactoring is not fully implemented yet");
        } catch (Exception e) {
            log.error("Error during TypeScript refactoring", e);
            return RefactoringResult.failure(
                    "Failed to apply TypeScript refactoring: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        // Clean up any resources if needed
        log.debug("Closing TypeScript refactoring resources");
    }

    /**
     * Validates if the new name is a valid TypeScript/JavaScript identifier.
     *
     * @param newName The name to validate
     * @return true if the name is valid, false otherwise
     */
    public boolean isNewNameValid(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        // Basic TypeScript/JavaScript identifier validation
        return newName.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$");
    }

    /**
     * Clean up any resources used by this refactoring. This is an alias for close() to maintain
     * backward compatibility.
     */
    public void cleanup() {
        close();
    }
}
