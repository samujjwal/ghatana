import type { Blocker } from '@/components/phase/PhaseBlockerPanel';
import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';
import {
  getCanonicalPhaseLabel,
  getNextCanonicalPhase,
  type PhaseNameInput,
} from './CanonicalPhaseService';

export type WorkspaceRole = 'owner' | 'approver' | 'contributor' | 'viewer' | 'guest' | 'collaborator';
export type NextActionSource = 'backend' | 'blocker' | 'gate' | 'health' | 'draft' | 'role' | 'fallback';
export type NextActionRisk = 'low' | 'medium' | 'high';

export interface NextActionRankingInput {
  readonly phase: PhaseNameInput;
  readonly phaseSteps: readonly {
    readonly title: string;
    readonly completed: boolean;
  }[];
  readonly completedSteps: readonly string[];
  readonly project: {
    readonly hasUnsavedChanges: boolean;
  };
  readonly projectSignals: {
    readonly aiNextActions: readonly string[];
    readonly aiHealthScore?: number;
  };
  readonly role: WorkspaceRole;
  readonly canTransitionForward: boolean;
  readonly blockers?: readonly Pick<Blocker, 'title' | 'severity'>[];
  readonly predictionConfidence?: number | null;
}

export interface RankedNextAction {
  readonly id: string;
  readonly title: string;
  readonly description: string;
  readonly type: SuggestedStep['type'];
  readonly score: number;
  readonly source: NextActionSource;
  readonly risk: NextActionRisk;
  readonly requiresApproval: boolean;
  readonly safeToRun: boolean;
}

function normalizeActionTitle(title: string): string {
  return title.trim().replace(/\s+/g, ' ');
}

function getPhaseLabel(phase: PhaseNameInput): string {
  return getCanonicalPhaseLabel(phase);
}

function dedupeByTitle(actions: readonly RankedNextAction[]): RankedNextAction[] {
  const seen = new Set<string>();
  return [...actions]
    .sort((a, b) => b.score - a.score)
    .map((action) => ({
      ...action,
      title: normalizeActionTitle(action.title),
    }))
    .filter((action) => action.title.length > 0)
    .filter((action) => {
      const title = action.title;
      const key = title.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
}

function createRankedAction(args: Omit<RankedNextAction, 'id'>): RankedNextAction {
  const normalizedTitle = normalizeActionTitle(args.title);
  const idSource = `${args.source}:${normalizedTitle.toLowerCase()}`;
  const id = idSource.replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
  return {
    ...args,
    id,
    title: normalizedTitle,
  };
}

export function rankNextActionDetails(input: NextActionRankingInput): readonly RankedNextAction[] {
  const ranked: RankedNextAction[] = [];

  const firstIncomplete = input.phaseSteps.find((step) => !step.completed);
  if (firstIncomplete) {
    ranked.push(createRankedAction({
      title: firstIncomplete.title,
      description: 'Continue the next incomplete phase step before taking higher-risk actions.',
      type: 'manual',
      score: 100,
      source: 'fallback',
      risk: 'low',
      requiresApproval: false,
      safeToRun: true,
    }));
  }

  input.projectSignals.aiNextActions.forEach((action, index) => {
    ranked.push(createRankedAction({
      title: action,
      description: 'Ranked from the latest backed project guidance and lifecycle signals.',
      type: 'review',
      score: 90 - index * 4,
      source: 'backend',
      risk: 'medium',
      requiresApproval: true,
      safeToRun: false,
    }));
  });

  if (input.project.hasUnsavedChanges) {
    ranked.push(createRankedAction({
      title: 'Save and synchronize pending page artifact changes',
      description: 'Persist local artifact changes before review, generation, or run actions.',
      type: 'manual',
      score: 96,
      source: 'draft',
      risk: 'low',
      requiresApproval: false,
      safeToRun: true,
    }));
  }

  if (typeof input.projectSignals.aiHealthScore === 'number' && input.projectSignals.aiHealthScore < 60) {
    ranked.push(createRankedAction({
      title: 'Stabilize failing signals before promoting this phase',
      description: `Project health is ${input.projectSignals.aiHealthScore}%, so promotion should wait for evidence or remediation.`,
      type: 'review',
      score: 92,
      source: 'health',
      risk: 'high',
      requiresApproval: true,
      safeToRun: false,
    }));
  }

  input.blockers?.forEach((blocker, index) => {
    const highSeverity = blocker.severity === 'critical' || blocker.severity === 'high';
    ranked.push(createRankedAction({
      title: `Resolve blocker: ${blocker.title}`,
      description: highSeverity
        ? 'A blocking lifecycle issue must be cleared before safe promotion.'
        : 'Address this lifecycle concern to improve readiness confidence.',
      type: 'review',
      score: highSeverity ? 98 - index : 88 - index,
      source: 'blocker',
      risk: highSeverity ? 'high' : 'medium',
      requiresApproval: highSeverity,
      safeToRun: !highSeverity,
    }));
  });

  if (typeof input.predictionConfidence === 'number' && input.predictionConfidence < 0.6) {
    ranked.push(createRankedAction({
      title: 'Gather stronger evidence before accepting lifecycle guidance',
      description: `Prediction confidence is ${Math.round(input.predictionConfidence * 100)}%, below the review threshold.`,
      type: 'review',
      score: 84,
      source: 'gate',
      risk: 'medium',
      requiresApproval: true,
      safeToRun: false,
    }));
  }

  if (!firstIncomplete && input.canTransitionForward) {
    const nextPhase = getNextCanonicalPhase(input.phase);
    if (nextPhase) {
      ranked.push(createRankedAction({
        title: `Move to ${getPhaseLabel(nextPhase)}`,
        description: 'Lifecycle preview indicates the gate can advance with the current evidence.',
        type: input.role === 'owner' || input.role === 'approver' ? 'automation' : 'review',
        score: input.role === 'owner' || input.role === 'approver' ? 86 : 74,
        source: 'gate',
        risk: 'medium',
        requiresApproval: input.role !== 'owner' && input.role !== 'approver',
        safeToRun: input.role === 'owner' || input.role === 'approver',
      }));
    }
  }

  if (input.role === 'collaborator' || input.role === 'contributor') {
    ranked.push(createRankedAction({
      title: 'Request owner review for lifecycle promotion',
      description: 'Your role can prepare the recommendation, but an owner or approver should make the promotion decision.',
      type: 'review',
      score: 85,
      source: 'role',
      risk: 'medium',
      requiresApproval: true,
      safeToRun: false,
    }));
  }

  if (input.role === 'viewer' || input.role === 'guest') {
    ranked.push(createRankedAction({
      title: 'Ask a project contributor to take the next lifecycle action',
      description: 'Current permissions are read-only for phase-changing actions.',
      type: 'manual',
      score: 89,
      source: 'role',
      risk: 'low',
      requiresApproval: true,
      safeToRun: false,
    }));
  }

  const fallback = `Review ${getPhaseLabel(input.phase)} evidence and close remaining gaps`;
  ranked.push(createRankedAction({
    title: fallback,
    description: 'Fallback action when no higher-confidence backed action is available.',
    type: 'manual',
    score: 20,
    source: 'fallback',
    risk: 'low',
    requiresApproval: false,
    safeToRun: true,
  }));

  return dedupeByTitle(ranked).slice(0, 4);
}

export function rankNextActions(input: NextActionRankingInput): string[] {
  return rankNextActionDetails(input).map((action) => action.title);
}
