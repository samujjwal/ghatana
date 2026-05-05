import { describe, expect, it, vi } from 'vitest';
import {
  buildPhaseBlockers,
  buildPhaseEvidence,
  buildPhaseGovernanceRecords,
  buildPhaseSuggestedSteps,
  getPhaseCockpitConfig,
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
});
