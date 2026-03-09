/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.datacloud.config.cli;

import com.ghatana.datacloud.config.CollectionConfigCompiler;
import com.ghatana.datacloud.config.ConfigLoader;
import com.ghatana.datacloud.config.ConfigValidator;
import com.ghatana.datacloud.config.PluginConfigCompiler;
import com.ghatana.datacloud.config.PolicyConfigCompiler;
import com.ghatana.datacloud.config.RoutingConfigCompiler;
import com.ghatana.datacloud.config.StorageProfileCompiler;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.RawCollectionConfig;
import com.ghatana.datacloud.config.model.RawPluginConfig;
import com.ghatana.datacloud.config.model.RawPolicyConfig;
import com.ghatana.datacloud.config.model.RawRoutingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Configuration validation CLI for CI/CD gating.
 *
 * <p>
 * <b>Purpose</b><br>
 * Validates all YAML configuration files before deployment:
 * <ul>
 * <li>Schema validation (required fields, types)</li>
 * <li>Compilation validation (can be compiled to runtime objects)</li>
 * <li>Cross-reference validation (referenced configs exist)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * # Validate all configs in a directory
 * java -jar data-cloud-cli.jar validate --config-path /path/to/configs
 *
 * # Validate specific config type
 * java -jar data-cloud-cli.jar validate --config-path /path/to/configs --type collection
 *
 * # Validate with verbose output
 * java -jar data-cloud-cli.jar validate --config-path /path/to/configs --verbose
 * }</pre>
 *
 * <p>
 * <b>Exit Codes</b><br>
 * <ul>
 * <li>0 - All validations passed</li>
 * <li>1 - One or more validations failed</li>
 * <li>2 - Invalid arguments or configuration error</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Configuration validation CLI for CI gating
 * @doc.layer product
 * @doc.pattern CLI
 */
