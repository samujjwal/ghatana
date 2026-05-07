import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';

import type { Blocker } from '@/components/phase/PhaseBlockerPanel';
import { rankNextActionDetails } from './NextActionRankingService';
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
      description: `${action.description} Source: ${action.source}; risk: ${action.risk}${action.requiresApproval ? '; review required' : ''}.`,
      type: action.type,
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
      estimatedTime: '3 min',
      onAccept,
    },
  ];
}
