import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { LabResultSummary } from '../types';

export function LabsPage(): React.ReactElement {
  const [labs, setLabs] = useState<LabResultSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => setLabs(data.labs))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.labsLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('labs.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  return (
    <Card>
      <CardHeader title={t('labs.title')} subheader={t('labs.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          {labs.map((lab) => (
            <div key={lab.id} className="data-card">
              <div>
                <strong>{lab.name}</strong>
                <p className="muted">{t('labs.collected', { date: formatPhrDate(lab.collectedAt) })}</p>
              </div>
              <div className="row gap-sm align-center">
                <span className={`pill ${lab.status === 'attention' ? 'warning' : ''}`}>{lab.status}</span>
                <strong>{lab.value}</strong>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
