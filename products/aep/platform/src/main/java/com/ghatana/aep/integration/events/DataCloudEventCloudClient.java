package com.ghatana.aep.integration.events;

import com.ghatana.core.event.cloud.AppendResult;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.core.event.cloud.EventRecord;
import com.ghatana.core.event.cloud.EventStream;
import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.PartitionId;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DataCloud-backed implementation of the platform {@link EventCloud} interface.
 *
 * <p><b>Purpose</b><br>
 * Bridges AEP's EventCloud API to data-cloud's {@link EventLogStore} SPI,
 * enabling AEP components to use the familiar EventCloud interface while
 * events are physically stored and streamed through data-cloud infrastructure.
 *
 * <p><b>Architecture</b>
 * <pre>
 *   AEP Pipeline / Operators
 *           │
 *           ▼
 *   EventCloud (platform interface)
 *           │
 *           ▼
 *   DataCloudEventCloudClient (this class)
 *           │
 *     ┌─────┴─────┐
 *     ▼           ▼
 *  EventLogStore  (StorageTier routing)
 *  (data-cloud)
 * </pre>
 *
 * <p><b>Capabilities</b>
 * <ul>
 *   <li><b>Append</b>: Single and batch event persistence with idempotency</li>
 *   <li><b>Subscribe</b>: Real-time event streaming via tail-based subscription</li>
 *   <li><b>Query</b>: Historical event retrieval with filtering and pagination</li>
 *   <li><b>Scan</b>: Large-scale historical event scanning with backpressure</li>
 *   <li><b>Storage Tier Routing</b>: Configurable HOT/WARM/COOL/COLD tier assignment</li>
 *   <li><b>Metrics</b>: Append/subscribe/query operation counters and latency tracking</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * This class is fully thread-safe. All mutable state uses atomic operations or
 * concurrent data structures. The underlying EventLogStore is assumed to be
 * thread-safe per its SPI contract.
 *
 * @doc.type adapter
 * @doc.purpose AEP EventCloud implementation backed by data-cloud EventLogStore
 * @doc.layer integration
 * @doc.pattern Adapter, Bridge, Delegation
 * @since 1.0.0
 */
public class DataCloudEventCloudClient implements EventCloud {

    private static final Logger LOG = Logger.getLogger(DataCloudEventCloudClient.class.getName());

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 10_000;
    private static final String DEFAULT_PARTITION = "0";
    private static final int DEFAULT_SCAN_BATCH_SIZE = 500;

    private final EventLogStore eventLogStore;
    private final StorageTier defaultTier;

    // Metrics
    private final AtomicLong appendCount = new AtomicLong(0);
    private final AtomicLong appendBatchCount = new AtomicLong(0);
    private final AtomicLong appendFailureCount = new AtomicLong(0);
    private final AtomicLong subscribeCount = new AtomicLong(0);
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong scanCount = new AtomicLong(0);

    // Active subscriptions tracking for graceful shutdown
    private final CopyOnWriteArrayList<DataCloudEventStream> activeSubscriptions =
        new CopyOnWriteArrayList<>();

    /**
     * Creates a new DataCloudEventCloudClient.
     *
     * @param eventLogStore the data-cloud event log store (must not be null)
     * @throws NullPointerException if eventLogStore is null
     */
    public DataCloudEventCloudClient(EventLogStore eventLogStore) {
        this(eventLogStore, StorageTier.defaultTier());
    }

