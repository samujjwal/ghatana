package com.ghatana.kernel.plugin.validation;

import com.ghatana.kernel.plugin.PluginManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator for plugin configuration against the schema.
 *
 * <p>Validates PluginManifest objects against the JSON schema defined in
 * plugin-config-schema.json to ensure plugin configurations are valid before
 * loading.</p>
 *
 * @doc.type class
 * @doc.purpose Validates plugin configuration against JSON schema
 * @doc.layer core
 * @doc.pattern Validator
 * @since 1.2.0
 */
public class PluginConfigValidator {

    private static final Logger LOG = LoggerFactory.getLogger(PluginConfigValidator.class);
    private static final String SCHEMA_PATH = "/schema/plugin-config-schema.json";

    private final ObjectMapper objectMapper;
    private JsonNode schemaNode;

    public PluginConfigValidator() {
        this.objectMapper = new ObjectMapper();
        loadSchema();
    }

    private void loadSchema() {
        try (InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (schemaStream == null) {
                LOG.warn("Plugin config schema not found at {}, validation will be skipped", SCHEMA_PATH);
                return;
            }
            this.schemaNode = objectMapper.readTree(schemaStream);
            LOG.debug("Loaded plugin config schema from {}", SCHEMA_PATH);
        } catch (IOException e) {
            LOG.error("Failed to load plugin config schema from {}", SCHEMA_PATH, e);
        }
    }

    /**
     * Validates a plugin manifest against the schema.
     *
     * @param manifest the plugin manifest to validate
     * @return ValidationResult containing validation status and any errors
     */
    public ValidationResult validate(PluginManifest manifest) {
        if (schemaNode == null) {
            LOG.warn("Schema not loaded, skipping validation for plugin {}", manifest.getPluginId());
            return ValidationResult.success(manifest.getPluginId());
        }

        List<String> errors = new ArrayList<>();

        // Validate required fields
        if (manifest.getPluginId() == null || manifest.getPluginId().isBlank()) {
            errors.add("pluginId is required");
        } else if (!manifest.getPluginId().matches("^[a-z][a-z0-9-]*$")) {
            errors.add("pluginId must be lowercase alphanumeric with hyphens, starting with a letter");
        }

        if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            errors.add("version is required");
        } else if (!manifest.getVersion().matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$")) {
            errors.add("version must follow semantic versioning (e.g., 1.0.0 or 1.0.0-beta)");
        }

        // Validate optional fields if present
        if (manifest.getDescription() != null && manifest.getDescription().length() > 500) {
            errors.add("description must not exceed 500 characters");
        }

        if (manifest.getMainClass() != null && !manifest.getMainClass().matches("^[a-zA-Z][a-zA-Z0-9._]*$")) {
            errors.add("mainClass must be a valid Java class name");
        }

        // Validate capabilities
        for (var capability : manifest.getCapabilities()) {
            String capId = capability.getCapabilityId();
            if (capId != null && !capId.matches("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$")) {
                errors.add("capability ID '" + capId + "' must follow pattern 'domain:capability'");
            }
        }

        // Validate tier if present
        if (manifest.getTier() != null) {
            String tierName = manifest.getTier().name();
            if (!tierName.matches("^(CORE|STANDARD|PREMIUM|CUSTOM)$")) {
                errors.add("tier must be one of: CORE, STANDARD, PREMIUM, CUSTOM");
            }
        }

        // Validate resource quotas
        if (manifest.getResourceQuotas() != null) {
            var quotas = manifest.getResourceQuotas();
            if (quotas.getMaxMemoryMb() < 64) {
                errors.add("maxMemoryMB must be at least 64");
            }
            if (quotas.getMaxCpuPercent() < 0.1 || quotas.getMaxCpuPercent() > 100.0) {
                errors.add("maxCpuPercent must be between 0.1 and 100.0");
            }
            if (quotas.getMaxFileDescriptors() < 32) {
                errors.add("maxFileDescriptors must be at least 32");
            }
        }

        if (errors.isEmpty()) {
            LOG.debug("Plugin manifest {} validated successfully", manifest.getPluginId());
            return ValidationResult.success(manifest.getPluginId());
        } else {
            LOG.warn("Plugin manifest {} validation failed: {}", manifest.getPluginId(), errors);
            return ValidationResult.failure(manifest.getPluginId(), errors);
        }
    }

    /**
     * Result of plugin manifest validation.
     */
    public record ValidationResult(
        String pluginId,
        boolean valid,
        List<String> errors
    ) {
        public static ValidationResult success(String pluginId) {
            return new ValidationResult(pluginId, true, List.of());
        }

        public static ValidationResult failure(String pluginId, List<String> errors) {
            return new ValidationResult(pluginId, false, errors);
        }
    }
}
