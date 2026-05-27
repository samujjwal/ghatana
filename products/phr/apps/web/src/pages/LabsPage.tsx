import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchObservations } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

export function LabsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [labs, setLabs] = useState<ObservationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchObservations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setLabs)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load lab results'))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">Loading lab results...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title="Lab Results" subheader="Recent laboratory test results" />
        <CardContent>
          <div className="info-banner">
            <p className="muted">
              For detailed clinical trends and historical data, visit the <Link to="/observations">Observations</Link> page.
            </p>
          </div>
          <div className="stack gap-md">
            {labs.length === 0 ? (
              <p className="empty">No lab results found</p>
            ) : (
              labs.map((lab) => (
                <Link key={lab.id} className="data-card" to={`/labs/${lab.id}`}>
                  <div>
                    <strong>{lab.name}</strong>
                    <p className="muted">{formatPhrDate(lab.effectiveDate)}</p>
                  </div>
                  <div className="row gap-sm align-center">
                    <span className={`pill ${lab.status === 'abnormal' ? 'warning' : ''}`}>{lab.status}</span>
                    <strong>{lab.value}{lab.unit && <span className="muted"> {lab.unit}</span>}</strong>
                  </div>
                </Link>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
