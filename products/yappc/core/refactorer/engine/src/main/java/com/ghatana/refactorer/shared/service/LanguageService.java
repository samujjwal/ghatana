/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared.service;

import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import io.activej.promise.Promise;
import java.nio.file.Path;
import java.util.List;

/**
 * Language-specific diagnostic and fix service interface using ActiveJ Promise-based async operations.
 * 
 * <p>All methods return {@link Promise} to support non-blocking I/O and parallel processing.
 * Implementations should use {@code Promises.runBlocking(reactor, () -> ...)} for blocking operations.
 * 
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.type interface
 * @doc.purpose Defines the contract for language service
 * @doc.layer core
 * @doc.pattern Service
*/
public interface LanguageService {
    String id();

    boolean supports(Path file);

    /**
     * Asynchronously diagnose files and return a list of diagnostics.
     * 
     * @param context the project context containing configuration and resources
     * @param files the list of files to diagnose
     * @return a Promise resolving to a list of unified diagnostics
     */
    Promise<List<UnifiedDiagnostic>> diagnose(PolyfixProjectContext context, List<Path> files);

    /**
     * Asynchronously plan fixes for a given diagnostic.
     * 
     * @param diagnostic the diagnostic to fix
     * @param context the project context
     * @return a Promise resolving to a list of fix actions
     */
    default Promise<List<FixAction>> planFixes(UnifiedDiagnostic diagnostic, PolyfixProjectContext context) {
        return Promise.of(List.of());
    }

    /**
     * Returns the list of file extensions supported by this language service. Extensions should be
     * in the format ".ext" (with leading dot).
     *
     * @return List of supported file extensions
     */
    List<String> getSupportedFileExtensions();
}
