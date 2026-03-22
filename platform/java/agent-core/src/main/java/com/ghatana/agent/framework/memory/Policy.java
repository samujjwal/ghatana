package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents procedural memory - learned policies about how to act in situations.
 * 
 * <p>Policies are versioned, confidence-scored procedures that guide agent behavior.
 * They are learned from successful episodes and refined over time.
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * Policy policy = Policy.builder()
 *     .agentId("ProductOwnerAgent")
 *     .situation("requirement lacks acceptance criteria")
 *     .action("delegate to QA Lead to define testable criteria")
 *     .confidence(0.92)
 *     .learnedFrom(List.of("episode-123", "episode-456"))
 *     .build();
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Procedural memory (policy) representation
 * @doc.layer framework
 * @doc.pattern Value Object
 * @doc.gaa.memory procedural
 */
public final class Policy {
    
    private final String id;
    private final String agentId;
    private final String situation;
    private final String action;
    private final Double confidence;
    private final Instant learnedAt;
    private final Instant lastUsedAt;
    private final Integer useCount;
    private final String learnedFromEpisodes;
    private final String version;
    private final Map<String, Object> metadata;
    
    private Policy(Builder builder) {
        this.id = builder.id;
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.situation = Objects.requireNonNull(builder.situation, "situation cannot be null");
        this.action = Objects.requireNonNull(builder.action, "action cannot be null");
        this.confidence = Objects.requireNonNull(builder.confidence, "confidence cannot be null");
        this.learnedAt = Objects.requireNonNull(builder.learnedAt, "learnedAt cannot be null");
        this.lastUsedAt = builder.lastUsedAt;
        this.useCount = builder.useCount != null ? builder.useCount : 0;
        this.learnedFromEpisodes = builder.learnedFromEpisodes;
        this.version = builder.version != null ? builder.version : "1.0";
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
    public String getSituation() {
        return situation;
    }
    
    @NotNull
    public String getAction() {
        return action;
    }
    
    @NotNull
    public Double getConfidence() {
        return confidence;
    }
    
    @NotNull
    public Instant getLearnedAt() {
        return learnedAt;
    }
    
    @Nullable
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
    
    @NotNull
    public Integer getUseCount() {
        return useCount;
    }
    
    @Nullable
    public String getLearnedFromEpisodes() {
        return learnedFromEpisodes;
    }
    
    @NotNull
    public String getVersion() {
        return version;
    }
    
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Checks if this policy requires human review.
     * Policies with confidence < 0.7 should be reviewed.
     * 
     * @return true if requires review
     */
    public boolean requiresReview() {
        return confidence < 0.7;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String agentId;
        private String situation;
        private String action;
        private Double confidence;
        private Instant learnedAt;
        private Instant lastUsedAt;
        private Integer useCount;
        private String learnedFromEpisodes;
        private String version;
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
        
        public Builder situation(@NotNull String situation) {
            this.situation = situation;
            return this;
        }
        
        public Builder action(@NotNull String action) {
            this.action = action;
            return this;
        }
        
        public Builder confidence(@NotNull Double confidence) {
            this.confidence = confidence;
            return this;
        }
        
        public Builder learnedAt(@NotNull Instant learnedAt) {
            this.learnedAt = learnedAt;
            return this;
        }
        
        public Builder lastUsedAt(@Nullable Instant lastUsedAt) {
            this.lastUsedAt = lastUsedAt;
            return this;
        }
        
        public Builder useCount(@Nullable Integer useCount) {
            this.useCount = useCount;
            return this;
        }
        
        public Builder learnedFromEpisodes(@Nullable String learnedFromEpisodes) {
            this.learnedFromEpisodes = learnedFromEpisodes;
            return this;
        }
        
        public Builder version(@Nullable String version) {
            this.version = version;
            return this;
        }
        
        public Builder metadata(@NotNull Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        @NotNull
        public Policy build() {
            return new Policy(this);
        }
    }
}
