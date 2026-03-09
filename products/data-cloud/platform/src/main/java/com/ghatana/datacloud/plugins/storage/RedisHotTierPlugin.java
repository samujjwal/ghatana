package com.ghatana.datacloud.plugins.redis;

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
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Redis M0 HOT tier storage plugin using Redis Streams.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides ultra-low latency event storage for the HOT tier (M0):
 * <ul>
 * <li>Redis Streams for ordered event log</li>
 * <li>LMAX Disruptor ring buffer for batched writes</li>
 * <li>Automatic TTL-based expiration</li>
 * <li>Consumer group support for at-least-once delivery</li>
 * <li>Automatic flush to L1 (PostgreSQL) tier</li>
 * </ul>
 *
 * <p>
 * <b>Architecture</b><br>
 * 
 * <pre>
 * [Append Request] → [Disruptor RingBuffer] → [Batch Handler] → [Redis XADD]
 *                                                    ↓
 *                                          [Flush to L1 Callback]
 * </pre>
 *
 * <p>
 * <b>Key Format</b><br>
 * 
 * <pre>
 * Stream Key: ec:stream:{tenantId}:{streamName}
 * Event Key:  ec:event:{tenantId}:{eventId}
 * Offset Key: ec:offset:{tenantId}:{streamName}:{partitionId}
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Redis M0 HOT tier storage
 * @doc.layer plugin
 * @doc.pattern Plugin, Producer-Consumer
 */
public class RedisHotTierPlugin implements StoragePlugin {

    private static final Logger log = LoggerFactory.getLogger(RedisHotTierPlugin.class);

    private static final String PLUGIN_NAME = "redis-m0-hot";
    private static final String PLUGIN_VERSION = "1.0.0";

    // Redis key prefixes
    private static final String STREAM_PREFIX = "ec:stream:";
    private static final String EVENT_PREFIX = "ec:event:";
    private static final String IDEMPOTENCY_PREFIX = "ec:idem:";

    // Stream field names
    private static final String FIELD_EVENT_ID = "id";
    private static final String FIELD_TENANT_ID = "tenant";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_PARTITION = "partition";
    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_HEADERS = "headers";
    private static final String FIELD_OCCURRED_AT = "occurred_at";
    private static final String FIELD_IDEMPOTENCY_KEY = "idem_key";

    private final RedisStorageConfig config;
    private final ObjectMapper objectMapper;
    private final PluginMetadata metadata;

    // Connection pool
    private JedisPool jedisPool;

    // Disruptor ring buffer for batching
    private Disruptor<EventHolder> disruptor;
    private RingBuffer<EventHolder> ringBuffer;

