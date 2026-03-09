package com.ghatana.refactorer.refactoring.api;

/**
 * Interface for refactoring operations that move code elements between files or packages. 
 * @doc.type interface
 * @doc.purpose Defines the contract for move refactoring
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface MoveRefactoring extends Refactoring<MoveRefactoring.Context> {

    /**
 * Context specific to move refactoring operations. */
    interface Context extends RefactoringContext {
        /**
 * Gets the fully qualified name of the element to be moved. */
        String getElementName();

        /**
 * Gets the type of element being moved (e.g., "class", "function", "variable"). */
        String getElementType();

        /**
 * Gets the source file containing the element to be moved. */
        String getSourceFile();

        /**
 * Gets the target file or package where the element should be moved. */
        String getTargetLocation();

        /**
 * Gets the new fully qualified name for the element after moving. */
        String getNewName();

        /**
 * Gets whether to update references to the moved element. */
        boolean shouldUpdateReferences();

        /**
 * Gets whether to move related files (e.g., test files, resource files). */
        boolean shouldMoveRelatedFiles();
    }

    /**
 * Checks if the target location is valid for the element being moved. */
    boolean isTargetLocationValid(String targetLocation);
}
