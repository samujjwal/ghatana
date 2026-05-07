package com.ghatana.digitalmarketing.domain.agency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing an approval SLA for agency client approvals.
 *
 * @doc.type class
 * @doc.purpose Approval SLA for agency client approval workflows (P3-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class AgencyApprovalSLA {

    private final String id;
    private final String contractId;
    private final String agencyTenantId;
    private final String clientId;
    private final String approvalType;
    private final Duration maxApprovalTime;
    private final int escalationLevel;
    private final Map<String, Duration> escalationTimeouts;
    private final String escalationProcedure;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgencyApprovalSLA(Builder builder) {
        this.id = builder.id;
        this.contractId = builder.contractId;
        this.agencyTenantId = builder.agencyTenantId;
        this.clientId = builder.clientId;
        this.approvalType = builder.approvalType;
        this.maxApprovalTime = builder.maxApprovalTime;
        this.escalationLevel = builder.escalationLevel;
        this.escalationTimeouts = Map.copyOf(builder.escalationTimeouts);
        this.escalationProcedure = builder.escalationProcedure;
        this.active = builder.active;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public AgencyApprovalSLA activate() {
        return toBuilder()
            .active(true)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyApprovalSLA deactivate(String reason) {
        return toBuilder()
            .active(false)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyApprovalSLA updateEscalationLevel(int newLevel) {
        if (newLevel < 0 || newLevel > 5) {
            throw new IllegalArgumentException("Escalation level must be between 0 and 5");
        }
        return toBuilder()
            .escalationLevel(newLevel)
            .updatedAt(Instant.now())
            .build();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isOverdue(Instant requestTime) {
        if (!active || maxApprovalTime == null) {
            return false;
        }
        Instant deadline = requestTime.plus(maxApprovalTime);
        return Instant.now().isAfter(deadline);
    }

    public Duration getTimeUntilDeadline(Instant requestTime) {
        if (!active || maxApprovalTime == null) {
            return null;
        }
        Instant deadline = requestTime.plus(maxApprovalTime);
        Duration remaining = Duration.between(Instant.now(), deadline);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public String getId() { return id; }
    public String getContractId() { return contractId; }
    public String getAgencyTenantId() { return agencyTenantId; }
    public String getClientId() { return clientId; }
    public String getApprovalType() { return approvalType; }
    public Duration getMaxApprovalTime() { return maxApprovalTime; }
    public int getEscalationLevel() { return escalationLevel; }
    public Map<String, Duration> getEscalationTimeouts() { return escalationTimeouts; }
    public String getEscalationProcedure() { return escalationProcedure; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgencyApprovalSLA)) return false;
        return id.equals(((AgencyApprovalSLA) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "AgencyApprovalSLA{id='" + id + "', approvalType='" + approvalType + "', active=" + active + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .contractId(contractId)
            .agencyTenantId(agencyTenantId)
            .clientId(clientId)
            .approvalType(approvalType)
            .maxApprovalTime(maxApprovalTime)
            .escalationLevel(escalationLevel)
            .escalationTimeouts(escalationTimeouts)
            .escalationProcedure(escalationProcedure)
            .active(active)
            .createdAt(createdAt)
            .updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String contractId;
        private String agencyTenantId;
        private String clientId;
        private String approvalType;
        private Duration maxApprovalTime;
        private int escalationLevel = 0;
        private Map<String, Duration> escalationTimeouts = Map.of();
        private String escalationProcedure;
        private boolean active = true;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder contractId(String v) { this.contractId = v; return this; }
        public Builder agencyTenantId(String v) { this.agencyTenantId = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder approvalType(String v) { this.approvalType = v; return this; }
        public Builder maxApprovalTime(Duration v) { this.maxApprovalTime = v; return this; }
        public Builder escalationLevel(int v) { this.escalationLevel = v; return this; }
        public Builder escalationTimeouts(Map<String, Duration> v) { this.escalationTimeouts = v; return this; }
        public Builder escalationProcedure(String v) { this.escalationProcedure = v; return this; }
        public Builder active(boolean v) { this.active = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AgencyApprovalSLA build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (contractId == null || contractId.isBlank()) throw new IllegalArgumentException("contractId must not be blank");
            if (agencyTenantId == null || agencyTenantId.isBlank()) throw new IllegalArgumentException("agencyTenantId must not be blank");
            if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank");
            if (approvalType == null || approvalType.isBlank()) throw new IllegalArgumentException("approvalType must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new AgencyApprovalSLA(this);
        }
    }
}