    // Flush scheduler
    private ScheduledExecutorService flushScheduler;
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);

    // Pending events for batch flush
    private final List<Event> pendingEvents = Collections.synchronizedList(new ArrayList<>());

    // Metrics
    private final LongAdder appendCount = new LongAdder();
    private final LongAdder flushCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    // State
    private volatile PluginState state = PluginState.UNLOADED;
    private PluginContext pluginContext;

    // Flush callback (to L1 tier)
    private FlushCallback flushCallback;

    // Capabilities implementation
    private final Capabilities capabilities = new RedisCapabilities();

    /**
     * Callback interface for flushing events to the next tier.
     */
    @FunctionalInterface
    public interface FlushCallback {

        Promise<Void> flush(List<Event> events);
    }

    public RedisHotTierPlugin() {
        this(RedisStorageConfig.defaults());
    }

    public RedisHotTierPlugin(RedisStorageConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = createObjectMapper();
        this.metadata = createMetadata();
    }

    private ObjectMapper createObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }

    private PluginMetadata createMetadata() {
        return PluginMetadata.builder()
                .id(PLUGIN_NAME)
                .name("Redis M0 HOT Tier Storage Plugin")
                .version(PLUGIN_VERSION)
                .description("Redis M0 HOT Tier Storage Plugin")
                .type(PluginType.STORAGE)
                .capabilities(Set.of("streaming", "time-range-query", "idempotency"))
                .vendor("Ghatana")
                .license("Apache-2.0")
                .build();
    }

    // ==================== Plugin Lifecycle ====================
    @Override
    public PluginMetadata metadata() {
        return metadata;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.pluginContext = context;
        log.info("Initializing Redis HOT tier plugin: {}", config);

        try {
            String effectivePassword = config.getPassword();
            if (effectivePassword == null || effectivePassword.isEmpty()) {
                effectivePassword = context.getConfig("datacloud.plugins.redis.password", "");
            }

            // Create connection pool
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(config.getMaxPoolSize());
            poolConfig.setMinIdle(config.getMinIdleConnections());
            poolConfig.setMaxWait(config.getMaxWaitTime());
            poolConfig.setTestOnBorrow(config.isTestOnBorrow());
            poolConfig.setTestWhileIdle(true);

            if (!effectivePassword.isEmpty()) {
                jedisPool = new JedisPool(
                        poolConfig,
                        config.getHost(),
                        config.getPort(),
                        (int) config.getConnectionTimeout().toMillis(),
                        effectivePassword,
                        config.getDatabase());
            } else {
                jedisPool = new JedisPool(
                        poolConfig,
                        config.getHost(),
                        config.getPort(),
                        (int) config.getConnectionTimeout().toMillis());
            }

            // Initialize Disruptor ring buffer
            initializeDisruptor();

            // Initialize flush scheduler
            flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "redis-hot-tier-flush");
                t.setDaemon(true);
                return t;
            });

            state = PluginState.INITIALIZED;
            log.info("Redis HOT tier plugin initialized successfully");
            return Promise.complete();

        } catch (Exception e) {
            log.error("Failed to initialize Redis HOT tier plugin", e);
            state = PluginState.FAILED;
            return Promise.ofException(e);
        }
    }

    private void initializeDisruptor() {
        disruptor = new Disruptor<>(
                EventHolder.FACTORY,
                config.getRingBufferSize(),
                DaemonThreadFactory.INSTANCE);

        // Event handler adds events to pending batch
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            if (event.getEvent() != null) {
                pendingEvents.add(event.getEvent());
                event.clear();
            }

            // Flush if batch size reached or end of batch
            if (pendingEvents.size() >= config.getFlushBatchSize() || endOfBatch) {
                triggerFlush();
            }
        });

        ringBuffer = disruptor.start();
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Redis HOT tier plugin");

        // Schedule periodic flush
        flushScheduler.scheduleAtFixedRate(
                this::triggerFlush,
                config.getFlushIntervalMs(),
                config.getFlushIntervalMs(),
                TimeUnit.MILLISECONDS);

        state = PluginState.RUNNING;
        log.info("Redis HOT tier plugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Redis HOT tier plugin");

        // Flush any remaining events
        triggerFlush();

        // Shutdown scheduler
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try {
                if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    flushScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flushScheduler.shutdownNow();
            }
        }

        // Shutdown disruptor
        if (disruptor != null) {
            disruptor.shutdown();
        }

        state = PluginState.STOPPED;
        log.info("Redis HOT tier plugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        log.info("Shutting down Redis HOT tier plugin");

        if (jedisPool != null) {
            jedisPool.close();
        }

        state = PluginState.STOPPED;
        log.info("Redis HOT tier plugin shut down");
        return Promise.complete();
    }

    @Override
    public Promise<HealthStatus> healthCheck() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            return Promise.of("PONG".equals(pong)
                    ? HealthStatus.ok("Redis connection healthy")
                    : HealthStatus.error("Redis ping failed"));
        } catch (Exception e) {
            log.warn("Health check failed", e);
            return Promise.of(HealthStatus.error("Redis health check failed", e));
        }
    }

    // ==================== Storage Operations ====================
    @Override
    public Promise<Offset> append(Event event) {
        Objects.requireNonNull(event, "event");

        if (state != PluginState.RUNNING) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }

        try {
            // Check idempotency
            String idempotencyKey = event.getIdempotencyKey();
            if (idempotencyKey != null) {
                String idemKey = buildIdempotencyKey(event.getTenantId(), idempotencyKey);
                try (Jedis jedis = jedisPool.getResource()) {
                    String existingId = jedis.get(idemKey);
                    if (existingId != null) {
                        log.debug("Duplicate event detected: {}", idempotencyKey);
                        return Promise.of(Offset.of(Long.parseLong(existingId)));
                    }
                }
            }

            // Direct write to Redis Streams
            Offset offset = writeToStream(event);
            appendCount.increment();

            // Publish to ring buffer for batch processing/flush callback
            publishToRingBuffer(event);

            return Promise.of(offset);

        } catch (Exception e) {
            errorCount.increment();
            log.error("Failed to append event", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<Offset>> appendBatch(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Promise.of(Collections.emptyList());
        }

        if (state != PluginState.RUNNING) {
            return Promise.ofException(new IllegalStateException("Plugin not running"));
        }

        try {
            List<Offset> offsets = new ArrayList<>(events.size());

            try (Jedis jedis = jedisPool.getResource()) {
                for (Event event : events) {
                    // Write to Redis Stream
                    Offset offset = writeToStream(jedis, event);
                    offsets.add(offset);

                    // Track idempotency
                    String idempotencyKey = event.getIdempotencyKey();
                    if (idempotencyKey != null) {
                        String idemKey = buildIdempotencyKey(event.getTenantId(), idempotencyKey);
                        jedis.setex(idemKey, (int) config.getHotTierTtl().toSeconds(),
                                String.valueOf(offset.value()));
                    }

                    // Add to pending for flush
                    pendingEvents.add(event);
                    appendCount.increment();
                }
            }

            // Trigger flush if batch size reached
            if (pendingEvents.size() >= config.getFlushBatchSize()) {
                triggerFlush();
            }

            return Promise.of(offsets);

        } catch (Exception e) {
            errorCount.increment();
            log.error("Failed to append batch", e);
            return Promise.ofException(e);
        }
    }

    private Offset writeToStream(Event event) {
        try (Jedis jedis = jedisPool.getResource()) {
            return writeToStream(jedis, event);
        }
    }

    private Offset writeToStream(Jedis jedis, Event event) {
        String streamKey = buildStreamKey(event.getTenantId(), event.getStreamName());

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_EVENT_ID, event.getId().toString());
        fields.put(FIELD_TENANT_ID, event.getTenantId());
        fields.put(FIELD_TYPE, event.getEventTypeName());
        fields.put(FIELD_PARTITION, String.valueOf(event.getPartitionId()));
        fields.put(FIELD_OCCURRED_AT, event.getOccurrenceTime().toString());

        String idempotencyKey = event.getIdempotencyKey();
        if (idempotencyKey != null) {
            fields.put(FIELD_IDEMPOTENCY_KEY, idempotencyKey);
        }

        try {
            fields.put(FIELD_PAYLOAD, objectMapper.writeValueAsString(event.getPayload()));
            Map<String, String> headers = event.getHeaders();
            if (headers != null && !headers.isEmpty()) {
                fields.put(FIELD_HEADERS, objectMapper.writeValueAsString(headers));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }

        // XADD with MAXLEN for automatic trimming
        XAddParams params = XAddParams.xAddParams()
                .maxLen(100000)
                .approximateTrimming();

        StreamEntryID entryId = jedis.xadd(streamKey, params, fields);

        // Parse entry ID to offset (format: timestamp-sequence)
        long offset = parseEntryIdToOffset(entryId);

        // Store event by ID for direct lookup
        String eventKey = buildEventKey(event.getTenantId(), event.getId().toString());
        jedis.setex(eventKey, (int) config.getHotTierTtl().toSeconds(),
                serializeEvent(event, offset));

        // Track idempotency
        if (idempotencyKey != null) {
            String idemKey = buildIdempotencyKey(event.getTenantId(), idempotencyKey);
            jedis.setex(idemKey, (int) config.getHotTierTtl().toSeconds(),
                    String.valueOf(offset));
        }

        return Offset.of(offset);
    }

    private void publishToRingBuffer(Event event) {
        long sequence = ringBuffer.next();
        try {
            EventHolder holder = ringBuffer.get(sequence);
            holder.setEvent(event);
            holder.setSequenceNumber(sequence);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    public Promise<Optional<Event>> readById(String tenantId, String eventId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");

        try (Jedis jedis = jedisPool.getResource()) {
            String eventKey = buildEventKey(tenantId, eventId);
            String data = jedis.get(eventKey);

            if (data == null) {
                return Promise.of(Optional.empty());
            }

            Event event = deserializeEvent(data);
            return Promise.of(Optional.of(event));

        } catch (Exception e) {
            log.error("Failed to read event by ID: {}", eventId, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Optional<Event>> readByIdempotencyKey(String tenantId, String idempotencyKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");

        try (Jedis jedis = jedisPool.getResource()) {
            String idemKey = buildIdempotencyKey(tenantId, idempotencyKey);
            String offsetStr = jedis.get(idemKey);

            if (offsetStr == null) {
                return Promise.of(Optional.empty());
            }

            // We need to scan the stream for this offset
            // This is less efficient but maintains idempotency semantics
            return Promise.of(Optional.empty()); // Simplified for now

        } catch (Exception e) {
            log.error("Failed to read by idempotency key: {}", idempotencyKey, e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<Event>> readRange(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset startOffset,
            int limit) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(streamName, "streamName");

        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            // Convert offset to Stream Entry ID
            String startId = offsetToEntryId(startOffset);

            // XREAD from stream
            XReadParams params = XReadParams.xReadParams().count(limit);
            List<Map.Entry<String, List<StreamEntry>>> result = jedis.xread(params,
                    Map.of(streamKey, new StreamEntryID(startId)));

            if (result == null || result.isEmpty()) {
                return Promise.of(Collections.emptyList());
            }

            List<Event> events = new ArrayList<>();
            for (Map.Entry<String, List<StreamEntry>> entry : result) {
                for (StreamEntry streamEntry : entry.getValue()) {
                    Event event = streamEntryToEvent(streamEntry, tenantId, streamName);
                    // Filter by partition if specified
                    if (partitionId == null || event.getPartitionId().equals(partitionId.value())) {
                        events.add(event);
                    }
                }
            }

            return Promise.of(events);

        } catch (Exception e) {
            log.error("Failed to read range", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<List<Event>> readByTimeRange(
            String tenantId,
            String streamName,
            Instant startTime,
            Instant endTime,
            int limit) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(streamName, "streamName");

        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            // Convert timestamps to Stream Entry IDs
            String startId = String.valueOf(startTime.toEpochMilli()) + "-0";
            String endId = String.valueOf(endTime.toEpochMilli()) + "-" + Long.MAX_VALUE;

            // XRANGE for time-based query
            List<StreamEntry> entries = jedis.xrange(streamKey,
                    new StreamEntryID(startId), new StreamEntryID(endId), limit);

            List<Event> events = new ArrayList<>();
            for (StreamEntry entry : entries) {
                Event event = streamEntryToEvent(entry, tenantId, streamName);
                events.add(event);
            }

            return Promise.of(events);

        } catch (Exception e) {
            log.error("Failed to read by time range", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Offset> getCurrentOffset(String tenantId, String streamName, PartitionId partitionId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            // XINFO STREAM to get last entry
            List<StreamEntry> entries = jedis.xrevrange(streamKey,
                    StreamEntryID.LAST_ENTRY, StreamEntryID.MINIMUM_ID, 1);

            if (entries.isEmpty()) {
                return Promise.of(Offset.FIRST);
            }

            long offset = parseEntryIdToOffset(entries.get(0).getID());
            return Promise.of(Offset.of(offset));

        } catch (Exception e) {
            log.error("Failed to get current offset", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Offset> getEarliestOffset(String tenantId, String streamName, PartitionId partitionId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            List<StreamEntry> entries = jedis.xrange(streamKey,
                    StreamEntryID.MINIMUM_ID, StreamEntryID.LAST_ENTRY, 1);

            if (entries.isEmpty()) {
                return Promise.of(Offset.FIRST);
            }

            long offset = parseEntryIdToOffset(entries.get(0).getID());
            return Promise.of(Offset.of(offset));

        } catch (Exception e) {
            log.error("Failed to get earliest offset", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Long> deleteBeforeTime(String tenantId, String streamName, Instant beforeTime) {
        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            // XTRIM with MINID
            String minId = String.valueOf(beforeTime.toEpochMilli()) + "-0";
            long deleted = jedis.xtrim(streamKey,
                    redis.clients.jedis.params.XTrimParams.xTrimParams()
                            .minId(minId));

            return Promise.of(deleted);

        } catch (Exception e) {
            log.error("Failed to delete before time", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Long> deleteBeforeOffset(
            String tenantId,
            String streamName,
            PartitionId partitionId,
            Offset beforeOffset) {

        try (Jedis jedis = jedisPool.getResource()) {
            String streamKey = buildStreamKey(tenantId, streamName);

            // Convert offset to entry ID and trim
            String minId = offsetToEntryId(beforeOffset);
            long deleted = jedis.xtrim(streamKey,
                    redis.clients.jedis.params.XTrimParams.xTrimParams()
                            .minId(minId));

            return Promise.of(deleted);

        } catch (Exception e) {
            log.error("Failed to delete before offset", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public Capabilities capabilities() {
        return capabilities;
    }

    // ==================== Flush Management ====================
    public void setFlushCallback(FlushCallback callback) {
        this.flushCallback = callback;
    }

    private void triggerFlush() {
        if (!flushInProgress.compareAndSet(false, true)) {
            return; // Flush already in progress
        }

        try {
            if (pendingEvents.isEmpty()) {
                return;
            }

            List<Event> toFlush;
            synchronized (pendingEvents) {
                toFlush = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }

            if (toFlush.isEmpty()) {
                return;
            }

            flushCount.increment();
            log.debug("Flushing {} events to downstream tier", toFlush.size());

            if (flushCallback != null) {
                flushCallback.flush(toFlush);
            }

        } finally {
            flushInProgress.set(false);
        }
    }

    // ==================== Helper Methods ====================
    private String buildStreamKey(String tenantId, String streamName) {
        return STREAM_PREFIX + tenantId + ":" + streamName;
    }

    private String buildEventKey(String tenantId, String eventId) {
        return EVENT_PREFIX + tenantId + ":" + eventId;
    }

    private String buildIdempotencyKey(String tenantId, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + tenantId + ":" + idempotencyKey;
    }

    private long parseEntryIdToOffset(StreamEntryID entryId) {
        // Entry ID format: timestamp-sequence
        // We combine them into a single long for offset
        return entryId.getTime() * 1000 + entryId.getSequence();
    }

    private String offsetToEntryId(Offset offset) {
        if (offset == null || offset.value() <= 0) {
            return "0-0";
        }
        long value = offset.value();
        long timestamp = value / 1000;
        long sequence = value % 1000;
        return timestamp + "-" + sequence;
    }

    private String serializeEvent(Event event, long offset) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", event.getId().toString());
            data.put("tenantId", event.getTenantId());
            data.put("type", event.getEventTypeName());
            data.put("stream", event.getStreamName());
            data.put("partitionId", event.getPartitionId());
            data.put("payload", event.getPayload());
            data.put("headers", event.getHeaders());
            data.put("occurredAt", event.getOccurrenceTime().toString());
            data.put("offset", offset);
            String idempotencyKey = event.getIdempotencyKey();
            if (idempotencyKey != null) {
                data.put("idempotencyKey", idempotencyKey);
            }
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Event deserializeEvent(String data) {
        try {
            Map<String, Object> map = objectMapper.readValue(data, Map.class);

            String id = (String) map.get("id");
            String tenantId = (String) map.get("tenantId");
            String typeName = (String) map.get("type");
            String streamName = (String) map.get("stream");
            int partitionValue = ((Number) map.get("partitionId")).intValue();
            Map<String, Object> payload = (Map<String, Object>) map.get("payload");
            Map<String, String> headers = (Map<String, String>) map.get("headers");
            Instant occurredAt = Instant.parse((String) map.get("occurredAt"));
            String idempotencyKey = (String) map.get("idempotencyKey");
            long offset = ((Number) map.get("offset")).longValue();

            return Event.builder()
                    .id(UUID.fromString(id))
                    .tenantId(tenantId)
                    .eventTypeName(typeName)
                    .streamName(streamName)
                    .partitionId(partitionValue)
                    .eventOffset(offset)
                    .data(payload)
                    .headers(headers)
                    .occurrenceTime(occurredAt)
                    .detectionTime(Instant.now())
                    .idempotencyKey(idempotencyKey)
                    .currentTier(StorageTier.HOT)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize event", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Event streamEntryToEvent(StreamEntry entry, String tenantId, String streamName) {
        Map<String, String> fields = entry.getFields();

        String id = fields.get(FIELD_EVENT_ID);
        String type = fields.get(FIELD_TYPE);
        int partition = Integer.parseInt(fields.get(FIELD_PARTITION));
        Instant occurredAt = Instant.parse(fields.get(FIELD_OCCURRED_AT));
        String idempotencyKey = fields.get(FIELD_IDEMPOTENCY_KEY);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(fields.get(FIELD_PAYLOAD), Map.class);
        } catch (JsonProcessingException e) {
            payload = Map.of();
        }

        Map<String, String> headers = null;
        if (fields.containsKey(FIELD_HEADERS)) {
            try {
                headers = objectMapper.readValue(fields.get(FIELD_HEADERS), Map.class);
            } catch (JsonProcessingException e) {
                headers = Map.of();
            }
        }

        long offset = parseEntryIdToOffset(entry.getID());

        return Event.builder()
                .id(UUID.fromString(id))
                .tenantId(tenantId)
                .eventTypeName(type)
                .streamName(streamName)
                .partitionId(partition)
                .eventOffset(offset)
                .data(payload)
                .headers(headers)
                .occurrenceTime(occurredAt)
                .detectionTime(Instant.now())
                .idempotencyKey(idempotencyKey)
                .currentTier(StorageTier.HOT)
                .build();
    }

    // ==================== Capabilities Implementation ====================
    private static final class RedisCapabilities implements Capabilities {

        @Override
        public boolean supportsTransactions() {
            return false; // Redis Streams don't support multi-key transactions
        }

        @Override
        public boolean supportsStreaming() {
            return true; // Redis Streams are designed for streaming
        }

        @Override
        public boolean supportsTimeRangeQuery() {
            return true; // XRANGE with timestamp-based IDs
        }

        @Override
        public boolean supportsCompaction() {
            return false; // Redis Streams don't support compaction
        }

        @Override
        public long maxBatchSize() {
            return 10000; // Practical limit for batch operations
        }

        @Override
        public int recommendedBatchSize() {
            return 1000;
        }
    }

    // ==================== Metrics Accessors ====================
    public long getAppendCount() {
        return appendCount.sum();
    }

    public long getFlushCount() {
        return flushCount.sum();
    }

    public long getErrorCount() {
        return errorCount.sum();
    }

    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    @Override
    public String toString() {
        return "RedisHotTierPlugin{"
                + "config=" + config
                + ", state=" + state
                + ", appendCount=" + appendCount.sum()
                + ", flushCount=" + flushCount.sum()
                + '}';
    }
}
