package com.ghatana.virtualorg.framework.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing a memory entry.
 *
 * <p><b>Purpose</b><br>
 * Represents a single entry in agent memory. Includes metadata for
 * categorization, search, retrieval, and importance scoring.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MemoryEntry entry = MemoryEntry.builder()
 *     .agentId("agent-1")
 *     .type(MemoryType.EPISODIC)
 *     .content("Successfully fixed bug #123")
 *     .importance(0.8)
 *     .metadata(Map.of("bug_id", "123"))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Memory entry value object
 * @doc.layer product
 * @doc.pattern Value Object
 *
 */
public final class MemoryEntry {

    private final String id;
    private final String agentId;
    private final MemoryType type;
    private final String content;
    private final String summary;
    private final String context;
    private final double importance;
    private final Instant createdAt;
    private final Instant accessedAt;
    private final String sessionId;
    private final String taskId;
    private final Map<String, String> metadata;
    private final float[] embedding;

    private MemoryEntry(Builder builder) {
        this.id = builder.id;
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.type = builder.type != null ? builder.type : MemoryType.EPISODIC;
        this.content = Objects.requireNonNull(builder.content, "content cannot be null");
        this.summary = builder.summary;
        this.context = builder.context;
        this.importance = builder.importance;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.accessedAt = builder.accessedAt != null ? builder.accessedAt : this.createdAt;
        this.sessionId = builder.sessionId;
        this.taskId = builder.taskId;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        this.embedding = builder.embedding;
    }

    // ========== Record-style accessors (for compatibility) ==========

    public String id() {
        return id;
    }

    public String agentId() {
        return agentId;
    }

    public MemoryType type() {
        return type;
    }

    public String content() {
        return content;
    }

    public String summary() {
        return summary;
    }

    public String context() {
        return context;
    }

    public double importance() {
        return importance;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant accessedAt() {
        return accessedAt;
    }

    public String sessionId() {
        return sessionId;
    }

    public String taskId() {
        return taskId;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public float[] embedding() {
        return embedding;
    }

    // ========== Getter-style accessors (for compatibility) ==========

    public String getId() {
        return id;
    }

    public String getAgentId() {
        return agentId;
    }

    public MemoryType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public String getContext() {
        return context;
    }

    public double getImportance() {
        return importance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    // ========== Legacy compatibility (for existing code) ==========

    /**
     * Gets the category (alias for context).
     */
    public String getCategory() {
        return context;
    }

    /**
     * Gets the title (alias for summary).
     */
    public String getTitle() {
        return summary;
    }

    /**
     * Gets the actor (alias for agentId).
     */
    public String getActor() {
        return agentId;
    }

    /**
     * Gets the tags (from metadata).
     */
    public String getTags() {
        return metadata.getOrDefault("tags", "");
    }

    // ========== Methods ==========

    /**
     * Creates a new entry with updated access time.
     *
     * @return A new entry with current access time
     */
    public MemoryEntry markAccessed() {
        return new Builder(this)
                .accessedAt(Instant.now())
                .build();
    }

    /**
     * Creates a new entry with updated importance.
     *
     * @param newImportance The new importance value
     * @return A new entry with updated importance
     */
    public MemoryEntry withImportance(double newImportance) {
        return new Builder(this)
                .importance(newImportance)
                .build();
    }

    /**
     * Creates a new entry with embedding.
     *
     * @param embedding The embedding vector
     * @return A new entry with embedding
     */
    public MemoryEntry withEmbedding(float[] embedding) {
        return new Builder(this)
                .embedding(embedding)
                .build();
    }

    // ========== Factory Methods ==========

    /**
     * Creates a simple memory entry.
     */
    public static MemoryEntry of(String agentId, String content) {
        return builder()
                .agentId(agentId)
                .content(content)
                .build();
    }

    /**
     * Creates a memory entry with type.
     */
    public static MemoryEntry of(String agentId, MemoryType type, String content) {
        return builder()
                .agentId(agentId)
                .type(type)
                .content(content)
                .build();
    }

    /**
     * Creates a memory entry (legacy format).
     */
    public static MemoryEntry of(String category, String title, String content, String actor) {
        return builder()
                .agentId(actor)
                .content(content)
                .summary(title)
                .context(category)
                .build();
    }

    /**
     * Creates a builder for MemoryEntry.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MemoryEntry.
     */
    public static class Builder {

        private String id = UUID.randomUUID().toString();
        private String agentId;
        private MemoryType type = MemoryType.EPISODIC;
        private String content;
        private String summary;
        private String context;
        private double importance = 0.5;
        private Instant createdAt;
        private Instant accessedAt;
        private String sessionId;
        private String taskId;
        private Map<String, String> metadata;
        private float[] embedding;

        public Builder() {
        }

        public Builder(MemoryEntry entry) {
            this.id = entry.id;
            this.agentId = entry.agentId;
            this.type = entry.type;
            this.content = entry.content;
            this.summary = entry.summary;
            this.context = entry.context;
            this.importance = entry.importance;
            this.createdAt = entry.createdAt;
            this.accessedAt = entry.accessedAt;
            this.sessionId = entry.sessionId;
            this.taskId = entry.taskId;
            this.metadata = entry.metadata;
            this.embedding = entry.embedding;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder type(MemoryType type) {
            this.type = type;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder importance(double importance) {
            this.importance = importance;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder accessedAt(Instant accessedAt) {
            this.accessedAt = accessedAt;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.createdAt = timestamp;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder embedding(float[] embedding) {
            this.embedding = embedding;
            return this;
        }

        // Legacy builder methods
        public Builder category(String category) {
            this.context = category;
            return this;
        }

        public Builder title(String title) {
            this.summary = title;
            return this;
        }

        public Builder actor(String actor) {
            this.agentId = actor;
            return this;
        }

        public Builder tags(String tags) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            }
            if (this.metadata instanceof java.util.HashMap) {
                ((java.util.HashMap<String, String>) this.metadata).put("tags", tags);
            }
            return this;
        }

        public MemoryEntry build() {
            return new MemoryEntry(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryEntry that = (MemoryEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemoryEntry{" +
                "id='" + id + '\'' +
                ", agentId='" + agentId + '\'' +
                ", type=" + type +
                ", importance=" + importance +
                ", createdAt=" + createdAt +
                '}';
    }
}
