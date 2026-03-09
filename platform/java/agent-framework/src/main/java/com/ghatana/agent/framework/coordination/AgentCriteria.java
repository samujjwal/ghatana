package com.ghatana.agent.framework.coordination;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Criteria for finding agents.
 * 
 * @doc.type class
 * @doc.purpose Agent discovery criteria
 * @doc.layer framework
 * @doc.pattern Specification
 */
public final class AgentCriteria {
    
    private final String role;
    private final List<String> capabilities;
    private final Map<String, Object> filters;
    
    private AgentCriteria(Builder builder) {
        this.role = builder.role;
        this.capabilities = builder.capabilities != null ? List.copyOf(builder.capabilities) : List.of();
        this.filters = builder.filters != null ? Map.copyOf(builder.filters) : Map.of();
    }
    
    @Nullable
    public String getRole() {
        return role;
    }
    
    @NotNull
    public List<String> getCapabilities() {
        return capabilities;
    }
    
    @NotNull
    public Map<String, Object> getFilters() {
        return filters;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String role;
        private List<String> capabilities;
        private Map<String, Object> filters;
        
        private Builder() {}
        
        @NotNull
        public Builder role(@Nullable String role) {
            this.role = role;
            return this;
        }
        
        @NotNull
        public Builder capabilities(@NotNull List<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }
        
        @NotNull
        public Builder filters(@NotNull Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }
        
        @NotNull
        public AgentCriteria build() {
            return new AgentCriteria(this);
        }
    }
}
