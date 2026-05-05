import { LifecyclePhase, PHASE_LABELS } from '@/types/lifecycle';

export type WorkspaceRole = 'owner' | 'collaborator';

export interface NextActionRankingInput {
  readonly phase: LifecyclePhase;
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
}

interface RankedAction {
  readonly title: string;
  readonly score: number;
}

function normalizeActionTitle(title: string): string {
  return title.trim().replace(/\s+/g, ' ');
}

function dedupeByTitle(actions: readonly RankedAction[]): string[] {
  const seen = new Set<string>();
  const ordered = actions
    .sort((a, b) => b.score - a.score)
    .map((action) => normalizeActionTitle(action.title))
    .filter((title) => title.length > 0)
    .filter((title) => {
      const key = title.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  return ordered;
}

export function rankNextActions(input: NextActionRankingInput): string[] {
  const ranked: RankedAction[] = [];

  const firstIncomplete = input.phaseSteps.find((step) => !step.completed);
  if (firstIncomplete) {
    ranked.push({
      title: firstIncomplete.title,
      score: 100,
    });
  }

  input.projectSignals.aiNextActions.forEach((action, index) => {
    ranked.push({
      title: action,
      score: 90 - index * 4,
    });
  });

  if (input.project.hasUnsavedChanges) {
    ranked.push({
      title: 'Save and synchronize pending page artifact changes',
      score: 96,
    });
  }

  if (typeof input.projectSignals.aiHealthScore === 'number' && input.projectSignals.aiHealthScore < 60) {
    ranked.push({
      title: 'Stabilize failing signals before promoting this phase',
      score: 92,
    });
  }

  if (!firstIncomplete && input.canTransitionForward) {
    const nextPhase = getNextPhase(input.phase);
    if (nextPhase) {
      ranked.push({
        title: `Move to ${PHASE_LABELS[nextPhase]}`,
        score: input.role === 'owner' ? 86 : 74,
      });
    }
  }

  if (input.role === 'collaborator') {
    ranked.push({
      title: 'Request owner review for lifecycle promotion',
      score: 85,
    });
  }

  const fallback = `Review ${PHASE_LABELS[input.phase]} evidence and close remaining gaps`;
  ranked.push({ title: fallback, score: 20 });

  return dedupeByTitle(ranked).slice(0, 4);
}

function getNextPhase(phase: LifecyclePhase): LifecyclePhase | null {
  const order = [
    LifecyclePhase.INTENT,
    LifecyclePhase.CONTEXT,
    LifecyclePhase.PLAN,
    LifecyclePhase.EXECUTE,
    LifecyclePhase.VERIFY,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.LEARN,
    LifecyclePhase.INSTITUTIONALIZE,
  ] as const;

  const index = order.indexOf(phase);
  if (index < 0 || index === order.length - 1) {
    return null;
  }

  return order[index + 1];
}
