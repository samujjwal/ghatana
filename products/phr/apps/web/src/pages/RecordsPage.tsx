import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router';
import { fetchDashboardData } from '../api/phrApi';
import type { PatientRecordSummary } from '../types';

export function RecordsPage(): React.ReactElement {
  const [records, setRecords] = useState<PatientRecordSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then(data => setRecords(data.records))
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load records'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">Loading records...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <Card>
      <CardHeader title="Patient records" subheader="All record types are accessible through the portal" />
      <CardContent>
        <div className="stack gap-md">
          {records.map((record) => (
            <Link key={record.id} className="data-card" to={`/records/${record.id}`}>
              <div>
                <strong>{record.title}</strong>
                <p className="muted">{record.resourceType} · Updated {new Date(record.updatedAt).toLocaleString()}</p>
              </div>
              <span className="pill">{record.category}</span>
            </Link>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}