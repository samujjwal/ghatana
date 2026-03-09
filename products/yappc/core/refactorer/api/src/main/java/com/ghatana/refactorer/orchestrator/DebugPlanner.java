package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.action.FixAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Converts diagnostics into actionable FixAction plans. This is a minimal scaffold that maps common
 * diagnostic categories to engines/runners configured elsewhere (OpenRewrite, LibCST, jscodeshift,
 * goimports, taplo, etc.). Text replacement fields are intentionally left null; the engine is
 * selected via metadata.
 
 * @doc.type class
 * @doc.purpose Handles debug planner operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class DebugPlanner {
    private static final Logger LOG = LogManager.getLogger(DebugPlanner.class);

    // Simple patterns for classification; extend as needed
    private static final Pattern DEPRECATION = Pattern.compile("(?i)deprecat");
    private static final Pattern PY_MODULE_NOT_FOUND =
            Pattern.compile(
                    "ModuleNotFoundError: No module named '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_IMPORT =
            Pattern.compile("(?i)import|module not found|undefined name");

    private DebugPlanner() {}

    public static List<FixAction> plan(PolyfixProjectContext ctx, List<UnifiedDiagnostic> diags) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(diags, "diags");
        List<FixAction> out = new ArrayList<>();

        for (UnifiedDiagnostic d : diags) {
            String tool = safe(d.tool());
            String msg = safe(d.message());
            String file = d.file();
            int line = d.line();
            int col = d.column();
            Path filePath = (file != null && !file.isBlank()) ? Path.of(file) : null;

            if ("openrewrite".equalsIgnoreCase(tool) || DEPRECATION.matcher(msg).find()) {
                // Plan: run Java OpenRewrite recipe pack (ProductionHygiene or targeted migration)
                Map<String, String> meta = new HashMap<>();
                meta.put("engine", "rewrite");
                meta.put("recipePack", "config/rewriters/java-recipes.yml");
                meta.put("recipe", "com.ghatana.refactorer.ProductionHygiene");
                out.add(
                        new FixAction(
                                "engine-invoke",
                                "Apply OpenRewrite hygiene/deprecation recipe",
                                filePath,
                                line,
                                col,
                                line,
                                col,
                                null,
                                null,
                                meta));
                continue;
            }

            if (MISSING_IMPORT.matcher(msg).find()) {
                // Plan: language-specific import fixer
                String symbol = null;
                String module = null;
                // Java/Javac: cannot find symbol:   symbol: class Foo
                int idx = msg.indexOf("symbol:");
                if (idx >= 0) {
                    String tail = msg.substring(idx).replace('\n', ' ');
                    int iName = tail.indexOf("class ");
                    if (iName >= 0) {
                        String after = tail.substring(iName + 6).trim();
                        int sp = after.indexOf(' ');
                        symbol = sp > 0 ? after.substring(0, sp) : after;
                    }
                }
                // Python: NameError: name 'foo' is not defined
                var mPy = Pattern.compile("name '([^']+)' is not defined").matcher(msg);
                if (mPy.find()) symbol = mPy.group(1);
                // TS: Cannot find name 'Foo'. (TS2304)
                var mTsSym =
                        Pattern.compile("Cannot find name '([^']+)'", Pattern.CASE_INSENSITIVE)
                                .matcher(msg);
                if (mTsSym.find()) symbol = mTsSym.group(1);
                // TS: Cannot find module 'x' (TS2307)
                var mTsMod =
                        Pattern.compile("Cannot find module '([^']+)'", Pattern.CASE_INSENSITIVE)
                                .matcher(msg);
                if (mTsMod.find()) module = mTsMod.group(1);

                Map<String, String> meta = new HashMap<>();
                meta.put("engine", "import-fix");
                meta.put("tool", tool);
                if (symbol != null && !symbol.isBlank()) meta.put("symbol", symbol);
                if (module != null && !module.isBlank()) meta.put("module", module);
                out.add(
                        new FixAction(
                                "import-fix",
                                "Add missing import (engine-resolved)",
                                filePath,
                                line,
                                col,
                                line,
                                col,
                                null,
                                null,
                                meta));
                continue;
            }

            if ("staticcheck".equalsIgnoreCase(tool)) {
                // Plan: goimports fix or staticcheck quick fix
                Map<String, String> meta = new HashMap<>();
                meta.put("engine", "goimports");
                out.add(
                        new FixAction(
                                "goimports",
                                "Apply goimports/go fix",
                                filePath,
                                line,
                                col,
                                line,
                                col,
                                null,
                                null,
                                meta));
                continue;
            }

            if ("taplo".equalsIgnoreCase(tool)) {
                Map<String, String> meta = new HashMap<>();
                meta.put("engine", "taplo-fix");
                out.add(
                        new FixAction(
                                "taplo-fix",
                                "Format/auto-fix TOML issues",
                                filePath,
                                line,
                                col,
                                line,
                                col,
                                null,
                                null,
                                meta));
                continue;
            }

            // Default: log and no-op plan placeholder
            LOG.debug("No specific plan for diagnostic: {} {} {}:{}", tool, msg, file, line);
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
