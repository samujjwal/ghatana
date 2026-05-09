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
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

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
  degraded: <AlertTriangle className="h-4 w-4 text-warning-color" aria-hidden="true" />,
  down: <AlertTriangle className="h-4 w-4 text-destructive" aria-hidden="true" />,
};

const STATUS_CHIP_CLASS: Record<HealthStatus, string> = {
  healthy: 'bg-emerald-50 text-emerald-700',
  degraded: 'bg-warning-bg text-warning-color',
  down: 'bg-destructive-bg text-destructive',
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
      metric.status === 'down' && 'border-destructive-border',
      metric.status === 'degraded' && 'border-warning-border'
    )}
    data-testid={`metric-card-${metric.id}`}
  >
    <CardContent className="space-y-3 p-4">
      <Box className="flex items-center justify-between">
        <Typography className="text-sm font-medium text-fg-muted">
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
        <Typography className="text-xs text-fg-muted">
          Previous: {metric.previousValue}
        </Typography>
      )}

      <Box className="flex items-center gap-1 text-xs text-fg-muted">
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
  const { t } = useI18n();
  const overallStatus: HealthStatus = metrics.some((m) => m.status === 'down')
    ? 'down'
    : metrics.some((m) => m.status === 'degraded')
    ? 'degraded'
    : 'healthy';

  return (
    <Box className={cn('space-y-6', className)} aria-label={t('admin.observability.dashboard')}>
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-fg" aria-hidden="true" />
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
          <Button
            type="button"
            onClick={onRefresh}
            disabled={isLoading}
            variant="outline"
            size="small"
            aria-label={t('admin.observability.refresh')}
            className="flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm text-fg-muted hover:bg-surface-muted disabled:opacity-50"
          >
            <Zap className="h-3 w-3" />
            Refresh
          </Button>
        )}
      </Box>

      {/* Error state */}
      {error && (
        <Box
          role="alert"
          className="rounded-md border border-destructive-border bg-destructive-bg p-4 text-sm text-destructive"
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
                <Box className="mb-2 h-4 w-24 rounded bg-surface-muted" />
                <Box className="h-8 w-16 rounded bg-surface-muted" />
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
        <Box className="rounded-md border border-border p-8 text-center">
          <Activity className="mx-auto mb-2 h-8 w-8 text-fg-muted" />
          <Typography className="text-sm text-fg-muted">
            No metrics available. Check that the metrics endpoint is reachable.
          </Typography>
        </Box>
      )}

      {/* Monitoring stack links */}
      <Box className="space-y-3">
        <Typography className="text-sm font-medium text-fg">
          Monitoring Stack
        </Typography>
        <Box className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {monitoringLinks.map((link) => (
            <a
              key={link.id}
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={t('admin.observability.openLink', { label: link.label })}
              className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm text-fg hover:border-info-border hover:bg-info-bg hover:text-info-color transition-colors"
            >
              <Box>
                <Typography className="font-medium">{link.label}</Typography>
                <Typography className="text-xs text-fg-muted">
                  {link.description}
                </Typography>
              </Box>
              <ExternalLink className="h-3 w-3 flex-shrink-0 text-fg-muted" aria-hidden="true" />
            </a>
          ))}
        </Box>
      </Box>
    </Box>
  );
};

export default ObservabilityDashboard;
