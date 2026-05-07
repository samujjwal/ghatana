package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.*;
import com.ghatana.datacloud.config.model.CompiledCollectionConfig.RecordType;
import com.ghatana.datacloud.config.model.CompiledEventCollectionConfig.*;
import com.ghatana.datacloud.config.model.CompiledIndexConfig.IndexType;
import com.ghatana.datacloud.config.model.CompiledLifecycleConfig.*;
import com.ghatana.datacloud.config.model.CompiledObjectSchema.CompiledPropertyConfig;
import com.ghatana.datacloud.config.model.CompiledPermissionsConfig.CompiledPermissionRule;
import com.ghatana.platform.core.exception.ConfigurationException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compiles raw YAML configuration into optimized runtime objects.
 *
 * <p>
 * The compiler transforms {@link RawCollectionConfig} into either
 * {@link CompiledCollectionConfig} or {@link CompiledEventCollectionConfig}
 * depending on the record type.
 *
 * <p>
 * Compilation includes:
 * <ul>
 * <li>Type conversion and normalization</li>
 * <li>Pre-computed field lookup maps</li>
 * <li>Duration parsing</li>
 * <li>Enum value resolution</li>
 * <li>Validation of event model alignment</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Compile raw YAML config into optimized runtime objects
 * @doc.layer core
 * @doc.pattern Builder, Compiler
 */
public class CollectionConfigCompiler {

    private final AtomicLong versionCounter;

    /**
     * Creates a new compiler with shared version counter.
     */
    public CollectionConfigCompiler() {
        this.versionCounter = new AtomicLong(0);
    }

    /**
     * Creates a compiler with an external version counter.
     *
     * @param versionCounter shared version counter
     */
    public CollectionConfigCompiler(AtomicLong versionCounter) {
        this.versionCounter = Objects.requireNonNull(versionCounter);
    }

    /**
     * Compile a raw collection configuration.
     *
     * @param raw raw config from YAML
     * @return compiled collection config
     * @throws ConfigurationException if compilation fails or if trying to
     * compile an event collection (use compileEventCollection for event
     * collections)
     */
    public CompiledCollectionConfig compile(RawCollectionConfig raw) {
        Objects.requireNonNull(raw, "Raw config cannot be null");

        RecordType recordType = parseRecordType(raw.spec().recordType());

        // Event collections must use compileEventCollection
        if (recordType == RecordType.EVENT) {
            throw new ConfigurationException(
                    "Event collections must be compiled using compileEventCollection(). "
                    + "Collection: " + raw.metadata().name());
        }

        return compileStandardCollection(raw, recordType);
    }

    /**
     * Compile a standard (non-event) collection.
     */
    private CompiledCollectionConfig compileStandardCollection(
            RawCollectionConfig raw,
            RecordType recordType) {

        var metadata = raw.metadata();
        var spec = raw.spec();

        // Compile fields with pre-computed lookup map
        List<CompiledFieldConfig> fields = compileFields(spec.schema().fields());
        Map<String, CompiledFieldConfig> fieldsByName = buildFieldMap(fields);

        // Compile other sections
        List<CompiledIndexConfig> indexes = compileIndexes(spec.indexes());
        CompiledStorageConfig storage = compileStorage(spec.storage());
        CompiledLifecycleConfig lifecycle = compileLifecycle(spec.lifecycle());
        CompiledPermissionsConfig permissions = compilePermissions(spec.permissions());

        return CompiledCollectionConfig.builder()
                .name(metadata.name())
                .tenantId(metadata.namespace())
                .recordType(recordType)
                .displayName(spec.displayName())
                .description(spec.description())
                .icon(spec.icon())
                .schemaVersion(spec.schema().version())
                .fields(fields)
                .fieldsByName(fieldsByName)
                .indexes(indexes)
                .storage(storage)
                .lifecycle(lifecycle)
                .permissions(permissions)
                .policyRefs(spec.policies())
                .labels(metadata.labels())
                .annotations(metadata.annotations())
                .configVersion(versionCounter.incrementAndGet())
                .loadedAt(Instant.now())
                .build();
    }

