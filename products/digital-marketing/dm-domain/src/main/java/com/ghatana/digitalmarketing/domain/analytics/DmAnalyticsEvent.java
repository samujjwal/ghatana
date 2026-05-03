package com.ghatana.digitalmarketing.domain.analytics;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a collected analytics event.
 *
 * @doc.type class
 * @doc.purpose Domain entity for MVP analytics event collection (DMOS-F2-016)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAnalyticsEvent {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String sessionId;
    private final String eventType;
    private final String sourceUrl;
    private final String utmSource;
    private final String utmMedium;
    private final String utmCampaign;
    private final String utmContent;
    private final String utmTerm;
    private final String visitorId;
    private final Map<String, String> properties;
    private final Instant occurredAt;

    private DmAnalyticsEvent(Builder b) {
        this.id          = b.id;
        this.tenantId    = b.tenantId;
        this.workspaceId = b.workspaceId;
        this.sessionId   = b.sessionId;
        this.eventType   = b.eventType;
        this.sourceUrl   = b.sourceUrl;
        this.utmSource   = b.utmSource;
        this.utmMedium   = b.utmMedium;
        this.utmCampaign = b.utmCampaign;
        this.utmContent  = b.utmContent;
        this.utmTerm     = b.utmTerm;
        this.visitorId   = b.visitorId;
        this.properties  = Map.copyOf(b.properties);
        this.occurredAt  = b.occurredAt;
    }

    public String getId()           { return id; }
    public String getTenantId()     { return tenantId; }
    public String getWorkspaceId()  { return workspaceId; }
    public String getSessionId()    { return sessionId; }
    public String getEventType()    { return eventType; }
    public String getSourceUrl()    { return sourceUrl; }
    public String getUtmSource()    { return utmSource; }
    public String getUtmMedium()    { return utmMedium; }
    public String getUtmCampaign()  { return utmCampaign; }
    public String getUtmContent()   { return utmContent; }
    public String getUtmTerm()      { return utmTerm; }
    public String getVisitorId()    { return visitorId; }
    public Map<String, String> getProperties() { return properties; }
    public Instant getOccurredAt()  { return occurredAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAnalyticsEvent)) return false;
        return id.equals(((DmAnalyticsEvent) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmAnalyticsEvent{id='" + id + "', type='" + eventType + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, sessionId, eventType;
        private String sourceUrl, utmSource, utmMedium, utmCampaign, utmContent, utmTerm, visitorId;
        private Map<String, String> properties = Map.of();
        private Instant occurredAt;

        public Builder id(String v)           { this.id = v; return this; }
        public Builder tenantId(String v)     { this.tenantId = v; return this; }
        public Builder workspaceId(String v)  { this.workspaceId = v; return this; }
        public Builder sessionId(String v)    { this.sessionId = v; return this; }
        public Builder eventType(String v)    { this.eventType = v; return this; }
        public Builder sourceUrl(String v)    { this.sourceUrl = v; return this; }
        public Builder utmSource(String v)    { this.utmSource = v; return this; }
        public Builder utmMedium(String v)    { this.utmMedium = v; return this; }
        public Builder utmCampaign(String v)  { this.utmCampaign = v; return this; }
        public Builder utmContent(String v)   { this.utmContent = v; return this; }
        public Builder utmTerm(String v)      { this.utmTerm = v; return this; }
        public Builder visitorId(String v)    { this.visitorId = v; return this; }
        public Builder properties(Map<String, String> v) { this.properties = v; return this; }
        public Builder occurredAt(Instant v)  { this.occurredAt = v; return this; }

        public DmAnalyticsEvent build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            Objects.requireNonNull(properties, "properties must not be null");
            return new DmAnalyticsEvent(this);
        }
    }
}
