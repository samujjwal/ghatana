/**
 * W-015: Observation detail page.
 * Shows date range, abnormal flags, units, provenance.
 */

import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import { fetchObservationDetail } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { ObservationSummary } from '../types';

export function ObservationDetailPage(): React.ReactElement {
  const { observationId } = useParams<{ observationId: string }>();
  const { session } = usePhrSession();
  const [observation, setObservation] = useState<ObservationSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session || !observationId) return;
    fetchObservationDetail(observationId, session.principalId, toSessionContext(session))
      .then(setObservation)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('observationDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, observationId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('observationDetail.loading')}</div>;
  if (error) return <SafeError title={t('observationDetail.error.load')} message={error.message} correlationId={error.correlationId} />;
  if (!observation) return <SafeError title={t('observationDetail.error.load')} message={t('observationDetail.error.load')} />;

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
