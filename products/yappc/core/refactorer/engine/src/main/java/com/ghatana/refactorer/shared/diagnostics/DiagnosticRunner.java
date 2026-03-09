package com.ghatana.refactorer.shared.diagnostics;

import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.codemods.CodeModificationPlan;
import io.activej.promise.Promise;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for diagnostic runners that can analyze code and produce diagnostics. 
 * @doc.type interface
 * @doc.purpose Defines the contract for diagnostic runner
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface DiagnosticRunner {
    /**
 * Returns a unique identifier for this runner. */
    String id();

    /**
     * Runs the diagnostic tool on the specified project root.
     *
     * @param projectRoot The root directory of the project to analyze
     * @return A promise that completes with the list of diagnostics
     */
    Promise<List<UnifiedDiagnostic>> run(Path projectRoot);

    /**
 * Checks if this runner is available in the current environment. */
    boolean isAvailable();

    /**
     * Plans fixes for the given diagnostics.
     *
     * @param filePath The file to analyze
     * @param diagnostics The diagnostics to plan fixes for
     * @return A promise that completes with the list of code modification plans
     */
    default Promise<List<CodeModificationPlan>> planFixes(
            Path filePath, List<UnifiedDiagnostic> diagnostics) {
        return Promise.of(List.of());
    }
}
