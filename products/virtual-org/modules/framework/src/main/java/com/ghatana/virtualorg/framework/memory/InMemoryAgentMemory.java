package com.ghatana.virtualorg.framework.memory;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory implementation of AgentMemory.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a simple in-memory agent memory for development and testing. Does
 * not support vector similarity search.
 *
 * <p>
 * <b>Limitations</b><br>
 * - No persistence - No vector similarity (uses text contains instead) -
 * Limited scalability
 *
 * @doc.type class
 * @doc.purpose In-memory agent memory
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryAgentMemory implements AgentMemory {

    private final ConcurrentHashMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();
    private final int maxEntriesPerAgent;

    public InMemoryAgentMemory() {
        this(1000);
    }

    public InMemoryAgentMemory(int maxEntriesPerAgent) {
        this.maxEntriesPerAgent = maxEntriesPerAgent;
    }

    @Override
    public Promise<MemoryEntry> store(MemoryEntry entry) {
        // Check if we need to evict old entries
        long agentCount = entries.values().stream()
                .filter(e -> e.agentId().equals(entry.agentId()))
                .count();

        if (agentCount >= maxEntriesPerAgent) {
            evictOldest(entry.agentId());
        }

        entries.put(entry.id(), entry);
        return Promise.of(entry);
    }

    @Override
    public Promise<List<MemoryEntry>> storeAll(List<MemoryEntry> toStore) {
        List<MemoryEntry> stored = new ArrayList<>();
        for (MemoryEntry entry : toStore) {
            entries.put(entry.id(), entry);
            stored.add(entry);
        }
        return Promise.of(stored);
    }

    @Override
    public Promise<MemoryEntry> getById(String id) {
        MemoryEntry entry = entries.get(id);
        if (entry != null) {
            // Update access info
            MemoryEntry accessed = entry.markAccessed();
            entries.put(id, accessed);
            return Promise.of(accessed);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<List<MemoryEntry>> getRecent(String agentId, int limit) {
        return Promise.of(entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .limit(limit)
                .toList());
    }

    @Override
    public Promise<List<MemoryEntry>> getByType(String agentId, MemoryType type, int limit) {
        return Promise.of(entries.values().stream()
                .filter(e -> e.agentId().equals(agentId) && e.type() == type)
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .limit(limit)
                .toList());
    }

    @Override
    public Promise<List<MemoryEntry>> searchSimilar(String agentId, String query, int limit) {
        return searchSimilar(agentId, query, limit, 0.0);
    }

    @Override
    public Promise<List<MemoryEntry>> searchSimilar(String agentId, String query,
            int limit, double minSimilarity) {
        // Simple text-based search (no vector similarity)
        String lowerQuery = query.toLowerCase();

        return Promise.of(entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .filter(e -> {
                    String content = e.content() != null ? e.content().toLowerCase() : "";
                    String summary = e.summary() != null ? e.summary().toLowerCase() : "";
                    return content.contains(lowerQuery) || summary.contains(lowerQuery);
                })
                .sorted(Comparator.comparing(MemoryEntry::importance).reversed())
                .limit(limit)
                .toList());
    }

    @Override
    public Promise<List<MemoryEntry>> search(MemoryQuery query) {
        Stream<MemoryEntry> stream = entries.values().stream();

        // Apply filters
        if (query.agentId() != null) {
            stream = stream.filter(e -> e.agentId().equals(query.agentId()));
        }

        if (query.types() != null && !query.types().isEmpty()) {
            stream = stream.filter(e -> query.types().contains(e.type()));
        }

        if (query.textQuery() != null) {
            String lowerQuery = query.textQuery().toLowerCase();
            stream = stream.filter(e -> {
                String content = e.content() != null ? e.content().toLowerCase() : "";
                return content.contains(lowerQuery);
            });
        }

        if (query.startTime() != null) {
            stream = stream.filter(e -> !e.createdAt().isBefore(query.startTime()));
        }

        if (query.endTime() != null) {
            stream = stream.filter(e -> !e.createdAt().isAfter(query.endTime()));
        }

        if (query.minImportance() > 0) {
            stream = stream.filter(e -> e.importance() >= query.minImportance());
        }

        if (query.sessionId() != null) {
            stream = stream.filter(e -> query.sessionId().equals(e.sessionId()));
        }

        if (query.taskId() != null) {
            stream = stream.filter(e -> query.taskId().equals(e.taskId()));
        }

        // Apply sorting
        Comparator<MemoryEntry> comparator = switch (query.sortBy()) {
            case CREATED_AT ->
                Comparator.comparing(MemoryEntry::createdAt);
            case ACCESSED_AT ->
                Comparator.comparing(MemoryEntry::accessedAt);
            case IMPORTANCE ->
                Comparator.comparing(MemoryEntry::importance);
            case RELEVANCE ->
                Comparator.comparing(MemoryEntry::importance); // fallback
        };

        if (query.sortOrder() == MemoryQuery.SortOrder.DESCENDING) {
            comparator = comparator.reversed();
        }

        stream = stream.sorted(comparator);

        // Apply pagination
        if (query.offset() > 0) {
            stream = stream.skip(query.offset());
        }
        stream = stream.limit(query.limit());

        return Promise.of(stream.toList());
    }

    @Override
    public Promise<MemoryEntry> update(MemoryEntry entry) {
        if (!entries.containsKey(entry.id())) {
            return Promise.ofException(
                    new IllegalArgumentException("Memory entry not found: " + entry.id()));
        }
        entries.put(entry.id(), entry);
        return Promise.of(entry);
    }

    @Override
    public Promise<Boolean> delete(String id) {
        MemoryEntry removed = entries.remove(id);
        return Promise.of(removed != null);
    }

    @Override
    public Promise<Integer> deleteAllForAgent(String agentId) {
        List<String> toRemove = entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .map(MemoryEntry::id)
                .toList();

        toRemove.forEach(entries::remove);
        return Promise.of(toRemove.size());
    }

    @Override
    public Promise<Integer> consolidate(String agentId, int olderThanDays) {
        Instant cutoff = Instant.now().minusSeconds(olderThanDays * 24L * 60 * 60);

        List<String> toRemove = entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .filter(e -> e.createdAt().isBefore(cutoff))
                .filter(e -> e.type() == MemoryType.WORKING) // Only consolidate working memory
                .map(MemoryEntry::id)
                .toList();

        toRemove.forEach(entries::remove);
        return Promise.of(toRemove.size());
    }

    private void evictOldest(String agentId) {
        // Remove oldest 10% of entries for this agent
        List<String> toRemove = entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .sorted(Comparator.comparing(MemoryEntry::createdAt))
                .limit(maxEntriesPerAgent / 10)
                .map(MemoryEntry::id)
                .toList();

        toRemove.forEach(entries::remove);
    }

    /**
     * Gets the total number of entries.
     *
     * @return Total entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Gets the number of entries for a specific agent.
     *
     * @param agentId Agent ID
     * @return Entry count for agent
     */
    public long sizeForAgent(String agentId) {
        return entries.values().stream()
                .filter(e -> e.agentId().equals(agentId))
                .count();
    }

    /**
     * Clears all entries.
     */
    public void clear() {
        entries.clear();
    }
}
