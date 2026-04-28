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
      className={`rounded-lg border border-red-200 bg-red-50 p-6 ${className ?? ''}`}
      aria-label="Delete my data"
    >
      <h3 className="text-base font-semibold text-red-700">Delete my data</h3>
      <p className="mt-1 text-sm text-red-600">
        Permanently deletes your conversation memory, AI-assist history, and cached
        prompt responses. This action cannot be undone. Your workspace projects and
        files are not affected.
      </p>

      {stage === 'idle' && (
        <button
          type="button"
          onClick={handleRequestDelete}
          className="mt-4 rounded-md border border-red-600 bg-white px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-500"
        >
          Request data deletion
        </button>
      )}

      {stage === 'confirm' && (
        <div className="mt-4 space-y-3">
          <p className="text-sm font-medium text-red-700">
            Are you sure? This will permanently erase your conversation history and
            AI memory. This cannot be undone.
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={handleConfirm}
              className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500"
            >
              Yes, delete my data
            </button>
            <button
              type="button"
              onClick={handleCancel}
              className="rounded-md border border-neutral-300 bg-white px-4 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-50 focus:outline-none focus:ring-2 focus:ring-neutral-400"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {stage === 'submitting' && (
        <p className="mt-4 text-sm text-red-600" role="status" aria-live="polite">
          Submitting deletion request…
        </p>
      )}

      {stage === 'requested' && (
        <div className="mt-4 rounded-md border border-green-200 bg-green-50 p-4 text-sm text-green-700" role="alert">
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
              className="mt-2 inline-block text-green-700 underline hover:no-underline"
            >
              Check deletion status
            </a>
          )}
        </div>
      )}

      {stage === 'error' && (
        <div className="mt-4 rounded-md border border-red-300 bg-red-100 p-4 text-sm text-red-700" role="alert">
          <p className="font-medium">Request failed.</p>
          <p className="mt-1">{error?.message ?? 'An unexpected error occurred. Please try again.'}</p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-2 rounded-md border border-red-500 bg-white px-3 py-1 text-sm text-red-700 hover:bg-red-50"
          >
            Try again
          </button>
        </div>
      )}
    </div>
  );
};

export default DeleteMyDataSection;
