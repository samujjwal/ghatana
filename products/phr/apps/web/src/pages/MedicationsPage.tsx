import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchMedications } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { MedicationSummary } from '../types';

export function MedicationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [medications, setMedications] = useState<MedicationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'active' | 'history'>('active');

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

  if (loading) return <div className="loading">{t('medications.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

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
              visibleMedications.map((medication) => (
                <Link key={medication.id} to={`/medications/${medication.id}`} className="data-card">
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
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
