import React, { useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { requestEmergencyAccess, reviewEmergencyAccess } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { EmergencyAccessEvent } from '../types';

// Hard-coded context for demo; production wires from auth session.
const DEMO_CONTEXT = { tenantId: 'tenant-health-1', principalId: 'current', role: 'clinician' };

export function EmergencyAccessPage(): React.ReactElement {
  // Emergency access request fields
  const [patientId, setPatientId] = useState<string>('');
  const [reason, setReason] = useState<string>('');
  const [clinicianId, setClinicianId] = useState<string>('');
  const [requesting, setRequesting] = useState<boolean>(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [requestResult, setRequestResult] = useState<EmergencyAccessEvent | null>(null);

  // Review fields
  const [reviewEventId, setReviewEventId] = useState<string>('');
  const [reviewNote, setReviewNote] = useState<string>('');
  const [reviewing, setReviewing] = useState<boolean>(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewResult, setReviewResult] = useState<EmergencyAccessEvent | null>(null);

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
        DEMO_CONTEXT,
      );
      setRequestResult(result);
      setPatientId('');
      setReason('');
      setClinicianId('');
    } catch (err: unknown) {
      setRequestError(err instanceof Error ? err.message : t('emergency.error.request'));
    } finally {
      setRequesting(false);
    }
  };

  const handleReview = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setReviewError(null);
    setReviewResult(null);

    if (!reviewEventId.trim() || !reviewNote.trim()) {
      setReviewError(t('validation.required', { field: 'Event ID and review note' }));
      return;
    }

    setReviewing(true);
    try {
      const result = await reviewEmergencyAccess(
        { eventId: reviewEventId.trim(), reviewNote: reviewNote.trim(), reviewerId: DEMO_CONTEXT.principalId },
        DEMO_CONTEXT,
      );
      setReviewResult(result);
      setReviewEventId('');
      setReviewNote('');
    } catch (err: unknown) {
      setReviewError(err instanceof Error ? err.message : t('emergency.error.review'));
    } finally {
      setReviewing(false);
    }
  };

  return (
    <div className="two-column-layout">
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
            <Button type="submit" className="danger-button" disabled={requesting}>
              {requesting ? t('emergency.requesting') : t('emergency.request')}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title={t('emergency.review.title')} subheader={t('emergency.review.subheader')} />
        <CardContent>
          {reviewResult && (
            <div role="status" className="success-message mb-4">
              {t('emergency.success.review', { id: reviewResult.id })}
            </div>
          )}
          {reviewError && (
            <div role="alert" className="error mb-4">{reviewError}</div>
          )}
          <form onSubmit={(e) => void handleReview(e)} className="stack gap-md" noValidate>
            <Input
              aria-label={t('emergency.reviewEventId.label')}
              placeholder={t('emergency.reviewEventId.placeholder')}
              value={reviewEventId}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setReviewEventId(e.target.value)}
              required
            />
            <Input
              aria-label={t('emergency.review.note.label')}
              placeholder={t('emergency.review.note.placeholder')}
              value={reviewNote}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setReviewNote(e.target.value)}
              required
            />
            <Button type="submit" className="secondary-button" disabled={reviewing}>
              {reviewing ? t('emergency.review.submitting') : t('emergency.review.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
