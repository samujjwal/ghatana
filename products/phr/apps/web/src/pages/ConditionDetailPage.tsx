/**
 * W-014: Condition detail page.
 * Shows active/resolved condition details.
 */

import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import { fetchConditionDetail } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { logError } from '../utils/safeLogger';
import type { ConditionSummary } from '../types';

export function ConditionDetailPage(): React.ReactElement {
  const { conditionId } = useParams<{ conditionId: string }>();
  const { session } = usePhrSession();
  const [condition, setCondition] = useState<ConditionSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !conditionId) return;
    fetchConditionDetail(conditionId, session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setCondition)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load condition details'))
      .finally(() => setLoading(false));
  }, [session, conditionId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">Loading condition details...</div>;
  if (error) return <div className="error" role="alert">Failed to load condition details: {error}</div>;
  if (!condition) return <div className="error" role="alert">Failed to load condition details</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={condition.name} subheader="Condition details" />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>Status</dt><dd><Badge variant={condition.status === 'active' ? 'destructive' : 'secondary'}>{condition.status}</Badge></dd></div>
            <div><dt>Code</dt><dd>{condition.code || condition.icdCode || '—'}</dd></div>
            {condition.onsetDate && (
              <div><dt>Onset date</dt><dd>{new Date(condition.onsetDate).toLocaleDateString()}</dd></div>
            )}
            {condition.resolvedDate && (
              <div><dt>Resolved date</dt><dd>{new Date(condition.resolvedDate).toLocaleDateString()}</dd></div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
