package com.ghatana.core.event.cloud;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * EventCloud - Append-only immutable event log with real-time tailing, history queries, and multi-tenant isolation.
 *
 * <p><b>Migration Notice</b><br>
 * This module is migrating from {@code com.ghatana.core.event.cloud} to {@code com.ghatana.datacloud.event}
 * as part of the eventcloud → data-cloud/event consolidation. The package {@code com.ghatana.core.event.cloud}
 * is deprecated for new code. Use the new package location when available.
 *
 * <p><b>Purpose</b><br>
 * Core abstraction for durable event storage in Ghatana EventCloud platform. Provides append operations (single/batch),
 * real-time streaming (subscribe/tail), and historical queries (bounded/scan). Enforces multi-tenant isolation via TenantId.
 * Foundation for event-driven architecture, CQRS, event sourcing, and distributed tracing.
 *
 * @doc.type interface
 * @doc.purpose Core abstraction for append-only immutable event log with real-time tailing and multi-tenant isolation
 * @doc.layer core
 * @doc.pattern Service Provider Interface, Event Store
 * * <p><b>Architecture Role</b><br>
 * L1 storage tier interface in EventCloud v4 architecture. Implemented by adapters: PostgresEventCloudAdapter (L1 durable
 * storage), InMemoryEventCloud (testing), KafkaEventCloudAdapter (L0 fan-out). Used by IngestionService for event intake,
 * PipelineBuilder for stream sources, QueryService for history retrieval, and OperatorCatalog for operator event storage.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Append single event (idempotent)
 * EventCloud cloud = getEventCloud();
 * 
 * EventRecord event = EventRecord.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .typeRef(EventTypeRef.of("transaction.created", Version.of("1.0.0")))
 *     .eventId(EventId.random())
 *     .occurrenceTime(Instant.now())
 *     .detectionTime(Instant.now())
 *     .payload(ByteBuffer.wrap(jsonBytes))
 *     .contentType(ContentType.JSON)
 *     .idempotencyKey(Optional.of(IdempotencyKey.of("tx-12345")))
 *     .build();
 * 
 * AppendRequest request = new AppendRequest(event, AppendOptions.defaults());
 * cloud.append(request).whenComplete((result, ex) -> {
 *     if (ex == null) {
 *         log.info("Appended to partition {} offset {}", 
 *             result.partitionId(), result.offset());
 *     }
 * });
 * // Idempotent: Duplicate idempotencyKey returns same partition/offset
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Batch append (atomic per partition)
 * EventCloud cloud = getEventCloud();
 * 
 * List<AppendRequest> requests = List.of(
 *     new AppendRequest(event1, AppendOptions.defaults()),
 *     new AppendRequest(event2, AppendOptions.defaults()),
 *     new AppendRequest(event3, AppendOptions.defaults())
 * );
 * 
 * cloud.appendBatch(requests).whenComplete((results, ex) -> {
 *     if (ex == null) {
 *         results.forEach(result -> 
 *             log.info("Batch appended: {} @ {}", 
 *                 result.partitionId(), result.offset()));
 *     }
 * });
 * // More efficient than 3 individual append() calls
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Real-time tail (subscribe to live stream)
 * EventCloud cloud = getEventCloud();
 * 
 * TailRequest tailRequest = TailRequest.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .typeRef(Optional.of(EventTypeRef.of("transaction.*")))  // Wildcard
 *     .partitionId(Optional.empty())  // All partitions
 *     .fromOffset(Offset.LATEST)  // Start from now
 *     .build();
 * 
 * cloud.tail(tailRequest, event -> {
 *     log.info("Received event: {} at {}", 
 *         event.typeRef(), event.occurrenceTime());
 *     // Process event in real-time
 * }).whenComplete((subscription, ex) -> {
 *     // subscription.cancel() to stop tailing
 * });
 * // Backpressure-aware streaming
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: History query (bounded retrieval)
 * EventCloud cloud = getEventCloud();
 * 
 * HistoryQuery query = HistoryQuery.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .typeRef(Optional.of(EventTypeRef.of("transaction.created")))
 *     .startTime(Instant.now().minus(Duration.ofHours(24)))
 *     .endTime(Instant.now())
 *     .limit(1000)
 *     .build();
 * 
 * cloud.queryHistory(query).whenComplete((events, ex) -> {
 *     if (ex == null) {
 *         log.info("Retrieved {} events from last 24h", events.size());
 *         events.forEach(event -> processHistorical(event));
 *     }
 * });
 * // Returns up to 1000 events (pagination via offset continuation)
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Stream scan (unbounded iterator)
 * EventCloud cloud = getEventCloud();
 * 
 * ScanRequest scan = ScanRequest.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .partitionId(PartitionId.of(3))
 *     .fromOffset(Offset.EARLIEST)  // From beginning
 *     .build();
 * 
 * cloud.scan(scan, event -> {
 *     // Process event
 *     return true;  // Continue scanning
 * }).whenComplete((count, ex) -> {
 *     log.info("Scanned {} events", count);
 * });
 * // Backpressure-aware streaming for large datasets
 * }</pre>
 *
 * <p><b>API Operations</b><br>
 * 1. **Append Operations**:
 *    - `append(request)` - Single event append (idempotent by idempotencyKey)
 *    - `appendBatch(requests)` - Batch append (atomic per partition)
 *
 * 2. **Real-time Streaming**:
 *    - `tail(request, consumer)` - Subscribe to live event stream (push-based)
 *    - Returns `Subscription` for cancellation
 *
 * 3. **Historical Queries**:
 *    - `queryHistory(query)` - Bounded retrieval (time range, limit)
 *    - `scan(request, consumer)` - Unbounded streaming iterator
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All operations enforce tenant isolation via TenantId:
 * - Append: Events tagged with tenantId
 * - Tail/Query: Only returns events for specified tenantId
 * - Partitioning: Tenant-scoped (tenant-123 partition-3 != tenant-456 partition-3)
 * - Idempotency: Scoped to (tenantId, idempotencyKey) tuple
 *
 * <p><b>Idempotency Guarantees</b><br>
 * Append operations idempotent by (tenantId, idempotencyKey):
 * - First append: Assigns partition/offset, stores event
 * - Duplicate append: Returns cached partition/offset, no storage
 * - Enables at-least-once delivery semantics with exactly-once processing
 *
 * <p><b>Partitioning Strategy</b><br>
 * Events distributed across partitions:
 * - Hash-based partitioning (e.g., tenantId + eventId hash)
 * - Preserves ordering within partition
 * - No global ordering guarantee across partitions
 * - Typical partition count: 8-64 per tenant
 *
 * <p><b>Best Practices</b><br>
 * - Always provide idempotencyKey for critical events (duplicate protection)
 * - Use batch append for high-throughput ingestion (reduces network RTT)
 * - Set contentType and schemaUri for schema validation
 * - Use tail() for real-time processing, queryHistory() for analytics
 * - Cancel subscriptions on shutdown (avoid resource leaks)
 * - Paginate large history queries (use limit + offset continuation)
 *
 * <p><b>Performance Characteristics</b><br>
 * - **Append latency**: P99 <10ms (single), P99 <50ms (batch 1000 events)
 * - **Tail latency**: <10ms from append to consumer notification
 * - **Query latency**: P99 <100ms for 1000 events (indexed by time/typeRef)
 * - **Throughput**: 100k+ events/sec per EventCloud instance
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe for concurrent append/query/tail operations.
 *
 * <p><b>Integration Points</b><br>
 * - IngestionService: Event intake via append/appendBatch
 * - PipelineBuilder: Stream sources via tail()
 * - QueryService: Historical analytics via queryHistory()
 * - OperatorCatalog: Operator event storage (operator.registered events)
 * - PostgreSQL: L1 durable storage (PostgresEventCloudAdapter)
 * - Kafka: L0 fan-out (KafkaEventCloudAdapter, optional)
 *
 * @see InMemoryEventCloud
 * @see EventRecord
 * @see AppendResult
 * @see PostgresEventCloudAdapter
 * @since 2.0.0
 * @doc.type interface
 * @doc.purpose Append-only event log with real-time tailing and history queries
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface EventCloud {

    // ==================== Append Operations ====================

    /**
     * Append a single event to the log.
     * Idempotent by (tenantId, idempotencyKey).
     *
     * @param request append request with event and options
     * @return promise of append result with assigned partition/offset
     */
    Promise<AppendResult> append(AppendRequest request);

    /**
     * Append multiple events atomically per partition.
     *
     * @param requests batch of append requests
     * @return promise of list of append results
     */
    Promise<List<AppendResult>> appendBatch(List<AppendRequest> requests);

    /**
     * Request to append an event.
     */
    record AppendRequest(EventRecord event, AppendOptions options) {
        public AppendRequest {
            Objects.requireNonNull(event, "event required");
            Objects.requireNonNull(options, "options required");
        }
    }

    /**
     * Options for appending events.
     */
    record AppendOptions(
        boolean validateSchema,
        boolean requireKnownEventType,
        boolean enforceOrdering
    ) {
        public static AppendOptions defaults() {
            return new AppendOptions(true, true, false);
        }

        public static AppendOptions lenient() {
            return new AppendOptions(false, false, false);
        }
    }

    // ==================== Real-time Tail (Subscribe) ====================

    /**
     * Subscribe to a live event stream.
     * Events matching the selection criteria will be delivered via the returned stream.
     *
     * @param tenant tenant ID for filtering
     * @param selection event selection criteria
     * @param start starting positions per partition
     * @return backpressure-aware event stream
     */
    EventStream subscribe(TenantId tenant, Selection selection, StartingPositions start);

    /**
     * Selection criteria for subscribing to events.
     */
    record Selection(
        List<String> eventTypeNames,
        List<String> versions,
        FilterExpression headerFilter
    ) {
        public Selection {
            eventTypeNames = List.copyOf(Objects.requireNonNull(eventTypeNames, "eventTypeNames required"));
            versions = List.copyOf(Objects.requireNonNull(versions, "versions required"));
            Objects.requireNonNull(headerFilter, "headerFilter required");
        }

        public static Selection all() {
            return new Selection(List.of(), List.of(), EventCloud.TRUE);
        }

        public static Selection byTypes(String... typeNames) {
            return new Selection(List.of(typeNames), List.of(), EventCloud.TRUE);
        }
    }

    /**
     * Starting positions for reading from partitions.
     */
    sealed interface StartingPositions {}

    record StartAtLatest() implements StartingPositions {}
    record StartAtEarliest() implements StartingPositions {}
    record StartAtTime(Instant time) implements StartingPositions {
        public StartAtTime {
            Objects.requireNonNull(time, "time required");
        }
    }
    record StartAtOffsets(List<StartOffset> offsets) implements StartingPositions {
        public StartAtOffsets {
            offsets = List.copyOf(Objects.requireNonNull(offsets, "offsets required"));
        }
    }

    /**
     * Starting offset for a specific partition.
     */
    record StartOffset(PartitionId partition, Offset offset) {
        public StartOffset {
            Objects.requireNonNull(partition, "partition required");
            Objects.requireNonNull(offset, "offset required");
        }
    }

    /**
     * Filter expression DSL for headers (DFA-suitable).
     */
    sealed interface FilterExpression {}

    FilterExpression TRUE = new TrueFilter();

    record TrueFilter() implements FilterExpression {}

    record EqualsFilter(String key, String value) implements FilterExpression {
        public EqualsFilter {
            Objects.requireNonNull(key, "key required");
            Objects.requireNonNull(value, "value required");
        }
    }

    record PrefixFilter(String key, String prefix) implements FilterExpression {
        public PrefixFilter {
            Objects.requireNonNull(key, "key required");
            Objects.requireNonNull(prefix, "prefix required");
        }
    }

    record AndFilter(FilterExpression left, FilterExpression right) implements FilterExpression {
        public AndFilter {
            Objects.requireNonNull(left, "left required");
            Objects.requireNonNull(right, "right required");
        }
    }

    record OrFilter(FilterExpression left, FilterExpression right) implements FilterExpression {
        public OrFilter {
            Objects.requireNonNull(left, "left required");
            Objects.requireNonNull(right, "right required");
        }
    }

    record NotFilter(FilterExpression expr) implements FilterExpression {
        public NotFilter {
            Objects.requireNonNull(expr, "expr required");
        }
    }

    /**
     * Consumer of event chunks.
     */
    @FunctionalInterface
    interface EventConsumer {
        void accept(EventChunk chunk);
    }

    /**
     * Chunk of events delivered in a stream.
     */
    record EventChunk(List<EventEnvelope> events, boolean endOfPartition) {
        public EventChunk {
            events = List.copyOf(Objects.requireNonNull(events, "events required"));
        }
    }

    /**
     * Event envelope with partition and offset metadata.
     */
    record EventEnvelope(EventRecord record, PartitionId partitionId, Offset offset) {
        public EventEnvelope {
            Objects.requireNonNull(record, "record required");
            Objects.requireNonNull(partitionId, "partitionId required");
            Objects.requireNonNull(offset, "offset required");
        }
    }

    // ==================== History Query ====================

    /**
     * Bounded query returning pages of historical events.
     * For large scans, prefer {@link #scan}.
     *
     * @param query history query criteria
     * @return promise of page of results
     */
    Promise<Page> query(HistoryQuery query);

    /**
     * Streamed history scan with consistent snapshot semantics.
     * Suitable for large result sets.
     *
     * @param query history query criteria
     * @return history scan handle
     */
    HistoryScan scan(HistoryQuery query);

    /**
     * History query criteria.
     */
    record HistoryQuery(
        TenantId tenant,
        List<String> eventTypeNames,
        TimeRange eventTimeRange,
        FilterExpression headerFilter,
        Paging paging
    ) {
        public HistoryQuery {
            Objects.requireNonNull(tenant, "tenant required");
            eventTypeNames = List.copyOf(Objects.requireNonNull(eventTypeNames, "eventTypeNames required"));
            Objects.requireNonNull(eventTimeRange, "eventTimeRange required");
            Objects.requireNonNull(headerFilter, "headerFilter required");
            Objects.requireNonNull(paging, "paging required");
        }
    }

    /**
     * Time range for querying events.
     */
    record TimeRange(Instant fromInclusive, Instant toExclusive) {
        public TimeRange {
            Objects.requireNonNull(fromInclusive, "fromInclusive required");
            Objects.requireNonNull(toExclusive, "toExclusive required");
            if (!fromInclusive.isBefore(toExclusive)) {
                throw new IllegalArgumentException("fromInclusive must be before toExclusive");
            }
        }

        public boolean contains(Instant time) {
            return (time.equals(fromInclusive) || time.isAfter(fromInclusive)) && time.isBefore(toExclusive);
        }
    }

    /**
     * Pagination options.
     */
    record Paging(int limit, Offset resumeFrom) {
        public Paging {
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive, got: " + limit);
            }
            Objects.requireNonNull(resumeFrom, "resumeFrom required");
        }

        public static Paging first(int limit) {
            return new Paging(limit, Offset.of("0"));
        }
    }

    /**
     * Page of query results.
     */
    record Page(List<EventEnvelope> items, boolean hasMore, Offset nextResumeToken) {
        public Page {
            items = List.copyOf(Objects.requireNonNull(items, "items required"));
            Objects.requireNonNull(nextResumeToken, "nextResumeToken required");
        }
    }

    /**
     * History scan handle for streaming large result sets.
     */
    interface HistoryScan extends AutoCloseable {
        /**
         * Register batch consumer callback.
         */
        void onBatch(Consumer<List<EventEnvelope>> consumer);

        /**
         * Start the scan.
         */
        void start();

        /**
         * Pause the scan.
         */
        void pause();

        /**
         * Resume the scan.
         */
        void resume();

        /**
         * Close the scan and release resources.
         */
        @Override
        void close();
    }

    // ==================== Convenience Publish Methods ====================

    /**
     * Convenience method to publish a simple event with topic and payload.
     * Creates an EventRecord with minimal metadata and appends it.
     *
     * @param topic event topic/type
     * @param payload event payload as map
     * @return promise of void (fire-and-forget style)
     */
    default Promise<Void> publish(String topic, Map<String, Object> payload) {
        // Default implementation - subclasses should override for efficiency
        return Promise.ofException(new UnsupportedOperationException(
            "publish(topic, payload) not implemented - use append() directly"));
    }

    /**
     * Convenience method to publish an event with topic, tenant ID, and payload.
     *
     * @param topic event topic/type
     * @param tenantId tenant identifier
     * @param payload event payload as map
     * @return promise of void
     */
    default Promise<Void> publish(String topic, String tenantId, Map<String, Object> payload) {
        return publish(topic, payload);
    }

    /**
     * Convenience method to publish an event with all common parameters.
     *
     * @param topic event topic/type
     * @param tenantId tenant identifier
     * @param runId workflow run identifier
     * @param category event category (e.g., "STEP", "ERROR")
     * @param stepName step name
     * @param data event data
     * @param timestamp event timestamp
     * @return promise of void
     */
    default Promise<Void> publish(
        String topic,
        String tenantId,
        String runId,
        String category,
        String stepName,
        Map<String, Object> data,
        Instant timestamp) {
        
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("topic", topic);
        payload.put("tenantId", tenantId);
        payload.put("runId", runId);
        payload.put("category", category);
        payload.put("stepName", stepName);
        payload.put("data", data);
        payload.put("timestamp", timestamp.toString());
        
        return publish(topic, tenantId, payload);
    }
}
