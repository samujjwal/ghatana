import React, { useCallback, useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { fetchAppointments, bookAppointment, cancelAppointment, rescheduleAppointment } from '../api/adminApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logWarn } from '../utils/safeLogger';
import type { AppointmentSummary } from '../types';

export function AppointmentsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [appointments, setAppointments] = useState<AppointmentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'upcoming' | 'past'>('upcoming');

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
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'Failed to load appointments'))
      .finally(() => setLoading(false));
  }, [session]);

  useEffect(() => {
    loadAppointments();
  }, [loadAppointments]);

  const handleCancel = async (appointmentId: string): Promise<void> => {
    if (!session) return;
    try {
      await cancelAppointment(appointmentId, session.principalId, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      loadAppointments();
    } catch {
      logWarn('Failed to cancel appointment');
    }
  };

  const handleReschedule = async (appointmentId: string, newSlot: string): Promise<void> => {
    if (!session) return;
    try {
      await rescheduleAppointment(appointmentId, session.principalId, newSlot, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      loadAppointments();
    } catch {
      logWarn('Failed to reschedule appointment');
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">Loading appointments...</div>;
  if (loadError) return <div role="alert" className="error">Error: {loadError}</div>;

  const now = new Date();
  const upcoming = appointments.filter(apt => new Date(apt.startsAt) >= now);
  const past = appointments.filter(apt => new Date(apt.startsAt) < now);

  const displayedAppointments = activeTab === 'upcoming' ? upcoming : past;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('route.appointments.label')} subheader={t('route.appointments.description')} />
        <CardContent>
          <div className="tabs">
            <button
              className={`tab ${activeTab === 'upcoming' ? 'active' : ''}`}
              onClick={() => setActiveTab('upcoming')}
            >
              Upcoming ({upcoming.length})
            </button>
            <button
              className={`tab ${activeTab === 'past' ? 'active' : ''}`}
              onClick={() => setActiveTab('past')}
            >
              Past ({past.length})
            </button>
          </div>
          <div className="stack gap-md">
            {displayedAppointments.length === 0 ? (
              <p className="empty">No {activeTab} appointments</p>
            ) : (
              displayedAppointments.map((appointment) => (
                <div key={appointment.id} className="data-card">
                  <div>
                    <strong>{appointment.provider}</strong>
                    <p className="muted">{appointment.specialty} - {appointment.location}</p>
                  </div>
                  <div className="row gap-sm align-center">
                    <span className="pill">{new Date(appointment.startsAt).toLocaleString()}</span>
                    <span className={`badge badge--${appointment.status}`}>{appointment.status}</span>
                    {appointment.reminderSent && <span className="muted">Reminder sent</span>}
                  </div>
                  {activeTab === 'upcoming' && appointment.status === 'confirmed' && (
                    <div className="row gap-sm">
                      <Button size="small" variant="outline" onClick={() => {
                        const newSlot = prompt('Enter new time slot (ISO format):');
                        if (newSlot) handleReschedule(appointment.id, newSlot);
                      }}>Reschedule</Button>
                      <Button size="small" onClick={() => handleCancel(appointment.id)}>Cancel</Button>
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
