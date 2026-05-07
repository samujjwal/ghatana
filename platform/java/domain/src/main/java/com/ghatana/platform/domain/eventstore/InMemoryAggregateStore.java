package com.ghatana.platform.domain.eventstore;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;

/**
 * In-memory implementation of {@link AggregateStore} for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory aggregate store for testing (KERNEL-P1)
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryAggregateStore implements AggregateStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryAggregateStore.class);

    private final Map<String, ConcurrentSkipListMap<Long, AggregateEvent>> aggregates = new ConcurrentHashMap<>();
    private final Map<String, Long> aggregateVersions = new ConcurrentHashMap<>();
    private final Executor executor;

    public InMemoryAggregateStore(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<Long> appendEvent(
        TenantContext tenant,
        String aggregateId,
        String eventType,
        String eventVersion,
        byte[] payload,
        long expectedVersion
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(eventVersion, "eventVersion must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            ConcurrentSkipListMap<Long, AggregateEvent> events = aggregates.computeIfAbsent(key, k -> new ConcurrentSkipListMap<>());
            Long currentVersion = aggregateVersions.getOrDefault(key, 0L);

            if (expectedVersion >= 0 && !currentVersion.equals(expectedVersion)) {
                throw new IllegalStateException(
                    String.format("Optimistic concurrency violation: expected version %d, actual version %d",
                        expectedVersion, currentVersion)
                );
            }

            long newVersion = currentVersion + 1;
            AggregateEvent event = AggregateEvent.of(
                aggregateId,
                newVersion,
                eventType,
                eventVersion,
                payload
            );

            events.put(newVersion, event);
            aggregateVersions.put(key, newVersion);

            LOG.info("[AGGREGATE-STORE] Appended event tenant={} aggregateId={} version={} eventType={}",
                tenant.tenantId(), aggregateId, newVersion, eventType);

            return newVersion;
        });
    }

    @Override
    public Promise<List<AggregateEvent>> readAggregateEvents(
        TenantContext tenant,
        String aggregateId
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            ConcurrentSkipListMap<Long, AggregateEvent> events = aggregates.get(key);
            if (events == null) {
                return List.of();
            }
            return new ArrayList<>(events.values());
        });
    }

    @Override
    public Promise<List<AggregateEvent>> readAggregateEventsFromVersion(
        TenantContext tenant,
        String aggregateId,
        long fromVersion
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            ConcurrentSkipListMap<Long, AggregateEvent> events = aggregates.get(key);
            if (events == null) {
                return List.of();
            }
            return new ArrayList<>(events.tailMap(fromVersion).values());
        });
    }

    @Override
    public Promise<Optional<Long>> getAggregateVersion(
        TenantContext tenant,
        String aggregateId
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            return Optional.ofNullable(aggregateVersions.get(key));
        });
    }

    @Override
    public Promise<Boolean> aggregateExists(
        TenantContext tenant,
        String aggregateId
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            return aggregateVersions.containsKey(key);
        });
    }

    @Override
    public Promise<Void> deleteAggregate(
        TenantContext tenant,
        String aggregateId
    ) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String key = tenant.tenantId() + ":" + aggregateId;
            aggregates.remove(key);
            aggregateVersions.remove(key);
            LOG.info("[AGGREGATE-STORE] Deleted aggregate tenant={} aggregateId={}",
                tenant.tenantId(), aggregateId);
            return null;
        });
    }
}
