import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/patientApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { DashboardData } from '../types';

export function DashboardPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session) {
      setError({ message: t('error.sessionRequired') });
      setLoading(false);
      return;
    }
    fetchDashboardData({
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
      persona: session.persona,
      tier: session.tier,
      facilityId: session.facilityId,
      })
      .then(setData)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('error.dashboardLoad'))))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('dashboard.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;
  if (!data) return <div className="empty" role="status">{t('dashboard.empty')}</div>;

  const { profileSummary, nextAppointment, medications, recentObservations, activeConditions, documents, accessAlerts } = data;

  return (
    <div className="stack gap-lg">
      <section className="hero-panel">
        <div>
          <p className="eyebrow">{t('dashboard.eyebrow')}</p>
          <h2>{profileSummary.name}</h2>
          <p className="muted">
            {t('dashboard.summaryMeta', {
              role: data.role,
              generatedAt: data.generatedAt,
            })}
          </p>
        </div>
        <div className="metric-strip">
          <div><span>{accessAlerts.expiringConsents}</span><small>{t('dashboard.metric.consent')}</small></div>
          <div><span>{nextAppointment ? 1 : 0}</span><small>{t('dashboard.metric.visits')}</small></div>
          <div><span>{recentObservations.count}</span><small>{t('dashboard.metric.labs')}</small></div>
          <div><span>{medications.activeCount}</span><small>{t('dashboard.metric.medications')}</small></div>
        </div>
      </section>
      <section className="dashboard-grid">
        <Card>
          <CardHeader title={t('dashboard.nextAppointment.title')} subheader={t('dashboard.nextAppointment.subheader')} />
          <CardContent>
            <ul className="stack gap-sm">
              {nextAppointment ? (
                <>
                  <li>{t('dashboard.nextAppointment.time', { scheduledTime: nextAppointment.scheduledTime })}</li>
                  <li>{t('dashboard.nextAppointment.provider', { provider: nextAppointment.provider })}</li>
                  <li>{t('dashboard.nextAppointment.type', { type: nextAppointment.type })}</li>
                </>
              ) : (
                <li>{t('dashboard.nextAppointment.none')}</li>
              )}
            </ul>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={t('dashboard.alerts.title')} subheader={t('dashboard.alerts.subheader')} />
          <CardContent>
            <ul className="stack gap-sm">
              <li>{t('dashboard.alerts.expiringConsents', { count: accessAlerts.expiringConsents })}</li>
              <li>{t(accessAlerts.emergencyAccessPending ? 'dashboard.alerts.emergencyPending' : 'dashboard.alerts.emergencyClear')}</li>
              <li>{t('dashboard.alerts.pendingOcr', { count: documents.pendingOcr })}</li>
              <li>{t(recentObservations.hasCritical ? 'dashboard.alerts.criticalObservation' : 'dashboard.alerts.noCriticalObservation')}</li>
              <li>{t(activeConditions.hasChronic ? 'dashboard.alerts.chronicCondition' : 'dashboard.alerts.noChronicCondition')}</li>
            </ul>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
