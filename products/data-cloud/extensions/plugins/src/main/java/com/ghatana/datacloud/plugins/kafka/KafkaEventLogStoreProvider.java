package com.ghatana.datacloud.plugins.kafka;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @doc.type class
 * @doc.purpose Lazy ServiceLoader-facing Kafka EventLogStore provider that avoids broker connections during discovery.
 * @doc.layer product
 * @doc.pattern Provider, Lazy Initialization
 */
public final class KafkaEventLogStoreProvider implements EventLogStore, AutoCloseable {

    private static final Executor INIT_EXECUTOR = Thread::startVirtualThread;

    private final AtomicReference<KafkaEventLogStore> delegate = new AtomicReference<>();

    @Override
    public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
        return resolveDelegate().then(store -> store.append(tenant, entry));
    }

    @Override
    public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
        return resolveDelegate().then(store -> store.appendBatch(tenant, entries));
    }

    @Override
    public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
        return resolveDelegate().then(store -> store.read(tenant, from, limit));
    }

    @Override
    public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
        return resolveDelegate().then(store -> store.readByTimeRange(tenant, startTime, endTime, limit));
    }

    @Override
    public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
        return resolveDelegate().then(store -> store.readByType(tenant, eventType, from, limit));
    }

    @Override
    public Promise<Offset> getLatestOffset(TenantContext tenant) {
        return resolveDelegate().then(store -> store.getLatestOffset(tenant));
    }

    @Override
    public Promise<Offset> getEarliestOffset(TenantContext tenant) {
        return resolveDelegate().then(store -> store.getEarliestOffset(tenant));
    }

    @Override
    public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
        return resolveDelegate().then(store -> store.tail(tenant, from, handler));
    }

    @Override
    public void close() {
        KafkaEventLogStore current = delegate.get();
        if (current != null) {
            current.close();
        }
    }

    private KafkaEventLogStore delegate() {
        KafkaEventLogStore current = delegate.get();
        if (current != null) {
            return current;
        }

        KafkaEventLogStore created = new KafkaEventLogStore(buildConfigFromEnvironment());
        if (delegate.compareAndSet(null, created)) {
            return created;
        }

        created.close();
        return delegate.get();
    }

    private Promise<KafkaEventLogStore> resolveDelegate() {
        KafkaEventLogStore current = delegate.get();
        if (current != null) {
            return Promise.of(current);
        }
        // Offload first-time Kafka store creation because initTransactions() blocks.
        return Promise.ofBlocking(INIT_EXECUTOR, this::delegate);
    }

    private static KafkaEventLogStoreConfig buildConfigFromEnvironment() {
        KafkaEventLogStoreConfig defaults = KafkaEventLogStoreConfig.defaults();

        String bootstrapServers = System.getProperty(
            "datacloud.kafka.bootstrapServers",
            System.getenv().getOrDefault("DATACLOUD_KAFKA_BOOTSTRAP_SERVERS", defaults.bootstrapServers())
        );

        int partitions = Integer.getInteger(
            "datacloud.kafka.partitions",
            parseIntegerEnv("DATACLOUD_KAFKA_PARTITIONS", defaults.partitions())
        );
        short replicationFactor = (short) Integer.getInteger(
            "datacloud.kafka.replicationFactor",
            parseIntegerEnv("DATACLOUD_KAFKA_REPLICATION_FACTOR", defaults.replicationFactor())
        ).intValue();
        long readTimeoutMs = Long.getLong(
            "datacloud.kafka.readTimeoutMs",
            parseLongEnv("DATACLOUD_KAFKA_READ_TIMEOUT_MS", defaults.readTimeoutMs())
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
}