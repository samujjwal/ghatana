package com.ghatana.appplatform.eventstore.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.appplatform.eventstore.domain.CompatibilityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Checks whether a new JSON Schema version is compatible with its predecessor.
 *
 * <p>Compatibility semantics (JSON Schema Draft-07 subset):
 * <ul>
 *   <li><strong>BACKWARD</strong>: consumers using the new schema can still read data written
 *       with the old schema.
 *       <ul>
 *         <li>Adding a new <em>optional</em> property → OK (defaults to absent).</li>
 *         <li>Removing a <em>required</em> property → BREAKING (old data lacks the field).</li>
 *         <li>Removing an optional property → BREAKING (old data may carry the field; new
 *             schema would reject it under {@code additionalProperties: false}, and consumers
 *             expecting the field break).</li>
 *         <li>Changing a property's type → BREAKING.</li>
 *         <li>Narrowing an enum → BREAKING.</li>
 *       </ul>
 *   <li><strong>FORWARD</strong>: consumers using the old schema can still read data written
 *       with the new schema.
 *       <ul>
 *         <li>Adding a new <em>required</em> property → BREAKING (old consumers don't know it).</li>
 *         <li>Removing an optional property → OK (old consumers just won't see it).</li>
 *       </ul>
 *   <li><strong>FULL</strong>: both BACKWARD and FORWARD must hold simultaneously.</li>
 *   <li><strong>NONE</strong>: no compatibility is checked; the result is always compatible.</li>
 * </ul>
 *
 * <p>This checker operates only on the {@code properties}, {@code required}, and {@code type}
 * nodes of the root JSON Schema object. Nested schema composition ({@code $ref}, {@code allOf},
 * etc.) is not resolved — those schemas are treated as opaque.
 *
 * @doc.type class
 * @doc.purpose Checks JSON Schema compatibility between consecutive event schema versions
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public final class SchemaCompatibilityChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Result of a compatibility check.
     *
     * @param compatible   true if the new schema satisfies the required compatibility type.
     * @param violations   human-readable list of compatibility violations (empty when compatible).
     */
    public record CompatibilityResult(boolean compatible, List<String> violations) {

        public static CompatibilityResult ok() {
            return new CompatibilityResult(true, List.of());
        }

        public static CompatibilityResult broken(List<String> vs) {
            return new CompatibilityResult(false, List.copyOf(vs));
        }
    }

    /**
     * Check whether {@code newSchema} is compatible with {@code oldSchema} according to
     * the declared {@code required} compatibility type.
     *
     * @param oldSchema    JSON Schema string of the previous version
     * @param newSchema    JSON Schema string of the candidate new version
     * @param required     declared compatibility requirement
     * @return result containing compatibility status and any violations found
     */
    public CompatibilityResult checkCompatibility(
            String oldSchema,
            String newSchema,
            CompatibilityType required) {

        if (required == CompatibilityType.NONE) {
            return CompatibilityResult.ok();
        }
        try {
            JsonNode oldRoot = MAPPER.readTree(oldSchema);
            JsonNode newRoot = MAPPER.readTree(newSchema);

            List<String> violations = new ArrayList<>();

            if (required == CompatibilityType.BACKWARD || required == CompatibilityType.FULL) {
                checkBackward(oldRoot, newRoot, violations);
            }
            if (required == CompatibilityType.FORWARD || required == CompatibilityType.FULL) {
                checkForward(oldRoot, newRoot, violations);
            }

            return violations.isEmpty()
                ? CompatibilityResult.ok()
                : CompatibilityResult.broken(violations);

        } catch (Exception e) {
            return CompatibilityResult.broken(
                List.of("Failed to parse schemas for compatibility check: " + e.getMessage()));
        }
    }

    /**
     * BACKWARD check: new schema can read data written with old schema.
     *
     * <p>Rules applied:
     * <ol>
     *   <li>No required field present in old schema may be removed.</li>
     *   <li>No property present in old schema may change its {@code type}.</li>
     *   <li>No enum value set may be narrowed (values removed).</li>
     * </ol>
     */
    private void checkBackward(JsonNode oldRoot, JsonNode newRoot, List<String> violations) {
        Set<String> oldRequired  = requiredFields(oldRoot);
        Set<String> newRequired  = requiredFields(newRoot);
        JsonNode    oldProps      = oldRoot.path("properties");
        JsonNode    newProps      = newRoot.path("properties");

        // Rule 1: required fields in old must still exist as required in new
        for (String field : oldRequired) {
            if (!newRequired.contains(field)) {
                violations.add("BACKWARD: required field '" + field
                    + "' was required in old schema but is no longer required or has been removed.");
            }
        }

        // Rule 2: property types must not change
        if (oldProps.isObject() && newProps.isObject()) {
            for (Iterator<String> it = oldProps.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                JsonNode oldType = oldProps.path(field).path("type");
                JsonNode newType = newProps.path(field).path("type");
                if (!oldType.isMissingNode() && !newType.isMissingNode()
                        && !oldType.equals(newType)) {
                    violations.add("BACKWARD: property '" + field + "' type changed from '"
                        + oldType.asText() + "' to '" + newType.asText() + "'.");
                }
            }
        }

        // Rule 3: enum values must not be removed
        if (oldProps.isObject() && newProps.isObject()) {
            for (Iterator<String> it = oldProps.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                JsonNode oldEnum = oldProps.path(field).path("enum");
                JsonNode newEnum = newProps.path(field).path("enum");
                if (oldEnum.isArray() && newEnum.isArray()) {
                    Set<String> oldValues = enumValues(oldEnum);
                    Set<String> newValues = enumValues(newEnum);
                    Set<String> removed = new HashSet<>(oldValues);
                    removed.removeAll(newValues);
                    for (String rv : removed) {
                        violations.add("BACKWARD: enum value '" + rv + "' removed from property '"
                            + field + "'.");
                    }
                }
            }
        }
    }

    /**
     * FORWARD check: old schema can read data written with new schema.
     *
     * <p>Rules applied:
     * <ol>
     *   <li>No new required field may be added (old consumers don't know about it).</li>
     * </ol>
     */
    private void checkForward(JsonNode oldRoot, JsonNode newRoot, List<String> violations) {
        Set<String> oldRequired  = requiredFields(oldRoot);
        Set<String> newRequired  = requiredFields(newRoot);

        for (String field : newRequired) {
            if (!oldRequired.contains(field)) {
                violations.add("FORWARD: required field '" + field
                    + "' is new in the schema — old consumers cannot produce it.");
            }
        }
    }

    private Set<String> requiredFields(JsonNode root) {
        Set<String> required = new HashSet<>();
        JsonNode arr = root.path("required");
        if (arr.isArray()) {
            arr.forEach(n -> required.add(n.asText()));
        }
        return required;
    }

    private Set<String> enumValues(JsonNode enumArray) {
        Set<String> values = new HashSet<>();
        enumArray.forEach(n -> values.add(n.asText()));
        return values;
    }
}
