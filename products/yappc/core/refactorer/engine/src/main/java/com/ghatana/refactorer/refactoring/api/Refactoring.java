package com.ghatana.refactorer.refactoring.api;

/**
 * Base interface for all refactoring operations. 
 * @doc.type interface
 * @doc.purpose Defines the contract for refactoring
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface Refactoring<T extends RefactoringContext> {
    /**
 * Gets the unique identifier for this refactoring operation. */
    String getId();

    /**
 * Gets a human-readable name for this refactoring operation. */
    String getName();

    /**
 * Gets a description of what this refactoring does. */
    String getDescription();

    /**
 * Gets the class of the context type that this refactoring operates on. */
    Class<T> getContextType();

    /**
 * Checks if this refactoring can be applied to the given context. */
    boolean canApply(T context);

    /**
 * Applies the refactoring to the given context. */
    RefactoringResult apply(T context);

    /**
 * Creates a preview of the changes that would be made by this refactoring. */
    default RefactoringResult preview(T context) {
        throw new UnsupportedOperationException("Preview not supported for this refactoring");
    }
}
