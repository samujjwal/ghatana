/**
 * @fileoverview Metric Type Definitions
 *
 * Comprehensive type definitions for the metrics system.
 * These types ensure type safety and consistency across the analytics platform.
 *
 * @module analytics/types
 * @since 2.0.0
 */

/**
 * Metric namespace grouping related metrics
 */
export interface MetricNamespace {
  /** Unique identifier for the namespace */
  id: string;
  /** Human-readable name */
  name: string;
  /** Detailed description */
  description: string;
  /** Icon identifier */
  icon: string;
  /** Category for grouping */
  category: 'performance' | 'network' | 'usage' | 'business';
  /** Metrics in this namespace */
  metrics: Record<string, MetricDefinition>;
  /** Relationships to other namespaces */
  relationships: MetricRelationship[];
}

/**
 * Complete metric definition with all metadata
 */
export interface MetricDefinition {
  /** Unique metric identifier */
  id: string;
  /** Human-readable name */
  name: string;
  /** Detailed description */
  description: string;
  /** Unit of measurement */
  unit: string;
  /** Format type for display */
  format: 'number' | 'percentage' | 'duration' | 'bytes' | 'rate';
  /** Decimal precision */
  precision: number;
  /** Aggregation strategy */
  aggregation: AggregationStrategy;
  /** Threshold configuration */
  thresholds: ThresholdConfig;
  /** Insight configuration */
  insights: InsightConfig;
  /** Visualization configuration */
  visualization: VisualizationConfig;
  /** Data sources for this metric */
  sources: DataSource[];
}

/**
 * Aggregation strategy for metric calculations
 */
export interface AggregationStrategy {
  /** Type of aggregation */
  type: 'sum' | 'average' | 'count' | 'percentile' | 'ratio' | 'min' | 'max';
  /** Percentile value (for percentile type) */
  value?: number;
  /** Time window for aggregation */
  window: string;
  /** Additional aggregation parameters */
  params?: Record<string, any>;
}

/**
 * Threshold configuration for alerts and coloring
 */
export interface ThresholdConfig {
  /** Good threshold value */
  good: number;
  /** Needs improvement threshold value */
  needsImprovement: number;
  /** Poor threshold value */
  poor: number;
  /** Direction of optimization */
  direction: 'lower-is-better' | 'higher-is-better';
}

/**
 * Insight configuration for metric explanations
 */
export interface InsightConfig {
  /** Title for the insight */
  title: string;
  /** Detailed description */
  description: string;
  /** Actionable recommendations */
  recommendations: string[];
  /** Related documentation links */
  documentation?: string[];
}

/**
 * Visualization configuration for metric display
 */
export interface VisualizationConfig {
  /** Type of visualization */
  type: 'sparkline' | 'line' | 'bar' | 'area' | 'gauge' | 'heatmap' | 'scatter';
  /** Color scheme */
  color: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info';
  /** Show trend indicator */
  showTrend: boolean;
  /** Show threshold indicators */
  showThresholds: boolean;
  /** Additional visualization options */
  options?: Record<string, any>;
}

/**
 * Data source configuration
 */
export interface DataSource {
  /** Source type */
  type: 'page' | 'network' | 'usage' | 'business' | 'custom';
  /** Path to data in source */
  path: string;
  /** Whether this source is required */
  required: boolean;
  /** Transformation function */
  transform?: string;
}

/**
 * Relationship between metrics
 */
export interface MetricRelationship {
  /** Related metric ID */
  metricId: string;
  /** Relationship type */
  type: 'correlation' | 'causation' | 'dependency' | 'association';
  /** Relationship strength (0-1) */
  strength: number;
  /** Description of relationship */
  description: string;
}

/**
 * Time range configuration
 */
export interface TimeRange {
  /** Start timestamp */
  from: number;
  /** End timestamp */
  to: number;
  /** Predefined range type */
  preset?: '1h' | '24h' | '7d' | '30d' | 'custom';
}

/**
 * Metric data point
 */
export interface MetricDataPoint {
  /** Timestamp */
  timestamp: number;
  /** Value */
  value: number;
  /** Additional metadata */
  metadata?: Record<string, any>;
}

/**
 * Metric data with metadata
 */
export interface MetricData {
  /** Metric ID */
  metricId: string;
  /** Data points */
  data: MetricDataPoint[];
  /** Current value */
  current: number;
  /** Previous value for comparison */
  previous?: number;
  /** Trend information */
  trend?: MetricTrend;
  /** Threshold status */
  status: 'good' | 'needs-improvement' | 'poor';
  /** Last updated timestamp */
  lastUpdated: number;
}

