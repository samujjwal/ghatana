import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';

import type { MountedPhase, PhaseProjectSnapshot } from './types';

export function buildPhaseSuggestedSteps(
  phase: MountedPhase,
  project: PhaseProjectSnapshot,
  onAccept: () => void,
): SuggestedStep[] {
  const nextActions = (project.nextActionHints ?? [])
    .slice(0, 2)
    .map((title, index) => ({
      id: `${phase}-suggested-${index}`,
      title,
      description: 'Suggested next action from the latest backed project signals.',
      type: 'review' as const,
      estimatedTime: '5 min',
      onAccept,
    }));

  if (nextActions.length > 0) {
    return nextActions;
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
