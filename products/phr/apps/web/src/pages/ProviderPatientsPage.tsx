import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchProviderPatients } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { PatientRosterEntry } from '../types';

export function ProviderPatientsPage(): React.ReactElement {
  const [patients, setPatients] = useState<PatientRosterEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchProviderPatients()
      .then(setPatients)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('provider.patients.error')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('provider.patients.loading')}</div>;
  if (error) return <div className="error">{t('provider.patients.error')}: {error}</div>;
  if (!patients.length) return <div className="empty">{t('provider.patients.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('provider.patients.title')} subheader={t('provider.patients.subheader')} />
        <CardContent>
          <table className="data-table" aria-label={t('provider.patients.title')}>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Age</th>
                <th scope="col">Status</th>
                <th scope="col">Last Visit</th>
                <th scope="col">Next Appointment</th>
              </tr>
            </thead>
            <tbody>
              {patients.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.age}</td>
                  <td><span className={`badge badge--${p.status}`}>{p.status}</span></td>
                  <td>{p.lastVisit != null ? new Date(p.lastVisit).toLocaleDateString() : '—'}</td>
                  <td>{p.nextAppointment != null ? new Date(p.nextAppointment).toLocaleDateString() : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
