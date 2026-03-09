/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Base class for language-specific test classes. Provides common setup and utilities for testing
 * language implementations with ActiveJ Promise support.
 * 
 * Extends EventloopTestBase to provide Reactor and Promise execution support.
 
 * @doc.type class
 * @doc.purpose Handles abstract language test operations
 * @doc.layer core
 * @doc.pattern Test
*/
public abstract class AbstractLanguageTest extends EventloopTestBase {

    @TempDir protected Path tempDir;

    protected PolyfixProjectContext projectContext;
    protected ExecutorService executorService;

    @BeforeEach
    void setUp() {
        // Create a basic configuration
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java", "python"),
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

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Create project context
        projectContext =
                new PolyfixProjectContext(
                        tempDir, // root
                        config, // config
                        List.of(), // languages - will be added by concrete test classes
                        executorService, // exec
                        LogManager.getLogger(getClass()) // log
                        );
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (executorService != null) {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Copies a test resource file to the test directory.
     *
     * @param resourcePath Path to the resource file (relative to src/test/resources)
     * @return The path to the copied file in the test directory
     * @throws IOException if the file cannot be copied
     */
    protected Path copyTestResource(String resourcePath) throws IOException {
        // Split the resource path and create the source path
        String[] pathParts = resourcePath.split("/");
        Path source = Path.of("src", "test", "resources");
        for (String part : pathParts) {
            source = source.resolve(part);
        }

        Path target = tempDir.resolve(source.getFileName());
        Files.copy(
                getClass().getClassLoader().getResourceAsStream(resourcePath),
                target,
                StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
