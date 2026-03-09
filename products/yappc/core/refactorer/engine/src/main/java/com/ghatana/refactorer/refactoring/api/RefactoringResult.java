package com.ghatana.refactorer.refactoring.api;

import com.ghatana.refactorer.refactoring.core.BaseRefactoringResult;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents the result of a refactoring operation. 
 * @doc.type interface
 * @doc.purpose Defines the contract for refactoring result
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface RefactoringResult {
    /**
 * Gets whether the refactoring was successful. */
    boolean isSuccess();

    /**
 * Gets any error message if the refactoring failed. */
    String getErrorMessage();

    /**
 * Gets the list of files that were modified by the refactoring. */
    List<Path> getModifiedFiles();

    /**
 * Gets the number of changes made by the refactoring. */
    int getChangeCount();

    /**
 * Gets a human-readable summary of the changes made. */
    String getChangeSummary();

    /**
     * Creates a successful refactoring result.
     *
     * @param modifiedFiles The list of modified files
     * @param changeCount The number of changes made
     * @param changeSummary A human-readable summary of the changes
     * @return A successful refactoring result
     */
    static RefactoringResult success(
            List<Path> modifiedFiles, int changeCount, String changeSummary) {
        return BaseRefactoringResult.success(modifiedFiles, changeCount, changeSummary);
    }

    /**
     * Creates a failed refactoring result.
     *
     * @param errorMessage The error message
     * @return A failed refactoring result
     */
    static RefactoringResult failure(String errorMessage) {
        return BaseRefactoringResult.failure(errorMessage);
    }

    /**
     * Creates a partial refactoring result.
     *
     * @param modifiedFiles The list of modified files
     * @param changeCount The number of changes made
     * @param changeSummary A human-readable summary of the changes
     * @return A partial refactoring result
     */
    static RefactoringResult partial(
            List<Path> modifiedFiles, int changeCount, String changeSummary) {
        return BaseRefactoringResult.partial(modifiedFiles, changeCount, changeSummary);
    }
}
