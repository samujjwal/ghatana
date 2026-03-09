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
public class TestESLintSetup {
    private static final Logger logger = LogManager.getLogger(TestESLintSetup.class);

    /**
     * Set up ESLint for testing by ensuring the required configuration and dependencies are in
     * place. This method is designed to be resilient to failures and will not throw exceptions.
     *
     * @param projectRoot The project root directory
     * @return true if ESLint is properly set up, false otherwise
     */
    public static boolean setupESLint(Path projectRoot) {
        try {
            // Create the project root directory if it doesn't exist
            if (!Files.exists(projectRoot)) {
                Files.createDirectories(projectRoot);
                logger.info("Created project directory at: {}", projectRoot);
            }

            // Create a basic ESLint config file if it doesn't exist
            Path eslintConfig = projectRoot.resolve(".eslintrc.js");
            if (!Files.exists(eslintConfig)) {
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
                Files.writeString(eslintConfig, defaultConfig, StandardCharsets.UTF_8);
                logger.info("Created default ESLint config at: {}", eslintConfig);
            }

            // Check if package.json exists, create one if it doesn't
            Path packageJson = projectRoot.resolve("package.json");
            if (!Files.exists(packageJson)) {
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
                Files.writeString(packageJson, defaultPackageJson, StandardCharsets.UTF_8);
                logger.info("Created default package.json at: {}", packageJson);
            }

            // Check if Node.js is available
            if (!isNodeAvailable()) {
                logger.warn("Node.js is not available. ESLint setup will be incomplete.");
                return false;
            }

            // Install dependencies if node_modules doesn't exist
            Path nodeModules = projectRoot.resolve("node_modules");
            if (!Files.exists(nodeModules)) {
                if (!installDependencies(projectRoot)) {
                    logger.warn("Failed to install npm dependencies. Some tests may fail.");
                    // Continue setup even if installation fails
                }
            }

            // Create a minimal PolyfixConfig
            List<String> languages = List.of("typescript", "javascript");
            List<String> schemaPaths = List.of("schemas/");

            // Create config with default values
            PolyfixConfig.Budgets budgets = new PolyfixConfig.Budgets(10, 100);
            PolyfixConfig.Policies policies =
                    new PolyfixConfig.Policies(
                            true, // tsAllowTemporaryAny
                            true, // pythonAddMissingImports
                            true, // bashEnforceStrictMode
                            true // jsonAutofillRequiredDefaults
                            );

            // Create tools with default paths
            PolyfixConfig.Tools tools =
                    new PolyfixConfig.Tools(
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
                    new PolyfixConfig(languages, schemaPaths, budgets, policies, tools);

            // Create a simple PolyfixProjectContext for testing
            List<LanguageService> services = new ArrayList<>();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Logger logger = LogManager.getLogger("test-eslint-setup");

            PolyfixProjectContext context =
                    new PolyfixProjectContext(projectRoot, config, services, executor, logger);

            // Verify ESLint can be created (don't fail if it can't)
            try {
                ESLintService esLintService = new ESLintService(context);
                logger.info("ESLint service initialized successfully");
                return true;
            } catch (Exception e) {
                logger.warn("Failed to initialize ESLint service: {}", e.getMessage());
                logger.debug("Stack trace:", e);
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to set up ESLint: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if Node.js is available on the system.
     *
     * @return true if Node.js is available, false otherwise
     */
    private static boolean isNodeAvailable() {
        try {
            Process process =
                    new ProcessBuilder("node", "--version").redirectErrorStream(true).start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("Node.js check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Install npm dependencies in the specified directory.
     *
     * @param projectRoot The directory containing package.json
     * @return true if installation was successful, false otherwise
     */
    private static boolean installDependencies(Path projectRoot) {
        logger.info("Installing npm dependencies in {}", projectRoot);

        try {
            ProcessBuilder pb =
                    new ProcessBuilder(
                                    "npm",
                                    "install",
                                    "--no-package-lock",
                                    "--no-audit",
                                    "--no-fund")
                            .directory(projectRoot.toFile())
                            .redirectErrorStream(true);

            Process process = pb.start();

            // Read output in a separate thread to prevent deadlocks
            Thread outputReader =
                    new Thread(
                            () -> {
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        process.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        logger.info("[npm] {}", line);
                                    }
                                } catch (IOException e) {
                                    logger.warn("Error reading npm output: {}", e.getMessage());
                                }
                            });
            outputReader.setDaemon(true);
            outputReader.start();

            // Wait for process to complete with timeout (5 minutes)
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                logger.warn("npm install timed out after 5 minutes");
                process.destroy();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("npm install failed with exit code: {}", exitCode);
                return false;
            }

            logger.info("Successfully installed npm dependencies");
            return true;

        } catch (Exception e) {
            logger.warn("Failed to install npm dependencies: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return false;
        }
    }
}
