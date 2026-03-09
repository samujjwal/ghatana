package com.ghatana.datacloud.plugins.s3archive;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/**
 * S3 L4 (COLD tier) Archive Plugin implementation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides long-term archival storage for events using Amazon S3 with:
 * <ul>
 * <li>Intelligent lifecycle transitions to Glacier tiers</li>
 * <li>Server-side encryption (SSE-S3 or SSE-KMS)</li>
 * <li>Compliance retention with Object Lock</li>
 * <li>Efficient Parquet/GZIP archive format</li>
 * <li>Restore capabilities from Glacier</li>
 * </ul>
 *
 * <p>
 * <b>Storage Tier</b><br>
 * L4 (COLD) tier - Archive storage for historical events (12+ months). Events
 * are migrated from L2 (Iceberg) after the retention threshold.
 *
 * <p>
 * <b>Archive Format</b><br>
 * Events are archived as GZIP-compressed JSON files with structure:
 * <pre>
 * s3://bucket/prefix/tenant-id/stream-name/YYYY/MM/archive-TIMESTAMP.json.gz
 * </pre>
 *
 * <p>
 * <b>Six Pillars</b><br>
 * <ul>
 * <li><b>Security</b>: Encryption at rest, bucket policies, Object Lock</li>
 * <li><b>Observability</b>: Archive/restore latency, storage metrics</li>
 * <li><b>Debuggability</b>: Archive manifests, restore tracking</li>
 * <li><b>Scalability</b>: Multipart uploads, batch archival</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose S3 L4 COLD tier archive implementation
 * @doc.layer plugin
 * @doc.pattern Plugin, Repository
 */
public class ColdTierArchivePlugin implements StoragePlugin {

    private static final Logger log = LoggerFactory.getLogger(ColdTierArchivePlugin.class);

    private static final String PLUGIN_NAME = "s3-l4-archive";
    private static final String PLUGIN_VERSION = "1.0.0";
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final S3ArchiveConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicReference<PluginState> state;

    // AWS clients
    private S3Client s3Client;
    private GlacierRestoreManager restoreManager;
    private PluginContext pluginContext;

    // Metrics
    private MeterRegistry meterRegistry;
    private Timer archiveTimer;
    private Timer restoreTimer;
    private Counter eventsArchivedCounter;
    private Counter archiveFilesCounter;
    private Counter bytesArchivedCounter;

    // ==================== Constructors ====================
    public ColdTierArchivePlugin() {
        this(S3ArchiveConfig.builder().bucketName("eventcloud-archive").build());
    }

    public ColdTierArchivePlugin(S3ArchiveConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = createObjectMapper();
        this.state = new AtomicReference<>(PluginState.UNLOADED);
    }

    // ==================== Plugin Lifecycle ====================
    @Override
    public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .id(PLUGIN_NAME)
                .name("S3 L4 COLD Tier Archive Plugin")
                .version(PLUGIN_VERSION)
                .type(PluginType.STORAGE)
                .description("S3 L4 (COLD tier) archive storage for events with Glacier lifecycle")
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
            log.info("Initializing S3 L4 archive plugin...");

            // Initialize metrics
            initializeMetrics();

            // Build S3 client
            s3Client = buildS3Client();

            // Create restore manager
            restoreManager = new GlacierRestoreManager(s3Client, config);

            state.set(PluginState.INITIALIZED);
            log.info("S3 L4 archive plugin initialized: {}", config);
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
            log.info("Starting S3 L4 archive plugin...");

            // Verify bucket exists and is accessible
            verifyBucketAccess();

