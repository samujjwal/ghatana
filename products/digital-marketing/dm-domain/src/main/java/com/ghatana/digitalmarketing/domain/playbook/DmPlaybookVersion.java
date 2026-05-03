package com.ghatana.digitalmarketing.domain.playbook;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a versioned playbook.
 *
 * @doc.type class
 * @doc.purpose Domain entity for playbook versioning and promotion (DMOS-F3-004)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmPlaybookVersion {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String playbookId;
    private final int versionNumber;
    private final String contentJson;
    private final DmPlaybookVersionStatus status;
    private final String promotedBy;
    private final Instant promotedAt;
    private final Instant createdAt;

    private DmPlaybookVersion(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.playbookId    = b.playbookId;
        this.versionNumber = b.versionNumber;
        this.contentJson   = b.contentJson;
        this.status        = b.status;
        this.promotedBy    = b.promotedBy;
        this.promotedAt    = b.promotedAt;
        this.createdAt     = b.createdAt;
    }

    public DmPlaybookVersion promote(String actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        if (status != DmPlaybookVersionStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT versions can be promoted");
        }
        return toBuilder().status(DmPlaybookVersionStatus.ACTIVE)
            .promotedBy(actor).promotedAt(Instant.now()).build();
    }

    public DmPlaybookVersion archive() {
        if (status != DmPlaybookVersionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE versions can be archived");
        }
        return toBuilder().status(DmPlaybookVersionStatus.ARCHIVED).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getPlaybookId()        { return playbookId; }
    public int getVersionNumber()        { return versionNumber; }
    public String getContentJson()       { return contentJson; }
    public DmPlaybookVersionStatus getStatus() { return status; }
    public String getPromotedBy()        { return promotedBy; }
    public Instant getPromotedAt()       { return promotedAt; }
    public Instant getCreatedAt()        { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmPlaybookVersion)) return false;
        return id.equals(((DmPlaybookVersion) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmPlaybookVersion{id='" + id + "', v=" + versionNumber + ", status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .playbookId(playbookId).versionNumber(versionNumber).contentJson(contentJson)
            .status(status).promotedBy(promotedBy).promotedAt(promotedAt).createdAt(createdAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, playbookId, contentJson, promotedBy;
        private int versionNumber;
        private DmPlaybookVersionStatus status;
        private Instant promotedAt, createdAt;

        public Builder id(String v)                        { this.id = v; return this; }
        public Builder tenantId(String v)                  { this.tenantId = v; return this; }
        public Builder workspaceId(String v)               { this.workspaceId = v; return this; }
        public Builder playbookId(String v)                { this.playbookId = v; return this; }
        public Builder versionNumber(int v)                { this.versionNumber = v; return this; }
        public Builder contentJson(String v)               { this.contentJson = v; return this; }
        public Builder status(DmPlaybookVersionStatus v)   { this.status = v; return this; }
        public Builder promotedBy(String v)                { this.promotedBy = v; return this; }
        public Builder promotedAt(Instant v)               { this.promotedAt = v; return this; }
        public Builder createdAt(Instant v)                { this.createdAt = v; return this; }

        public DmPlaybookVersion build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (playbookId == null || playbookId.isBlank()) throw new IllegalArgumentException("playbookId must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmPlaybookVersion(this);
        }
    }
}
