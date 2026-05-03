package com.ghatana.digitalmarketing.domain.attribution;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a last-click or UTM-source attribution record.
 *
 * @doc.type class
 * @doc.purpose Tracks conversion attribution for MVP last-click model (DMOS-F2-017)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAttributionRecord {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String visitorId;
    private final String sessionId;
    private final String conversionEventId;
    private final String attributedSource;
    private final String attributedMedium;
    private final String attributedCampaign;
    private final String attributedContent;
    private final String attributedTerm;
    private final DmAttributionModel model;
    private final double attributionWeight;
    private final Instant convertedAt;
    private final Instant createdAt;

    private DmAttributionRecord(Builder b) {
        this.id                  = b.id;
        this.tenantId            = b.tenantId;
        this.workspaceId         = b.workspaceId;
        this.visitorId           = b.visitorId;
        this.sessionId           = b.sessionId;
        this.conversionEventId   = b.conversionEventId;
        this.attributedSource    = b.attributedSource;
        this.attributedMedium    = b.attributedMedium;
        this.attributedCampaign  = b.attributedCampaign;
        this.attributedContent   = b.attributedContent;
        this.attributedTerm      = b.attributedTerm;
        this.model               = b.model;
        this.attributionWeight   = b.attributionWeight;
        this.convertedAt         = b.convertedAt;
        this.createdAt           = b.createdAt;
    }

    public String getId()                 { return id; }
    public String getTenantId()           { return tenantId; }
    public String getWorkspaceId()        { return workspaceId; }
    public String getVisitorId()          { return visitorId; }
    public String getSessionId()          { return sessionId; }
    public String getConversionEventId()  { return conversionEventId; }
    public String getAttributedSource()   { return attributedSource; }
    public String getAttributedMedium()   { return attributedMedium; }
    public String getAttributedCampaign() { return attributedCampaign; }
    public String getAttributedContent()  { return attributedContent; }
    public String getAttributedTerm()     { return attributedTerm; }
    public DmAttributionModel getModel()  { return model; }
    public double getAttributionWeight()  { return attributionWeight; }
    public Instant getConvertedAt()       { return convertedAt; }
    public Instant getCreatedAt()         { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAttributionRecord)) return false;
        return id.equals(((DmAttributionRecord) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmAttributionRecord{id='" + id + "', model=" + model + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, visitorId, sessionId, conversionEventId;
        private String attributedSource, attributedMedium, attributedCampaign, attributedContent, attributedTerm;
        private DmAttributionModel model;
        private double attributionWeight = 1.0;
        private Instant convertedAt, createdAt;

        public Builder id(String v)                  { this.id = v; return this; }
        public Builder tenantId(String v)            { this.tenantId = v; return this; }
        public Builder workspaceId(String v)         { this.workspaceId = v; return this; }
        public Builder visitorId(String v)           { this.visitorId = v; return this; }
        public Builder sessionId(String v)           { this.sessionId = v; return this; }
        public Builder conversionEventId(String v)   { this.conversionEventId = v; return this; }
        public Builder attributedSource(String v)    { this.attributedSource = v; return this; }
        public Builder attributedMedium(String v)    { this.attributedMedium = v; return this; }
        public Builder attributedCampaign(String v)  { this.attributedCampaign = v; return this; }
        public Builder attributedContent(String v)   { this.attributedContent = v; return this; }
        public Builder attributedTerm(String v)      { this.attributedTerm = v; return this; }
        public Builder model(DmAttributionModel v)   { this.model = v; return this; }
        public Builder attributionWeight(double v)   { this.attributionWeight = v; return this; }
        public Builder convertedAt(Instant v)        { this.convertedAt = v; return this; }
        public Builder createdAt(Instant v)          { this.createdAt = v; return this; }

        public DmAttributionRecord build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(model, "model must not be null");
            Objects.requireNonNull(convertedAt, "convertedAt must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmAttributionRecord(this);
        }
    }
}