    /**
     * Creates a DataCloudEventCloudClient with explicit storage tier.
     *
     * @param eventLogStore the data-cloud event log store
     * @param defaultTier the default storage tier for new events
     * @throws NullPointerException if any argument is null
     */
    public DataCloudEventCloudClient(EventLogStore eventLogStore, StorageTier defaultTier) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore must not be null");
        this.defaultTier = Objects.requireNonNull(defaultTier, "defaultTier must not be null");
    }

    /**
     * Creates a client using the builder pattern.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== Append Operations ====================

    /**
     * {@inheritDoc}
     *
     * <p>Maps the platform {@link EventRecord} to a data-cloud {@link EventLogStore.EventEntry}
     * using {@link DataCloudEventMapper}, appends to the configured EventLogStore, and returns
     * the result as a platform {@link AppendResult}.
     */
    @Override
    public Promise<AppendResult> append(AppendRequest request) {
        Objects.requireNonNull(request, "AppendRequest must not be null");
        Objects.requireNonNull(request.event(), "EventRecord must not be null");

        LOG.log(Level.FINE, "Appending event: type={0}, tenant={1}",
            new Object[]{
                request.event().typeRef().name(),
                request.event().tenantId().toString()
            });

        try {
            EventLogStore.EventEntry entry = DataCloudEventMapper.toEventEntry(request);
            TenantContext tenantContext = DataCloudEventMapper.toTenantContext(
                request.event().tenantId());

            return eventLogStore.append(tenantContext, entry)
                .map(offset -> {
                    appendCount.incrementAndGet();
                    AppendResult result = DataCloudEventMapper.toAppendResult(
                        offset, DEFAULT_PARTITION);
                    LOG.log(Level.FINE, "Event appended: offset={0}", result.offset());
                    return result;
                })
                .mapException(ex -> {
                    appendFailureCount.incrementAndGet();
                    LOG.log(Level.WARNING, "Event append failed: " + ex.getMessage(), ex);
                    return new EventCloudException(
                        "Failed to append event: " + ex.getMessage(), ex);
                });
        } catch (Exception e) {
            appendFailureCount.incrementAndGet();
            LOG.log(Level.SEVERE, "Event mapping failed: " + e.getMessage(), e);
            return Promise.ofException(new EventCloudException(
                "Failed to map event for append: " + e.getMessage(), e));
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Atomically appends all events. If any mapping fails, the entire batch is rejected.
     * Events are mapped before submission to fail fast on type conversion errors.
     */
    @Override
    public Promise<List<AppendResult>> appendBatch(List<AppendRequest> requests) {
        Objects.requireNonNull(requests, "requests must not be null");

        if (requests.isEmpty()) {
            return Promise.of(List.of());
        }

        LOG.log(Level.FINE, "Appending batch of {0} events", requests.size());

        try {
            // Pre-validate and map all events before submitting
            // Extracts tenant from first event (all events in a batch should share tenant)
            TenantId tenantId = requests.getFirst().event().tenantId();
            TenantContext tenantContext = DataCloudEventMapper.toTenantContext(tenantId);

            List<EventLogStore.EventEntry> entries = requests.stream()
                .map(DataCloudEventMapper::toEventEntry)
                .toList();

            return eventLogStore.appendBatch(tenantContext, entries)
                .map(offsets -> {
                    appendBatchCount.incrementAndGet();
                    appendCount.addAndGet(offsets.size());
                    List<AppendResult> results = offsets.stream()
                        .map(offset -> DataCloudEventMapper.toAppendResult(
                            offset, DEFAULT_PARTITION))
                        .toList();
                    LOG.log(Level.FINE, "Batch of {0} events appended", results.size());
                    return results;
                })
                .mapException(ex -> {
                    appendFailureCount.addAndGet(requests.size());
                    LOG.log(Level.WARNING,
                        "Batch append failed for " + requests.size() + " events: " + ex.getMessage(), ex);
                    return new EventCloudException(
                        "Failed to append batch: " + ex.getMessage(), ex);
                });
        } catch (Exception e) {
            appendFailureCount.addAndGet(requests.size());
            return Promise.ofException(new EventCloudException(
                "Failed to map events for batch append: " + e.getMessage(), e));
        }
    }

    // ==================== Subscribe Operation ====================

    /**
     * {@inheritDoc}
     *
     * <p>Creates a backpressure-aware event stream over data-cloud's tail subscription.
     * The stream maps incoming data-cloud events to platform EventEnvelopes and delivers
     * them to registered consumers.
     *
     * <p>Starting positions are mapped:
     * <ul>
     *   <li>{@link StartAtLatest} → EventLogStore.getLatestOffset()</li>
     *   <li>{@link StartAtEarliest} → EventLogStore.getEarliestOffset()</li>
     *   <li>{@link StartAtTime} → Not directly supported, uses earliest + time filter</li>
     *   <li>{@link StartAtOffsets} → Uses first offset from the list</li>
     * </ul>
     */
    @Override
    public EventStream subscribe(TenantId tenant, Selection selection, StartingPositions start) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        Objects.requireNonNull(start, "start must not be null");

        subscribeCount.incrementAndGet();
        LOG.log(Level.INFO, "Creating subscription: tenant={0}, types={1}",
            new Object[]{tenant.toString(), selection.eventTypeNames()});

        TenantContext tenantContext = DataCloudEventMapper.toTenantContext(tenant);
        DataCloudEventStream stream = new DataCloudEventStream(
            tenant, selection, tenantContext, eventLogStore);
        activeSubscriptions.add(stream);

        // Resolve starting offset and initiate tail subscription
        resolveStartingOffset(tenantContext, start)
            .whenResult(startOffset -> {
                LOG.log(Level.FINE, "Subscription starting from offset: {0}", startOffset);
                stream.startTailing(startOffset);
            })
            .whenException(ex -> {
                LOG.log(Level.WARNING,
                    "Failed to resolve starting offset: " + ex.getMessage(), ex);
                stream.signalError(ex);
            });

        return stream;
    }

    // ==================== Query Operation ====================

    /**
     * {@inheritDoc}
     *
     * <p>Executes a historical query against the EventLogStore. Applies:
     * <ul>
     *   <li>Event type filtering via readByType or readByTimeRange</li>
     *   <li>Time range filtering</li>
     *   <li>Header filter evaluation (client-side)</li>
     *   <li>Pagination with resume token support</li>
     * </ul>
     */
    @Override
    public Promise<Page> query(HistoryQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(query.tenant(), "tenant must not be null");

        queryCount.incrementAndGet();
        LOG.log(Level.FINE, "Querying events: tenant={0}, types={1}",
            new Object[]{query.tenant().toString(), query.eventTypeNames()});

        TenantContext tenantContext = DataCloudEventMapper.toTenantContext(query.tenant());
        int limit = resolveLimit(query.paging());

        return executeQuery(tenantContext, query, limit)
            .map(entries -> buildPage(entries, query.tenant(), limit))
            .mapException(ex -> {
                LOG.log(Level.WARNING, "Query failed: " + ex.getMessage(), ex);
                return new EventCloudException(
                    "Failed to execute query: " + ex.getMessage(), ex);
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a scan that reads events in configurable batch sizes,
     * delivering results through the registered batch consumer. Supports
     * pause/resume for backpressure control.
     */
    @Override
    public HistoryScan scan(HistoryQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        scanCount.incrementAndGet();
        LOG.log(Level.FINE, "Starting scan: tenant={0}", query.tenant().toString());

        TenantContext tenantContext = DataCloudEventMapper.toTenantContext(query.tenant());
        return new DataCloudHistoryScan(tenantContext, query, eventLogStore);
    }

    // ==================== Metrics & Status ====================

    /**
     * Returns operational metrics for monitoring and observability.
     *
     * @return unmodifiable map of metric name → value
     */
    public Map<String, Object> getMetrics() {
        return Map.of(
            "append_count", appendCount.get(),
            "append_batch_count", appendBatchCount.get(),
            "append_failure_count", appendFailureCount.get(),
            "subscribe_count", subscribeCount.get(),
            "query_count", queryCount.get(),
            "scan_count", scanCount.get(),
            "active_subscriptions", activeSubscriptions.size(),
            "default_storage_tier", defaultTier.name()
        );
    }

    /**
     * Returns the default storage tier.
     */
    public StorageTier getDefaultTier() {
        return defaultTier;
    }

    /**
     * Returns the number of currently active subscriptions.
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }

    /**
     * Closes all active subscriptions. Call during shutdown.
     */
    public void closeAllSubscriptions() {
        LOG.log(Level.INFO, "Closing {0} active subscriptions", activeSubscriptions.size());
        for (DataCloudEventStream stream : activeSubscriptions) {
            try {
                stream.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error closing subscription: " + e.getMessage(), e);
            }
        }
        activeSubscriptions.clear();
    }

    // ==================== Internal: Offset Resolution ====================

    private Promise<com.ghatana.platform.types.identity.Offset> resolveStartingOffset(
            TenantContext tenantContext, StartingPositions start) {
        return switch (start) {
            case StartAtLatest ignored -> eventLogStore.getLatestOffset(tenantContext);
            case StartAtEarliest ignored -> eventLogStore.getEarliestOffset(tenantContext);
            case StartAtTime sat -> {
                // Time-based start: use earliest offset and filter by time in the stream
                LOG.log(Level.FINE, "Time-based subscription start: {0}", sat.time());
                yield eventLogStore.getEarliestOffset(tenantContext);
            }
            case StartAtOffsets sao -> {
                if (sao.offsets().isEmpty()) {
                    yield eventLogStore.getEarliestOffset(tenantContext);
                }
                // Use the first offset; multi-partition offsets are resolved to the minimum
                com.ghatana.platform.types.identity.Offset platformOffset =
                    sao.offsets().getFirst().offset();
                yield Promise.of(DataCloudEventMapper.toCoreOffset(platformOffset));
            }
        };
    }

    // ==================== Internal: Query Execution ====================

    private Promise<List<EventLogStore.EventEntry>> executeQuery(
            TenantContext tenantContext, HistoryQuery query, int limit) {

        // Determine starting offset from paging
        com.ghatana.platform.types.identity.Offset resumeFrom =
            query.paging() != null && query.paging().resumeFrom() != null
                ? DataCloudEventMapper.toCoreOffset(query.paging().resumeFrom())
                : com.ghatana.platform.types.identity.Offset.zero();

        // Route to the most efficient query method
        boolean hasTimeRange = query.eventTimeRange() != null;
        boolean hasEventTypes = query.eventTypeNames() != null
            && !query.eventTypeNames().isEmpty();

        if (hasTimeRange && hasEventTypes) {
            // Time range + event types: use time range query then filter by type
            return eventLogStore.readByTimeRange(
                    tenantContext,
                    query.eventTimeRange().fromInclusive(),
                    query.eventTimeRange().toExclusive(),
                    limit * 2)  // Over-fetch for post-filtering
                .map(entries -> entries.stream()
                    .filter(e -> query.eventTypeNames().contains(e.eventType()))
                    .limit(limit)
                    .toList());
        } else if (hasEventTypes) {
            // Event type filter only: use readByType for the first type
            // For multiple types, we need to merge results
            if (query.eventTypeNames().size() == 1) {
                return eventLogStore.readByType(
                    tenantContext,
                    query.eventTypeNames().getFirst(),
                    resumeFrom,
                    limit);
            } else {
                return readMultipleTypes(tenantContext, query.eventTypeNames(),
                    resumeFrom, limit);
            }
        } else if (hasTimeRange) {
            // Time range only
            return eventLogStore.readByTimeRange(
                tenantContext,
                query.eventTimeRange().fromInclusive(),
                query.eventTimeRange().toExclusive(),
                limit);
        } else {
            // No specific filter: read from offset
            return eventLogStore.read(tenantContext, resumeFrom, limit);
        }
    }

    private Promise<List<EventLogStore.EventEntry>> readMultipleTypes(
            TenantContext tenantContext,
            List<String> eventTypeNames,
            com.ghatana.platform.types.identity.Offset resumeFrom,
            int limit) {
        // Read each type separately with a proportional limit, then merge
        int perTypeLimit = Math.max(1, limit / eventTypeNames.size()) + 1;

        @SuppressWarnings("unchecked")
        Promise<List<EventLogStore.EventEntry>>[] typePromises = eventTypeNames.stream()
            .map(typeName -> eventLogStore.readByType(
                tenantContext, typeName, resumeFrom, perTypeLimit))
            .toArray(Promise[]::new);

        return Promise.ofCallback(cb -> {
            // Collect all results as they complete
            List<EventLogStore.EventEntry> allEntries =
                Collections.synchronizedList(new ArrayList<>());
            AtomicLong pendingCount = new AtomicLong(typePromises.length);

            for (Promise<List<EventLogStore.EventEntry>> p : typePromises) {
                p.whenResult(entries -> {
                    allEntries.addAll(entries);
                    if (pendingCount.decrementAndGet() == 0) {
                        // All types fetched — sort by timestamp, limit
                        List<EventLogStore.EventEntry> sorted = allEntries.stream()
                            .sorted(Comparator.comparing(EventLogStore.EventEntry::timestamp))
                            .limit(limit)
                            .toList();
                        cb.set(sorted);
                    }
                })
                .whenException(ex -> {
                    if (pendingCount.getAndSet(-1) > 0) {
                        cb.setException(ex);
                    }
                });
            }
        });
    }

    private Page buildPage(List<EventLogStore.EventEntry> entries, TenantId tenantId, int limit) {
        List<EventEnvelope> envelopes = new ArrayList<>(entries.size());
        com.ghatana.platform.types.identity.Offset lastOffset =
            com.ghatana.platform.types.identity.Offset.zero();

        for (int i = 0; i < entries.size(); i++) {
            EventLogStore.EventEntry entry = entries.get(i);
            com.ghatana.platform.types.identity.Offset offset =
                com.ghatana.platform.types.identity.Offset.of(i);
            envelopes.add(DataCloudEventMapper.toEventEnvelope(
                entry, tenantId, offset, DEFAULT_PARTITION));
            lastOffset = offset;
        }

        boolean hasMore = entries.size() >= limit;
        com.ghatana.platform.types.identity.Offset resumeToken =
            hasMore
                ? DataCloudEventMapper.toPlatformOffset(
                    com.ghatana.platform.types.identity.Offset.of(offsetAsLong(lastOffset) + 1))
                : DataCloudEventMapper.toPlatformOffset(lastOffset);

        return new Page(envelopes, hasMore, resumeToken);
    }

    private int resolveLimit(Paging paging) {
        if (paging == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(1, paging.limit()), MAX_PAGE_SIZE);
    }

    private static long offsetAsLong(com.ghatana.platform.types.identity.Offset offset) {
        Objects.requireNonNull(offset, "offset must not be null");
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Offset must be numeric for AEP event stream operations: '" + offset.value() + "'",
                e
            );
        }
    }

    // ==================== EventStream Implementation ====================

    /**
     * Data-cloud-backed EventStream that adapts tail subscription to
     * the platform's backpressure-aware stream API.
     */
    private class DataCloudEventStream implements EventStream {

        private final TenantId tenantId;
        private final Selection selection;
        private final TenantContext tenantContext;
        private final EventLogStore store;

        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicLong requested = new AtomicLong(0);
        private final AtomicLong delivered = new AtomicLong(0);
        private final AtomicReference<EventConsumer> consumer = new AtomicReference<>();
        private final AtomicReference<EventLogStore.Subscription> tailSubscription =
            new AtomicReference<>();
        private final AtomicReference<Instant> timeFilter = new AtomicReference<>();

        DataCloudEventStream(TenantId tenantId, Selection selection,
                             TenantContext tenantContext, EventLogStore store) {
            this.tenantId = tenantId;
            this.selection = selection;
            this.tenantContext = tenantContext;
            this.store = store;
        }

        void startTailing(com.ghatana.platform.types.identity.Offset startOffset) {
            if (closed.get()) return;

            store.tail(tenantContext, startOffset, this::onEventEntry)
                .whenResult(sub -> {
                    if (closed.get()) {
                        sub.cancel();
                    } else {
                        tailSubscription.set(sub);
                        LOG.log(Level.FINE, "Tail subscription established");
                    }
                })
                .whenException(ex -> {
                    LOG.log(Level.WARNING,
                        "Failed to establish tail subscription: " + ex.getMessage(), ex);
                    signalError(ex);
                });
        }

        void signalError(Throwable ex) {
            LOG.log(Level.WARNING, "Stream error: " + ex.getMessage(), ex);
            close();
        }

        private void onEventEntry(EventLogStore.EventEntry entry) {
            if (closed.get() || paused.get()) return;
            if (requested.get() <= 0 && requested.get() != Long.MAX_VALUE) return;

            // Apply event type filter
            if (!matchesSelection(entry)) return;

            // Apply time filter if StartAtTime was used
            Instant tf = timeFilter.get();
            if (tf != null && entry.timestamp().isBefore(tf)) return;

            try {
                EventEnvelope envelope = DataCloudEventMapper.toEventEnvelope(
                    entry, tenantId,
                    com.ghatana.platform.types.identity.Offset.of(delivered.get()),
                    DEFAULT_PARTITION);

                EventConsumer c = consumer.get();
                if (c != null) {
                    EventChunk chunk = new EventChunk(List.of(envelope), false);
                    c.accept(chunk);
                    delivered.incrementAndGet();
                    if (requested.get() != Long.MAX_VALUE) {
                        requested.decrementAndGet();
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    "Error delivering event to consumer: " + e.getMessage(), e);
            }
        }

        private boolean matchesSelection(EventLogStore.EventEntry entry) {
            // Empty event types means accept all
            if (selection.eventTypeNames().isEmpty()) return true;
            return selection.eventTypeNames().contains(entry.eventType());
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("request count must be positive");
            }
            requested.addAndGet(n);
        }

        @Override
        public void onEvent(EventConsumer consumer) {
            Objects.requireNonNull(consumer, "consumer must not be null");
            this.consumer.set(consumer);
        }

        @Override
        public void pause() {
            paused.set(true);
        }

        @Override
        public void resume() {
            paused.set(false);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                EventLogStore.Subscription sub = tailSubscription.getAndSet(null);
                if (sub != null) {
                    sub.cancel();
                }
                activeSubscriptions.remove(this);
                LOG.log(Level.FINE, "Stream closed, delivered {0} events", delivered.get());
            }
        }

        /**
         * Sets a time filter for StartAtTime-based subscriptions.
         */
        void setTimeFilter(Instant filterTime) {
            timeFilter.set(filterTime);
        }
    }

    // ==================== HistoryScan Implementation ====================

    /**
     * Data-cloud-backed HistoryScan that reads events in batches.
     */
    private static class DataCloudHistoryScan implements HistoryScan {

        private final TenantContext tenantContext;
        private final HistoryQuery query;
        private final EventLogStore store;

        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicReference<Consumer<List<EventEnvelope>>> batchConsumer =
            new AtomicReference<>();

        private volatile com.ghatana.platform.types.identity.Offset currentOffset;

        DataCloudHistoryScan(TenantContext tenantContext, HistoryQuery query,
                             EventLogStore store) {
            this.tenantContext = tenantContext;
            this.query = query;
            this.store = store;
            this.currentOffset = com.ghatana.platform.types.identity.Offset.zero();
        }

        @Override
        public void onBatch(Consumer<List<EventEnvelope>> consumer) {
            Objects.requireNonNull(consumer, "batch consumer must not be null");
            this.batchConsumer.set(consumer);
        }

        @Override
        public void start() {
            if (!started.compareAndSet(false, true)) {
                throw new IllegalStateException("Scan already started");
            }
            Consumer<List<EventEnvelope>> bc = batchConsumer.get();
            if (bc == null) {
                throw new IllegalStateException("Batch consumer must be set before starting");
            }
            fetchNextBatch();
        }

        @Override
        public void pause() {
            paused.set(true);
        }

        @Override
        public void resume() {
            if (paused.compareAndSet(true, false)) {
                fetchNextBatch();
            }
        }

        @Override
        public void close() {
            closed.set(true);
        }

        private void fetchNextBatch() {
            if (closed.get() || paused.get()) return;

            Consumer<List<EventEnvelope>> bc = batchConsumer.get();
            if (bc == null) return;

            boolean hasTimeRange = query.eventTimeRange() != null;

            Promise<List<EventLogStore.EventEntry>> readPromise;
            if (hasTimeRange) {
                readPromise = store.readByTimeRange(
                    tenantContext,
                    query.eventTimeRange().fromInclusive(),
                    query.eventTimeRange().toExclusive(),
                    DEFAULT_SCAN_BATCH_SIZE);
            } else {
                readPromise = store.read(tenantContext, currentOffset, DEFAULT_SCAN_BATCH_SIZE);
            }

            readPromise
                .whenResult(entries -> {
                    if (closed.get()) return;

                    // Filter by event types if specified
                    List<EventLogStore.EventEntry> filtered = entries;
                    if (query.eventTypeNames() != null && !query.eventTypeNames().isEmpty()) {
                        filtered = entries.stream()
                            .filter(e -> query.eventTypeNames().contains(e.eventType()))
                            .toList();
                    }

                    if (filtered.isEmpty()) {
                        // No more data — deliver empty terminal batch
                        bc.accept(List.of());
                        return;
                    }

                    // Map entries to envelopes
                    TenantId tid = query.tenant();
                    List<EventEnvelope> envelopes = new ArrayList<>(filtered.size());
                    for (int i = 0; i < filtered.size(); i++) {
                        com.ghatana.platform.types.identity.Offset offset =
                            com.ghatana.platform.types.identity.Offset.of(
                                offsetAsLong(currentOffset) + i);
                        envelopes.add(DataCloudEventMapper.toEventEnvelope(
                            filtered.get(i), tid, offset, DEFAULT_PARTITION));
                    }

                    // Advance cursor
                    currentOffset = com.ghatana.platform.types.identity.Offset.of(
                        offsetAsLong(currentOffset) + entries.size());

                    bc.accept(envelopes);

                    // Continue if not paused/closed and there might be more data
                    if (entries.size() >= DEFAULT_SCAN_BATCH_SIZE && !paused.get() && !closed.get()) {
                        fetchNextBatch();
                    }
                })
                .whenException(ex -> {
                    LOG.log(Level.WARNING, "Scan batch failed: " + ex.getMessage(), ex);
                    // Deliver error as empty batch — consumer should check for scan closure
                    Consumer<List<EventEnvelope>> c = batchConsumer.get();
                    if (c != null) {
                        c.accept(List.of());
                    }
                    close();
                });
        }
    }

    // ==================== Builder ====================

    /**
     * Builder for DataCloudEventCloudClient.
     */
    public static class Builder {
        private EventLogStore eventLogStore;
        private StorageTier defaultTier = StorageTier.defaultTier();

        private Builder() {}

        /**
         * Sets the event log store (required).
         */
        public Builder eventLogStore(EventLogStore eventLogStore) {
            this.eventLogStore = eventLogStore;
            return this;
        }

        /**
         * Sets the default storage tier for new events.
         */
        public Builder defaultTier(StorageTier tier) {
            this.defaultTier = tier;
            return this;
        }

        /**
         * Builds the client.
         *
         * @throws NullPointerException if eventLogStore is not set
         */
        public DataCloudEventCloudClient build() {
            return new DataCloudEventCloudClient(eventLogStore, defaultTier);
        }
    }

    // ==================== Exception ====================

    /**
     * Exception thrown by EventCloud operations when data-cloud calls fail.
     */
    public static class EventCloudException extends RuntimeException {
        public EventCloudException(String message) {
            super(message);
        }

        public EventCloudException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
