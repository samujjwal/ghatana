/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.validation;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.FieldValidation;
import com.ghatana.datacloud.entity.MetaField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates entity data maps against registered collection schemas at the API boundary.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>Required-field enforcement</li>
 *   <li>Type coercion validation (string, number, boolean, timestamp)</li>
 *   <li>Numeric range constraints (min/max)</li>
 *   <li>String length constraints (minLength/maxLength)</li>
 *   <li>Regex pattern enforcement</li>
 *   <li>Enum (allowlist) validation</li>
 *   <li>Thread-safe schema registration and eviction</li>
 *   <li>Unknown-field detection (optional strict mode)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EntitySchemaValidator validator = EntitySchemaValidator.create();
 * validator.registerSchema("tenant-a", "products", productFields);
 *
 * ValidationResult result = validator.validate("tenant-a", "products", entityData);
 * if (!result.valid()) {
 *     throw new ValidationException(result.violations());
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose API-level entity schema validation with per-tenant schema registry
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EntitySchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(EntitySchemaValidator.class);

    /**
     * Schema key: "{tenantId}/{collection}" → ordered field list.
     * Operations are O(1) and lock-free.
     */
    private final ConcurrentHashMap<String, List<MetaField>> schemas = new ConcurrentHashMap<>();

    /** When true, fields not present in the schema are reported as violations. */
    private final boolean strictMode;

    private EntitySchemaValidator(boolean strictMode) {
        this.strictMode = strictMode;
    }

    /**
     * Creates a validator in lenient mode (unknown fields are allowed).
     *
     * @return new validator instance
     */
    public static EntitySchemaValidator create() {
        return new EntitySchemaValidator(false);
    }

    /**
     * Creates a validator in strict or lenient mode.
     *
     * @param strictMode {@code true} to reject unknown fields
     * @return new validator instance
     */
    public static EntitySchemaValidator create(boolean strictMode) {
        return new EntitySchemaValidator(strictMode);
    }

    // =========================================================================
    // Schema Registration
    // =========================================================================

    /**
     * Registers or replaces the schema for a collection.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param fields     ordered list of field definitions
     */
    public void registerSchema(String tenantId, String collection, List<MetaField> fields) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(collection, "collection required");
        Objects.requireNonNull(fields, "fields required");
        schemas.put(schemaKey(tenantId, collection), Collections.unmodifiableList(new ArrayList<>(fields)));
        log.debug("Registered schema for tenant={} collection={} fields={}", tenantId, collection, fields.size());
    }

    /**
     * Removes the schema for a collection.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     */
    public void evictSchema(String tenantId, String collection) {
        schemas.remove(schemaKey(tenantId, collection));
    }

    /**
     * Returns {@code true} if a schema is registered for the given tenant/collection pair.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @return {@code true} if schema exists
     */
    public boolean hasSchema(String tenantId, String collection) {
        return schemas.containsKey(schemaKey(tenantId, collection));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Validates entity data against the registered schema.
     *
     * <p>If no schema is registered for the collection, validation is skipped
     * and an {@link ValidationResult#unregistered()} is returned — the entity
     * is allowed through. This preserves schema-optional semantics.
     *
     * @param tenantId   tenant identifier
     * @param collection collection name
     * @param data       entity data map
     * @return {@link ValidationResult} — always non-null
     */
    public ValidationResult validate(String tenantId, String collection, Map<String, Object> data) {
        List<MetaField> fields = schemas.get(schemaKey(tenantId, collection));
        if (fields == null) {
            // No schema registered — pass through
            return ValidationResult.unregistered();
        }
        if (data == null) {
            return ValidationResult.failure(List.of("Entity data must not be null"));
        }

        List<String> violations = new ArrayList<>();

        for (MetaField field : fields) {
            String fieldName = field.getName();
            Object value = data.get(fieldName);
            validateField(field, value, violations);
        }

        if (strictMode) {
            for (String key : data.keySet()) {
                boolean known = fields.stream().anyMatch(f -> f.getName().equals(key));
                if (!known) {
                    violations.add("Unknown field '" + key + "' not defined in collection schema");
                }
            }
        }

        return violations.isEmpty() ? ValidationResult.success() : ValidationResult.failure(violations);
    }

    // =========================================================================
    // Per-field Validation
    // =========================================================================

    private void validateField(MetaField field, Object value, List<String> violations) {
        boolean isPresent = value != null;

        // 1. Required
        FieldValidation fv = field.getValidationTyped();

        boolean required = Boolean.TRUE.equals(fv.required()) || Boolean.TRUE.equals(field.getRequired());
        if (required && !isPresent) {
            violations.add("Field '" + field.getName() + "' is required but missing");
            return; // further checks on null value are meaningless
        }

        if (!isPresent) {
            return; // optional field not present — skip further checks
        }

        // 2. Type check
        DataType expectedType = field.getType();
        if (expectedType != null) {
            String typeViolation = checkType(field.getName(), value, expectedType);
            if (typeViolation != null) {
                violations.add(typeViolation);
                return; // type mismatch makes range/pattern checks unreliable
            }
        }

        String valueStr = value.toString();

        // 3. Numeric constraints
        if (isNumeric(expectedType) && value instanceof Number num) {
            double dv = num.doubleValue();
            if (fv.min() != null && dv < fv.min()) {
                violations.add("Field '" + field.getName() + "' value " + dv
                    + " is below minimum " + fv.min());
            }
            if (fv.max() != null && dv > fv.max()) {
                violations.add("Field '" + field.getName() + "' value " + dv
                    + " exceeds maximum " + fv.max());
            }
        }

        // 4. String constraints
        if (expectedType == DataType.STRING || value instanceof String) {
            int len = valueStr.length();
            if (fv.minLength() != null && len < fv.minLength()) {
                violations.add("Field '" + field.getName() + "' length " + len
                    + " is below minimum " + fv.minLength());
            }
            if (fv.maxLength() != null && len > fv.maxLength()) {
                violations.add("Field '" + field.getName() + "' length " + len
                    + " exceeds maximum " + fv.maxLength());
            }
            if (fv.pattern() != null) {
                try {
                    if (!Pattern.matches(fv.pattern(), valueStr)) {
                        violations.add("Field '" + field.getName() + "' value '"
                            + valueStr + "' does not match pattern '" + fv.pattern() + "'");
                    }
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid regex pattern '{}' on field '{}'", fv.pattern(), field.getName());
                }
            }
        }

        // 5. Enum allowlist
        if (fv.enumValues() != null && !fv.enumValues().isEmpty()) {
            if (!fv.enumValues().contains(valueStr)) {
                violations.add("Field '" + field.getName() + "' value '" + valueStr
                    + "' is not in allowed values: " + fv.enumValues());
            }
        }
    }

    private String checkType(String fieldName, Object value, DataType expected) {
        return switch (expected) {
            case STRING   -> (value instanceof String)  ? null : "Field '" + fieldName + "' expects STRING, got " + value.getClass().getSimpleName();
            case NUMBER   -> (value instanceof Number)  ? null : "Field '" + fieldName + "' expects NUMBER, got " + value.getClass().getSimpleName();
            case BOOLEAN  -> (value instanceof Boolean) ? null : "Field '" + fieldName + "' expects BOOLEAN, got " + value.getClass().getSimpleName();
            case EMAIL -> {
                if (value instanceof String email) {
                    yield email.contains("@") ? null :
                        "Field '" + fieldName + "' expects a valid email address, got '" + email + "'";
                }
                yield "Field '" + fieldName + "' expects EMAIL string, got " + value.getClass().getSimpleName();
            }
            case DATETIME -> {
                if (value instanceof String ts) {
                    try {
                        Instant.parse(ts);
                        yield null;
                    } catch (DateTimeParseException e) {
                        yield "Field '" + fieldName + "' expects ISO-8601 DATETIME, got '" + ts + "'";
                    }
                }
                yield "Field '" + fieldName + "' expects DATETIME string, got " + value.getClass().getSimpleName();
            }
            // ARRAY, JSON, EMBEDDED, REFERENCE, etc. — structural validation out of scope; pass through
            default -> null;
        };
    }

    private static boolean isNumeric(DataType type) {
        return type == DataType.NUMBER;
    }

    private static String schemaKey(String tenantId, String collection) {
        // Use '/' as separator — both values are validated to be non-null
        return tenantId + "/" + collection;
    }
}
