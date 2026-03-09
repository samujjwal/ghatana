package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting and classifying schema changes between collection versions.
 *
 * <p><b>Purpose</b><br>
 * Analyzes schema differences between two versions of a MetaCollection to:
 * - Detect fields added, removed, or modified
 * - Classify changes as breaking vs non-breaking
 * - Suggest appropriate version increments (MAJOR/MINOR/PATCH)
 * - Generate migration recommendations
 * - Support rollback planning
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SchemaDiffService diffService = new SchemaDiffService(metrics);
 *
 * // Compare two collection versions
 * SchemaDiff diff = diffService.compareSchemas(oldCollection, newCollection);
 *
 * // Check if changes are breaking
 * if (diff.hasBreakingChanges()) {
 *     logger.warn("Breaking changes detected: {}", diff.getBreakingChanges());
 * }
 *
 * // Get recommended version bump
 * VersionBump bump = diffService.recommendVersionBump(diff);
 * // Returns: MAJOR, MINOR, or PATCH
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Service in application layer (hexagonal architecture)
 * - Used by CollectionService for schema validation
 * - Supports schema evolution workflow
 * - Emits metrics for schema changes
 *
 * <p><b>Change Classification</b><br>
 * - **MAJOR (breaking)**: Field removed, type changed, constraints tightened
 * - **MINOR (non-breaking)**: Field added (nullable), constraints relaxed
 * - **PATCH (metadata only)**: Label/description changed, no behavioral impact
 *
 * <p><b>Thread Safety</b><br>
 * Stateless service - thread-safe. All state passed via parameters.
 *
 * @see MetaCollection
 * @see MetaField
 * @see SchemaDiff
 * @doc.type class
 * @doc.purpose Schema diff and evolution analysis for collections
 * @doc.layer product
 * @doc.pattern Service (Application Layer)
 */
public class SchemaDiffService {

    private static final Logger logger = LoggerFactory.getLogger(SchemaDiffService.class);

    private final MetricsCollector metrics;

