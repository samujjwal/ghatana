/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.refactorer.languages.tsjs.eslint.ESLintService;
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
 * ActiveJ Promise-based implementation of LanguageService for TypeScript and JavaScript files.
 * Supports .ts, .tsx, .js, .jsx, .mjs, and .cjs file extensions. Integrates with ESLint for
 * advanced code analysis with asynchronous processing.
 *
 * @since 2.0.0 - Migrated to Promise-based API
 
 * @doc.type class
 * @doc.purpose Handles type script java script language service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class TypeScriptJavaScriptLanguageService implements LanguageService {

    private static final String LANGUAGE_ID = "typescript-javascript";
    private static final Logger logger =
            LogManager.getLogger(TypeScriptJavaScriptLanguageService.class);
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs");

    private final ESLintService eslintService;
    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Creates a new TypeScriptJavaScriptLanguageService. Reactor is resolved lazily,
     * making this safe for ServiceLoader instantiation outside an ActiveJ eventloop thread.
     */
    public TypeScriptJavaScriptLanguageService() {
        this(null, null, null);
    }

    /**
     * Constructor with PolyfixProjectContext for production use. Will initialize ESLintService if
     * context is provided.
     */
    TypeScriptJavaScriptLanguageService(PolyfixProjectContext context) {
        this(context, context != null ? createESLintService(context) : null, Reactor.getCurrentReactor());
    }

    /**
     * Constructor that accepts a pre-configured ESLintService. Primarily for testing purposes.
     */
    TypeScriptJavaScriptLanguageService(
            PolyfixProjectContext context, ESLintService eslintService) {
        this(context, eslintService, Reactor.getCurrentReactor());
    }

    /**
     * Full constructor with Reactor support.
     *
     * @param context the project context
     * @param eslintService optional ESLint service for advanced analysis
     * @param reactor the Reactor to use for async operations
     */
    TypeScriptJavaScriptLanguageService(
            PolyfixProjectContext context, ESLintService eslintService, Reactor reactor) {
        this.eslintService = eslintService;
        this.reactor = reactor;
        if (context != null && this.eslintService != null) {
            logger.info("ESLintService initialized successfully");
        }
    }

    /**
     * Factory method for creating an ESLintService instance. Can be overridden in tests to provide
     * a mock implementation.
     */
    protected static ESLintService createESLintService(PolyfixProjectContext context) {
        try {
            return new ESLintService(context);
        } catch (Exception e) {
            LogManager.getLogger(TypeScriptJavaScriptLanguageService.class)
                    .warn(
                            "Failed to initialize ESLintService. ESLint analysis will be disabled.",
                            e);
            return null;
        }
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
        if (file == null) return false;
        String fileName = file.getFileName().toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> diagnose(
            PolyfixProjectContext ctx, List<Path> files) {
        // Filter for TypeScript/JavaScript files only
        List<Path> tsJsFiles = files.stream().filter(this::supports).collect(Collectors.toList());

        if (tsJsFiles.isEmpty()) {
            return Promise.of(List.of());
        }

        logger.info(
                "Running TypeScript/JavaScript diagnostics on {} files asynchronously",
                tsJsFiles.size());

        // Process files in parallel using Promise.toList for better performance
        Promise<List<UnifiedDiagnostic>> basicDiagnostics =
                Promises.toList(tsJsFiles.stream()
                                .map(this::diagnoseFile)
                                .collect(Collectors.toList()))
                        .map(listOfLists -> listOfLists.stream()
                                .flatMap(List::stream)
                                .collect(Collectors.toList()));

        // Run ESLint analysis if the service was initialized
        if (eslintService != null) {
            return basicDiagnostics.then(basic -> runESLintAnalysis(tsJsFiles).map(eslint -> {
                List<UnifiedDiagnostic> combined = new ArrayList<>(basic);
                combined.addAll(eslint);
                return combined;
            }));
        }

        return basicDiagnostics;
    }

    /**
     * Diagnoses a single TypeScript/JavaScript file asynchronously.
     *
     * @param file the file to diagnose
     * @return a Promise resolving to a list of diagnostics for this file
     */
    private Promise<List<UnifiedDiagnostic>> diagnoseFile(Path file) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            List<UnifiedDiagnostic> diagnostics = new ArrayList<>();

            try {
                // Simple check for empty files
                if (Files.size(file) == 0) {
                    diagnostics.add(
                            createDiagnostic(
                                    file,
                                    "Empty TypeScript/JavaScript source file",
                                    "The file is empty",
                                    1,
                                    1,
                                    "tsjs.empty_file"));
                    return diagnostics;
                }

                // Check for syntax errors by attempting to read the file
                try {
                    String content = Files.readString(file);
                    if (content.trim().isEmpty()) {
                        diagnostics.add(
                                createDiagnostic(
                                        file,
                                        "Empty TypeScript/JavaScript source file",
                                        "The file contains only whitespace",
                                        1,
                                        1,
                                        "tsjs.empty_file"));
                    }
                } catch (Exception e) {
                    // If we can't read the file, it's likely a syntax error
                    diagnostics.add(
                            createDiagnostic(
                                    file,
                                    "Error reading TypeScript/JavaScript file: " + e.getMessage(),
                                    "Syntax error or invalid file encoding: " + e.getMessage(),
                                    1,
                                    1,
                                    "tsjs.syntax_error"));
                }

            } catch (IOException e) {
                logger.error("Error analyzing TypeScript/JavaScript file: " + file, e);
                diagnostics.add(
                        createDiagnostic(
                                file,
                                "Error analyzing TypeScript/JavaScript file: " + e.getMessage(),
                                "Error accessing file: " + e.getMessage(),
                                1,
                                1,
                                "tsjs.error"));
            }

            return diagnostics;
        });
    }

    /**
     * Runs ESLint analysis asynchronously on the provided files.
     *
     * @param tsJsFiles files to analyze with ESLint
     * @return a Promise resolving to ESLint diagnostics
     */
    private Promise<List<UnifiedDiagnostic>> runESLintAnalysis(List<Path> tsJsFiles) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                logger.debug("Running ESLint analysis on {} files", tsJsFiles.size());
                List<UnifiedDiagnostic> eslintDiagnostics = eslintService.analyze(tsJsFiles);
                if (eslintDiagnostics != null && !eslintDiagnostics.isEmpty()) {
                    logger.debug(
                            "ESLint analysis completed with {} issues", eslintDiagnostics.size());
                    return eslintDiagnostics;
                }
                return List.of();
            } catch (Exception e) {
                logger.warn("Failed to run ESLint analysis: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    @Override
    public Promise<List<FixAction>> planFixes(
            UnifiedDiagnostic diagnostic, PolyfixProjectContext context) {
        // NOTE: Implement TypeScript/JavaScript-specific fix planning based on diagnostic codes
        // This is a placeholder implementation
        return Promise.of(new ArrayList<>());
    }

    private UnifiedDiagnostic createDiagnostic(
            Path file, String message, String description, int line, int column, String code) {
        return new UnifiedDiagnostic(
                "tsjs", // tool
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
