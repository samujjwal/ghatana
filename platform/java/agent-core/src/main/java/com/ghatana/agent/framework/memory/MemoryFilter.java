package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Filter for querying memory.
 * 
 * @doc.type class
 * @doc.purpose Memory query filter
 * @doc.layer framework
 * @doc.pattern Specification
 */
public final class MemoryFilter {
    
    private final Instant startTime;
    private final Instant endTime;
    private final List<String> tags;
    private final String agentId;
    private final String turnId;
    
    private MemoryFilter(Builder builder) {
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.tags = builder.tags != null ? List.copyOf(builder.tags) : List.of();
        this.agentId = builder.agentId;
        this.turnId = builder.turnId;
    }
    
    @Nullable
    public Instant getStartTime() {
        return startTime;
    }
    
    @Nullable
    public Instant getEndTime() {
        return endTime;
    }
    
    @NotNull
    public List<String> getTags() {
        return tags;
    }
    
    @Nullable
    public String getAgentId() {
        return agentId;
    }
    
    @Nullable
    public String getTurnId() {
        return turnId;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Instant startTime;
        private Instant endTime;
        private List<String> tags;
        private String agentId;
        private String turnId;
        
        private Builder() {}
        
        public Builder startTime(@Nullable Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(@Nullable Instant endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder tags(@NotNull List<String> tags) {
            this.tags = tags;
            return this;
        }
        
        public Builder agentId(@Nullable String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder turnId(@Nullable String turnId) {
            this.turnId = turnId;
            return this;
        }
        
        @NotNull
        public MemoryFilter build() {
            return new MemoryFilter(this);
        }
    }
}
