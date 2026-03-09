package com.ghatana.agent.framework.coordination;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Status of a delegation request.
 * 
 * @doc.type class
 * @doc.purpose Delegation status tracking
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class DelegationStatus {
    
    private final String delegationId;
    private final String fromAgentId;
    private final String toAgentId;
    private final State state;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String errorMessage;
    
    public DelegationStatus(
            @NotNull String delegationId,
            @NotNull String fromAgentId,
            @NotNull String toAgentId,
            @NotNull State state,
            @NotNull Instant createdAt,
            @Nullable Instant startedAt,
            @Nullable Instant completedAt,
            @Nullable String errorMessage) {
        this.delegationId = delegationId;
        this.fromAgentId = fromAgentId;
        this.toAgentId = toAgentId;
        this.state = state;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
    
    @NotNull
    public String getDelegationId() {
        return delegationId;
    }
    
    @NotNull
    public String getFromAgentId() {
        return fromAgentId;
    }
    
    @NotNull
    public String getToAgentId() {
        return toAgentId;
    }
    
    @NotNull
    public State getState() {
        return state;
    }
    
    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }
    
    @Nullable
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public boolean isComplete() {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }
    
    public enum State {
        PENDING,
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
