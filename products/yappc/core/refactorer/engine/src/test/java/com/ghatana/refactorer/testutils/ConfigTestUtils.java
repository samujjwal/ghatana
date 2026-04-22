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
public class ConfigTestUtils {
    private static final Logger logger = LogManager.getLogger(ConfigTestUtils.class); // GH-90000

    /**
     * Creates a test PolyfixProjectContext with default configuration.
     *
     * @param projectRoot The project root directory
     * @return A configured PolyfixProjectContext
     */
    public static PolyfixProjectContext createTestContext(Path projectRoot) { // GH-90000
        // Create a simple configuration
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of("typescript", "javascript"), // GH-90000
                        List.of(), // GH-90000
                        new PolyfixConfig.Budgets(10, 100), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, true), // GH-90000
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
                                "pylint",
                                "mvn",
                                "gradle"));

        // Create and return the context
        return new PolyfixProjectContext( // GH-90000
                projectRoot,
                config,
                List.of(), // No language services needed for basic tests // GH-90000
                Executors.newSingleThreadExecutor(), // GH-90000
                logger);
    }

    /**
     * Copies a directory recursively to a target location.
     *
     * @param source The source directory to copy from
     * @param target The target directory to copy to
     * @throws java.io.IOException If an I/O error occurs
     */
    public static void copyDirectory(Path source, Path target) throws java.io.IOException { // GH-90000
        if (!java.nio.file.Files.exists(source)) { // GH-90000
            throw new java.io.IOException("Source directory does not exist: " + source); // GH-90000
        }

        // Create target directory if it doesn't exist
        java.nio.file.Files.createDirectories(target); // GH-90000

        // Copy all files and subdirectories
        java.nio.file.Files.walk(source) // GH-90000
                .forEach( // GH-90000
                        sourcePath -> {
                            try {
                                Path targetPath = target.resolve(source.relativize(sourcePath)); // GH-90000
                                if (java.nio.file.Files.isDirectory(sourcePath)) { // GH-90000
                                    if (!java.nio.file.Files.exists(targetPath)) { // GH-90000
                                        java.nio.file.Files.createDirectory(targetPath); // GH-90000
                                    }
                                } else {
                                    java.nio.file.Files.copy( // GH-90000
                                            sourcePath,
                                            targetPath,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (java.io.IOException e) { // GH-90000
                                throw new RuntimeException("Failed to copy " + sourcePath, e); // GH-90000
                            }
                        });
    }
}
