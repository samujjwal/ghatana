import { ApiRequestError, yappcApi } from '@/lib/api/client';

import type { MountedPhase, PhaseTransitionPreviewSnapshot } from './types';

export type PhaseActionKind = 'navigate' | 'surface' | 'generate-review' | 'run-workflow';

export interface ExecutePhaseActionParams {
  readonly phase: MountedPhase;
  readonly projectId: string;
  readonly tenantId?: string;
  readonly preview: PhaseTransitionPreviewSnapshot | null;
}

export interface PhaseActionResult {
  readonly kind: PhaseActionKind;
  readonly message: string;
  readonly runId?: string;
  readonly status?: string;
  readonly reviewRequired?: boolean;
}

const RUN_WORKFLOW_TEMPLATE_ID = 'yappc-run';

function getGeneratedRunId(response: { readonly runId?: string; readonly executionId?: string } | undefined): string | undefined {
  return response?.runId ?? response?.executionId;
}

function requireReady(preview: PhaseTransitionPreviewSnapshot | null): void {
  if (preview && !preview.canAdvance) {
    const details = preview.blockers.length > 0
      ? ` Blockers: ${preview.blockers.join(', ')}.`
      : '';
    throw new Error(`This phase is not ready to execute yet.${details}`);
  }
}

function getActionErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Phase action failed unexpectedly.';
}

export function describePhaseActionError(error: unknown): string {
  return getActionErrorMessage(error);
}

export async function executePhasePrimaryAction({
  phase,
  projectId,
  tenantId,
  preview,
}: ExecutePhaseActionParams): Promise<PhaseActionResult> {
  if (phase === 'generate') {
    requireReady(preview);

    const generation = await yappcApi.generate.run({
      projectId,
      phase: 'GENERATE',
    });
    const runId = getGeneratedRunId(generation);

    if (!runId) {
      return {
        kind: 'generate-review',
        message: 'Generation request was accepted, but the backend did not return a run id for diff review.',
        status: generation?.status,
        reviewRequired: generation?.reviewRequired,
      };
    }

    const diffReview = await yappcApi.generate.diff({
      runId,
      diff: 'initial-review',
    });

    return {
      kind: 'generate-review',
      runId,
      status: diffReview?.status ?? generation?.status,
      reviewRequired: diffReview?.reviewRequired ?? generation?.reviewRequired ?? true,
      message: `Generation run ${runId} is ready for diff review.`,
    };
  }

  if (phase === 'run') {
    requireReady(preview);

    if (!tenantId) {
      throw new Error('Run execution requires an authenticated tenant context.');
    }

    const started = await yappcApi.workflows.start(RUN_WORKFLOW_TEMPLATE_ID, tenantId);
    const runId = started.runId;

    if (!runId) {
      return {
        kind: 'run-workflow',
        status: started.status,
        message: 'Run workflow was accepted, but the backend did not return a run id for status tracking.',
      };
    }

    const status = await yappcApi.workflows.status(runId);

    return {
      kind: 'run-workflow',
      runId,
      status: status.status ?? started.status,
      message: `Run workflow ${runId} started and is now ${status.status ?? started.status ?? 'pending'}.`,
    };
  }

  return {
    kind: 'surface',
    message: 'Review the phase-native surface before continuing.',
  };
}
