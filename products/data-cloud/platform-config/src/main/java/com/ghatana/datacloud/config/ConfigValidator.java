package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.*;
import com.ghatana.platform.core.exception.ConfigurationException;

import java.util.*;

/**
 * Validates raw collection configuration before compilation.
 *
 * <p>
 * Performs structural and semantic validation including:
 * <ul>
 * <li>Required field presence</li>
 * <li>Field type validity</li>
 * <li>Event model alignment (for EVENT collections)</li>
 * <li>Index field references</li>
 * <li>Policy references</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Validate raw YAML config before compilation
 * @doc.layer core
 * @doc.pattern Validator
 */
public class ConfigValidator {

    /**
     * Event model required fields from libs:event-core.
     */
    private static final Set<String> EVENT_MODEL_REQUIRED_FIELDS = Set.of(
            "eventId", "eventType", "aggregateId", "aggregateVersion",
            "correlationId", "timestamp", "tenantId", "payload"
    );

    /**
     * Event model optional fields.
     */
    private static final Set<String> EVENT_MODEL_OPTIONAL_FIELDS = Set.of(
            "causationId", "metadata"
    );

    /**
     * Validate raw configuration and return validation result.
     *
     * @param config raw config to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validate(RawCollectionConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate metadata
        validateMetadata(config, errors, warnings);

        // Validate spec
        validateSpec(config, errors, warnings);

        return new ValidationResult(
                errors.isEmpty(),
                Collections.unmodifiableList(errors),
                Collections.unmodifiableList(warnings)
        );
    }

    /**
     * Validate configuration and throw if invalid.
     *
     * @param config raw config to validate
     * @throws ConfigurationException if validation fails
     */
    public void validateOrFail(RawCollectionConfig config) {
        ValidationResult result = validate(config);
        if (!result.isValid()) {
            throw new ConfigurationException(
                    "Configuration validation failed:\n" + String.join("\n", result.errors())
            );
        }
    }

    private void validateMetadata(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        if (config.metadata() == null) {
            errors.add("Missing 'metadata' section");
            return;
        }

        var metadata = config.metadata();

        if (metadata.name() == null || metadata.name().isBlank()) {
            errors.add("metadata.name is required");
        } else if (!isValidName(metadata.name())) {
            errors.add("metadata.name must be lowercase alphanumeric with hyphens");
        }

        if (metadata.namespace() == null || metadata.namespace().isBlank()) {
            errors.add("metadata.namespace (tenant ID) is required");
        }
    }

    private void validateSpec(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        if (config.spec() == null) {
            errors.add("Missing 'spec' section");
            return;
        }

        var spec = config.spec();

        // Validate record type
        if (spec.recordType() == null || spec.recordType().isBlank()) {
            errors.add("spec.recordType is required");
        } else {
            validateRecordType(spec.recordType(), errors);
        }

        // Validate schema
        validateSchema(config, errors, warnings);

        // Validate indexes reference existing fields
        validateIndexes(config, errors, warnings);

        // Validate storage config
        validateStorage(config, errors, warnings);

        // Validate event-specific requirements
        if ("EVENT".equalsIgnoreCase(spec.recordType())) {
            validateEventCollection(config, errors, warnings);
        }
    }

