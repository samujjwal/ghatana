import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchObservations } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

export function LabDetailPage(): React.ReactElement {
  const { labId } = useParams();
  const { session } = usePhrSession();
  const [lab, setLab] = useState<ObservationSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session || !labId) return;
    fetchObservations(session.principalId, toSessionContext(session))
      .then((observations) => {
        const found = observations.find((obs) => obs.id === labId);
        if (found) {
          setLab(found);
        } else {
          setError({ message: t('labDetail.notFound') });
        }
      })
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('labDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, labId]);

  if (loading) return <div className="loading">{t('labDetail.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;
  if (!lab) return <div className="empty">{t('labDetail.notFound')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={lab.name} subheader={t('labDetail.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <div className="detail-row">
              <span className="detail-label">{t('labDetail.value')}:</span>
              <span className="detail-value">
                <strong>{lab.value}</strong>
                {lab.unit && <span className="muted"> {lab.unit}</span>}
              </span>
            </div>
            <div className="detail-row">
              <span className="detail-label">{t('conditionDetail.status')}:</span>
              <span className={`badge badge--${lab.status}`}>{lab.status}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">{t('labDetail.date')}:</span>
              <time dateTime={lab.effectiveDate}>{new Date(lab.effectiveDate).toLocaleString()}</time>
            </div>
            {lab.loincCode && (
              <div className="detail-row">
                <span className="detail-label">{t('observationDetail.loincCode')}:</span>
                <code>{lab.loincCode}</code>
              </div>
            )}
            {lab.recordedAt && (
              <div className="detail-row">
                <span className="detail-label">{t('labDetail.recorded')}:</span>
                <time dateTime={lab.recordedAt}>{new Date(lab.recordedAt).toLocaleString()}</time>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
