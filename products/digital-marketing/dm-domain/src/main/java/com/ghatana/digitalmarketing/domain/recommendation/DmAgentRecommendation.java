package com.ghatana.digitalmarketing.domain.recommendation;

import com.ghatana.digitalmarketing.domain.command.DmCommandType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing an agent recommendation awaiting conversion to a command.
 *
 * @doc.type class
 * @doc.purpose Captures agent-generated recommendations with metadata for gateway processing (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAgentRecommendation {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String agentId;
    private final DmCommandType targetCommandType;
    private final Map<String, Object> payload;
    private final String rationale;
    private final DmRecommendationStatus status;
    private final String rejectionReason;
    private final String commandId;
    private final Instant createdAt;
    private final Instant processedAt;
    private final Instant expiresAt;

    private DmAgentRecommendation(Builder builder) {
        this.id                = builder.id;
        this.tenantId          = builder.tenantId;
        this.workspaceId       = builder.workspaceId;
        this.agentId           = builder.agentId;
        this.targetCommandType = builder.targetCommandType;
        this.payload           = Map.copyOf(builder.payload);
        this.rationale         = builder.rationale;
        this.status            = builder.status;
        this.rejectionReason   = builder.rejectionReason;
        this.commandId         = builder.commandId;
        this.createdAt         = builder.createdAt;
        this.processedAt       = builder.processedAt;
        this.expiresAt         = builder.expiresAt;
    }

    /** Accept and convert this recommendation to a command. */
    public DmAgentRecommendation accept(String commandId) {
        if (this.status != DmRecommendationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING recommendations can be accepted, was: " + status);
        }
        Objects.requireNonNull(commandId, "commandId must not be null");
        return toBuilder()
            .status(DmRecommendationStatus.ACCEPTED)
            .commandId(commandId)
            .processedAt(Instant.now())
            .build();
    }

    /** Reject this recommendation with a reason. */
    public DmAgentRecommendation reject(String reason) {
        if (this.status != DmRecommendationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING recommendations can be rejected, was: " + status);
        }
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder()
            .status(DmRecommendationStatus.REJECTED)
            .rejectionReason(reason)
            .processedAt(Instant.now())
            .build();
    }

    /** Mark as expired if it was not processed in time. */
    public DmAgentRecommendation expire() {
        if (this.status != DmRecommendationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING recommendations can expire, was: " + status);
        }
        return toBuilder()
            .status(DmRecommendationStatus.EXPIRED)
            .processedAt(Instant.now())
            .build();
    }

    /** Whether this recommendation has been processed (not PENDING). */
    public boolean isProcessed() {
        return status != DmRecommendationStatus.PENDING;
    }

    public String getId()                        { return id; }
    public String getTenantId()                  { return tenantId; }
    public String getWorkspaceId()               { return workspaceId; }
    public String getAgentId()                   { return agentId; }
    public DmCommandType getTargetCommandType()  { return targetCommandType; }
    public Map<String, Object> getPayload()      { return payload; }
    public String getRationale()                 { return rationale; }
    public DmRecommendationStatus getStatus()    { return status; }
    public String getRejectionReason()           { return rejectionReason; }
    public String getCommandId()                 { return commandId; }
    public Instant getCreatedAt()                { return createdAt; }
    public Instant getProcessedAt()              { return processedAt; }
    public Instant getExpiresAt()                { return expiresAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAgentRecommendation)) return false;
        return id.equals(((DmAgentRecommendation) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "DmAgentRecommendation{id='" + id + "', agentId='" + agentId
            + "', commandType=" + targetCommandType + ", status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id).tenantId(tenantId).workspaceId(workspaceId)
            .agentId(agentId).targetCommandType(targetCommandType)
            .payload(payload).rationale(rationale).status(status)
            .rejectionReason(rejectionReason).commandId(commandId)
            .createdAt(createdAt).processedAt(processedAt).expiresAt(expiresAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String agentId;
        private DmCommandType targetCommandType;
        private Map<String, Object> payload = Map.of();
        private String rationale;
        private DmRecommendationStatus status;
        private String rejectionReason;
        private String commandId;
        private Instant createdAt;
        private Instant processedAt;
        private Instant expiresAt;

        public Builder id(String id)                                 { this.id = id; return this; }
        public Builder tenantId(String t)                            { this.tenantId = t; return this; }
        public Builder workspaceId(String w)                         { this.workspaceId = w; return this; }
        public Builder agentId(String a)                             { this.agentId = a; return this; }
        public Builder targetCommandType(DmCommandType t)            { this.targetCommandType = t; return this; }
        public Builder payload(Map<String, Object> p)                { this.payload = p; return this; }
        public Builder rationale(String r)                           { this.rationale = r; return this; }
        public Builder status(DmRecommendationStatus s)              { this.status = s; return this; }
        public Builder rejectionReason(String r)                     { this.rejectionReason = r; return this; }
        public Builder commandId(String c)                           { this.commandId = c; return this; }
        public Builder createdAt(Instant t)                          { this.createdAt = t; return this; }
        public Builder processedAt(Instant t)                        { this.processedAt = t; return this; }
        public Builder expiresAt(Instant t)                          { this.expiresAt = t; return this; }

        public DmAgentRecommendation build() {
            Objects.requireNonNull(status, "status must not be null");
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(targetCommandType, "targetCommandType must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmAgentRecommendation(this);
        }
    }
}
