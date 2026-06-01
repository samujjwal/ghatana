import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchLabs } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

function labSeverityClass(status: ObservationSummary['status']): string {
  if (status === 'critical') return 'danger';
  if (status === 'attention' || status === 'abnormal') return 'warning';
  return '';
}

function labStatusLabel(status: ObservationSummary['status']): string {
  return t(`observations.status.${status}` as any);
}

export function LabsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [labs, setLabs] = useState<ObservationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchLabs(session.principalId, toSessionContext(session))
      .then(setLabs)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('labs.error'))))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('labs.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;

  const criticalCount = labs.filter((lab) => lab.status === 'critical').length;
  const attentionCount = labs.filter((lab) => lab.status === 'attention' || lab.status === 'abnormal').length;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('labs.title')} subheader={t('labs.subheader')} />
        <CardContent>
          <div className="info-banner" role="note">
            <p className="muted">
              Labs are laboratory-focused clinical observations. {t('labs.trendsPrefix')} <Link to="/observations">{t('labs.trendsLink')}</Link>.
            </p>
          </div>
          <div className="row gap-sm wrap" aria-label={t('labs.severitySummary')}>
            <span className="pill danger">{t('labs.criticalCount', { count: criticalCount })}</span>
            <span className="pill warning">{t('labs.attentionCount', { count: attentionCount })}</span>
          </div>
          <div className="stack gap-md">
            {labs.length === 0 ? (
              <p className="empty" role="status">{t('labs.empty')}</p>
            ) : (
              labs.map((lab) => (
                <Link key={lab.id} className={`data-card ${labSeverityClass(lab.status)}`} to={`/labs/${lab.id}`}>
                  <div>
                    <strong>{lab.name}</strong>
                    <p className="muted">{t('labs.collected', { date: formatPhrDate(lab.effectiveDate) })}</p>
                  </div>
                  <div className="row gap-sm align-center">
                    <span className={`pill ${labSeverityClass(lab.status)}`}>{labStatusLabel(lab.status)}</span>
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