    private void validateRecordType(String recordType, List<String> errors) {
        try {
            CompiledCollectionConfig.RecordType.valueOf(recordType.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("spec.recordType must be one of: ENTITY, EVENT, TIMESERIES, GRAPH, DOCUMENT");
        }
    }

    private void validateSchema(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        if (spec.schema() == null) {
            errors.add("spec.schema is required");
            return;
        }

        var schema = spec.schema();

        if (schema.version() == null || schema.version().isBlank()) {
            warnings.add("spec.schema.version is recommended");
        }

        if (schema.fields() == null || schema.fields().isEmpty()) {
            errors.add("spec.schema.fields must have at least one field");
            return;
        }

        // Validate each field
        Set<String> fieldNames = new HashSet<>();
        for (var field : schema.fields()) {
            validateField(field, fieldNames, errors, warnings);
        }
    }

    private void validateField(
            RawCollectionConfig.RawField field,
            Set<String> fieldNames,
            List<String> errors,
            List<String> warnings) {

        if (field.name() == null || field.name().isBlank()) {
            errors.add("Field name is required");
            return;
        }

        String name = field.name();

        if (fieldNames.contains(name)) {
            errors.add("Duplicate field name: " + name);
        }
        fieldNames.add(name);

        if (field.type() == null || field.type().isBlank()) {
            errors.add("Field '" + name + "' must have a type");
            return;
        }

        try {
            FieldType type = FieldType.fromYaml(field.type());

            // Validate enum has values
            if (type == FieldType.ENUM && (field.values() == null || field.values().isEmpty())) {
                errors.add("Enum field '" + name + "' must have values defined");
            }

            // Validate array has items type
            if (type == FieldType.ARRAY && field.items() == null) {
                errors.add("Array field '" + name + "' must have items.type defined");
            }

            // Validate unique only on supported types
            if (field.unique() && !type.supportsUnique()) {
                warnings.add("Field '" + name + "' type " + type + " may not support unique constraint");
            }

            // Validate indexed only on indexable types
            if (field.indexed() && !type.isIndexable()) {
                warnings.add("Field '" + name + "' type " + type + " may not support indexing");
            }

        } catch (IllegalArgumentException e) {
            errors.add("Field '" + name + "' has unknown type: " + field.type());
        }
    }

    private void validateIndexes(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        if (spec.indexes() == null || spec.indexes().isEmpty()) {
            return;
        }

        // Collect field names
        Set<String> fieldNames = new HashSet<>();
        if (spec.schema() != null && spec.schema().fields() != null) {
            for (var field : spec.schema().fields()) {
                fieldNames.add(field.name());
            }
        }

        Set<String> indexNames = new HashSet<>();
        for (var index : spec.indexes()) {
            // Validate index name uniqueness
            if (index.name() == null || index.name().isBlank()) {
                errors.add("Index must have a name");
                continue;
            }

            if (indexNames.contains(index.name())) {
                errors.add("Duplicate index name: " + index.name());
            }
            indexNames.add(index.name());

            // Validate index fields exist
            if (index.fields() == null || index.fields().isEmpty()) {
                errors.add("Index '" + index.name() + "' must have at least one field");
                continue;
            }

            for (String fieldName : index.fields()) {
                if (!fieldNames.contains(fieldName)) {
                    errors.add("Index '" + index.name() + "' references unknown field: " + fieldName);
                }
            }
        }
    }

    private void validateStorage(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        if (spec.storage() == null) {
            warnings.add("spec.storage is not specified, using defaults");
            return;
        }

        var storage = spec.storage();

        // Validate partition key exists if specified
        if (storage.partitionKey() != null && spec.schema() != null) {
            boolean found = spec.schema().fields().stream()
                    .anyMatch(f -> storage.partitionKey().equals(f.name()));
            if (!found) {
                errors.add("storage.partitionKey references unknown field: " + storage.partitionKey());
            }
        }

        // Validate sort key exists if specified
        if (storage.sortKey() != null && spec.schema() != null) {
            boolean found = spec.schema().fields().stream()
                    .anyMatch(f -> storage.sortKey().equals(f.name()));
            if (!found) {
                errors.add("storage.sortKey references unknown field: " + storage.sortKey());
            }
        }
    }

    /**
     * Validate event collection conforms to libs:event-core Event model.
     */
    private void validateEventCollection(
            RawCollectionConfig config,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        var schema = spec.schema();

        if (schema == null || schema.fields() == null) {
            return;
        }

        // Collect field names and types
        Map<String, String> fieldTypes = new HashMap<>();
        for (var field : schema.fields()) {
            fieldTypes.put(field.name(), field.type());
        }

        // Check required event model fields
        for (String requiredField : EVENT_MODEL_REQUIRED_FIELDS) {
            if (!fieldTypes.containsKey(requiredField)) {
                errors.add(
                        "Event collection MUST have field '" + requiredField
                        + "' (libs:event-core alignment)"
                );
            }
        }

        // Validate field types match event model
        validateEventFieldType(fieldTypes, "eventId", "uuid", errors);
        validateEventFieldType(fieldTypes, "aggregateId", "uuid", errors);
        validateEventFieldType(fieldTypes, "aggregateVersion", "long", errors);
        validateEventFieldType(fieldTypes, "correlationId", "uuid", errors);
        validateEventFieldType(fieldTypes, "timestamp", "timestamp", errors);
        validateEventFieldType(fieldTypes, "tenantId", "string", errors);
        validateEventFieldType(fieldTypes, "payload", "object", errors);

        // Validate immutability for event fields
        for (var field : schema.fields()) {
            if (EVENT_MODEL_REQUIRED_FIELDS.contains(field.name()) && !field.immutable()) {
                warnings.add(
                        "Event field '" + field.name() + "' should be immutable"
                );
            }
        }

        // Validate event sourcing config
        if (spec.eventSourcing() == null) {
            warnings.add("Event collection should have eventSourcing configuration");
        } else if (!spec.eventSourcing().appendOnly()) {
            errors.add("Event collection MUST be append-only (immutable)");
        }
    }

    private void validateEventFieldType(
            Map<String, String> fieldTypes,
            String fieldName,
            String expectedType,
            List<String> errors) {

        String actualType = fieldTypes.get(fieldName);
        if (actualType != null && !expectedType.equalsIgnoreCase(actualType)) {
            errors.add(
                    String.format(
                            "Event field '%s' should be type '%s', got '%s'",
                            fieldName, expectedType, actualType
                    )
            );
        }
    }

    // ========== Multi-Tenancy Validation ==========
    /**
     * Validates multi-tenancy constraints for a configuration.
     *
     * <p>
     * Checks:
     * <ul>
     * <li>Tenant ID format (alphanumeric, hyphens, underscores)</li>
     * <li>No cross-tenant references in schema</li>
     * <li>Tenant isolation in storage config</li>
     * </ul>
     *
     * @param config raw config to validate
     * @param expectedTenantId the expected tenant ID (from request context)
     * @return validation result with tenant-specific errors
     */
    public ValidationResult validateTenancy(RawCollectionConfig config, String expectedTenantId) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config.metadata() == null) {
            errors.add("Missing metadata for tenancy validation");
            return new ValidationResult(false, errors, warnings);
        }

