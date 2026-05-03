package com.ghatana.digitalmarketing.domain.lead;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a captured lead from a landing page form.
 *
 * @doc.type class
 * @doc.purpose Domain entity for lead capture and CRM-lite integration (DMOS-F2-011)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmLeadCapture {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String landingPageId;
    private final String email;
    private final String name;
    private final String phone;
    private final Map<String, String> customFields;
    private final String utmSource;
    private final String utmMedium;
    private final String utmCampaign;
    private final DmLeadStatus status;
    private final Instant capturedAt;

    private DmLeadCapture(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.landingPageId = b.landingPageId;
        this.email         = b.email;
        this.name          = b.name;
        this.phone         = b.phone;
        this.customFields  = Map.copyOf(b.customFields);
        this.utmSource     = b.utmSource;
        this.utmMedium     = b.utmMedium;
        this.utmCampaign   = b.utmCampaign;
        this.status        = b.status;
        this.capturedAt    = b.capturedAt;
    }

    public String getId()            { return id; }
    public String getTenantId()      { return tenantId; }
    public String getWorkspaceId()   { return workspaceId; }
    public String getLandingPageId() { return landingPageId; }
    public String getEmail()         { return email; }
    public String getName()          { return name; }
    public String getPhone()         { return phone; }
    public Map<String, String> getCustomFields() { return customFields; }
    public String getUtmSource()     { return utmSource; }
    public String getUtmMedium()     { return utmMedium; }
    public String getUtmCampaign()   { return utmCampaign; }
    public DmLeadStatus getStatus()  { return status; }
    public Instant getCapturedAt()   { return capturedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmLeadCapture)) return false;
        return id.equals(((DmLeadCapture) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmLeadCapture{id='" + id + "', email='" + email + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, landingPageId, email, name, phone;
        private Map<String, String> customFields = Map.of();
        private String utmSource, utmMedium, utmCampaign;
        private DmLeadStatus status;
        private Instant capturedAt;

        public Builder id(String v)              { this.id = v; return this; }
        public Builder tenantId(String v)        { this.tenantId = v; return this; }
        public Builder workspaceId(String v)     { this.workspaceId = v; return this; }
        public Builder landingPageId(String v)   { this.landingPageId = v; return this; }
        public Builder email(String v)           { this.email = v; return this; }
        public Builder name(String v)            { this.name = v; return this; }
        public Builder phone(String v)           { this.phone = v; return this; }
        public Builder customFields(Map<String, String> v) { this.customFields = v; return this; }
        public Builder utmSource(String v)       { this.utmSource = v; return this; }
        public Builder utmMedium(String v)       { this.utmMedium = v; return this; }
        public Builder utmCampaign(String v)     { this.utmCampaign = v; return this; }
        public Builder status(DmLeadStatus v)    { this.status = v; return this; }
        public Builder capturedAt(Instant v)     { this.capturedAt = v; return this; }

        public DmLeadCapture build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (email == null || email.isBlank()) throw new IllegalArgumentException("email must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(capturedAt, "capturedAt must not be null");
            Objects.requireNonNull(customFields, "customFields must not be null");
            return new DmLeadCapture(this);
        }
    }
}
