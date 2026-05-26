import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchFchvDashboard } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { FchvPatientEntry } from '../types';

export function FchvDashboardPage(): React.ReactElement {
  const [patients, setPatients] = useState<FchvPatientEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchFchvDashboard()
      .then(setPatients)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('fchv.dashboard.error')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('fchv.dashboard.loading')}</div>;
  if (error) return <div className="error">{t('fchv.dashboard.error')}: {error}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('fchv.dashboard.title')} subheader={t('fchv.dashboard.subheader')} />
        <CardContent>
          {!patients.length ? (
            <p className="empty">{t('provider.patients.empty')}</p>
          ) : (
            <ul className="stack gap-sm">
              {patients.map((p) => (
                <li key={p.id} className="fchv-patient-entry">
                  <strong>{p.name}</strong>
                  <span className="muted">{p.village}</span>
                  {p.pendingActions > 0 && (
                    <span className="badge badge--attention">{p.pendingActions} pending</span>
                  )}
                  {p.lastContact != null && (
                    <span className="muted">Last: {new Date(p.lastContact).toLocaleDateString()}</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
