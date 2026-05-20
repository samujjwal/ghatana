import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchDashboardData } from '../api/phrApi';
import type { PatientRecordSummary } from '../types';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const [record, setRecord] = useState<PatientRecordSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => {
        setRecord(data.records.find((item) => item.id === recordId) ?? null);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load record payload');
      })
      .finally(() => setLoading(false));
  }, [recordId]);

  if (loading) {
    return <div className="loading">Loading record...</div>;
  }

  if (error) {
    return <div className="error">Error: {error}</div>;
  }

  if (!record) {
    return (
      <Card>
        <CardHeader title="Record unavailable" subheader="FHIR resource rendering" />
        <CardContent>
          <p className="muted">No record payload is available for the requested identifier.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title={record.title} subheader="FHIR resource rendering" />
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
