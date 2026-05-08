/**
 * Observe Signals Panel
 *
 * Phase-native panel for the Observe phase. Displays preview health, key
 * metrics, active incidents, and links to traces/logs for observability.
 *
 * @doc.type component
 * @doc.purpose Observability signals surface for the Observe phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useCallback, useState } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';

export type HealthStatus = 'healthy' | 'degraded' | 'down' | 'unknown';

export type MetricTrend = 'up' | 'down' | 'stable';

export interface SignalMetric {
  readonly id: string;
  readonly name: string;
  readonly value: string | number;
  readonly unit?: string;
  readonly trend?: MetricTrend;
  readonly threshold?: number;
  readonly breached: boolean;
  readonly description?: string;
}

export type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low';
export type IncidentStatus = 'open' | 'investigating' | 'mitigating' | 'resolved';

export interface Incident {
  readonly id: string;
  readonly title: string;
  readonly severity: IncidentSeverity;
  readonly status: IncidentStatus;
  readonly startedAt: string;
  readonly resolvedAt?: string;
  readonly description?: string;
  readonly runbookUrl?: string;
}

export interface TraceLink {
  readonly id: string;
  readonly label: string;
  readonly url: string;
  readonly kind: 'trace' | 'log' | 'dashboard' | 'alert';
}

export interface ObserveSignalsPanelProps {
  /** Overall preview health */
  readonly previewHealth: HealthStatus;
  /** ISO timestamp of last health check */
  readonly healthCheckedAt?: string;
  /** Key metrics to display */
  readonly metrics: readonly SignalMetric[];
  /** Active and recent incidents */
  readonly incidents: readonly Incident[];
  /** External trace/log links */
  readonly traceLinks: readonly TraceLink[];
  /** Called to refresh signals */
  readonly onRefresh: () => void;
  /** Whether a refresh is in progress */
  readonly isRefreshing?: boolean;
  /** Custom className */
  readonly className?: string;
}

const HEALTH_STYLE: Record<HealthStatus, { label: string; className: string; dot: string }> = {
  healthy: {
    label: 'Healthy',
    className: 'bg-success-bg border-success-border text-success-color',
    dot: 'bg-success-color',
  },
  degraded: {
    label: 'Degraded',
    className: 'bg-warning-bg border-warning-border text-warning-color',
    dot: 'bg-warning-color',
  },
  down: {
    label: 'Down',
    className: 'bg-destructive-bg border-destructive-border text-destructive',
    dot: 'bg-destructive',
  },
  unknown: {
    label: 'Unknown',
    className: 'bg-surface-muted border-border text-fg-muted',
    dot: 'bg-fg-muted',
  },
};

const INCIDENT_SEVERITY_CLASS: Record<IncidentSeverity, string> = {
  critical: 'border-destructive-border bg-destructive-bg',
  high: 'border-warning-border bg-warning-bg',
  medium: 'border-warning-border bg-warning-bg/50',
  low: 'border-info-border bg-info-bg',
};

const INCIDENT_STATUS_LABEL: Record<IncidentStatus, string> = {
  open: 'Open',
  investigating: 'Investigating',
  mitigating: 'Mitigating',
  resolved: 'Resolved',
};

const TREND_ICON: Record<MetricTrend, string> = {
  up: '↑',
  down: '↓',
  stable: '→',
};

const TRACE_KIND_ICON: Record<TraceLink['kind'], string> = {
  trace: '🔍',
  log: '📋',
  dashboard: '📊',
  alert: '🔔',
};

/**
 * Observe Signals Panel
 *
 * Provides operational visibility including:
 * - Preview environment health status
 * - Key metric values with breach indicators
 * - Active incident list with severity and runbook links
 * - External trace/log/dashboard links
 */
