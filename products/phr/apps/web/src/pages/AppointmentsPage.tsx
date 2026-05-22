import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { AppointmentSummary } from '../types';

export function AppointmentsPage(): React.ReactElement {
  const [appointments, setAppointments] = useState<AppointmentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => setAppointments(data.appointments))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.appointmentsLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('appointments.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  return (
    <div className="two-column-layout">
      <Card>
        <CardHeader title={t('appointments.title')} subheader={t('appointments.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            {appointments.map((appointment) => (
              <div key={appointment.id} className="data-card">
                <div>
                  <strong>{appointment.provider}</strong>
                  <p className="muted">{appointment.specialty} - {appointment.location}</p>
                </div>
                <span className="pill">{formatPhrDateTime(appointment.startsAt)}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader title={t('appointments.request.title')} subheader={t('appointments.request.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <Input aria-label={t('appointments.specialty.label')} placeholder={t('appointments.specialty.placeholder')} />
            <Input aria-label={t('appointments.preferredDate.label')} type="date" />
            <Button className="primary-cta">{t('appointments.submit')}</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
