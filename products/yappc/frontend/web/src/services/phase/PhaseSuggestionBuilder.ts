import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';

import type { Blocker } from '@/components/phase/PhaseBlockerPanel';
import { rankNextActionDetails, type RankedNextAction } from './NextActionRankingService';
import type {
  MountedPhase,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
  PhaseUserRole,
} from './types';

function buildPhaseSteps(
  phase: MountedPhase,
  project: PhaseProjectSnapshot,
  preview: PhaseTransitionPreviewSnapshot | null,
): readonly { readonly title: string; readonly completed: boolean }[] {
  const hasDescription = Boolean(project.description?.trim());
  const hasRecentProjectTruth = Boolean(project.updatedAt);
  const gatePassing = preview?.canAdvance ?? true;

  return [
    {
      title: `Confirm ${phase} project context`,
      completed: hasDescription,
    },
    {
      title: 'Review latest backed project activity',
      completed: hasRecentProjectTruth,
    },
    {
      title: 'Clear lifecycle gate for the next phase',
      completed: gatePassing,
    },
  ] as const;
}

function getSuggestionConfidence(
  action: RankedNextAction,
  preview: PhaseTransitionPreviewSnapshot | null,
  project: PhaseProjectSnapshot,
): number {
  if (typeof preview?.predictionConfidence === 'number' && action.source === 'gate') {
    return preview.predictionConfidence;
  }
  if (typeof project.healthScore === 'number' && action.source === 'health') {
    return Math.max(0, Math.min(1, project.healthScore / 100));
  }
  if (action.source === 'backend') {
    return 0.82;
  }
  if (action.source === 'blocker') {
    return action.risk === 'high' ? 0.93 : 0.78;
  }
  if (action.source === 'role') {
    return 0.88;
  }
  return action.safeToRun ? 0.74 : 0.68;
}

function getSuggestionEvidence(
  action: RankedNextAction,
  preview: PhaseTransitionPreviewSnapshot | null,
  project: PhaseProjectSnapshot,
): readonly string[] {
  const evidence = [`Source: ${action.source}`, `Risk: ${action.risk}`];

  if (typeof preview?.predictionConfidence === 'number') {
    evidence.push(`Lifecycle readiness confidence: ${Math.round(preview.predictionConfidence * 100)}%`);
  }
  if (typeof project.healthScore === 'number') {
    evidence.push(`Project health: ${project.healthScore}%`);
  }
  if (action.requiresApproval) {
    evidence.push('Human approval is required before this action can mutate lifecycle state.');
  }
  if (action.safeToRun) {
    evidence.push('Current role and gate signals allow this suggestion to be prepared safely.');
  }

  return evidence;
}

function getSuggestionApplyMode(action: RankedNextAction): SuggestedStep['applyMode'] {
  if (action.type === 'automation' && action.safeToRun && !action.requiresApproval) {
    return 'one-click';
  }
  if (action.requiresApproval || action.type === 'review') {
    return 'review-required';
  }
  return 'manual';
}

function supportsRollback(phase: MountedPhase, action: RankedNextAction): boolean {
  if (!action.safeToRun || action.requiresApproval) {
    return false;
  }
  return phase === 'generate' || phase === 'run' || action.type === 'automation';
}

export function buildPhaseSuggestedSteps(
  phase: MountedPhase,
  project: PhaseProjectSnapshot,
  onAccept: () => void,
  options?: {
    readonly blockers?: readonly Blocker[];
    readonly preview?: PhaseTransitionPreviewSnapshot | null;
    readonly role?: PhaseUserRole;
  },
): SuggestedStep[] {
  const preview = options?.preview ?? null;
  const rankedActions = rankNextActionDetails({
    phase,
    phaseSteps: buildPhaseSteps(phase, project, preview),
    completedSteps: [],
    project: {
      hasUnsavedChanges: false,
    },
    projectSignals: {
      aiNextActions: project.nextActionHints ?? [],
      aiHealthScore: project.healthScore ?? undefined,
    },
    role: options?.role ?? 'contributor',
    canTransitionForward: preview?.canAdvance ?? false,
    blockers: options?.blockers ?? [],
    predictionConfidence: preview?.predictionConfidence,
  });

  if (rankedActions.length > 0) {
    return rankedActions.map((action, index) => ({
      id: `${phase}-next-best-${action.id || index}`,
      title: action.title,
      description: action.description,
      type: action.type,
      confidence: getSuggestionConfidence(action, preview, project),
      evidence: getSuggestionEvidence(action, preview, project),
      riskLevel: action.risk,
      applyMode: getSuggestionApplyMode(action),
      approvalRequired: action.requiresApproval,
      rollbackSupported: supportsRollback(phase, action),
      estimatedTime: action.safeToRun ? '3 min' : '5 min',
      onAccept,
    }));
  }

  return [
    {
      id: `${phase}-default-suggestion`,
      title: 'Review the details below',
      description: 'Use the detailed surface below to gather the evidence needed for the next decision.',
      type: 'manual',
      confidence: 0.65,
      evidence: ['Source: fallback', 'No higher-confidence backed action was available.'],
      riskLevel: 'low',
      applyMode: 'manual',
      approvalRequired: false,
      rollbackSupported: false,
      estimatedTime: '3 min',
      onAccept,
    },
  ];
}
