/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.shared.FixAction;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.reactor.Reactor;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ActiveJ Promise-based implementation of LanguageService for Python files. Supports .py, .pyi,
 * and .pyw file extensions with asynchronous processing.
 *
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.type class
 * @doc.purpose Handles python language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class PythonLanguageService implements LanguageService {

    private static final String LANGUAGE_ID = "python";
    private static final Logger LOGGER = LogManager.getLogger(PythonLanguageService.class);
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".py", ".pyi", ".pyw");

    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Creates a new PythonLanguageService with the current thread's Reactor.
     */
    public PythonLanguageService() {
        this.reactor = null; // Lazy init for ServiceLoader compatibility
    }

    /**
     * Creates a new PythonLanguageService with the specified Reactor.
     *
     * @param reactor the Reactor to use for async operations
     */
    public PythonLanguageService(Reactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public List<String> getSupportedFileExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
    public String id() {
        return LANGUAGE_ID;
    }

    @Override
    public boolean supports(Path file) {
        if (file == null) {
            return false;
        }
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> diagnose(
            PolyfixProjectContext ctx, List<Path> files) {
        // Filter for Python files only
        List<Path> pythonFiles = files.stream().filter(this::supports).collect(Collectors.toList());

        if (pythonFiles.isEmpty()) {
            return Promise.of(List.of());
        }

        LOGGER.info("Running Python diagnostics on {} files asynchronously", pythonFiles.size());

        // Process files in parallel using Promise.toList for better performance
        return Promises.toList(pythonFiles.stream()
                        .map(this::diagnoseFile)
                        .collect(Collectors.toList()))
                .map(listOfLists ->
                        listOfLists.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    /**
     * Diagnoses a single Python file asynchronously.
     *
     * @param pythonFile the file to diagnose
     * @return a Promise resolving to a list of diagnostics for this file
     */
    private Promise<List<UnifiedDiagnostic>> diagnoseFile(Path pythonFile) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

            try {
                // Simple check for empty files
                if (Files.size(pythonFile) == 0) {
                    diagnostics.add(
                            createDiagnostic(
                                    pythonFile,
                                    "Empty Python source file",
                                    "The file is empty",
                                    1,
                                    1,
                                    "python.empty_file"));
                }
                // NOTE: Add more sophisticated Python code analysis using a Python parser

            } catch (IOException e) {
                LOGGER.error("Error analyzing Python file: " + pythonFile, e);
                diagnostics.add(
                        createDiagnostic(
                                pythonFile,
                                "Error analyzing Python file: " + e.getMessage(),
                                "Error analyzing file: " + e.getMessage(),
                                1,
                                1,
                                "python.error"));
            }

            return diagnostics;
        });
    }

    @Override
    public Promise<List<FixAction>> planFixes(
            UnifiedDiagnostic diagnostic, PolyfixProjectContext context) {
        // NOTE: Implement Python-specific fix planning based on diagnostic codes
        // This is a placeholder implementation
        return Promise.of(new ArrayList<>());
    }

    private UnifiedDiagnostic createDiagnostic(
            Path file, String message, String description, int line, int column, String code) {
        return new UnifiedDiagnostic(
                "python", // tool
                code, // rule
                message, // message
                file.toString(), // file
                line, // startLine
                column, // startColumn
                Severity.ERROR, // severity
                new HashMap<>(Map.of("description", description)) // meta
                );
    }
}
