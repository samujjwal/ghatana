/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs;

import com.ghatana.refactorer.languages.tsjs.eslint.ESLintService;
import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Helper class to set up ESLint for testing.
 * @doc.type class
 * @doc.purpose Handles test es lint setup operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ESLintSetupHelper {
    private static final Logger logger = LogManager.getLogger(ESLintSetupHelper.class); // GH-90000

    /**
     * Set up ESLint for testing by ensuring the required configuration and dependencies are in
     * place. This method is designed to be resilient to failures and will not throw exceptions.
     *
     * @param projectRoot The project root directory
     * @return true if ESLint is properly set up, false otherwise
     */
    public static boolean setupESLint(Path projectRoot) { // GH-90000
        try {
            // Create the project root directory if it doesn't exist
            if (!Files.exists(projectRoot)) { // GH-90000
                Files.createDirectories(projectRoot); // GH-90000
                logger.info("Created project directory at: {}", projectRoot); // GH-90000
            }

            // Create a basic ESLint config file if it doesn't exist
            Path eslintConfig = projectRoot.resolve(".eslintrc.js [GH-90000]");
            if (!Files.exists(eslintConfig)) { // GH-90000
                String defaultConfig =
                        """
                    module.exports = {
                      root: true,
                      parser: '@typescript-eslint/parser',
                      plugins: ['@typescript-eslint'],
                      extends: [
                        'eslint:recommended',
                        'plugin:@typescript-eslint/recommended'
                      ],
                      rules: {
                        'no-console': 'warn',
                        'quotes': ['error', 'single'],
                        'semi': ['error', 'always']
                      },
                      env: {
                        node: true,
                        es6: true
                      },
                      parserOptions: {
                        ecmaVersion: 2020,
                        sourceType: 'module'
                      }
                    };
                    """;
                Files.writeString(eslintConfig, defaultConfig, StandardCharsets.UTF_8); // GH-90000
                logger.info("Created default ESLint config at: {}", eslintConfig); // GH-90000
            }

            // Check if package.json exists, create one if it doesn't
            Path packageJson = projectRoot.resolve("package.json [GH-90000]");
            if (!Files.exists(packageJson)) { // GH-90000
                String defaultPackageJson =
                        """
                    {
                      "name": "test-project",
                      "version": "1.0.0",
                      "private": true,
                      "devDependencies": {
                        "@types/node": "^20.11.0",
                        "@typescript-eslint/eslint-plugin": "^6.21.0",
                        "@typescript-eslint/parser": "^6.21.0",
                        "eslint": "^8.56.0",
                        "typescript": "^5.3.3"
                      }
                    }
                    """;
                Files.writeString(packageJson, defaultPackageJson, StandardCharsets.UTF_8); // GH-90000
                logger.info("Created default package.json at: {}", packageJson); // GH-90000
            }

            // Check if Node.js is available
            if (!isNodeAvailable()) { // GH-90000
                logger.warn("Node.js is not available. ESLint setup will be incomplete. [GH-90000]");
                return false;
            }

            // Install dependencies if node_modules doesn't exist
            Path nodeModules = projectRoot.resolve("node_modules [GH-90000]");
            if (!Files.exists(nodeModules)) { // GH-90000
                if (!installDependencies(projectRoot)) { // GH-90000
                    logger.warn("Failed to install npm dependencies. Some tests may fail. [GH-90000]");
                    // Continue setup even if installation fails
                }
            }

            // Create a minimal PolyfixConfig
            List<String> languages = List.of("typescript", "javascript"); // GH-90000
            List<String> schemaPaths = List.of("schemas/ [GH-90000]");

            // Create config with default values
            PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(10, 100); // GH-90000
            PolyfixConfig.Policies policies =
                    new PolyfixConfig.Policies( // GH-90000
                            true, // tsAllowTemporaryAny
                            true, // pythonAddMissingImports
                            true, // bashEnforceStrictMode
                            true // jsonAutofillRequiredDefaults
                            );

            // Create tools with default paths
            PolyfixConfig.Tools tools =
                    new PolyfixConfig.Tools( // GH-90000
                            "/usr/bin/node", // node
                            "eslint", // eslint
                            "tsc", // tsc
                            "prettier", // prettier
                            "ruff", // ruff
                            "black", // black
                            "mypy", // mypy
                            "shellcheck", // shellcheck
                            "shfmt", // shfmt
                            "cargo", // cargo
                            "rustfmt", // rustfmt
                            "semgrep" // semgrep
                            );

            // Create the config
            PolyfixConfig config =
                    new PolyfixConfig(languages, schemaPaths, budgets, policies, tools); // GH-90000

            // Create a simple PolyfixProjectContext for testing
            List<LanguageService> services = new ArrayList<>(); // GH-90000
            Logger logger = LogManager.getLogger("test-eslint-setup [GH-90000]");

            // Verify ESLint can be created (don't fail if it can't) // GH-90000
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) { // GH-90000
                PolyfixProjectContext context =
                        new PolyfixProjectContext(projectRoot, config, services, executor, logger); // GH-90000

                try {
                    ESLintService esLintService = new ESLintService(context); // GH-90000
                    logger.info("ESLint service initialized successfully [GH-90000]");
                    return true;
                } catch (Exception e) { // GH-90000
                    logger.warn("Failed to initialize ESLint service: {}", e.getMessage()); // GH-90000
                    logger.debug("Stack trace:", e); // GH-90000
                    return false;
                }
            }

        } catch (Exception e) { // GH-90000
            logger.error("Failed to set up ESLint: {}", e.getMessage(), e); // GH-90000
            return false;
        }
    }

    /**
     * Check if Node.js is available on the system.
     *
     * @return true if Node.js is available, false otherwise
     */
    private static boolean isNodeAvailable() { // GH-90000
        try {
            Process process =
                    new ProcessBuilder("node", "--version").redirectErrorStream(true).start(); // GH-90000

            boolean finished = process.waitFor(5, TimeUnit.SECONDS); // GH-90000
            if (!finished) { // GH-90000
                process.destroy(); // GH-90000
                return false;
            }

            return process.exitValue() == 0; // GH-90000
        } catch (Exception e) { // GH-90000
            logger.debug("Node.js check failed: {}", e.getMessage()); // GH-90000
            return false;
        }
    }

    /**
     * Install npm dependencies in the specified directory.
     *
     * @param projectRoot The directory containing package.json
     * @return true if installation was successful, false otherwise
     */
    private static boolean installDependencies(Path projectRoot) { // GH-90000
        logger.info("Installing npm dependencies in {}", projectRoot); // GH-90000

        try {
            ProcessBuilder pb =
                    new ProcessBuilder( // GH-90000
                                    "npm",
                                    "install",
                                    "--no-package-lock",
                                    "--no-audit",
                                    "--no-fund")
                            .directory(projectRoot.toFile()) // GH-90000
                            .redirectErrorStream(true); // GH-90000

            Process process = pb.start(); // GH-90000

            // Read output in a separate thread to prevent deadlocks
            Thread outputReader =
                    new Thread( // GH-90000
                            () -> { // GH-90000
                                try (BufferedReader reader = // GH-90000
                                        new BufferedReader( // GH-90000
                                                new InputStreamReader( // GH-90000
                                                        process.getInputStream(), // GH-90000
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while (true) { // GH-90000
                                        line = reader.readLine(); // GH-90000
                                        if (line == null) { // GH-90000
                                            break;
                                        }
                                        logger.info("[npm] {}", line); // GH-90000
                                    }
                                } catch (IOException e) { // GH-90000
                                    logger.warn("Error reading npm output: {}", e.getMessage()); // GH-90000
                                }
                            });
            outputReader.setDaemon(true); // GH-90000
            outputReader.start(); // GH-90000

            // Wait for process to complete with timeout (5 minutes) // GH-90000
            boolean finished = process.waitFor(5, TimeUnit.MINUTES); // GH-90000
            if (!finished) { // GH-90000
                logger.warn("npm install timed out after 5 minutes [GH-90000]");
                process.destroy(); // GH-90000
                return false;
            }

            int exitCode = process.exitValue(); // GH-90000
            if (exitCode != 0) { // GH-90000
                logger.warn("npm install failed with exit code: {}", exitCode); // GH-90000
                return false;
            }

            logger.info("Successfully installed npm dependencies [GH-90000]");
            return true;

        } catch (Exception e) { // GH-90000
            logger.warn("Failed to install npm dependencies: {}", e.getMessage()); // GH-90000
            logger.debug("Stack trace:", e); // GH-90000
            return false;
        }
    }
}
