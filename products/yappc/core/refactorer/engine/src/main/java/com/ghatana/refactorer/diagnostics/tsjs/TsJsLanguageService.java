package com.ghatana.refactorer.diagnostics.tsjs;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.codemods.CodeAction;
import com.ghatana.refactorer.shared.codemods.CodeModificationPlan;
import com.ghatana.refactorer.shared.diagnostics.DiagnosticRunner;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.reactor.Reactor;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Temporary implementation of TsMorphPlan since we can't access it from the codemods module
/**
 * @doc.type class
 * @doc.purpose Handles ts morph plan operations
 * @doc.layer core
 * @doc.pattern Implementation
 */
class TsMorphPlan implements CodeModificationPlan {
    private final Path filePath;
    private final List<CodeAction> actions = new ArrayList<>();
    private final List<UnifiedDiagnostic> fixedDiagnostics = new ArrayList<>();

    public TsMorphPlan(Path filePath) {
        this.filePath = filePath;
    }

    public void addImport(
            String moduleSpecifier,
            List<String> namedImports,
            String defaultImport,
            boolean isTypeOnly,
            UnifiedDiagnostic diagnostic) {
        // Create a simple action for the import
        String actionDesc = String.format(
                "Add import from '%s'%s",
                moduleSpecifier,
                defaultImport != null ? " (default: " + defaultImport + ")" : "");
        actions.add(new CodeAction(actionDesc, "add-import"));
        fixedDiagnostics.add(diagnostic);
    }

    @Override
    public Path getFilePath() {
        return filePath;
    }

    @Override
    public List<CodeAction> getActions() {
        return new ArrayList<>(actions);
    }

    @Override
    public List<UnifiedDiagnostic> getFixedDiagnostics() {
        return fixedDiagnostics;
    }

    @Override
    public boolean isEmpty() {
        return actions.isEmpty();
    }
}

/**
 * ActiveJ Promise-based language service for TypeScript/JavaScript that combines TSC and ESLint
 * diagnostics.
 *
 * @doc.type service
 * @doc.language typescript,javascript
 * @doc.tools tsc,eslint
 * @since 2.0.0 - Migrated to Promise-based API
 */
public class TsJsLanguageService implements DiagnosticRunner {
    private static final String ID = "tsjs";
    private static final Logger logger = LogManager.getLogger(TsJsLanguageService.class);

    private final TscRunner tscRunner;
    private final EslintRunner eslintRunner;
    private volatile Reactor reactor;
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    @SuppressWarnings("unused")
    private final PolyfixProjectContext context;

    /**
     * Creates a new TsJsLanguageService with the current thread's Reactor.
     */
    public TsJsLanguageService(PolyfixProjectContext context) {
        this(context, Reactor.getCurrentReactor());
    }

