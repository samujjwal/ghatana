import React, { useMemo, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { requestEmergencyAccess } from '../api/emergencyApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
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
  const [confirmingRequest, setConfirmingRequest] = useState<boolean>(false);
  const [requestError, setRequestError] = useState<SafeApiErrorState | null>(null);
  const [requestResult, setRequestResult] = useState<EmergencyAccessEvent | null>(null);

  const validateRequest = (): boolean => {
    if (!patientId.trim() || !reason.trim() || !clinicianId.trim()) {
      setRequestError({ message: t('emergency.error.required') });
      return false;
    }
    if (reason.trim().length < 5) {
      setRequestError({ message: t('emergency.error.request') });
      return false;
    }
    return true;
  };

  const handleRequestAccess = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    setRequestError(null);
    setRequestResult(null);

    if (!validateRequest()) {
      setConfirmingRequest(false);
      return;
    }

    setConfirmingRequest(true);
  };

  const submitConfirmedRequest = async (): Promise<void> => {
    setRequestError(null);
    setRequestResult(null);
    if (!validateRequest()) {
      setConfirmingRequest(false);
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
      setConfirmingRequest(false);
    } catch (err: unknown) {
      setRequestError(toSafeApiErrorState(err, t('emergency.error.request')));
      logError('Failed to request emergency access', undefined, { error: err });
    } finally {
      setRequesting(false);
    }
  };

  const updatePatientId = (value: string): void => {
    setPatientId(value);
    setConfirmingRequest(false);
  };

  const updateClinicianId = (value: string): void => {
    setClinicianId(value);
    setConfirmingRequest(false);
  };

  const updateReason = (value: string): void => {
    setReason(value);
    setConfirmingRequest(false);
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
            <div className="mt-4">
              <SafeError
                message={requestError.message}
                correlationId={requestError.correlationId}
                onDismiss={() => setRequestError(null)}
              />
            </div>
          )}

          <form onSubmit={(e) => void handleRequestAccess(e)} className="stack gap-md mt-6" noValidate>
            <Input
              aria-label={t('emergency.patientId.label')}
              placeholder={t('emergency.patientId.placeholder')}
              value={patientId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => updatePatientId(e.target.value)}
              required
            />
            <Input
              aria-label={t('emergency.clinicianId.label')}
              placeholder={t('emergency.clinicianId.placeholder')}
              value={clinicianId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => updateClinicianId(e.target.value)}
              required
            />
            <Input
              aria-label={t('emergency.reason.label')}
              placeholder={t('emergency.reason.placeholder')}
              value={reason}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => updateReason(e.target.value)}
              required
            />
            <Button type="submit" variant="destructive" disabled={requesting} aria-busy={requesting}>
              {requesting ? t('emergency.requesting') : t('emergency.request')}
            </Button>
          </form>

          {confirmingRequest && (
            <div className="data-card mt-4" role="status">
              <div>
                <strong>{t('emergency.confirm.title')}</strong>
                <p className="muted">{t('emergency.confirm.body')}</p>
              </div>
              <div className="row gap-sm">
                <Button
                  type="button"
                  variant="destructive"
                  disabled={requesting}
                  aria-busy={requesting}
                  onClick={() => void submitConfirmedRequest()}
                >
                  {requesting ? t('emergency.requesting') : t('emergency.confirm.submit')}
                </Button>
                <Button type="button" variant="secondary" disabled={requesting} onClick={() => setConfirmingRequest(false)}>
                  {t('emergency.confirm.cancel')}
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
