/**
 * useRequirementOrchestration Hook
 *
 * Wraps the AEP orchestration client in a TanStack Query mutation so the
 * lifecycle route can fire-and-track requirement orchestration requests after
 * an approval decision.
 *
 * @doc.type hook
 * @doc.purpose Submit requirement orchestration events to AEP after approval
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useMutation } from '@tanstack/react-query';
import {
  submitRequirementApproved,
  type OrchestrationRunRef,
} from '../services/aep/AepOrchestrationClient';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface SubmitOrchestrationParams {
  projectId: string;
  requirementId: string;
  approvalId: string;
}

export interface UseRequirementOrchestrationResult {
  /** Fire the AEP orchestration request for an approved requirement. */
  submitApproved: (params: SubmitOrchestrationParams) => void;
  /** The AEP run reference returned on success, or undefined while pending. */
  runRef: OrchestrationRunRef | undefined;
  /** True while the orchestration request is in-flight. */
  isSubmitting: boolean;
  /** Error from the most recent submission attempt, if any. */
  error: Error | null;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * @example
 * ```tsx
 * const { submitApproved, isSubmitting } = useRequirementOrchestration();
 *
 * // After user approves:
 * submitApproved({ projectId, requirementId, approvalId });
 * ```
 */
export function useRequirementOrchestration(): UseRequirementOrchestrationResult {
  const mutation = useMutation<
    OrchestrationRunRef,
    Error,
    SubmitOrchestrationParams
  >({
    mutationFn: (params) => submitRequirementApproved(params),
    // Errors are surfaced through `mutation.error` — callers decide how to display them.
    onError: (err) => {
      console.error('[useRequirementOrchestration] orchestration failed:', err);
    },
  });

  return {
    submitApproved: mutation.mutate,
    runRef: mutation.data,
    isSubmitting: mutation.isPending,
    error: mutation.error,
  };
}
