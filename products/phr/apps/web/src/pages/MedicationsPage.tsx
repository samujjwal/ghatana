import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import { formatPhrPercent, t } from '../i18n/phrI18n';
import type { MedicationSummary } from '../types';

export function MedicationsPage(): React.ReactElement {
  const [medications, setMedications] = useState<MedicationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => setMedications(data.medications))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.medicationsLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('medications.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  return (
    <Card>
      <CardHeader title={t('medications.title')} subheader={t('medications.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          {medications.map((medication) => (
            <div key={medication.id} className="data-card">
              <div>
                <strong>{medication.medication} {medication.dosage}</strong>
                <p className="muted">{t('medications.schedule', { schedule: medication.schedule })}</p>
              </div>
              <span className="pill">
                {t('medications.adherence', { percent: formatPhrPercent(medication.adherence) })}
              </span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
