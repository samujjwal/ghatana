package com.ghatana.datacloud.entity.search;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Search result value object with relevance score and highlighting.
 *
 * <p><b>Purpose</b><br>
 * Immutable representation of single search result including entity ID,
 * relevance score, content snippets, and highlighted matches.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchResult result = SearchResult.builder()
 *     .tenantId("tenant-123")
 *     .entityId("entity-456")
 *     .score(0.95f)
 *     .content(Map.of(
 *         "title", "Smartphone",
 *         "description", "Android smartphone with 5G"
 *     ))
 *     .highlights(Map.of(
 *         "description", "Android <em>smartphone</em> with 5G"
 *     ))
 *     .metadata(Map.of("category", "electronics", "price", 399.99))
 *     .build();
 * }</pre>
 *
 * <p><b>Field Requirements</b><br>
 * - tenantId: Non-blank, for tenant isolation
 * - entityId: Non-blank, unique identifier
 * - score: Float relevance score (0.0-1.0 for text, cosine similarity for vector)
 * - content: Non-empty map with text fields
 * - highlights: Optional map with highlighted snippets
 * - metadata: Optional map with additional fields
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - All fields are private final, collections are unmodifiable.
 *
 * @doc.type record
 * @doc.purpose Immutable search result value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class SearchResult {

    private final String tenantId;
    private final String entityId;
    private final float score;
    private final Map<String, String> content;
    private final Map<String, String> highlights;
    private final Map<String, Object> metadata;

    private SearchResult(
            String tenantId,
            String entityId,
            float score,
            Map<String, String> content,
            Map<String, String> highlights,
            Map<String, Object> metadata) {

        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.score = score;
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.highlights = highlights != null ? Map.copyOf(highlights) : Map.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (entityId.isBlank()) {
            throw new IllegalArgumentException("entityId cannot be blank");
        }
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content cannot be empty");
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEntityId() {
        return entityId;
    }

    public float getScore() {
        return score;
    }

    public Map<String, String> getContent() {
        return Collections.unmodifiableMap(content);
    }

    public Map<String, String> getHighlights() {
        return Collections.unmodifiableMap(highlights);
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public boolean hasHighlights() {
        return !highlights.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tenantId;
        private String entityId;
        private float score;
        private Map<String, String> content;
        private Map<String, String> highlights;
        private Map<String, Object> metadata;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder score(float score) {
            this.score = score;
            return this;
        }

        public Builder content(Map<String, String> content) {
            this.content = content;
            return this;
        }

        public Builder highlights(Map<String, String> highlights) {
            this.highlights = highlights;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(tenantId, entityId, score, content, highlights, metadata);
        }
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "tenantId='" + tenantId + '\'' +
                ", entityId='" + entityId + '\'' +
                ", score=" + score +
                ", content=" + content.keySet() +
                ", hasHighlights=" + hasHighlights() +
                ", metadata=" + metadata.keySet() +
                '}';
    }
}