    /**
     * Creates a new schema diff service.
     *
     * @param metrics the metrics collector (required)
     * @throws NullPointerException if metrics is null
     */
    public SchemaDiffService(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "MetricsCollector must not be null");
    }

    /**
     * Compares two collection schemas and generates a diff.
     *
     * <p><b>Comparison Logic</b><br>
     * 1. Identify fields added, removed, and common
     * 2. For common fields, detect type/constraint changes
     * 3. Classify each change as breaking or non-breaking
     * 4. Generate migration recommendations
     *
     * @param oldSchema the previous collection schema (required)
     * @param newSchema the new collection schema (required)
     * @return SchemaDiff containing all detected changes
     * @throws NullPointerException if oldSchema or newSchema is null
     */
    public SchemaDiff compareSchemas(MetaCollection oldSchema, MetaCollection newSchema) {
        Objects.requireNonNull(oldSchema, "Old schema must not be null");
        Objects.requireNonNull(newSchema, "New schema must not be null");

        if (!oldSchema.getName().equals(newSchema.getName())) {
            throw new IllegalArgumentException("Cannot compare different collections: "
                + oldSchema.getName() + " vs " + newSchema.getName());
        }

        logger.debug("Comparing schemas: collection={}, oldVersion={}, newVersion={}",
            oldSchema.getName(), oldSchema.getSchemaVersion(), newSchema.getSchemaVersion());

        // Build field maps for comparison
        Map<String, MetaField> oldFields = buildFieldMap(oldSchema);
        Map<String, MetaField> newFields = buildFieldMap(newSchema);

        SchemaDiff diff = new SchemaDiff(oldSchema.getSchemaVersion(), newSchema.getSchemaVersion());

        // Find added fields
        newFields.forEach((name, field) -> {
            if (!oldFields.containsKey(name)) {
                FieldChange change = FieldChange.added(field);
                diff.addChange(change);
                logger.debug("Field added: {}", name);
            }
        });

        // Find removed fields
        oldFields.forEach((name, field) -> {
            if (!newFields.containsKey(name)) {
                FieldChange change = FieldChange.removed(field);
                diff.addChange(change);
                logger.warn("Field removed (BREAKING): {}", name);
            }
        });

        // Find modified fields
        oldFields.forEach((oldName, oldField) -> {
            if (newFields.containsKey(oldName)) {
                MetaField newField = newFields.get(oldName);
                List<FieldChange> changes = detectFieldChanges(oldField, newField);
                changes.forEach(diff::addChange);
            }
        });

        // Emit metrics
        metrics.incrementCounter("schema.diff.generated",
            "collection", oldSchema.getName(),
            "breakingChanges", String.valueOf(diff.hasBreakingChanges()));
        metrics.increment("schema.diff.changes", diff.getAllChanges().size(),
            Map.of("collection", oldSchema.getName()));

        logger.info("Schema diff completed: collection={}, changes={}, breaking={}",
            oldSchema.getName(), diff.getAllChanges().size(), diff.hasBreakingChanges());

        return diff;
    }

    /**
     * Recommends a version bump based on schema diff.
     *
     * <p><b>Version Bump Rules</b><br>
     * - MAJOR: Any breaking change detected
     * - MINOR: Non-breaking changes (fields added, constraints relaxed)
     * - PATCH: Metadata-only changes (labels, descriptions)
     *
     * @param diff the schema diff (required)
     * @return recommended version bump type
     */
    public VersionBump recommendVersionBump(SchemaDiff diff) {
        Objects.requireNonNull(diff, "Schema diff must not be null");

        if (diff.hasBreakingChanges()) {
            logger.info("Recommending MAJOR version bump due to breaking changes");
            return VersionBump.MAJOR;
        }

        if (diff.hasNonBreakingChanges()) {
            logger.info("Recommending MINOR version bump due to non-breaking changes");
            return VersionBump.MINOR;
        }

        if (diff.hasMetadataChanges()) {
            logger.info("Recommending PATCH version bump due to metadata changes");
            return VersionBump.PATCH;
        }

        logger.info("No changes detected, no version bump needed");
        return VersionBump.NONE;
    }

    /**
     * Increments a semantic version based on bump type.
     *
     * <p><b>Format</b><br>
     * Semantic versioning: MAJOR.MINOR.PATCH (e.g., "1.2.3")
     *
     * <p><b>Increment Rules</b><br>
     * - MAJOR: 1.2.3 → 2.0.0 (reset minor and patch)
     * - MINOR: 1.2.3 → 1.3.0 (reset patch)
     * - PATCH: 1.2.3 → 1.2.4
     * - NONE: 1.2.3 → 1.2.3 (no change)
     *
     * @param currentVersion the current version (required, format: "MAJOR.MINOR.PATCH")
     * @param bump the version bump type (required)
     * @return the new version string
     * @throws IllegalArgumentException if version format is invalid
     */
    public String incrementVersion(String currentVersion, VersionBump bump) {
        Objects.requireNonNull(currentVersion, "Current version must not be null");
        Objects.requireNonNull(bump, "Version bump must not be null");

        if (bump == VersionBump.NONE) {
            return currentVersion;
        }

        String[] parts = currentVersion.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid version format: " + currentVersion
                + " (expected MAJOR.MINOR.PATCH)");
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);

            switch (bump) {
                case MAJOR:
                    return (major + 1) + ".0.0";
                case MINOR:
                    return major + "." + (minor + 1) + ".0";
                case PATCH:
                    return major + "." + minor + "." + (patch + 1);
                default:
                    return currentVersion;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version numbers in: " + currentVersion, e);
        }
    }

    /**
     * Builds a field map from collection for quick lookup.
     *
     * @param collection the collection
     * @return map of field name to MetaField
     */
    private Map<String, MetaField> buildFieldMap(MetaCollection collection) {
        if (collection.getFields() == null) {
            return Map.of();
        }
        return collection.getFields().stream()
            .collect(Collectors.toMap(MetaField::getName, f -> f));
    }

    /**
     * Detects changes between two versions of the same field.
     *
     * @param oldField the old field version
     * @param newField the new field version
     * @return list of detected changes
     */
    private List<FieldChange> detectFieldChanges(MetaField oldField, MetaField newField) {
        List<FieldChange> changes = new ArrayList<>();

        // Type change (breaking)
        if (!oldField.getType().equals(newField.getType())) {
            changes.add(FieldChange.typeChanged(oldField, newField));
            logger.warn("Field type changed (BREAKING): field={}, oldType={}, newType={}",
                oldField.getName(), oldField.getType(), newField.getType());
        }

        // Required constraint tightened (breaking)
        if (!oldField.getRequired() && newField.getRequired()) {
            changes.add(FieldChange.madeRequired(oldField, newField));
            logger.warn("Field made required (BREAKING): field={}", oldField.getName());
        }

        // Required constraint relaxed (non-breaking)
        if (oldField.getRequired() && !newField.getRequired()) {
            changes.add(FieldChange.madeOptional(oldField, newField));
            logger.debug("Field made optional (non-breaking): field={}", oldField.getName());
        }

        // Label changed (metadata only)
        if (!Objects.equals(oldField.getLabel(), newField.getLabel())) {
            changes.add(FieldChange.labelChanged(oldField, newField));
            logger.debug("Field label changed (metadata): field={}, oldLabel={}, newLabel={}",
                oldField.getName(), oldField.getLabel(), newField.getLabel());
        }

        // Default value changed (potentially breaking)
        if (!Objects.equals(oldField.getDefaultValue(), newField.getDefaultValue())) {
            changes.add(FieldChange.defaultValueChanged(oldField, newField));
            logger.info("Field default value changed: field={}", oldField.getName());
        }

        return changes;
    }

    /**
     * Schema diff result containing all detected changes.
     *
     * @doc.type class
     * @doc.purpose Schema diff result with change classification
     */
    public static class SchemaDiff {
        private final String oldVersion;
        private final String newVersion;
        private final List<FieldChange> changes = new ArrayList<>();

        public SchemaDiff(String oldVersion, String newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        public void addChange(FieldChange change) {
            changes.add(change);
        }

        public List<FieldChange> getAllChanges() {
            return Collections.unmodifiableList(changes);
        }

        public List<FieldChange> getBreakingChanges() {
            return changes.stream()
                .filter(FieldChange::isBreaking)
                .collect(Collectors.toList());
        }

        public List<FieldChange> getNonBreakingChanges() {
            return changes.stream()
                .filter(c -> !c.isBreaking() && !c.isMetadataOnly())
                .collect(Collectors.toList());
        }

        public List<FieldChange> getMetadataChanges() {
            return changes.stream()
                .filter(FieldChange::isMetadataOnly)
                .collect(Collectors.toList());
        }

        public boolean hasBreakingChanges() {
            return changes.stream().anyMatch(FieldChange::isBreaking);
        }

        public boolean hasNonBreakingChanges() {
            return changes.stream().anyMatch(c -> !c.isBreaking() && !c.isMetadataOnly());
        }

        public boolean hasMetadataChanges() {
            return changes.stream().anyMatch(FieldChange::isMetadataOnly);
        }

        public String getOldVersion() {
            return oldVersion;
        }

        public String getNewVersion() {
            return newVersion;
        }

        @Override
        public String toString() {
            return "SchemaDiff{" +
                "oldVersion='" + oldVersion + '\'' +
                ", newVersion='" + newVersion + '\'' +
                ", changes=" + changes.size() +
                ", breaking=" + hasBreakingChanges() +
                '}';
        }
    }

    /**
     * Represents a single field change in a schema diff.
     *
     * @doc.type class
     * @doc.purpose Field change with breaking/non-breaking classification
     */
    public static class FieldChange {
        private final ChangeType type;
        private final String fieldName;
        private final boolean breaking;
        private final boolean metadataOnly;
        private final String description;
        private final MetaField oldField;
        private final MetaField newField;

        private FieldChange(ChangeType type, String fieldName, boolean breaking,
                           boolean metadataOnly, String description,
                           MetaField oldField, MetaField newField) {
            this.type = type;
            this.fieldName = fieldName;
            this.breaking = breaking;
            this.metadataOnly = metadataOnly;
            this.description = description;
            this.oldField = oldField;
            this.newField = newField;
        }

        public static FieldChange added(MetaField field) {
            boolean breaking = field.getRequired();  // Adding required field is breaking
            return new FieldChange(ChangeType.FIELD_ADDED, field.getName(),
                breaking, false,
                "Field added: " + field.getName() + (breaking ? " (required)" : " (optional)"),
                null, field);
        }

        public static FieldChange removed(MetaField field) {
            return new FieldChange(ChangeType.FIELD_REMOVED, field.getName(),
                true, false,
                "Field removed: " + field.getName() + " (BREAKING)",
                field, null);
        }

        public static FieldChange typeChanged(MetaField oldField, MetaField newField) {
            return new FieldChange(ChangeType.TYPE_CHANGED, oldField.getName(),
                true, false,
                "Type changed: " + oldField.getType() + " → " + newField.getType() + " (BREAKING)",
                oldField, newField);
        }

        public static FieldChange madeRequired(MetaField oldField, MetaField newField) {
            return new FieldChange(ChangeType.MADE_REQUIRED, oldField.getName(),
                true, false,
                "Field made required (BREAKING)",
                oldField, newField);
        }

        public static FieldChange madeOptional(MetaField oldField, MetaField newField) {
            return new FieldChange(ChangeType.MADE_OPTIONAL, oldField.getName(),
                false, false,
                "Field made optional (non-breaking)",
                oldField, newField);
        }

        public static FieldChange labelChanged(MetaField oldField, MetaField newField) {
            return new FieldChange(ChangeType.LABEL_CHANGED, oldField.getName(),
                false, true,
                "Label changed: " + oldField.getLabel() + " → " + newField.getLabel(),
                oldField, newField);
        }

        public static FieldChange defaultValueChanged(MetaField oldField, MetaField newField) {
            return new FieldChange(ChangeType.DEFAULT_VALUE_CHANGED, oldField.getName(),
                false, false,
                "Default value changed",
                oldField, newField);
        }

        // Getters
        public ChangeType getType() { return type; }
        public String getFieldName() { return fieldName; }
        public boolean isBreaking() { return breaking; }
        public boolean isMetadataOnly() { return metadataOnly; }
        public String getDescription() { return description; }
        public MetaField getOldField() { return oldField; }
        public MetaField getNewField() { return newField; }

        @Override
        public String toString() {
            return "FieldChange{" +
                "type=" + type +
                ", fieldName='" + fieldName + '\'' +
                ", breaking=" + breaking +
                ", description='" + description + '\'' +
                '}';
        }
    }

    /**
     * Types of field changes that can be detected.
     *
     * @doc.type enum
     * @doc.purpose Field change type enumeration
     */
    public enum ChangeType {
        FIELD_ADDED,
        FIELD_REMOVED,
        TYPE_CHANGED,
        MADE_REQUIRED,
        MADE_OPTIONAL,
        LABEL_CHANGED,
        DEFAULT_VALUE_CHANGED
    }

    /**
     * Version bump types based on semantic versioning.
     *
     * @doc.type enum
     * @doc.purpose Semantic version bump types
     */
    public enum VersionBump {
        /** Breaking changes - increment MAJOR version */
        MAJOR,
        /** Non-breaking additions - increment MINOR version */
        MINOR,
        /** Metadata changes only - increment PATCH version */
        PATCH,
        /** No changes - no version bump */
        NONE
    }
}
