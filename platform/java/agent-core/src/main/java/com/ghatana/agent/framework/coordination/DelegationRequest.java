package com.ghatana.agent.framework.coordination;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Request to delegate a task to another agent.
 * 
 * @param <TResult> Expected result type
 * 
 * @doc.type class
 * @doc.purpose Delegation request specification
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class DelegationRequest<TResult> {
    
    private final String fromAgentId;
    private final String toAgentId;
    private final String toRole;
    private final Object task;
    private final Class<TResult> resultType;
    private final Priority priority;
    private final Map<String, Object> metadata;
    
    private DelegationRequest(Builder<TResult> builder) {
        this.fromAgentId = Objects.requireNonNull(builder.fromAgentId, "fromAgentId cannot be null");
        this.task = Objects.requireNonNull(builder.task, "task cannot be null");
        this.resultType = Objects.requireNonNull(builder.resultType, "resultType cannot be null");
        this.toAgentId = builder.toAgentId;
        this.toRole = builder.toRole;
        this.priority = builder.priority != null ? builder.priority : Priority.NORMAL;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        
        if (toAgentId == null && toRole == null) {
            throw new IllegalArgumentException("Either toAgentId or toRole must be specified");
        }
    }
    
    @NotNull
    public String getFromAgentId() {
        return fromAgentId;
    }
    
    @Nullable
    public String getToAgentId() {
        return toAgentId;
    }
    
    @Nullable
    public String getToRole() {
        return toRole;
    }
    
    @NotNull
    public Object getTask() {
        return task;
    }
    
    @NotNull
    public Class<TResult> getResultType() {
        return resultType;
    }
    
    @NotNull
    public Priority getPriority() {
        return priority;
    }
    
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    @NotNull
    public static <TResult> Builder<TResult> builder(@NotNull Class<TResult> resultType) {
        return new Builder<>(resultType);
    }
    
    public static final class Builder<TResult> {
        private final Class<TResult> resultType;
        private String fromAgentId;
        private String toAgentId;
        private String toRole;
        private Object task;
        private Priority priority;
        private Map<String, Object> metadata;
        
        private Builder(Class<TResult> resultType) {
            this.resultType = resultType;
        }
        
        @NotNull
        public Builder<TResult> fromAgent(@NotNull String fromAgentId) {
            this.fromAgentId = fromAgentId;
            return this;
        }
        
        @NotNull
        public Builder<TResult> toAgent(@NotNull String toAgentId) {
            this.toAgentId = toAgentId;
            return this;
        }
        
        @NotNull
        public Builder<TResult> toRole(@NotNull String toRole) {
            this.toRole = toRole;
            return this;
        }
        
        @NotNull
        public Builder<TResult> task(@NotNull Object task) {
            this.task = task;
            return this;
        }
        
        @NotNull
        public Builder<TResult> priority(@NotNull Priority priority) {
            this.priority = priority;
            return this;
        }
        
        @NotNull
        public Builder<TResult> metadata(@NotNull Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        @NotNull
        public DelegationRequest<TResult> build() {
            return new DelegationRequest<>(this);
        }
    }
    
    public enum Priority {
        LOW(1),
        NORMAL(5),
        HIGH(10),
        CRITICAL(20);
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
}
