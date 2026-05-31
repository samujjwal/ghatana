import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader, Table, TableBody, TableCell, TableHead, TableRow } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchMedicationDetail } from '../api/clinicalApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { MedicationDetail } from '../types';

export function MedicationDetailPage(): React.ReactElement {
  const { medicationId } = useParams();
  const { session } = usePhrSession();
  const [medication, setMedication] = useState<MedicationDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session || !medicationId) return;
    fetchMedicationDetail(session.principalId, medicationId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
      persona: session.persona,
      tier: session.tier,
      facilityId: session.facilityId,
    })
      .then(setMedication)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('medicationDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, medicationId]);

  if (loading) return <div className="loading">{t('medicationDetail.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;
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
              <span className="detail-value">{medication.schedule ?? t('medicationDetail.notProvided')}</span>
            </div>
            {medication.refillsRemaining !== undefined && (
              <div className="detail-row">
                <span className="detail-label">{t('medicationDetail.refillsRemaining')}:</span>
                <span className="detail-value"><strong>{medication.refillsRemaining}</strong></span>
              </div>
            )}
            {medication.prescribedAt && (
              <div className="detail-row">
                <span className="detail-label">{t('medicationDetail.prescribedAt')}:</span>
                <span className="detail-value"><time dateTime={medication.prescribedAt}>{new Date(medication.prescribedAt).toLocaleDateString()}</time></span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {((medication.interactions?.length ?? 0) > 0 || (medication.warnings?.length ?? 0) > 0) && (
        <Card>
          <CardHeader title={t('medicationDetail.safety.title')} subheader={t('medicationDetail.safety.subheader')} />
          <CardContent>
            <div className="stack gap-md">
              {(medication.interactions?.length ?? 0) > 0 && (
                <div>
                  <h4>{t('medicationDetail.interactions')}</h4>
                  <ul className="stack gap-sm">
                    {medication.interactions?.map((interaction, idx) => (
                      <li key={idx} className="warning-item">{interaction}</li>
                    ))}
                  </ul>
                </div>
              )}
              {(medication.warnings?.length ?? 0) > 0 && (
                <div>
                  <h4>{t('medicationDetail.warnings')}</h4>
                  <ul className="stack gap-sm">
                    {medication.warnings?.map((warning, idx) => (
                      <li key={idx} className="warning-item">{warning}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {(medication.history?.length ?? 0) > 0 && (
        <Card>
          <CardHeader title={t('medicationDetail.history.title')} subheader={t('medicationDetail.history.subheader')} />
          <CardContent>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell component="th">{t('labDetail.date')}</TableCell>
                  <TableCell component="th">{t('medicationDetail.action')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {medication.history?.map((entry, idx) => (
                  <TableRow key={idx}>
                    <TableCell><time dateTime={entry.date}>{new Date(entry.date).toLocaleDateString()}</time></TableCell>
                    <TableCell>{entry.action}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
