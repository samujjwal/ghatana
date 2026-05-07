import { LifecyclePhase } from '@/types/lifecycle';
import {
  deriveCapabilities,
  projectCanEdit,
  type ProjectAccessFields,
} from '@/services/workspace/accessControl';

export type CanvasPhaseMode = 'design' | 'review' | 'generate-review' | 'runtime-review' | 'improve';

export interface CanvasAccessPolicy {
  readonly mode: CanvasPhaseMode;
  readonly modeLabel: string;
  readonly canEditCanvas: boolean;
  readonly canCreateArtifacts: boolean;
  readonly canMutateArtifacts: boolean;
  readonly canMoveNodes: boolean;
  readonly canComment: boolean;
  readonly canGenerate: boolean;
  readonly readOnlyReason?: string;
}

const PHASE_MODES: Record<LifecyclePhase, CanvasPhaseMode> = {
  [LifecyclePhase.INTENT]: 'design',
  [LifecyclePhase.CONTEXT]: 'design',
  [LifecyclePhase.PLAN]: 'review',
  [LifecyclePhase.EXECUTE]: 'generate-review',
  [LifecyclePhase.VERIFY]: 'runtime-review',
  [LifecyclePhase.OBSERVE]: 'runtime-review',
  [LifecyclePhase.LEARN]: 'improve',
  [LifecyclePhase.INSTITUTIONALIZE]: 'runtime-review',
};

const MODE_LABELS: Record<CanvasPhaseMode, string> = {
  design: 'Design mode',
  review: 'Validation review',
  'generate-review': 'Generation review',
  'runtime-review': 'Runtime review',
  improve: 'Improvement planning',
};

const PHASE_LOCK_REASONS: Record<CanvasPhaseMode, string | undefined> = {
  design: undefined,
  review: 'Canvas edits are paused during validation review.',
  'generate-review': 'Canvas edits are paused while generated output is reviewed.',
  'runtime-review': 'Canvas edits are paused while runtime evidence is reviewed.',
  improve: 'Canvas edits are paused while improvement inputs are reviewed.',
};

export function getCanvasPhaseMode(phase: LifecyclePhase): CanvasPhaseMode {
  return PHASE_MODES[phase] ?? 'runtime-review';
}

export function normalizeCanvasPolicyPhase(value: string | undefined): LifecyclePhase {
  switch (value) {
    case LifecyclePhase.INTENT:
      return LifecyclePhase.INTENT;
    case 'SHAPE':
    case LifecyclePhase.CONTEXT:
      return LifecyclePhase.CONTEXT;
    case 'VALIDATE':
    case LifecyclePhase.PLAN:
      return LifecyclePhase.PLAN;
    case 'GENERATE':
    case LifecyclePhase.EXECUTE:
      return LifecyclePhase.EXECUTE;
    case 'RUN':
    case LifecyclePhase.VERIFY:
      return LifecyclePhase.VERIFY;
    case LifecyclePhase.OBSERVE:
      return LifecyclePhase.OBSERVE;
    case 'IMPROVE':
    case LifecyclePhase.LEARN:
      return LifecyclePhase.LEARN;
    case 'EVOLVE':
    case LifecyclePhase.INSTITUTIONALIZE:
      return LifecyclePhase.INSTITUTIONALIZE;
    default:
      return LifecyclePhase.CONTEXT;
  }
}

export function deriveCanvasAccessPolicy(
  phase: LifecyclePhase,
  projectAccess: ProjectAccessFields | undefined
): CanvasAccessPolicy {
  const mode = getCanvasPhaseMode(phase);
  const capabilities = deriveCapabilities(projectAccess ?? {});
  const hasProjectWriteAccess = projectAccess ? projectCanEdit(projectAccess) : false;
  const phaseAllowsCanvasMutation = mode === 'design';
  const canMutateArtifacts = hasProjectWriteAccess && phaseAllowsCanvasMutation;
  const accessReason = capabilities.reason || (projectAccess?.readOnly ? 'You have view-only access to this project.' : undefined);
  const phaseReason = PHASE_LOCK_REASONS[mode];
  const readOnlyReason = canMutateArtifacts ? undefined : accessReason ?? phaseReason ?? 'Canvas edits are unavailable in this mode.';

  return {
    mode,
    modeLabel: MODE_LABELS[mode],
    canEditCanvas: canMutateArtifacts,
    canCreateArtifacts: canMutateArtifacts,
    canMutateArtifacts,
    canMoveNodes: canMutateArtifacts,
    canComment: capabilities.comment === true,
    canGenerate: hasProjectWriteAccess && mode === 'generate-review',
    readOnlyReason,
  };
}