public class ConfigValidationCli {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigValidationCli.class);

    // Exit codes
    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_VALIDATION_FAILED = 1;
    public static final int EXIT_ERROR = 2;

    private final ConfigLoader loader;
    private final ConfigValidator validator;
    private final CollectionConfigCompiler collectionCompiler;
    private final PluginConfigCompiler pluginCompiler;
    private final StorageProfileCompiler storageProfileCompiler;
    private final PolicyConfigCompiler policyCompiler;
    private final RoutingConfigCompiler routingCompiler;
    private final PrintStream out;
    private final PrintStream err;
    private boolean verbose;

    /**
     * Creates a new ConfigValidationCli with default output streams.
     *
     * @param configBasePath the base path for config files
     */
    public ConfigValidationCli(Path configBasePath) {
        this(configBasePath, System.out, System.err);
    }

    /**
     * Creates a new ConfigValidationCli with custom output streams.
     *
     * @param configBasePath the base path for config files
     * @param out standard output stream
     * @param err error output stream
     */
    public ConfigValidationCli(Path configBasePath, PrintStream out, PrintStream err) {
        Executor executor = Executors.newSingleThreadExecutor();
        this.loader = new ConfigLoader(configBasePath, executor);
        this.validator = new ConfigValidator();
        this.collectionCompiler = new CollectionConfigCompiler();
        this.pluginCompiler = new PluginConfigCompiler();
        this.storageProfileCompiler = new StorageProfileCompiler();
        this.policyCompiler = new PolicyConfigCompiler();
        this.routingCompiler = new RoutingConfigCompiler();
        this.out = out;
        this.err = err;
        this.verbose = false;
    }

    /**
     * Sets verbose mode.
     *
     * @param verbose true for verbose output
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Validates all configuration files in the given path.
     *
     * @param configPath the path to validate
     * @return validation result summary
     */
    public ValidationSummary validateAll(Path configPath) {
        List<ValidationError> errors = new ArrayList<>();
        int totalFiles = 0;
        int validFiles = 0;

        out.println("╔════════════════════════════════════════════════════════════╗");
        out.println("║         Data-Cloud Configuration Validator                 ║");
        out.println("╠════════════════════════════════════════════════════════════╣");
        out.printf("║  Config Path: %-45s ║%n", truncate(configPath.toString(), 45));
        out.println("╚════════════════════════════════════════════════════════════╝");
        out.println();

        // Validate collections
        ValidationResult collectionResult = validateConfigType(configPath, "collections", "collection");
        errors.addAll(collectionResult.errors);
        totalFiles += collectionResult.totalFiles;
        validFiles += collectionResult.validFiles;

        // Validate plugins
        ValidationResult pluginResult = validateConfigType(configPath, "plugins", "plugin");
        errors.addAll(pluginResult.errors);
        totalFiles += pluginResult.totalFiles;
        validFiles += pluginResult.validFiles;

        // Validate storage profiles
        ValidationResult storageResult = validateConfigType(configPath, "storage-profiles", "storage-profile");
        errors.addAll(storageResult.errors);
        totalFiles += storageResult.totalFiles;
        validFiles += storageResult.validFiles;

        // Validate policies
        ValidationResult policyResult = validateConfigType(configPath, "policies", "policy");
        errors.addAll(policyResult.errors);
        totalFiles += policyResult.totalFiles;
        validFiles += policyResult.validFiles;

        // Validate routing
        ValidationResult routingResult = validateConfigType(configPath, "routing", "routing");
        errors.addAll(routingResult.errors);
        totalFiles += routingResult.totalFiles;
        validFiles += routingResult.validFiles;

        // Print summary
        printSummary(totalFiles, validFiles, errors);

        return new ValidationSummary(totalFiles, validFiles, errors);
    }

    /**
     * Validates a specific type of configuration.
     *
     * @param configPath the base config path
     * @param subdir the subdirectory for this config type
     * @param typeName the config type name for display
     * @return validation result
     */
    public ValidationResult validateConfigType(Path configPath, String subdir, String typeName) {
        List<ValidationError> errors = new ArrayList<>();
        int totalFiles = 0;
        int validFiles = 0;

        Path typePath = configPath.resolve(subdir);
        if (!Files.exists(typePath)) {
            if (verbose) {
                out.printf("  [SKIP] No %s directory found at %s%n", subdir, typePath);
            }
            return new ValidationResult(0, 0, errors);
        }

        out.printf("▶ Validating %s configs...%n", typeName);

        try (Stream<Path> files = Files.walk(typePath)) {
            List<Path> yamlFiles = files
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path file : yamlFiles) {
                totalFiles++;
                try {
                    validateFile(file, typeName);
                    validFiles++;
                    out.printf("  ✓ %s%n", relativePath(configPath, file));
                } catch (Exception e) {
                    ValidationError error = new ValidationError(
                            file.toString(),
                            typeName,
                            e.getMessage(),
                            e);
                    errors.add(error);
                    err.printf("  ✗ %s%n", relativePath(configPath, file));
                    err.printf("    Error: %s%n", e.getMessage());
                    if (verbose && e.getCause() != null) {
                        err.printf("    Cause: %s%n", e.getCause().getMessage());
                    }
                }
            }
        } catch (IOException e) {
            err.printf("  Error reading directory %s: %s%n", typePath, e.getMessage());
        }

        out.println();
        return new ValidationResult(totalFiles, validFiles, errors);
    }

    /**
     * Validates a single configuration file.
     *
     * @param file the file to validate
     * @param typeName the expected config type
     * @throws ConfigurationException if validation fails
     */
    private void validateFile(Path file, String typeName) throws Exception {
        switch (typeName) {
            case "collection" ->
                validateCollectionFile(file);
            case "plugin" ->
                validatePluginFile(file);
            case "storage-profile" ->
                validateStorageProfileFile(file);
            case "policy" ->
                validatePolicyFile(file);
            case "routing" ->
                validateRoutingFile(file);
            default ->
                throw new IllegalArgumentException("Unknown config type: " + typeName);
        }
    }

    private void validateCollectionFile(Path file) throws Exception {
        RawCollectionConfig raw = loader.loadCollectionFromFile(file);
        ConfigValidator.ValidationResult result = validator.validate(raw);
        if (!result.isValid()) {
            throw new ConfigurationException("Validation failed: " + result.errors());
        }
        // Try to compile
        collectionCompiler.compile(raw);
    }

    private void validatePluginFile(Path file) throws Exception {
        RawPluginConfig raw = loader.loadPluginFromFile(file);
        // Try to compile
        pluginCompiler.compile(raw);
    }

    private void validateStorageProfileFile(Path file) throws Exception {
        com.ghatana.datacloud.config.model.RawStorageProfileConfig raw = loader.loadStorageProfileFromFile(file);
        // Try to compile all profiles
        storageProfileCompiler.compileAll(raw);
    }

    private void validatePolicyFile(Path file) throws Exception {
        RawPolicyConfig raw = loader.loadPolicyFromFile(file);
        // Try to compile
        policyCompiler.compile(raw);
    }

    private void validateRoutingFile(Path file) throws Exception {
        RawRoutingConfig raw = loader.loadRoutingFromFile(file);
        // Try to compile
        routingCompiler.compile(raw);
    }

    private void printSummary(int total, int valid, List<ValidationError> errors) {
        out.println("════════════════════════════════════════════════════════════");
        out.println("                      VALIDATION SUMMARY");
        out.println("════════════════════════════════════════════════════════════");
        out.printf("  Total files:    %d%n", total);
        out.printf("  Valid files:    %d%n", valid);
        out.printf("  Invalid files:  %d%n", errors.size());
        out.println();

        if (errors.isEmpty()) {
            out.println("  ✅ All configurations are valid!");
        } else {
            out.println("  ❌ Validation FAILED");
            out.println();
            out.println("  Errors:");
            for (ValidationError error : errors) {
                out.printf("    • %s (%s)%n", error.file(), error.type());
                out.printf("      %s%n", error.message());
            }
        }
        out.println("════════════════════════════════════════════════════════════");
    }

    private String relativePath(Path base, Path file) {
        try {
            return base.relativize(file).toString();
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }

    private String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return "..." + s.substring(s.length() - maxLength + 3);
    }

    // =========================================================================
    // Main entry point
    // =========================================================================
    /**
     * Main entry point for CLI.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(EXIT_ERROR);
        }

        String command = args[0];
        if ("validate".equals(command)) {
            runValidate(args);
        } else if ("--help".equals(command) || "-h".equals(command)) {
            printUsage();
            System.exit(EXIT_SUCCESS);
        } else {
            System.err.println("Unknown command: " + command);
            printUsage();
            System.exit(EXIT_ERROR);
        }
    }

    private static void runValidate(String[] args) {
        String configPath = null;
        boolean verbose = false;

        for (int i = 1; i < args.length; i++) {
            if ("--config-path".equals(args[i]) && i + 1 < args.length) {
                configPath = args[++i];
            } else if ("--verbose".equals(args[i]) || "-v".equals(args[i])) {
                verbose = true;
            }
        }

        if (configPath == null) {
            System.err.println("Error: --config-path is required");
            printUsage();
            System.exit(EXIT_ERROR);
        }

        Path path = Path.of(configPath);
        if (!Files.exists(path)) {
            System.err.println("Error: Config path does not exist: " + configPath);
            System.exit(EXIT_ERROR);
        }

        ConfigValidationCli cli = new ConfigValidationCli(path);
        cli.setVerbose(verbose);

        ValidationSummary summary = cli.validateAll(path);
        System.exit(summary.errors().isEmpty() ? EXIT_SUCCESS : EXIT_VALIDATION_FAILED);
    }

    private static void printUsage() {
        System.out.println("Data-Cloud Configuration Validator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  config-validator validate --config-path <path> [--verbose]");
        System.out.println("  config-validator --help");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  validate    Validate all configuration files");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --config-path <path>  Path to configuration directory");
        System.out.println("  --verbose, -v         Enable verbose output");
        System.out.println("  --help, -h            Show this help message");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  All validations passed");
        System.out.println("  1  One or more validations failed");
        System.out.println("  2  Invalid arguments or error");
    }

    // =========================================================================
    // Inner records
    // =========================================================================
    /**
     * Validation error details.
     */
    public record ValidationError(
            String file,
            String type,
            String message,
            Throwable cause) {
    }

    /**
     * Validation result for a config type.
     */
    public record ValidationResult(
            int totalFiles,
            int validFiles,
            List<ValidationError> errors) {
    }

    /**
     * Overall validation summary.
     */
    public record ValidationSummary(
            int totalFiles,
            int validFiles,
            List<ValidationError> errors) {

        

    public boolean isValid() {
        return errors.isEmpty();
    }

    public int invalidFiles() {
        return totalFiles - validFiles;
    }
}
}
