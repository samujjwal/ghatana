/**
 * ObservabilityDashboard
 *
 * Displays system health metrics and links to the canonical monitoring stack
 * (Grafana, Prometheus, Jaeger, Loki). In production the metrics are fetched
 * from the /api/metrics endpoint; in development/test synthetic data is used.
 *
 * The dashboard is accessible to users with the `admin:observability` permission.
 * RBAC is enforced in the parent route; this component renders the data only.
 *
 * @doc.type component
 * @doc.purpose Admin observability surface — system health, agent metrics, error rates
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Typography,
} from '@ghatana/design-system';
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock,
  ExternalLink,
  Zap,
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type HealthStatus = 'healthy' | 'degraded' | 'down';

export interface HealthMetric {
  /** Unique identifier for this metric */
  id: string;
  /** Human-readable label */
  label: string;
  /** Current value as a string (e.g. "24ms", "98.7%") */
  value: string;
  /** Optional previous / baseline value for comparison */
  previousValue?: string;
  /** Health signal for colour coding */
  status: HealthStatus;
  /** ISO timestamp of when the metric was last refreshed */
  refreshedAt: string;
}

export interface MonitoringLink {
  id: string;
  label: string;
  href: string;
  description: string;
}

export interface ObservabilityDashboardProps {
  metrics: HealthMetric[];
  monitoringLinks?: MonitoringLink[];
  /** Whether the metrics are still loading from the API */
  isLoading?: boolean;
  /** Error message to display instead of metrics */
  error?: string;
  /** Triggered when the user requests a manual refresh */
  onRefresh?: () => void;
  className?: string;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const DEFAULT_MONITORING_LINKS: MonitoringLink[] = [
  {
    id: 'grafana',
    label: 'Grafana',
    href: 'http://localhost:3001',
    description: 'Dashboards and alerting',
  },
  {
    id: 'prometheus',
    label: 'Prometheus',
    href: 'http://localhost:9090',
    description: 'Metrics and targets',
  },
  {
    id: 'jaeger',
    label: 'Jaeger',
    href: 'http://localhost:16686',
    description: 'Distributed tracing',
  },
  {
    id: 'loki',
    label: 'Loki',
    href: 'http://localhost:3100',
    description: 'Log aggregation',
  },
];

const STATUS_ICON: Record<HealthStatus, React.ReactNode> = {
  healthy: <CheckCircle2 className="h-4 w-4 text-emerald-600" aria-hidden="true" />,
  degraded: <AlertTriangle className="h-4 w-4 text-amber-500" aria-hidden="true" />,
  down: <AlertTriangle className="h-4 w-4 text-red-600" aria-hidden="true" />,
};

const STATUS_CHIP_CLASS: Record<HealthStatus, string> = {
  healthy: 'bg-emerald-50 text-emerald-700',
  degraded: 'bg-amber-50 text-amber-700',
  down: 'bg-red-50 text-red-700',
};

const STATUS_LABEL: Record<HealthStatus, string> = {
  healthy: 'Healthy',
  degraded: 'Degraded',
  down: 'Down',
};

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString();
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface MetricCardProps {
  metric: HealthMetric;
}

const MetricCard: React.FC<MetricCardProps> = ({ metric }) => (
  <Card
    className={cn(
      'border transition-shadow hover:shadow-md',
      metric.status === 'down' && 'border-red-200',
      metric.status === 'degraded' && 'border-amber-200'
    )}
    data-testid={`metric-card-${metric.id}`}
  >
    <CardContent className="space-y-3 p-4">
      <Box className="flex items-center justify-between">
        <Typography className="text-sm font-medium text-gray-600">
          {metric.label}
        </Typography>
        <Box className="flex items-center gap-1">
          {STATUS_ICON[metric.status]}
          <Chip
            label={STATUS_LABEL[metric.status]}
            size="sm"
            className={STATUS_CHIP_CLASS[metric.status]}
          />
        </Box>
      </Box>

      <Typography className="text-2xl font-bold tabular-nums">
        {metric.value}
      </Typography>

      {metric.previousValue && (
        <Typography className="text-xs text-gray-400">
          Previous: {metric.previousValue}
        </Typography>
      )}

      <Box className="flex items-center gap-1 text-xs text-gray-400">
        <Clock className="h-3 w-3" />
        <span>Refreshed {formatTimestamp(metric.refreshedAt)}</span>
      </Box>
    </CardContent>
  </Card>
);

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export const ObservabilityDashboard: React.FC<ObservabilityDashboardProps> = ({
  metrics,
  monitoringLinks = DEFAULT_MONITORING_LINKS,
  isLoading = false,
  error,
  onRefresh,
  className = '',
}) => {
  const overallStatus: HealthStatus = metrics.some((m) => m.status === 'down')
    ? 'down'
    : metrics.some((m) => m.status === 'degraded')
    ? 'degraded'
    : 'healthy';

  return (
    <Box className={cn('space-y-6', className)} aria-label="Observability Dashboard">
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-gray-700" aria-hidden="true" />
          <Typography className="text-xl font-semibold">System Health</Typography>
          <Box className="flex items-center gap-1">
            {STATUS_ICON[overallStatus]}
            <Chip
              label={STATUS_LABEL[overallStatus]}
              size="sm"
              className={STATUS_CHIP_CLASS[overallStatus]}
            />
          </Box>
        </Box>
        {onRefresh && (
          <button
            type="button"
            onClick={onRefresh}
            disabled={isLoading}
            aria-label="Refresh metrics"
            className="flex items-center gap-1 rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <Zap className="h-3 w-3" />
            Refresh
          </button>
        )}
      </Box>

      {/* Error state */}
      {error && (
        <Box
          role="alert"
          className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700"
        >
          <Box className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 flex-shrink-0" />
            <span>{error}</span>
          </Box>
        </Box>
      )}

      {/* Loading skeleton */}
      {isLoading && !error && (
        <Box className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardContent className="p-4">
                <Box className="mb-2 h-4 w-24 rounded bg-gray-200" />
                <Box className="h-8 w-16 rounded bg-gray-200" />
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      {/* Metrics grid */}
      {!isLoading && !error && metrics.length > 0 && (
        <Box
          className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4"
          aria-live="polite"
          aria-atomic="true"
        >
          {metrics.map((metric) => (
            <MetricCard key={metric.id} metric={metric} />
          ))}
        </Box>
      )}

      {/* Empty state */}
      {!isLoading && !error && metrics.length === 0 && (
        <Box className="rounded-md border border-gray-200 p-8 text-center">
          <Activity className="mx-auto mb-2 h-8 w-8 text-gray-300" />
          <Typography className="text-sm text-gray-500">
            No metrics available. Check that the metrics endpoint is reachable.
          </Typography>
        </Box>
      )}

      {/* Monitoring stack links */}
      <Box className="space-y-3">
        <Typography className="text-sm font-medium text-gray-700">
          Monitoring Stack
        </Typography>
        <Box className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {monitoringLinks.map((link) => (
            <a
              key={link.id}
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Open ${link.label}`}
              className="flex items-center justify-between rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-700 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700 transition-colors"
            >
              <Box>
                <Typography className="font-medium">{link.label}</Typography>
                <Typography className="text-xs text-gray-400">
                  {link.description}
                </Typography>
              </Box>
              <ExternalLink className="h-3 w-3 flex-shrink-0 text-gray-400" aria-hidden="true" />
            </a>
          ))}
        </Box>
      </Box>
    </Box>
  );
};

export default ObservabilityDashboard;
