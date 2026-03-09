package com.ghatana.datacloud.plugins.iceberg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.datacloud.event.spi.StoragePlugin;
import com.ghatana.platform.plugin.HealthStatus;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Apache Iceberg L2 (COOL tier) storage plugin implementation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides columnar analytics storage for events using Apache Iceberg with:
 * <ul>
 * <li>Parquet file format for efficient columnar storage</li>
 * <li>Partitioning by tenant, stream, and detection date</li>
 * <li>Time-travel queries for historical analysis</li>
 * <li>ACID transactions with concurrent reads</li>
 * <li>Schema evolution without data rewrite</li>
 * </ul>
 *
 * <p>
 * <b>Storage Tier</b><br>
 * L2 (COOL) tier - Analytics storage for historical events (30+ days). Events
 * are migrated from L1 (PostgreSQL) after the retention threshold.
 *
 * <p>
 * <b>Six Pillars</b><br>
 * <ul>
 * <li><b>Security</b>: Tenant isolation via partition pruning</li>
 * <li><b>Observability</b>: Write latency, file count, snapshot metrics</li>
 * <li><b>Debuggability</b>: Detailed logging, snapshot history</li>
 * <li><b>Scalability</b>: Batch writes, automatic compaction</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * IcebergStorageConfig config = IcebergStorageConfig.builder()
 *     .catalogType(CatalogType.HADOOP)
 *     .warehousePath("s3://eventcloud-lake/warehouse")
 *     .fileFormat(FileFormat.PARQUET)
 *     .compressionCodec("zstd")
 *     .build();
 *
 * CoolTierStoragePlugin plugin = new CoolTierStoragePlugin(config);
 * plugin.initialize(context);
 * plugin.start();
 *
 * // Batch append (recommended for L2)
 * List<Offset> offsets = plugin.appendBatch(events).getResult();
 *
 * // Time-range query
 * List<Event> events = plugin.readByTimeRange(
 *     tenantId, streamName, startTime, endTime, 1000).getResult();
 *
 * // Time-travel query
 * Instant snapshotTime = Instant.now().minus(Duration.ofDays(1));
 * List<Event> historicalEvents = plugin.readAtSnapshot(
 *     tenantId, streamName, snapshotTime, 1000).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Apache Iceberg L2 COOL tier storage implementation
 * @doc.layer plugin
 * @doc.pattern Plugin, Repository
 */
public class CoolTierStoragePlugin implements StoragePlugin {

    private static final Logger log = LoggerFactory.getLogger(CoolTierStoragePlugin.class);

    private static final String PLUGIN_NAME = "iceberg-l2-storage";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final String TABLE_NAME = "events";

    private final IcebergStorageConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicReference<PluginState> state;

    // Iceberg components
    private Catalog catalog;
    private Table eventsTable;
    private IcebergTableManager tableManager;
    private PluginContext pluginContext;

    // Offset tracking (in-memory for L2, actual offset comes from L1)
    private final ConcurrentHashMap<String, AtomicLong> partitionOffsets = new ConcurrentHashMap<>();

    // Metrics
    private MeterRegistry meterRegistry;
    private Timer appendTimer;
    private Timer queryTimer;
    private Counter eventsAppendedCounter;
    private Counter filesWrittenCounter;

    // ==================== Constructors ====================
    public CoolTierStoragePlugin() {
        this(IcebergStorageConfig.defaults());
    }

    public CoolTierStoragePlugin(IcebergStorageConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = createObjectMapper();
        this.state = new AtomicReference<>(PluginState.UNLOADED);
    }

