import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { DashboardData } from '../types';

export function DashboardPage(): React.ReactElement {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then(setData)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.dashboardLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('dashboard.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;
  if (!data) return <div className="error">{t('dashboard.empty')}</div>;

  const { patient, consents, appointments, labs, medications } = data;

  return (
    <div className="stack gap-lg">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">{t('dashboard.eyebrow')}</p>
          <h2>{patient.name}</h2>
          <p className="muted">
            {t('dashboard.patientMeta', {
              location: patient.location,
              bloodType: patient.bloodType,
              emergencyContact: patient.emergencyContact,
            })}
          </p>
        </div>
        <div className="metric-strip">
          <div><span>{consents.length}</span><small>{t('dashboard.metric.consent')}</small></div>
          <div><span>{appointments.length}</span><small>{t('dashboard.metric.visits')}</small></div>
          <div><span>{labs.length}</span><small>{t('dashboard.metric.labs')}</small></div>
          <div><span>{medications.length}</span><small>{t('dashboard.metric.medications')}</small></div>
        </div>
      </section>
      <section className="dashboard-grid">
        <Card>
          <CardHeader title={t('dashboard.carePlan.title')} subheader={t('dashboard.carePlan.subheader')} />
          <CardContent>
            <ul className="stack gap-sm">
              <li>{t('dashboard.carePlan.followUp')}</li>
              <li>{t('dashboard.carePlan.renewConsent')}</li>
              <li>{t('dashboard.carePlan.shareHie')}</li>
            </ul>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={t('dashboard.emergency.title')} subheader={t('dashboard.emergency.subheader')} />
          <CardContent>
            <ul className="stack gap-sm">
              <li>{t('dashboard.emergency.contactVerified')}</li>
              <li>{t('dashboard.emergency.caregivers')}</li>
              <li>{t('dashboard.emergency.audit')}</li>
            </ul>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
