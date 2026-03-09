package com.ghatana.refactorer.testutils;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;

/**
 * Test configuration helper that creates a minimal {@link PolyfixProjectContext} for testing.
 
 * @doc.type class
 * @doc.purpose Handles test config operations
 * @doc.layer core
 * @doc.pattern Configuration
*/
public final class TestConfig {

    private TestConfig() {}

    /**
     * Creates a test-friendly {@link PolyfixProjectContext} rooted at the given directory.
     *
     * @param root the temporary directory to use as project root
     * @return a configured context for testing
     */
    public static PolyfixProjectContext createTestContext(Path root) {
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java", "typescript"),
                        List.of(),
                        new PolyfixConfig.Budgets(3, 20),
                        new PolyfixConfig.Policies(true, true, true, true),
                        new PolyfixConfig.Tools(
                                "node", "eslint", "tsc", "prettier",
                                "ruff", "black", "mypy",
                                "shellcheck", "shfmt", "cargo", "rustfmt", "semgrep"));
        return new PolyfixProjectContext(
                root,
                config,
                List.of(),
                Executors.newSingleThreadExecutor(),
                LogManager.getLogger(TestConfig.class));
    }
}
