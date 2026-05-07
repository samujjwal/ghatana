import { describe, expect, it, vi } from 'vitest';
import { LifecyclePhase } from '@/types/lifecycle';
import {
  buildPhaseBlockers,
  buildPhaseEvidence,
  buildPhaseGovernanceRecords,
  rankNextActions,
  buildPhaseSuggestedSteps,
  buildPhaseCockpitContract,
  getPhaseCockpitConfig,
  getCanonicalPhaseLabel,
  getNextCanonicalPhase,
  normalizeToMountedPhase,
  rankNextActionDetails,
  type PhaseActivityEvent,
  type PhaseProjectSnapshot,
  type PhaseTransitionPreviewSnapshot,
} from '..';

const project = {
  name: 'Alpha',
  description: 'Useful project',
  lifecyclePhase: 'SHAPE',
  updatedAt: '2026-04-21T10:00:00.000Z',
  healthScore: 88,
  nextActionHints: ['Review the latest lifecycle evidence'],
} satisfies PhaseProjectSnapshot;

const activity: PhaseActivityEvent[] = [
  {
    id: 'audit-1',
    source: 'audit',
    action: 'PROJECT_UPDATED',
    summary: 'Project updated',
    timestamp: '2026-04-21T11:00:00.000Z',
    actor: 'user-1',
    success: true,
  },
];

const preview: PhaseTransitionPreviewSnapshot = {
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

describe('phase services', () => {
  it('normalizes legacy lifecycle names to canonical mounted phases', () => {
    expect(normalizeToMountedPhase(LifecyclePhase.CONTEXT)).toBe('shape');
    expect(normalizeToMountedPhase(LifecyclePhase.EXECUTE)).toBe('generate');
    expect(getCanonicalPhaseLabel(LifecyclePhase.VERIFY)).toBe('Run');
    expect(getNextCanonicalPhase(LifecyclePhase.PLAN)).toBe('generate');
  });

  it('returns stable cockpit config for each phase', () => {
    const config = getPhaseCockpitConfig('generate');

    expect(config.name).toBe('Generate');
    expect(config.supportingTitle).toBe('Implementation context');
    expect(config.actionFeedback).toContain('Implementation details');
  });

  it('builds evidence and governance records from backed project data', () => {
    const evidence = buildPhaseEvidence('shape', project, activity, preview);
    const governance = buildPhaseGovernanceRecords(activity);

    expect(evidence.map((item) => item.title)).toEqual(
      expect.arrayContaining(['Current lifecycle phase', 'Health score', 'Readiness prediction']),
    );
    expect(governance[0]).toMatchObject({
      id: 'audit-1',
      reviewState: 'approved',
      source: 'backed',
    });
  });

  it('builds blockers and suggested steps with operator-friendly defaults', () => {
    const blockers = buildPhaseBlockers(
      'run',
      { ...project, description: '' },
      { ...preview, nextPhase: null, canAdvance: false, blockers: ['Approval missing'] },
    );
    const onAccept = vi.fn();
    const suggestions = buildPhaseSuggestedSteps('shape', project, onAccept);

    expect(blockers.map((item) => item.title)).toEqual(
      expect.arrayContaining(['Project description missing', 'Lifecycle gate still blocked', 'No further promotion step']),
    );
    expect(suggestions[0]).toMatchObject({
      title: 'Review the latest lifecycle evidence',
      type: 'review',
    });
  });

  it('ranks next actions with owner-sensitive promotion guidance', () => {
    const ranked = rankNextActions({
      phase: LifecyclePhase.CONTEXT,
      phaseSteps: [
        { title: 'Add components', completed: true },
        { title: 'Connect nodes', completed: true },
      ],
      completedSteps: ['shape-1', 'shape-2'],
      project: { hasUnsavedChanges: true },
      projectSignals: {
        aiNextActions: ['Run lifecycle validation'],
        aiHealthScore: 52,
      },
      role: 'collaborator',
      canTransitionForward: true,
    });

    expect(ranked).toEqual(
      expect.arrayContaining([
        'Save and synchronize pending page artifact changes',
        'Stabilize failing signals before promoting this phase',
        'Run lifecycle validation',
        'Request owner review for lifecycle promotion',
      ]),
    );
  });

  it('builds next-best-action details from backed guidance, blockers, permissions, and gate confidence', () => {
    const ranked = rankNextActionDetails({
      phase: LifecyclePhase.EXECUTE,
      phaseSteps: [
        { title: 'Confirm generated artifact diff', completed: true },
      ],
      completedSteps: [],
      project: { hasUnsavedChanges: false },
      projectSignals: {
        aiNextActions: ['Review generated diff'],
        aiHealthScore: 91,
      },
      role: 'contributor',
      canTransitionForward: true,
      blockers: [{ title: 'Security review missing', severity: 'critical' }],
      predictionConfidence: 0.55,
    });

    expect(ranked[0]).toMatchObject({
      title: 'Resolve blocker: Security review missing',
      source: 'blocker',
      risk: 'high',
      requiresApproval: true,
      safeToRun: false,
    });
    expect(ranked.map((action) => action.title)).toEqual(
      expect.arrayContaining([
        'Review generated diff',
        'Gather stronger evidence before accepting lifecycle guidance',
        'Request owner review for lifecycle promotion',
      ]),
    );
  });

  it('builds a canonical cockpit contract separating persisted, derived, suggested, and review data', () => {
    const blockers = buildPhaseBlockers(
      'generate',
      project,
      { ...preview, canAdvance: false, blockers: ['Approval missing'] },
    );
    const suggestions = buildPhaseSuggestedSteps('generate', project, vi.fn());
    const contract = buildPhaseCockpitContract({
      phase: 'generate',
      project,
      activity,
      preview: { ...preview, canAdvance: false, blockers: ['Approval missing'] },
      blockers,
      evidence: buildPhaseEvidence('generate', project, activity, preview),
      governance: buildPhaseGovernanceRecords(activity),
      suggestions,
    });

    expect(contract.persisted.project.name).toBe('Alpha');
    expect(contract.persisted.activity).toHaveLength(1);
    expect(contract.derived.blockers.length).toBeGreaterThan(0);
    expect(contract.suggested.actions[0]?.title).toBe('Review the latest lifecycle evidence');
    expect(contract.review).toMatchObject({
      required: true,
      canAdvance: false,
    });
    expect(contract.review.reason).toContain('blocker');
  });
});
