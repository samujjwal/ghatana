package com.ghatana.datacloud.entity.version;

import java.util.*;

/**
 * Represents differences between two entity versions.
 *
 * <p><b>Purpose</b><br>
 * Captures field-level changes between entity versions for comparison, rollback,
 * and audit trail display.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VersionDiff diff = new VersionDiff(
 *     Map.of(
 *         "name", new VersionDiff.FieldChange("Old Name", "New Name"),
 *         "email", new VersionDiff.FieldChange("old@example.com", "new@example.com")
 *     ),
 *     Set.of("description", "tags")  // added fields
 * );
 * }</pre>
 *
 * <p><b>Field Changes</b><br>
 * - changed: Map of field name to (oldValue, newValue)
 * - added: Set of field names that were added
 * - removed: Set of field names that were removed
 *
 * @doc.type class
 * @doc.purpose Represents field-level version differences
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public class VersionDiff {

    private final Map<String, FieldChange> changed;
    private final Set<String> added;
    private final Set<String> removed;

    /**
     * Creates a VersionDiff with specified changes.
     *
     * @param changed map of field name to FieldChange
     * @param added set of added field names
     * @param removed set of removed field names
     */
    public VersionDiff(
            Map<String, FieldChange> changed,
            Set<String> added,
            Set<String> removed) {
        this.changed = Objects.requireNonNull(changed, "Changed map must not be null");
        this.added = Objects.requireNonNull(added, "Added set must not be null");
        this.removed = Objects.requireNonNull(removed, "Removed set must not be null");
    }

    /**
     * Creates an empty VersionDiff (no changes).
     *
     * @return empty diff
     */
    public static VersionDiff empty() {
        return new VersionDiff(Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());
    }

    /**
     * Gets the changed fields.
     *
     * @return map of field name to FieldChange
     */
    public Map<String, FieldChange> getChanged() {
        return Collections.unmodifiableMap(changed);
    }

    /**
     * Gets the added fields.
     *
     * @return immutable set of added field names
     */
    public Set<String> getAdded() {
        return Collections.unmodifiableSet(added);
    }

    /**
     * Gets the removed fields.
     *
     * @return immutable set of removed field names
     */
    public Set<String> getRemoved() {
        return Collections.unmodifiableSet(removed);
    }

    /**
     * Checks if there are any differences.
     *
     * @return true if diff contains any changes
     */
    public boolean hasChanges() {
        return !changed.isEmpty() || !added.isEmpty() || !removed.isEmpty();
    }

    /**
     * Gets count of total changes.
     *
     * @return number of changed + added + removed fields
     */
    public int getTotalChangeCount() {
        return changed.size() + added.size() + removed.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionDiff diff = (VersionDiff) o;
        return Objects.equals(changed, diff.changed) &&
               Objects.equals(added, diff.added) &&
               Objects.equals(removed, diff.removed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changed, added, removed);
    }

    @Override
    public String toString() {
        return "VersionDiff{" +
                "changed=" + changed.size() +
                ", added=" + added.size() +
                ", removed=" + removed.size() +
                '}';
    }

    /**
     * Represents a single field change (old value to new value).
     *
     * <p><b>Immutability</b><br>
     * This record is immutable and thread-safe.
     *
     * @param oldValue the previous field value
     * @param newValue the new field value
     *
     * @doc.type record
     * @doc.purpose Represents a single field change
     * @doc.layer domain
     * @doc.pattern Value Object
     */
    public record FieldChange(Object oldValue, Object newValue) {
        /**
         * Creates a FieldChange with values.
         *
         * @param oldValue the previous value
         * @param newValue the new value
         */
        public FieldChange {
            // No validation - both can be null for optional fields
        }

        /**
         * Gets a human-readable summary of the change.
         *
         * @return change summary
         */
        public String getSummary() {
            return String.format("%s → %s", oldValue, newValue);
        }
    }
}
