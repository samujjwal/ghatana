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
 * ActiveJ Promise-based implementation of LanguageService for Java files. Provides support for
 * asynchronous Java source code analysis and fixes.
 *
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.type class
 * @doc.purpose Handles java language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class JavaLanguageService implements LanguageService {

    private static final String LANGUAGE_ID = "java";
    private static final Logger LOGGER = LogManager.getLogger(JavaLanguageService.class);
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".java");

    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Creates a new JavaLanguageService. The Reactor is resolved lazily from the
     * current thread when async operations are invoked, making this safe for
     * ServiceLoader instantiation outside an ActiveJ eventloop thread.
     */
    public JavaLanguageService() {
        this.reactor = null;
    }

    /**
     * Creates a new JavaLanguageService with the specified Reactor.
     *
     * @param reactor the Reactor to use for async operations
     */
    public JavaLanguageService(Reactor reactor) {
        this.reactor = reactor;
    }

    /**
     * Returns the Reactor, resolving lazily from the current thread if not set at construction.
     */
    private Reactor getReactor() {
        Reactor r = this.reactor;
        if (r == null) {
            r = Reactor.getCurrentReactor();
            this.reactor = r;
        }
        return r;
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
        // Filter for Java files only
        List<Path> javaFiles = files.stream().filter(this::supports).collect(Collectors.toList());

        if (javaFiles.isEmpty()) {
            return Promise.of(List.of());
        }

        LOGGER.info("Running Java diagnostics on {} files asynchronously", javaFiles.size());

        // Process files in parallel using Promise.toList for better performance
        return Promises.toList(javaFiles.stream().map(this::diagnoseFile).collect(Collectors.toList()))
                .map(listOfLists ->
                        listOfLists.stream().flatMap(List::stream).collect(Collectors.toList()));
    }

    /**
     * Diagnoses a single Java file asynchronously.
     *
     * @param javaFile the file to diagnose
     * @return a Promise resolving to a list of diagnostics for this file
     */
    private Promise<List<UnifiedDiagnostic>> diagnoseFile(Path javaFile) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

            try {
                // Simple check for empty files
                if (Files.size(javaFile) == 0) {
                    diagnostics.add(
                            createDiagnostic(
                                    javaFile,
                                    "Empty Java source file",
                                    "The file is empty",
                                    1,
                                    1,
                                    "java.empty_file"));
                }
                // NOTE: Add more sophisticated Java code analysis using JavaParser or similar

            } catch (IOException e) {
                LOGGER.error("Error analyzing Java file: " + javaFile, e);
                diagnostics.add(
                        createDiagnostic(
                                javaFile,
                                "Error analyzing Java file: " + e.getMessage(),
                                "Error analyzing file: " + e.getMessage(),
                                1,
                                1,
                                "java.error"));
            }

            return diagnostics;
        });
    }

    @Override
    public Promise<List<FixAction>> planFixes(
            UnifiedDiagnostic diagnostic, PolyfixProjectContext context) {
        // NOTE: Implement Java-specific fix planning based on diagnostic codes
        // This is a placeholder implementation
        return Promise.of(new ArrayList<>());
    }

    private UnifiedDiagnostic createDiagnostic(
            Path file, String message, String description, int line, int column, String code) {
        return new UnifiedDiagnostic(
                "java", // tool
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
