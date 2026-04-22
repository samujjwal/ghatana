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

    private TestConfig() {} // GH-90000

    /**
     * Creates a test-friendly {@link PolyfixProjectContext} rooted at the given directory.
     *
     * @param root the temporary directory to use as project root
     * @return a configured context for testing
     */
    public static PolyfixProjectContext createTestContext(Path root) { // GH-90000
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("java", "typescript"), // GH-90000
                        List.of(), // GH-90000
                        new PolyfixConfig.Budgets(3, 20), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, true), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
                                "node", "eslint", "tsc", "prettier",
                                "ruff", "black", "mypy",
                                "shellcheck", "shfmt", "cargo", "rustfmt", "semgrep"));
        return new PolyfixProjectContext( // GH-90000
                root,
                config,
                List.of(), // GH-90000
                Executors.newSingleThreadExecutor(), // GH-90000
                LogManager.getLogger(TestConfig.class)); // GH-90000
    }
}
