package com.ghatana.products.yappc.domain.enums;

/**
 * Widget type enumeration for dashboard components.
 *
 * <p><b>Purpose</b><br>
 * Defines the types of widgets that can be added to dashboards, each rendering different
 * data visualization patterns.
 *
 * <p><b>Widget Types</b><br>
 * - KPI_CARD: Single metric with trend indicator
 * - STATS_DASHBOARD: Multiple metrics in grid layout
 * - TIMESERIES: Line/bar chart showing metric over time
 * - TABLE: Tabular data display
 * - LIST: Simple list view
 * - CHART: Generic chart (pie, donut, radar, etc.)
 * - GAUGE: Progress/threshold gauge visualization
 * - HEATMAP: 2D density/intensity map
 * - MAP: Geographic or topology map
 * - ALERT_FEED: Real-time alert/incident stream
 * - CUSTOM: Custom widget implementation
 *
 * <p><b>Usage</b><br>
 * Used in Widget entity to determine rendering strategy.
 *
 * @see com.ghatana.products.yappc.domain.model.Widget
 * @doc.type enum
 * @doc.purpose Widget visualization types
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum WidgetType {
    /**
     * KPI card - single metric with trend.
     * Shows current value, change over time, and visual indicator (up/down arrow).
     */
    KPI_CARD,

    /**
     * Stats dashboard - multiple metrics in grid.
     * Displays several KPIs in a compact grid layout.
     */
    STATS_DASHBOARD,

    /**
     * Time series chart - metric over time.
     * Line or bar chart showing temporal trends.
     */
    TIMESERIES,

    /**
     * Table view - tabular data.
     * Sortable, filterable table for detailed data.
     */
    TABLE,

    /**
     * List view - simple list.
     * Vertical list of items (e.g., recent incidents, alerts).
     */
    LIST,

    /**
     * Generic chart - various chart types.
     * Pie, donut, radar, scatter, etc.
     */
    CHART,

    /**
     * Gauge - progress/threshold visualization.
     * Shows metric against target or threshold.
     */
    GAUGE,

    /**
     * Heatmap - 2D intensity visualization.
     * Shows density or correlation across two dimensions.
     */
    HEATMAP,

    /**
     * Map - geographic or topology map.
     * Shows geographic distribution or network topology.
     */
    MAP,

    /**
     * Alert feed - real-time alert stream.
     * Live feed of alerts, incidents, or notifications.
     */
    ALERT_FEED,

    /**
     * Custom widget - custom implementation.
     * For specialized widgets not covered by standard types.
     */
    CUSTOM
}
