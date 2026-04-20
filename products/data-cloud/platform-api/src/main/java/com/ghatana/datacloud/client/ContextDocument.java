package com.ghatana.datacloud.client;

import com.ghatana.datacloud.spi.ai.PredictionCapability;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Context document for LLM consumption (Tier-2).
 *
 * @doc.type class
 * @doc.purpose Context document for LLM gateway
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class ContextDocument {

    private final UUID contextId;
    private final Instant createdAt;
    private final String tenantId;
    private final ContextType contextType;
    private final ContextSource source;
    private final String content;
    private final Map<String, Object> structuredData;
    private final double confidence;
    private final PredictionCapability.DeterminismLevel determinism;
    private final Duration ttl;
    private final Instant expiresAt;
    private final float[] embedding;
    private final Map<String, String> tags;
    private final Integer tokenCount;
    private final int version;

    private ContextDocument(Builder builder) {
        this.contextId = builder.contextId;
        this.createdAt = builder.createdAt;
        this.tenantId = builder.tenantId;
        this.contextType = builder.contextType;
        this.source = builder.source;
        this.content = builder.content;
        this.structuredData = Collections.unmodifiableMap(builder.structuredData);
        this.confidence = builder.confidence;
        this.determinism = builder.determinism;
        this.ttl = builder.ttl;
        this.expiresAt = builder.expiresAt;
        this.embedding = builder.embedding;
        this.tags = Collections.unmodifiableMap(builder.tags);
        this.tokenCount = builder.tokenCount;
        this.version = builder.version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
            .contextId(contextId)
            .createdAt(createdAt)
            .tenantId(tenantId)
            .contextType(contextType)
            .source(source)
            .content(content)
            .structuredData(structuredData)
            .confidence(confidence)
            .determinism(determinism)
            .ttl(ttl)
            .expiresAt(expiresAt)
            .embedding(embedding)
            .tags(tags)
            .tokenCount(tokenCount)
            .version(version);
    }

    public UUID getContextId() { return contextId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTenantId() { return tenantId; }
    public ContextType getContextType() { return contextType; }
    public ContextSource getSource() { return source; }
    public String getContent() { return content; }
    public Map<String, Object> getStructuredData() { return structuredData; }
    public double getConfidence() { return confidence; }
    public PredictionCapability.DeterminismLevel getDeterminism() { return determinism; }
    public Duration getTtl() { return ttl; }
    public Instant getExpiresAt() { return expiresAt; }
    public float[] getEmbedding() { return embedding; }
    public Map<String, String> getTags() { return tags; }
    public Integer getTokenCount() { return tokenCount; }
    public int getVersion() { return version; }

    public boolean isFresh() {
        return Instant.now().isBefore(expiresAt);
    }

    public boolean isCriticalGrade() {
        return confidence >= 0.95 && determinism == PredictionCapability.DeterminismLevel.HIGH;
    }

    public enum ContextType {
        FACT,
        SUMMARY,
        TRACE,
        AGGREGATION,
        PATTERN,
        ANOMALY,
        RECOMMENDATION,
        EXPLANATION,
        CUSTOM
    }

    public static final class ContextSource {
        private final String collection;
        private final String plugin;
        private final String queryId;
        private final String operation;
        private final String actor;
        private final Map<String, String> provenance;

        private ContextSource(ContextSourceBuilder builder) {
            this.collection = builder.collection;
            this.plugin = builder.plugin;
            this.queryId = builder.queryId;
            this.operation = builder.operation;
            this.actor = builder.actor;
            this.provenance = Collections.unmodifiableMap(builder.provenance);
        }

        public static ContextSourceBuilder builder() {
            return new ContextSourceBuilder();
        }

        public String getCollection() { return collection; }
        public String getPlugin() { return plugin; }
        public String getQueryId() { return queryId; }
        public String getOperation() { return operation; }
        public String getActor() { return actor; }
        public Map<String, String> getProvenance() { return provenance; }

        public static final class ContextSourceBuilder {
            private String collection;
            private String plugin;
            private String queryId;
            private String operation;
            private String actor;
            private Map<String, String> provenance = Collections.emptyMap();

            private ContextSourceBuilder() {
            }

            public ContextSourceBuilder collection(String collection) { this.collection = collection; return this; }
            public ContextSourceBuilder plugin(String plugin) { this.plugin = plugin; return this; }
            public ContextSourceBuilder queryId(String queryId) { this.queryId = queryId; return this; }
            public ContextSourceBuilder operation(String operation) { this.operation = operation; return this; }
            public ContextSourceBuilder actor(String actor) { this.actor = actor; return this; }
            public ContextSourceBuilder provenance(Map<String, String> provenance) { this.provenance = provenance != null ? provenance : Collections.emptyMap(); return this; }

            public ContextSource build() { return new ContextSource(this); }
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("contextId", contextId.toString());
        map.put("createdAt", createdAt.toString());
        map.put("tenantId", tenantId);
        map.put("contextType", contextType.name());
        map.put("source", sourceToMap());
        map.put("content", content);
        map.put("structuredData", structuredData != null ? structuredData : Map.of());
        map.put("confidence", confidence);
        map.put("determinism", determinism.name());
        map.put("ttl", ttl.toString());
        map.put("expiresAt", expiresAt.toString());
        map.put("tags", tags != null ? tags : Map.of());
        map.put("tokenCount", tokenCount != null ? tokenCount : 0);
        map.put("version", version);
        return map;
    }

    private Map<String, Object> sourceToMap() {
        if (source == null) {
            return Map.of();
        }
        return Map.of(
            "collection", source.collection != null ? source.collection : "",
            "plugin", source.plugin != null ? source.plugin : "",
            "queryId", source.queryId != null ? source.queryId : "",
            "operation", source.operation != null ? source.operation : "",
            "actor", source.actor != null ? source.actor : "",
            "provenance", source.provenance != null ? source.provenance : Map.of()
        );
    }

    public static final class Builder {
        private UUID contextId = UUID.randomUUID();
        private Instant createdAt = Instant.now();
        private String tenantId;
        private ContextType contextType;
        private ContextSource source;
        private String content;
        private Map<String, Object> structuredData = Collections.emptyMap();
        private double confidence;
        private PredictionCapability.DeterminismLevel determinism;
        private Duration ttl = Duration.ofDays(30);
        private Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        private float[] embedding;
        private Map<String, String> tags = Collections.emptyMap();
        private Integer tokenCount;
        private int version = 1;

        private Builder() {
        }

        public Builder contextId(UUID contextId) { this.contextId = contextId != null ? contextId : UUID.randomUUID(); return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt != null ? createdAt : Instant.now(); return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder contextType(ContextType contextType) { this.contextType = contextType; return this; }
        public Builder source(ContextSource source) { this.source = source; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder structuredData(Map<String, Object> structuredData) { this.structuredData = structuredData != null ? structuredData : Collections.emptyMap(); return this; }
        public Builder confidence(double confidence) { this.confidence = confidence; return this; }
        public Builder determinism(PredictionCapability.DeterminismLevel determinism) { this.determinism = determinism; return this; }
        public Builder ttl(Duration ttl) { this.ttl = ttl != null ? ttl : Duration.ofDays(30); return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plus(Duration.ofDays(30)); return this; }
        public Builder embedding(float[] embedding) { this.embedding = embedding; return this; }
        public Builder tags(Map<String, String> tags) { this.tags = tags != null ? tags : Collections.emptyMap(); return this; }
        public Builder tokenCount(Integer tokenCount) { this.tokenCount = tokenCount; return this; }
        public Builder version(int version) { this.version = version; return this; }

        public ContextDocument build() {
            if (expiresAt == null && createdAt != null && ttl != null) {
                expiresAt = createdAt.plus(ttl);
            }
            return new ContextDocument(this);
        }
    }
}
