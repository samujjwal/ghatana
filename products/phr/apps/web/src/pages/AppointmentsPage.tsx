import React, { useCallback, useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { createAppointmentRequest, fetchAppointments, cancelAppointment, rescheduleAppointment } from '../api/adminApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { PhrMessageKey } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { AppointmentSummary } from '../types';

const appointmentStatusKeys: Record<NonNullable<AppointmentSummary['status']>, PhrMessageKey> = {
  requested: 'appointments.status.requested',
  confirmed: 'appointments.status.confirmed',
  completed: 'appointments.status.completed',
  cancelled: 'appointments.status.cancelled',
};

function appointmentStatusLabel(status: AppointmentSummary['status']): string {
  return t(appointmentStatusKeys[status ?? 'requested']);
}

export function AppointmentsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [appointments, setAppointments] = useState<AppointmentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<SafeApiErrorState | null>(null);
  const [activeTab, setActiveTab] = useState<'upcoming' | 'past'>('upcoming');
  const [actionError, setActionError] = useState<SafeApiErrorState | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionPendingId, setActionPendingId] = useState<string | null>(null);
  const [reschedulingId, setReschedulingId] = useState<string | null>(null);
  const [rescheduleSlot, setRescheduleSlot] = useState<string>('');
  const [requestSpecialty, setRequestSpecialty] = useState<string>('');
  const [requestPreferredDate, setRequestPreferredDate] = useState<string>('');
  const [requestNotes, setRequestNotes] = useState<string>('');
  const [requestingAppointment, setRequestingAppointment] = useState<boolean>(false);

  const loadAppointments = useCallback((): void => {
    if (!session) return;
    setLoading(true);
    setLoadError(null);
    fetchAppointments(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setAppointments)
      .catch((err: unknown) => setLoadError(toSafeApiErrorState(err, t('appointments.error.load'))))
      .finally(() => setLoading(false));
  }, [session]);

  useEffect(() => {
    loadAppointments();
  }, [loadAppointments]);

  const handleCancel = async (appointmentId: string): Promise<void> => {
    if (!session) return;
    setActionPendingId(appointmentId);
    setActionError(null);
    setActionMessage(null);
    try {
      await cancelAppointment(appointmentId, session.principalId, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      setActionMessage(t('appointments.cancel.success'));
      loadAppointments();
    } catch (err: unknown) {
      setActionError(toSafeApiErrorState(err, t('appointments.error.cancel')));
      logError('Failed to cancel appointment', undefined, { appointmentId, error: err });
    } finally {
      setActionPendingId(null);
    }
  };

  const handleRequestAppointment = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    if (!session) return;
    const specialty = requestSpecialty.trim();
    const preferredDate = requestPreferredDate.trim();
    const notes = requestNotes.trim();
    if (!specialty || !preferredDate) {
      setActionError({ message: t('appointments.request.required') });
      return;
    }
    if (new Date(preferredDate).getTime() <= Date.now()) {
      setActionError({ message: t('appointments.request.futureDate') });
      return;
    }

    setRequestingAppointment(true);
    setActionError(null);
    setActionMessage(null);
    try {
      const result = await createAppointmentRequest({
        specialty,
        preferredDate,
        ...(notes ? { notes } : {}),
      }, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      setActionMessage(t('appointments.success', { id: result.id }));
      setRequestSpecialty('');
      setRequestPreferredDate('');
      setRequestNotes('');
      loadAppointments();
    } catch (err: unknown) {
      setActionError(toSafeApiErrorState(err, t('appointments.error.submit')));
      logError('Failed to request appointment', undefined, { error: err });
    } finally {
      setRequestingAppointment(false);
    }
  };

  const handleReschedule = async (appointmentId: string, newSlot: string): Promise<void> => {
    if (!session) return;
    const trimmedSlot = newSlot.trim();
    if (!trimmedSlot) {
      setActionError({ message: t('appointments.reschedule.required') });
      return;
    }
    setActionPendingId(appointmentId);
    setActionError(null);
    setActionMessage(null);
    try {
      await rescheduleAppointment(appointmentId, session.principalId, trimmedSlot, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      setReschedulingId(null);
      setRescheduleSlot('');
      setActionMessage(t('appointments.reschedule.success'));
      loadAppointments();
    } catch (err: unknown) {
      setActionError(toSafeApiErrorState(err, t('appointments.error.reschedule')));
      logError('Failed to reschedule appointment', undefined, { appointmentId, error: err });
    } finally {
      setActionPendingId(null);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('appointments.loading')}</div>;
  if (loadError) return <SafeError title={t('appointments.error.load')} message={loadError.message} correlationId={loadError.correlationId} />;

  const now = new Date();
  const upcoming = appointments.filter(apt => new Date(apt.startsAt) >= now);
  const past = appointments.filter(apt => new Date(apt.startsAt) < now);

  const displayedAppointments = activeTab === 'upcoming' ? upcoming : past;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('route.appointments.label')} subheader={t('route.appointments.description')} />
        <CardContent>
          <form onSubmit={(event) => void handleRequestAppointment(event)} className="stack gap-sm">
            <h3>{t('appointments.request.title')}</h3>
            <div className="row gap-sm wrap">
              <label className="field">
                <span>{t('appointments.specialty.label')}</span>
                <Input
                  value={requestSpecialty}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setRequestSpecialty(event.target.value)}
                  placeholder={t('appointments.specialty.placeholder')}
                  aria-label={t('appointments.specialty.label')}
                  required
                />
              </label>
              <label className="field">
                <span>{t('appointments.preferredDate.label')}</span>
                <Input
                  type="datetime-local"
                  value={requestPreferredDate}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setRequestPreferredDate(event.target.value)}
                  aria-label={t('appointments.preferredDate.label')}
                  required
                />
              </label>
              <label className="field">
                <span>{t('appointments.notes.label')}</span>
                <Input
                  value={requestNotes}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setRequestNotes(event.target.value)}
                  placeholder={t('appointments.notes.placeholder')}
                  aria-label={t('appointments.notes.label')}
                />
              </label>
              <Button type="submit" disabled={requestingAppointment} aria-busy={requestingAppointment}>
                {requestingAppointment ? t('appointments.submitting') : t('appointments.submit')}
              </Button>
            </div>
          </form>
          <div className="tabs">
            <Button
              type="button"
              variant={activeTab === 'upcoming' ? 'primary' : 'secondary'}
              onClick={() => {
                setActiveTab('upcoming');
                setReschedulingId(null);
              }}
            >
              {t('appointments.tabs.upcoming', { count: upcoming.length })}
            </Button>
            <Button
              type="button"
              variant={activeTab === 'past' ? 'primary' : 'secondary'}
              onClick={() => {
                setActiveTab('past');
                setReschedulingId(null);
              }}
            >
              {t('appointments.tabs.past', { count: past.length })}
            </Button>
          </div>
          {actionError ? (
            <SafeError
              title={t('appointments.actionError')}
              message={actionError.message}
              correlationId={actionError.correlationId}
              onDismiss={() => setActionError(null)}
            />
          ) : null}
          {actionMessage ? <div role="status" className="success">{actionMessage}</div> : null}
          <div className="stack gap-md">
            {displayedAppointments.length === 0 ? (
              <p className="empty">{t('appointments.empty', { tab: activeTab })}</p>
            ) : (
              displayedAppointments.map((appointment) => (
                <div key={appointment.id} className="data-card">
                  <div>
                    <strong>{appointment.provider}</strong>
                    <p className="muted">{t('appointments.providerLine', { specialty: appointment.specialty, location: appointment.location })}</p>
                  </div>
                  <div className="row gap-sm align-center">
                    <span className="pill">{new Date(appointment.startsAt).toLocaleString()}</span>
                    <span className={`badge badge--${appointment.status}`}>{appointmentStatusLabel(appointment.status)}</span>
                    {appointment.reminderSent && <span className="muted">{t('appointments.reminderSent')}</span>}
                  </div>
                  {activeTab === 'upcoming' && appointment.status === 'confirmed' && (
                    <div className="stack gap-sm">
                      {reschedulingId === appointment.id ? (
                        <div className="row gap-sm align-end">
                          <label className="field">
                            <span>{t('appointments.reschedule.slot.label')}</span>
                            <Input
                              value={rescheduleSlot}
                              onChange={(event: React.ChangeEvent<HTMLInputElement>) => setRescheduleSlot(event.target.value)}
                              placeholder={t('appointments.reschedule.slot.placeholder')}
                              aria-label={t('appointments.reschedule.slot.label')}
                            />
                          </label>
                          <Button
                            size="small"
                            onClick={() => void handleReschedule(appointment.id, rescheduleSlot)}
                            disabled={actionPendingId === appointment.id}
                          >
                            {t('appointments.reschedule.save')}
                          </Button>
                          <Button
                            size="small"
                            variant="outline"
                            onClick={() => {
                              setReschedulingId(null);
                              setRescheduleSlot('');
                            }}
                            disabled={actionPendingId === appointment.id}
                          >
                            {t('appointments.reschedule.close')}
                          </Button>
                        </div>
                      ) : (
                        <div className="row gap-sm">
                          <Button
                            size="small"
                            variant="outline"
                            onClick={() => {
                              setReschedulingId(appointment.id);
                              setRescheduleSlot(appointment.startsAt);
                            }}
                            disabled={actionPendingId === appointment.id}
                          >
                            {t('appointments.reschedule')}
                          </Button>
                          <Button
                            size="small"
                            onClick={() => void handleCancel(appointment.id)}
                            disabled={actionPendingId === appointment.id}
                          >
                            {t('appointments.cancel')}
                          </Button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
