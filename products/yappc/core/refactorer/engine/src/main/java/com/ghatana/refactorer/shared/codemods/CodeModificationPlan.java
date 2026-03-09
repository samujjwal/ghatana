package com.ghatana.refactorer.shared.codemods;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a plan for modifying source code to fix issues. 
 * @doc.type interface
 * @doc.purpose Defines the contract for code modification plan
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface CodeModificationPlan {

    /**
 * Gets the file path that this plan will modify. */
    Path getFilePath();

    /**
 * Gets the list of code actions that will be performed. */
    List<CodeAction> getActions();

    /**
 * Gets the list of diagnostics that will be fixed by this plan. */
    List<UnifiedDiagnostic> getFixedDiagnostics();

    /**
 * Checks if this plan is empty (contains no actions). */
    boolean isEmpty();
}
