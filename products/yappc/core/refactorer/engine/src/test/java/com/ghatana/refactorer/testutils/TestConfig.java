package com.ghatana.refactorer.testutils;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Test utilities for creating test configurations. 
 * @doc.type class
 * @doc.purpose Handles test config operations
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class TestConfig {
    private static final Logger logger = LogManager.getLogger(TestConfig.class);

    /**
     * Creates a test PolyfixProjectContext with default configuration.
     *
     * @param projectRoot The project root directory
     * @return A configured PolyfixProjectContext
     */
    public static PolyfixProjectContext createTestContext(Path projectRoot) {
        // Create a simple configuration
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("typescript", "javascript"),
                        List.of(),
                        new PolyfixConfig.Budgets(10, 100),
                        new PolyfixConfig.Policies(true, true, true, true),
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
                                "pylint",
                                "mvn",
                                "gradle"));

        // Create and return the context
        return new PolyfixProjectContext(
                projectRoot,
                config,
                List.of(), // No language services needed for basic tests
                Executors.newSingleThreadExecutor(),
                logger);
    }

    /**
     * Copies a directory recursively to a target location.
     *
     * @param source The source directory to copy from
     * @param target The target directory to copy to
     * @throws java.io.IOException If an I/O error occurs
     */
    public static void copyDirectory(Path source, Path target) throws java.io.IOException {
        if (!java.nio.file.Files.exists(source)) {
            throw new java.io.IOException("Source directory does not exist: " + source);
        }

        // Create target directory if it doesn't exist
        java.nio.file.Files.createDirectories(target);

        // Copy all files and subdirectories
        java.nio.file.Files.walk(source)
                .forEach(
                        sourcePath -> {
                            try {
                                Path targetPath = target.resolve(source.relativize(sourcePath));
                                if (java.nio.file.Files.isDirectory(sourcePath)) {
                                    if (!java.nio.file.Files.exists(targetPath)) {
                                        java.nio.file.Files.createDirectory(targetPath);
                                    }
                                } else {
                                    java.nio.file.Files.copy(
                                            sourcePath,
                                            targetPath,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (java.io.IOException e) {
                                throw new RuntimeException("Failed to copy " + sourcePath, e);
                            }
                        });
    }
}
