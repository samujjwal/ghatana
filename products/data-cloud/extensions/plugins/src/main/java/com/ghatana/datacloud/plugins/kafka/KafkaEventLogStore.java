/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.kafka.common.TopicPartition;

/**
 * Kafka-backed implementation of the {@link EventLogStore} SPI.
 *
 * <p>Maps all {@code EventLogStore} operations onto Apache Kafka topics using a
 * per-tenant topic scheme: {@code datacloud.{tenantId}.events}.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Exactly-once semantics</b>: Transactional producers with isolation
 *       level {@code read_committed} on consumers.</li>
 *   <li><b>EventLoop safety</b>: All blocking Kafka calls are executed on a
 *       virtual-thread executor via {@code Promise.ofBlocking}, keeping the
 *       ActiveJ event loop free.</li>
 *   <li><b>Offset encoding</b>: Kafka {@code partition=0, offset} is encoded as
 *       the numeric string value of the {@link Offset} type so it rounds-trips
 *       through the platform type system cleanly.</li>
 *   <li><b>Multi-tenancy</b>: Every operation is scoped to its tenant; topics
 *       are auto-created on first use with configurable partition count.</li>
 * </ul>
 *
 * <h2>Header protocol</h2>
 * Each Kafka record carries the following headers:
 * <pre>
 *   event-id        - UUID (string)
 *   event-type      - string
 *   event-version   - string
 *   content-type    - string  (default: application/json)
 *   idempotency-key - string  (optional)
 *   timestamp       - ISO-8601 Instant
 *   tenant-id       - string
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Kafka-backed EventLogStore SPI implementation with exactly-once semantics
 * @doc.layer product
 * @doc.pattern EventStore, Adapter, Plugin
 */
