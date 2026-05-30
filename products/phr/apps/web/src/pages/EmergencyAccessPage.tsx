import React, { useMemo, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { requestEmergencyAccess } from '../api/emergencyApi';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { EmergencyAccessEvent } from '../types';

export function EmergencyAccessPage(): React.ReactElement {
  const { tenantId, principalId, role } = usePhrAccess();
  const apiContext = useMemo(() => ({ tenantId, principalId, role }), [tenantId, principalId, role]);
  // Emergency access request fields
  const [patientId, setPatientId] = useState<string>('');
  const [reason, setReason] = useState<string>('');
  const [clinicianId, setClinicianId] = useState<string>('');
  const [requesting, setRequesting] = useState<boolean>(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [requestResult, setRequestResult] = useState<EmergencyAccessEvent | null>(null);

  const handleRequestAccess = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setRequestError(null);
    setRequestResult(null);

    if (!patientId.trim() || !reason.trim() || !clinicianId.trim()) {
      setRequestError(t('validation.required', { field: 'Patient ID, reason, and clinician ID' }));
      return;
    }
    if (reason.trim().length < 5) {
      setRequestError(t('emergency.error.request'));
      return;
    }

    setRequesting(true);
    try {
      const result = await requestEmergencyAccess(
        { patientId: patientId.trim(), reason: reason.trim(), clinicianId: clinicianId.trim() },
        apiContext,
      );
      setRequestResult(result);
      setPatientId('');
      setReason('');
      setClinicianId('');
    } catch (err: unknown) {
      setRequestError(err instanceof Error ? err.message : t('emergency.error.request'));
      logError('Failed to request emergency access', undefined, { error: err });
    } finally {
      setRequesting(false);
    }
  };

  return (
    <div className="single-column-layout">
      <Card>
        <CardHeader title={t('emergency.title')} subheader={t('emergency.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <p>{t('emergency.summary')}</p>
            <ul className="stack gap-sm">
              <li>{t('emergency.reason')}</li>
              <li>{t('emergency.consent')}</li>
              <li>{t('emergency.redaction')}</li>
            </ul>
          </div>

          {requestResult && (
            <div role="status" className="success-message mt-4">
              {t('emergency.success.request', { id: requestResult.id })}
            </div>
          )}
          {requestError && (
            <div role="alert" className="error mt-4">{requestError}</div>
          )}

          <form onSubmit={(e) => void handleRequestAccess(e)} className="stack gap-md mt-6" noValidate>
            <Input
              aria-label={t('emergency.patientId.label')}
              placeholder={t('emergency.patientId.placeholder')}
              value={patientId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPatientId(e.target.value)}
              required
            />
            <Input
              aria-label={t('emergency.clinicianId.label')}
              placeholder={t('emergency.clinicianId.placeholder')}
              value={clinicianId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setClinicianId(e.target.value)}
              required
            />
            <Input
              aria-label={t('emergency.reason.label')}
              placeholder={t('emergency.reason.placeholder')}
              value={reason}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setReason(e.target.value)}
              required
            />
            <Button type="submit" variant="destructive" disabled={requesting} aria-busy={requesting}>
              {requesting ? t('emergency.requesting') : t('emergency.request')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
