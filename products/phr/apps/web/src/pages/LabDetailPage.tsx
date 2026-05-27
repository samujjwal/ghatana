import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchObservations } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import type { ObservationSummary } from '../types';

export function LabDetailPage(): React.ReactElement {
  const { labId } = useParams();
  const { session } = usePhrSession();
  const [lab, setLab] = useState<ObservationSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !labId) return;
    fetchObservations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then((observations) => {
        const found = observations.find((obs) => obs.id === labId);
        if (found) {
          setLab(found);
        } else {
          setError('Lab result not found');
        }
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load lab detail'))
      .finally(() => setLoading(false));
  }, [session, labId]);

  if (loading) return <div className="loading">Loading lab details...</div>;
  if (error) return <div className="error">Error: {error}</div>;
  if (!lab) return <div className="empty">Lab result not found</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={lab.name} subheader="Lab Result Details" />
        <CardContent>
          <div className="stack gap-md">
            <div className="detail-row">
              <span className="detail-label">Value:</span>
              <span className="detail-value">
                <strong>{lab.value}</strong>
                {lab.unit && <span className="muted"> {lab.unit}</span>}
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Status:</span>
              <span className={`badge badge--${lab.status}`}>{lab.status}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Date:</span>
              <time dateTime={lab.effectiveDate}>{new Date(lab.effectiveDate).toLocaleString()}</time>
            </div>
            {lab.loincCode && (
              <div className="detail-row">
                <span className="detail-label">LOINC Code:</span>
                <code>{lab.loincCode}</code>
              </div>
            )}
            {lab.recordedAt && (
              <div className="detail-row">
                <span className="detail-label">Recorded:</span>
                <time dateTime={lab.recordedAt}>{new Date(lab.recordedAt).toLocaleString()}</time>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