export const ObserveSignalsPanel: React.FC<ObserveSignalsPanelProps> = ({
  previewHealth,
  healthCheckedAt,
  metrics,
  incidents,
  traceLinks,
  onRefresh,
  isRefreshing = false,
  className = '',
}) => {
  const [showResolved, setShowResolved] = useState(false);

  const activeIncidents = incidents.filter((i) => i.status !== 'resolved');
  const resolvedIncidents = incidents.filter((i) => i.status === 'resolved');
  const breachedMetrics = metrics.filter((m) => m.breached);
  const healthStyle = HEALTH_STYLE[previewHealth];

  const toggleResolved = useCallback(() => {
    setShowResolved((prev) => !prev);
  }, []);

  const displayedIncidents = showResolved ? incidents : activeIncidents;

  return (
    <section
      className={`observe-signals-panel space-y-6 ${className}`}
      aria-label="Observe signals"
      data-testid="observe-signals-panel"
    >
      {/* Health Header */}
      <Card variant="outlined">
        <CardContent className="p-5">
          <div className="flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-3">
              <span
                className={`inline-block h-3 w-3 rounded-full ${healthStyle.dot}`}
                aria-hidden="true"
              />
              <div>
                <h3 className="text-base font-semibold text-fg">Preview environment</h3>
                {healthCheckedAt && (
                  <p className="text-xs text-fg-muted">
                    Checked:{' '}
                    <time dateTime={healthCheckedAt}>
                      {new Date(healthCheckedAt).toLocaleString()}
                    </time>
                  </p>
                )}
              </div>
            </div>
            <div className="flex items-center gap-3">
              <span
                className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${healthStyle.className}`}
                aria-label={`Preview health: ${healthStyle.label}`}
              >
                {healthStyle.label}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={onRefresh}
                disabled={isRefreshing}
                aria-label="Refresh signals"
              >
                {isRefreshing ? 'Refreshing…' : 'Refresh'}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Metrics */}
      {metrics.length > 0 && (
        <section aria-label="Key metrics">
          <h4 className="text-sm font-medium text-fg mb-3">
            Metrics
            {breachedMetrics.length > 0 && (
              <span className="ml-2 text-xs text-destructive">
                ({breachedMetrics.length} threshold{breachedMetrics.length !== 1 ? 's' : ''} breached)
              </span>
            )}
          </h4>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {metrics.map((metric) => (
              <Card
                key={metric.id}
                variant="outlined"
                className={metric.breached ? 'border-destructive-border bg-destructive-bg/30' : ''}
              >
                <CardContent className="p-3">
                  <p className="text-xs text-fg-muted truncate">{metric.name}</p>
                  <div className="flex items-baseline gap-1 mt-1">
                    <span className="text-xl font-semibold text-fg">
                      {metric.value}
                    </span>
                    {metric.unit && (
                      <span className="text-xs text-fg-muted">{metric.unit}</span>
                    )}
                    {metric.trend && (
                      <span
                        className={`text-xs ml-auto ${
                          metric.trend === 'up' ? 'text-destructive' : metric.trend === 'down' ? 'text-success-color' : 'text-fg-muted'
                        }`}
                        aria-label={`Trend: ${metric.trend}`}
                      >
                        {TREND_ICON[metric.trend]}
                      </span>
                    )}
                  </div>
                  {metric.threshold != null && (
                    <p className="text-xs text-fg-muted mt-0.5">
                      Threshold: {metric.threshold}{metric.unit ?? ''}
                    </p>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* Incidents */}
      <section aria-label="Incidents">
        <div className="flex items-center justify-between mb-3">
          <h4 className="text-sm font-medium text-fg">
            Incidents
            {activeIncidents.length > 0 && (
              <span className="ml-2 text-xs text-destructive font-normal">
                {activeIncidents.length} active
              </span>
            )}
          </h4>
          {resolvedIncidents.length > 0 && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-xs text-fg-muted hover:text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              onClick={toggleResolved}
              aria-pressed={showResolved}
            >
              {showResolved ? 'Hide resolved' : `Show ${resolvedIncidents.length} resolved`}
            </Button>
          )}
        </div>

        {displayedIncidents.length === 0 ? (
          <div className="rounded-lg border border-success-border bg-success-bg p-4 text-center">
            <p className="text-sm text-success-color font-medium">No active incidents</p>
          </div>
        ) : (
          <div className="space-y-2">
            {displayedIncidents.map((incident) => (
              <Card
                key={incident.id}
                variant="outlined"
                className={INCIDENT_SEVERITY_CLASS[incident.severity]}
              >
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-fg">{incident.title}</p>
                      {incident.description && (
                        <p className="text-xs text-fg-muted mt-0.5">{incident.description}</p>
                      )}
                      <p className="text-xs text-fg-muted mt-1">
                        <time dateTime={incident.startedAt}>
                          Started: {new Date(incident.startedAt).toLocaleString()}
                        </time>
                      </p>
                    </div>
                    <div className="flex flex-col items-end gap-2 flex-shrink-0">
                      <span className="text-xs font-medium text-fg-muted">
                        {INCIDENT_STATUS_LABEL[incident.status]}
                      </span>
                      <span className="text-xs text-fg-muted capitalize">
                        {incident.severity}
                      </span>
                    </div>
                  </div>
                  {incident.runbookUrl && (
                    <div className="mt-3 pt-3 border-t border-border">
                      <a
                        href={incident.runbookUrl}
                        target="_blank"
                        rel="noreferrer noopener"
                        className="text-xs text-info-color hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                        aria-label={`Open runbook for incident: ${incident.title}`}
                      >
                        Open runbook →
                      </a>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* Trace / Log Links */}
      {traceLinks.length > 0 && (
        <section aria-label="Traces, logs, and dashboards">
          <h4 className="text-sm font-medium text-fg mb-3">Observability links</h4>
          <div className="space-y-1">
            {traceLinks.map((link) => (
              <a
                key={link.id}
                href={link.url}
                target="_blank"
                rel="noreferrer noopener"
                className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-fg hover:bg-surface-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={`Open ${link.label}`}
              >
                <span aria-hidden="true">{TRACE_KIND_ICON[link.kind]}</span>
                {link.label}
              </a>
            ))}
          </div>
        </section>
      )}
    </section>
  );
};

export default ObserveSignalsPanel;
