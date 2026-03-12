package com.ghatana.core.event.cloud;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * In-memory implementation of EventCloud for testing, development, and embedded deployments.
 *
 * <p><b>Purpose</b><br>
 * Thread-safe, in-memory event log using ConcurrentHashMap for partition storage. Ideal for unit tests,
 * integration tests, development environments, and embedded applications. Does NOT persist events across
 * JVM restarts. For production multi-instance deployments, use PostgresEventCloudAdapter.
 *
 * <p><b>Architecture Role</b><br>
 * Test/development implementation of EventCloud interface. Used by unit tests for pipeline validation,
 * integration tests for end-to-end flows, and local development without PostgreSQL dependency. Not suitable
 * for production (no persistence, no multi-instance coordination, limited scalability).
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Basic test usage
 * @Test
 * void shouldAppendAndRetrieveEvent() {
 *     // GIVEN: In-memory EventCloud
 *     EventCloud cloud = new InMemoryEventCloud();
 *     
 *     EventRecord event = TestDataBuilders.eventRecord()
 *         .tenantId(TenantId.of("test-tenant"))
 *         .typeRef(EventTypeRef.of("test.event"))
 *         .build();
 *     
 *     // WHEN: Append event
 *     AppendResult result = cloud.append(
 *         new AppendRequest(event, AppendOptions.defaults()))
 *         .getResult();
 *     
 *     // THEN: Event retrievable
 *     assertThat(result.partitionId()).isNotNull();
 *     assertThat(result.offset()).isEqualTo(Offset.of(0));
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Testing real-time tail subscription
 * @Test
 * void shouldReceiveEventsViaTail() {
 *     EventCloud cloud = new InMemoryEventCloud();
 *     List<EventRecord> received = new ArrayList<>();
 *     
 *     // Subscribe to tail
 *     TailRequest tail = TailRequest.builder()
 *         .tenantId(TenantId.of("tenant-123"))
 *         .fromOffset(Offset.LATEST)
 *         .build();
 *     
 *     cloud.tail(tail, received::add).getResult();
 *     
 *     // Append event
 *     cloud.append(new AppendRequest(event, AppendOptions.defaults()))
 *         .getResult();
 *     
 *     // Verify received
 *     await().atMost(1, SECONDS).until(() -> !received.isEmpty());
 *     assertThat(received).hasSize(1);
 * }
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Testing idempotency
 * @Test
 * void shouldReturnSameResultForDuplicateIdempotencyKey() {
 *     EventCloud cloud = new InMemoryEventCloud();
 *     
 *     IdempotencyKey key = IdempotencyKey.of("unique-key-123");
 *     EventRecord event = TestDataBuilders.eventRecord()
 *         .idempotencyKey(Optional.of(key))
 *         .build();
 *     
 *     AppendRequest request = new AppendRequest(event, AppendOptions.defaults());
 *     
 *     // First append
 *     AppendResult result1 = cloud.append(request).getResult();
 *     
 *     // Duplicate append (same idempotencyKey)
 *     AppendResult result2 = cloud.append(request).getResult();
 *     
 *     // Same partition/offset
 *     assertThat(result2).isEqualTo(result1);
 * }
 * }</pre>
 *
 * <p><b>Limitations</b><br>
 * - ❌ No persistence (data lost on JVM restart)
 * - ❌ No multi-instance coordination (single-node only)
 * - ❌ Limited scalability (heap-bound, no disk overflow)
 * - ❌ No TTL/compaction (events accumulate in heap)
 * - ✅ Fast in-memory operations (O(1) append, O(n) scan)
 * - ✅ Thread-safe (ConcurrentHashMap + CopyOnWriteArrayList)
 * - ✅ No external dependencies (pure Java)
 *
 * <p><b>Best Practices</b><br>
 * - Use for unit/integration tests only (not production)
 * - Clear between tests to avoid cross-test pollution
 * - Monitor heap usage for large test suites (events not GC'd)
 * - Cancel subscriptions after test to avoid leaks
 * - For production, migrate to PostgresEventCloudAdapter
 *
 * <p><b>Performance Characteristics</b><br>
 * - **Append**: O(1) ConcurrentHashMap put + list append
 * - **Query**: O(n) full partition scan with filters
 * - **Tail**: O(1) subscription registration + O(k) notification to k subscribers
 * - **Memory**: ~1KB per event (varies by payload size)
 * - **Capacity**: Limited by JVM heap (tested up to 100k events)
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap (partitions), CopyOnWriteArrayList (events/subscriptions).
 * Concurrent append/query/tail operations supported. Subscription notifications serialized per subscription.
 *
 * @see EventCloud
 * @see PostgresEventCloudAdapter
 * @see EventRecord
 * @since 2.0.0
 * @doc.type class
 * @doc.purpose In-memory EventCloud for testing and development
 * @doc.layer core
 * @doc.pattern Repository
 */
public class InMemoryEventCloud implements EventCloud {

    // Partition -> List of events with metadata
    private final Map<PartitionId, List<StoredEvent>> partitions = new ConcurrentHashMap<>();

    // Offset counters per partition
    private final Map<PartitionId, AtomicLong> offsetCounters = new ConcurrentHashMap<>();

    // Idempotency tracking: (tenantId, idempotencyKey) -> AppendResult (bounded LRU)
    private static final int MAX_IDEMPOTENCY_CACHE_SIZE = 100_000;
    private final Map<String, AppendResult> idempotencyCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AppendResult> eldest) {
                    return size() > MAX_IDEMPOTENCY_CACHE_SIZE;
                }
            });

    // Active subscriptions
    private final List<ActiveSubscription> subscriptions = new CopyOnWriteArrayList<>();

    @Override
    public Promise<AppendResult> append(AppendRequest request) {
        EventRecord event = request.event();

        // Check idempotency
        if (event.idempotencyKey().isPresent()) {
            String idempotencyKey = makeIdempotencyKey(event.tenantId(), event.idempotencyKey().get().value());
            AppendResult cached = idempotencyCache.get(idempotencyKey);
            if (cached != null) {
                return Promise.of(cached);
            }
        }

        // Assign partition (simple hash-based partitioning)
        PartitionId partition = assignPartition(event);

        // Get next offset for partition
        Offset offset = nextOffset(partition);
        Instant appendTime = Instant.now();

        // Store the event
        StoredEvent stored = new StoredEvent(event, partition, offset, appendTime);
        partitions.computeIfAbsent(partition, k -> new CopyOnWriteArrayList<>()).add(stored);

        AppendResult result = new AppendResult(partition, offset, appendTime);

        // Cache for idempotency
        if (event.idempotencyKey().isPresent()) {
            String idempotencyKey = makeIdempotencyKey(event.tenantId(), event.idempotencyKey().get().value());
            idempotencyCache.put(idempotencyKey, result);
        }

        // Notify subscribers
        notifySubscribers(stored);

        return Promise.of(result);
    }

    @Override
    public Promise<List<AppendResult>> appendBatch(List<AppendRequest> requests) {
        // Chain sequentially using Promise combinators — never call .getResult()
        Promise<List<AppendResult>> chain = Promise.of(new ArrayList<>());
        for (AppendRequest request : requests) {
            chain = chain.then(results ->
                append(request).map(result -> {
                    results.add(result);
                    return results;
                })
            );
        }
        return chain;
    }

    @Override
    public EventStream subscribe(TenantId tenant, Selection selection, StartingPositions start) {
        InMemoryEventStream stream = new InMemoryEventStream(tenant, selection, start);
        subscriptions.add(new ActiveSubscription(stream, tenant, selection));
        return stream;
    }

    @Override
    public Promise<Page> query(HistoryQuery query) {
        List<StoredEvent> allEvents = new ArrayList<>();

        // Collect all matching events from all partitions
        for (List<StoredEvent> partitionEvents : partitions.values()) {
            for (StoredEvent stored : partitionEvents) {
                if (matches(stored, query)) {
                    allEvents.add(stored);
                }
            }
        }

        // Sort by occurrence time
        allEvents.sort(Comparator.comparing(se -> se.event.occurrenceTime()));

        // Apply paging
        int startIdx = Integer.parseInt(query.paging().resumeFrom().value());
        int endIdx = Math.min(startIdx + query.paging().limit(), allEvents.size());

        List<EventEnvelope> items = allEvents.subList(startIdx, endIdx).stream()
            .map(se -> new EventEnvelope(se.event, se.partition, se.offset))
            .toList();

        boolean hasMore = endIdx < allEvents.size();
        Offset nextToken = hasMore ? Offset.of(String.valueOf(endIdx)) : Offset.of(String.valueOf(allEvents.size()));

        return Promise.of(new Page(items, hasMore, nextToken));
    }

    @Override
    public HistoryScan scan(HistoryQuery query) {
        return new InMemoryHistoryScan(query);
    }

    /**
     * Convenience publish: accepted as a no-op for in-memory (testing/dev) deployments.
     * Production implementations should override to serialize and call {@link #append}.
     */
    @Override
    public Promise<Void> publish(String topic, Map<String, Object> payload) {
        return Promise.complete();
    }

    // ==================== Helper Methods ====================

    private PartitionId assignPartition(EventRecord event) {
        // Simple hash-based partitioning (4 partitions)
        int hash = event.tenantId().hashCode() ^ event.eventId().hashCode();
        int partitionNum = Math.abs(hash) % 4;
        return PartitionId.of(String.valueOf(partitionNum));
    }

    private Offset nextOffset(PartitionId partition) {
        AtomicLong counter = offsetCounters.computeIfAbsent(partition, k -> new AtomicLong(0));
        return Offset.of(String.valueOf(counter.getAndIncrement()));
    }

    private String makeIdempotencyKey(TenantId tenantId, String key) {
        return tenantId.toString() + ":" + key;
    }

    private boolean matches(StoredEvent stored, HistoryQuery query) {
        EventRecord event = stored.event;

        // Check tenant
        if (!event.tenantId().equals(query.tenant())) {
            return false;
        }

        // Check event type names
        if (!query.eventTypeNames().isEmpty() &&
            !query.eventTypeNames().contains(event.typeRef().name())) {
            return false;
        }

        // Check time range
        if (!query.eventTimeRange().contains(event.occurrenceTime())) {
            return false;
        }

        // Check header filter
        return evaluateFilter(query.headerFilter(), event.headers());
    }

    private boolean evaluateFilter(FilterExpression filter, Map<String, String> headers) {
        return switch (filter) {
            case TrueFilter t -> true;
            case EqualsFilter eq ->
                eq.value().equals(headers.get(eq.key()));
            case PrefixFilter prefix -> {
                String value = headers.get(prefix.key());
                yield value != null && value.startsWith(prefix.prefix());
            }
            case AndFilter and ->
                evaluateFilter(and.left(), headers) && evaluateFilter(and.right(), headers);
            case OrFilter or ->
                evaluateFilter(or.left(), headers) || evaluateFilter(or.right(), headers);
            case NotFilter not ->
                !evaluateFilter(not.expr(), headers);
        };
    }

    private void notifySubscribers(StoredEvent stored) {
        for (ActiveSubscription sub : subscriptions) {
            if (matchesSubscription(stored, sub)) {
                sub.stream.deliver(stored);
            }
        }
    }

    private boolean matchesSubscription(StoredEvent stored, ActiveSubscription sub) {
        EventRecord event = stored.event;

        // Check tenant
        if (!event.tenantId().equals(sub.tenant)) {
            return false;
        }

        // Check event type names
        if (!sub.selection.eventTypeNames().isEmpty() &&
            !sub.selection.eventTypeNames().contains(event.typeRef().name())) {
            return false;
        }

        // Check versions
        if (!sub.selection.versions().isEmpty() &&
            !sub.selection.versions().contains(event.typeRef().version().toString())) {
            return false;
        }

        // Check header filter
        return evaluateFilter(sub.selection.headerFilter(), event.headers());
    }

    // ==================== Inner Classes ====================

    private record StoredEvent(EventRecord event, PartitionId partition, Offset offset, Instant appendTime) {}

    private record ActiveSubscription(InMemoryEventStream stream, TenantId tenant, Selection selection) {}

    private class InMemoryEventStream implements EventStream {
        private final TenantId tenant;
        private final Selection selection;
        private final StartingPositions start;
        private final List<StoredEvent> buffer = new CopyOnWriteArrayList<>();
        private EventConsumer consumer;
        private volatile boolean paused = false;
        private volatile boolean closed = false;
        private long demand = 0;

        InMemoryEventStream(TenantId tenant, Selection selection, StartingPositions start) {
            this.tenant = tenant;
            this.selection = selection;
            this.start = start;

            // Load historical events based on starting position
            loadHistoricalEvents();
        }

        private void loadHistoricalEvents() {
            // Implementation depends on start position
            // For simplicity, StartAtLatest loads nothing, StartAtEarliest loads all
            if (start instanceof StartAtEarliest) {
                for (List<StoredEvent> partitionEvents : partitions.values()) {
                    for (StoredEvent stored : partitionEvents) {
                        if (matches(stored)) {
                            buffer.add(stored);
                        }
                    }
                }
            }
        }

        private boolean matches(StoredEvent stored) {
            return matchesSubscription(stored, new ActiveSubscription(this, tenant, selection));
        }

        void deliver(StoredEvent stored) {
            if (closed) return;

            buffer.add(stored);
            deliverPending();
        }

        @Override
        public void request(long n) {
            demand += n;
            deliverPending();
        }

        @Override
        public void onEvent(EventConsumer consumer) {
            this.consumer = consumer;
            deliverPending();
        }

        private synchronized void deliverPending() {
            if (paused || closed || consumer == null || demand <= 0 || buffer.isEmpty()) {
                return;
            }

            int toDeliver = (int) Math.min(demand, buffer.size());
            List<StoredEvent> batch = new ArrayList<>(buffer.subList(0, toDeliver));
            buffer.subList(0, toDeliver).clear();
            demand -= toDeliver;

            List<EventEnvelope> envelopes = batch.stream()
                .map(se -> new EventEnvelope(se.event, se.partition, se.offset))
                .toList();

            consumer.accept(new EventChunk(envelopes, false));
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
            deliverPending();
        }

        @Override
        public void close() {
            closed = true;
            subscriptions.removeIf(sub -> sub.stream == this);
        }
    }

    private class InMemoryHistoryScan implements HistoryScan {
        private final HistoryQuery query;
        private Consumer<List<EventEnvelope>> consumer;
        private volatile boolean paused = false;
        private volatile boolean closed = false;

        InMemoryHistoryScan(HistoryQuery query) {
            this.query = query;
        }

        @Override
        public void onBatch(Consumer<List<EventEnvelope>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void start() {
            if (consumer == null) {
                throw new IllegalStateException("Must call onBatch before start");
            }

            // Collect all matching events
            List<StoredEvent> allEvents = new ArrayList<>();
            for (List<StoredEvent> partitionEvents : partitions.values()) {
                for (StoredEvent stored : partitionEvents) {
                    if (matches(stored, query)) {
                        allEvents.add(stored);
                    }
                }
            }

            // Sort by occurrence time
            allEvents.sort(Comparator.comparing(se -> se.event.occurrenceTime()));

            // Deliver in batches
            int batchSize = 100;
            for (int i = 0; i < allEvents.size() && !closed; i += batchSize) {
                while (paused && !closed) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                int end = Math.min(i + batchSize, allEvents.size());
                List<EventEnvelope> batch = allEvents.subList(i, end).stream()
                    .map(se -> new EventEnvelope(se.event, se.partition, se.offset))
                    .toList();

                consumer.accept(batch);
            }
        }

        @Override
        public void pause() {
            paused = true;
        }

        @Override
        public void resume() {
            paused = false;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
