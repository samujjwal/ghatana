package com.ghatana.virtualorg.memory.impl;

import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.memory.MemoryEntry;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AgentMemory for development and testing.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link AgentMemory} using in-memory data structures.
 * Lightweight alternative to {@link PgVectorAgentMemory} for testing environments.
 *
 * <p><b>Architecture Role</b><br>
 * Test/development memory adapter. Uses:
 * - ConcurrentLinkedDeque for LRU short-term memory
 * - ConcurrentHashMap for long-term memory storage
 * - Full-text search (simple substring matching, not semantic)
 *
 * <p><b>Features</b><br>
 * - LRU cache for short-term memory (configurable size)
 * - Full-text search for long-term memory (no vector embeddings)
 * - Thread-safe concurrent operations
 * - Configurable size limits
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * InMemoryAgentMemory memory = new InMemoryAgentMemory(
 *     eventloop,
 *     100,    // short-term size
 *     10000   // long-term max items
 * );
 * 
 * memory.storeTaskResponse(task, response).getResult();
 * List<MemoryEntry> memories = memory.searchMemory("transaction", 5).getResult();
 * }</pre>
 *
 * <p><b>Limitations</b><br>
 * This implementation does NOT use vector embeddings. For production with
 * semantic search, use {@link PgVectorAgentMemory}.
 *
 * @doc.type class
 * @doc.purpose In-memory adapter for agent memory testing and development
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemoryAgentMemory implements AgentMemory {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentMemory.class);

    private final Eventloop eventloop;
    private final int shortTermSize;
    private final int longTermMaxItems;

    // Short-term memory: Recent tasks (FIFO queue)
    private final Deque<MemoryEntry> shortTermMemory;

    // Long-term memory: All tasks (for search)
    private final Map<String, MemoryEntry> longTermMemory;

    public InMemoryAgentMemory(
            @NotNull Eventloop eventloop,
            int shortTermSize,
            int longTermMaxItems) {

        this.eventloop = eventloop;
        this.shortTermSize = shortTermSize;
        this.longTermMaxItems = longTermMaxItems;

        this.shortTermMemory = new ConcurrentLinkedDeque<>();
        this.longTermMemory = new ConcurrentHashMap<>();

        log.info("Initialized InMemoryAgentMemory: shortTerm={}, longTerm={}",
                shortTermSize, longTermMaxItems);
    }

    @Override
    @NotNull
    public Promise<String> retrieveContext(@NotNull TaskProto task) {
        return Promise.ofBlocking(eventloop, () -> {
            StringBuilder context = new StringBuilder();

            // Add recent short-term memories
            if (!shortTermMemory.isEmpty()) {
                context.append("## Recent Task History\n\n");

                int count = 0;
                for (MemoryEntry entry : shortTermMemory) {
                    if (count >= 5) break; // Limit to 5 most recent

                    context.append(String.format("- %s: %s (%s)\n",
                            entry.taskType(),
                            truncate(entry.content(), 100),
                            entry.success() ? "✓" : "✗"
                    ));
                    count++;
                }
                context.append("\n");
            }

            // Search long-term memory for similar tasks
            List<MemoryEntry> similarTasks = searchSimilar(task.getDescription(), 3);

            if (!similarTasks.isEmpty()) {
                context.append("## Similar Past Tasks\n\n");

                for (MemoryEntry entry : similarTasks) {
                    context.append(String.format("- %s (score: %.2f): %s\n",
                            entry.taskType(),
                            entry.score(),
                            truncate(entry.content(), 150)
                    ));
                }
                context.append("\n");
            }

            log.debug("Retrieved context: shortTerm={}, similar={}",
                    Math.min(5, shortTermMemory.size()),
                    similarTasks.size());

            return context.toString();
        });
    }

    @Override
    @NotNull
    public Promise<Void> store(@NotNull TaskProto task, @NotNull TaskResponseProto response) {
        return Promise.ofBlocking(eventloop, () -> {
            String id = UUID.randomUUID().toString();

            // Build memory content
            String content = buildMemoryContent(task, response);

            MemoryEntry entry = new MemoryEntry(
                    id,
                    content,
                    1.0f, // Full score for exact match
                    task.getType().name(),
                    response.getSuccess(),
                    Instant.now(),
                    Map.of(
                            "task_id", task.getTaskId(),
                            "task_type", task.getType().name(),
                            "priority", task.getPriority().name()
                    )
            );

            // Add to short-term memory
            shortTermMemory.addFirst(entry);
            if (shortTermMemory.size() > shortTermSize) {
                shortTermMemory.removeLast();
            }

            // Add to long-term memory
            longTermMemory.put(id, entry);

            // Evict old entries if needed
            if (longTermMemory.size() > longTermMaxItems) {
                evictOldest();
            }

            log.debug("Stored memory: id={}, taskId={}", id, task.getTaskId());

            return null;
        });
    }

    @Override
    @NotNull
    public Promise<List<MemoryEntry>> search(@NotNull String query, int limit, float minScore) {
        return Promise.ofBlocking(eventloop, () -> {
            List<MemoryEntry> results = searchSimilar(query, limit);

            // Filter by minimum score
            return results.stream()
                    .filter(entry -> entry.score() >= minScore)
                    .limit(limit)
                    .collect(Collectors.toList());
        });
    }

    @Override
    @NotNull
    public Promise<Void> clearShortTerm() {
        return Promise.ofBlocking(eventloop, () -> {
            shortTermMemory.clear();
            log.info("Cleared short-term memory");
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> clearAll() {
        return Promise.ofBlocking(eventloop, () -> {
            shortTermMemory.clear();
            longTermMemory.clear();
            log.info("Cleared all memory");
            return null;
        });
    }

    // =============================
    // Helper methods
    // =============================

    private List<MemoryEntry> searchSimilar(String query, int limit) {
        String queryLower = query.toLowerCase();

        return longTermMemory.values().stream()
                .map(entry -> {
                    // Simple text similarity (Jaccard-like)
                    float score = calculateSimilarity(queryLower, entry.content().toLowerCase());
                    return new MemoryEntry(
                            entry.id(),
                            entry.content(),
                            score,
                            entry.taskType(),
                            entry.success(),
                            entry.timestamp(),
                            entry.metadata()
                    );
                })
                .filter(entry -> entry.score() > 0.1f) // Minimum similarity threshold
                .sorted(Comparator.comparing(MemoryEntry::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private float calculateSimilarity(String query, String content) {
        // Simple word overlap similarity
        Set<String> queryWords = new HashSet<>(Arrays.asList(query.split("\\s+")));
        Set<String> contentWords = new HashSet<>(Arrays.asList(content.split("\\s+")));

        Set<String> intersection = new HashSet<>(queryWords);
        intersection.retainAll(contentWords);

        Set<String> union = new HashSet<>(queryWords);
        union.addAll(contentWords);

        if (union.isEmpty()) return 0.0f;

        return (float) intersection.size() / union.size();
    }

    private String buildMemoryContent(TaskProto task, TaskResponseProto response) {
        StringBuilder content = new StringBuilder();

        content.append("Task: ").append(task.getTitle()).append("\n");
        content.append("Description: ").append(task.getDescription()).append("\n");
        content.append("Type: ").append(task.getType().name()).append("\n");
        content.append("Result: ").append(response.getSuccess() ? "Success" : "Failed").append("\n");

        if (!response.getReasoning().isEmpty()) {
            content.append("Reasoning: ").append(truncate(response.getReasoning(), 500)).append("\n");
        }

        if (!response.getToolCallsList().isEmpty()) {
            content.append("Tools used: ");
            content.append(response.getToolCallsList().stream()
                    .map(tc -> tc.getToolName())
                    .collect(Collectors.joining(", ")));
            content.append("\n");
        }

        return content.toString();
    }

    private void evictOldest() {
        // Find and remove oldest entry
        longTermMemory.entrySet().stream()
                .min(Comparator.comparing(e -> e.getValue().timestamp()))
                .ifPresent(oldest -> {
                    longTermMemory.remove(oldest.getKey());
                    log.debug("Evicted oldest memory entry: {}", oldest.getKey());
                });
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
