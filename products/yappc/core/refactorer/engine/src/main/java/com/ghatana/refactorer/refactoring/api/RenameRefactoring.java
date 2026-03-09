package com.ghatana.refactorer.refactoring.api;

/**
 * Interface for refactoring operations that rename program elements. 
 * @doc.type interface
 * @doc.purpose Defines the contract for rename refactoring
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface RenameRefactoring extends Refactoring<RenameRefactoring.Context> {
    /**
 * Context specific to rename refactoring operations. */
    interface Context extends RefactoringContext {
        /**
 * Gets the fully qualified name of the element to be renamed. */
        String getOldName();

        /**
 * Gets the new name for the element. */
        String getNewName();

        /**
 * Gets the type of element being renamed (e.g., "class", "method", "variable"). */
        String getElementType();

        /**
 * Gets the source file containing the element to be renamed. */
        String getSourceFile();

        /**
 * Gets the line number where the element is defined. */
        int getLineNumber();

        /**
 * Gets the column number where the element is defined. */
        int getColumnNumber();
    }

    /**
 * Checks if the new name is valid for the element being renamed. */
    default boolean isNewNameValid(String newName) {
        // Default implementation allows any non-empty name without spaces
        return newName != null && !newName.trim().isEmpty() && !newName.matches(".*\\s+.*");
    }
}