    /**
     * Compile an event collection with event sourcing configuration.
     *
     * @param raw raw config from YAML
     * @return compiled event collection config
     */
    public CompiledEventCollectionConfig compileEventCollection(RawCollectionConfig raw) {
        var metadata = raw.metadata();
        var spec = raw.spec();

        // Compile fields with pre-computed lookup map
        List<CompiledFieldConfig> fields = compileFields(spec.schema().fields());
        Map<String, CompiledFieldConfig> fieldsByName = buildFieldMap(fields);

        // Compile event-specific sections
        EventSourcingConfig eventSourcing = compileEventSourcing(spec.eventSourcing());
        StreamingConfig streaming = compileStreaming(spec.streaming());
        ReplayConfig replay = compileReplay(spec.replay());

        // Compile common sections
        List<CompiledIndexConfig> indexes = compileIndexes(spec.indexes());
        CompiledStorageConfig storage = compileStorage(spec.storage());
        CompiledLifecycleConfig lifecycle = compileLifecycle(spec.lifecycle());
        CompiledPermissionsConfig permissions = compilePermissions(spec.permissions());

        var compiled = CompiledEventCollectionConfig.builder()
                .name(metadata.name())
                .tenantId(metadata.namespace())
                .displayName(spec.displayName())
                .description(spec.description())
                .schemaVersion(spec.schema().version())
                .baseModel(spec.schema().baseModel())
                .fields(fields)
                .fieldsByName(fieldsByName)
                .indexes(indexes)
                .storage(storage)
                .lifecycle(lifecycle)
                .permissions(permissions)
                .policyRefs(spec.policies())
                .eventSourcing(eventSourcing)
                .streaming(streaming)
                .replay(replay)
                .labels(metadata.labels())
                .annotations(metadata.annotations())
                .configVersion(versionCounter.incrementAndGet())
                .loadedAt(Instant.now())
                .build();

        // Validate event model alignment
        compiled.validateEventModel();

        return compiled;
    }

    /**
     * Compile fields from raw field definitions.
     */
    private List<CompiledFieldConfig> compileFields(List<RawCollectionConfig.RawField> rawFields) {
        if (rawFields == null || rawFields.isEmpty()) {
            return List.of();
        }

        return rawFields.stream()
                .map(this::compileField)
                .toList();
    }

    /**
     * Compile a single field.
     */
    private CompiledFieldConfig compileField(RawCollectionConfig.RawField raw) {
        FieldType type = FieldType.fromYaml(raw.type());

        // Compile nested schema for object fields
        CompiledObjectSchema nestedSchema = null;
        if (type == FieldType.OBJECT && raw.schema() != null) {
            nestedSchema = compileObjectSchema(raw.schema());
        }

        // Get array item type
        FieldType arrayItemType = null;
        if (type == FieldType.ARRAY && raw.items() != null) {
            arrayItemType = FieldType.fromYaml(raw.items().type());
        }

        return CompiledFieldConfig.builder()
                .name(raw.name())
                .type(type)
                .format(raw.format())
                .required(raw.required())
                .unique(raw.unique())
                .indexed(raw.indexed())
                .pii(raw.pii())
                .auto(raw.auto())
                .immutable(raw.immutable())
                .defaultValue(raw.defaultValue())
                .minLength(raw.minLength())
                .maxLength(raw.maxLength())
                .maxItems(raw.maxItems())
                .enumValues(raw.values())
                .nestedSchema(nestedSchema)
                .arrayItemType(arrayItemType)
                .description(raw.description())
                .build();
    }

