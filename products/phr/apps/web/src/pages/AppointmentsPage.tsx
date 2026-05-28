import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { fetchAppointments, fetchProviders, bookAppointment, cancelAppointment, rescheduleAppointment } from '../api/adminApi';
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

  // Booking form state
  const [selectedSpecialty, setSelectedSpecialty] = useState<string>('');
  const [selectedProvider, setSelectedProvider] = useState<string>('');
  const [selectedSlot, setSelectedSlot] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitResult, setSubmitResult] = useState<string | null>(null);

  // Provider and slot data
  const [providers, setProviders] = useState<Array<{ id: string; name: string; specialty: string; availableSlots: string[] }>>([]);
  const [loadingProviders, setLoadingProviders] = useState<boolean>(false);

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

  const loadProviders = useCallback((): void => {
    if (!session) return;
    setLoadingProviders(true);
    fetchProviders({
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
      })
      .then(setProviders)
      .catch(() => logWarn('Failed to load providers'))
      .finally(() => setLoadingProviders(false));
  }, [session]);

  useEffect(() => {
    loadAppointments();
    loadProviders();
  }, [loadAppointments, loadProviders]);

  const handleBook = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setSubmitError(null);
    setSubmitResult(null);

    if (!session || !selectedProvider || !selectedSlot) {
      setSubmitError('Please select a provider and time slot');
      return;
    }

    setSubmitting(true);
    try {
      const result = await bookAppointment(
        session.principalId,
        selectedProvider,
        selectedSlot,
        notes.trim() || undefined,
        {
          tenantId: session.tenantId,
          principalId: session.principalId,
          role: session.role,
        },
      );
      setSubmitResult(`Appointment booked successfully: ${result.id}`);
      setSelectedSpecialty('');
      setSelectedProvider('');
      setSelectedSlot('');
      setNotes('');
      loadAppointments();
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to book appointment');
    } finally {
      setSubmitting(false);
    }
  };

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

  if (loading) return <div className="loading">Loading appointments...</div>;
  if (loadError) return <div role="alert" className="error">Error: {loadError}</div>;

  const now = new Date();
  const upcoming = appointments.filter(apt => new Date(apt.startsAt) >= now);
  const past = appointments.filter(apt => new Date(apt.startsAt) < now);

  const displayedAppointments = activeTab === 'upcoming' ? upcoming : past;

  // Filter providers by specialty
  const filteredProviders = selectedSpecialty
    ? providers.filter(p => p.specialty === selectedSpecialty)
    : providers;

  const selectedProviderData = providers.find(p => p.id === selectedProvider);

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
                      <Button size="small" onClick={() => handleCancel(appointment.id)}>Cancel</Button>
                    </div>
                  )}
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title={t('appointments.request.title')} subheader={t('appointments.request.subheader')} />
        <CardContent>
          {submitResult && (
            <div role="status" className="success-message mb-4">{submitResult}</div>
          )}
          {submitError && (
            <div role="alert" className="error mb-4">{submitError}</div>
          )}
          <form onSubmit={(e) => void handleBook(e)} className="stack gap-md" noValidate>
            <div>
              <label htmlFor="specialty">Specialty</label>
              <select
                id="specialty"
                value={selectedSpecialty}
                onChange={(e) => {
                  setSelectedSpecialty(e.target.value);
                  setSelectedProvider('');
                  setSelectedSlot('');
                }}
              >
                <option value="">Select specialty</option>
                {Array.from(new Set(providers.map(p => p.specialty))).map(specialty => (
                  <option key={specialty} value={specialty}>{specialty}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="provider">Provider</label>
              <select
                id="provider"
                value={selectedProvider}
                onChange={(e) => {
                  setSelectedProvider(e.target.value);
                  setSelectedSlot('');
                }}
                disabled={!selectedSpecialty}
              >
                <option value="">{t('appointments.provider.placeholder')}</option>
                {filteredProviders.map(provider => (
                  <option key={provider.id} value={provider.id}>{provider.name}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="slot">{t('appointments.slot.label')}</label>
              <select
                id="slot"
                value={selectedSlot}
                onChange={(e) => setSelectedSlot(e.target.value)}
                disabled={!selectedProvider}
              >
                <option value="">{t('appointments.slot.placeholder')}</option>
                {selectedProviderData?.availableSlots.map(slot => (
                  <option key={slot} value={slot}>{new Date(slot).toLocaleString()}</option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="notes">{t('appointments.notes.label')}</label>
              <Input
                id="notes"
                value={notes}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNotes(e.target.value)}
                placeholder={t('appointments.notes.reasonPlaceholder')}
              />
            </div>
            <Button type="submit" className="primary-cta" disabled={submitting || !selectedSlot}>
              {submitting ? t('appointments.booking') : t('appointments.book')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
