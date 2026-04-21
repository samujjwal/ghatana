import React, { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import type { MedicationSummary } from '../types';

export function MedicationsPage(): React.ReactElement {
  const [medications, setMedications] = useState<MedicationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then(data => setMedications(data.medications))
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load medications'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">Loading medications...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  return (
    <Card>
      <CardHeader title="Medication management" subheader="Medication schedule, adherence, and refill planning" />
      <CardContent>
        <div className="stack gap-md">
          {medications.map((medication) => (
            <div key={medication.id} className="data-card">
              <div>
                <strong>{medication.medication} {medication.dosage}</strong>
                <p className="muted">Schedule {medication.schedule}</p>
              </div>
              <span className="pill">{Math.round(medication.adherence * 100)}% adherence</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}