import { describe, expect, it } from 'vitest';
import type { Blocker } from '@/components/phase/PhaseBlockerPanel';
import type { EvidenceItem } from '@/components/phase/PhaseEvidencePanel';
import type { GovernanceRecord } from '@/components/phase/PhaseGovernanceTrace';
import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';
import { buildPhaseCockpitContract } from '../PhaseCockpitContractBuilder';
import type { PhaseProjectSnapshot, PhaseTransitionPreviewSnapshot, PhaseActivityEvent } from '../types';

const project: PhaseProjectSnapshot = {
  name: 'Alpha Project',
  description: 'Test project',
  lifecyclePhase: 'SHAPE',
  updatedAt: '2026-04-21T10:00:00.000Z',
  healthScore: 88,
  nextActionHints: ['Review evidence'],
};

const activity: readonly PhaseActivityEvent[] = [
  {
    id: 'evt-1',
    source: 'audit',
    action: 'PROJECT_UPDATED',
    summary: 'Project updated',
    timestamp: '2026-04-21T11:00:00.000Z',
    actor: 'user-1',
    success: true,
  },
];

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
  blockers: ['Security review pending'],
};

const makeBlocker = (overrides: Partial<Blocker> = {}): Blocker => ({
  id: 'blocker-1',
  title: 'Missing security review',
  description: 'Security review is required before proceeding.',
  severity: 'critical',
  ...overrides,
});

const makeEvidence = (overrides: Partial<EvidenceItem> = {}): EvidenceItem => ({
  id: 'e1',
  type: 'observation',
  title: 'Spec review complete',
  description: 'The spec was reviewed and approved.',
  ...overrides,
});

const makeGovernance = (overrides: Partial<GovernanceRecord> = {}): GovernanceRecord => ({
  id: 'g1',
  artifactId: 'art-1',
  action: 'APPROVED',
  actor: 'reviewer-1',
  timestamp: '2026-04-21T11:10:00.000Z',
  source: 'backed',
  ...overrides,
});

const makeSuggestion = (overrides: Partial<SuggestedStep> = {}): SuggestedStep => ({
  id: 's1',
  title: 'Submit for review',
  description: 'Submit the current phase artifacts for human review.',
  type: 'review',
  confidence: 0.9,
  evidence: [],
  riskLevel: 'low',
  applyMode: 'review-required',
  approvalRequired: true,
  rollbackSupported: false,
  ...overrides,
});

describe('buildPhaseCockpitContract', () => {
  it('returns a contract with all required top-level sections', () => {
    const contract = buildPhaseCockpitContract({
      phase: 'shape',
      project,
      activity,
      preview: readyPreview,
      blockers: [],
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.phase).toBe('shape');
    expect(contract.persisted.project).toBe(project);
    expect(contract.persisted.activity).toBe(activity);
    expect(contract.derived.preview).toBe(readyPreview);
    expect(contract.derived.blockers).toEqual([]);
    expect(contract.suggested.actions).toEqual([]);
  });

  it('sets review.required to false and canAdvance to true when no blockers and preview allows advancement', () => {
    const contract = buildPhaseCockpitContract({
      phase: 'shape',
      project,
      activity,
      preview: readyPreview,
      blockers: [],
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.required).toBe(false);
    expect(contract.review.canAdvance).toBe(true);
    expect(contract.review.reason).toBeNull();
  });

  it('sets review.required to true and surfaces singular blocker reason', () => {
    const blockers: Blocker[] = [makeBlocker()];

    const contract = buildPhaseCockpitContract({
      phase: 'validate',
      project,
      activity,
      preview: blockedPreview,
      blockers,
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.required).toBe(true);
    expect(contract.review.reason).toBe('1 blocker require review.');
    expect(contract.review.canAdvance).toBe(false);
  });

  it('sets review.required to true when preview canAdvance is false but no blockers', () => {
    const contract = buildPhaseCockpitContract({
      phase: 'generate',
      project,
      activity,
      preview: blockedPreview,
      blockers: [],
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.required).toBe(true);
    expect(contract.review.reason).toBe('Lifecycle preview has not approved promotion.');
    expect(contract.review.canAdvance).toBe(false);
  });

  it('sets canAdvance to true when preview is null and no blockers', () => {
    const contract = buildPhaseCockpitContract({
      phase: 'intent',
      project,
      activity,
      preview: null,
      blockers: [],
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.canAdvance).toBe(true);
    expect(contract.review.required).toBe(false);
  });

  it('sets canAdvance to false when preview is null and blockers are present', () => {
    const blockers: Blocker[] = [
      makeBlocker({ id: 'b1', title: 'Missing requirement', severity: 'high' }),
      makeBlocker({ id: 'b2', title: 'Unresolved feedback', severity: 'medium' }),
    ];

    const contract = buildPhaseCockpitContract({
      phase: 'intent',
      project,
      activity,
      preview: null,
      blockers,
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.canAdvance).toBe(false);
    expect(contract.review.required).toBe(true);
    expect(contract.review.reason).toContain('2 blockers');
  });

  it('passes evidence and governance through to the derived section unchanged', () => {
    const evidence: EvidenceItem[] = [makeEvidence()];
    const governance: GovernanceRecord[] = [makeGovernance()];

    const contract = buildPhaseCockpitContract({
      phase: 'validate',
      project,
      activity,
      preview: readyPreview,
      blockers: [],
      evidence,
      governance,
      suggestions: [],
    });

    expect(contract.derived.evidence).toBe(evidence);
    expect(contract.derived.governance).toBe(governance);
  });

  it('passes suggestions through to the suggested section unchanged', () => {
    const suggestions: SuggestedStep[] = [makeSuggestion()];

    const contract = buildPhaseCockpitContract({
      phase: 'validate',
      project,
      activity,
      preview: readyPreview,
      blockers: [],
      evidence: [],
      governance: [],
      suggestions,
    });

    expect(contract.suggested.actions).toBe(suggestions);
  });

  it('uses plural reason message when multiple blockers exist', () => {
    const blockers: Blocker[] = [
      makeBlocker({ id: 'b1', title: 'Blocker A', severity: 'critical' }),
      makeBlocker({ id: 'b2', title: 'Blocker B', severity: 'high' }),
      makeBlocker({ id: 'b3', title: 'Blocker C', severity: 'medium' }),
    ];

    const contract = buildPhaseCockpitContract({
      phase: 'run',
      project,
      activity,
      preview: readyPreview,
      blockers,
      evidence: [],
      governance: [],
      suggestions: [],
    });

    expect(contract.review.reason).toBe('3 blockers require review.');
  });
});
