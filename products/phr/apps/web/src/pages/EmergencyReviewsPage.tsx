import React, { useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { reviewEmergencyAccess } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { EmergencyAccessEvent } from '../types';

// Hard-coded context for demo; production wires from auth session.
const DEMO_CONTEXT = { tenantId: 'tenant-health-1', principalId: 'current', role: 'admin' };

export function EmergencyReviewsPage(): React.ReactElement {
  const [reviewEventId, setReviewEventId] = useState<string>('');
  const [reviewNote, setReviewNote] = useState<string>('');
  const [reviewing, setReviewing] = useState<boolean>(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const [reviewResult, setReviewResult] = useState<EmergencyAccessEvent | null>(null);

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
    <div className="single-column-layout">
      <Card>
        <CardHeader title={t('emergency.review.title')} subheader={t('emergency.review.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            <p>{t('emergency.review.subheader')}</p>
            <ul className="stack gap-sm">
              <li>Pending emergency access reviews</li>
              <li>Overdue emergency access reviews</li>
              <li>Audit trail for emergency access</li>
            </ul>
          </div>

          {reviewResult && (
            <div role="status" className="success-message mt-4">
              {t('emergency.success.review', { id: reviewResult.id })}
            </div>
          )}
          {reviewError && (
            <div role="alert" className="error mt-4">{reviewError}</div>
          )}

          <form onSubmit={(e) => void handleReview(e)} className="stack gap-md mt-6" noValidate>
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
