import type { Blocker } from '@/components/phase/PhaseBlockerPanel';

import type {
  MountedPhase,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

export function buildPhaseBlockers(
  phase: MountedPhase,
  project: PhaseProjectSnapshot,
  preview: PhaseTransitionPreviewSnapshot | null,
): Blocker[] {
  const blockers: Blocker[] = [];

  if (!project.description?.trim()) {
    blockers.push({
      id: `${phase}-missing-description`,
      title: 'Project description missing',
      description: 'Add a short description so collaborators understand the scope and intended outcome.',
      severity: 'medium',
    });
  }

  if (preview && !preview.canAdvance) {
    preview.blockers.forEach((blocker, index) => {
      blockers.push({
        id: `${phase}-gate-${index}`,
        title: 'Lifecycle gate still blocked',
        description: blocker,
        severity: index === 0 ? 'high' : 'medium',
      });
    });
  }

  if (phase === 'run' && !preview?.nextPhase) {
    blockers.push({
      id: 'run-final-phase',
      title: 'No further promotion step',
      description: 'This project is already at the final available lifecycle phase.',
      severity: 'low',
    });
  }

  return blockers;
}
