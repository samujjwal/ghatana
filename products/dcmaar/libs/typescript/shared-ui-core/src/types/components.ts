/**
 * Shared Component Types
 * Used across all DCMAAR apps
 */

/**
 * Status variants for StatusBadge component
 */
export type StatusVariant =
  | 'online'
  | 'offline'
  | 'connecting'
  | 'error'
  | 'warning'
  | 'success'
  | 'info';

/**
 * StatusBadge component props
 */
export interface StatusBadgeProps {
  status: StatusVariant;
  label?: string;
  pulse?: boolean;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

/**
 * Connection status data
 */
export interface ConnectionStatus {
  isConnected: boolean;
  lastConnectionTime: string;
  uptime: string;
  serverAddress: string;
  latency?: number;
  metrics?: {
    totalEvents: number;
    errorEvents: number;
    successRate: number;
    queueSize: number;
    avgResponseTime: number;
  };
}

/**
 * Activity item
 */
export interface Activity {
  id: string;
  type: string;
  timestamp: string;
  source: string;
  details?: string;
  timeAgo: string;
}

/**
 * Metrics data structure
 */
export interface MetricsData {
  totalEvents: number;
  eventTypes: Record<string, number>;
  metricsOverTime: Array<{ timestamp: string; count: number }>;
  performanceMetrics: {
    avgLatency: number;
    p95Latency: number;
    p99Latency: number;
  };
  topCategories: Array<{ name: string; count: number }>;
}

/**
 * Time range options
 */
export type TimeRange = 'last1h' | 'last24h' | 'last7d' | 'last30d';

/**
 * Button variants
 */
export type ButtonVariant = 'primary' | 'secondary' | 'tertiary' | 'danger' | 'ghost';

/**
 * Component sizes
 */
export type ComponentSize = 'sm' | 'md' | 'lg';

/**
 * Card variants
 */
export type CardVariant = 'default' | 'solid';

/**
 * Badge variants
 */
export type BadgeVariant = 'primary' | 'success' | 'warning' | 'error' | 'info';
