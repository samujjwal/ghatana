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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void wrapsIoFailuresInRefactorerOperationException() {
        ProcessRunner runner = new ProcessRunner(createContext(tempDir));

        assertThatThrownBy(() -> runner.execute("command-that-does-not-exist-123", List.of(), tempDir, true))
                .isInstanceOf(RefactorerOperationException.class)
                .hasMessageContaining("Failed to execute process")
                .hasMessageContaining("command-that-does-not-exist-123")
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    private PolyfixProjectContext createContext(Path root) {
        PolyfixConfig config = new PolyfixConfig(
                List.of("java"),
                List.of("schemas"),
                new PolyfixConfig.Budgets(3, 20),
                new PolyfixConfig.Policies(true, true, true, false),
                new PolyfixConfig.Tools(
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
        return new PolyfixProjectContext(
                root,
                config,
                List.of(),
                executor,
                LogManager.getLogger(ProcessRunnerTest.class));
    }
}