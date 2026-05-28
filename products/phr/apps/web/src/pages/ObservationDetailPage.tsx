/**
 * W-015: Observation detail page.
 * Shows date range, abnormal flags, units, provenance.
 */

import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import { fetchObservationDetail } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
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
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('observationDetail.error.load')))
      .finally(() => setLoading(false));
  }, [session, observationId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('observationDetail.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('observationDetail.error.load')}: {error}</div>;
  if (!observation) return <div className="error" role="alert">{t('observationDetail.error.load')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={observation.name} subheader={t('observationDetail.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('labDetail.value')}</dt><dd>{observation.value}</dd></div>
            {observation.unit && (
              <div><dt>{t('observationDetail.unit')}</dt><dd>{observation.unit}</dd></div>
            )}
            <div><dt>{t('conditionDetail.status')}</dt><dd><Badge variant={observation.status === 'abnormal' ? 'destructive' : 'secondary'}>{observation.status}</Badge></dd></div>
            <div><dt>{t('observationDetail.recordedAt')}</dt><dd>{new Date(observation.recordedAt).toLocaleString()}</dd></div>
            <div><dt>{t('observationDetail.effectiveDate')}</dt><dd>{new Date(observation.effectiveDate).toLocaleDateString()}</dd></div>
            {observation.loincCode && (
              <div><dt>{t('observationDetail.loincCode')}</dt><dd>{observation.loincCode}</dd></div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
