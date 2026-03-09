package com.ghatana.datacloud.application.version;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.version.VersionDiff;
import java.util.*;

/**
 * Utility for computing field-level diffs between entity versions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Analyzes entity snapshots and identifies what changed (field-level). Provides
 * structured VersionDiff output for audit trails and comparison UIs.
 *
 * <p>
 * <b>Comparison Strategy</b><br>
 * - Compares all fields of two entity snapshots - Identifies changed fields
 * (oldValue → newValue) - Identifies added fields (in new but not in old) -
 * Identifies removed fields (in old but not in new)
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * VersionComparator comparator = new VersionComparator();
 * VersionDiff diff = comparator.compare(entityV1, entityV2);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Computes field-level diffs between entity versions
 * @doc.layer application
 * @doc.pattern Utility
 */
public class VersionComparator {

    /**
     * Creates a VersionComparator.
     */
    public VersionComparator() {
        // No-arg constructor
    }

    /**
     * Computes diff between two entity versions.
     *
     * @param oldEntity the previous entity version
     * @param newEntity the new entity version
     * @return VersionDiff showing changed/added/removed fields
     */
    public VersionDiff compare(Entity oldEntity, Entity newEntity) {
        Objects.requireNonNull(oldEntity, "Old entity must not be null");
        Objects.requireNonNull(newEntity, "New entity must not be null");

        Map<String, VersionDiff.FieldChange> changed = new HashMap<>();
        Set<String> added = new HashSet<>();
        Set<String> removed = new HashSet<>();

        // Get all field names from both entities (dynamic data keys only)
        Set<String> allFields = getAllFieldNames(oldEntity, newEntity);

        for (String fieldName : allFields) {
            Object oldValue = getFieldValue(oldEntity, fieldName);
            Object newValue = getFieldValue(newEntity, fieldName);

            if (!Objects.equals(oldValue, newValue)) {
                // Record value change
                changed.put(fieldName, new VersionDiff.FieldChange(oldValue, newValue));

                // Also classify as added/removed based on null transitions
                if (oldValue == null && newValue != null) {
                    added.add(fieldName);
                } else if (oldValue != null && newValue == null) {
                    removed.add(fieldName);
                }
            }
            // If equal, no change
        }

        return new VersionDiff(changed, added, removed);
    }

    /**
     * Gets all field names from both entities.
     *
     * @param entity1 first entity
     * @param entity2 second entity
     * @return set of all field names
     */
    private Set<String> getAllFieldNames(Entity entity1, Entity entity2) {
        Set<String> fields = new HashSet<>();

        // Dynamic fields from the data map for fine-grained diffs (e.g. name,
        // description, field1, field2)
        if (entity1.getData() != null) {
            fields.addAll(entity1.getData().keySet());
        }
        if (entity2.getData() != null) {
            fields.addAll(entity2.getData().keySet());
        }

        return fields;
    }

    /**
     * Gets the value of a field from an entity. Handles both Entity fields and
     * data map keys.
     *
     * @param entity    the entity
     * @param fieldName the field name
     * @return field value or null if field not found
     */
    private Object getFieldValue(Entity entity, String fieldName) {
        try {
            return switch (fieldName) {
                // For fields stored in data map, extract from map
                default ->
                    entity.getData() != null ? entity.getData().get(fieldName) : null;
            };
        } catch (Exception e) {
            // Field doesn't exist or can't be accessed - treat as null
            return null;
        }
    }
}
