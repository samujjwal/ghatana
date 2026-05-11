import { ApiRequestError, yappcApi } from '@/lib/api/client';
import type { GenerateReviewDecision } from '@/lib/api/client';

import type { MountedPhase, PhaseTransitionPreviewSnapshot } from './types';

export type PhaseActionKind = 'navigate' | 'surface' | 'generate-review' | 'run-workflow' | 'lifecycle-transition';

export interface ExecutePhaseActionParams {
  readonly phase: MountedPhase;
  readonly projectId: string;
  readonly tenantId?: string;
  readonly actorId?: string;
  readonly preview: PhaseTransitionPreviewSnapshot | null;
}

export interface ExecuteGenerateReviewDecisionParams {
  readonly projectId: string;
  readonly runId: string;
  readonly decision: GenerateReviewDecision;
  readonly actorId: string;
  readonly reason?: string;
}

export type RunPostAction = 'rollback' | 'promote' | 'observe';

export interface ExecuteRunPostActionParams {
  readonly projectId: string;
  readonly runId: string;
  readonly action: RunPostAction;
  readonly targetVersion?: string;
  readonly targetEnvironment?: string;
}

export interface PhaseActionResult {
  readonly kind: PhaseActionKind;
  readonly message: string;
  readonly runId?: string;
  readonly status?: string;
  readonly reviewRequired?: boolean;
  readonly fromPhase?: string;
  readonly toPhase?: string;
  readonly auditEventId?: string;
}

const RUN_WORKFLOW_TEMPLATE_ID = 'yappc-run';

interface SurfaceReviewActionConfig {
  readonly auditType: string;
  readonly description: string;
  readonly resultMessage: string;
}

