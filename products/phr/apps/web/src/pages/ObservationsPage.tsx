import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { TimeSeriesChart } from '@ghatana/charts';
import type { ChartDataPoint } from '@ghatana/charts';
import { fetchObservations } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

type ObservationStatusFilter = ObservationSummary['status'] | 'all';
type ObservationRangeFilter = 'all' | '30d' | '90d';

interface ObservationGroup {
  code: string;
  label: string;
  observations: ObservationSummary[];
}

function isInDateRange(observation: ObservationSummary, range: ObservationRangeFilter): boolean {
  if (range === 'all') return true;
  const days = range === '30d' ? 30 : 90;
  const observedAt = new Date(observation.effectiveDate).getTime();
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  return observedAt >= cutoff;
}

function statusLabel(status: ObservationSummary['status']): string {
  return t(`observations.status.${status}`);
}

function statusClass(status: ObservationSummary['status']): string {
  if (status === 'critical') return 'danger';
  if (status === 'attention' || status === 'abnormal') return 'warning';
  return '';
}

function groupObservations(observations: ObservationSummary[]): ObservationGroup[] {
  const grouped = observations.reduce<Record<string, ObservationSummary[]>>((acc, observation) => {
    const code = observation.loincCode ?? observation.name;
    acc[code] = [...(acc[code] ?? []), observation];
    return acc;
  }, {});

  return Object.entries(grouped)
    .map(([code, group]) => {
      const sorted = [...group].sort((a, b) => new Date(b.effectiveDate).getTime() - new Date(a.effectiveDate).getTime());
      return {
        code,
        label: sorted[0]?.name ?? code,
        observations: sorted,
      };
    })
    .sort((a, b) => a.label.localeCompare(b.label));
}

function trendLabel(observations: ObservationSummary[]): string {
  if (observations.length < 2) return t('observations.trend.insufficient');
  const latest = Number.parseFloat(observations[0]?.value ?? '0');
  const previous = Number.parseFloat(observations[1]?.value ?? '0');
  if (latest > previous) return t('observations.trend.increasing');
  if (latest < previous) return t('observations.trend.decreasing');
  return t('observations.trend.stable');
}

function getChartData(observations: ObservationSummary[]): ChartDataPoint[] {
  return observations
    .map((observation) => {
      const value = Number.parseFloat(observation.value);
      return {
        x: new Date(observation.effectiveDate).getTime(),
        y: value,
        label: formatPhrDate(observation.effectiveDate),
        value,
      };
    })
    .filter((point) => Number.isFinite(point.value))
    .sort((a, b) => a.x - b.x);
}

export function ObservationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [observations, setObservations] = useState<ObservationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedObservation, setSelectedObservation] = useState<string | null>(null);
  const [metricFilter, setMetricFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<ObservationStatusFilter>('all');
  const [rangeFilter, setRangeFilter] = useState<ObservationRangeFilter>('all');

  useEffect(() => {
    if (!session) return;
    fetchObservations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setObservations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('observations.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('observations.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />;
  if (!observations.length) return <div className="empty" role="status">{t('observations.empty')}</div>;

  const metricOptions = groupObservations(observations);
  const filteredObservations = observations.filter((observation) => {
    const code = observation.loincCode ?? observation.name;
    const metricMatches = metricFilter === 'all' || code === metricFilter;
    const statusMatches = statusFilter === 'all' || observation.status === statusFilter;
    return metricMatches && statusMatches && isInDateRange(observation, rangeFilter);
  });
  const groups = groupObservations(filteredObservations);

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('observations.title')} subheader={t('observations.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <div className="toolbar row gap-sm wrap">
              <label className="field">
                <span>{t('observations.filters.metric')}</span>
                <select value={metricFilter} onChange={(event: React.ChangeEvent<HTMLSelectElement>) => setMetricFilter(event.target.value)}>
                  <option value="all">{t('observations.filters.allMetrics')}</option>
                  {metricOptions.map((option) => (
                    <option key={option.code} value={option.code}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span>{t('observations.filters.status')}</span>
                <select value={statusFilter} onChange={(event: React.ChangeEvent<HTMLSelectElement>) => setStatusFilter(event.target.value as ObservationStatusFilter)}>
                  <option value="all">{t('observations.filters.allStatuses')}</option>
                  <option value="normal">{t('observations.status.normal')}</option>
                  <option value="attention">{t('observations.status.attention')}</option>
                  <option value="critical">{t('observations.status.critical')}</option>
                  <option value="abnormal">{t('observations.status.abnormal')}</option>
                  <option value="pending">{t('observations.status.pending')}</option>
                </select>
              </label>
              <label className="field">
                <span>{t('observations.filters.range')}</span>
                <select value={rangeFilter} onChange={(event: React.ChangeEvent<HTMLSelectElement>) => setRangeFilter(event.target.value as ObservationRangeFilter)}>
                  <option value="all">{t('observations.filters.allDates')}</option>
                  <option value="30d">{t('observations.filters.last30')}</option>
                  <option value="90d">{t('observations.filters.last90')}</option>
                </select>
              </label>
            </div>

            {groups.length === 0 ? (
              <p className="empty" role="status">{t('observations.filters.empty')}</p>
            ) : groups.map((group) => {
              const latest = group.observations[0];
              if (!latest) return null;
              const chartData = getChartData(group.observations);
              return (
                <div key={group.code} className="observation-group">
                  <button
                    type="button"
                    onClick={() => setSelectedObservation(group.code === selectedObservation ? null : group.code)}
                    className="observation-group-header"
                    aria-expanded={selectedObservation === group.code}
                  >
                    <strong>{latest.name}</strong>
                    <span className={`pill ${statusClass(latest.status)}`}>{statusLabel(latest.status)}</span>
                    <span className="muted">{trendLabel(group.observations)}</span>
                    <span className="muted">{t('observations.readingCount', { count: group.observations.length })}</span>
                  </button>

                  {selectedObservation === group.code && (
                    <div className="observation-trend">
                      <h4>{t('observations.trendHistory')}</h4>
                      {chartData.length > 1 && (
                        <div className="trend-chart">
                          <span className="visually-hidden" aria-hidden="false">
                            Line chart showing {chartData.length} data points for {latest.name}
                          </span>
                          <TimeSeriesChart
                            data={chartData}
                            series={[{ id: 'value', label: latest.name, color: '#3b82f6' }]}
                            height={200}
                            showLegend={false}
                            aria-label={t('observations.chart.label', { metric: latest.name })}
                          />
                        </div>
                      )}
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>{t('observations.table.date')}</th>
                            <th>{t('observations.table.value')}</th>
                            <th>{t('observations.table.status')}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {group.observations.map((observation) => (
                            <tr key={observation.id}>
                              <td><time dateTime={observation.effectiveDate}>{formatPhrDate(observation.effectiveDate)}</time></td>
                              <td>{observation.value} <span className="muted">{observation.unit}</span></td>
                              <td><span className={`badge badge--${observation.status} ${statusClass(observation.status)}`}>{statusLabel(observation.status)}</span></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
