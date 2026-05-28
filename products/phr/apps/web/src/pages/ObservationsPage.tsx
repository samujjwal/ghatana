import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { TimeSeriesChart } from '@ghatana/charts';
import { fetchObservations } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

export function ObservationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [observations, setObservations] = useState<ObservationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedObservation, setSelectedObservation] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchObservations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setObservations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load observations'))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">Loading observations...</div>;
  if (error) return <div className="error">Error: {error}</div>;
  if (!observations.length) return <div className="empty">No observations found</div>;

  // Group observations by code/LOINC to show trends
  const observationsByCode = observations.reduce((acc, obs) => {
    const code = obs.loincCode || 'unknown';
    if (!acc[code]) acc[code] = [];
    acc[code].push(obs);
    return acc;
  }, {} as Record<string, ObservationSummary[]>);

  // Sort each group by date
  Object.keys(observationsByCode).forEach(code => {
    const group = observationsByCode[code];
    if (group) {
      group.sort((a, b) => 
        new Date(b.effectiveDate).getTime() - new Date(a.effectiveDate).getTime()
      );
    }
  });

  // Calculate trend for a group
  const getTrend = (obs: ObservationSummary[]): string => {
    if (obs.length < 2) return 'insufficient';
    const latest = parseFloat(obs[0]?.value || '0');
    const previous = parseFloat(obs[1]?.value || '0');
    if (latest > previous) return 'increasing';
    if (latest < previous) return 'decreasing';
    return 'stable';
  };

  const selectedGroup = selectedObservation ? observationsByCode[selectedObservation] : null;

  // Prepare chart data for TimeSeriesChart
	  const getChartData = (obsGroup: ObservationSummary[]) => {
	    return obsGroup
	      .map(obs => ({
	        x: new Date(obs.effectiveDate).getTime(),
	        y: parseFloat(obs.value),
	        label: new Date(obs.effectiveDate).toLocaleDateString(),
	        value: parseFloat(obs.value),
	      }))
	      .sort((a, b) => a.x - b.x);
	  };

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('observations.title')} subheader={t('observations.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            {/* Observation groups with trend indicators */}
            {Object.entries(observationsByCode).map(([code, obsGroup]) => {
              const trend = getTrend(obsGroup);
              const latest = obsGroup[0];
              if (!latest) return null;
              return (
                <div key={code} className="observation-group">
                  <button
                    onClick={() => setSelectedObservation(code === selectedObservation ? null : code)}
                    className="observation-group-header"
                  >
                    <strong>{latest.name}</strong>
                    <span className={`trend-indicator trend--${trend}`}>
                      {trend === 'increasing' && '↑'}
                      {trend === 'decreasing' && '↓'}
                      {trend === 'stable' && '→'}
                      {trend === 'insufficient' && '—'}
                    </span>
                    <span className="muted">{obsGroup.length} readings</span>
                  </button>
                  
                  {selectedObservation === code && (
                    <div className="observation-trend">
                      <h4>Trend History</h4>
                      <div className="trend-chart">
	                        <TimeSeriesChart
	                          data={getChartData(obsGroup)}
	                          series={[{ id: 'value', label: latest.name, color: '#3b82f6' }]}
	                          height={200}
	                          showLegend={false}
	                        />
                      </div>
                      <table className="data-table">
                        <thead>
                          <tr>
                            <th>Date</th>
                            <th>Value</th>
                            <th>Status</th>
                          </tr>
                        </thead>
                        <tbody>
                          {obsGroup.map((obs) => (
                            <tr key={obs.id}>
                              <td><time dateTime={obs.effectiveDate}>{new Date(obs.effectiveDate).toLocaleDateString()}</time></td>
                              <td>{obs.value} <span className="muted">{obs.unit}</span></td>
                              <td><span className={`badge badge--${obs.status}`}>{obs.status}</span></td>
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
