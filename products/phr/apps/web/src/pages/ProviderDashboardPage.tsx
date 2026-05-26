import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchProviderPatients } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { t } from '../i18n/phrI18n';
import type { PatientRosterEntry } from '../types';

export function ProviderDashboardPage(): React.ReactElement {
  const [patients, setPatients] = useState<PatientRosterEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { session } = usePhrSession();
  const { role } = usePhrAccess();

  useEffect(() => {
    if (!session) return;
    fetchProviderPatients({ tenantId: session.tenantId, principalId: session.principalId, role })
      .then(setPatients)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('provider.dashboard.error')))
      .finally(() => setLoading(false));
  }, [session, role]);

  if (loading) return <div className="loading">{t('provider.dashboard.loading')}</div>;
  if (error) return <div className="error">{t('provider.dashboard.error')}: {error}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('provider.dashboard.title')} subheader={t('provider.dashboard.subheader')} />
        <CardContent>
          {!patients.length ? (
            <p className="empty">{t('provider.patients.empty')}</p>
          ) : (
            <ul className="stack gap-sm">
              {patients.map((p) => (
                <li key={p.id} className="patient-roster-entry">
                  <strong>{p.name}</strong>
                  <span className="muted">Age {p.age}</span>
                  <span className={`badge badge--${p.status}`}>{p.status}</span>
                  {p.nextAppointment != null && <span className="muted">Next: {new Date(p.nextAppointment).toLocaleDateString()}</span>}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
