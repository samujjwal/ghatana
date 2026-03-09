/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads and validates Polyfix configuration with support for: - Default values - File-based
 * configuration - Environment variable overrides - Schema validation
 
 * @doc.type class
 * @doc.purpose Handles polyfix config loader operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class PolyfixConfigLoader {
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    private static final Set<String> VALID_LANGUAGES =
            Set.of("java", "typescript", "javascript", "python", "bash", "sh", "json", "yaml");

    private PolyfixConfigLoader() {}

    public static PolyfixConfig load(Path root, Map<String, String> overrides) throws IOException {
        var defaults = getDefaultConfig();
        Path repoCfg = root.resolve("polyfix.json");
        PolyfixConfig config = defaults;

        if (Files.exists(repoCfg)) {
            try {
                JsonNode configNode = MAPPER.readTree(repoCfg.toFile());
                config = MAPPER.treeToValue(configNode, PolyfixConfig.class);
                // Detect structurally invalid config (unrecognized properties yield all-null fields)
                if (config.languages() == null
                        && config.budgets() == null
                        && config.policies() == null
                        && config.tools() == null) {
                    throw new IOException(
                            "Invalid configuration format: "
                                    + "config file does not match expected schema");
                }
                validateConfig(config);
            } catch (JsonProcessingException e) {
                throw new IOException("Invalid configuration format: " + e.getMessage(), e);
            }
        }
        if (overrides != null && !overrides.isEmpty()) {
            config = applyOverrides(config, overrides);
        }
        return config;
    }

    /* package-private for testing */
    static PolyfixConfig getDefaultConfig() {
        return new PolyfixConfig(
                List.of("java", "typescript", "python", "bash", "json", "yaml"),
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
    }

    /* package-private for testing */
    static void validateConfig(PolyfixConfig config) {
        // Validate languages
        if (config.languages() == null) {
            throw new IllegalArgumentException("languages cannot be null");
        }

        for (String lang : config.languages()) {
            if (!VALID_LANGUAGES.contains(lang.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported language: " + lang);
            }
        }

        // Validate budgets
        if (config.budgets() == null) {
            throw new IllegalArgumentException("budgets cannot be null");
        }

        if (config.budgets().maxPasses() < 1) {
            throw new IllegalArgumentException("maxPasses must be at least 1");
        }

        if (config.budgets().maxEditsPerFile() < 0) {
            throw new IllegalArgumentException("maxEditsPerFile cannot be negative");
        }

        // Validate policies
        if (config.policies() == null) {
            throw new IllegalArgumentException("policies cannot be null");
        }

        // Validate tools
        if (config.tools() == null) {
            throw new IllegalArgumentException("tools cannot be null");
        }

        // Validate budgets
        if (config.budgets().maxPasses() < 1) {
            throw new IllegalArgumentException("maxPasses must be at least 1");
        }
        if (config.budgets().maxEditsPerFile() < 0) {
            throw new IllegalArgumentException("maxEditsPerFile cannot be negative");
        }
    }

    /* package-private for testing */
    static PolyfixConfig applyOverrides(PolyfixConfig config, Map<String, String> overrides) {
        // If no overrides, return the original config
        if (overrides == null || overrides.isEmpty()) {
            return config;
        }

        // Convert config to JSON node for easier manipulation
        ObjectNode configNode = MAPPER.valueToTree(config);

        // Apply simple dot-notation overrides (e.g., "budgets.maxPasses" -> 5)
        overrides.forEach(
                (key, value) -> {
                    if (key == null || value == null) {
                        return; // Skip null keys or values
                    }
                    String[] path = key.split("\\.");
                    JsonNode currentNode = configNode;

                    // Navigate to the parent node
                    for (int i = 0; i < path.length - 1; i++) {
                        currentNode = currentNode.path(path[i]);
                        if (currentNode.isMissingNode()) {
                            throw new IllegalArgumentException(
                                    "Invalid configuration path: " + key);
                        }
                    }

                    // Set the value if the node is mutable
                    if (currentNode instanceof ObjectNode parentNode) {
                        String field = path[path.length - 1];
                        try {
                            // Try to parse as number first, then as boolean, then as string
                            try {
                                parentNode.put(field, Integer.parseInt(value));
                            } catch (NumberFormatException e) {
                                if (value.equalsIgnoreCase("true")
                                        || value.equalsIgnoreCase("false")) {
                                    parentNode.put(field, Boolean.parseBoolean(value));
                                } else {
                                    parentNode.put(field, value);
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Cannot set value for path: " + key, e);
                        }
                    }
                });

        // Convert back to config object
        try {
            return MAPPER.treeToValue(configNode, PolyfixConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to apply configuration overrides", e);
        }
    }
}
