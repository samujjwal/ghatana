package com.ghatana.virtualorg.framework.hitl;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory implementation of AuditTrail.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a simple in-memory audit trail for development and testing. Not
 * suitable for production use where persistence is required.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * This implementation is thread-safe using ConcurrentHashMap.
 *
 * @doc.type class
 * @doc.purpose In-memory audit trail implementation
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryAuditTrail implements AuditTrail {

    private final ConcurrentHashMap<String, AuditEntry> entries = new ConcurrentHashMap<>();
    private final int maxEntries;

    public InMemoryAuditTrail() {
        this(10_000);
    }

    public InMemoryAuditTrail(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    public Promise<AuditEntry> recordToolExecution(String agentId, String toolName,
            Map<String, Object> input,
            Map<String, Object> output) {
        AuditEntry entry = AuditEntry.toolExecution(agentId, toolName, input, output);
        return storeEntry(entry);
    }

    @Override
    public Promise<AuditEntry> recordDecision(String agentId, String decision,
            String reasoning, double confidence) {
        AuditEntry entry = AuditEntry.decision(agentId, decision, reasoning, confidence);
        return storeEntry(entry);
    }

    @Override
    public Promise<AuditEntry> recordApproval(String agentId, String requestId,
            String action, boolean approved,
            String reviewer, String comments) {
        AuditEntry entry = AuditEntry.approval(agentId, requestId, action,
                approved, reviewer, comments);
        return storeEntry(entry);
    }

    @Override
    public Promise<AuditEntry> recordStateChange(String agentId, String oldState,
            String newState, String reason) {
        AuditEntry entry = AuditEntry.stateChange(agentId, oldState, newState, reason);
        return storeEntry(entry);
    }

    @Override
    public Promise<AuditEntry> recordError(String agentId, String errorType,
            String errorMessage, Map<String, Object> context) {
        AuditEntry entry = AuditEntry.error(agentId, errorType, errorMessage, context);
        return storeEntry(entry);
    }

    @Override
    public Promise<AuditEntry> recordEvent(String agentId, String eventType,
            Map<String, Object> data) {
        AuditEntry entry = AuditEntry.builder()
                .agentId(agentId)
                .eventType(eventType)
                .data(data)
                .build();
        return storeEntry(entry);
    }

    @Override
    public Promise<List<AuditEntry>> getEntriesForAgent(String agentId) {
        return query(AuditQuery.builder()
                .agentId(agentId)
                .build());
    }

    @Override
    public Promise<List<AuditEntry>> getEntriesByTimeRange(Instant startTime, Instant endTime) {
        return query(AuditQuery.builder()
                .timeRange(startTime, endTime)
                .build());
    }

    @Override
    public Promise<List<AuditEntry>> getEntriesByType(String eventType) {
        return query(AuditQuery.builder()
                .eventType(eventType)
                .build());
    }

    @Override
    public Promise<List<AuditEntry>> query(AuditQuery queryParams) {
        Stream<AuditEntry> stream = entries.values().stream()
                .filter(queryParams::matches);

        // Sort
        Comparator<AuditEntry> comparator = Comparator.comparing(AuditEntry::timestamp);
        if (queryParams.sortOrder() == AuditQuery.SortOrder.DESCENDING) {
            comparator = comparator.reversed();
        }
        stream = stream.sorted(comparator);

        // Pagination
        if (queryParams.offset() > 0) {
            stream = stream.skip(queryParams.offset());
        }
        if (queryParams.limit() > 0) {
            stream = stream.limit(queryParams.limit());
        }

        return Promise.of(stream.toList());
    }

    private Promise<AuditEntry> storeEntry(AuditEntry entry) {
        // Evict oldest entries if at capacity
        if (entries.size() >= maxEntries) {
            evictOldest();
        }

        entries.put(entry.id(), entry);
        return Promise.of(entry);
    }

    private void evictOldest() {
        // Remove oldest 10% of entries
        int toRemove = maxEntries / 10;
        entries.values().stream()
                .sorted(Comparator.comparing(AuditEntry::timestamp))
                .limit(toRemove)
                .forEach(e -> entries.remove(e.id()));
    }

    /**
     * Gets the total number of entries in the audit trail.
     *
     * @return Number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Clears all entries from the audit trail.
     */
    public void clear() {
        entries.clear();
    }
}
