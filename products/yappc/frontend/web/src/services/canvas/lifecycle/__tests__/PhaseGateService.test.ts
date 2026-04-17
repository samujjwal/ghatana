/**
 * PhaseGateService — Unit Tests
 *
 * Tests for InMemoryProjectPhaseRepository, PhaseGateService, and the
 * usePhaseGates React hook.
 *
 * @doc.type test
 * @doc.purpose Verify phase gate service operations and transition logic
 * @doc.layer product
 * @doc.pattern Service Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

import {
  InMemoryProjectPhaseRepository,
  PhaseGateService,
  usePhaseGates,
  type TransitionRequest,
} from '../PhaseGateService';
import { LifecyclePhase } from '@/types/lifecycle';
import { PHASE_GATES } from '@/shared/types/phase-gates';
import {
  type LifecycleArtifactKind,
  getArtifactsForPhase,
} from '@/shared/types/lifecycle-artifacts';
import type { ArtifactSummary } from '../LifecycleArtifactService';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const PROJECT_ID = 'proj-test-001';

/** Build a mock LifecycleArtifactService that returns the given summaries. */
function makeArtifactSvc(summaries: ArtifactSummary[]): {
  listArtifacts: (filter: { projectId?: string }) => Promise<ArtifactSummary[]>;
} {
  return {
    listArtifacts: vi.fn().mockResolvedValue(summaries),
  };
}

/** Create ArtifactSummary objects for all required artifacts in a given phase. */
function makeCompleteArtifactsForPhase(
  phase: LifecyclePhase,
  status: ArtifactSummary['status'] = 'complete'
): ArtifactSummary[] {
  return getArtifactsForPhase(phase).map((kind: LifecycleArtifactKind) => ({
    id: `art-${kind}`,
    kind,
    title: kind,
    status,
    phase,
    updatedAt: '2025-01-01T00:00:00Z',
  }));
}

// ---------------------------------------------------------------------------
// InMemoryProjectPhaseRepository
// ---------------------------------------------------------------------------

describe('InMemoryProjectPhaseRepository', () => {
  let repo: InMemoryProjectPhaseRepository;

  beforeEach(() => {
    repo = new InMemoryProjectPhaseRepository();
  });

  it('returns null for an unknown projectId', async () => {
    const result = await repo.getState('does-not-exist');
    expect(result).toBeNull();
  });

  it('round-trips state through saveState / getState', async () => {
    const state = {
      projectId: PROJECT_ID,
      currentPhase: LifecyclePhase.INTENT,
      phaseHistory: [],
      gateStatuses: {},
      lastUpdated: '2025-01-01T00:00:00Z',
    };
    await repo.saveState(state);
    const stored = await repo.getState(PROJECT_ID);

    expect(stored).toBeDefined();
    expect(stored?.projectId).toBe(PROJECT_ID);
    expect(stored?.currentPhase).toBe(LifecyclePhase.INTENT);
  });

  it('getState returns a copy — mutating the result does not affect storage', async () => {
    const state = {
      projectId: PROJECT_ID,
      currentPhase: LifecyclePhase.INTENT,
      phaseHistory: [],
      gateStatuses: {},
      lastUpdated: '2025-01-01T00:00:00Z',
    };
    await repo.saveState(state);

    const copy = await repo.getState(PROJECT_ID);
    copy!.currentPhase = LifecyclePhase.SHAPE;

    const unchanged = await repo.getState(PROJECT_ID);
    expect(unchanged?.currentPhase).toBe(LifecyclePhase.INTENT);
  });

  it('updatePhase updates the current phase and appends a history record', async () => {
    const state = {
      projectId: PROJECT_ID,
      currentPhase: LifecyclePhase.INTENT,
      phaseHistory: [],
      gateStatuses: {},
      lastUpdated: '2025-01-01T00:00:00Z',
    };
    await repo.saveState(state);

    await repo.updatePhase(PROJECT_ID, LifecyclePhase.SHAPE, {
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      bypassed: false,
      userId: 'user-1',
      timestamp: new Date().toISOString(),
    });

    const updated = await repo.getState(PROJECT_ID);
    expect(updated?.currentPhase).toBe(LifecyclePhase.SHAPE);
    expect(updated?.phaseHistory).toHaveLength(1);
    expect(updated?.phaseHistory[0]?.fromPhase).toBe(LifecyclePhase.INTENT);
  });

  it('updatePhase throws when the project is unknown', async () => {
    await expect(
      repo.updatePhase('unknown', LifecyclePhase.SHAPE, {
        fromPhase: LifecyclePhase.INTENT,
        toPhase: LifecyclePhase.SHAPE,
        bypassed: false,
        userId: 'user-1',
        timestamp: new Date().toISOString(),
      })
    ).rejects.toMatchObject({ message: 'Project state not found: unknown' });
  });
});

