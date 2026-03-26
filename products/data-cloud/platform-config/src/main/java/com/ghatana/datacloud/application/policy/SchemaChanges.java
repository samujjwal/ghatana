package com.ghatana.datacloud.application.policy;

import java.util.*;

/**
 * Descriptor for schema changes requiring policy evaluation.
 *
 * <p><b>Purpose</b><br>
 * Represents schema modification operations for governance and approval workflows.
 * Includes field additions, removals, type changes, and constraint updates.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SchemaChanges changes = SchemaChanges.builder()
 *     .addFieldAddition("discount", "DECIMAL")
 *     .addFieldRemoval("legacyCode")
 *     .addTypeChange("status", "STRING", "ENUM")
 *     .isBreaking(true)
 *     .versionFrom("1.2")
 *     .versionTo("1.3")
 *     .build();
 *
 * boolean requiresApproval = changes.isBreaking();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @doc.type class
 * @doc.purpose Schema change descriptor for policy evaluation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class SchemaChanges {

    private final List<FieldAddition> fieldAdditions;
    private final List<FieldRemoval> fieldRemovals;
    private final List<TypeChange> typeChanges;
    private final List<ConstraintChange> constraintChanges;
    private final boolean breaking;
    private final String versionFrom;
    private final String versionTo;

    private SchemaChanges(Builder builder) {
        this.fieldAdditions = List.copyOf(builder.fieldAdditions);
        this.fieldRemovals = List.copyOf(builder.fieldRemovals);
        this.typeChanges = List.copyOf(builder.typeChanges);
        this.constraintChanges = List.copyOf(builder.constraintChanges);
        this.breaking = builder.breaking;
        this.versionFrom = Objects.requireNonNull(builder.versionFrom, "versionFrom must not be null");
        this.versionTo = Objects.requireNonNull(builder.versionTo, "versionTo must not be null");
    }

    /**
     * Gets field additions.
     *
     * @return immutable list of field additions (never null, may be empty)
     */
    public List<FieldAddition> getFieldAdditions() {
        return fieldAdditions;
    }

    /**
     * Gets field removals.
     *
     * @return immutable list of field removals (never null, may be empty)
     */
    public List<FieldRemoval> getFieldRemovals() {
        return fieldRemovals;
    }

    /**
     * Gets type changes.
     *
     * @return immutable list of type changes (never null, may be empty)
     */
    public List<TypeChange> getTypeChanges() {
        return typeChanges;
    }

    /**
     * Gets constraint changes.
     *
     * @return immutable list of constraint changes (never null, may be empty)
     */
    public List<ConstraintChange> getConstraintChanges() {
        return constraintChanges;
    }

    /**
     * Checks if changes are breaking.
     *
     * @return true if breaking changes (require approval)
     */
    public boolean isBreaking() {
        return breaking;
    }

    /**
     * Gets source schema version.
     *
     * @return version before changes (never null)
     */
    public String getVersionFrom() {
        return versionFrom;
    }

    /**
     * Gets target schema version.
     *
     * @return version after changes (never null)
     */
    public String getVersionTo() {
        return versionTo;
    }

    /**
     * Converts to map for policy evaluation.
     *
     * @return map representation suitable for policy engine
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "fieldAdditions", fieldAdditions.stream().map(FieldAddition::toMap).toList(),
            "fieldRemovals", fieldRemovals.stream().map(FieldRemoval::toMap).toList(),
            "typeChanges", typeChanges.stream().map(TypeChange::toMap).toList(),
            "constraintChanges", constraintChanges.stream().map(ConstraintChange::toMap).toList(),
            "breaking", breaking,
            "versionFrom", versionFrom,
            "versionTo", versionTo
        );
    }

    /**
     * Gets summary for logging.
     *
     * @return concise summary string
     */
    public String summary() {
        return String.format("%s->%s: +%d fields, -%d fields, %d type changes, breaking=%s",
            versionFrom, versionTo,
            fieldAdditions.size(),
            fieldRemovals.size(),
            typeChanges.size(),
            breaking);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<FieldAddition> fieldAdditions = new ArrayList<>();
        private List<FieldRemoval> fieldRemovals = new ArrayList<>();
        private List<TypeChange> typeChanges = new ArrayList<>();
        private List<ConstraintChange> constraintChanges = new ArrayList<>();
        private boolean breaking = false;
        private String versionFrom = "0.0";
        private String versionTo = "0.1";

        public Builder addFieldAddition(String fieldName, String fieldType) {
            this.fieldAdditions.add(new FieldAddition(fieldName, fieldType));
            return this;
        }

        public Builder addFieldRemoval(String fieldName) {
            this.fieldRemovals.add(new FieldRemoval(fieldName));
            return this;
        }

        public Builder addTypeChange(String fieldName, String fromType, String toType) {
            this.typeChanges.add(new TypeChange(fieldName, fromType, toType));
            return this;
        }

        public Builder addConstraintChange(String fieldName, String constraintType, String oldValue, String newValue) {
            this.constraintChanges.add(new ConstraintChange(fieldName, constraintType, oldValue, newValue));
            return this;
        }

        public Builder isBreaking(boolean breaking) {
            this.breaking = breaking;
            return this;
        }

        public Builder versionFrom(String versionFrom) {
            this.versionFrom = versionFrom;
            return this;
        }

        public Builder versionTo(String versionTo) {
            this.versionTo = versionTo;
            return this;
        }

        public SchemaChanges build() {
            return new SchemaChanges(this);
        }
    }

    public record FieldAddition(String fieldName, String fieldType) {
        public FieldAddition {
            Objects.requireNonNull(fieldName, "fieldName must not be null");
            Objects.requireNonNull(fieldType, "fieldType must not be null");
        }

        public Map<String, Object> toMap() {
            return Map.of("fieldName", fieldName, "fieldType", fieldType);
        }
    }

    public record FieldRemoval(String fieldName) {
        public FieldRemoval {
            Objects.requireNonNull(fieldName, "fieldName must not be null");
        }

        public Map<String, Object> toMap() {
            return Map.of("fieldName", fieldName);
        }
    }

    public record TypeChange(String fieldName, String fromType, String toType) {
        public TypeChange {
            Objects.requireNonNull(fieldName, "fieldName must not be null");
            Objects.requireNonNull(fromType, "fromType must not be null");
            Objects.requireNonNull(toType, "toType must not be null");
        }

        public Map<String, Object> toMap() {
            return Map.of("fieldName", fieldName, "fromType", fromType, "toType", toType);
        }
    }

    public record ConstraintChange(String fieldName, String constraintType, String oldValue, String newValue) {
        public ConstraintChange {
            Objects.requireNonNull(fieldName, "fieldName must not be null");
            Objects.requireNonNull(constraintType, "constraintType must not be null");
        }

        public Map<String, Object> toMap() {
            return Map.of(
                "fieldName", fieldName,
                "constraintType", constraintType,
                "oldValue", oldValue != null ? oldValue : "",
                "newValue", newValue != null ? newValue : ""
            );
        }
    }
}
