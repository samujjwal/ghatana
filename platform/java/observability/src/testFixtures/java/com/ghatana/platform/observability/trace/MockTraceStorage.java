package com.ghatana.platform.observability.trace;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link TraceStorage} for testing purposes.
 * <p>
 * MockTraceStorage stores spans in memory using ConcurrentHashMap and provides
 * filtering/querying capabilities without requiring an external database. This
 * is ideal for unit tests, integration tests, and local development.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>In-Memory Storage</b>: Spans stored in ConcurrentHashMap (traceId → List&lt;SpanData&gt;)</li>
 *   <li><b>Full Query Support</b>: Service, operation, status, duration, time range, tags, span count filters</li>
 *   <li><b>Statistics Calculation</b>: Aggregates (counts, durations, percentiles, most common service/operation)</li>
 *   <li><b>Thread-Safe</b>: ConcurrentHashMap ensures concurrent access safety</li>
 *   <li><b>Promise-Based</b>: All operations return ActiveJ Promise (immediate completion)</li>
 * </ul>
 * 
 * <h2>Limitations (NOT for Production)</h2>
 * <ul>
 *   <li><b>No Persistence</b>: Data lost on restart (in-memory only)</li>
 *   <li><b>No Scalability</b>: Single JVM memory only (no distributed storage)</li>
 *   <li><b>Linear Scans</b>: O(n) query performance (no indexing)</li>
 *   <li><b>Memory Pressure</b>: All traces retained until cleared</li>
 *   <li><b>No Retention Policy</b>: No automatic cleanup of old traces</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Test scenario: Store and query traces
 * MockTraceStorage storage = new MockTraceStorage();
 *
 * // Store test data
 * SpanData span1 = SpanData.builder()
 *     .withSpanId("span-1")
 *     .withTraceId("trace-123")
 *     .withServiceName("test-service")
 *     .withStartTime(Instant.now())
 *     .withEndTime(Instant.now().plusMillis(100))
 *     .withStatus("OK")
 *     .build();
 * storage.storeSpan(span1).join();
 *
 * // Query traces
 * TraceQuery query = TraceQuery.builder()
 *     .withServiceName("test-service")
 *     .withStatus("OK")
 *     .build();
 * List<TraceInfo> traces = storage.queryTraces(query).join();
 *
 * // Verify
 * assertEquals(1, traces.size());
 * assertEquals("trace-123", traces.get(0).traceId());
 *
 * // Get statistics
 * TraceStatistics stats = storage.getStatistics(query).join();
 * assertEquals(1, stats.totalTraces());
 * }</pre>
 *
 * <h2>Query Filtering Logic</h2>
 * All filters are combined with AND logic:
 * <ul>
 *   <li><b>Service Name</b>: Any span in trace must match</li>
 *   <li><b>Operation Name</b>: Any span in trace must match</li>
 *   <li><b>Status</b>: Overall trace status must match</li>
 *   <li><b>Duration</b>: Trace duration must be within range</li>
 *   <li><b>Time Range</b>: Trace start/end time must be within range</li>
 *   <li><b>Tags</b>: All tags must match at least one span</li>
 *   <li><b>Span Count</b>: Number of spans must be within range</li>
 * </ul>
 * 
 * <h2>Statistics Calculation</h2>
 * <ul>
 *   <li><b>Total Traces</b>: Count of matching traces</li>
 *   <li><b>Total Spans</b>: Sum of spans across all traces</li>
 *   <li><b>Error Count</b>: Traces with at least one error span</li>
 *   <li><b>Duration Percentiles</b>: P50/P95/P99 calculated from sorted durations</li>
 *   <li><b>Most Common Service</b>: Service appearing most frequently in traces</li>
 *   <li><b>Most Common Operation</b>: Operation appearing most frequently in spans</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * Uses {@link ConcurrentHashMap} for storing spans, ensuring thread-safe concurrent
 * access. However, queries perform full scans and are not optimized for high concurrency.
 * 
 * <h2>Cleanup</h2>
 * Call {@link #clear()} or {@link #close()} to remove all stored traces and free memory.
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit, Integration
 * @thread_safety Thread-safe
 * @performance O(n) queries (linear scan)
 * @since 1.0.0
 * @see TraceStorage
 * @see SpanData
 * @see TraceInfo
 * @see TraceQuery
 * @doc.type class
 * @doc.purpose In-memory trace storage implementation for testing (not production)
 * @doc.layer observability
 * @doc.pattern Adapter (TraceStorage implementation)
 */
public class MockTraceStorage implements TraceStorage {

    // Thread-safe storage: traceId -> List<SpanData>
    private final Map<String, List<SpanData>> spansByTrace = new ConcurrentHashMap<>();

    /**
     * Stores a single span asynchronously.
     * <p>
     * The span is stored in memory grouped by traceId.
     * </p>
     *
     * @param span the span to store (must not be null)
     * @return a Promise that completes immediately (in-memory operation)
     * @throws IllegalArgumentException if span is null
     */
    @Override
    public Promise<Void> storeSpan(SpanData span) {
        if (span == null) {
            return Promise.ofException(new IllegalArgumentException("span must not be null"));
        }

        spansByTrace.computeIfAbsent(span.traceId(), k -> new ArrayList<>()).add(span);
        return Promise.complete();
    }

    /**
     * Stores multiple spans asynchronously in a batch.
     *
     * @param spans the spans to store (must not be null or empty)
     * @return a Promise that completes immediately (in-memory operation)
     * @throws IllegalArgumentException if spans is null or empty
     */
    @Override
    public Promise<Void> storeSpans(List<SpanData> spans) {
        if (spans == null || spans.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("spans must not be null or empty"));
        }

        for (SpanData span : spans) {
            spansByTrace.computeIfAbsent(span.traceId(), k -> new ArrayList<>()).add(span);
        }
        return Promise.complete();
    }

    /**
     * Queries traces matching the given criteria.
     * <p>
     * This implementation performs linear scans and in-memory filtering.
     * All optional filters are applied with AND logic.
     * </p>
     *
     * @param query the query criteria (must not be null)
     * @return a Promise with list of matching traces (empty list if no matches)
     * @throws IllegalArgumentException if query is null
     */
    @Override
    public Promise<List<TraceInfo>> queryTraces(TraceQuery query) {
        if (query == null) {
            return Promise.ofException(new IllegalArgumentException("query must not be null"));
        }

        // Build TraceInfo for each traceId
        List<TraceInfo> allTraces = spansByTrace.entrySet().stream()
            .map(entry -> buildTraceInfo(entry.getKey(), entry.getValue()))
            .filter(trace -> matchesQuery(trace, query))
            .sorted(Comparator.comparing(TraceInfo::startTime).reversed()) // Most recent first
            .skip(query.getOffset())
            .limit(query.getLimit())
            .toList();

        return Promise.of(allTraces);
    }

    /**
     * Gets aggregated statistics for traces matching the query.
     *
     * @param query the query criteria (must not be null)
     * @return a Promise with aggregated statistics
     * @throws IllegalArgumentException if query is null
     */
    @Override
    public Promise<TraceStatistics> getStatistics(TraceQuery query) {
        if (query == null) {
            return Promise.ofException(new IllegalArgumentException("query must not be null"));
        }

        // Get matching traces (without pagination)
        List<TraceInfo> matchingTraces = spansByTrace.entrySet().stream()
            .map(entry -> buildTraceInfo(entry.getKey(), entry.getValue()))
            .filter(trace -> matchesQuery(trace, query))
            .toList();

        if (matchingTraces.isEmpty()) {
            return Promise.of(TraceStatistics.empty());
        }

        // Calculate statistics
        int totalTraces = matchingTraces.size();
        int totalSpans = matchingTraces.stream().mapToInt(TraceInfo::spanCount).sum();
        int errorCount = (int) matchingTraces.stream().filter(TraceInfo::hasErrors).count();

        List<Long> durations = matchingTraces.stream()
            .map(TraceInfo::durationMs)
            .sorted()
            .toList();

        long avgDurationMs = (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long minDurationMs = durations.get(0);
        long maxDurationMs = durations.get(durations.size() - 1);
        long p50DurationMs = percentile(durations, 0.50);
        long p95DurationMs = percentile(durations, 0.95);
        long p99DurationMs = percentile(durations, 0.99);

        // Find most common service and operation
        String mostCommonService = matchingTraces.stream()
            .map(TraceInfo::serviceName)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        // Most common operation from all spans
        String mostCommonOperation = matchingTraces.stream()
            .flatMap(trace -> trace.spans().stream())
            .map(SpanData::operationName)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(op -> op, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        TraceStatistics stats = TraceStatistics.builder()
            .withTotalTraces(totalTraces)
            .withTotalSpans(totalSpans)
            .withErrorCount(errorCount)
            .withAvgDurationMs(avgDurationMs)
            .withMinDurationMs(minDurationMs)
            .withMaxDurationMs(maxDurationMs)
            .withP50DurationMs(p50DurationMs)
            .withP95DurationMs(p95DurationMs)
            .withP99DurationMs(p99DurationMs)
            .withMostCommonService(mostCommonService)
            .withMostCommonOperation(mostCommonOperation)
            .build();

        return Promise.of(stats);
    }

    /**
     * Always returns true (in-memory storage is always healthy).
     *
     * @return a Promise with true
     */
    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.of(true);
    }

    /**
     * Clears all stored traces (useful for test cleanup).
     *
     * @return a Promise that completes immediately
     */
    @Override
    public Promise<Void> close() {
        spansByTrace.clear();
        return Promise.complete();
    }

    /**
     * Gets the total number of traces stored.
     *
     * @return number of unique trace IDs
     */
    public int getTraceCount() {
        return spansByTrace.size();
    }

    /**
     * Gets the total number of spans stored across all traces.
     *
     * @return total span count
     */
    public int getTotalSpanCount() {
        return spansByTrace.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    /**
     * Clears all stored data (useful for test cleanup).
     */
    public void clear() {
        spansByTrace.clear();
    }

    /**
     * Builds a TraceInfo from a list of spans.
     */
    private TraceInfo buildTraceInfo(String traceId, List<SpanData> spans) {
        return TraceInfo.builder()
            .withTraceId(traceId)
            .addSpans(spans)
            .build();
    }

    /**
     * Checks if a trace matches the query criteria.
     */
    private boolean matchesQuery(TraceInfo trace, TraceQuery query) {
        // Service name filter
        if (query.getServiceName().isPresent()) {
            String serviceName = query.getServiceName().get();
            boolean hasService = trace.spans().stream()
                .anyMatch(span -> serviceName.equals(span.serviceName()));
            if (!hasService) {
                return false;
            }
        }

        // Operation name filter
        if (query.getOperationName().isPresent()) {
            String operationName = query.getOperationName().get();
            boolean hasOperation = trace.spans().stream()
                .anyMatch(span -> operationName.equals(span.operationName()));
            if (!hasOperation) {
                return false;
            }
        }

        // Status filter
        if (query.getStatus().isPresent()) {
            String status = query.getStatus().get();
            if (!status.equals(trace.status())) {
                return false;
            }
        }

        // Duration filters
        if (query.getMinDurationMs().isPresent()) {
            if (trace.durationMs() < query.getMinDurationMs().get()) {
                return false;
            }
        }
        if (query.getMaxDurationMs().isPresent()) {
            if (trace.durationMs() > query.getMaxDurationMs().get()) {
                return false;
            }
        }

        // Time range filters
        if (query.getStartTime().isPresent()) {
            if (trace.startTime().isBefore(query.getStartTime().get())) {
                return false;
            }
        }
        if (query.getEndTime().isPresent()) {
            if (trace.endTime().isAfter(query.getEndTime().get())) {
                return false;
            }
        }

        // Tag filters (all tags must match at least one span)
        if (query.getTags().isPresent()) {
            Map<String, String> requiredTags = query.getTags().get();
            for (Map.Entry<String, String> tag : requiredTags.entrySet()) {
                boolean tagMatches = trace.spans().stream()
                    .anyMatch(span -> tag.getValue().equals(span.getTag(tag.getKey())));
                if (!tagMatches) {
                    return false;
                }
            }
        }

        // Span count filters
        if (query.getMinSpanCount().isPresent()) {
            if (trace.spanCount() < query.getMinSpanCount().get()) {
                return false;
            }
        }
        if (query.getMaxSpanCount().isPresent()) {
            if (trace.spanCount() > query.getMaxSpanCount().get()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculates percentile from sorted list of values.
     */
    private long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }
}
