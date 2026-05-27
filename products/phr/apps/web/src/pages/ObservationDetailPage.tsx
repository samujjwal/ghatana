/**
 * W-015: Observation detail page.
 * Shows date range, abnormal flags, units, provenance.
 */

import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import { fetchObservationDetail } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { logError } from '../utils/safeLogger';
import type { ObservationSummary } from '../types';

export function ObservationDetailPage(): React.ReactElement {
  const { observationId } = useParams<{ observationId: string }>();
  const { session } = usePhrSession();
  const [observation, setObservation] = useState<ObservationSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !observationId) return;
    fetchObservationDetail(observationId, session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setObservation)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load observation details'))
      .finally(() => setLoading(false));
  }, [session, observationId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">Loading observation details...</div>;
  if (error) return <div className="error" role="alert">Failed to load observation details: {error}</div>;
  if (!observation) return <div className="error" role="alert">Failed to load observation details</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={observation.name} subheader="Observation details" />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>Value</dt><dd>{observation.value}</dd></div>
            {observation.unit && (
              <div><dt>Unit</dt><dd>{observation.unit}</dd></div>
            )}
            <div><dt>Status</dt><dd><Badge variant={observation.status === 'abnormal' ? 'destructive' : 'secondary'}>{observation.status}</Badge></dd></div>
            <div><dt>Recorded at</dt><dd>{new Date(observation.recordedAt).toLocaleString()}</dd></div>
            <div><dt>Effective date</dt><dd>{new Date(observation.effectiveDate).toLocaleDateString()}</dd></div>
            {observation.loincCode && (
              <div><dt>LOINC code</dt><dd>{observation.loincCode}</dd></div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