    // ==================== Plugin Lifecycle ====================
    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .id(PLUGIN_NAME)
                .name("Apache Iceberg L2 COOL Tier Storage Plugin")
                .version(PLUGIN_VERSION)
                .type(PluginType.STORAGE)
                .description("Apache Iceberg L2 (COOL tier) analytics storage for events")
                .vendor("Ghatana")
                .build();
    }

    @Override
    public PluginState getState() {
        return state.get();
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        if (!state.compareAndSet(PluginState.UNLOADED, PluginState.DISCOVERED)) {
            return Promise.ofException(new IllegalStateException(
                    "Cannot initialize plugin in state: " + state.get()));
        }

        this.pluginContext = context;
        config.validate();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Initializing Iceberg L2 storage plugin...");

            // Initialize metrics
            initializeMetrics();

            // Create Hadoop configuration
            Configuration hadoopConf = createHadoopConfig();

            // Create Iceberg catalog based on type
            catalog = createCatalog(hadoopConf);

            // Create table manager
            tableManager = new IcebergTableManager(catalog, config);

            state.set(PluginState.INITIALIZED);
            log.info("Iceberg L2 storage plugin initialized: {}", config);
            return null;
        });
    }

    @Override
    public Promise<Void> start() {
        if (!state.compareAndSet(PluginState.INITIALIZED, PluginState.STARTED)) {
            return Promise.ofException(new IllegalStateException(
                    "Cannot start plugin in state: " + state.get()));
        }

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Starting Iceberg L2 storage plugin...");

            // Create or load events table
            eventsTable = tableManager.createTableIfNotExists(TABLE_NAME).getResult();

            state.set(PluginState.RUNNING);
            log.info("Iceberg L2 storage plugin started. Table: {}.{}",
                    config.getDatabaseName(), TABLE_NAME);
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        PluginState current = state.get();
        if (current != PluginState.RUNNING && current != PluginState.STARTED) {
            return Promise.complete();
        }

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Stopping Iceberg L2 storage plugin...");
            state.set(PluginState.STOPPED);
            return null;
        });
    }

    @Override
    public Promise<Void> shutdown() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Shutting down Iceberg L2 storage plugin...");

            if (tableManager != null) {
                tableManager.close();
            }

            state.set(PluginState.STOPPED);
            log.info("Iceberg L2 storage plugin shut down");
            return null;
        });
    }

    @Override
    public Promise<HealthStatus> healthCheck() {
        if (state.get() != PluginState.RUNNING) {
            return Promise.of(HealthStatus.error("Plugin not running, state: " + state.get()));
        }

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Verify table is accessible
                eventsTable.refresh();
                return HealthStatus.ok("Iceberg table accessible");
            } catch (Exception e) {
                log.warn("Health check failed: {}", e.getMessage());
                return HealthStatus.error("Iceberg health check failed", e);
            }
        });
    }

    // ==================== Append Operations ====================
    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Note</b>: For L2 storage, single event appends are discouraged. Use
     * {@link #appendBatch(List)} for better performance.</p>
     */
    @Override
    public Promise<Offset> append(Event event) {
        requireRunning();
        return appendBatch(List.of(event)).map(offsets -> offsets.get(0));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>L2 Optimization</b>: Writes events as Parquet files with partitioning
     * by tenant_id, stream_name, and detection_date.</p>
     */
    @Override
    public Promise<List<Offset>> appendBatch(List<Event> events) {
        requireRunning();

        if (events.isEmpty()) {
            return Promise.of(List.of());
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<Offset> offsets = new ArrayList<>();

            try {
                // Group events by partition for efficient writing
                Map<String, List<Event>> eventsByPartition = groupEventsByPartition(events);

                // Write each partition group
                List<DataFile> dataFiles = new ArrayList<>();

                for (Map.Entry<String, List<Event>> entry : eventsByPartition.entrySet()) {
                    String partitionKey = entry.getKey();
                    List<Event> partitionEvents = entry.getValue();

                    // Write Parquet file for this partition
                    DataFile dataFile = writeParquetFile(partitionKey, partitionEvents);
                    if (dataFile != null) {
                        dataFiles.add(dataFile);
                    }

                    // Track offsets
                    for (Event event : partitionEvents) {
                        offsets.add(new Offset(event.getEventOffset()));
                    }
                }

                // Commit all files atomically
                if (!dataFiles.isEmpty()) {
                    AppendFiles append = eventsTable.newAppend();
                    for (DataFile dataFile : dataFiles) {
                        append.appendFile(dataFile);
                    }
                    append.commit();

                    filesWrittenCounter.increment(dataFiles.size());
                    eventsAppendedCounter.increment(events.size());

                    log.debug("Committed {} data files with {} events to Iceberg",
                            dataFiles.size(), events.size());
                }

            } finally {
                sample.stop(appendTimer);
            }

            return offsets;
        });
    }

    // ==================== Read Operations ====================
    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Note</b>: Point queries by ID are inefficient in L2. Consider using L1
     * (PostgreSQL) for recent events.</p>
     */
    @Override
    public Promise<Optional<Event>> readById(String tenantId, String eventId) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<Record> records = tableManager.scanTable(
                        eventsTable,
                        tenantId,
                        null, // all streams
                        null, // no time filter
                        null,
                        1000 // scan limit
                ).getResult();

                // Filter by ID (inefficient but works)
                for (Record record : records) {
                    if (eventId.equals(record.getField("id"))) {
                        return Optional.of(mapRecordToEvent(record));
                    }
                }

                return Optional.empty();
            } finally {
                sample.stop(queryTimer);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<Optional<Event>> readByIdempotencyKey(String tenantId, String idempotencyKey) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<Record> records = tableManager.scanTable(
                    eventsTable,
                    tenantId,
                    null,
                    null,
                    null,
                    1000
            ).getResult();

            for (Record record : records) {
                String key = (String) record.getField("idempotency_key");
                if (idempotencyKey.equals(key)) {
                    return Optional.of(mapRecordToEvent(record));
                }
            }

            return Optional.empty();
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Promise<List<Event>> readRange(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset startOffset,
            int limit) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<Record> records = tableManager.scanTable(
                        eventsTable,
                        tenantId,
                        streamName,
                        null,
                        null,
                        limit * 10 // Over-fetch then filter
                ).getResult();

                // Filter by partition and offset
                return records.stream()
                        .filter(r -> ((Integer) r.getField("partition_id")).equals(partitionId.value()))
                        .filter(r -> ((Long) r.getField("event_offset")) >= startOffset.value())
                        .limit(limit)
                        .map(this::mapRecordToEvent)
                        .toList();
            } finally {
                sample.stop(queryTimer);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>L2 Optimization</b>: Time-range queries are efficient in L2 due to
     * time-based partitioning and predicate pushdown.</p>
     */
    @Override
    public Promise<List<Event>> readByTimeRange(
            String tenantId,
            String streamName,
            Instant startTime,
            Instant endTime,
            int limit) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<Record> records = tableManager.scanTable(
                        eventsTable,
                        tenantId,
                        streamName,
                        startTime,
                        endTime,
                        limit
                ).getResult();

                return records.stream()
                        .map(this::mapRecordToEvent)
                        .toList();
            } finally {
                sample.stop(queryTimer);
            }
        });
    }

    /**
     * Reads events at a specific snapshot (time-travel query).
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name (optional)
     * @param snapshotTime timestamp for the snapshot
     * @param limit max events
     * @return Promise with list of events as of snapshot time
     */
    public Promise<List<Event>> readAtSnapshot(
            String tenantId,
            String streamName,
            Instant snapshotTime,
            int limit) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                List<Record> records = tableManager.scanAtSnapshot(
                        eventsTable,
                        snapshotTime,
                        tenantId,
                        limit
                ).getResult();

                // Filter by stream if specified
                return records.stream()
                        .filter(r -> streamName == null
                        || streamName.equals(r.getField("stream_name")))
                        .map(this::mapRecordToEvent)
                        .toList();
            } finally {
                sample.stop(queryTimer);
            }
        });
    }

    // ==================== Offset Operations ====================
    @Override
    public Promise<Offset> getCurrentOffset(String tenantId, String streamName, PartitionId partitionId) {
        requireRunning();

        String key = offsetKey(tenantId, streamName, partitionId);
        AtomicLong offset = partitionOffsets.get(key);

        return Promise.of(offset != null ? new Offset(offset.get()) : new Offset(-1));
    }

    @Override
    public Promise<Offset> getEarliestOffset(String tenantId, String streamName, PartitionId partitionId) {
        requireRunning();
        // L2 doesn't track earliest offset - events come from L1
        return Promise.of(new Offset(0));
    }

    // ==================== Retention Operations ====================
    @Override
    public Promise<Long> deleteBeforeTime(String tenantId, String streamName, Instant beforeTime) {
        requireRunning();
        // L2 deletion is handled via snapshot expiration and partition deletion
        log.warn("deleteBeforeTime not directly supported in L2. Use snapshot expiration.");
        return Promise.of(0L);
    }

    @Override
    public Promise<Long> deleteBeforeOffset(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset beforeOffset) {
        requireRunning();
        log.warn("deleteBeforeOffset not directly supported in L2.");
        return Promise.of(0L);
    }

    // ==================== Maintenance Operations ====================
    /**
     * Expires old snapshots to free storage.
     *
     * @return Promise completing when snapshots are expired
     */
    public Promise<Void> expireSnapshots() {
        requireRunning();
        return tableManager.expireSnapshots(eventsTable);
    }

    /**
     * Lists all available snapshots for time-travel queries.
     *
     * @return list of snapshot information
     */
    public List<IcebergTableManager.SnapshotInfo> listSnapshots() {
        requireRunning();
        return tableManager.listSnapshots(eventsTable);
    }

    /**
     * Refreshes the table metadata.
     *
     * @return Promise completing when refresh is done
     */
    public Promise<Void> refreshTable() {
        requireRunning();
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            eventsTable.refresh();
            return null;
        });
    }

    // ==================== Capabilities ====================
    @Override
    public Capabilities capabilities() {
        return new Capabilities() {
            @Override
            public boolean supportsTransactions() {
                return true; // Iceberg provides ACID transactions
            }

            @Override
            public boolean supportsStreaming() {
                return false; // L2 is batch-oriented
            }

            @Override
            public boolean supportsTimeRangeQuery() {
                return true; // Optimized for time-range queries
            }

            @Override
            public boolean supportsCompaction() {
                return true; // Via Iceberg's maintenance operations
            }

            @Override
            public long maxBatchSize() {
                return config.getMaxBatchSize();
            }

            @Override
            public int recommendedBatchSize() {
                return config.getRecommendedBatchSize();
            }
        };
    }

    // ==================== Private Methods ====================
    private void initializeMetrics() {
        meterRegistry = new SimpleMeterRegistry();

        String prefix = config.getMetricsPrefix();

        appendTimer = Timer.builder(prefix + ".append.latency")
                .description("Time to append events to Iceberg")
                .register(meterRegistry);

        queryTimer = Timer.builder(prefix + ".query.latency")
                .description("Time to query events from Iceberg")
                .register(meterRegistry);

        eventsAppendedCounter = Counter.builder(prefix + ".events.appended")
                .description("Total events appended to Iceberg")
                .register(meterRegistry);

        filesWrittenCounter = Counter.builder(prefix + ".files.written")
                .description("Total data files written to Iceberg")
                .register(meterRegistry);
    }

    private Configuration createHadoopConfig() {
        Configuration conf = new Configuration();

        // S3 configuration
        if (config.getWarehousePath().startsWith("s3://")) {
            conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

            if (config.getS3Endpoint() != null) {
                conf.set("fs.s3a.endpoint", config.getS3Endpoint());
            }

            if (config.isS3PathStyleAccess()) {
                conf.set("fs.s3a.path.style.access", "true");
            }

            // AWS credentials from environment or instance profile
            conf.set("fs.s3a.aws.credentials.provider",
                    "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider,"
                    + "com.amazonaws.auth.EnvironmentVariableCredentialsProvider,"
                    + "com.amazonaws.auth.InstanceProfileCredentialsProvider");
        }

        return conf;
    }

    private Catalog createCatalog(Configuration hadoopConf) {
        return switch (config.getCatalogType()) {
            case HADOOP -> {
                HadoopCatalog hadoopCatalog = new HadoopCatalog();
                hadoopCatalog.setConf(hadoopConf);

                Map<String, String> properties = new HashMap<>();
                properties.put("warehouse", config.getWarehousePath());
                hadoopCatalog.initialize(config.getCatalogName(), properties);

                yield hadoopCatalog;
            }
            case GLUE -> {
                // For AWS Glue, would use GlueCatalog
                throw new UnsupportedOperationException(
                        "GLUE catalog requires iceberg-aws-glue dependency");
            }
            case HIVE -> {
                // For Hive, would use HiveCatalog
                throw new UnsupportedOperationException(
                        "HIVE catalog requires iceberg-hive-metastore dependency");
            }
            case NESSIE -> {
                throw new UnsupportedOperationException(
                        "NESSIE catalog requires iceberg-nessie dependency");
            }
            case REST -> {
                throw new UnsupportedOperationException(
                        "REST catalog requires additional configuration");
            }
        };
    }

    private Map<String, List<Event>> groupEventsByPartition(List<Event> events) {
        Map<String, List<Event>> grouped = new LinkedHashMap<>();

        for (Event event : events) {
            LocalDate detectionDate = event.getDetectionTime()
                    .atZone(ZoneOffset.UTC).toLocalDate();

            String partitionKey = String.format("%s/%s/%s",
                    event.getTenantId(),
                    event.getStreamName(),
                    detectionDate);

            grouped.computeIfAbsent(partitionKey, k -> new ArrayList<>()).add(event);
        }

        return grouped;
    }

    private DataFile writeParquetFile(String partitionKey, List<Event> events) throws IOException {
        if (events.isEmpty()) {
            return null;
        }

        // Generate unique file path
        String fileName = String.format("%s-%s.parquet",
                partitionKey.replace("/", "_"),
                UUID.randomUUID());

        String filePath = config.getWarehousePath() + "/"
                + config.getDatabaseName() + "/" + TABLE_NAME + "/data/" + fileName;

        OutputFile outputFile = eventsTable.io().newOutputFile(filePath);

        try (DataWriter<GenericRecord> writer = Parquet.writeData(outputFile)
                .schema(IcebergTableManager.EVENT_SCHEMA)
                .createWriterFunc(GenericParquetWriter::create)
                .overwrite()
                .withSpec(eventsTable.spec())
                .build()) {

            for (Event event : events) {
                GenericRecord record = eventToRecord(event);
                writer.write(record);
            }

            return writer.toDataFile();
        }
    }

    private GenericRecord eventToRecord(Event event) {
        GenericRecord record = GenericRecord.create(IcebergTableManager.EVENT_SCHEMA);

        record.setField("id", event.getId().toString());
        record.setField("tenant_id", event.getTenantId());
        record.setField("event_type_name", event.getEventTypeName());
        record.setField("event_type_version", event.getEventTypeVersion());
        record.setField("stream_name", event.getStreamName());
        record.setField("partition_id", event.getPartitionId());
        record.setField("event_offset", event.getEventOffset());

        record.setField("occurred_at", event.getOccurrenceTime().atOffset(ZoneOffset.UTC));
        record.setField("detected_at", event.getDetectionTime().atOffset(ZoneOffset.UTC));
        record.setField("ingested_at", Instant.now().atOffset(ZoneOffset.UTC));

        try {
            record.setField("headers", objectMapper.writeValueAsString(event.getHeaders()));
            record.setField("payload", objectMapper.writeValueAsString(event.getPayload()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }

        record.setField("content_type", event.getContentType());
        record.setField("correlation_id", event.getCorrelationId());
        record.setField("causation_id", event.getCausationId());
        record.setField("idempotency_key", event.getIdempotencyKey());

        LocalDate detectionDate = event.getDetectionTime().atZone(ZoneOffset.UTC).toLocalDate();
        record.setField("detection_date", detectionDate);

        record.setField("created_by", event.getCreatedBy());

        return record;
    }

    @SuppressWarnings("unchecked")
    private Event mapRecordToEvent(Record record) {
        try {
            String headersJson = (String) record.getField("headers");
            String payloadJson = (String) record.getField("payload");

            Map<String, String> headers = headersJson != null
                    ? objectMapper.readValue(headersJson,
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                    : Map.of();

            Map<String, Object> payload = objectMapper.readValue(payloadJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

            java.time.OffsetDateTime occurredAt = (java.time.OffsetDateTime) record.getField("occurred_at");
            java.time.OffsetDateTime detectedAt = (java.time.OffsetDateTime) record.getField("detected_at");

            return Event.builder()
                    .id(UUID.fromString((String) record.getField("id")))
                    .tenantId((String) record.getField("tenant_id"))
                    .eventTypeName((String) record.getField("event_type_name"))
                    .eventTypeVersion((String) record.getField("event_type_version"))
                    .streamName((String) record.getField("stream_name"))
                    .partitionId((Integer) record.getField("partition_id"))
                    .eventOffset((Long) record.getField("event_offset"))
                    .occurrenceTime(occurredAt.toInstant())
                    .detectionTime(detectedAt.toInstant())
                    .headers(headers)
                    .payload(payload)
                    .contentType((String) record.getField("content_type"))
                    .correlationId((String) record.getField("correlation_id"))
                    .causationId((String) record.getField("causation_id"))
                    .idempotencyKey((String) record.getField("idempotency_key"))
                    .currentTier(StorageTier.COOL)
                    .createdBy((String) record.getField("created_by"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }

    private String offsetKey(String tenantId, String streamName, PartitionId partitionId) {
        return String.format("%s:%s:%d", tenantId, streamName, partitionId.value());
    }

    private void requireRunning() {
        if (state.get() != PluginState.RUNNING) {
            throw new IllegalStateException("Plugin not running: " + state.get());
        }
    }

    private ObjectMapper createObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }

    @Override
    public String toString() {
        return "CoolTierStoragePlugin{"
                + "name='" + PLUGIN_NAME + '\''
                + ", state=" + state.get()
                + ", config=" + config
                + '}';
    }
}
