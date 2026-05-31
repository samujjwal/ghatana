import React, { useMemo, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { reviewEmergencyAccess } from '../api/emergencyApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { t } from '../i18n/phrI18n';
import type { EmergencyAccessEvent } from '../types';

export function EmergencyReviewsPage(): React.ReactElement {
  const { tenantId, principalId, role } = usePhrAccess();
  const apiContext = useMemo(() => ({ tenantId, principalId, role }), [tenantId, principalId, role]);
  const [reviewEventId, setReviewEventId] = useState<string>('');
  const [reviewNote, setReviewNote] = useState<string>('');
  const [reviewing, setReviewing] = useState<boolean>(false);
  const [reviewError, setReviewError] = useState<SafeApiErrorState | null>(null);
  const [reviewResult, setReviewResult] = useState<EmergencyAccessEvent | null>(null);

  const handleReview = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setReviewError(null);
    setReviewResult(null);

    if (!reviewEventId.trim() || !reviewNote.trim()) {
      setReviewError({ message: t('emergency.review.error.required') });
      return;
    }

    setReviewing(true);
    try {
      const result = await reviewEmergencyAccess(
        { eventId: reviewEventId.trim(), reviewNote: reviewNote.trim(), reviewerId: principalId },
        apiContext,
      );
      setReviewResult(result);
      setReviewEventId('');
      setReviewNote('');
    } catch (err: unknown) {
      setReviewError(toSafeApiErrorState(err, t('emergency.error.review')));
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
            <p>{t('emergency.review.summary')}</p>
          </div>

          {reviewResult && (
            <div role="status" className="success-message mt-4">
              {t('emergency.success.review', { id: reviewResult.id })}
            </div>
          )}
          {reviewError && (
            <div className="mt-4">
              <SafeError
                message={reviewError.message}
                correlationId={reviewError.correlationId}
                onDismiss={() => setReviewError(null)}
              />
            </div>
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
            <Button type="submit" variant="secondary" disabled={reviewing} aria-busy={reviewing}>
              {reviewing ? t('emergency.review.submitting') : t('emergency.review.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
