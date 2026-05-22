import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchDashboardData } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { PatientRecordSummary } from '../types';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const [record, setRecord] = useState<PatientRecordSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => {
        setRecord(data.records.find((item) => item.id === recordId) ?? null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : t('error.recordPayloadLoad'));
      })
      .finally(() => setLoading(false));
  }, [recordId]);

  if (loading) {
    return <div className="loading">{t('recordDetail.loading')}</div>;
  }

  if (error) {
    return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;
  }

  if (!record) {
    return (
      <Card>
        <CardHeader title={t('recordDetail.unavailable.title')} subheader={t('recordDetail.rendering')} />
        <CardContent>
          <p className="muted">{t('recordDetail.unavailable.body')}</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title={record.title} subheader={t('recordDetail.rendering')} />
      <CardContent>
        <div className="stack gap-md">
          <div className="row gap-sm">
            <span className="pill">{record.resourceType}</span>
            <span className="pill ghost">{record.category}</span>
          </div>
          <pre className="code-block">{record.fhirJson}</pre>
        </div>
      </CardContent>
    </Card>
  );
}