public class KafkaEventLogStore implements EventLogStore {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventLogStore.class);

    /** Topic prefix — tenant id is appended after this. */
    private static final String TOPIC_PREFIX = "datacloud.";
    private static final String TOPIC_SUFFIX = ".events";

    private static final String HDR_EVENT_ID = "event-id";
    private static final String HDR_EVENT_TYPE = "event-type";
    private static final String HDR_EVENT_VERSION = "event-version";
    private static final String HDR_CONTENT_TYPE = "content-type";
    private static final String HDR_IDEMPOTENCY_KEY = "idempotency-key";
    private static final String HDR_TIMESTAMP = "timestamp";
    private static final String HDR_TENANT_ID = "tenant-id";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    // -------------------------------------------------------------------------
    //  Dependencies
    // -------------------------------------------------------------------------

    private final KafkaEventLogStoreConfig config;
    private final Executor blockingExecutor;
    private final MeterRegistry meterRegistry;

    // Shared, thread-safe producer (transactional)
    private final KafkaProducer<String, byte[]> producer;
    private final AdminClient adminClient;
    private final Object producerTransactionLock = new Object();

    // Track topics already created so we avoid repeated describe calls
    private final ConcurrentHashMap<String, Boolean> createdTopics = new ConcurrentHashMap<>();

    // Active tail subscriptions
    private final ConcurrentHashMap<String, TailSubscription> tailSubscriptions = new ConcurrentHashMap<>();

    // Tail runtime telemetry
    private final AtomicLong totalTailSubscriptions = new AtomicLong();
    private final AtomicLong totalTailPolls = new AtomicLong();
    private final AtomicLong totalTailErrors = new AtomicLong();
    private final AtomicLong totalTailEventsDispatched = new AtomicLong();

    // Executor for tail polling threads
    private final ExecutorService tailExecutor;

    // -------------------------------------------------------------------------
    //  Metrics
    // -------------------------------------------------------------------------

    private final Counter appendCounter;
    private final Counter appendErrorCounter;
    private final Counter readCounter;
    private final Timer appendTimer;
    private final Timer readTimer;

    // =========================================================================
    //  Constructors
    // =========================================================================

    /**
     * Creates a store with default in-memory metrics registry (for testing).
     *
     * @param config Kafka and store configuration
     */
    public KafkaEventLogStore() {
        this(defaultConfigFromEnvironment());
    }

    public KafkaEventLogStore(KafkaEventLogStoreConfig config) {
        this(config, new SimpleMeterRegistry(), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a store with full dependency injection.
     *
     * @param config           Kafka and store configuration
     * @param meterRegistry    Micrometer registry for metrics
     * @param blockingExecutor executor for all blocking Kafka calls
     */
    public KafkaEventLogStore(
            KafkaEventLogStoreConfig config,
            MeterRegistry meterRegistry,
            Executor blockingExecutor) {
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.blockingExecutor = blockingExecutor;
        this.tailExecutor = Executors.newVirtualThreadPerTaskExecutor();

        this.producer = buildProducer(config);
        this.adminClient = buildAdminClient(config);

        this.appendCounter = meterRegistry.counter("kafka.eventlog.append.count");
        this.appendErrorCounter = meterRegistry.counter("kafka.eventlog.append.error");
        this.readCounter = meterRegistry.counter("kafka.eventlog.read.count");
        this.appendTimer = meterRegistry.timer("kafka.eventlog.append.duration");
        this.readTimer = meterRegistry.timer("kafka.eventlog.read.duration");
    }

    // =========================================================================
    //  Append
    // =========================================================================

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        return Promise.ofBlocking(blockingExecutor, () -> doAppend(tenant, entry));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        if (entries.isEmpty()) return Promise.of(List.of());
        return Promise.ofBlocking(blockingExecutor, () -> doAppendBatchSync(tenant, entries));
    }

    private List<Offset> doAppendBatchSync(TenantContext tenant, List<EventEntry> entries) {
        String topic = topicFor(tenant.tenantId());
        ensureTopicExists(topic);

        synchronized (producerTransactionLock) {
            producer.beginTransaction();
            try {
                List<Future<RecordMetadata>> futures = new ArrayList<>(entries.size());
                for (EventEntry entry : entries) {
                    ProducerRecord<String, byte[]> record = toProducerRecord(topic, tenant.tenantId(), entry);
                    futures.add(producer.send(record));
                }
                producer.commitTransaction();

                List<Offset> offsets = new ArrayList<>(futures.size());
                for (Future<RecordMetadata> f : futures) {
                    RecordMetadata meta = f.get();
                    offsets.add(Offset.of(meta.offset()));
                    appendCounter.increment();
                }
                return offsets;
            } catch (Exception e) {
                producer.abortTransaction();
                appendErrorCounter.increment();
                log.error("appendBatch failed for tenant={}", tenant.tenantId(), e);
                throw new RuntimeException("Kafka appendBatch failed", e);
            }
        }
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        long fromOffset = parseLong(from);
        return Promise.ofBlocking(blockingExecutor,
                () -> readFromKafka(tenant.tenantId(), fromOffset, limit, null, null, null));
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        return Promise.ofBlocking(blockingExecutor,
                () -> readFromKafka(tenant.tenantId(), 0L, limit, startTime, endTime, null));
    }

    @Override
    public Promise<List<EventEntry>> readByType(
            TenantContext tenant, String eventType, Offset from, int limit) {
        long fromOffset = parseLong(from);
        return Promise.ofBlocking(blockingExecutor,
                () -> readFromKafka(tenant.tenantId(), fromOffset, limit, null, null, eventType));
    }

    // =========================================================================
    //  Offset Management
    // =========================================================================

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            String topic = topicFor(tenant.tenantId());
            ensureTopicExists(topic);
            try (KafkaConsumer<String, byte[]> consumer = buildConsumer(config, "offset-query-" + UUID.randomUUID())) {
                TopicPartition tp = new TopicPartition(topic, 0);
                consumer.assign(Collections.singletonList(tp));
                // Use an explicit timeout to avoid blocking indefinitely if broker is down
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(
                        Collections.singletonList(tp), Duration.ofSeconds(10));
                Long end = endOffsets.get(tp);
                return end != null && end > 0 ? Offset.of(end - 1) : Offset.zero();
            }
        });
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            String topic = topicFor(tenant.tenantId());
            ensureTopicExists(topic);
            try (KafkaConsumer<String, byte[]> consumer = buildConsumer(config, "offset-query-" + UUID.randomUUID())) {
                TopicPartition tp = new TopicPartition(topic, 0);
                consumer.assign(Collections.singletonList(tp));
                Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(
                        Collections.singletonList(tp), Duration.ofSeconds(10));
                Long begin = beginOffsets.get(tp);
                return begin != null ? Offset.of(begin) : Offset.zero();
            }
        });
    }

    // =========================================================================
    //  Tail (real-time subscription)
    // =========================================================================

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        String subId = UUID.randomUUID().toString();
        TailSubscription subscription = new TailSubscription(subId);
        tailSubscriptions.put(subId, subscription);
        totalTailSubscriptions.incrementAndGet();

        long startOffset = parseLong(from);
        String topic = topicFor(tenant.tenantId());

        tailExecutor.submit(() -> {
            try {
                ensureTopicExists(topic);
            } catch (Exception e) {
                log.error("Failed to ensure topic exists for tail: tenant={}", tenant.tenantId(), e);
                subscription.cancel();
                return;
            }

            String groupId = "tail-" + tenant.tenantId() + "-" + subId;
            try (KafkaConsumer<String, byte[]> consumer = buildConsumer(config, groupId)) {
                TopicPartition tp = new TopicPartition(topic, 0);
                consumer.assign(Collections.singletonList(tp));

                // Offset.latest() is encoded as -1. Resolve to end-of-log so only future events are tailed.
                if (startOffset < 0) {
                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(
                        Collections.singletonList(tp), Duration.ofSeconds(10));
                    long latestExclusive = endOffsets.getOrDefault(tp, 0L);
                    consumer.seek(tp, latestExclusive);
                } else {
                    consumer.seek(tp, Math.max(0, startOffset));
                }

                while (!subscription.isCancelled()) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
                    totalTailPolls.incrementAndGet();
                    for (ConsumerRecord<String, byte[]> record : records) {
                        if (subscription.isCancelled()) break;
                        try {
                            EventEntry entry = fromConsumerRecord(record);
                            handler.accept(entry);
                            totalTailEventsDispatched.incrementAndGet();
                        } catch (Exception e) {
                            totalTailErrors.incrementAndGet();
                            log.warn("Error dispatching tail event at offset={}", record.offset(), e);
                        }
                    }
                }
            } catch (WakeupException ignored) {
                // normal cancellation
            } catch (Exception e) {
                totalTailErrors.incrementAndGet();
                log.error("Tail subscription error for tenant={}", tenant.tenantId(), e);
            } finally {
                tailSubscriptions.remove(subId);
            }
        });

        return Promise.of(subscription);
    }

    /**
     * Close all resources (producer, admin client, tail executor).
     * Call when the store is no longer needed.
     */
    public void close() {
        tailSubscriptions.values().forEach(TailSubscription::cancel);
        tailExecutor.shutdownNow();
        try {
            tailExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        producer.close(Duration.ofSeconds(5));
        adminClient.close(Duration.ofSeconds(5));
    }

    /**
     * Runtime telemetry snapshot consumed by Runtime Truth posture reflection.
     */
    public Map<String, Object> tailRuntimeSnapshot() {
        return Map.of(
            "available", true,
            "configurable", false,
            "storeType", getClass().getSimpleName(),
            "mode", "polling",
            "activeSubscribers", tailSubscriptions.size(),
            "totalSubscriptions", totalTailSubscriptions.get(),
            "totalPolls", totalTailPolls.get(),
            "pollErrors", totalTailErrors.get(),
            "eventsDispatched", totalTailEventsDispatched.get());
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private Offset doAppend(TenantContext tenant, EventEntry entry) {
        String topic = topicFor(tenant.tenantId());
        ensureTopicExists(topic);
        return appendTimer.record(() -> {
            ProducerRecord<String, byte[]> record = toProducerRecord(topic, tenant.tenantId(), entry);
            synchronized (producerTransactionLock) {
                producer.beginTransaction();
                try {
                    RecordMetadata meta = producer.send(record).get();
                    producer.commitTransaction();
                    appendCounter.increment();
                    return Offset.of(meta.offset());
                } catch (Exception e) {
                    producer.abortTransaction();
                    appendErrorCounter.increment();
                    log.error("append failed for tenant={} eventType={}", tenant.tenantId(), entry.eventType(), e);
                    throw new RuntimeException("Kafka append failed", e);
                }
            }
        });
    }

    private static KafkaEventLogStoreConfig defaultConfigFromEnvironment() {
        String bootstrapServers = System.getProperty(
            "datacloud.kafka.bootstrapServers",
            System.getenv().getOrDefault("DATACLOUD_KAFKA_BOOTSTRAP_SERVERS", KafkaEventLogStoreConfig.defaults().bootstrapServers())
        );

        int partitions = Integer.getInteger(
            "datacloud.kafka.partitions",
            parseIntegerEnv("DATACLOUD_KAFKA_PARTITIONS", KafkaEventLogStoreConfig.defaults().partitions())
        );
        short replicationFactor = (short) Integer.getInteger(
            "datacloud.kafka.replicationFactor",
            parseIntegerEnv("DATACLOUD_KAFKA_REPLICATION_FACTOR", KafkaEventLogStoreConfig.defaults().replicationFactor())
        ).intValue();
        long readTimeoutMs = Long.getLong(
            "datacloud.kafka.readTimeoutMs",
            parseLongEnv("DATACLOUD_KAFKA_READ_TIMEOUT_MS", KafkaEventLogStoreConfig.defaults().readTimeoutMs())
        );

        return KafkaEventLogStoreConfig.builder()
            .bootstrapServers(bootstrapServers)
            .partitions(partitions)
            .replicationFactor(replicationFactor)
            .readTimeoutMs(readTimeoutMs)
            .build();
    }

    private static int parseIntegerEnv(String key, int defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(raw.trim());
    }

    private static long parseLongEnv(String key, long defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(raw.trim());
    }

    private List<EventEntry> readFromKafka(
            String tenantId,
            long fromOffset,
            int limit,
            Instant startTime,
            Instant endTime,
            String filterType) {

        String topic = topicFor(tenantId);
        ensureTopicExists(topic);
        readCounter.increment();

        String groupId = "read-" + tenantId + "-" + UUID.randomUUID();
        try (KafkaConsumer<String, byte[]> consumer = buildConsumer(config, groupId)) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(Collections.singletonList(tp));
            consumer.seek(tp, Math.max(0, fromOffset));

            List<EventEntry> results = new ArrayList<>();
            long deadline = System.currentTimeMillis() + config.readTimeoutMs();
            // After assign()+seek(), the very first poll() may return empty while the
            // consumer initialises the partition metadata fetch with the broker.  We
            // only stop polling once we have seen at least one batch of records and
            // the next poll returns empty — i.e. we are genuinely at the end of the log.
            boolean receivedAtLeastOneBatch = false;

            while (results.size() < limit && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
                if (records.isEmpty()) {
                    if (receivedAtLeastOneBatch) {
                        // Confirmed end-of-log: a prior batch was consumed and the
                        // next poll returned nothing — stop reading.
                        break;
                    }
                    // Still waiting for the initial metadata fetch — continue.
                    continue;
                }
                receivedAtLeastOneBatch = true;
                for (ConsumerRecord<String, byte[]> record : records) {
                    if (results.size() >= limit) break;
                    try {
                        EventEntry entry = fromConsumerRecord(record);

                        // Apply time range filter if requested
                        if (startTime != null && entry.timestamp().isBefore(startTime)) continue;
                        if (endTime != null && !entry.timestamp().isBefore(endTime)) continue;

                        // Apply event type filter if requested
                        if (filterType != null && !filterType.equals(entry.eventType())) continue;

                        results.add(entry);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize record at offset={}", record.offset(), e);
                    }
                }
            }
            return results;
        }
    }

    private void ensureTopicExists(String topic) {
        createdTopics.computeIfAbsent(topic, t -> {
            try {
                NewTopic newTopic = new NewTopic(t, config.partitions(), config.replicationFactor());
                adminClient.createTopics(Collections.singletonList(newTopic)).all().get(10, TimeUnit.SECONDS);
                log.info("Created Kafka topic: {}", t);
            } catch (Exception e) {
                // Topic may already exist — that's fine
                log.debug("Topic {} already exists or creation failed (benign): {}", t, e.getMessage());
            }
            return true;
        });
    }

    private static String topicFor(String tenantId) {
        return TOPIC_PREFIX + tenantId.replaceAll("[^a-zA-Z0-9._-]", "_") + TOPIC_SUFFIX;
    }

    private static ProducerRecord<String, byte[]> toProducerRecord(
            String topic, String tenantId, EventEntry entry) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(HDR_EVENT_ID, entry.eventId().toString().getBytes(StandardCharsets.UTF_8));
        headers.add(HDR_EVENT_TYPE, entry.eventType().getBytes(StandardCharsets.UTF_8));
        headers.add(HDR_EVENT_VERSION, entry.eventVersion().getBytes(StandardCharsets.UTF_8));
        headers.add(HDR_CONTENT_TYPE, entry.contentType().getBytes(StandardCharsets.UTF_8));
        headers.add(HDR_TIMESTAMP, entry.timestamp().toString().getBytes(StandardCharsets.UTF_8));
        headers.add(HDR_TENANT_ID, tenantId.getBytes(StandardCharsets.UTF_8));
        entry.idempotencyKey().ifPresent(k ->
                headers.add(HDR_IDEMPOTENCY_KEY, k.getBytes(StandardCharsets.UTF_8)));

        // Persist per-entry user headers
        entry.headers().forEach((k, v) ->
                headers.add(k, v.getBytes(StandardCharsets.UTF_8)));

        byte[] value = entry.payload().hasArray()
                ? entry.payload().array()
                : toByteArray(entry.payload());

        return new ProducerRecord<>(topic, null, entry.timestamp().toEpochMilli(),
                entry.eventId().toString(), value, headers);
    }

    private static EventEntry fromConsumerRecord(ConsumerRecord<String, byte[]> record) {
        Headers hdrs = record.headers();
        UUID eventId = UUID.fromString(headerValue(hdrs, HDR_EVENT_ID));
        String eventType = headerValue(hdrs, HDR_EVENT_TYPE);
        String eventVersion = headerValueOrDefault(hdrs, HDR_EVENT_VERSION, "1.0.0");
        String contentType = headerValueOrDefault(hdrs, HDR_CONTENT_TYPE, "application/json");
        Instant timestamp = Instant.parse(headerValueOrDefault(hdrs, HDR_TIMESTAMP,
                Instant.ofEpochMilli(record.timestamp()).toString()));
        Optional<String> idempotencyKey = Optional.ofNullable(headerValueNullable(hdrs, HDR_IDEMPOTENCY_KEY));

        // Reconstruct user headers (exclude well-known ones)
        Map<String, String> userHeaders = new HashMap<>();
        record.headers().forEach(h -> {
            if (!isReservedHeader(h.key())) {
                userHeaders.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
            }
        });
        // Embed the Kafka offset so consumers can reliably advance their read position
        userHeaders.put("_x_dc_offset", String.valueOf(record.offset()));

        return EventEntry.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(eventVersion)
                .timestamp(timestamp)
                .payload(record.value())
                .contentType(contentType)
                .headers(userHeaders)
                .idempotencyKey(idempotencyKey.orElse(null))
                .build();
    }

    private static String headerValue(Headers headers, String key) {
        var header = headers.lastHeader(key);
        if (header == null) throw new IllegalStateException("Missing required Kafka header: " + key);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String headerValueOrDefault(Headers headers, String key, String defaultValue) {
        var header = headers.lastHeader(key);
        return header == null ? defaultValue : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String headerValueNullable(Headers headers, String key) {
        var header = headers.lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static boolean isReservedHeader(String key) {
        return key.equals(HDR_EVENT_ID) || key.equals(HDR_EVENT_TYPE) || key.equals(HDR_EVENT_VERSION)
                || key.equals(HDR_CONTENT_TYPE) || key.equals(HDR_IDEMPOTENCY_KEY)
                || key.equals(HDR_TIMESTAMP) || key.equals(HDR_TENANT_ID);
    }

    private static long parseLong(Offset offset) {
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    // ==================== Checkpoint Management (P3-03) ====================

    @Override
    public Promise<Optional<Checkpoint>> readCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        // P3-03: For Kafka, checkpoints are managed via consumer group offsets
        // This is a simplified implementation that returns empty for now
        // Production implementation would use Kafka consumer group offset management
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<Checkpoint> commitCheckpoint(TenantContext tenant, String stream, String consumerGroup, Offset offset, String idempotencyKey) {
        // P3-03: For Kafka, checkpoint commits are handled via consumer group offset commits
        // This is a simplified implementation that returns a checkpoint for now
        // Production implementation would use Kafka consumer group offset commits
        Checkpoint checkpoint = new Checkpoint(stream, consumerGroup, offset, Instant.now(), idempotencyKey);
        return Promise.of(checkpoint);
    }

    @Override
    public Promise<Boolean> deleteCheckpoint(TenantContext tenant, String stream, String consumerGroup) {
        // P3-03: For Kafka, checkpoint deletion is handled via consumer group offset resets
        // This is a simplified implementation that returns false for now
        // Production implementation would use Kafka consumer group offset resets
        return Promise.of(false);
    }

    @Override
    public Promise<Map<String, Checkpoint>> getAllCheckpointsWithMetadata(TenantContext tenant) {
        // P3-03: For Kafka, checkpoint metadata is available via consumer group offset metadata
        // This is a simplified implementation that returns empty for now
        // Production implementation would query Kafka consumer group offset metadata
        return Promise.of(Map.of());
    }

    @Override
    public Promise<List<EventEntry>> replay(TenantContext tenant, ReplaySpec spec) {
        // P3-03: For Kafka, replay is implemented by creating a temporary consumer
        // that reads from the specified offset range
        return Promise.ofBlocking(blockingExecutor, () -> {
            long fromOffsetValue = parseLong(spec.fromOffset());
            long toOffsetValue = spec.toOffset().value().equals("-1") ? Long.MAX_VALUE : parseLong(spec.toOffset());
            
            String topic = topicFor(tenant.tenantId());
            try (KafkaConsumer<String, byte[]> consumer = buildConsumer(config, "replay-" + UUID.randomUUID())) {
                TopicPartition partition = new TopicPartition(topic, 0);
                consumer.assign(List.of(partition));
                consumer.seek(partition, fromOffsetValue);
                
                List<EventEntry> entries = new ArrayList<>();
                while (entries.size() < 1000) { // Limit to prevent unbounded reads
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                    if (records.isEmpty()) break;
                    
                    for (ConsumerRecord<String, byte[]> record : records) {
                        if (record.offset() > toOffsetValue) break;
                        if (spec.eventTypes().isEmpty() || spec.eventTypes().contains(headerValue(record.headers(), HDR_EVENT_TYPE))) {
                            entries.add(fromConsumerRecord(record));
                        }
                    }
                }
                return entries;
            }
        });
    }

    @Override
    public Promise<Void> unsubscribe(TenantContext tenant, SubscriptionId subscriptionId) {
        // P3-03: For Kafka, unsubscribe is handled by the consumer's wakeup() method
        // This is a simplified implementation that completes immediately
        // Production implementation would track subscriptions and call wakeup()
        return Promise.of(null);
    }

    // =========================================================================
    //  Kafka client factories
    // =========================================================================

    private static KafkaProducer<String, byte[]> buildProducer(KafkaEventLogStoreConfig config) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArraySerializer");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "datacloud-eventlog-" + UUID.randomUUID());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, "3");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
        props.putAll(config.additionalProducerProps());

        KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props);
        producer.initTransactions();
        return producer;
    }

    private static KafkaConsumer<String, byte[]> buildConsumer(KafkaEventLogStoreConfig config, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.putAll(config.additionalConsumerProps());
        return new KafkaConsumer<>(props);
    }

    private static AdminClient buildAdminClient(KafkaEventLogStoreConfig config) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.bootstrapServers());
        return AdminClient.create(props);
    }

    // =========================================================================
    //  Inner types
    // =========================================================================

    /**
     * Cancellable tail subscription that drives a Kafka polling loop.
     */
    private static final class TailSubscription implements Subscription {

        private final String id;
        private volatile boolean cancelled = false;

        TailSubscription(String id) {
            this.id = id;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
