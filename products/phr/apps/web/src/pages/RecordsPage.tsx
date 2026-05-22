import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchDashboardData } from '../api/phrApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { PatientRecordSummary } from '../types';

export function RecordsPage(): React.ReactElement {
  const [records, setRecords] = useState<PatientRecordSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => setRecords(data.records))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.recordsLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('records.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  return (
    <Card>
      <CardHeader title={t('records.title')} subheader={t('records.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          {records.map((record) => (
            <Link key={record.id} className="data-card" to={`/records/${record.id}`}>
              <div>
                <strong>{record.title}</strong>
                <p className="muted">
                  {t('records.updated', {
                    resourceType: record.resourceType,
                    date: formatPhrDateTime(record.updatedAt),
                  })}
                </p>
              </div>
              <span className="pill">{record.category}</span>
            </Link>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