const SURFACE_REVIEW_ACTIONS: Partial<Record<MountedPhase, SurfaceReviewActionConfig>> = {
  shape: {
    auditType: 'phase.shape.builder_review_started',
    description: 'Shape phase primary action opened the canvas and page-builder review workspace.',
    resultMessage: 'Shape review started. The canvas and page-builder workspace is open for persisted component work.',
  },
  observe: {
    auditType: 'phase.observe.metrics_review_started',
    description: 'Observe phase primary action opened preview metrics and runtime diagnostic review.',
    resultMessage: 'Observe review started. Preview metrics and runtime diagnostics are open for inspection.',
  },
  learn: {
    auditType: 'phase.learn.retrospective_started',
    description: 'Learn phase primary action opened the retrospective evidence capture flow.',
    resultMessage: 'Learning capture started. Retrospective evidence is open for review.',
  },
  evolve: {
    auditType: 'phase.evolve.next_cycle_planning_started',
    description: 'Evolve phase primary action opened next-cycle planning from backed lifecycle evidence.',
    resultMessage: 'Next-cycle planning started. Roadmap and backlog evidence is open for review.',
  },
};

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
  actorId,
  preview,
}: ExecutePhaseActionParams): Promise<PhaseActionResult> {
  const surfaceReviewAction = SURFACE_REVIEW_ACTIONS[phase];
  if (surfaceReviewAction) {
    if (!actorId) {
      throw new Error(`${phase} review requires an authenticated actor.`);
    }

    const auditEvent = await yappcApi.audit.emit({
      type: surfaceReviewAction.auditType,
      userId: actorId,
      projectId,
      flowStage: phase,
      phase: phase.toUpperCase(),
      description: surfaceReviewAction.description,
      metadata: {
        currentPhase: preview?.currentPhase ?? null,
        nextPhase: preview?.nextPhase ?? null,
        readiness: preview?.readiness ?? null,
        canAdvance: preview?.canAdvance ?? null,
        blockerCount: preview?.blockers.length ?? 0,
      },
    });

    return {
      kind: 'surface',
      status: 'AUDIT_RECORDED',
      auditEventId: auditEvent.id,
      message: `${surfaceReviewAction.resultMessage} Audit event ${auditEvent.id} recorded.`,
    };
  }

  if (phase === 'validate') {
    requireReady(preview);

    if (!actorId) {
      throw new Error('Lifecycle approval requires an authenticated reviewer.');
    }
    if (!preview?.currentPhase || !preview.nextPhase) {
      throw new Error('Lifecycle approval requires a current phase and next phase from the readiness preview.');
    }

    // Record audit event before lifecycle transition
    const auditEvent = await yappcApi.audit.emit({
      type: 'phase.validate.approval_requested',
      userId: actorId,
      projectId,
      flowStage: phase,
      phase: phase.toUpperCase(),
      description: 'Lifecycle transition approval requested from Validate phase.',
      metadata: {
        currentPhase: preview.currentPhase,
        nextPhase: preview.nextPhase,
        readiness: preview.readiness,
        canAdvance: preview.canAdvance,
        blockerCount: preview.blockers.length,
      },
    });

    const transition = await yappcApi.lifecycle.advance({
      projectId,
      fromPhase: preview.currentPhase,
      toPhase: preview.nextPhase,
      userId: actorId,
    });

    return {
      kind: 'lifecycle-transition',
      status: transition.success ? 'APPROVED' : 'BLOCKED',
      fromPhase: preview.currentPhase,
      toPhase: transition.currentPhase ?? preview.nextPhase,
      auditEventId: auditEvent.id,
      message: transition.success
        ? `Lifecycle transition approved from ${preview.currentPhase} to ${transition.currentPhase ?? preview.nextPhase}. Audit event ${auditEvent.id} recorded.`
        : `Lifecycle transition could not be approved: ${(transition.errors ?? ['Unknown lifecycle error']).join(', ')}`,
    };
  }

  if (phase === 'generate') {
    requireReady(preview);

    if (!actorId) {
      throw new Error('Generation requires an authenticated actor.');
    }

    // Record audit event before generation
    const auditEvent = await yappcApi.audit.emit({
      type: 'phase.generate.requested',
      userId: actorId,
      projectId,
      flowStage: phase,
      phase: phase.toUpperCase(),
      description: 'Generation run requested from Generate phase.',
      metadata: {
        currentPhase: preview?.currentPhase ?? null,
        nextPhase: preview?.nextPhase ?? null,
        readiness: preview?.readiness ?? null,
        canAdvance: preview?.canAdvance ?? null,
        blockerCount: preview?.blockers.length ?? 0,
      },
    });

    const generation = await yappcApi.generate.run({
      projectId,
      phase: 'GENERATE',
    });
    const runId = getGeneratedRunId(generation);

    if (!runId) {
      return {
        kind: 'generate-review',
        auditEventId: auditEvent.id,
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
      auditEventId: auditEvent.id,
      status: diffReview?.status ?? generation?.status,
      reviewRequired: diffReview?.reviewRequired ?? generation?.reviewRequired ?? true,
      message: `Generation run ${runId} is ready for diff review. Audit event ${auditEvent.id} recorded.`,
    };
  }

  if (phase === 'run') {
    requireReady(preview);

    if (!tenantId) {
      throw new Error('Run execution requires an authenticated tenant context.');
    }
    if (!actorId) {
      throw new Error('Run execution requires an authenticated actor.');
    }

    // Record audit event before run execution
    const auditEvent = await yappcApi.audit.emit({
      type: 'phase.run.requested',
      userId: actorId,
      projectId,
      flowStage: phase,
      phase: phase.toUpperCase(),
      description: 'Run workflow execution requested from Run phase.',
      metadata: {
        currentPhase: preview?.currentPhase ?? null,
        nextPhase: preview?.nextPhase ?? null,
        readiness: preview?.readiness ?? null,
        canAdvance: preview?.canAdvance ?? null,
        blockerCount: preview?.blockers.length ?? 0,
        workflowTemplate: RUN_WORKFLOW_TEMPLATE_ID,
      },
    });

    const started = await yappcApi.workflows.start(RUN_WORKFLOW_TEMPLATE_ID, tenantId);
    const runId = started.runId;

    if (!runId) {
      return {
        kind: 'run-workflow',
        auditEventId: auditEvent.id,
        status: started.status,
        message: 'Run workflow was accepted, but the backend did not return a run id for status tracking.',
      };
    }

    const status = await yappcApi.workflows.status(runId);

    return {
      kind: 'run-workflow',
      runId,
      auditEventId: auditEvent.id,
      status: status.status ?? started.status,
      message: `Run workflow ${runId} started and is now ${status.status ?? started.status ?? 'pending'}. Audit event ${auditEvent.id} recorded.`,
    };
  }

  return {
    kind: 'surface',
    message: 'Review the phase-native surface before continuing.',
  };
}

export async function executeGenerateReviewDecision({
  projectId,
  runId,
  decision,
  actorId,
  reason,
}: ExecuteGenerateReviewDecisionParams): Promise<PhaseActionResult> {
  if (!runId) {
    throw new Error('Generation review requires a run id.');
  }
  if (!projectId) {
    throw new Error('Generation review requires a project id.');
  }
  if (!actorId) {
    throw new Error('Generation review requires an authenticated reviewer.');
  }

  // Record audit event before review decision
  const auditEvent = await yappcApi.audit.emit({
    type: `phase.generate.review_${decision}`,
    userId: actorId,
    projectId,
    flowStage: 'generate',
    phase: 'GENERATE',
    description: `Generation review ${decision} decision recorded.`,
    metadata: {
      runId,
      decision,
      reason: reason ?? null,
    },
  });

  const response = await yappcApi.generate.review(runId, decision, {
    projectId,
    actorId,
    reason,
  });
  const status = response.status ?? decision.toUpperCase();
  const message = response.message ?? `Generation run ${runId} ${decision} decision recorded.`;

  return {
    kind: 'generate-review',
    runId: response.runId ?? runId,
    auditEventId: auditEvent.id,
    status,
    reviewRequired: response.reviewRequired ?? false,
    message: `${message} Audit event ${auditEvent.id} recorded.`,
  };
}

export async function executeRunPostAction({
  projectId,
  runId,
  action,
  targetVersion = 'previous-stable',
  targetEnvironment = 'staging',
}: ExecuteRunPostActionParams): Promise<PhaseActionResult> {
  if (!projectId) {
    throw new Error('Run post-action requires a project id.');
  }
  if (!runId) {
    throw new Error('Run post-action requires a run id.');
  }

  // Record audit event before run post-action
  const auditEvent = await yappcApi.audit.emit({
    type: `phase.run.${action}`,
    userId: 'system', // Run post-actions are system-initiated based on workflow state
    projectId,
    flowStage: 'run',
    phase: 'RUN',
    description: `Run ${action} action requested.`,
    metadata: {
      runId,
      action,
      targetVersion: action === 'rollback' ? targetVersion : null,
      targetEnvironment: action === 'promote' ? targetEnvironment : null,
    },
  });

  if (action === 'rollback') {
    const result = await yappcApi.run.rollback({
      deploymentId: runId,
      targetVersion,
    });
    return {
      kind: 'run-workflow',
      runId,
      auditEventId: auditEvent.id,
      status: result.status ?? 'ROLLBACK_REQUESTED',
      message: `Run ${runId} rollback requested to ${targetVersion}. Audit event ${auditEvent.id} recorded.`,
    };
  }

  if (action === 'promote') {
    const result = await yappcApi.run.promote({
      deploymentId: runId,
      targetEnvironment,
    });
    return {
      kind: 'run-workflow',
      runId,
      auditEventId: auditEvent.id,
      status: result.status ?? 'PROMOTION_REQUESTED',
      message: `Run ${runId} promotion requested for ${targetEnvironment}. Audit event ${auditEvent.id} recorded.`,
    };
  }

  return {
    kind: 'navigate',
    runId,
    auditEventId: auditEvent.id,
    status: 'OBSERVATION_HANDOFF',
    message: `Run ${runId} is ready for observation handoff. Audit event ${auditEvent.id} recorded.`,
  };
}
