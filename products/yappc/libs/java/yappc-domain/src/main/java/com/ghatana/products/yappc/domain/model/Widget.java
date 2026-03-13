package com.ghatana.products.yappc.domain.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.ghatana.products.yappc.domain.Identifiable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Widget entity representing dashboard widgets.
 *
 * <p>
 * <b>Purpose</b><br>
 * Widget defines individual visualization components displayed on dashboards.
 * Each widget renders specific data (KPIs, charts, tables) with custom
 * configuration.
 *
 * <p>
 * <b>Widget Types</b><br>
 * - KPI_CARD: Single metric with trend indicator - STATS_DASHBOARD: Multiple
 * related metrics - TIMESERIES: Line/area chart over time - TABLE: Tabular data
 * display - CHART: Bar/pie/doughnut charts - HEATMAP: Matrix visualization -
 * GAUGE: Progress/gauge indicators - ALERT_LIST: Security alert feed -
 * ACTIVITY_FEED: Recent activity stream
 *
 * <p>
 * <b>Data Source Configuration</b><br>
 * config JSONB defines data source and visualization: - data_source: Which
 * entity/metric to display - refresh_interval: Auto-refresh frequency (seconds)
 * - chart_type: Visualization style - filters: Data filtering criteria -
 * aggregation: How to aggregate data (sum, avg, count)
 *
 * <p>
 * <b>Layout</b><br>
 * Grid-based layout using position and size: - position_x, position_y: Top-left
 * corner - width, height: Widget dimensions in grid units
 *
 * @see Dashboard
 * @see WidgetType
 * @doc.type class
 * @doc.purpose Dashboard widget definition entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "widget", indexes = {
    @Index(name = "idx_widget_dashboard_position",
            columnList = "dashboard_id, position_x, position_y")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Widget implements Identifiable<UUID> {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "dashboard_id", nullable = false)
    private UUID dashboardId;

    @Column(name = "widget_type", nullable = false, length = 50)
    private String widgetType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "position_x", nullable = false)
    private Integer positionX;

    @Column(name = "position_y", nullable = false)
    private Integer positionY;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Type(JsonBinaryType.class)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
