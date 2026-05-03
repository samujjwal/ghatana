package com.ghatana.digitalmarketing.domain.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an MVP analytics dashboard definition.
 *
 * @doc.type class
 * @doc.purpose Domain entity for analytics dashboard configuration (DMOS-F2-018)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAnalyticsDashboard {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String name;
    private final String description;
    private final List<DmDashboardWidget> widgets;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmAnalyticsDashboard(Builder b) {
        this.id          = b.id;
        this.tenantId    = b.tenantId;
        this.workspaceId = b.workspaceId;
        this.name        = b.name;
        this.description = b.description;
        this.widgets     = List.copyOf(b.widgets);
        this.createdAt   = b.createdAt;
        this.updatedAt   = b.updatedAt;
    }

    public String getId()           { return id; }
    public String getTenantId()     { return tenantId; }
    public String getWorkspaceId()  { return workspaceId; }
    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public List<DmDashboardWidget> getWidgets() { return widgets; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAnalyticsDashboard)) return false;
        return id.equals(((DmAnalyticsDashboard) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmAnalyticsDashboard{id='" + id + "', name='" + name + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, name, description;
        private List<DmDashboardWidget> widgets = List.of();
        private Instant createdAt, updatedAt;

        public Builder id(String v)             { this.id = v; return this; }
        public Builder tenantId(String v)       { this.tenantId = v; return this; }
        public Builder workspaceId(String v)    { this.workspaceId = v; return this; }
        public Builder name(String v)           { this.name = v; return this; }
        public Builder description(String v)    { this.description = v; return this; }
        public Builder widgets(List<DmDashboardWidget> v) { this.widgets = v; return this; }
        public Builder createdAt(Instant v)     { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)     { this.updatedAt = v; return this; }

        public DmAnalyticsDashboard build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(widgets, "widgets must not be null");
            return new DmAnalyticsDashboard(this);
        }
    }

    /**
     * A single widget in the analytics dashboard.
     */
    public record DmDashboardWidget(
        String widgetId,
        String title,
        String metricKey,
        String widgetType
    ) {
        public DmDashboardWidget {
            Objects.requireNonNull(widgetId, "widgetId must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(metricKey, "metricKey must not be null");
            Objects.requireNonNull(widgetType, "widgetType must not be null");
        }
    }
}
