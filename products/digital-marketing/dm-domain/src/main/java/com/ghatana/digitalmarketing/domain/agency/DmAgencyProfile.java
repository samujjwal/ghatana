package com.ghatana.digitalmarketing.domain.agency;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an agency managing multiple client tenants.
 *
 * @doc.type class
 * @doc.purpose Domain entity for agency mode (DMOS-F4-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAgencyProfile {

    private final String id;
    private final String agencyTenantId;
    private final String displayName;
    private final List<String> managedTenantIds;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmAgencyProfile(Builder b) {
        this.id               = b.id;
        this.agencyTenantId   = b.agencyTenantId;
        this.displayName      = b.displayName;
        this.managedTenantIds = List.copyOf(b.managedTenantIds);
        this.active           = b.active;
        this.createdAt        = b.createdAt;
        this.updatedAt        = b.updatedAt;
    }

    public boolean manages(String tenantId) {
        return managedTenantIds.contains(tenantId);
    }

    public String getId()                   { return id; }
    public String getAgencyTenantId()       { return agencyTenantId; }
    public String getDisplayName()          { return displayName; }
    public List<String> getManagedTenantIds() { return managedTenantIds; }
    public boolean isActive()               { return active; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getUpdatedAt()           { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAgencyProfile)) return false;
        return id.equals(((DmAgencyProfile) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmAgencyProfile{id='" + id + "', agency='" + agencyTenantId + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, agencyTenantId, displayName;
        private List<String> managedTenantIds = List.of();
        private boolean active = true;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder agencyTenantId(String v)          { this.agencyTenantId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder managedTenantIds(List<String> v)  { this.managedTenantIds = v; return this; }
        public Builder active(boolean v)                 { this.active = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmAgencyProfile build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (agencyTenantId == null || agencyTenantId.isBlank()) throw new IllegalArgumentException("agencyTenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(managedTenantIds, "managedTenantIds must not be null");
            return new DmAgencyProfile(this);
        }
    }
}
