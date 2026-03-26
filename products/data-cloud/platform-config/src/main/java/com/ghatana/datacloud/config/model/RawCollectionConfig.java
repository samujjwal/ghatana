package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw collection configuration as parsed from YAML files.
 *
 * <p>
 * This is the unvalidated, uncompiled representation of a collection
 * configuration. It mirrors the YAML structure directly and supports
 * environment variable interpolation placeholders.
 *
 * <p>
 * After loading and variable interpolation, this config is validated against
 * JSON Schema and then compiled into {@link CompiledCollectionConfig}.
 *
 * @doc.type record
 * @doc.purpose Raw YAML-parsed collection configuration before compilation
 * @doc.layer core
 * @doc.pattern Value Object, DTO
 */
public record RawCollectionConfig(
        String apiVersion,
        String kind,
        RawMetadata metadata,
        RawSpec spec
        ) {

    /**
     * Metadata section from YAML configuration.
     */
    public record RawMetadata(
            String name,
            String namespace,
            Map<String, String> labels,
            Map<String, String> annotations
    ) {
        

    

    public RawMetadata    {
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
}

/**
 * Spec section from YAML configuration.
 */
public record RawSpec(
        String recordType,
        String displayName,
        String description,
        String icon,
        RawSchema schema,
        RawStorage storage,
        List<RawIndex> indexes,
        RawLifecycle lifecycle,
        RawPermissions permissions,
        List<String> policies,
        RawEventSourcing eventSourcing,
        RawStreaming streaming,
        RawReplay replay
        ) {

    public RawSpec             {
        indexes = indexes != null ? List.copyOf(indexes) : List.of();
        policies = policies != null ? List.copyOf(policies) : List.of();
    }
}

/**
 * Schema definition for the collection.
 */
public record RawSchema(
        String version,
        String baseModel,
        List<RawField> fields
        ) {

    public RawSchema   {
        fields = fields != null ? List.copyOf(fields) : List.of();
    }
}

/**
 * Field definition within a schema.
 */
public record RawField(
        String name,
        String type,
        String format,
        boolean required,
        boolean unique,
        boolean indexed,
        boolean pii,
        boolean auto,
        boolean immutable,
        Object defaultValue,
        Integer minLength,
        Integer maxLength,
        Integer maxItems,
        List<String> values,
        RawObjectSchema schema,
        RawArrayItems items,
        String description,
        String reference, // Reference to another collection
        String join // Join collection for relationships
        ) {

    public RawField                   {
        values = values != null ? List.copyOf(values) : List.of();
    }
}

/**
 * Nested object schema for object fields.
 */
public record RawObjectSchema(
        Map<String, RawProperty> properties
        ) {

    public RawObjectSchema {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }
}

/**
 * Property definition for nested objects.
 */
public record RawProperty(
        String type,
        String format
        ) {

}

/**
 * Array items definition.
 */
public record RawArrayItems(
        String type
        ) {

}

/**
 * Storage configuration.
 */
public record RawStorage(
        String profile,
        String partitionKey,
        String sortKey,
        List<RawStorageTier> tiers,
        Boolean shared // Whether storage is shared across tenants
        ) {

    public RawStorage     {
        tiers = tiers != null ? List.copyOf(tiers) : List.of();
    }
}

/**
 * Storage tier for multi-tier event storage.
 */
public record RawStorageTier(
        String profile,
        String duration,
        boolean appendOnly
        ) {

}

/**
 * Index definition.
 */
public record RawIndex(
        String name,
        List<String> fields,
        boolean unique,
        String type
        ) {

    public RawIndex    {
        fields = fields != null ? List.copyOf(fields) : List.of();
    }
}

/**
 * Lifecycle configuration.
 */
public record RawLifecycle(
        RawRetention retention,
        RawArchival archival,
        RawCompaction compaction
        ) {

}

/**
 * Retention policy.
 */
public record RawRetention(
        String duration,
        String action
        ) {

}

/**
 * Archival configuration.
 */
public record RawArchival(
        boolean enabled,
        int afterDays,
        String targetProfile
        ) {

}

/**
 * Compaction configuration (for non-event collections).
 */
public record RawCompaction(
        boolean enabled
        ) {

}

/**
 * Permissions configuration.
 */
public record RawPermissions(
        List<RawPermissionRule> read,
        List<RawPermissionRule> write,
        List<RawPermissionRule> delete
        ) {

    public RawPermissions   {
        read = read != null ? List.copyOf(read) : List.of();
        write = write != null ? List.copyOf(write) : List.of();
        delete = delete != null ? List.copyOf(delete) : List.of();
    }
}

/**
 * Permission rule.
 */
public record RawPermissionRule(
        String role,
        String condition
        ) {

}

/**
 * Event sourcing configuration (for EVENT record types).
 */
public record RawEventSourcing(
        boolean enabled,
        boolean appendOnly,
        String orderingGuarantee,
        RawSnapshotting snapshotting
        ) {

}

/**
 * Snapshotting configuration for event sourcing.
 */
public record RawSnapshotting(
        boolean enabled,
        int interval
        ) {

}

/**
 * Streaming configuration.
 */
public record RawStreaming(
        boolean enabled,
        int partitions,
        String partitionKey,
        int replicationFactor,
        Map<String, RawConsumerGroup> consumerGroups
        ) {

    public RawStreaming     {
        consumerGroups = consumerGroups != null ? Map.copyOf(consumerGroups) : Map.of();
    }
}

/**
 * Consumer group configuration.
 */
public record RawConsumerGroup(
        boolean autoCommit,
        int maxPollRecords,
        String sessionTimeout
        ) {

}

/**
 * Event replay configuration.
 */
public record RawReplay(
        boolean enabled,
        int batchSize
        ) {

}

/**
 * Get the tenant ID from metadata namespace.
 */
public String getTenantId() {
        return metadata != null ? metadata.namespace() : null;
    }

    /**
     * Get the collection name from metadata.
     */
    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    /**
     * Check if this is an event collection.
     */
    public boolean isEventCollection() {
        return spec != null && "EVENT".equals(spec.recordType());
    }
}
