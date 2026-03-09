package com.ghatana.refactorer.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.refactorer.codemods.OpenRewriteRunner;
import com.ghatana.refactorer.debug.StackTraceParser;
import com.ghatana.refactorer.debug.TestFailureClassifier;
import com.ghatana.refactorer.orchestrator.DebugPlanner;
import com.ghatana.refactorer.orchestrator.LanguageServices;
import com.ghatana.refactorer.orchestrator.debug.DebugEntry;
import com.ghatana.refactorer.rewriters.GoToolsRunner;
import com.ghatana.refactorer.rewriters.JavaAdvancedRewriteRunner;
import com.ghatana.refactorer.rewriters.PyLibCSTBridge;
import com.ghatana.refactorer.rewriters.TaploRunner;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixConfigLoader;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.action.FixAction;
import com.ghatana.refactorer.shared.util.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandLine.Command(
        name = "debug",
        mixinStandardHelpOptions = true,
        description = "Run a safe debug session and parse failures")
/**
 * @doc.type class
 * @doc.purpose Handles debug command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public final class DebugCommand implements Runnable {
    private static final Logger LOG = LogManager.getLogger(DebugCommand.class);

    @CommandLine.Option(names = "--root", required = true, description = "Project root")
    Path root;

    @CommandLine.Option(
            names = "--timeout-ms",
            defaultValue = "600000",
            description = "Timeout in ms")
    long timeoutMs;

    @CommandLine.Option(names = "--json", description = "Output JSON with frames and suggestions")
    boolean json;

    @CommandLine.Option(names = "--print-raw", description = "Always print raw combined output")
    boolean printRaw;

    @CommandLine.Option(names = "--plan-only", description = "Plan fixes but do not apply")
    boolean planOnly;

    @CommandLine.Option(names = "--apply", description = "Apply planned fixes (experimental)")
    boolean apply;

    @CommandLine.Parameters(
            arity = "1..*",
            paramLabel = "CMD",
            description = "Command and args to run (must be allowlisted)")
    List<String> cmd;

    @Override
    public void run() {
        try {
            PolyfixConfig cfg = PolyfixConfigLoader.load(root, java.util.Map.of());
            PolyfixProjectContext ctx =
                    new PolyfixProjectContext(
                            root,
                            cfg,
                            LanguageServices.load(cfg),
                            Executors.newFixedThreadPool(4),
                            LOG);

            var result = DebugEntry.run(ctx, cmd, Duration.ofMillis(timeoutMs));

            // Classify the failure
            var classifier = new TestFailureClassifier();
            var suggestions = classifier.classify(result.frames(), result.raw());

            // Convert suggestions to diagnostics for planning (minimal mapping)
            var diagsBuilder = new java.util.ArrayList<UnifiedDiagnostic>();
            for (var s : suggestions) {
                UnifiedDiagnostic d =
                        UnifiedDiagnostic.builder()
                                .tool("runtime")
                                .message(s.detail())
                                .file(s.file() != null ? s.file() : "")
                                .line(s.line() > 0 ? s.line() : 1)
                                .column(1)
                                .build();
                diagsBuilder.add(d);
            }
            var plans = DebugPlanner.plan(ctx, diagsBuilder);

            if (json) {
                var payload =
                        new DebugOutput(
                                result.success() ? 0 : 1, // Use success flag to determine exit code
                                result.frames(),
                                suggestions,
                                plans,
                                printRaw ? result.raw() : null);
                LOG.info("{}", JsonSupport.toPrettyJson(payload));
            } else {
                LOG.info("Success: {}", result.success());
                if (!result.frames().isEmpty()) {
                    LOG.info("Stack trace frames ({}):", result.frames().size());
                    for (var frame : result.frames()) {
                        LOG.info("  {}", frame);
                    }
                }
                if (!suggestions.isEmpty()) {
                    LOG.info("Suggestions ({}):", suggestions.size());
                    for (var s : suggestions) {
                        LOG.info(" - [{}] {}{}", s.category(), s.detail(), (s.file() != null ? (" @ " + s.file() + ":" + s.line()) : ""));
                    }
                }
                if (!plans.isEmpty()) {
                    LOG.info("Plans ({}):", plans.size());
                    for (FixAction p : plans) {
                        LOG.info(" - [{}] {}{}", p.getType(), p.getDescription(), (p.getFile() != null ? (" @ " + p.getFile()) : ""));
                    }
                }
                if (printRaw || result.frames().isEmpty()) {
                    LOG.info("\nRaw output ({} chars):", result.raw().length());
                    LOG.info("-".repeat(80));
                    LOG.info("{}", result.raw());
                    LOG.info("-".repeat(80));
                }
            }

            if (apply && !plans.isEmpty()) {
                applyFixes(ctx, plans, timeoutMs);
            }
        } catch (SecurityException se) {
            LOG.error("Denied by allowlist policy: {}", se.getMessage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static final class DebugOutput {
        public final int exitCode;
        public final java.util.List<StackTraceParser.TraceFrame> frames;
        public final java.util.List<TestFailureClassifier.Suggestion> suggestions;
        public final java.util.List<FixAction> plans;
        public final String raw;

        DebugOutput(
                int exitCode,
                java.util.List<StackTraceParser.TraceFrame> frames,
                java.util.List<TestFailureClassifier.Suggestion> suggestions,
                java.util.List<FixAction> plans,
                String raw) {
            this.exitCode = exitCode;
            this.frames = frames;
            this.suggestions = suggestions;
            this.plans = plans;
            this.raw = raw;
        }
    }

    // --- Apply Path ---
    private static void applyFixes(PolyfixProjectContext ctx, List<FixAction> plans, long timeoutMs) {
        if (plans == null || plans.isEmpty()) {
            LOG.info("No fixes to apply.");
            return;
        }
        LOG.info("Applying {} fix plans...", plans.size());
        int applied = 0;
        int failed = 0;
        for (FixAction plan : plans) {
            try {
                boolean ok = executeFixPlan(ctx, plan, timeoutMs);
                if (ok) {
                    applied++;
                    LOG.debug("Applied fix: {}", plan.getDescription());
                } else {
                    failed++;
                    LOG.warn("Failed to apply fix: {}", plan.getDescription());
                }
            } catch (Exception ex) {
                failed++;
                LOG.error("Error applying fix plan: {}", plan.getDescription(), ex);
            }
        }
        LOG.info("Applied {}/{} fixes ({} failed)", applied, plans.size(), failed);
    }

    private static boolean executeFixPlan(PolyfixProjectContext ctx, FixAction plan, long timeoutMs) {
        if (plan == null || plan.getMetadata() == null) return false;
        String engine = plan.getMetadata().get("engine");
        if (engine == null || engine.isBlank()) {
            LOG.warn("No engine specified in fix plan: {}", plan.getDescription());
            return false;
        }
        switch (engine) {
            case "rewrite":
                return applyRewriteFix(ctx, plan);
            case "import-fix":
                return applyImportFix(ctx, plan);
            case "goimports":
                return applyGoImports(ctx, plan, timeoutMs);
            case "taplo-fix":
                return applyTaploFix(ctx, plan, timeoutMs);
            case "py-libcst":
                return applyPyLibCstFix(ctx, plan, timeoutMs);
            default:
                LOG.warn("Unknown fix engine: {}", engine);
                return false;
        }
    }

    private static boolean applyRewriteFix(PolyfixProjectContext ctx, FixAction plan) {
        try {
            String recipePack = plan.getMetadata().get("recipePack");
            String recipe = plan.getMetadata().get("recipe");
            if (recipePack == null || recipe == null) {
                LOG.warn("Missing recipe pack or recipe in fix plan");
                return false;
            }

            JavaAdvancedRewriteRunner runner =
                    new JavaAdvancedRewriteRunner(new OpenRewriteRunner(ctx));
            var diags = runner.applyRecipes(ctx.root(), Paths.get(recipePack), List.of(recipe));
            return diags != null; // non-null means invocation succeeded; changes may be zero
        } catch (Exception e) {
            LOG.error("Error applying rewrite fix: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Applies import fixes to a source file based on the provided fix action plan. Supports Java
     * (via OpenRewrite) and TypeScript/JavaScript files.
     *
     * @param ctx The project context
     * @param plan The fix action plan containing import fix details
     * @return true if the fix was applied successfully, false otherwise
     */
    private static boolean applyImportFix(PolyfixProjectContext ctx, FixAction plan) {
        try {
            if (plan.getFile() == null) {
                LOG.warn("No file associated with import-fix plan: {}", plan.getDescription());
                return false;
            }
            Path file = plan.getFile();
            String name = file.toString().toLowerCase();
            String module = plan.getMetadata().getOrDefault("module", "");
            String symbol = plan.getMetadata().getOrDefault("symbol", "");

            // Handle Java files using OpenRewrite
            if (name.endsWith(".java")) {
                return applyJavaImportFix(ctx, file, symbol, module);
            }

            // TypeScript/JavaScript: insert import if not present
            if (name.endsWith(".ts")
                    || name.endsWith(".tsx")
                    || name.endsWith(".js")
                    || name.endsWith(".jsx")) {
                return applyJsImportFix(file, module, symbol);
            }

            LOG.warn("Unsupported file for import-fix: {}", file);
            return false;
        } catch (Exception e) {
            LOG.error("Error applying import-fix: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Applies import fixes to a TypeScript/JavaScript file.
     *
     * @param file The file to fix
     * @param module The module to import from
     * @param symbol The symbol to import
     * @return true if the fix was applied successfully
     * @throws IOException if there's an error reading or writing the file
     */
    private static boolean applyJsImportFix(Path file, String module, String symbol)
            throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);

        // Check if import already exists
        if (!module.isBlank() && content.contains("from '" + module + "'")) {
            return true; // already imported
        }

        // Determine the appropriate import statement
        String importLine;
        if (!module.isBlank() && !symbol.isBlank()) {
            importLine = "import { " + symbol + " } from '" + module + "';\n";
        } else if (!module.isBlank()) {
            importLine = "import '" + module + "';\n";
        } else if (!symbol.isBlank()) {
            LOG.warn("Missing module for symbol {}; cannot add import", symbol);
            return false;
        } else {
            LOG.warn("No module/symbol metadata for import-fix");
            return false;
        }

        // Add the import at the top of the file
        String updated = importLine + content;
        Files.writeString(
                file, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        LOG.info("Added import to {}: {}", file, importLine.trim());
        return true;
    }

    /**
     * Applies import fixes to a Java file using OpenRewrite.
     *
     * @param ctx The project context
     * @param file The Java file to fix
     * @param symbol The symbol to import
     * @param module Optional module hint (not used for Java)
     * @return true if the fix was applied successfully
     */
    private static boolean applyJavaImportFix(
            PolyfixProjectContext ctx, Path file, String symbol, String module) {
        try {
            if (symbol.isBlank()) {
                LOG.warn("No symbol provided for Java import fix");
                return false;
            }

            // Load the import mapping configuration
            // Look for config in the project root's config directory
            Path configFile = ctx.root().resolve("config/rewriters/java-imports.json");
            if (!Files.exists(configFile)) {
                LOG.warn("Java import mapping config not found at: {}", configFile);
                return false;
            }

            // Parse the import mapping configuration
            ObjectMapper mapper = JsonUtils.getDefaultMapper();
            JsonNode config = mapper.readTree(configFile.toFile());
            JsonNode commonImports = config.path("common_imports");
            JsonNode testImports = config.path("test_imports");

            // Look up the symbol in the configuration
            String fqcn = findFullyQualifiedName(commonImports, symbol);
            if (fqcn == null && file.toString().contains("/test/")) {
                fqcn = findFullyQualifiedName(testImports, symbol);
            }

            if (fqcn == null) {
                LOG.warn("No mapping found for symbol: {}", symbol);
                return false;
            }

            // Get the simple name from the fully qualified class name
            String simpleName =
                    fqcn.contains(".") ? fqcn.substring(fqcn.lastIndexOf('.') + 1) : fqcn;

            // Create a properly implemented Recipe that wraps the AddImport functionality
            String finalFqcn = fqcn;
            Recipe recipe =
                    new Recipe() {
                        @Override
                        public String getName() {
                            return "AddImport";
                        }

                        @Override
                        public String getDisplayName() {
                            return "Add import for " + finalFqcn;
                        }

                        @Override
                        public String getDescription() {
                            return "Adds import for " + finalFqcn;
                        }

                        @Override
                        public TreeVisitor<?, ExecutionContext> getVisitor() {
                            return new org.openrewrite.java.AddImport<>(
                                    finalFqcn, simpleName, false);
                        }
                    };

            // Run the recipe
            OpenRewriteRunner runner = new OpenRewriteRunner(ctx);
            List<UnifiedDiagnostic> diagnostics = runner.run(recipe, List.of(file));
            boolean success = diagnostics.isEmpty();

            if (success) {
                LOG.info("Successfully added import for {} as {}", symbol, fqcn);
            } else {
                LOG.warn(
                        "Failed to add import for {}: {}",
                        symbol,
                        diagnostics.stream()
                                .map(d -> d.getMessage())
                                .collect(Collectors.joining(", ")));
            }

            return success;
        } catch (Exception e) {
            LOG.error("Error applying Java import fix", e);
            return false;
        }
    }

    /**
     * Finds the fully qualified class name for a symbol in the import mapping.
     *
     * @param importsNode The JSON node containing the import mappings
     * @param symbol The symbol to look up
     * @return The fully qualified class name, or null if not found
     */
    private static String findFullyQualifiedName(JsonNode importsNode, String symbol) {
        // First try exact match
        if (importsNode.has(symbol)) {
            if (importsNode.get(symbol).isArray() && importsNode.get(symbol).size() > 0) {
                return importsNode.get(symbol).get(0).asText();
            }
            return importsNode.get(symbol).asText();
        }

        // Then try case-insensitive match
        Iterable<Map.Entry<String, JsonNode>> iterable = () -> importsNode.fields();
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterable.iterator(), 0), false)
                .filter(entry -> entry.getKey().equalsIgnoreCase(symbol))
                .findFirst()
                .map(entry -> entry.getValue().asText())
                .orElse(null);
    }

    /**
     * Handles errors during import fix application.
     *
     * @param e The exception that occurred
     * @return Always returns false to indicate failure
     */
    private static boolean handleImportFixError(Exception e) {
        LOG.error("Error applying import-fix: {}", e.getMessage(), e);
        return false;
    }

    private static boolean applyGoImports(PolyfixProjectContext ctx, FixAction plan, long timeoutMs) {
        try {
            if (plan.getFile() == null) {
                LOG.warn("No file associated with goimports plan: {}", plan.getDescription());
                return false;
            }
            GoToolsRunner runner = new GoToolsRunner(ctx);
            List<Path> files = new ArrayList<>();
            files.add(plan.getFile());
            int changed = runner.goimports(ctx.root(), files, timeoutMs);
            return changed > 0;
        } catch (Exception e) {
            LOG.error("Error running goimports: {}", e.getMessage(), e);
            return false;
        }
    }

    private static boolean applyTaploFix(PolyfixProjectContext ctx, FixAction plan, long timeoutMs) {
        try {
            if (plan.getFile() == null) {
                LOG.warn("No file associated with taplo plan: {}", plan.getDescription());
                return false;
            }
            TaploRunner runner = new TaploRunner();
            List<Path> files = new ArrayList<>();
            files.add(plan.getFile());
            int formatted = runner.format(ctx.root(), files, timeoutMs);
            return formatted > 0;
        } catch (Exception e) {
            LOG.error("Error running taplo format: {}", e.getMessage(), e);
            return false;
        }
    }

    private static boolean applyPyLibCstFix(PolyfixProjectContext ctx, FixAction plan, long timeoutMs) {
        try {
            if (plan.getFile() == null) {
                LOG.warn("No file associated with py-libcst plan: {}", plan.getDescription());
                return false;
            }
            // Build a minimal plan JSON for the python bridge
            String op = plan.getMetadata().getOrDefault("op", "add_import");
            String module = plan.getMetadata().getOrDefault("module", "");
            var planObj = new HashMap<String, Object>();
            planObj.put("file", ctx.root().relativize(plan.getFile()).toString());
            var ops = new java.util.ArrayList<java.util.Map<String, Object>>();
            var opMap = new java.util.HashMap<String, Object>();
            opMap.put("kind", op);
            if (!module.isBlank()) opMap.put("module", module);
            ops.add(opMap);
            planObj.put("ops", ops);

            // Write to a temp JSON file under build/tmp
            Path tmpDir = ctx.root().resolve("build/tmp/py-libcst");
            Files.createDirectories(tmpDir);
            Path planFile = Files.createTempFile(tmpDir, "plan-", ".json");
            Files.writeString(
                    planFile,
                    JsonUtils.toPrettyJson(planObj),
                    StandardOpenOption.TRUNCATE_EXISTING);

            // Resolve path to bundled script (dev-time path)
            Path script =
                    ctx.root()
                            .resolve("modules/rewriters/src/main/resources/python/libcst_apply.py");
            if (!Files.exists(script)) {
                LOG.warn("LibCST script not found at {}", script);
                return false;
            }

            PyLibCSTBridge bridge = new PyLibCSTBridge();
            var cmd = List.of("python3", script.toString(), "--plan-file", planFile.toString());
            var res = com.ghatana.refactorer.shared.util.ProcessExec.run(
                    ctx.root(), java.time.Duration.ofMillis(timeoutMs), cmd, java.util.Map.of());
            if (res.exitCode() != 0) {
                LOG.warn("py-libcst apply failed: {}\n{}", res.exitCode(), res.err());
                return false;
            }
            return true;
        } catch (IOException ioe) {
            LOG.error("IO error during py-libcst apply: {}", ioe.getMessage(), ioe);
            return false;
        } catch (Exception e) {
            LOG.error("Error running py-libcst: {}", e.getMessage(), e);
            return false;
        }
    }
}
