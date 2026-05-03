package com.ghatana.digitalmarketing.domain.preflight;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing a preflight safety checklist evaluation.
 *
 * @doc.type class
 * @doc.purpose Ensures campaign meets all safety requirements before launch (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmPreflightChecklist {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String campaignId;
    private final List<DmPreflightCheckItem> items;
    private final DmPreflightStatus status;
    private final Instant evaluatedAt;
    private final Instant createdAt;

    private DmPreflightChecklist(Builder b) {
        this.id           = b.id;
        this.tenantId     = b.tenantId;
        this.workspaceId  = b.workspaceId;
        this.campaignId   = b.campaignId;
        this.items        = List.copyOf(b.items);
        this.status       = b.status;
        this.evaluatedAt  = b.evaluatedAt;
        this.createdAt    = b.createdAt;
    }

    /** Returns true if all required checks have passed. */
    public boolean allRequiredPassed() {
        return items.stream()
            .filter(DmPreflightCheckItem::required)
            .allMatch(i -> i.result() == DmPreflightCheckResult.PASSED);
    }

    public String getId()           { return id; }
    public String getTenantId()     { return tenantId; }
    public String getWorkspaceId()  { return workspaceId; }
    public String getCampaignId()   { return campaignId; }
    public List<DmPreflightCheckItem> getItems() { return items; }
    public DmPreflightStatus getStatus() { return status; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public Instant getCreatedAt()   { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmPreflightChecklist)) return false;
        return id.equals(((DmPreflightChecklist) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmPreflightChecklist{id='" + id + "', status=" + status + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, campaignId;
        private List<DmPreflightCheckItem> items = List.of();
        private DmPreflightStatus status;
        private Instant evaluatedAt, createdAt;

        public Builder id(String v)             { this.id = v; return this; }
        public Builder tenantId(String v)       { this.tenantId = v; return this; }
        public Builder workspaceId(String v)    { this.workspaceId = v; return this; }
        public Builder campaignId(String v)     { this.campaignId = v; return this; }
        public Builder items(List<DmPreflightCheckItem> v) { this.items = v; return this; }
        public Builder status(DmPreflightStatus v) { this.status = v; return this; }
        public Builder evaluatedAt(Instant v)   { this.evaluatedAt = v; return this; }
        public Builder createdAt(Instant v)     { this.createdAt = v; return this; }

        public DmPreflightChecklist build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(items, "items must not be null");
            return new DmPreflightChecklist(this);
        }
    }

    /**
     * An individual check item in the preflight checklist.
     */
    public record DmPreflightCheckItem(
        String name,
        String description,
        boolean required,
        DmPreflightCheckResult result,
        String message
    ) {
        public DmPreflightCheckItem {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(result, "result must not be null");
        }
    }
}