// ---------------------------------------------------------------------------
// PhaseGateService — Initialization & State Management
// ---------------------------------------------------------------------------

describe('PhaseGateService - initialization', () => {
  let repo: InMemoryProjectPhaseRepository;
  let service: PhaseGateService;

  beforeEach(() => {
    repo = new InMemoryProjectPhaseRepository();
    service = new PhaseGateService(repo);
  });

  it('initializeProject creates state starting at INTENT by default', async () => {
    const state = await service.initializeProject(PROJECT_ID);

    expect(state.projectId).toBe(PROJECT_ID);
    expect(state.currentPhase).toBe(LifecyclePhase.INTENT);
    expect(state.phaseHistory).toHaveLength(0);
  });

  it('initializeProject respects a custom initial phase', async () => {
    const state = await service.initializeProject(
      PROJECT_ID,
      LifecyclePhase.SHAPE
    );
    expect(state.currentPhase).toBe(LifecyclePhase.SHAPE);
  });

  it('initializeProject is idempotent — repeated calls return the stored state', async () => {
    const first = await service.initializeProject(PROJECT_ID);
    const second = await service.initializeProject(PROJECT_ID);

    expect(second.projectId).toBe(first.projectId);
    expect(second.currentPhase).toBe(first.currentPhase);
  });

  it('getCurrentPhase returns INTENT for an uninitialized project', async () => {
    const phase = await service.getCurrentPhase('unknown-project');
    expect(phase).toBe(LifecyclePhase.INTENT);
  });

  it('getCurrentPhase returns stored phase', async () => {
    await service.initializeProject(PROJECT_ID, LifecyclePhase.GENERATE);
    const phase = await service.getCurrentPhase(PROJECT_ID);
    expect(phase).toBe(LifecyclePhase.GENERATE);
  });

  it('getProjectState returns null for unknown project', async () => {
    const state = await service.getProjectState('nonexistent');
    expect(state).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// PhaseGateService — Gate Operations
// ---------------------------------------------------------------------------

describe('PhaseGateService - gate operations', () => {
  let repo: InMemoryProjectPhaseRepository;
  let service: PhaseGateService;

  beforeEach(async () => {
    repo = new InMemoryProjectPhaseRepository();
    service = new PhaseGateService(repo);
    await service.initializeProject(PROJECT_ID);
  });

  it('getAllGates returns all 6 phase gates', () => {
    expect(service.getAllGates()).toHaveLength(6);
    expect(service.getAllGates()).toBe(PHASE_GATES);
  });

  it('getGateById returns the gate for a valid ID', () => {
    const gate = service.getGateById('gate:intent-to-shape');
    expect(gate).toBeDefined();
    expect(gate?.fromPhase).toBe(LifecyclePhase.INTENT);
    expect(gate?.toPhase).toBe(LifecyclePhase.SHAPE);
  });

  it('getGateById returns undefined for an unknown ID', () => {
    expect(service.getGateById('gate:nonexistent')).toBeUndefined();
  });

  it('getGateForTransition finds the correct gate for adjacent phases', () => {
    const gate = service.getGateForTransition(
      LifecyclePhase.SHAPE,
      LifecyclePhase.VALIDATE
    );
    expect(gate?.id).toBe('gate:shape-to-validate');
  });

  it('getGateForTransition returns undefined for non-adjacent phases', () => {
    expect(
      service.getGateForTransition(
        LifecyclePhase.INTENT,
        LifecyclePhase.VALIDATE
      )
    ).toBeUndefined();
  });

  it('checkGateStatus throws for an unknown gate ID', async () => {
    await expect(
      service.checkGateStatus(PROJECT_ID, 'gate:bad-id')
    ).rejects.toMatchObject({ message: 'Gate not found: gate:bad-id' });
  });

  it('checkGateStatus returns blocked status when no artifacts exist', async () => {
    const status = await service.checkGateStatus(
      PROJECT_ID,
      'gate:intent-to-shape'
    );
    expect(status.status).toBe('blocked');
    expect(status.blockedReason).toBeTruthy();
  });

  it('checkAllGateStatuses returns an entry for every gate', async () => {
    const statuses = await service.checkAllGateStatuses(PROJECT_ID);
    for (const gate of PHASE_GATES) {
      expect(statuses[gate.id]).toBeDefined();
    }
  });
});

// ---------------------------------------------------------------------------
// PhaseGateService — Transitions (without artifact service → always blocked)
// ---------------------------------------------------------------------------

describe('PhaseGateService - transitions (no artifact service)', () => {
  let repo: InMemoryProjectPhaseRepository;
  let service: PhaseGateService;

  beforeEach(async () => {
    repo = new InMemoryProjectPhaseRepository();
    service = new PhaseGateService(repo);
    await service.initializeProject(PROJECT_ID);
  });

  it('canTransition returns false when no artifacts provided', async () => {
    const result = await service.canTransition(
      PROJECT_ID,
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE
    );
    expect(result.canTransition).toBe(false);
    expect(result.gateStatus?.status).toBe('blocked');
  });

  it('transition fails when gate is blocked and bypass is not requested', async () => {
    const request: TransitionRequest = {
      projectId: PROJECT_ID,
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      userId: 'user-1',
    };
    const result = await service.transition(request);

    expect(result.success).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
    expect(await service.getCurrentPhase(PROJECT_ID)).toBe(
      LifecyclePhase.INTENT
    );
  });

  it('transition succeeds with bypass when gate.canBypass is true', async () => {
    const request: TransitionRequest = {
      projectId: PROJECT_ID,
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      userId: 'user-1',
      bypass: true,
      bypassReason: 'emergency release',
    };
    const result = await service.transition(request);

    expect(result.success).toBe(true);
    expect(result.newPhase).toBe(LifecyclePhase.SHAPE);
    expect(result.warnings.some((w) => w.includes('bypassed'))).toBe(true);
    expect(await service.getCurrentPhase(PROJECT_ID)).toBe(
      LifecyclePhase.SHAPE
    );
  });

  it('bypassed transition records bypassed=true in history', async () => {
    const request: TransitionRequest = {
      projectId: PROJECT_ID,
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      userId: 'user-1',
      bypass: true,
      bypassReason: 'speed',
    };
    await service.transition(request);

    const state = await repo.getState(PROJECT_ID);
    expect(state?.phaseHistory[0]?.bypassed).toBe(true);
    expect(state?.phaseHistory[0]?.bypassReason).toBe('speed');
  });
});

// ---------------------------------------------------------------------------
// PhaseGateService — Transitions (with complete artifact service → gate passes)
// ---------------------------------------------------------------------------

describe('PhaseGateService - transitions (with artifact service)', () => {
  let repo: InMemoryProjectPhaseRepository;
  let service: PhaseGateService;

  beforeEach(async () => {
    const artifactSvc = makeArtifactSvc(
      makeCompleteArtifactsForPhase(LifecyclePhase.INTENT)
    );
    repo = new InMemoryProjectPhaseRepository();
    // Cast is safe — we only rely on listArtifacts in buildGateContext
    service = new PhaseGateService(repo, artifactSvc as never);
    await service.initializeProject(PROJECT_ID);
  });

  it('canTransition returns true when all required artifacts are complete', async () => {
    const result = await service.canTransition(
      PROJECT_ID,
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE
    );
    expect(result.canTransition).toBe(true);
    expect(result.gateStatus?.status).toBe('passed');
  });

  it('transition succeeds when gate passes', async () => {
    const request: TransitionRequest = {
      projectId: PROJECT_ID,
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      userId: 'user-2',
    };
    const result = await service.transition(request);

    expect(result.success).toBe(true);
    expect(result.newPhase).toBe(LifecyclePhase.SHAPE);
    expect(result.errors).toHaveLength(0);
    expect(await service.getCurrentPhase(PROJECT_ID)).toBe(
      LifecyclePhase.SHAPE
    );
  });

  it('successful transition records bypassed=false in history', async () => {
    const request: TransitionRequest = {
      projectId: PROJECT_ID,
      fromPhase: LifecyclePhase.INTENT,
      toPhase: LifecyclePhase.SHAPE,
      userId: 'user-2',
    };
    await service.transition(request);

    const state = await repo.getState(PROJECT_ID);
    expect(state?.phaseHistory[0]?.bypassed).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// PhaseGateService — Progress Tracking
// ---------------------------------------------------------------------------

describe('PhaseGateService - progress tracking', () => {
  let repo: InMemoryProjectPhaseRepository;
  let service: PhaseGateService;

  beforeEach(async () => {
    repo = new InMemoryProjectPhaseRepository();
    service = new PhaseGateService(repo);
    await service.initializeProject(PROJECT_ID, LifecyclePhase.SHAPE);
  });

  it('getPhaseProgress returns "blocked" for a past phase whose outgoing gate has no artifacts', async () => {
    // INTENT is before SHAPE (completed), but gate:intent-to-shape is blocked
    // because no artifact service → status is overridden to 'blocked'
    const progress = await service.getPhaseProgress(
      PROJECT_ID,
      LifecyclePhase.INTENT
    );
    expect(progress.status).toBe('blocked');
  });

  it('getPhaseProgress returns "completed" for a past phase when its gate passes', async () => {
    const artifactSvc = makeArtifactSvc(
      makeCompleteArtifactsForPhase(LifecyclePhase.INTENT)
    );
    const svcWithArtifacts = new PhaseGateService(repo, artifactSvc as never);
    const progress = await svcWithArtifacts.getPhaseProgress(
      PROJECT_ID,
      LifecyclePhase.INTENT
    );
    expect(progress.status).toBe('completed');
  });

  it('getPhaseProgress returns "blocked" for the current phase when artifacts are missing', async () => {
    // SHAPE is current but gate:shape-to-validate has no artifacts → blocked
    const progress = await service.getPhaseProgress(
      PROJECT_ID,
      LifecyclePhase.SHAPE
    );
    expect(progress.status).toBe('blocked');
  });

  it('getPhaseProgress returns "not_started" for IMPROVE (last phase — no outgoing gate)', async () => {
    // IMPROVE has no following phase, so the gate-blocking override never fires
    const progress = await service.getPhaseProgress(
      PROJECT_ID,
      LifecyclePhase.IMPROVE
    );
    expect(progress.status).toBe('not_started');
  });

  it.each([
    [LifecyclePhase.INTENT, 3] as const,
    [LifecyclePhase.SHAPE, 3] as const,
    [LifecyclePhase.VALIDATE, 3] as const,
    [LifecyclePhase.GENERATE, 2] as const,
    [LifecyclePhase.RUN, 2] as const,
    [LifecyclePhase.OBSERVE, 2] as const,
    [LifecyclePhase.IMPROVE, 2] as const,
  ])(
    'getPhaseProgress(%s) uses catalog-derived total of %i artifacts',
    async (phase, expectedTotal) => {
      const progress = await service.getPhaseProgress(PROJECT_ID, phase);
      expect(progress.artifactProgress.total).toBe(expectedTotal);
    }
  );

  it('getLifecycleProgress includes a PhaseProgress entry for every phase', async () => {
    const progress = await service.getLifecycleProgress(PROJECT_ID);
    expect(progress.phases).toHaveLength(Object.values(LifecyclePhase).length);
  });

  it('getLifecycleProgress projectId and currentPhase are correct', async () => {
    const progress = await service.getLifecycleProgress(PROJECT_ID);
    expect(progress.projectId).toBe(PROJECT_ID);
    expect(progress.currentPhase).toBe(LifecyclePhase.SHAPE);
  });

  it('getLifecycleProgress overallProgress > 0 when a completed phase gate passes', async () => {
    const artifactSvc = makeArtifactSvc(
      makeCompleteArtifactsForPhase(LifecyclePhase.INTENT)
    );
    const svcWithArtifacts = new PhaseGateService(repo, artifactSvc as never);
    // Still use the PROJECT_ID that was initialized with SHAPE as current phase
    const progress = await svcWithArtifacts.getLifecycleProgress(PROJECT_ID);
    // INTENT is before SHAPE and its gate now passes → counted as completed
    expect(progress.overallProgress).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// usePhaseGates hook
// ---------------------------------------------------------------------------

describe('usePhaseGates', () => {
  it('initializes with loading=true', () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));
    expect(result.current.loading).toBe(true);
  });

  it('after async init, loading is false and currentPhase defaults to INTENT', async () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(result.current.currentPhase).toBe(LifecyclePhase.INTENT);
  });

  it('exposes gateStatuses with one entry per gate after load', async () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(Object.keys(result.current.gateStatuses)).toHaveLength(6);
  });

  it('transition with bypass changes currentPhase', async () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));

    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.transition(LifecyclePhase.SHAPE, 'user-1', {
        bypass: true,
        bypassReason: 'test bypass',
      });
    });

    await waitFor(() => {
      expect(result.current.currentPhase).toBe(LifecyclePhase.SHAPE);
    });
  });

  it('canTransition returns an object with canTransition field', async () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let canTransResult: { canTransition: boolean } | undefined;
    await act(async () => {
      canTransResult = await result.current.canTransition(LifecyclePhase.SHAPE);
    });

    expect(canTransResult).toHaveProperty('canTransition');
  });

  it('getProgress returns lifecycle progress with phases', async () => {
    const { result } = renderHook(() => usePhaseGates(PROJECT_ID));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let progress: { phases: unknown[] } | undefined;
    await act(async () => {
      progress = await result.current.getProgress();
    });

    expect(progress?.phases).toHaveLength(7);
  });
});
