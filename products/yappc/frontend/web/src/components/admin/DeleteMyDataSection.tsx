/**
 * DeleteMyDataSection — YAPPC Web.
 *
 * Provides the authenticated user a one-click path to request complete deletion of
 * their conversation memory and AI-assist history from YAPPC. The backend delegates
 * to AEP's GDPR/CCPA `DELETE /api/users/me/data` endpoint and purges local
 * `ConversationMessage` rows before responding with `202 Accepted`.
 *
 * The component honours GDPR right-to-erasure (Art. 17) and CCPA right-to-delete
 * requirements (F-Y058 / C-Y15).
 *
 * @doc.type component
 * @doc.purpose GDPR/CCPA delete-my-data request flow for authenticated user
 * @doc.layer product
 * @doc.pattern UI Form
 */

import React, { useState, useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { userData } from '@/lib/api';
import { useTranslation } from '@ghatana/i18n';
import { Button } from '../ui/Button';

// ── Types ──────────────────────────────────────────────────────────────────────

interface DeleteMyDataResponse {
  /** URL to poll for completion. */
  statusUrl: string;
  /** Estimated completion in ISO 8601 duration (e.g. PT30S). */
  estimatedDuration: string;
}

// ── API call ──────────────────────────────────────────────────────────────────

async function requestDeleteMyData(): Promise<DeleteMyDataResponse> {
  const { statusUrl } = await userData.requestDeletion();
  return { statusUrl: statusUrl ?? '', estimatedDuration: 'PT30S' };
}

// ── Component ──────────────────────────────────────────────────────────────────

export interface DeleteMyDataSectionProps {
  /** Optional CSS class applied to the outer card. */
  className?: string;
}

type Stage = 'idle' | 'confirm' | 'submitting' | 'requested' | 'error';

/**
 * DeleteMyDataSection
 *
 * Renders a danger-zone card with a confirmation flow before issuing the
 * delete-my-data request. Two-step confirmation prevents accidental data loss.
 */
const DeleteMyDataSection: React.FC<DeleteMyDataSectionProps> = ({ className }) => {
  const { t } = useTranslation('common');
  const [stage, setStage] = useState<Stage>('idle');
  const [statusUrl, setStatusUrl] = useState<string>('');

  const { mutate, error } = useMutation<DeleteMyDataResponse, Error, void>({
    mutationFn: requestDeleteMyData,
    onMutate: () => setStage('submitting'),
    onSuccess: (data) => {
      setStatusUrl(data.statusUrl);
      setStage('requested');
    },
    onError: () => setStage('error'),
  });

  const handleRequestDelete = useCallback(() => {
    setStage('confirm');
  }, []);

  const handleConfirm = useCallback(() => {
    mutate();
  }, [mutate]);

  const handleCancel = useCallback(() => {
    setStage('idle');
  }, []);

  const handleRetry = useCallback(() => {
    setStage('idle');
  }, []);

  return (
    <div
      className={`rounded-lg border border-destructive-border bg-destructive-bg p-6 ${className ?? ''}`}
      aria-label={t('admin.deleteMyData.sectionLabel')}
    >
      <h3 className="text-base font-semibold text-destructive">Delete my data</h3>
      <p className="mt-1 text-sm text-destructive">
        Permanently deletes your conversation memory, AI-assist history, and cached
        prompt responses. This action cannot be undone. Your workspace projects and
        files are not affected.
      </p>

      {stage === 'idle' && (
        <Button
          type="button"
          onClick={handleRequestDelete}
          variant="outline"
          tone="danger"
          className="mt-4 rounded-md border border-destructive-border bg-white px-4 py-2 text-sm font-medium text-destructive hover:bg-destructive-bg focus:outline-none focus:ring-2 focus:ring-red-500"
        >
          Request data deletion
        </Button>
      )}

      {stage === 'confirm' && (
        <div className="mt-4 space-y-3">
          <p className="text-sm font-medium text-destructive">
            Are you sure? This will permanently erase your conversation history and
            AI memory. This cannot be undone.
          </p>
          <div className="flex gap-3">
            <Button
              type="button"
              onClick={handleConfirm}
              variant="destructive"
              className="rounded-md bg-destructive-bg px-4 py-2 text-sm font-medium text-white hover:bg-destructive-bg focus:outline-none focus:ring-2 focus:ring-red-500"
            >
              Yes, delete my data
            </Button>
            <Button
              type="button"
              onClick={handleCancel}
              variant="outline"
              className="rounded-md border border-neutral-300 bg-white px-4 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-50 focus:outline-none focus:ring-2 focus:ring-neutral-400"
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {stage === 'submitting' && (
        <p className="mt-4 text-sm text-destructive" role="status" aria-live="polite">
          Submitting deletion request…
        </p>
      )}

      {stage === 'requested' && (
        <div className="mt-4 rounded-md border border-success-border bg-success-bg p-4 text-sm text-success-color" role="alert">
          <p className="font-medium">Deletion request submitted.</p>
          <p className="mt-1">
            Your data will be purged within 30 days in accordance with GDPR Art. 17.
            You will receive an email confirmation when the process is complete.
          </p>
          {statusUrl && (
            <a
              href={statusUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-2 inline-block text-success-color underline hover:no-underline"
            >
              Check deletion status
            </a>
          )}
        </div>
      )}

      {stage === 'error' && (
        <div className="mt-4 rounded-md border border-destructive-border bg-destructive-bg p-4 text-sm text-destructive" role="alert">
          <p className="font-medium">Request failed.</p>
          <p className="mt-1">{error?.message ?? 'An unexpected error occurred. Please try again.'}</p>
          <Button
            type="button"
            onClick={handleRetry}
            variant="outline"
            tone="danger"
            className="mt-2 rounded-md border border-destructive-border bg-white px-3 py-1 text-sm text-destructive hover:bg-destructive-bg"
          >
            Try again
          </Button>
        </div>
      )}
    </div>
  );
};

export default DeleteMyDataSection;