    /**
     * Compile nested object schema.
     */
    private CompiledObjectSchema compileObjectSchema(RawCollectionConfig.RawObjectSchema raw) {
        if (raw == null || raw.properties() == null) {
            return null;
        }

        Map<String, CompiledPropertyConfig> properties = new HashMap<>();
        for (var entry : raw.properties().entrySet()) {
            var prop = entry.getValue();
            properties.put(entry.getKey(), new CompiledPropertyConfig(
                    entry.getKey(),
                    FieldType.fromYaml(prop.type()),
                    prop.format()
            ));
        }

        return new CompiledObjectSchema(properties);
    }

    /**
     * Build pre-computed field lookup map.
     */
    private Map<String, CompiledFieldConfig> buildFieldMap(List<CompiledFieldConfig> fields) {
        Map<String, CompiledFieldConfig> map = new HashMap<>();
        for (CompiledFieldConfig field : fields) {
            map.put(field.name(), field);
        }
        return Map.copyOf(map);
    }

    /**
     * Compile indexes.
     */
    private List<CompiledIndexConfig> compileIndexes(List<RawCollectionConfig.RawIndex> rawIndexes) {
        if (rawIndexes == null || rawIndexes.isEmpty()) {
            return List.of();
        }

        return rawIndexes.stream()
                .map(raw -> new CompiledIndexConfig(
                raw.name(),
                raw.fields(),
                raw.unique(),
                parseIndexType(raw.type())
        ))
                .toList();
    }

    /**
     * Compile storage configuration.
     */
    private CompiledStorageConfig compileStorage(RawCollectionConfig.RawStorage raw) {
        if (raw == null) {
            return new CompiledStorageConfig("default", null, null, List.of());
        }

        List<CompiledStorageTier> tiers = List.of();
        if (raw.tiers() != null && !raw.tiers().isEmpty()) {
            tiers = raw.tiers().stream()
                    .map(this::compileStorageTier)
                    .toList();
        }

        return new CompiledStorageConfig(
                raw.profile() != null ? raw.profile() : "default",
                raw.partitionKey(),
                raw.sortKey(),
                tiers
        );
    }

    /**
     * Compile a storage tier.
     */
    private CompiledStorageTier compileStorageTier(RawCollectionConfig.RawStorageTier raw) {
        if ("FOREVER".equalsIgnoreCase(raw.duration())) {
            return CompiledStorageTier.forever(raw.profile(), raw.appendOnly());
        }

        Duration duration = parseDuration(raw.duration());
        return CompiledStorageTier.withDuration(raw.profile(), duration, raw.appendOnly());
    }

    /**
     * Compile lifecycle configuration.
     */
    private CompiledLifecycleConfig compileLifecycle(RawCollectionConfig.RawLifecycle raw) {
        if (raw == null) {
            return new CompiledLifecycleConfig(null, null, null);
        }

        CompiledRetentionConfig retention = null;
        if (raw.retention() != null) {
            retention = new CompiledRetentionConfig(
                    parseDuration(raw.retention().duration()),
                    parseRetentionAction(raw.retention().action())
            );
        }

        CompiledArchivalConfig archival = null;
        if (raw.archival() != null && raw.archival().enabled()) {
            archival = new CompiledArchivalConfig(
                    true,
                    Duration.ofDays(raw.archival().afterDays()),
                    raw.archival().targetProfile()
            );
        }

        CompiledCompactionConfig compaction = null;
        if (raw.compaction() != null) {
            compaction = new CompiledCompactionConfig(raw.compaction().enabled());
        }

        return new CompiledLifecycleConfig(retention, archival, compaction);
    }

    /**
     * Compile permissions configuration.
     */
    private CompiledPermissionsConfig compilePermissions(RawCollectionConfig.RawPermissions raw) {
        if (raw == null) {
            return CompiledPermissionsConfig.openAccess();
        }

        return new CompiledPermissionsConfig(
                compilePermissionRules(raw.read()),
                compilePermissionRules(raw.write()),
                compilePermissionRules(raw.delete())
        );
    }

    /**
     * Compile permission rules.
     */
    private List<CompiledPermissionRule> compilePermissionRules(
            List<RawCollectionConfig.RawPermissionRule> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        return raw.stream()
                .map(r -> new CompiledPermissionRule(r.role(), r.condition()))
                .toList();
    }

