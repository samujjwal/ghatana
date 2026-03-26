package com.ghatana.datacloud.entity.search;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Search document value object for indexing.
 *
 * <p><b>Purpose</b><br>
 * Immutable representation of document to be indexed for search. Contains
 * entity content, metadata, and optional embedding vector for semantic search.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchDocument doc = SearchDocument.builder()
 *     .tenantId("tenant-123")
 *     .entityId("entity-456")
 *     .content(Map.of(
 *         "title", "Product Name",
 *         "description", "Product description text"
 *     ))
 *     .metadata(Map.of(
 *         "category", "electronics",
 *         "price", 99.99,
 *         "inStock", true
 *     ))
 *     .vector(new float[]{0.1f, 0.2f, ...}) // Optional
 *     .build();
 * }</pre>
 *
 * <p><b>Field Requirements</b><br>
 * - tenantId: Non-blank, for tenant isolation
 * - entityId: Non-blank, unique identifier
 * - content: Non-empty map, text fields for full-text search
 * - metadata: Optional map, additional fields for filtering
 * - vector: Optional float array, embedding for semantic search
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - All fields are private final, collections are unmodifiable.
 *
 * @doc.type record
 * @doc.purpose Immutable search document value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class SearchDocument {

    private final String tenantId;
    private final String entityId;
    private final Map<String, String> content;
    private final Map<String, Object> metadata;
    private final float[] vector;

    private SearchDocument(
            String tenantId,
            String entityId,
            Map<String, String> content,
            Map<String, Object> metadata,
            float[] vector) {

        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.vector = vector;

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

    public Map<String, String> getContent() {
        return Collections.unmodifiableMap(content);
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public float[] getVector() {
        return vector;
    }

    public boolean hasVector() {
        return vector != null && vector.length > 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tenantId;
        private String entityId;
        private Map<String, String> content;
        private Map<String, Object> metadata;
        private float[] vector;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder content(Map<String, String> content) {
            this.content = content;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        public SearchDocument build() {
            return new SearchDocument(tenantId, entityId, content, metadata, vector);
        }
    }

    @Override
    public String toString() {
        return "SearchDocument{" +
                "tenantId='" + tenantId + '\'' +
                ", entityId='" + entityId + '\'' +
                ", content=" + content.keySet() +
                ", metadata=" + metadata.keySet() +
                ", hasVector=" + hasVector() +
                '}';
    }
}
