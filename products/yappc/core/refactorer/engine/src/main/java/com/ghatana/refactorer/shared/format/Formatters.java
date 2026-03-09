package com.ghatana.refactorer.shared.format;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Formatting utilities (e.g., Prettier integration for TS/JS/JSON/YAML). 
 * @doc.type class
 * @doc.purpose Handles formatters operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class Formatters {
    private static final Logger log = LogManager.getLogger(Formatters.class);

    private Formatters() {}

    /**
     * Runs Prettier with --write at the project root when available.
     *
     * @param ctx project context
     * @return true if formatting succeeded or was skipped safely; false on hard failure
     */
    public static boolean runPrettier(PolyfixProjectContext ctx) {
        try {
            Path root = ctx.getProjectRoot();
            ProcessRunner runner = new ProcessRunner(ctx);
            // Prefer npx to use local project version if available
            ProcessResult result =
                    runner.execute(
                            null, // No environment variables needed
                            "npx",
                            java.util.List.of("prettier", "--write", "."),
                            root,
                            true);
            if (!result.isSuccess()) {
                log.warn(
                        "Prettier returned non-zero exit: {} - {}",
                        result.exitCode(),
                        result.error());
                return false;
            }
            log.info("Prettier formatted the project successfully");
            return true;
        } catch (Exception e) {
            log.debug("Prettier not available or failed to run: {}", e.getMessage());
            return false;
        }
    }
}
