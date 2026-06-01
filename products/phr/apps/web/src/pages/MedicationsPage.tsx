import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchMedications } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { PhrMessageKey } from '../i18n/phrI18n';
import type { MedicationSummary } from '../types';

const medicationStatusKeys: Record<NonNullable<MedicationSummary['status']>, PhrMessageKey> = {
  active: 'medications.status.active',
  history: 'medications.status.history',
  stopped: 'medications.status.stopped',
};

function medicationStatusLabel(status: NonNullable<MedicationSummary['status']>): string {
  return t(medicationStatusKeys[status]);
}

export function MedicationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [medications, setMedications] = useState<MedicationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [activeTab, setActiveTab] = useState<'active' | 'history'>('active');
  useEffect(() => {
    if (!session) return;
    fetchMedications(session.principalId, toSessionContext(session))
      .then(setMedications)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('medications.error'))))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('medications.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;

  const visibleMedications = medications.filter((medication) => (
    activeTab === 'active'
      ? medication.status == null || medication.status === 'active'
      : medication.status === 'history' || medication.status === 'stopped'
  ));

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
              <ul className="medication-list" role="list">
                {visibleMedications.map((medication) => (
                  <li key={medication.id} className="medication-item">
                    <div className="data-card">
                      <Link to={`/medications/${medication.id}`} className="medication-link">
                        <div>
                          <strong>{medication.medication} {medication.dosage}</strong>
                          {medication.schedule && <p className="muted">{medication.schedule}</p>}
                          {medication.warnings && medication.warnings.length > 0 && (
                            <p className="warning-text" aria-label={t('medications.warnings.label')}>
                              {medication.warnings.map((warning, idx) => (
                                <span key={idx} className="warning-badge">{warning}</span>
                              ))}
                            </p>
                          )}
                        </div>
                        <div className="row gap-sm align-center">
                          {medication.adherence !== undefined && (
                            <span className="pill">
                              {t('medications.adherenceLabel')}: {medication.adherence}%
                            </span>
                          )}
                          {medication.status && <span className={`badge badge--${medication.status}`}>{medicationStatusLabel(medication.status)}</span>}
                        </div>
                      </Link>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
