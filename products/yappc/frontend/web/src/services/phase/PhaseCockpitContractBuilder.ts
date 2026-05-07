import type { Blocker } from '@/components/phase/PhaseBlockerPanel';
import type { EvidenceItem } from '@/components/phase/PhaseEvidencePanel';
import type { GovernanceRecord } from '@/components/phase/PhaseGovernanceTrace';
import type { SuggestedStep } from '@/components/phase/PhaseSuggestedNextStep';

import type {
  MountedPhase,
  PhaseActivityEvent,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

export interface PhaseCockpitContract {
  readonly phase: MountedPhase;
  readonly persisted: {
    readonly project: PhaseProjectSnapshot;
    readonly activity: readonly PhaseActivityEvent[];
  };
  readonly derived: {
    readonly preview: PhaseTransitionPreviewSnapshot | null;
    readonly blockers: readonly Blocker[];
    readonly evidence: readonly EvidenceItem[];
    readonly governance: readonly GovernanceRecord[];
  };
  readonly suggested: {
    readonly actions: readonly SuggestedStep[];
  };
  readonly review: {
    readonly required: boolean;
    readonly canAdvance: boolean;
    readonly reason: string | null;
  };
}

function getReviewReason(
  blockers: readonly Blocker[],
  preview: PhaseTransitionPreviewSnapshot | null,
): string | null {
  if (blockers.length > 0) {
    return `${blockers.length} blocker${blockers.length === 1 ? '' : 's'} require review.`;
  }

  if (preview && !preview.canAdvance) {
    return 'Lifecycle preview has not approved promotion.';
  }

  return null;
}

export function buildPhaseCockpitContract(params: {
  readonly phase: MountedPhase;
  readonly project: PhaseProjectSnapshot;
  readonly activity: readonly PhaseActivityEvent[];
  readonly preview: PhaseTransitionPreviewSnapshot | null;
  readonly blockers: readonly Blocker[];
  readonly evidence: readonly EvidenceItem[];
  readonly governance: readonly GovernanceRecord[];
  readonly suggestions: readonly SuggestedStep[];
}): PhaseCockpitContract {
  const reason = getReviewReason(params.blockers, params.preview);

  return {
    phase: params.phase,
    persisted: {
      project: params.project,
      activity: params.activity,
    },
    derived: {
      preview: params.preview,
      blockers: params.blockers,
      evidence: params.evidence,
      governance: params.governance,
    },
    suggested: {
      actions: params.suggestions,
    },
    review: {
      required: reason !== null,
      canAdvance: params.preview?.canAdvance ?? params.blockers.length === 0,
      reason,
    },
  };
}