/**
 * Metric trend information
 */
export interface MetricTrend {
  /** Trend direction */
  direction: 'up' | 'down' | 'stable';
  /** Percentage change */
  percentage: number;
  /** Absolute change */
  absolute: number;
  /** Significance level */
  significance: 'high' | 'medium' | 'low';
}

/**
 * Dashboard context
 */
export interface DashboardContext {
  /** Current scope */
  scope: 'global' | 'domain' | 'page';
  /** Entity ID (domain or page) */
  entity?: string;
  /** Time range */
  timeRange: TimeRange;
  /** Applied filters */
  filters: Record<string, any>;
}

/**
 * Metric query configuration
 */
export interface MetricQuery {
  /** Metric ID */
  metricId: string;
  /** Aggregation override */
  aggregation?: Partial<AggregationStrategy>;
  /** Filters to apply */
  filters?: Record<string, any>;
  /** Group by fields */
  groupBy?: string[];
}

/**
 * Metric calculation result
 */
export interface MetricResult {
  /** Metric ID */
  metricId: string;
  /** Calculated value */
  value: number;
  /** Status based on thresholds */
  status: 'good' | 'needs-improvement' | 'poor';
  /** Trend information */
  trend?: MetricTrend;
  /** Confidence score */
  confidence: number;
  /** Sample size */
  sampleSize: number;
  /** Calculation metadata */
  metadata: Record<string, any>;
}

/**
 * Alert configuration
 */
export interface AlertConfig {
  /** Alert ID */
  id: string;
  /** Metric ID to monitor */
  metricId: string;
  /** Alert condition */
  condition: AlertCondition;
  /** Alert severity */
  severity: 'info' | 'warning' | 'error' | 'critical';
  /** Alert message template */
  message: string;
  /** Notification channels */
  channels: NotificationChannel[];
  /** Alert enabled status */
  enabled: boolean;
}

/**
 * Alert condition
 */
export interface AlertCondition {
  /** Comparison operator */
  operator: '>' | '<' | '>=' | '<=' | '==' | '!=';
  /** Threshold value */
  threshold: number;
  /** Duration condition must persist */
  duration?: number;
  /** Additional conditions */
  conditions?: Record<string, any>;
}

/**
 * Notification channel
 */
export interface NotificationChannel {
  /** Channel type */
  type: 'email' | 'slack' | 'webhook' | 'in-app';
  /** Channel configuration */
  config: Record<string, any>;
  /** Channel enabled status */
  enabled: boolean;
}

/**
 * Dashboard configuration
 */
export interface DashboardConfig {
  /** Dashboard ID */
  id: string;
  /** Dashboard name */
  name: string;
  /** Dashboard description */
  description: string;
  /** Layout configuration */
  layout: DashboardLayout;
  /** Widgets configuration */
  widgets: WidgetConfig[];
  /** Refresh interval */
  refreshInterval: number;
  /** Default time range */
  defaultTimeRange: TimeRange;
}

/**
 * Dashboard layout
 */
export interface DashboardLayout {
  /** Layout type */
  type: 'grid' | 'flex' | 'custom';
  /** Layout configuration */
  config: Record<string, any>;
}

/**
 * Widget configuration
 */
export interface WidgetConfig {
  /** Widget ID */
  id: string;
  /** Widget type */
  type: 'metric' | 'chart' | 'table' | 'text' | 'alert';
  /** Widget position */
  position: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  /** Widget configuration */
  config: Record<string, any>;
  /** Widget data sources */
  dataSources: string[];
}

/**
 * Export configuration
 */
export interface ExportConfig {
  /** Export format */
  format: 'csv' | 'json' | 'pdf' | 'xlsx';
  /** Time range */
  timeRange: TimeRange;
  /** Metrics to include */
  metrics: string[];
  /** Export options */
  options: Record<string, any>;
}

/**
 * Comparison configuration
 */
export interface ComparisonConfig {
  /** Comparison type */
  type: 'period-over-period' | 'domain-comparison' | 'page-comparison';
  /** Primary entity */
  primary: string;
  /** Comparison entities */
  comparison: string[];
  /** Metrics to compare */
  metrics: string[];
  /** Time range */
  timeRange: TimeRange;
}
