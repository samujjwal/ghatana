import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchMedications } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logInfo, logError } from '../utils/safeLogger';
import type { MedicationSummary } from '../types';

export function MedicationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [medications, setMedications] = useState<MedicationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'active' | 'history'>('active');
  const [refillingId, setRefillingId] = useState<string | null>(null);
  const [discontinuingId, setDiscontinuingId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchMedications(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setMedications)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('medications.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('medications.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('dashboard.errorPrefix')}: {error}</div>;

  const visibleMedications = medications.filter((medication) => (
    activeTab === 'active'
      ? medication.status == null || medication.status === 'active'
      : medication.status === 'history' || medication.status === 'stopped'
  ));

  const handleRefill = async (medicationId: string): Promise<void> => {
    if (!session) return;
    setRefillingId(medicationId);
    try {
      logInfo('Medication refill requested', undefined, { medicationId, principalId: session.principalId });
      // TODO: Call actual refill API when available
      // await refillMedication(medicationId, session.principalId, { tenantId: session.tenantId, principalId: session.principalId, role: session.role });
      alert('Refill request submitted (placeholder - backend API needed)');
    } catch (err: unknown) {
      logError('Failed to request medication refill', undefined, { medicationId, error: err });
      setError('Failed to request refill');
    } finally {
      setRefillingId(null);
    }
  };

  const handleDiscontinue = async (medicationId: string): Promise<void> => {
    if (!session) return;
    if (!confirm('Are you sure you want to discontinue this medication?')) return;
    setDiscontinuingId(medicationId);
    try {
      logInfo('Medication discontinuation requested', undefined, { medicationId, principalId: session.principalId });
      // TODO: Call actual discontinue API when available
      // await discontinueMedication(medicationId, session.principalId, { tenantId: session.tenantId, principalId: session.principalId, role: session.role });
      const updated = medications.map(m => m.id === medicationId ? { ...m, status: 'stopped' as const } : m);
      setMedications(updated);
    } catch (err: unknown) {
      logError('Failed to discontinue medication', undefined, { medicationId, error: err });
      setError('Failed to discontinue medication');
    } finally {
      setDiscontinuingId(null);
    }
  };

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('medications.title')} subheader={t('medications.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <div className="row gap-sm" role="tablist" aria-label={t('medications.tabs.label')}>
              <Button
                type="button"
                role="tab"
                aria-selected={activeTab === 'active'}
                variant={activeTab === 'active' ? 'solid' : 'outline'}
                onClick={() => setActiveTab('active')}
              >
                {t('medications.tabs.active')}
              </Button>
              <Button
                type="button"
                role="tab"
                aria-selected={activeTab === 'history'}
                variant={activeTab === 'history' ? 'solid' : 'outline'}
                onClick={() => setActiveTab('history')}
              >
                {t('medications.tabs.history')}
              </Button>
            </div>
            {visibleMedications.length === 0 ? (
              <p className="empty">{t('medications.empty')}</p>
            ) : (
              visibleMedications.map((medication) => (
                <div key={medication.id} className="data-card">
                  <Link to={`/medications/${medication.id}`} className="medication-link">
                    <div>
                      <strong>{medication.medication} {medication.dosage}</strong>
                      <p className="muted">{medication.schedule}</p>
                    </div>
                    <div className="row gap-sm align-center">
                      <span className="pill">
                        {t('medications.adherenceLabel')}: {medication.adherence}%
                      </span>
                      {medication.status && <span className={`badge badge--${medication.status}`}>{medication.status}</span>}
                    </div>
                  </Link>
                  {activeTab === 'active' && medication.status !== 'stopped' && (
                    <div className="row gap-sm mt-2">
                      <Button
                        size="small"
                        variant="outline"
                        onClick={() => void handleRefill(medication.id)}
                        disabled={refillingId === medication.id}
                        aria-busy={refillingId === medication.id}
                      >
                        {refillingId === medication.id ? 'Requesting...' : 'Refill'}
                      </Button>
                      <Button
                        size="small"
                        variant="outline"
                        tone="danger"
                        onClick={() => void handleDiscontinue(medication.id)}
                        disabled={discontinuingId === medication.id}
                        aria-busy={discontinuingId === medication.id}
                      >
                        {discontinuingId === medication.id ? 'Discontinuing...' : 'Discontinue'}
                      </Button>
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