    /**
     * Compile event sourcing configuration.
     */
    private EventSourcingConfig compileEventSourcing(RawCollectionConfig.RawEventSourcing raw) {
        if (raw == null) {
            return EventSourcingConfig.defaultConfig();
        }

        SnapshotConfig snapshotConfig = null;
        if (raw.snapshotting() != null && raw.snapshotting().enabled()) {
            snapshotConfig = new SnapshotConfig(true, raw.snapshotting().interval());
        }

        return new EventSourcingConfig(
                raw.enabled(),
                raw.appendOnly(),
                parseOrderingGuarantee(raw.orderingGuarantee()),
                snapshotConfig
        );
    }

    /**
     * Compile streaming configuration.
     */
    private StreamingConfig compileStreaming(RawCollectionConfig.RawStreaming raw) {
        if (raw == null || !raw.enabled()) {
            return null;
        }

        Map<String, ConsumerGroupConfig> consumerGroups = new HashMap<>();
        if (raw.consumerGroups() != null) {
            for (var entry : raw.consumerGroups().entrySet()) {
                var cg = entry.getValue();
                consumerGroups.put(entry.getKey(), new ConsumerGroupConfig(
                        cg.autoCommit(),
                        cg.maxPollRecords(),
                        parseDuration(cg.sessionTimeout())
                ));
            }
        }

        return new StreamingConfig(
                raw.enabled(),
                raw.partitions(),
                raw.partitionKey(),
                raw.replicationFactor(),
                consumerGroups
        );
    }

    /**
     * Compile replay configuration.
     */
    private ReplayConfig compileReplay(RawCollectionConfig.RawReplay raw) {
        if (raw == null || !raw.enabled()) {
            return ReplayConfig.defaultConfig();
        }

        return new ReplayConfig(raw.enabled(), raw.batchSize());
    }

    // ==================== Parsing Helpers ====================
    /**
     * Parse record type from string.
     */
    private RecordType parseRecordType(String value) {
        if (value == null) {
            return RecordType.ENTITY;
        }
        try {
            return RecordType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown record type: " + value);
        }
    }

    /**
     * Parse index type from string.
     */
    private IndexType parseIndexType(String value) {
        if (value == null) {
            return IndexType.BTREE;
        }
        try {
            return IndexType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown index type: " + value);
        }
    }

    /**
     * Parse retention action from string.
     */
    private RetentionAction parseRetentionAction(String value) {
        if (value == null) {
            return RetentionAction.DELETE;
        }
        try {
            return RetentionAction.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown retention action: " + value);
        }
    }

    /**
     * Parse ordering guarantee from string.
     */
    private OrderingGuarantee parseOrderingGuarantee(String value) {
        if (value == null) {
            return OrderingGuarantee.PER_AGGREGATE;
        }
        try {
            return OrderingGuarantee.valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Unknown ordering guarantee: " + value);
        }
    }

    /**
     * Parse ISO 8601 duration or time string.
     *
     * @param value duration string (e.g., "P7Y", "PT1H", "30s")
     * @return parsed duration or null
     */
    private Duration parseDuration(String value) {
        if (value == null || value.isBlank() || "FOREVER".equalsIgnoreCase(value)) {
            return null;
        }

        // Handle ISO 8601 duration
        if (value.startsWith("P")) {
            return Duration.parse(value);
        }

        // Handle simple time strings (30s, 5m, 1h, 7d)
        String digits = value.replaceAll("[^0-9]", "");
        String unit = value.replaceAll("[0-9]", "").toLowerCase();
        long amount = Long.parseLong(digits);

        return switch (unit) {
            case "s" ->
                Duration.ofSeconds(amount);
            case "m" ->
                Duration.ofMinutes(amount);
            case "h" ->
                Duration.ofHours(amount);
            case "d" ->
                Duration.ofDays(amount);
            default ->
                Duration.parse("PT" + value);
        };
    }
}
