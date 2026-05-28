import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchMedicationDetail } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
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
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('medicationDetail.error.load')))
      .finally(() => setLoading(false));
  }, [session, medicationId]);

  if (loading) return <div className="loading">{t('medicationDetail.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;
  if (!medication) return <div className="empty">{t('medicationDetail.notFound')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={medication.medication} subheader={t('medicationDetail.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <div className="detail-row">
              <span className="detail-label">{t('medicationDetail.dosage')}:</span>
              <span className="detail-value"><strong>{medication.dosage}</strong></span>
            </div>
            <div className="detail-row">
              <span className="detail-label">{t('medicationDetail.schedule')}:</span>
              <span className="detail-value">{medication.schedule}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">{t('medications.adherenceLabel')}:</span>
              <span className="detail-value">
                <strong>{medication.adherence}%</strong>
                <span className="muted"> {t('medicationDetail.refillBasis')}</span>
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Interactions and Warnings */}
      {(medication.interactions.length > 0 || medication.warnings.length > 0) && (
        <Card>
          <CardHeader title={t('medicationDetail.safety.title')} subheader={t('medicationDetail.safety.subheader')} />
          <CardContent>
            <div className="stack gap-md">
              {medication.interactions.length > 0 && (
                <div>
                  <h4>{t('medicationDetail.interactions')}</h4>
                  <ul className="stack gap-sm">
                    {medication.interactions.map((interaction, idx) => (
                      <li key={idx} className="warning-item">{interaction}</li>
                    ))}
                  </ul>
                </div>
              )}
              {medication.warnings.length > 0 && (
                <div>
                  <h4>{t('medicationDetail.warnings')}</h4>
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
        <CardHeader title={t('medicationDetail.history.title')} subheader={t('medicationDetail.history.subheader')} />
        <CardContent>
          <table className="data-table">
            <thead>
              <tr>
                <th>{t('labDetail.date')}</th>
                <th>{t('medicationDetail.action')}</th>
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
