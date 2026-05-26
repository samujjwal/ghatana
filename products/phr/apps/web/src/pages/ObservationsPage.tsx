import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchObservations } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ObservationSummary } from '../types';

export function ObservationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [observations, setObservations] = useState<ObservationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchObservations(session.principalId)
      .then(setObservations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('observations.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('observations.loading')}</div>;
  if (error) return <div className="error">{t('observations.error')}: {error}</div>;
  if (!observations.length) return <div className="empty">{t('observations.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('observations.title')} subheader={t('observations.subheader')} />
        <CardContent>
          <table className="data-table" aria-label={t('observations.title')}>
            <thead>
              <tr>
                <th scope="col">{t('observations.title')}</th>
                <th scope="col">Value</th>
                <th scope="col">Status</th>
                <th scope="col">Date</th>
              </tr>
            </thead>
            <tbody>
              {observations.map((obs) => (
                <tr key={obs.id}>
                  <td>{obs.name}</td>
                  <td>{obs.value}{obs.unit != null && <span className="muted"> {obs.unit}</span>}</td>
                  <td><span className={`badge badge--${obs.status}`}>{obs.status}</span></td>
                  <td><time dateTime={obs.effectiveDate}>{new Date(obs.effectiveDate).toLocaleDateString()}</time></td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
