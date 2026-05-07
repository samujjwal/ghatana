package com.ghatana.datacloud.config.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiled, immutable collection configuration with pre-computed lookups.
 *
 * <p>
 * This is the runtime representation of a collection after validation and
 * compilation. All fields are immutable and thread-safe.
 *
 * <p>
 * HOT PATH: Field lookups via {@link #getField(String)} are O(1) using
 * pre-computed HashMap. No parsing, no reflection at runtime.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled collection configuration for runtime access
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledCollectionConfig(
        String name,
        String tenantId,
        RecordType recordType,
        String displayName,
        String description,
        String icon,
        String schemaVersion,
        List<CompiledFieldConfig> fields,
        Map<String, CompiledFieldConfig> fieldsByName,
        List<CompiledIndexConfig> indexes,
        CompiledStorageConfig storage,
        CompiledLifecycleConfig lifecycle,
        CompiledPermissionsConfig permissions,
        List<String> policyRefs,
        Map<String, String> labels,
        Map<String, String> annotations,
        long configVersion,
        Instant loadedAt
        ) {

    /**
     * Creates a CompiledCollectionConfig with validation and pre-computed
     * lookups.
     */
    public CompiledCollectionConfig                  {
        Objects.requireNonNull(name, "Collection name cannot be null");
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        Objects.requireNonNull(recordType, "Record type cannot be null");
        Objects.requireNonNull(fields, "Fields cannot be null");
        Objects.requireNonNull(loadedAt, "LoadedAt cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID cannot be blank");
        }

        // Defensive copies
        fields = List.copyOf(fields);
        indexes = indexes != null ? List.copyOf(indexes) : List.of();
        policyRefs = policyRefs != null ? List.copyOf(policyRefs) : List.of();
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();

        // Pre-compute field lookup map if not provided
        if (fieldsByName == null || fieldsByName.isEmpty()) {
            Map<String, CompiledFieldConfig> computed = new HashMap<>();
            for (CompiledFieldConfig field : fields) {
                computed.put(field.name(), field);
            }
            fieldsByName = Map.copyOf(computed);
        } else {
            fieldsByName = Map.copyOf(fieldsByName);
        }
    }

    /**
     * Get a field configuration by name. O(1) lookup.
     *
     * @param fieldName field name
     * @return field config or null if not found
     */
    public CompiledFieldConfig getField(String fieldName) {
        return fieldsByName.get(fieldName);
    }

    /**
     * Check if a field exists.
     *
     * @param fieldName field name
     * @return true if field exists
     */
    public boolean hasField(String fieldName) {
        return fieldsByName.containsKey(fieldName);
    }

    /**
     * Get all required fields.
     *
     * @return list of required field configs
     */
    public List<CompiledFieldConfig> getRequiredFields() {
        return fields.stream()
                .filter(CompiledFieldConfig::required)
                .toList();
    }

    /**
     * Get all indexed fields.
     *
     * @return list of indexed field configs
     */
    public List<CompiledFieldConfig> getIndexedFields() {
        return fields.stream()
                .filter(CompiledFieldConfig::indexed)
                .toList();
    }

    /**
     * Get all PII fields.
     *
     * @return list of PII field configs
     */
    public List<CompiledFieldConfig> getPiiFields() {
        return fields.stream()
                .filter(CompiledFieldConfig::pii)
                .toList();
    }

    /**
     * Get the config key for this collection.
     *
     * @return config key
     */
    public ConfigKey getConfigKey() {
        return recordType == RecordType.EVENT
                ? ConfigKey.eventCollection(tenantId, name)
                : ConfigKey.collection(tenantId, name);
    }

    /**
     * Check if this is an event collection.
     *
     * @return true if event collection
     */
    public boolean isEventCollection() {
        return recordType == RecordType.EVENT;
    }

    /**
     * Check if this is an entity collection.
     *
     * @return true if entity collection
     */
    public boolean isEntityCollection() {
        return recordType == RecordType.ENTITY;
    }

    /**
     * Record types supported by the platform.
     */
    public enum RecordType {
        /**
         * Entity - mutable records with CRUD operations
         */
        ENTITY,
        /**
         * Event - immutable, append-only records
         */
        EVENT,
        /**
         * TimeSeries - time-indexed metrics/observations
         */
        TIMESERIES,
        /**
         * Graph - nodes and edges with relationships
         */
        GRAPH,
        /**
         * Document - semi-structured JSON documents
         */
        DOCUMENT
    }

    /**
     * Builder for CompiledCollectionConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder pattern for CompiledCollectionConfig.
     */
    public static class Builder {

        private String name;
        private String tenantId;
        private RecordType recordType;
        private String displayName;
        private String description;
        private String icon;
        private String schemaVersion;
        private List<CompiledFieldConfig> fields;
        private Map<String, CompiledFieldConfig> fieldsByName;
        private List<CompiledIndexConfig> indexes;
        private CompiledStorageConfig storage;
        private CompiledLifecycleConfig lifecycle;
        private CompiledPermissionsConfig permissions;
        private List<String> policyRefs;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private long configVersion;
        private Instant loadedAt = Instant.now();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder recordType(RecordType recordType) {
            this.recordType = recordType;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder fields(List<CompiledFieldConfig> fields) {
            this.fields = fields;
            return this;
        }

        public Builder fieldsByName(Map<String, CompiledFieldConfig> fieldsByName) {
            this.fieldsByName = fieldsByName;
            return this;
        }

        public Builder indexes(List<CompiledIndexConfig> indexes) {
            this.indexes = indexes;
            return this;
        }

        public Builder storage(CompiledStorageConfig storage) {
            this.storage = storage;
            return this;
        }

        public Builder lifecycle(CompiledLifecycleConfig lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        public Builder permissions(CompiledPermissionsConfig permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder policyRefs(List<String> policyRefs) {
            this.policyRefs = policyRefs;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder annotations(Map<String, String> annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder configVersion(long configVersion) {
            this.configVersion = configVersion;
            return this;
        }

        public Builder loadedAt(Instant loadedAt) {
            this.loadedAt = loadedAt;
            return this;
        }

        public CompiledCollectionConfig build() {
            return new CompiledCollectionConfig(
                    name, tenantId, recordType, displayName, description, icon,
                    schemaVersion, fields, fieldsByName, indexes, storage,
                    lifecycle, permissions, policyRefs, labels, annotations,
                    configVersion, loadedAt
            );
        }
    }
}
