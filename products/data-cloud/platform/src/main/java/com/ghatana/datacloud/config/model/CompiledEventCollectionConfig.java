package com.ghatana.datacloud.config.model;

import com.ghatana.platform.core.exception.ConfigurationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiled event collection configuration with event sourcing semantics.
 *
 * <p>
 * Extends collection configuration with event-specific features aligned with
 * libs:event-core Event model.
 *
 * <p>
 * Event collections MUST be append-only and include all required event model
 * fields: eventId, eventType, aggregateId, aggregateVersion, correlationId,
 * timestamp, tenantId, payload.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled event collection config aligned with
 * libs:event-core
 * @doc.layer core
 * @doc.pattern Immutable Value Object
 */
public record CompiledEventCollectionConfig(
        String name,
        String tenantId,
        CompiledCollectionConfig.RecordType recordType,
        String displayName,
        String description,
        String schemaVersion,
        String baseModel,
        List<CompiledFieldConfig> fields,
        Map<String, CompiledFieldConfig> fieldsByName,
        List<CompiledIndexConfig> indexes,
        CompiledStorageConfig storage,
        CompiledLifecycleConfig lifecycle,
        CompiledPermissionsConfig permissions,
        List<String> policyRefs,
        EventSourcingConfig eventSourcing,
        StreamingConfig streaming,
        ReplayConfig replay,
        Map<String, String> labels,
        Map<String, String> annotations,
        long configVersion,
        Instant loadedAt
        ) {

    /**
     * Creates a CompiledEventCollectionConfig with validation.
     */
    public CompiledEventCollectionConfig                     {
        Objects.requireNonNull(name, "Collection name cannot be null");
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        Objects.requireNonNull(fields, "Fields cannot be null");
        Objects.requireNonNull(loadedAt, "LoadedAt cannot be null");

        // Event collections MUST be EVENT type
        if (recordType != CompiledCollectionConfig.RecordType.EVENT) {
            throw new IllegalArgumentException(
                    "Event collection must have recordType EVENT, got: " + recordType
            );
        }

        // Defensive copies
        fields = List.copyOf(fields);
        indexes = indexes != null ? List.copyOf(indexes) : List.of();
        policyRefs = policyRefs != null ? List.copyOf(policyRefs) : List.of();
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
        fieldsByName = fieldsByName != null ? Map.copyOf(fieldsByName) : Map.of();

        // Event sourcing defaults
        if (eventSourcing == null) {
            eventSourcing = EventSourcingConfig.defaultConfig();
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
     * Get the config key for this event collection.
     *
     * @return config key
     */
    public ConfigKey getConfigKey() {
        return ConfigKey.eventCollection(tenantId, name);
    }

    /**
     * Validate that this event collection conforms to libs:event-core Event
     * model.
     *
     * @throws ConfigurationException if validation fails
     */
    public void validateEventModel() {
        requireField("eventId", FieldType.UUID);
        requireField("eventType", null); // Can be ENUM or STRING
        requireField("aggregateId", FieldType.UUID);
        requireField("aggregateVersion", FieldType.LONG);
        requireField("correlationId", FieldType.UUID);
        requireField("timestamp", FieldType.TIMESTAMP);
        requireField("tenantId", FieldType.STRING);
        requireField("payload", FieldType.OBJECT);

        // Event collections MUST be append-only
        if (eventSourcing == null || !eventSourcing.appendOnly()) {
            throw new ConfigurationException(
                    "Event collection '" + name + "' MUST be append-only (immutable)"
            );
        }
    }

    private void requireField(String fieldName, FieldType expectedType) {
        CompiledFieldConfig field = fieldsByName.get(fieldName);
        if (field == null) {
            throw new ConfigurationException(
                    String.format("Event collection '%s' MUST have field '%s'", name, fieldName)
            );
        }
        if (expectedType != null && field.type() != expectedType) {
            throw new ConfigurationException(
                    String.format(
                            "Event collection '%s' field '%s' MUST be type %s, got %s",
                            name, fieldName, expectedType, field.type()
                    )
            );
        }
    }

    /**
     * Event sourcing configuration.
     */
    public record EventSourcingConfig(
            boolean enabled,
            boolean appendOnly,
            OrderingGuarantee orderingGuarantee,
            SnapshotConfig snapshotConfig
    ) {
        /**
         * Default event sourcing configuration.
         */
    public static EventSourcingConfig defaultConfig() {
        return new EventSourcingConfig(
                true, true, OrderingGuarantee.PER_AGGREGATE, null
        );
    }
}

/**
 * Ordering guarantee for event delivery.
 */
public enum OrderingGuarantee {
    /**
     * No ordering guarantee
     */
    NONE,
    /**
     * Events for same aggregate are ordered
     */
    PER_AGGREGATE,
    /**
     * Events for same partition key are ordered
     */
    PER_PARTITION,
    /**
     * Global total ordering
     */
    TOTAL
}

/**
 * Snapshot configuration for event sourcing.
 */
public record SnapshotConfig(
        boolean enabled,
        int interval
        ) {

}

/**
 * Streaming configuration.
 */
public record StreamingConfig(
        boolean enabled,
        int partitions,
        String partitionKey,
        int replicationFactor,
        Map<String, ConsumerGroupConfig> consumerGroups
        ) {

    public StreamingConfig     {
        consumerGroups = consumerGroups != null
                ? Map.copyOf(consumerGroups)
                : Map.of();
    }
}

/**
 * Consumer group configuration.
 */
public record ConsumerGroupConfig(
        boolean autoCommit,
        int maxPollRecords,
        java.time.Duration sessionTimeout
        ) {

}

/**
 * Event replay configuration.
 */
public record ReplayConfig(
        boolean enabled,
        int batchSize
        ) {

    public static ReplayConfig defaultConfig() {
        return new ReplayConfig(true, 1000);
    }
}

/**
 * Builder for CompiledEventCollectionConfig.
 */
public static Builder builder() {
        return new Builder();

}

    /**
     * Builder pattern for CompiledEventCollectionConfig.
     */
    public static class Builder {

    private String name;
    private String tenantId;
    private CompiledCollectionConfig.RecordType recordType = CompiledCollectionConfig.RecordType.EVENT;
    private String displayName;
    private String description;
    private String schemaVersion;
    private String baseModel;
    private List<CompiledFieldConfig> fields;
    private Map<String, CompiledFieldConfig> fieldsByName;
    private List<CompiledIndexConfig> indexes;
    private CompiledStorageConfig storage;
    private CompiledLifecycleConfig lifecycle;
    private CompiledPermissionsConfig permissions;
    private List<String> policyRefs;
    private EventSourcingConfig eventSourcing;
    private StreamingConfig streaming;
    private ReplayConfig replay;
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

    public Builder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public Builder description(String description) {
        this.description = description;
        return this;
    }

    public Builder schemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }

    public Builder baseModel(String baseModel) {
        this.baseModel = baseModel;
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

    public Builder eventSourcing(EventSourcingConfig eventSourcing) {
        this.eventSourcing = eventSourcing;
        return this;
    }

    public Builder streaming(StreamingConfig streaming) {
        this.streaming = streaming;
        return this;
    }

    public Builder replay(ReplayConfig replay) {
        this.replay = replay;
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

    public CompiledEventCollectionConfig build() {
        return new CompiledEventCollectionConfig(
                name, tenantId, recordType, displayName, description,
                schemaVersion, baseModel, fields, fieldsByName, indexes,
                storage, lifecycle, permissions, policyRefs, eventSourcing,
                streaming, replay, labels, annotations, configVersion, loadedAt
        );
    }
}
}
