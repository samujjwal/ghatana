import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { createAppointmentRequest, fetchDashboardData } from '../api/phrApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { AppointmentCreateResult, AppointmentSummary } from '../types';

// Hard-coded context for demo; production wires from auth session.
const DEMO_CONTEXT = { tenantId: 'tenant-health-1', principalId: 'current', role: 'patient' };

export function AppointmentsPage(): React.ReactElement {
  const [appointments, setAppointments] = useState<AppointmentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Scheduling form state
  const [specialty, setSpecialty] = useState<string>('');
  const [preferredDate, setPreferredDate] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitResult, setSubmitResult] = useState<AppointmentCreateResult | null>(null);

  const loadAppointments = useCallback((): void => {
    setLoading(true);
    setLoadError(null);
    fetchDashboardData()
      .then((data) => setAppointments(data.appointments))
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : t('error.appointmentsLoad')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadAppointments();
  }, [loadAppointments]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setSubmitError(null);
    setSubmitResult(null);

    if (!specialty.trim()) {
      setSubmitError(t('validation.required', { field: t('appointments.specialty.label') }));
      return;
    }
    if (!preferredDate.trim()) {
      setSubmitError(t('validation.required', { field: t('appointments.preferredDate.label') }));
      return;
    }

    setSubmitting(true);
    try {
      const result = await createAppointmentRequest(
        { specialty: specialty.trim(), preferredDate: preferredDate.trim(), notes: notes.trim() || undefined },
        DEMO_CONTEXT,
      );
      setSubmitResult(result);
      setSpecialty('');
      setPreferredDate('');
      setNotes('');
      loadAppointments();
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : t('appointments.error.submit'));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="loading">{t('appointments.loading')}</div>;
  if (loadError) return <div role="alert" className="error">{t('dashboard.errorPrefix')}: {loadError}</div>;

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
          {submitResult && (
            <div role="status" className="success-message mb-4">
              {t('appointments.success', { id: submitResult.id })}
            </div>
          )}
          {submitError && (
            <div role="alert" className="error mb-4">{submitError}</div>
          )}
          <form onSubmit={(e) => void handleSubmit(e)} className="stack gap-md" noValidate>
            <Input
              aria-label={t('appointments.specialty.label')}
              placeholder={t('appointments.specialty.placeholder')}
              value={specialty}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSpecialty(e.target.value)}
              required
            />
            <Input
              aria-label={t('appointments.preferredDate.label')}
              type="date"
              value={preferredDate}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPreferredDate(e.target.value)}
              required
            />
            <Input
              aria-label={t('appointments.notes.label')}
              placeholder={t('appointments.notes.placeholder')}
              value={notes}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNotes(e.target.value)}
            />
            <Button type="submit" className="primary-cta" disabled={submitting}>
              {submitting ? t('appointments.submitting') : t('appointments.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