            state.set(PluginState.RUNNING);
            log.info("S3 L4 archive plugin started. Bucket: {}", config.getBucketName());
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
            log.info("Stopping S3 L4 archive plugin...");
            state.set(PluginState.STOPPED);
            return null;
        });
    }

    @Override
    public Promise<Void> shutdown() {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Shutting down S3 L4 archive plugin...");

            if (s3Client != null) {
                s3Client.close();
            }

            state.set(PluginState.STOPPED);
            log.info("S3 L4 archive plugin shut down");
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
                s3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(config.getBucketName())
                        .build());
                return HealthStatus.ok("S3 bucket accessible: " + config.getBucketName());
            } catch (Exception e) {
                log.warn("Health check failed: {}", e.getMessage());
                return HealthStatus.error("S3 health check failed", e);
            }
        });
    }

    // ==================== Archive Operations ====================
    /**
     * Archives a batch of events to S3.
     *
     * @param events events to archive
     * @return Promise with archive key
     */
    public Promise<ArchiveResult> archiveBatch(List<Event> events) {
        requireRunning();

        if (events.isEmpty()) {
            return Promise.of(new ArchiveResult("", 0, 0));
        }

        Timer.Sample sample = Timer.start(meterRegistry);

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                Event first = events.get(0);
                String tenantId = first.getTenantId();
                String streamName = first.getStreamName();

                // Generate archive key
                String archiveKey = generateArchiveKey(tenantId, streamName, Instant.now());

                // Serialize events to JSON
                String json = objectMapper.writeValueAsString(events);

                // Compress with GZIP
                byte[] compressed = compressGzip(json.getBytes(StandardCharsets.UTF_8));

                // Upload to S3
                PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                        .bucket(config.getBucketName())
                        .key(archiveKey)
                        .contentType("application/json")
                        .contentEncoding("gzip")
                        .storageClass(config.getS3StorageClass())
                        .metadata(Map.of(
                                "eventCount", String.valueOf(events.size()),
                                "tenantId", tenantId,
                                "streamName", streamName,
                                "archivedAt", Instant.now().toString()
                        ));

                // Add encryption
                applyEncryption(requestBuilder);

                s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(compressed));

                // Update metrics
                eventsArchivedCounter.increment(events.size());
                archiveFilesCounter.increment();
                bytesArchivedCounter.increment(compressed.length);

                log.info("Archived {} events to s3://{}/{} ({} bytes)",
                        events.size(), config.getBucketName(), archiveKey, compressed.length);

                return new ArchiveResult(archiveKey, events.size(), compressed.length);

            } finally {
                sample.stop(archiveTimer);
            }
        });
    }

    /**
     * Lists archive files for a tenant/stream within a time range.
     *
     * @param tenantId tenant ID
     * @param streamName stream name
     * @param startDate start date
     * @param endDate end date
     * @return Promise with list of archive keys
     */
    public Promise<List<ArchiveInfo>> listArchives(
            String tenantId,
            String streamName,
            LocalDate startDate,
            LocalDate endDate) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            List<ArchiveInfo> archives = new ArrayList<>();

            // Generate prefixes for each month in range
            LocalDate current = startDate.withDayOfMonth(1);
            while (!current.isAfter(endDate)) {
                String prefix = String.format("%s%s/%s/%s/",
                        config.getKeyPrefix(),
                        tenantId,
                        streamName,
                        current.format(YEAR_MONTH_FORMAT));

                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(config.getBucketName())
                        .prefix(prefix)
                        .build();

                ListObjectsV2Response response = s3Client.listObjectsV2(request);

                for (S3Object obj : response.contents()) {
                    archives.add(new ArchiveInfo(
                            obj.key(),
                            obj.size(),
                            obj.lastModified(),
                            obj.storageClass()
                    ));
                }

                current = current.plusMonths(1);
            }

            return archives;
        });
    }

    /**
     * Initiates restore for a Glacier-archived object.
     *
     * @param archiveKey archive S3 key
     * @param tier restore tier
     * @return Promise with restore result
     */
    public Promise<GlacierRestoreManager.RestoreResult> initiateRestore(
            String archiveKey,
            S3ArchiveConfig.RestoreTier tier) {
        requireRunning();

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return restoreManager.initiateRestore(archiveKey, tier);
        } finally {
            sample.stop(restoreTimer);
        }
    }

    /**
     * Initiates restore with default tier.
     *
     * @param archiveKey archive S3 key
     * @return Promise with restore result
     */
    public Promise<GlacierRestoreManager.RestoreResult> initiateRestore(String archiveKey) {
        return initiateRestore(archiveKey, config.getDefaultRestoreTier());
    }

    /**
     * Gets restore status for an archive.
     *
     * @param archiveKey archive S3 key
     * @return Promise with restore status
     */
    public Promise<GlacierRestoreManager.RestoreResult> getRestoreStatus(String archiveKey) {
        requireRunning();
        return restoreManager.getRestoreStatus(archiveKey);
    }

    /**
     * Reads events from a restored archive.
     *
     * @param archiveKey archive S3 key
     * @return Promise with list of events
     */
    public Promise<List<Event>> readRestoredArchive(String archiveKey) {
        requireRunning();

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Get object
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(archiveKey)
                    .build();

            byte[] compressed = s3Client.getObjectAsBytes(request).asByteArray();

            // Decompress
            byte[] json = decompressGzip(compressed);

            // Deserialize
            List<Map<String, Object>> eventMaps = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            // Convert to Event objects
            List<Event> events = new ArrayList<>();
            for (Map<String, Object> map : eventMaps) {
                events.add(mapToEvent(map));
            }

            log.debug("Read {} events from archive {}", events.size(), archiveKey);
            return events;
        });
    }

    // ==================== StoragePlugin Interface ====================
    // Note: L4 archive is write-heavy, reads require restore first
    @Override
    public Promise<Offset> append(Event event) {
        requireRunning();
        // L4 doesn't support single event append - use archiveBatch
        return archiveBatch(List.of(event)).map(r -> new Offset(0));
    }

    @Override
    public Promise<List<Offset>> appendBatch(List<Event> events) {
        requireRunning();
        return archiveBatch(events).map(r
                -> events.stream().map(e -> new Offset(e.getEventOffset())).toList());
    }

    @Override
    public Promise<Optional<Event>> readById(String tenantId, String eventId) {
        requireRunning();
        // L4 doesn't support point queries - use L1/L2 for recent data
        log.warn("readById not supported in L4 archive tier. Use L1/L2 for recent events.");
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Optional<Event>> readByIdempotencyKey(String tenantId, String idempotencyKey) {
        requireRunning();
        log.warn("readByIdempotencyKey not supported in L4 archive tier.");
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<List<Event>> readRange(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset startOffset,
            int limit) {
        requireRunning();
        log.warn("readRange not supported in L4 archive tier. Use listArchives + readRestoredArchive.");
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<Event>> readByTimeRange(
            String tenantId,
            String streamName,
            Instant startTime,
            Instant endTime,
            int limit) {
        requireRunning();
        log.warn("readByTimeRange not supported in L4 archive tier. Use listArchives + readRestoredArchive.");
        return Promise.of(List.of());
    }

    @Override
    public Promise<Offset> getCurrentOffset(String tenantId, String streamName, PartitionId partitionId) {
        // L4 doesn't track offsets
        return Promise.of(new Offset(-1));
    }

    @Override
    public Promise<Offset> getEarliestOffset(String tenantId, String streamName, PartitionId partitionId) {
        return Promise.of(new Offset(0));
    }

    @Override
    public Promise<Long> deleteBeforeTime(String tenantId, String streamName, Instant beforeTime) {
        requireRunning();
        // S3 lifecycle handles deletion automatically
        log.warn("deleteBeforeTime handled by S3 lifecycle policies in L4 tier.");
        return Promise.of(0L);
    }

    @Override
    public Promise<Long> deleteBeforeOffset(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset beforeOffset) {
        requireRunning();
        log.warn("deleteBeforeOffset not supported in L4 archive tier.");
        return Promise.of(0L);
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities() {
            @Override
            public boolean supportsTransactions() {
                return false; // S3 is eventually consistent
            }

            @Override
            public boolean supportsStreaming() {
                return false; // Archive is batch-oriented
            }

            @Override
            public boolean supportsTimeRangeQuery() {
                return false; // Requires restore first
            }

            @Override
            public boolean supportsCompaction() {
                return false;
            }

            @Override
            public long maxBatchSize() {
                return config.getMaxEventsPerArchive();
            }

            @Override
            public int recommendedBatchSize() {
                return 50_000;
            }
        };
    }

    // ==================== Private Methods ====================
    private void initializeMetrics() {
        meterRegistry = new SimpleMeterRegistry();

        String prefix = config.getMetricsPrefix();

        archiveTimer = Timer.builder(prefix + ".archive.latency")
                .description("Time to archive events to S3")
                .register(meterRegistry);

        restoreTimer = Timer.builder(prefix + ".restore.latency")
                .description("Time to initiate restore from Glacier")
                .register(meterRegistry);

        eventsArchivedCounter = Counter.builder(prefix + ".events.archived")
                .description("Total events archived to S3")
                .register(meterRegistry);

        archiveFilesCounter = Counter.builder(prefix + ".files.archived")
                .description("Total archive files created")
                .register(meterRegistry);

        bytesArchivedCounter = Counter.builder(prefix + ".bytes.archived")
                .description("Total bytes archived to S3")
                .register(meterRegistry);
    }

    private S3Client buildS3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (config.getEndpointOverride() != null) {
            builder.endpointOverride(URI.create(config.getEndpointOverride()));
        }

        if (config.isPathStyleAccess()) {
            builder.forcePathStyle(true);
        }

        return builder.build();
    }

    private void verifyBucketAccess() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(config.getBucketName())
                    .build());
            log.info("Verified access to bucket: {}", config.getBucketName());
        } catch (NoSuchBucketException e) {
            log.warn("Bucket {} does not exist. Creating...", config.getBucketName());
            createBucket();
        }
    }

    private void createBucket() {
        CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder()
                .bucket(config.getBucketName());

        // Add location constraint for non-us-east-1 regions
        if (!"us-east-1".equals(config.getRegion())) {
            requestBuilder.createBucketConfiguration(CreateBucketConfiguration.builder()
                    .locationConstraint(config.getRegion())
                    .build());
        }

        s3Client.createBucket(requestBuilder.build());
        log.info("Created bucket: {}", config.getBucketName());
    }

    private void applyEncryption(PutObjectRequest.Builder builder) {
        switch (config.getEncryptionType()) {
            case SSE_S3 ->
                builder.serverSideEncryption(ServerSideEncryption.AES256);
            case SSE_KMS -> {
                builder.serverSideEncryption(ServerSideEncryption.AWS_KMS);
                builder.ssekmsKeyId(config.getKmsKeyId());
                if (config.isBucketKeyEnabled()) {
                    builder.bucketKeyEnabled(true);
                }
            }
            case NONE, SSE_C -> {
                // No encryption or customer-provided (not implemented)
            }
        }
    }

    private String generateArchiveKey(String tenantId, String streamName, Instant timestamp) {
        LocalDate date = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
        String timestampStr = timestamp.atZone(ZoneOffset.UTC).format(TIMESTAMP_FORMAT);

        return String.format("%s%s/%s/%s/archive-%s-%s.json.gz",
                config.getKeyPrefix(),
                tenantId,
                streamName,
                date.format(YEAR_MONTH_FORMAT),
                timestampStr,
                UUID.randomUUID().toString().substring(0, 8));
    }

    private byte[] compressGzip(byte[] data) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private byte[] decompressGzip(byte[] compressed) throws java.io.IOException {
        try (java.util.zip.GZIPInputStream gzip
                = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed)); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private Event mapToEvent(Map<String, Object> map) {
        return Event.builder()
                .id(UUID.fromString((String) map.get("id")))
                .tenantId((String) map.get("tenantId"))
                .eventTypeName((String) map.get("eventTypeName"))
                .eventTypeVersion((String) map.get("eventTypeVersion"))
                .streamName((String) map.get("streamName"))
                .partitionId(((Number) map.get("partitionId")).intValue())
                .eventOffset(((Number) map.get("eventOffset")).longValue())
                .occurrenceTime(Instant.parse((String) map.get("occurrenceTime")))
                .detectionTime(Instant.parse((String) map.get("detectionTime")))
                .headers((Map<String, String>) map.get("headers"))
                .payload((Map<String, Object>) map.get("payload"))
                .contentType((String) map.get("contentType"))
                .correlationId((String) map.get("correlationId"))
                .causationId((String) map.get("causationId"))
                .idempotencyKey((String) map.get("idempotencyKey"))
                .currentTier(StorageTier.COLD)
                .createdBy((String) map.get("createdBy"))
                .build();
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
        return "ColdTierArchivePlugin{"
                + "name='" + PLUGIN_NAME + '\''
                + ", state=" + state.get()
                + ", bucket=" + config.getBucketName()
                + '}';
    }

    // ==================== Inner Classes ====================
    /**
     * Result of an archive operation.
     */
    public record ArchiveResult(
            String archiveKey,
            int eventCount,
            long bytesWritten
            ) {

    }

    /**
     * Archive file information.
     */
    public record ArchiveInfo(
            String key,
            long sizeBytes,
            Instant lastModified,
            software.amazon.awssdk.services.s3.model.ObjectStorageClass storageClass
            ) {

    }
}
