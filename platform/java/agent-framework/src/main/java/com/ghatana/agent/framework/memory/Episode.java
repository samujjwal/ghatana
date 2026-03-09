package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an episodic memory - what happened at a specific time.
 * 
 * <p>Episodes capture agent experiences including:
 * <ul>
 *   <li>Input received</li>
 *   <li>Actions taken</li>
 *   <li>Outcomes observed</li>
 *   <li>Context and metadata</li>
 * </ul>
 * 
 * <p>Episodes are immutable once stored.
 * 
 * @doc.type class
 * @doc.purpose Episodic memory representation
 * @doc.layer framework
 * @doc.pattern Value Object
 * @doc.gaa.memory episodic
 */
public final class Episode {
    
    private final String id;
    private final String agentId;
    private final String turnId;
    private final Instant timestamp;
    private final String input;
    private final String output;
    private final String action;
    private final Map<String, Object> context;
    private final List<String> tags;
    private final Double reward;
    private final String embedding;
    
    private Episode(Builder builder) {
        this.id = builder.id;
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.turnId = Objects.requireNonNull(builder.turnId, "turnId cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp cannot be null");
        this.input = Objects.requireNonNull(builder.input, "input cannot be null");
        this.output = builder.output;
        this.action = builder.action;
        this.context = builder.context != null ? Map.copyOf(builder.context) : Map.of();
        this.tags = builder.tags != null ? List.copyOf(builder.tags) : List.of();
        this.reward = builder.reward;
        this.embedding = builder.embedding;
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
    public String getTurnId() {
        return turnId;
    }
    
    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @NotNull
    public String getInput() {
        return input;
    }
    
    @Nullable
    public String getOutput() {
        return output;
    }
    
    @Nullable
    public String getAction() {
        return action;
    }
    
    @NotNull
    public Map<String, Object> getContext() {
        return context;
    }
    
    @NotNull
    public List<String> getTags() {
        return tags;
    }
    
    @Nullable
    public Double getReward() {
        return reward;
    }
    
    @Nullable
    public String getEmbedding() {
        return embedding;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String agentId;
        private String turnId;
        private Instant timestamp;
        private String input;
        private String output;
        private String action;
        private Map<String, Object> context;
        private List<String> tags;
        private Double reward;
        private String embedding;
        
        private Builder() {
            this.timestamp = Instant.now();
        }
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder agentId(@NotNull String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder turnId(@NotNull String turnId) {
            this.turnId = turnId;
            return this;
        }
        
        public Builder timestamp(@NotNull Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder input(@NotNull String input) {
            this.input = input;
            return this;
        }
        
        public Builder output(@Nullable String output) {
            this.output = output;
            return this;
        }
        
        public Builder action(@Nullable String action) {
            this.action = action;
            return this;
        }
        
        public Builder context(@NotNull Map<String, Object> context) {
            this.context = context;
            return this;
        }
        
        public Builder tags(@NotNull List<String> tags) {
            this.tags = tags;
            return this;
        }
        
        public Builder reward(@Nullable Double reward) {
            this.reward = reward;
            return this;
        }
        
        public Builder embedding(@Nullable String embedding) {
            this.embedding = embedding;
            return this;
        }
        
        @NotNull
        public Episode build() {
            return new Episode(this);
        }
    }
}
