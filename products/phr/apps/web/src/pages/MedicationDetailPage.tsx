import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchMedicationDetail } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import type { MedicationSummary } from '../types';

export function MedicationDetailPage(): React.ReactElement {
  const { medicationId } = useParams();
  const { session } = usePhrSession();
  const [medication, setMedication] = useState<MedicationSummary & {
    interactions: string[];
    warnings: string[];
    history: Array<{ date: string; action: string }>;
  } | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !medicationId) return;
    fetchMedicationDetail(session.principalId, medicationId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setMedication)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load medication detail'))
      .finally(() => setLoading(false));
  }, [session, medicationId]);

  if (loading) return <div className="loading">Loading medication details...</div>;
  if (error) return <div className="error">Error: {error}</div>;
  if (!medication) return <div className="empty">Medication not found</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={medication.medication} subheader="Medication Details" />
        <CardContent>
          <div className="stack gap-md">
            <div className="detail-row">
              <span className="detail-label">Dosage:</span>
              <span className="detail-value"><strong>{medication.dosage}</strong></span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Schedule:</span>
              <span className="detail-value">{medication.schedule}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Adherence:</span>
              <span className="detail-value">
                <strong>{medication.adherence}%</strong>
                <span className="muted"> (based on refill data)</span>
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Interactions and Warnings */}
      {(medication.interactions.length > 0 || medication.warnings.length > 0) && (
        <Card>
          <CardHeader title="Safety Information" subheader="Interactions and warnings" />
          <CardContent>
            <div className="stack gap-md">
              {medication.interactions.length > 0 && (
                <div>
                  <h4>Drug Interactions</h4>
                  <ul className="stack gap-sm">
                    {medication.interactions.map((interaction, idx) => (
                      <li key={idx} className="warning-item">{interaction}</li>
                    ))}
                  </ul>
                </div>
              )}
              {medication.warnings.length > 0 && (
                <div>
                  <h4>Warnings</h4>
                  <ul className="stack gap-sm">
                    {medication.warnings.map((warning, idx) => (
                      <li key={idx} className="warning-item">{warning}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {/* History */}
      <Card>
        <CardHeader title="Prescription History" subheader="Changes and refills" />
        <CardContent>
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {medication.history.map((entry, idx) => (
                <tr key={idx}>
                  <td><time dateTime={entry.date}>{new Date(entry.date).toLocaleDateString()}</time></td>
                  <td>{entry.action}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
