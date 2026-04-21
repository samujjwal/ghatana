import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import type { LabResultSummary } from '../types';

export function LabsPage(): React.ReactElement {
  const [labs, setLabs] = useState<LabResultSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then(data => setLabs(data.labs))
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load lab results'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">Loading lab results...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <Card>
      <CardHeader title="Lab results" subheader="HL7-ingested results rendered in a patient-readable view" />
      <CardContent>
        <div className="stack gap-md">
          {labs.map((lab) => (
            <div key={lab.id} className="data-card">
              <div>
                <strong>{lab.name}</strong>
                <p className="muted">Collected {lab.collectedAt}</p>
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