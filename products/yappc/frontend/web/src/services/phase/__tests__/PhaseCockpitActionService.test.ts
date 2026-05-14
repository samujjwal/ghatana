import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  describePhaseActionError,
  executePhasePrimaryAction,
  executeGenerateReviewDecision,
  executeRunPostAction,
} from '../PhaseCockpitActionService';
import type { PhaseTransitionPreviewSnapshot } from '../types';

vi.mock('@/lib/api/client', () => ({
  ApiRequestError: class ApiRequestError extends Error {
    public readonly status: number;
    public readonly type?: string;

    constructor(status: number, message: string, type?: string) {
      super(message);
      this.name = 'ApiRequestError';
      this.status = status;
      this.type = type;
    }
  },
  yappcApi: {
    audit: {
      emit: vi.fn(),
    },
    lifecycle: {
      advance: vi.fn(),
    },
    generate: {
      run: vi.fn(),
      diff: vi.fn(),
      review: vi.fn(),
    },
    workflows: {
      start: vi.fn(),
      status: vi.fn(),
    },
    run: {
      rollback: vi.fn(),
      promote: vi.fn(),
    },
  },
}));

import { yappcApi, ApiRequestError } from '@/lib/api/client';

const readyPreview: PhaseTransitionPreviewSnapshot = {
  projectId: 'proj-1',
  currentPhase: 'SHAPE',
  nextPhase: 'VALIDATE',
  canAdvance: true,
  readiness: 92,
  blockers: [],
  requiredArtifacts: ['Requirements packet'],
  completedArtifacts: ['Intent brief'],
  estimatedReadyIn: 'Ready now',
  estimatedReadyInHours: 0,
  predictionConfidence: 0.8,
  checkedAt: '2026-04-21T11:05:00.000Z',
};

const blockedPreview: PhaseTransitionPreviewSnapshot = {
  ...readyPreview,
  canAdvance: false,
  blockers: ['Approval pending'],
};

