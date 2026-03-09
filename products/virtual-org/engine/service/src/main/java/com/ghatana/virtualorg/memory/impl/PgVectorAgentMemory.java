package com.ghatana.virtualorg.memory.impl;

import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.memory.MemoryEntry;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL + pgvector implementation of AgentMemory.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link AgentMemory} using PostgreSQL with pgvector extension
 * for semantic memory search via vector embeddings.
 *
 * <p><b>Architecture Role</b><br>
 * Persistent long-term memory adapter. Uses:
 * - pgvector extension for cosine similarity search
 * - JSONB for metadata storage
 * - IVFFlat index for fast similarity retrieval
 *
 * <p><b>Features</b><br>
 * - Vector embeddings for semantic search (1536 dimensions, OpenAI ada-002)
 * - Cosine similarity search (range: 0.0 to 1.0)
 * - Persistent storage (scalable to millions of memories)
 * - Metadata filtering via JSONB queries
 *
 * <p><b>Database Schema</b><br>
 * <pre>
 * CREATE EXTENSION IF NOT EXISTS vector;
 *
 * CREATE TABLE agent_memory (
 *     id UUID PRIMARY KEY,
 *     agent_id VARCHAR(255) NOT NULL,
 *     content TEXT NOT NULL,
 *     embedding vector(1536),  -- OpenAI ada-002 dimension
 *     task_type VARCHAR(100),
 *     success BOOLEAN,
 *     created_at TIMESTAMP NOT NULL,
 *     metadata JSONB
 * );
 *
 * CREATE INDEX ON agent_memory USING ivfflat (embedding vector_cosine_ops);
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PgVectorAgentMemory memory = new PgVectorAgentMemory(
 *     "agent-123",
 *     dataSource,
 *     embeddingClient,
 *     eventloop,
 *     10,    // top K results
 *     0.7f   // similarity threshold
 * );
 * 
 * List<MemoryEntry> memories = memory.searchMemory("transaction processing", 5).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL pgvector adapter for persistent semantic memory
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PgVectorAgentMemory implements AgentMemory {

    private static final Logger log = LoggerFactory.getLogger(PgVectorAgentMemory.class);

    private final String agentId;
    private final DataSource dataSource;
    private final LLMClient embeddingClient;
    private final Eventloop eventloop;
    private final int shortTermSize;

    public PgVectorAgentMemory(
            @NotNull String agentId,
            @NotNull DataSource dataSource,
            @NotNull LLMClient embeddingClient,
            @NotNull Eventloop eventloop,
            int shortTermSize) {

        this.agentId = agentId;
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
        this.eventloop = eventloop;
        this.shortTermSize = shortTermSize;

        log.info("Initialized PgVectorAgentMemory for agent: {}", agentId);
    }

    @Override
    @NotNull
    public Promise<String> retrieveContext(@NotNull TaskProto task) {
        // Get recent memories first (blocking operation)
        return Promise.ofBlocking(eventloop, () -> {
            StringBuilder context = new StringBuilder();

            // Get recent memories
            List<MemoryEntry> recentMemories = getRecentMemories(shortTermSize);

            if (!recentMemories.isEmpty()) {
                context.append("## Recent Task History\n\n");
                for (MemoryEntry entry : recentMemories) {
                    context.append(String.format("- %s: %s (%s)\n",
                            entry.taskType(),
                            truncate(entry.content(), 100),
                            entry.success() ? "✓" : "✗"
                    ));
                }
                context.append("\n");
            }

            return context;
        }).then(context -> 
            // Chain async search for similar memories
            searchSimilar(task.getDescription(), 3)
                .map(similarMemories -> {
                    if (!similarMemories.isEmpty()) {
                        context.append("## Similar Past Tasks\n\n");
                        for (MemoryEntry entry : similarMemories) {
                            context.append(String.format("- %s (similarity: %.2f): %s\n",
                                    entry.taskType(),
                                    entry.score(),
                                    truncate(entry.content(), 150)
                            ));
                        }
                        context.append("\n");
                    }

                    log.debug("Retrieved context for agent {}: similar={}",
                            agentId, similarMemories.size());

                    return context.toString();
                })
        );
    }

    @Override
    @NotNull
    public Promise<Void> store(@NotNull TaskProto task, @NotNull TaskResponseProto response) {
        String content = buildMemoryContent(task, response);
        
        // Use async embedding, then store in DB
        return embeddingClient.embed(content)
            .then(embedding -> Promise.ofBlocking(eventloop, () -> {
                // Store in database
                String sql = """
                        INSERT INTO agent_memory (id, agent_id, content, embedding, task_type, success, created_at, metadata)
                        VALUES (?, ?, ?, ?::vector, ?, ?, ?, ?::jsonb)
                        """;

                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setObject(1, UUID.randomUUID());
                    stmt.setString(2, agentId);
                    stmt.setString(3, content);
                    stmt.setString(4, vectorToString(embedding));
                    stmt.setString(5, task.getType().name());
                    stmt.setBoolean(6, response.getSuccess());
                    stmt.setTimestamp(7, Timestamp.from(Instant.now()));
                    stmt.setString(8, buildMetadataJson(task));

                    stmt.executeUpdate();

                    log.debug("Stored memory for agent {}: taskId={}", agentId, task.getTaskId());
                }

                return null;
            }));
    }

    @Override
    @NotNull
    public Promise<List<MemoryEntry>> search(@NotNull String query, int limit, float minScore) {
        // Use async embedding, then search DB
        return embeddingClient.embed(query)
            .then(queryEmbedding -> Promise.ofBlocking(eventloop, () -> {
                // Search using cosine similarity
                String sql = """
                        SELECT id, content, task_type, success, created_at, metadata,
                               1 - (embedding <=> ?::vector) as similarity
                        FROM agent_memory
                        WHERE agent_id = ? AND 1 - (embedding <=> ?::vector) >= ?
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """;

                List<MemoryEntry> results = new ArrayList<>();
                String vectorStr = vectorToString(queryEmbedding);

                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, vectorStr);
                    stmt.setString(2, agentId);
                    stmt.setString(3, vectorStr);
                    stmt.setFloat(4, minScore);
                    stmt.setString(5, vectorStr);
                    stmt.setInt(6, limit);

                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        results.add(new MemoryEntry(
                                rs.getObject("id", UUID.class).toString(),
                                rs.getString("content"),
                                rs.getFloat("similarity"),
                                rs.getString("task_type"),
                                rs.getBoolean("success"),
                                rs.getTimestamp("created_at").toInstant(),
                                parseMetadata(rs.getString("metadata"))
                        ));
                    }
                }

                return results;
            }));
    }

    @Override
    @NotNull
    public Promise<Void> clearShortTerm() {
        // For pgvector, this would delete recent entries
        // For now, we just log
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Clear short-term not implemented for PgVector");
            return null;
        });
    }

    @Override
    @NotNull
    public Promise<Void> clearAll() {
        return Promise.ofBlocking(eventloop, () -> {
            String sql = "DELETE FROM agent_memory WHERE agent_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, agentId);
                int deleted = stmt.executeUpdate();

                log.info("Cleared all memory for agent {}: deleted={}", agentId, deleted);
            }

            return null;
        });
    }

    // =============================
    // Helper methods
    // =============================

    private List<MemoryEntry> getRecentMemories(int limit) throws SQLException {
        String sql = """
                SELECT id, content, task_type, success, created_at, metadata
                FROM agent_memory
                WHERE agent_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        List<MemoryEntry> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, agentId);
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                results.add(new MemoryEntry(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("content"),
                        1.0f, // Recent memories have full score
                        rs.getString("task_type"),
                        rs.getBoolean("success"),
                        rs.getTimestamp("created_at").toInstant(),
                        parseMetadata(rs.getString("metadata"))
                ));
            }
        }

        return results;
    }

    private Promise<List<MemoryEntry>> searchSimilar(String query, int limit) {
        // Return Promise instead of blocking - search() already returns Promise
        return search(query, limit, 0.7f)
            .whenException(e -> log.error("Error searching similar memories", e))
            .map(result -> result != null ? result : List.of());
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
            content.append("Tools: ");
            content.append(response.getToolCallsList().stream()
                    .map(tc -> tc.getToolName())
                    .collect(Collectors.joining(", ")));
            content.append("\n");
        }

        return content.toString();
    }

    private String vectorToString(float[] vector) {
        // Manual iteration since Arrays.stream(float[]) doesn't exist
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildMetadataJson(TaskProto task) {
        return String.format("""
                {
                    "task_id": "%s",
                    "task_type": "%s",
                    "priority": "%s"
                }
                """, task.getTaskId(), task.getType().name(), task.getPriority().name());
    }

    private Map<String, String> parseMetadata(String json) {
        // Simple JSON parsing (use Jackson in production)
        Map<String, String> metadata = new HashMap<>();
        if (json != null && !json.isEmpty()) {
            // Parse basic JSON (simplified)
            metadata.put("raw", json);
        }
        return metadata;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
