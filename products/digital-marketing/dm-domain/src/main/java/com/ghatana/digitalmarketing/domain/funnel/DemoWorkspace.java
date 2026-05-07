package com.ghatana.digitalmarketing.domain.funnel;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a demo workspace for self-marketing acquisition funnel.
 *
 * @doc.type class
 * @doc.purpose Demo workspace for product-led growth trials (P3-001)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DemoWorkspace {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String leadId;
    private final String templateId;
    private final DemoWorkspaceStatus status;
    private final Map<String, Object> templateConfig;
    private final Instant createdAt;
    private final Instant activatedAt;
    private final Instant expiresAt;
    private final String deactivationReason;

    private DemoWorkspace(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.leadId = builder.leadId;
        this.templateId = builder.templateId;
        this.status = builder.status;
        this.templateConfig = Map.copyOf(builder.templateConfig);
        this.createdAt = builder.createdAt;
        this.activatedAt = builder.activatedAt;
        this.expiresAt = builder.expiresAt;
        this.deactivationReason = builder.deactivationReason;
    }

    public DemoWorkspace activate() {
        if (status != DemoWorkspaceStatus.PROVISIONED) {
            throw new IllegalStateException("Cannot activate workspace in status: " + status);
        }
        return toBuilder()
            .status(DemoWorkspaceStatus.ACTIVE)
            .activatedAt(Instant.now())
            .build();
    }

    public DemoWorkspace deactivate(String reason) {
        if (status != DemoWorkspaceStatus.ACTIVE) {
            throw new IllegalStateException("Cannot deactivate workspace in status: " + status);
        }
        return toBuilder()
            .status(DemoWorkspaceStatus.DEACTIVATED)
            .deactivationReason(reason)
            .build();
    }

    public DemoWorkspace expire() {
        if (status != DemoWorkspaceStatus.ACTIVE) {
            throw new IllegalStateException("Cannot expire workspace in status: " + status);
        }
        return toBuilder()
            .status(DemoWorkspaceStatus.EXPIRED)
            .build();
    }

    public boolean isActive() {
        return status == DemoWorkspaceStatus.ACTIVE;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getLeadId() { return leadId; }
    public String getTemplateId() { return templateId; }
    public DemoWorkspaceStatus getStatus() { return status; }
    public Map<String, Object> getTemplateConfig() { return templateConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getDeactivationReason() { return deactivationReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DemoWorkspace)) return false;
        return id.equals(((DemoWorkspace) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "DemoWorkspace{id='" + id + "', workspaceId='" + workspaceId + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .leadId(leadId)
            .templateId(templateId)
            .status(status)
            .templateConfig(templateConfig)
            .createdAt(createdAt)
            .activatedAt(activatedAt)
            .expiresAt(expiresAt)
            .deactivationReason(deactivationReason);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String leadId;
        private String templateId;
        private DemoWorkspaceStatus status = DemoWorkspaceStatus.PROVISIONED;
        private Map<String, Object> templateConfig = Map.of();
        private Instant createdAt;
        private Instant activatedAt;
        private Instant expiresAt;
        private String deactivationReason;

        public Builder id(String v) { this.id = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder workspaceId(String v) { this.workspaceId = v; return this; }
        public Builder leadId(String v) { this.leadId = v; return this; }
        public Builder templateId(String v) { this.templateId = v; return this; }
        public Builder status(DemoWorkspaceStatus v) { this.status = v; return this; }
        public Builder templateConfig(Map<String, Object> v) { this.templateConfig = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder activatedAt(Instant v) { this.activatedAt = v; return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder deactivationReason(String v) { this.deactivationReason = v; return this; }

        public DemoWorkspace build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
            if (leadId == null || leadId.isBlank()) throw new IllegalArgumentException("leadId must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DemoWorkspace(this);
        }
    }
}