        String configTenantId = config.metadata().namespace();

        // Validate tenant ID format
        if (!isValidTenantId(configTenantId)) {
            errors.add("Invalid tenant ID format: " + configTenantId
                    + ". Must be alphanumeric with optional hyphens/underscores (3-64 chars)");
        }

        // Verify tenant matches expected
        if (expectedTenantId != null && !expectedTenantId.equals(configTenantId)) {
            errors.add(String.format(
                    "Tenant ID mismatch: config has '%s' but expected '%s'",
                    configTenantId, expectedTenantId));
        }

        // Check for cross-tenant references
        validateNoCrossTenantReferences(config, configTenantId, errors, warnings);

        // Validate tenant isolation in storage
        validateTenantStorageIsolation(config, configTenantId, errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private boolean isValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.length() < 3 || tenantId.length() > 64) {
            return false;
        }
        return tenantId.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
    }

    private void validateNoCrossTenantReferences(
            RawCollectionConfig config,
            String tenantId,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        if (spec == null || spec.schema() == null || spec.schema().fields() == null) {
            return;
        }

        for (var field : spec.schema().fields()) {
            // Check reference fields
            if (field.reference() != null) {
                String refCollection = field.reference();
                // Reference format: "tenant/collection" or just "collection" (same tenant)
                if (refCollection.contains("/")) {
                    String refTenant = refCollection.split("/")[0];
                    if (!refTenant.equals(tenantId)) {
                        errors.add(String.format(
                                "Cross-tenant reference not allowed: field '%s' references tenant '%s'",
                                field.name(), refTenant));
                    }
                }
            }

            // Check join fields
            if (field.join() != null) {
                String joinCollection = field.join();
                if (joinCollection.contains("/")) {
                    String joinTenant = joinCollection.split("/")[0];
                    if (!joinTenant.equals(tenantId)) {
                        errors.add(String.format(
                                "Cross-tenant join not allowed: field '%s' joins tenant '%s'",
                                field.name(), joinTenant));
                    }
                }
            }
        }
    }

    private void validateTenantStorageIsolation(
            RawCollectionConfig config,
            String tenantId,
            List<String> errors,
            List<String> warnings) {

        var spec = config.spec();
        if (spec == null || spec.storage() == null) {
            return;
        }

        var storage = spec.storage();

        // Validate partition key includes tenant if not using dedicated storage
        if (storage.shared() != null && storage.shared()) {
            // Shared storage requires tenant in partition key
            if (storage.partitionKey() == null || !storage.partitionKey().contains("tenantId")) {
                warnings.add("Shared storage should include 'tenantId' in partition key for isolation");
            }
        }

        // Validate storage profile is tenant-accessible
        if (storage.profile() != null && storage.profile().contains("/")) {
            String profileTenant = storage.profile().split("/")[0];
            if (!profileTenant.equals(tenantId) && !"default".equals(profileTenant)) {
                errors.add(String.format(
                        "Storage profile '%s' belongs to different tenant",
                        storage.profile()));
            }
        }
    }

    private boolean isValidName(String name) {
        return name.matches("^[a-z][a-z0-9-]*[a-z0-9]$") || name.matches("^[a-z]$");
    }

    /**
     * Result of configuration validation.
     */
    public record ValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings
    ) {
        /**
         * Create a successful validation result.
         */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    /**
     * Create a failed validation result.
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }
}
}
