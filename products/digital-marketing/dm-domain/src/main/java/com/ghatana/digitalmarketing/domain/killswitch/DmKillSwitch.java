package com.ghatana.digitalmarketing.domain.killswitch;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a kill switch for campaign operations.
 *
 * @doc.type class
 * @doc.purpose Provides emergency pause mechanism for running campaigns (DMOS-F2-015)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmKillSwitch {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String scope;
    private final String scopeId;
    private final boolean active;
    private final String reason;
    private final String activatedBy;
    private final Instant activatedAt;
    private final Instant deactivatedAt;
    private final Instant createdAt;

    private DmKillSwitch(Builder b) {
        this.id             = b.id;
        this.tenantId       = b.tenantId;
        this.workspaceId    = b.workspaceId;
        this.scope          = b.scope;
        this.scopeId        = b.scopeId;
        this.active         = b.active;
        this.reason         = b.reason;
        this.activatedBy    = b.activatedBy;
        this.activatedAt    = b.activatedAt;
        this.deactivatedAt  = b.deactivatedAt;
        this.createdAt      = b.createdAt;
    }

    public DmKillSwitch deactivate() {
        if (!active) {
            throw new IllegalStateException("Kill switch is already inactive");
        }
        return toBuilder().active(false).deactivatedAt(Instant.now()).build();
    }

    public String getId()            { return id; }
    public String getTenantId()      { return tenantId; }
    public String getWorkspaceId()   { return workspaceId; }
    public String getScope()         { return scope; }
    public String getScopeId()       { return scopeId; }
    public boolean isActive()        { return active; }
    public String getReason()        { return reason; }
    public String getActivatedBy()   { return activatedBy; }
    public Instant getActivatedAt()  { return activatedAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public Instant getCreatedAt()    { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmKillSwitch)) return false;
        return id.equals(((DmKillSwitch) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmKillSwitch{id='" + id + "', active=" + active + ", scope='" + scope + "'}";
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .scope(scope).scopeId(scopeId).active(active).reason(reason)
            .activatedBy(activatedBy).activatedAt(activatedAt)
            .deactivatedAt(deactivatedAt).createdAt(createdAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, scope, scopeId, reason, activatedBy;
        private boolean active;
        private Instant activatedAt, deactivatedAt, createdAt;

        public Builder id(String v)             { this.id = v; return this; }
        public Builder tenantId(String v)       { this.tenantId = v; return this; }
        public Builder workspaceId(String v)    { this.workspaceId = v; return this; }
        public Builder scope(String v)          { this.scope = v; return this; }
        public Builder scopeId(String v)        { this.scopeId = v; return this; }
        public Builder active(boolean v)        { this.active = v; return this; }
        public Builder reason(String v)         { this.reason = v; return this; }
        public Builder activatedBy(String v)    { this.activatedBy = v; return this; }
        public Builder activatedAt(Instant v)   { this.activatedAt = v; return this; }
        public Builder deactivatedAt(Instant v) { this.deactivatedAt = v; return this; }
        public Builder createdAt(Instant v)     { this.createdAt = v; return this; }

        public DmKillSwitch build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmKillSwitch(this);
        }
    }
}