describe('PhaseCockpitActionService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(yappcApi.audit.emit).mockResolvedValue({ id: 'audit-123' });
  });

  describe('describePhaseActionError', () => {
    it('returns the message of an ApiRequestError', () => {
      const error = new ApiRequestError(400, 'Bad request');
      expect(describePhaseActionError(error)).toBe('Bad request');
    });

    it('returns the message of a standard Error', () => {
      expect(describePhaseActionError(new Error('Something failed'))).toBe('Something failed');
    });

    it('returns a generic fallback for non-Error values', () => {
      expect(describePhaseActionError('unexpected')).toBe('Phase action failed unexpectedly.');
      expect(describePhaseActionError(null)).toBe('Phase action failed unexpectedly.');
      expect(describePhaseActionError(undefined)).toBe('Phase action failed unexpectedly.');
    });
  });

  describe('executePhasePrimaryAction — surface review phases', () => {
    it('emits a shape audit event and returns a surface result with the audit id', async () => {
      const result = await executePhasePrimaryAction({
        phase: 'shape',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(yappcApi.audit.emit).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'phase.shape.builder_review_started',
          userId: 'user-1',
          projectId: 'proj-1',
        }),
      );

      expect(result.kind).toBe('surface');
      expect(result.status).toBe('AUDIT_RECORDED');
      expect(result.auditEventId).toBe('audit-123');
    });

    it('throws when surface review phase has no actorId', async () => {
      await expect(
        executePhasePrimaryAction({
          phase: 'shape',
          projectId: 'proj-1',
          actorId: undefined,
          preview: readyPreview,
        }),
      ).rejects.toThrow('shape review requires an authenticated actor.');
    });

    it('emits an observe audit event for the observe phase', async () => {
      const result = await executePhasePrimaryAction({
        phase: 'observe',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(yappcApi.audit.emit).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'phase.observe.metrics_review_started' }),
      );
      expect(result.kind).toBe('surface');
    });
  });

  describe('executePhasePrimaryAction — validate phase', () => {
    it('throws when the preview canAdvance is false', async () => {
      await expect(
        executePhasePrimaryAction({
          phase: 'validate',
          projectId: 'proj-1',
          actorId: 'user-1',
          preview: blockedPreview,
        }),
      ).rejects.toThrow('not ready to execute');
    });

    it('throws when actorId is missing', async () => {
      await expect(
        executePhasePrimaryAction({
          phase: 'validate',
          projectId: 'proj-1',
          actorId: undefined,
          preview: readyPreview,
        }),
      ).rejects.toThrow('Lifecycle approval requires an authenticated reviewer.');
    });

    it('returns a lifecycle-transition result after successful lifecycle advance', async () => {
      vi.mocked(yappcApi.lifecycle.advance).mockResolvedValue({
        success: true,
        currentPhase: 'VALIDATE',
        errors: null,
      });

      const result = await executePhasePrimaryAction({
        phase: 'validate',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(result.kind).toBe('lifecycle-transition');
      expect(result.status).toBe('APPROVED');
      expect(result.fromPhase).toBe('SHAPE');
      expect(result.toPhase).toBe('VALIDATE');
      expect(result.auditEventId).toBe('audit-123');
    });

    it('returns a BLOCKED status when lifecycle advance fails', async () => {
      vi.mocked(yappcApi.lifecycle.advance).mockResolvedValue({
        success: false,
        currentPhase: null,
        errors: ['Missing approver'],
      });

      const result = await executePhasePrimaryAction({
        phase: 'validate',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(result.kind).toBe('lifecycle-transition');
      expect(result.status).toBe('BLOCKED');
      expect(result.message).toContain('Missing approver');
    });
  });

  describe('executePhasePrimaryAction — generate phase', () => {
    it('returns a generate-review result with runId when generation succeeds', async () => {
      vi.mocked(yappcApi.generate.run).mockResolvedValue({
        runId: 'run-42',
        status: 'PENDING_REVIEW',
        reviewRequired: true,
      });
      vi.mocked(yappcApi.generate.diff).mockResolvedValue({
        status: 'DIFF_READY',
        reviewRequired: true,
      });

      const result = await executePhasePrimaryAction({
        phase: 'generate',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(result.kind).toBe('generate-review');
      expect(result.runId).toBe('run-42');
      expect(result.reviewRequired).toBe(true);
      expect(result.auditEventId).toBe('audit-123');
    });

    it('returns a generate-review result without runId when backend omits run id', async () => {
      vi.mocked(yappcApi.generate.run).mockResolvedValue({
        status: 'ACCEPTED',
        reviewRequired: false,
      });

      const result = await executePhasePrimaryAction({
        phase: 'generate',
        projectId: 'proj-1',
        actorId: 'user-1',
        preview: readyPreview,
      });

      expect(result.kind).toBe('generate-review');
      expect(result.runId).toBeUndefined();
    });

    it('throws when actorId is missing for generate phase', async () => {
      await expect(
        executePhasePrimaryAction({
          phase: 'generate',
          projectId: 'proj-1',
          actorId: undefined,
          preview: readyPreview,
        }),
      ).rejects.toThrow('Generation requires an authenticated actor.');
    });
  });

  describe('executeGenerateReviewDecision', () => {
    it('records a review decision and returns a result with the audit id', async () => {
      vi.mocked(yappcApi.generate.review).mockResolvedValue({
        runId: 'run-99',
        status: 'APPROVED',
        reviewRequired: false,
        message: 'Approved',
      });

      const result = await executeGenerateReviewDecision({
        projectId: 'proj-1',
        runId: 'run-99',
        decision: 'approve',
        actorId: 'user-1',
      });

      expect(result.kind).toBe('generate-review');
      expect(result.runId).toBe('run-99');
      expect(result.status).toBe('APPROVED');
      expect(result.reviewRequired).toBe(false);
      expect(result.auditEventId).toBe('audit-123');
    });

    it('throws when runId is missing', async () => {
      await expect(
        executeGenerateReviewDecision({
          projectId: 'proj-1',
          runId: '',
          decision: 'reject',
          actorId: 'user-1',
        }),
      ).rejects.toThrow('Generation review requires a run id.');
    });

    it('throws when actorId is missing', async () => {
      await expect(
        executeGenerateReviewDecision({
          projectId: 'proj-1',
          runId: 'run-1',
          decision: 'approve',
          actorId: '',
        }),
      ).rejects.toThrow('Generation review requires an authenticated reviewer.');
    });
  });

  describe('executeRunPostAction', () => {
    it('returns a rollback result with the audit id', async () => {
      vi.mocked(yappcApi.run.rollback).mockResolvedValue({ status: 'ROLLBACK_IN_PROGRESS' });

      const result = await executeRunPostAction({
        projectId: 'proj-1',
        runId: 'run-42',
        action: 'rollback',
        targetVersion: 'v1.2.3',
      });

      expect(result.kind).toBe('run-workflow');
      expect(result.status).toBe('ROLLBACK_IN_PROGRESS');
      expect(result.auditEventId).toBe('audit-123');
      expect(result.message).toContain('rollback');
    });

    it('returns a promote result with the audit id', async () => {
      vi.mocked(yappcApi.run.promote).mockResolvedValue({ status: 'PROMOTION_REQUESTED' });

      const result = await executeRunPostAction({
        projectId: 'proj-1',
        runId: 'run-42',
        action: 'promote',
        targetEnvironment: 'production',
      });

      expect(result.kind).toBe('run-workflow');
      expect(result.status).toBe('PROMOTION_REQUESTED');
    });

    it('returns an observe handoff result', async () => {
      const result = await executeRunPostAction({
        projectId: 'proj-1',
        runId: 'run-42',
        action: 'observe',
      });

      expect(result.kind).toBe('navigate');
      expect(result.status).toBe('OBSERVATION_HANDOFF');
    });

    it('throws when projectId is missing', async () => {
      await expect(
        executeRunPostAction({ projectId: '', runId: 'run-1', action: 'rollback' }),
      ).rejects.toThrow('Run post-action requires a project id.');
    });

    it('throws when runId is missing', async () => {
      await expect(
        executeRunPostAction({ projectId: 'proj-1', runId: '', action: 'rollback' }),
      ).rejects.toThrow('Run post-action requires a run id.');
    });
  });
});
