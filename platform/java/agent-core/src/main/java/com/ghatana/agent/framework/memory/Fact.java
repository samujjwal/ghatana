package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents semantic memory - facts and knowledge.
 * Facts are represented as subject-predicate-object triples (RDF-style).
 * 
 * <p><b>Examples:</b>
 * <ul>
 *   <li>("User123", "prefers", "dark_mode")</li>
 *   <li>("ServiceA", "depends_on", "ServiceB")</li>
 *   <li>("RequirementR1", "satisfies", "ComplianceRule-GDPR")</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Semantic memory (fact) representation
 * @doc.layer framework
 * @doc.pattern Value Object
 * @doc.gaa.memory semantic
 */
public final class Fact {
    
    private final String id;
    private final String agentId;
    private final String subject;
    private final String predicate;
    private final String object;
    private final Instant learnedAt;
    private final Double confidence;
    private final String source;
    private final Map<String, Object> metadata;
    
    private Fact(Builder builder) {
        this.id = builder.id;
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.subject = Objects.requireNonNull(builder.subject, "subject cannot be null");
        this.predicate = Objects.requireNonNull(builder.predicate, "predicate cannot be null");
        this.object = Objects.requireNonNull(builder.object, "object cannot be null");
        this.learnedAt = Objects.requireNonNull(builder.learnedAt, "learnedAt cannot be null");
        this.confidence = builder.confidence != null ? builder.confidence : 1.0;
        this.source = builder.source;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }
    
    @Nullable
    public String getId() {
        return id;
    }
    
    @NotNull
    public String getAgentId() {
        return agentId;
    }
    
    @NotNull
    public String getSubject() {
        return subject;
    }
    
    @NotNull
    public String getPredicate() {
        return predicate;
    }
    
    @NotNull
    public String getObject() {
        return object;
    }
    
    @NotNull
    public Instant getLearnedAt() {
        return learnedAt;
    }
    
    @NotNull
    public Double getConfidence() {
        return confidence;
    }
    
    @Nullable
    public String getSource() {
        return source;
    }
    
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String agentId;
        private String subject;
        private String predicate;
        private String object;
        private Instant learnedAt;
        private Double confidence;
        private String source;
        private Map<String, Object> metadata;
        
        private Builder() {
            this.learnedAt = Instant.now();
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder agentId(@NotNull String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder subject(@NotNull String subject) {
            this.subject = subject;
            return this;
        }
        
        public Builder predicate(@NotNull String predicate) {
            this.predicate = predicate;
            return this;
        }
        
        public Builder object(@NotNull String object) {
            this.object = object;
            return this;
        }
        
        public Builder learnedAt(@NotNull Instant learnedAt) {
            this.learnedAt = learnedAt;
            return this;
        }
        
        public Builder confidence(@Nullable Double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public Builder source(@Nullable String source) {
            this.source = source;
            return this;
        }
        
        public Builder metadata(@NotNull Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        @NotNull
        public Fact build() {
            return new Fact(this);
        }
    }
}
