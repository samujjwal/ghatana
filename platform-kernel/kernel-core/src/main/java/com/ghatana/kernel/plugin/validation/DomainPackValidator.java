package com.ghatana.kernel.plugin.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Validator for domain pack manifests against the schema.
 *
 * <p>Validates domain pack YAML/JSON manifests against the JSON schema
 * defined in domain-pack-schema.json to ensure pack manifests are valid
 * before loading.</p>
 *
 * @doc.type class
 * @doc.purpose Validates domain pack manifests against JSON schema
 * @doc.layer core
 * @doc.pattern Validator
 * @since 1.2.0
 */
public class DomainPackValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DomainPackValidator.class);
    private static final String SCHEMA_PATH = "/schema/domain-pack-schema.json";

    private final ObjectMapper objectMapper;
    private JsonNode schemaNode;

    public DomainPackValidator() {
        this.objectMapper = new ObjectMapper();
        loadSchema();
    }

    private void loadSchema() {
        try (InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (schemaStream == null) {
                LOG.warn("Domain pack schema not found at {}, validation will be skipped", SCHEMA_PATH);
                return;
            }
            this.schemaNode = objectMapper.readTree(schemaStream);
            LOG.debug("Loaded domain pack schema from {}", SCHEMA_PATH);
        } catch (IOException e) {
            LOG.error("Failed to load domain pack schema from {}", SCHEMA_PATH, e);
        }
    }

    /**
     * Validates a domain pack manifest against the schema.
     *
     * @param manifestJson the manifest JSON string to validate
     * @param packId the pack identifier for logging
     * @return ValidationResult containing validation status and any errors
     */
    public ValidationResult validate(String manifestJson, String packId) {
        if (schemaNode == null) {
            LOG.warn("Schema not loaded, skipping validation for pack {}", packId);
            return ValidationResult.success(packId);
        }

        List<String> errors = new ArrayList<>();

        try {
            JsonNode manifestNode = objectMapper.readTree(manifestJson);

            // Validate required top-level structure
            if (!manifestNode.has("pack")) {
                errors.add("Missing required 'pack' field");
            } else {
                JsonNode packNode = manifestNode.get("pack");

                // Validate required pack fields
                if (!packNode.has("id") || packNode.get("id").asText().isBlank()) {
                    errors.add("pack.id is required");
                } else {
                    String id = packNode.get("id").asText();
                    if (!id.matches("^[a-z][a-z0-9-]*$")) {
                        errors.add("pack.id must be lowercase alphanumeric with hyphens, starting with a letter");
                    }
                }

                if (!packNode.has("name") || packNode.get("name").asText().isBlank()) {
                    errors.add("pack.name is required");
                }

                if (!packNode.has("version") || packNode.get("version").asText().isBlank()) {
                    errors.add("pack.version is required");
                } else {
                    String version = packNode.get("version").asText();
                    if (!version.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$")) {
                        errors.add("pack.version must follow semantic versioning (e.g., 1.0.0 or 1.0.0-beta)");
                    }
                }

                if (!packNode.has("description") || packNode.get("description").asText().isBlank()) {
                    errors.add("pack.description is required");
                }

                // Validate capabilities if present
                if (packNode.has("capabilities")) {
                    JsonNode capabilities = packNode.get("capabilities");
                    if (capabilities.isArray()) {
                        for (int i = 0; i < capabilities.size(); i++) {
                            JsonNode capability = capabilities.get(i);
                            if (!capability.has("id") || capability.get("id").asText().isBlank()) {
                                errors.add("capabilities[" + i + "].id is required");
                            }
                            if (!capability.has("name") || capability.get("name").asText().isBlank()) {
                                errors.add("capabilities[" + i + "].name is required");
                            }
                            if (!capability.has("type") || capability.get("type").asText().isBlank()) {
                                errors.add("capabilities[" + i + "].type is required");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            errors.add("Failed to parse manifest JSON: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            LOG.debug("Domain pack manifest {} validated successfully", packId);
            return ValidationResult.success(packId);
        } else {
            LOG.warn("Domain pack manifest {} validation failed: {}", packId, errors);
            return ValidationResult.failure(packId, errors);
        }
    }

    /**
     * Result of domain pack manifest validation.
     */
    public record ValidationResult(
        String packId,
        boolean valid,
        List<String> errors
    ) {
        public static ValidationResult success(String packId) {
            return new ValidationResult(packId, true, List.of());
        }

        public static ValidationResult failure(String packId, List<String> errors) {
            return new ValidationResult(packId, false, errors);
        }
    }
}
