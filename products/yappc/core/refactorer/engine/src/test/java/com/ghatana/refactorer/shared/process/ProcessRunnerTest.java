package com.ghatana.refactorer.shared.process;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.RefactorerOperationException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @doc.type class
 * @doc.purpose Verifies typed process execution failures in the refactorer engine
 * @doc.layer core
 * @doc.pattern Test
 */
class ProcessRunnerTest {

    @TempDir
    Path tempDir;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // GH-90000

    @AfterEach
    void tearDown() { // GH-90000
        executor.shutdownNow(); // GH-90000
    }

    @Test
    void wrapsIoFailuresInRefactorerOperationException() { // GH-90000
        ProcessRunner runner = new ProcessRunner(createContext(tempDir)); // GH-90000

        assertThatThrownBy(() -> runner.execute("command-that-does-not-exist-123", List.of(), tempDir, true)) // GH-90000
                .isInstanceOf(RefactorerOperationException.class) // GH-90000
                .hasMessageContaining("Failed to execute process [GH-90000]")
                .hasMessageContaining("command-that-does-not-exist-123 [GH-90000]")
                .hasCauseInstanceOf(java.io.IOException.class); // GH-90000
    }

    private PolyfixProjectContext createContext(Path root) { // GH-90000
        PolyfixConfig config = new PolyfixConfig( // GH-90000
                List.of("java [GH-90000]"),
                List.of("schemas [GH-90000]"),
                new PolyfixConfig.Budgets(3, 20), // GH-90000
                new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                new PolyfixConfig.Tools( // GH-90000
                        "node",
                        "eslint",
                        "tsc",
                        "prettier",
                        "ruff",
                        "black",
                        "mypy",
                        "shellcheck",
                        "shfmt",
                        "cargo",
                        "rustfmt",
                        "semgrep"));
        return new PolyfixProjectContext( // GH-90000
                root,
                config,
                List.of(), // GH-90000
                executor,
                LogManager.getLogger(ProcessRunnerTest.class)); // GH-90000
    }
}
