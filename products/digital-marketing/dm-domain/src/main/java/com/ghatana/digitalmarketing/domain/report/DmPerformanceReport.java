package com.ghatana.digitalmarketing.domain.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing a generated performance report.
 *
 * @doc.type class
 * @doc.purpose Domain entity for basic performance report generation (DMOS-F2-019)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmPerformanceReport {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String title;
    private final DmReportPeriod period;
    private final List<DmReportSection> sections;
    private final DmReportStatus status;
    private final String generatedByActor;
    private final Instant generatedAt;
    private final Instant createdAt;

    private DmPerformanceReport(Builder b) {
        this.id               = b.id;
        this.tenantId         = b.tenantId;
        this.workspaceId      = b.workspaceId;
        this.title            = b.title;
        this.period           = b.period;
        this.sections         = List.copyOf(b.sections);
        this.status           = b.status;
        this.generatedByActor = b.generatedByActor;
        this.generatedAt      = b.generatedAt;
        this.createdAt        = b.createdAt;
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getTitle()             { return title; }
    public DmReportPeriod getPeriod()    { return period; }
    public List<DmReportSection> getSections() { return sections; }
    public DmReportStatus getStatus()    { return status; }
    public String getGeneratedByActor()  { return generatedByActor; }
    public Instant getGeneratedAt()      { return generatedAt; }
    public Instant getCreatedAt()        { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmPerformanceReport)) return false;
        return id.equals(((DmPerformanceReport) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmPerformanceReport{id='" + id + "', title='" + title + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, title, generatedByActor;
        private DmReportPeriod period;
        private List<DmReportSection> sections = List.of();
        private DmReportStatus status;
        private Instant generatedAt, createdAt;

        public Builder id(String v)               { this.id = v; return this; }
        public Builder tenantId(String v)         { this.tenantId = v; return this; }
        public Builder workspaceId(String v)      { this.workspaceId = v; return this; }
        public Builder title(String v)            { this.title = v; return this; }
        public Builder period(DmReportPeriod v)   { this.period = v; return this; }
        public Builder sections(List<DmReportSection> v) { this.sections = v; return this; }
        public Builder status(DmReportStatus v)   { this.status = v; return this; }
        public Builder generatedByActor(String v) { this.generatedByActor = v; return this; }
        public Builder generatedAt(Instant v)     { this.generatedAt = v; return this; }
        public Builder createdAt(Instant v)       { this.createdAt = v; return this; }

        public DmPerformanceReport build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            Objects.requireNonNull(period, "period must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(sections, "sections must not be null");
            return new DmPerformanceReport(this);
        }
    }

    /** Report time period. */
    public record DmReportPeriod(Instant from, Instant to) {
        public DmReportPeriod {
            Objects.requireNonNull(from, "from must not be null");
            Objects.requireNonNull(to, "to must not be null");
        }
    }

    /** A section within the report. */
    public record DmReportSection(String title, String content, String sectionType) {
        public DmReportSection {
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }
    }
}
