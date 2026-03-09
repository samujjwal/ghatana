/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs.eslint;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Service for running ESLint on TypeScript/JavaScript files. Provides advanced static analysis and
 * code quality checks.
 
 * @doc.type class
 * @doc.purpose Handles es lint service operations
 * @doc.layer core
 * @doc.pattern Service
*/
public class ESLintService {
    private static final Logger logger = LogManager.getLogger(ESLintService.class);
    private final PolyfixProjectContext context;
    private final Path projectRoot;
    private final Path nodeModulesPath;
    private final Path eslintConfigPath;

    public ESLintService(PolyfixProjectContext context) {
        this.context = context;
        this.projectRoot = context.root();
        this.nodeModulesPath = projectRoot.resolve("node_modules");
        this.eslintConfigPath = findESLintConfig(projectRoot);
    }

    /**
 * Run ESLint on the specified files and return the results as UnifiedDiagnostic objects. */
    public List<UnifiedDiagnostic> analyze(List<Path> files) {
        logger.info("Starting ESLint analysis for {} files", files != null ? files.size() : 0);

        if (files == null || files.isEmpty()) {
            logger.warn("No files provided for ESLint analysis");
            return Collections.emptyList();
        }

        // Filter out non-existent files
        List<Path> existingFiles =
                files.stream()
                        .filter(Objects::nonNull)
                        .filter(Files::exists)
                        .collect(Collectors.toList());

        if (existingFiles.isEmpty()) {
            logger.warn("No existing files to analyze");
            return Collections.emptyList();
        }

        logger.debug("Files to analyze: {}", existingFiles);

        try {
            // Check if ESLint is available
            if (!isESLintAvailable()) {
                String warning =
                        "ESLint is not available. Install it with 'npm install --save-dev eslint'"
                                + " in "
                                + projectRoot;
                logger.warn(warning);
                return Collections.emptyList();
            }

            logger.info("ESLint is available, starting analysis");

            // Run ESLint and parse results
            ESLintRunner runner = new ESLintRunner(projectRoot, nodeModulesPath, eslintConfigPath);
            List<UnifiedDiagnostic> diagnostics = runner.analyze(existingFiles);

            logger.info("ESLint analysis completed with {} issues", diagnostics.size());
            if (!diagnostics.isEmpty()) {
                logger.debug("ESLint issues found: {}", diagnostics);
            }

            return diagnostics;

        } catch (Exception e) {
            logger.error("Error running ESLint analysis: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
 * Check if ESLint is available in the project. */
    public boolean isESLintAvailable() {
        return Files.exists(nodeModulesPath.resolve(".bin/eslint"))
                || Files.exists(nodeModulesPath.resolve("eslint/bin/eslint.js"));
    }

    /**
 * Find the ESLint configuration file in the project. */
    private Path findESLintConfig(Path projectRoot) {
        // List of possible ESLint config file names
        String[] configFiles = {
            ".eslintrc.js",
            ".eslintrc.cjs",
            ".eslintrc.yaml",
            ".eslintrc.yml",
            ".eslintrc.json",
            ".eslintrc",
            "package.json" // ESLint config can be in package.json
        };

        // Check each possible config file
        for (String configFile : configFiles) {
            Path configPath = projectRoot.resolve(configFile);
            if (Files.exists(configPath)) {
                return configPath;
            }
        }

        // If no config file found, use default
        return projectRoot.resolve(".eslintrc.js");
    }
}
