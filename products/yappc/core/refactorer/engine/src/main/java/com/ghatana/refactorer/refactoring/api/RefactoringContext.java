package com.ghatana.refactorer.refactoring.api;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.Set;

/**
 * Context for a refactoring operation, providing access to project state and configuration. 
 * @doc.type interface
 * @doc.purpose Defines the contract for refactoring context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface RefactoringContext {
    /**
 * Gets the project context. */
    PolyfixProjectContext getPolyfixProjectContext();

    /**
 * Gets the root directory of the project being refactored. */
    Path getProjectRoot();

    /**
 * Gets the set of files that will be affected by the refactoring. */
    Set<Path> getAffectedFiles();

    /**
 * Gets a value indicating whether the refactoring should be performed in a dry-run mode. */
    boolean isDryRun();

    /**
 * Gets a value indicating whether the refactoring should be performed interactively. */
    boolean isInteractive();
}