    /**
     * Creates a new TsJsLanguageService with the specified Reactor.
     *
     * @param context the project context
     * @param reactor the Reactor to use for async operations
     */
    public TsJsLanguageService(PolyfixProjectContext context, Reactor reactor) {
        this.context = context;
        this.reactor = reactor;
        this.tscRunner = new TscRunner(context);
        this.eslintRunner = new EslintRunner(context);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Promise<List<UnifiedDiagnostic>> run(Path projectRoot) {
        logger.info("Running TypeScript/JavaScript diagnostics on: {}", projectRoot);

        // Run TSC asynchronously (TscRunner now returns Promise directly)
        Promise<List<UnifiedDiagnostic>> tscPromise = tscRunner.run(projectRoot)
                .map(result -> result, e -> {
                    logger.error("Error running TSC diagnostics", e);
                    return List.of();
                });

        // Configure and run ESLint asynchronously
        Promise<List<UnifiedDiagnostic>> eslintPromise = Promise.of(List.of());

        if (eslintRunner.isAvailable()) {
            // Configure ESLint with appropriate settings
            eslintRunner
                    .withFix(false) // Don't auto-fix during diagnostics
                    .withIncludePatterns(List.of("**/*.{js,jsx,ts,tsx}"))
                    .withIgnorePatterns(List.of("**/node_modules/**", "**/dist/**"));

            eslintPromise = eslintRunner.run(projectRoot)
                    .map(result -> result, e -> {
                        logger.error("Error running ESLint", e);
                        return List.of();
                    });
        }

        // Combine results when both complete
        return Promises.toList(tscPromise, eslintPromise)
                .map(results -> {
                    List<UnifiedDiagnostic> allDiags = new ArrayList<>();
                    allDiags.addAll(results.get(0));
                    allDiags.addAll(results.get(1));
                    return allDiags;
                });
    }

    @Override
    public boolean isAvailable() {
        // Check if either TSC or ESLint is available
        return tscRunner.isAvailable() || eslintRunner.isAvailable();
    }

    /**
     * Plan fixes for the given diagnostics asynchronously.
     */
    @Override
    public Promise<List<CodeModificationPlan>> planFixes(
            Path filePath, List<UnifiedDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return Promise.of(emptyList());
        }

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            // Group diagnostics by file
            Map<Path, List<UnifiedDiagnostic>> diagnosticsByFile = diagnostics.stream()
                    .filter(Objects::nonNull)
                    .filter(d -> d.file() != null)
                    .collect(
                            Collectors.groupingBy(
                                    d -> Path.of(d.file()), HashMap::new, Collectors.toList()));

            // Create plans for each file
            List<CodeModificationPlan> plans = new ArrayList<>();

            for (Map.Entry<Path, List<UnifiedDiagnostic>> entry : diagnosticsByFile.entrySet()) {
                Path currentFile = entry.getKey();
                List<UnifiedDiagnostic> fileDiagnostics = entry.getValue();

                // Skip non-TypeScript/JavaScript files
                if (!isSupportedFile(currentFile)) {
                    continue;
                }

                // Create a plan for this file
                CodeModificationPlan plan = createFixPlanForFile(currentFile, fileDiagnostics);
                if (!plan.isEmpty()) {
                    plans.add(plan);
                }
            }

            return plans;
        });
    }

    private CodeModificationPlan createFixPlanForFile(
            Path filePath, List<UnifiedDiagnostic> diagnostics) {
        TsMorphPlan plan = new TsMorphPlan(filePath);

        for (UnifiedDiagnostic diagnostic : diagnostics) {
            // Handle TypeScript errors
            if (diagnostic.tool() != null && diagnostic.tool().equals("typescript")) {
                handleTypeScriptError(plan, diagnostic);
            }
            // Handle ESLint errors if needed
            else if (diagnostic.tool() != null && diagnostic.tool().startsWith("eslint")) {
                handleEslintError(plan, diagnostic);
            }
        }

        return plan;
    }

    private void handleTypeScriptError(TsMorphPlan plan, UnifiedDiagnostic diagnostic) {
        String code = diagnostic.ruleId() != null ? diagnostic.ruleId() : "";

        // Handle common TypeScript errors
        switch (code) {
            case "TS2304": // Cannot find name 'X'
            case "TS2307": // Cannot find module 'X' or its corresponding type declarations
                handleMissingImport(plan, diagnostic);
                break;

            case "TS6133": // 'X' is declared but its value is never read
            case "TS6192": // All imports in import declaration are unused
                // These are typically handled by ESLint's no-unused-vars
                break;

            // Add more TypeScript error codes as needed
        }
    }

    private void handleMissingImport(TsMorphPlan plan, UnifiedDiagnostic diagnostic) {
        String message = diagnostic.message();
        Path filePath = Path.of(diagnostic.file());

        // Extract the missing identifier from the error message
        // Example: "Cannot find name 'React'" -> "React"
        String identifier = extractIdentifierFromMessage(message);
        if (identifier == null || identifier.isEmpty()) {
            return;
        }

        // Try to find the module for this identifier
        String moduleSpecifier = resolveModuleForIdentifier(identifier, filePath);
        if (moduleSpecifier == null) {
            logger.debug("Could not resolve module for identifier: {}", identifier);
            return;
        }

        // Add the import to the plan
        boolean isTypeOnly = isTypeOnlyUsage(diagnostic);
        plan.addImport(moduleSpecifier, singletonList(identifier), null, isTypeOnly, diagnostic);
    }

    private String extractIdentifierFromMessage(String message) {
        // Handle patterns like:
        // - "Cannot find name 'React'"
        // - "Cannot find module 'react' or its corresponding type declarations"
        if (message.contains("Cannot find name '")) {
            return message.substring("Cannot find name '".length(), message.lastIndexOf("'"));
        } else if (message.contains("Cannot find module '")) {
            return message.substring("Cannot find module '".length(), message.indexOf("'"));
        }
        return null;
    }

    private String resolveModuleForIdentifier(String identifier, Path filePath) {
        // Simple mapping of common identifiers to their modules
        Map<String, String> commonImports = new HashMap<>();
        commonImports.put("React", "react");
        commonImports.put("useState", "react");
        commonImports.put("useEffect", "react");
        commonImports.put("useContext", "react");
        commonImports.put("useReducer", "react");
        commonImports.put("useCallback", "react");
        commonImports.put("useMemo", "react");
        commonImports.put("useRef", "react");
        commonImports.put("useImperativeHandle", "react");
        commonImports.put("useLayoutEffect", "react");
        commonImports.put("useDebugValue", "react");

        // Check if it's a common import
        String module = commonImports.get(identifier);
        if (module != null) {
            return module;
        }

        // NOTE: Implement more sophisticated module resolution
        // - Check project dependencies
        // - Check TypeScript type definitions
        // - Check relative/absolute imports

        return null;
    }

    private boolean isTypeOnlyUsage(UnifiedDiagnostic diagnostic) {
        // NOTE: Implement type-only detection
        // This should analyze the context to determine if the identifier is used as a
        // type
        // For now, we'll assume it's not a type-only usage
        return false;
    }

    private void handleEslintError(TsMorphPlan plan, UnifiedDiagnostic diagnostic) {
        // Extract rule ID and message
        String ruleId = diagnostic.ruleId() != null ? diagnostic.ruleId() : "unknown";
        String message = diagnostic.message() != null ? diagnostic.message() : "ESLint error";

        // Create a more descriptive action based on the rule ID
        String actionDescription = String.format("Fix ESLint: %s - %s", ruleId, message);

        // Add the action to the plan
        plan.getActions().add(new CodeAction(actionDescription, "eslint-fix"));
        plan.getFixedDiagnostics().add(diagnostic);

        // Log the ESLint diagnostic for debugging
        logger.debug("Added ESLint fix action for rule {}: {}", ruleId, message);
    }

    private boolean isSupportedFile(Path filePath) {
        if (filePath == null) {
            return false;
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".ts")
                || fileName.endsWith(".tsx")
                || fileName.endsWith(".js")
                || fileName.endsWith(".jsx");
    }
}
