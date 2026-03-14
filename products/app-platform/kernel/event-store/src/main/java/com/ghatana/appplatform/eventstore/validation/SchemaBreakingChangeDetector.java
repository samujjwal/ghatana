package com.ghatana.appplatform.eventstore.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Detects breaking schema changes and blocks event schema registration until a
 * {@link MigrationPlan} is provided that covers every affected field.
 *
 * <h2>What is a breaking change?</h2>
 * <ul>
 *   <li><strong>Required field removal</strong> — a field present in the old
 *       {@code required} array is absent from the new {@code properties} completely.
 *       Old producers still emit the field; new consumers may not know how to handle it.</li>
 *   <li><strong>Type change</strong> — a property's {@code type} keyword changes between
 *       schema versions; existing stored events become unreadable under the new schema.</li>
 *   <li><strong>Enum narrowing</strong> — a property's {@code enum} set shrinks; stored
 *       events may carry values that the new schema now rejects.</li>
 * </ul>
 *
 * <h2>Registration gate</h2>
 * <ul>
 *   <li>{@link #mayRegister(String, String)} — only allows registration when there are no
 *       breaking changes at all.</li>
 *   <li>{@link #mayRegister(String, String, MigrationPlan)} — allows registration of a
 *       breaking schema when the supplied plan documents a remediation strategy for every
 *       removed required field and has a non-blank justification and plan ID.</li>
 * </ul>
 *
 * <p>This class delegates semantic version classification to {@link SchemaSemanticVersionGuard}.
 * It does not duplicate the compatibility check logic.
 *
 * @doc.type class
 * @doc.purpose Detects breaking schema changes and gates registration behind a migration plan (STORY-K05-030)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SchemaBreakingChangeDetector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchemaSemanticVersionGuard versionGuard;

    // ── Public model types ────────────────────────────────────────────────────

    /**
     * Migration remediation strategies for a breaking schema change.
     */
    public enum MigrationStrategy {
        /** Back-fill missing or new required field values in historical records. */
        BACKFILL,
        /** Consumers will substitute a default value when the field is absent. */
        DEFAULT_VALUE,
        /** All consumers must be deployed to the new schema before it goes live. */
        CONSUMER_UPGRADE,
        /** Old schema version stays active during a grace period; consumers migrate gradually. */
        DEPRECATION_WINDOW
    }

    /**
     * Documents the remediation plan for registering a breaking schema change.
     *
     * @param planId          unique plan identifier (non-blank, required)
     * @param author          user or service submitting the plan (non-blank, required)
     * @param eventType       event type the migration pertains to
     * @param coveredFields   set of field names (removed required fields) that this plan covers
     * @param strategy        primary migration strategy
     * @param justification   human-readable rationale (non-blank, required)
     */
    public record MigrationPlan(
            String planId,
            String author,
            String eventType,
            Set<String> coveredFields,
            MigrationStrategy strategy,
            String justification
    ) {
        /**
         * Validates that all mandatory fields are non-blank and the covered field set
         * is non-null.
         *
         * @throws IllegalArgumentException if any required field is blank or coveredFields is null
         */
        public MigrationPlan {
            requireNonBlank(planId, "planId");
            requireNonBlank(author, "author");
            requireNonBlank(justification, "justification");
            Objects.requireNonNull(coveredFields, "coveredFields");
            Objects.requireNonNull(strategy, "strategy");
            coveredFields = Set.copyOf(coveredFields);
        }

        private static void requireNonBlank(String v, String name) {
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("MigrationPlan." + name + " must not be blank");
            }
        }
    }

    /**
     * Structural breaking-change analysis between two consecutive schema versions.
     *
     * @param removedRequiredFields fields present in the old {@code required} array that
     *                              have been entirely removed from the new {@code properties}
     * @param typeChangedFields     fields whose {@code type} keyword changed
     * @param narrowedEnumFields    fields whose {@code enum} set decreased in size
     * @param hasBreakingChanges    true when any of the above lists is non-empty
     * @param requiresMigrationPlan true — currently always mirrors {@code hasBreakingChanges}
     */
    public record BreakingChangeReport(
            List<String> removedRequiredFields,
            List<String> typeChangedFields,
            List<String> narrowedEnumFields,
            boolean hasBreakingChanges,
            boolean requiresMigrationPlan
    ) {
        /** Short human-readable description for use in error/log messages. */
        public String summary() {
            if (!hasBreakingChanges) return "No breaking changes detected.";
            return "Breaking changes: removedRequired=" + removedRequiredFields
                + " typeChanged=" + typeChangedFields
                + " enumNarrowed=" + narrowedEnumFields;
        }
    }

    // ── Construction ─────────────────────────────────────────────────────────

    /**
     * Creates a detector backed by a fresh {@link SchemaSemanticVersionGuard}.
     */
    public SchemaBreakingChangeDetector() {
        this(new SchemaSemanticVersionGuard());
    }

    /**
     * Creates a detector using a provided {@link SchemaSemanticVersionGuard}
     * (allows injection for testing).
     *
     * @param versionGuard the guard to use for MAJOR classification
     */
    public SchemaBreakingChangeDetector(SchemaSemanticVersionGuard versionGuard) {
        this.versionGuard = Objects.requireNonNull(versionGuard, "versionGuard");
    }

    // ── Core API ─────────────────────────────────────────────────────────────

    /**
     * Analyses the structural differences between {@code oldSchema} and {@code newSchema}
     * and returns a detailed report of any breaking changes found.
     *
     * @param oldSchema JSON Schema string of the currently registered version
     * @param newSchema JSON Schema string of the candidate new version
     * @return a {@link BreakingChangeReport} describing all breaking changes found
     */
    public BreakingChangeReport detect(String oldSchema, String newSchema) {
        Objects.requireNonNull(oldSchema, "oldSchema");
        Objects.requireNonNull(newSchema, "newSchema");

        try {
            JsonNode oldRoot = MAPPER.readTree(oldSchema);
            JsonNode newRoot = MAPPER.readTree(newSchema);

            List<String> removedRequired = detectRemovedRequiredFields(oldRoot, newRoot);
            List<String> typeChanged     = detectTypeChanges(oldRoot, newRoot);
            List<String> enumNarrowed    = detectEnumNarrowing(oldRoot, newRoot);

            boolean hasBreaking = !removedRequired.isEmpty()
                               || !typeChanged.isEmpty()
                               || !enumNarrowed.isEmpty();

            return new BreakingChangeReport(
                List.copyOf(removedRequired),
                List.copyOf(typeChanged),
                List.copyOf(enumNarrowed),
                hasBreaking,
                hasBreaking
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Schema parse error during breaking change detection", e);
        }
    }

    /**
     * Returns {@code true} when the schema change has no breaking changes and registration
     * may proceed without a migration plan.
     *
     * @param oldSchema currently registered schema JSON
     * @param newSchema candidate new schema JSON
     * @return true if registration is allowed unconditionally
     */
    public boolean mayRegister(String oldSchema, String newSchema) {
        return !detect(oldSchema, newSchema).hasBreakingChanges();
    }

    /**
     * Returns {@code true} when registration may proceed because:
     * <ol>
     *   <li>There are no breaking changes, <em>or</em></li>
     *   <li>The provided {@code migrationPlan} covers every removed required field and
     *       has a valid plan ID and justification.</li>
     * </ol>
     *
     * @param oldSchema     currently registered schema JSON
     * @param newSchema     candidate new schema JSON
     * @param migrationPlan the migration plan to evaluate; may be {@code null}
     *                      (causes rejection when there are breaking changes)
     * @return true if registration is allowed given the provided plan
     */
    public boolean mayRegister(String oldSchema, String newSchema, MigrationPlan migrationPlan) {
        BreakingChangeReport report = detect(oldSchema, newSchema);
        if (!report.hasBreakingChanges()) return true;
        if (migrationPlan == null) return false;

        // All removed required fields must be covered by the migration plan
        List<String> uncovered = report.removedRequiredFields().stream()
            .filter(field -> !migrationPlan.coveredFields().contains(field))
            .toList();
        return uncovered.isEmpty();
    }

    // ── Structural analysis ───────────────────────────────────────────────────

    /**
     * Finds required fields from the old schema whose property definitions are
     * entirely absent from the new schema.
     */
    private List<String> detectRemovedRequiredFields(JsonNode oldRoot, JsonNode newRoot) {
        Set<String> oldRequired = requiredSet(oldRoot);
        Set<String> newProperties = propertyNames(newRoot);

        List<String> removed = new ArrayList<>();
        for (String field : oldRequired) {
            if (!newProperties.contains(field)) {
                removed.add(field);
            }
        }
        return removed;
    }

    /**
     * Finds properties whose {@code type} keyword changed between old and new schemas.
     */
    private List<String> detectTypeChanges(JsonNode oldRoot, JsonNode newRoot) {
        JsonNode oldProps = oldRoot.path("properties");
        JsonNode newProps = newRoot.path("properties");
        if (oldProps.isMissingNode() || newProps.isMissingNode()) return List.of();

        List<String> changed = new ArrayList<>();
        Iterator<String> names = oldProps.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            JsonNode oldType = oldProps.path(field).path("type");
            JsonNode newType = newProps.path(field).path("type");
            if (!oldType.isMissingNode() && !newType.isMissingNode()
                    && !oldType.asText().equals(newType.asText())) {
                changed.add(field);
            }
        }
        return changed;
    }

    /**
     * Finds properties where the {@code enum} array in the new schema is a strict
     * subset of the old {@code enum} array (values were removed).
     */
    private List<String> detectEnumNarrowing(JsonNode oldRoot, JsonNode newRoot) {
        JsonNode oldProps = oldRoot.path("properties");
        JsonNode newProps = newRoot.path("properties");
        if (oldProps.isMissingNode() || newProps.isMissingNode()) return List.of();

        List<String> narrowed = new ArrayList<>();
        Iterator<String> names = oldProps.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            JsonNode oldEnum = oldProps.path(field).path("enum");
            JsonNode newEnum = newProps.path(field).path("enum");
            if (!oldEnum.isArray() || !newEnum.isArray()) continue;

            Set<String> oldValues = new HashSet<>();
            oldEnum.forEach(v -> oldValues.add(v.asText()));
            Set<String> newValues = new HashSet<>();
            newEnum.forEach(v -> newValues.add(v.asText()));

            if (!newValues.containsAll(oldValues)) {
                narrowed.add(field);
            }
        }
        return narrowed;
    }

    // ── Schema parsing utilities ──────────────────────────────────────────────

    private static Set<String> requiredSet(JsonNode root) {
        Set<String> result = new HashSet<>();
        JsonNode req = root.path("required");
        if (req.isArray()) {
            req.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private static Set<String> propertyNames(JsonNode root) {
        Set<String> result = new HashSet<>();
        JsonNode props = root.path("properties");
        if (!props.isMissingNode()) {
            props.fieldNames().forEachRemaining(result::add);
        }
        return result;
    }
}
