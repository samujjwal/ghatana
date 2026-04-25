/**
 * Unit tests for AiOperationsService.
 *
 * Verifies:
 * - Success path: API response parsed through Zod and returned.
 * - Boundary path: HTTP 404/405/501 → UnsupportedRuntimeBoundaryError (not re-thrown network errors).
 * - Re-throw: generic network errors propagate to TanStack Query as-is.
 *
 * Every test calls a real method on the real service class (via `aiOperationsService` singleton).
 * No object-literal assertions — all assertions exercise production code paths.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { UnsupportedRuntimeBoundaryError } from '../../lib/runtime-boundaries';

// ── Mock setup ────────────────────────────────────────────────────────────────

const mockGet = vi.hoisted(() => vi.fn());
const mockPost = vi.hoisted(() => vi.fn());

vi.mock('../../lib/api/client', () => ({
  apiClient: {
    get: mockGet,
    post: mockPost,
  },
}));

vi.mock('../../lib/auth/session', () => ({
  default: {
    requireTenantId: () => 'test-tenant-id',
  },
}));

// Import AFTER mocks are established.
import { aiOperationsService } from '../../api/ai-operations.service';

// ── Fixture helpers ───────────────────────────────────────────────────────────

function makeBoundaryError(status: number): object {
  return { status, message: `HTTP ${status}` };
}

function makeNetworkError(): Error {
  return new Error('Network failure');
}

const now = new Date().toISOString();

function makeSuggestionEnvelope() {
  return {
    tenantId: 'test-tenant-id',
    surface: 'alerts',
    suggestions: [
      {
        id: 'sug-1',
        surface: 'alerts',
        title: 'Increase alert threshold',
        description: 'ML model recommends raising threshold to reduce noise.',
        confidence: 0.87,
        confidenceBand: 'high',
        canAutoApply: false,
        impact: {
          severity: 'medium',
          affectedEntities: ['alert-42'],
          description: 'Reduces alert volume by ~30%.',
        },
        contextIds: ['alert-42'],
        generatedAt: now,
        source: 'ml-scoring-v2',
      },
    ],
    count: 1,
    generatedAt: now,
    modelVersion: 'v2.1.0',
  };
}

function makeApplyResponse() {
  return {
    suggestionId: 'sug-1',
    applied: true,
    outcome: 'success',
    message: 'Suggestion applied successfully.',
    appliedAt: now,
    auditEventId: 'audit-999',
  };
}

function makeCorrelationsEnvelope() {
  return {
    tenantId: 'test-tenant-id',
    correlations: [
      {
        id: 'corr-1',
        primarySurface: 'alerts',
        primaryEntityId: 'alert-99',
        correlatedSurface: 'workflows',
        correlatedEntityId: 'wf-77',
        correlationType: 'causal',
        confidence: 0.75,
        confidenceBand: 'medium',
        explanation: 'Workflow failure caused the alert to fire.',
        suggestedAction: 'Review workflow wf-77.',
        detectedAt: now,
      },
    ],
    count: 1,
    generatedAt: now,
  };
}

function makeWorkflowAdvisory(workflowId: string = 'wf-1') {
  return {
    workflowId,
    tenantId: 'test-tenant-id',
    advisories: [
      {
        id: 'adv-1',
        type: 'performance',
        title: 'Parallel execution recommended',
        description: 'Steps 3-5 are sequential but can run in parallel.',
        confidence: 0.91,
        confidenceBand: 'high',
        suggestedAction: 'Enable parallel step execution.',
        priority: 'high',
      },
    ],
    generatedAt: now,
    modelVersion: 'v1.0.0',
  };
}

function makeQualityAdvisory(collectionId: string = 'events') {
  return {
    collectionId,
    tenantId: 'test-tenant-id',
    overallScore: 0.82,
    scoreBand: 'high',
    advisories: [
      {
        id: 'qa-1',
        fieldName: 'email',
        type: 'completeness',
        title: 'Missing email values',
        description: '3.2% of records missing email.',
        affectedCount: 320,
        confidence: 0.95,
        suggestedAction: 'Backfill from CRM.',
      },
    ],
    generatedAt: now,
    modelVersion: 'v1.0.0',
  };
}

function makeFabricAdvisory(collectionId: string = 'logs') {
  return {
    collectionId,
    tenantId: 'test-tenant-id',
    currentTier: 'hot',
    recommendedTier: 'warm',
    advisories: [
      {
        id: 'fa-1',
        type: 'tier-migration',
        title: 'Move to warm tier',
        description: 'Access frequency dropped below hot-tier threshold.',
        estimatedCostImpact: 'positive',
        confidence: 0.88,
        confidenceBand: 'high',
        suggestedAction: 'Migrate to warm tier within 7 days.',
      },
    ],
    generatedAt: now,
    modelVersion: 'v1.0.0',
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('AiOperationsService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── getSuggestions ──────────────────────────────────────────────────────────

  describe('getSuggestions', () => {
    it('returns parsed suggestions on success', async () => {
      const envelope = makeSuggestionEnvelope();
      mockPost.mockResolvedValue(envelope);

      const result = await aiOperationsService.getSuggestions({ surface: 'alerts' });

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('sug-1');
      expect(result[0].surface).toBe('alerts');
      expect(mockPost).toHaveBeenCalledWith(
        '/ai/suggestions',
        expect.objectContaining({ surface: 'alerts' }),
        expect.objectContaining({ headers: { 'X-Tenant-ID': 'test-tenant-id' } }),
      );
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockPost.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.getSuggestions({ surface: 'workflows' }),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 405', async () => {
      mockPost.mockRejectedValue(makeBoundaryError(405));

      await expect(
        aiOperationsService.getSuggestions({ surface: 'quality' }),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 501', async () => {
      mockPost.mockRejectedValue(makeBoundaryError(501));

      await expect(
        aiOperationsService.getSuggestions({ surface: 'fabric' }),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('re-throws non-boundary network errors', async () => {
      const networkError = makeNetworkError();
      mockPost.mockRejectedValue(networkError);

      await expect(
        aiOperationsService.getSuggestions({ surface: 'alerts' }),
      ).rejects.toThrow('Network failure');
    });

    it('passes contextIds and limit to the API', async () => {
      mockPost.mockResolvedValue(makeSuggestionEnvelope());

      await aiOperationsService.getSuggestions({
        surface: 'alerts',
        contextIds: ['alert-1', 'alert-2'],
        limit: 5,
      });

      expect(mockPost).toHaveBeenCalledWith(
        '/ai/suggestions',
        { surface: 'alerts', contextIds: ['alert-1', 'alert-2'], limit: 5 },
        expect.any(Object),
      );
    });
  });

  // ── applySuggestion ─────────────────────────────────────────────────────────

  describe('applySuggestion', () => {
    it('returns parsed apply response on success', async () => {
      mockPost.mockResolvedValue(makeApplyResponse());

      const result = await aiOperationsService.applySuggestion('sug-1');

      expect(result.suggestionId).toBe('sug-1');
      expect(result.applied).toBe(true);
      expect(result.outcome).toBe('success');
      expect(mockPost).toHaveBeenCalledWith(
        '/ai/suggestions/sug-1/apply',
        { context: {} },
        expect.objectContaining({ headers: { 'X-Tenant-ID': 'test-tenant-id' } }),
      );
    });

    it('passes context when provided', async () => {
      mockPost.mockResolvedValue(makeApplyResponse());

      await aiOperationsService.applySuggestion('sug-2', { dryRun: true });

      expect(mockPost).toHaveBeenCalledWith(
        '/ai/suggestions/sug-2/apply',
        { context: { dryRun: true } },
        expect.any(Object),
      );
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockPost.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.applySuggestion('sug-1'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 501', async () => {
      mockPost.mockRejectedValue(makeBoundaryError(501));

      await expect(
        aiOperationsService.applySuggestion('sug-1'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });
  });

  // ── getCrossCorrelations ────────────────────────────────────────────────────

  describe('getCrossCorrelations', () => {
    it('returns parsed correlations on success', async () => {
      mockGet.mockResolvedValue(makeCorrelationsEnvelope());

      const result = await aiOperationsService.getCrossCorrelations({ primarySurface: 'alerts' });

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('corr-1');
      expect(result[0].correlationType).toBe('causal');
    });

    it('calls the correct endpoint with query params', async () => {
      mockGet.mockResolvedValue(makeCorrelationsEnvelope());

      await aiOperationsService.getCrossCorrelations({
        primarySurface: 'alerts',
        primaryEntityIds: ['alert-99'],
        limit: 10,
      });

      expect(mockGet).toHaveBeenCalledWith(
        '/ai/correlations',
        expect.objectContaining({
          params: expect.objectContaining({
            primarySurface: 'alerts',
            primaryEntityIds: 'alert-99',
            limit: 10,
          }),
        }),
      );
    });

    it('defaults to empty request when no args provided', async () => {
      mockGet.mockResolvedValue({ ...makeCorrelationsEnvelope(), correlations: [], count: 0 });

      const result = await aiOperationsService.getCrossCorrelations();

      expect(result).toEqual([]);
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.getCrossCorrelations(),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 405', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(405));

      await expect(
        aiOperationsService.getCrossCorrelations({ primarySurface: 'workflows' }),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('re-throws non-boundary errors', async () => {
      mockGet.mockRejectedValue(makeNetworkError());

      await expect(
        aiOperationsService.getCrossCorrelations(),
      ).rejects.toThrow('Network failure');
    });
  });

  // ── getWorkflowAdvisories ───────────────────────────────────────────────────

  describe('getWorkflowAdvisories', () => {
    it('returns parsed advisory on success', async () => {
      mockGet.mockResolvedValue(makeWorkflowAdvisory('wf-42'));

      const result = await aiOperationsService.getWorkflowAdvisories('wf-42');

      expect(result.workflowId).toBe('wf-42');
      expect(result.advisories).toHaveLength(1);
      expect(result.advisories[0].type).toBe('performance');
      expect(result.advisories[0].priority).toBe('high');
      expect(mockGet).toHaveBeenCalledWith(
        '/ai/advisories/workflows/wf-42',
        expect.objectContaining({ headers: { 'X-Tenant-ID': 'test-tenant-id' } }),
      );
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.getWorkflowAdvisories('wf-1'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 501', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(501));

      await expect(
        aiOperationsService.getWorkflowAdvisories('wf-1'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('re-throws non-boundary errors', async () => {
      mockGet.mockRejectedValue(makeNetworkError());

      await expect(
        aiOperationsService.getWorkflowAdvisories('wf-1'),
      ).rejects.toThrow('Network failure');
    });
  });

  // ── getQualityAdvisories ────────────────────────────────────────────────────

  describe('getQualityAdvisories', () => {
    it('returns parsed quality advisory on success', async () => {
      mockGet.mockResolvedValue(makeQualityAdvisory('events'));

      const result = await aiOperationsService.getQualityAdvisories('events');

      expect(result.collectionId).toBe('events');
      expect(result.overallScore).toBe(0.82);
      expect(result.advisories).toHaveLength(1);
      expect(result.advisories[0].type).toBe('completeness');
      expect(mockGet).toHaveBeenCalledWith(
        '/ai/advisories/quality/events',
        expect.objectContaining({ headers: { 'X-Tenant-ID': 'test-tenant-id' } }),
      );
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.getQualityAdvisories('events'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 405', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(405));

      await expect(
        aiOperationsService.getQualityAdvisories('events'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('re-throws non-boundary errors', async () => {
      mockGet.mockRejectedValue(makeNetworkError());

      await expect(
        aiOperationsService.getQualityAdvisories('events'),
      ).rejects.toThrow('Network failure');
    });
  });

  // ── getFabricAdvisories ─────────────────────────────────────────────────────

  describe('getFabricAdvisories', () => {
    it('returns parsed fabric advisory on success', async () => {
      mockGet.mockResolvedValue(makeFabricAdvisory('logs'));

      const result = await aiOperationsService.getFabricAdvisories('logs');

      expect(result.collectionId).toBe('logs');
      expect(result.currentTier).toBe('hot');
      expect(result.recommendedTier).toBe('warm');
      expect(result.advisories).toHaveLength(1);
      expect(result.advisories[0].type).toBe('tier-migration');
      expect(result.advisories[0].estimatedCostImpact).toBe('positive');
      expect(mockGet).toHaveBeenCalledWith(
        '/ai/advisories/fabric/logs',
        expect.objectContaining({ headers: { 'X-Tenant-ID': 'test-tenant-id' } }),
      );
    });

    it('raises UnsupportedRuntimeBoundaryError on 404', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(404));

      await expect(
        aiOperationsService.getFabricAdvisories('logs'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('raises UnsupportedRuntimeBoundaryError on 501', async () => {
      mockGet.mockRejectedValue(makeBoundaryError(501));

      await expect(
        aiOperationsService.getFabricAdvisories('logs'),
      ).rejects.toBeInstanceOf(UnsupportedRuntimeBoundaryError);
    });

    it('re-throws non-boundary errors', async () => {
      mockGet.mockRejectedValue(makeNetworkError());

      await expect(
        aiOperationsService.getFabricAdvisories('logs'),
      ).rejects.toThrow('Network failure');
    });
  });
});
