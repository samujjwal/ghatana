/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.3 — Schema compatibility checker.
 */
package com.ghatana.datacloud.schema;

import com.ghatana.datacloud.schema.EventSchema.SchemaField;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks schema compatibility between versions according to the specified compatibility mode.
 *
 * <h2>Compatibility Rules</h2>
 * <table>
 *   <tr><th>Mode</th><th>Add optional field</th><th>Add required field</th><th>Remove optional field</th><th>Remove required field</th><th>Change type</th></tr>
 *   <tr><td>BACKWARD</td><td>✅</td><td>❌</td><td>✅</td><td>✅ (readers can skip)</td><td>❌</td></tr>
 *   <tr><td>FORWARD</td><td>✅</td><td>✅ (with default)</td><td>❌</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>FULL</td><td>✅</td><td>❌</td><td>❌</td><td>❌</td><td>❌</td></tr>
 *   <tr><td>NONE</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td><td>✅</td></tr>
 * </table>
 *
 * @doc.type class
 * @doc.purpose Validates schema evolution compatibility between versions using BACKWARD, FORWARD, FULL, or NONE modes
 * @doc.layer product
 * @doc.pattern Service
 */
public class SchemaCompatibilityChecker {

    /**
     * Result of a compatibility check.
     *
     * @param compatible whether the schemas are compatible
     * @param violations list of violations if incompatible
     */
    public record CompatibilityResult(
            boolean compatible,
            List<String> violations
    ) {
        public CompatibilityResult {
            violations = violations != null ? List.copyOf(violations) : List.of();
        }

        public static CompatibilityResult ok() {
            return new CompatibilityResult(true, List.of());
        }

        public static CompatibilityResult failed(List<String> violations) {
            return new CompatibilityResult(false, violations);
        }
    }

    /**
     * Checks if the new schema is compatible with the old schema under the given mode.
     *
     * @param oldSchema the existing schema (currently deployed)
     * @param newSchema the proposed schema (to be registered)
     * @param mode the compatibility mode to enforce
     * @return the compatibility result
     */
    public CompatibilityResult check(EventSchema oldSchema, EventSchema newSchema,
                                     CompatibilityMode mode) {
        Objects.requireNonNull(oldSchema, "oldSchema required");
        Objects.requireNonNull(newSchema, "newSchema required");
        Objects.requireNonNull(mode, "mode required");

        if (mode == CompatibilityMode.NONE) {
            return CompatibilityResult.ok();
        }

        if (!oldSchema.format().equals(newSchema.format())) {
            return CompatibilityResult.failed(
                    List.of("Schema format changed from " + oldSchema.format() +
                            " to " + newSchema.format()));
        }

        List<String> violations = new ArrayList<>();

        Map<String, SchemaField> oldFields = fieldMap(oldSchema.fields());
        Map<String, SchemaField> newFields = fieldMap(newSchema.fields());

        switch (mode) {
            case BACKWARD -> checkBackward(oldFields, newFields, violations);
            case FORWARD -> checkForward(oldFields, newFields, violations);
            case FULL -> checkFull(oldFields, newFields, violations);
            default -> { /* NONE already handled */ }
        }

        return violations.isEmpty()
                ? CompatibilityResult.ok()
                : CompatibilityResult.failed(violations);
    }

    /**
     * BACKWARD: new schema can read old data.
     * - Adding optional fields: OK (old data won't have them, reader uses default)
     * - Adding required fields without default: VIOLATION (old data won't have them)
     * - Removing fields: OK (reader ignores extra fields from old data)
     * - Changing field type: VIOLATION
     */
    private void checkBackward(Map<String, SchemaField> oldFields,
                               Map<String, SchemaField> newFields,
                               List<String> violations) {
        // New required fields without defaults are not backward-compatible
        for (Map.Entry<String, SchemaField> entry : newFields.entrySet()) {
            SchemaField newField = entry.getValue();
            if (!oldFields.containsKey(entry.getKey()) && newField.required() && newField.defaultValue() == null) {
                violations.add("BACKWARD: New required field '" + entry.getKey() +
                        "' has no default — old data will be unreadable");
            }
        }

        // Type changes on existing fields
        checkTypeChanges(oldFields, newFields, "BACKWARD", violations);
    }

    /**
     * FORWARD: old schema can read new data.
     * - Adding fields: OK (old reader ignores unknown fields)
     * - Removing optional fields: VIOLATION (old reader expects them)
     * - Removing required fields: VIOLATION
     * - Changing field type: VIOLATION
     */
    private void checkForward(Map<String, SchemaField> oldFields,
                              Map<String, SchemaField> newFields,
                              List<String> violations) {
        // Removed fields that existed in old schema
        for (Map.Entry<String, SchemaField> entry : oldFields.entrySet()) {
            if (!newFields.containsKey(entry.getKey())) {
                violations.add("FORWARD: Field '" + entry.getKey() +
                        "' removed — old readers will fail on new data");
            }
        }

        // Type changes on existing fields
        checkTypeChanges(oldFields, newFields, "FORWARD", violations);
    }

    /**
     * FULL: both backward and forward compatible.
     * - Adding optional fields: OK
     * - Adding required fields: VIOLATION
     * - Removing any fields: VIOLATION
     * - Changing field type: VIOLATION
     */
    private void checkFull(Map<String, SchemaField> oldFields,
                           Map<String, SchemaField> newFields,
                           List<String> violations) {
        // Removed fields
        for (String oldFieldName : oldFields.keySet()) {
            if (!newFields.containsKey(oldFieldName)) {
                violations.add("FULL: Field '" + oldFieldName + "' removed — breaks forward compatibility");
            }
        }

        // New required fields
        for (Map.Entry<String, SchemaField> entry : newFields.entrySet()) {
            if (!oldFields.containsKey(entry.getKey()) && entry.getValue().required()) {
                violations.add("FULL: New required field '" + entry.getKey() +
                        "' — breaks backward compatibility");
            }
        }

        checkTypeChanges(oldFields, newFields, "FULL", violations);
    }

    private void checkTypeChanges(Map<String, SchemaField> oldFields,
                                  Map<String, SchemaField> newFields,
                                  String mode, List<String> violations) {
        for (Map.Entry<String, SchemaField> entry : oldFields.entrySet()) {
            SchemaField newField = newFields.get(entry.getKey());
            if (newField != null && !entry.getValue().type().equals(newField.type())) {
                violations.add(mode + ": Field '" + entry.getKey() +
                        "' type changed from " + entry.getValue().type() +
                        " to " + newField.type());
            }
        }
    }

    private static Map<String, SchemaField> fieldMap(List<SchemaField> fields) {
        return fields.stream().collect(Collectors.toMap(SchemaField::name, f -> f));
    }
}
