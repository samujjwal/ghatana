import { LifecyclePhase } from '@/types/lifecycle';

import type { MountedPhase } from './types';

export const CANONICAL_PHASE_ORDER = [
  'intent',
  'shape',
  'validate',
  'generate',
  'run',
  'observe',
  'learn',
  'evolve',
] as const satisfies readonly MountedPhase[];

export const CANONICAL_PHASE_LABELS: Record<MountedPhase, string> = {
  intent: 'Intent',
  shape: 'Shape',
  validate: 'Validate',
  generate: 'Generate',
  run: 'Run',
  observe: 'Observe',
  learn: 'Learn',
  evolve: 'Evolve',
};

export type PhaseNameInput = MountedPhase | LifecyclePhase | string;

const LIFECYCLE_TO_MOUNTED_PHASE: Record<LifecyclePhase, MountedPhase> = {
  [LifecyclePhase.INTENT]: 'intent',
  [LifecyclePhase.CONTEXT]: 'shape',
  [LifecyclePhase.PLAN]: 'validate',
  [LifecyclePhase.EXECUTE]: 'generate',
  [LifecyclePhase.VERIFY]: 'run',
  [LifecyclePhase.OBSERVE]: 'observe',
  [LifecyclePhase.LEARN]: 'learn',
  [LifecyclePhase.INSTITUTIONALIZE]: 'evolve',
};

const MOUNTED_PHASES = new Set<string>(CANONICAL_PHASE_ORDER);

export function normalizeToMountedPhase(phase: PhaseNameInput): MountedPhase {
  const rawPhase = String(phase);
  const lowerPhase = rawPhase.toLowerCase();

  if (MOUNTED_PHASES.has(lowerPhase)) {
    return lowerPhase as MountedPhase;
  }

  return LIFECYCLE_TO_MOUNTED_PHASE[rawPhase as LifecyclePhase] ?? 'intent';
}

export function getCanonicalPhaseLabel(phase: PhaseNameInput): string {
  return CANONICAL_PHASE_LABELS[normalizeToMountedPhase(phase)];
}

export function getNextCanonicalPhase(phase: PhaseNameInput): MountedPhase | null {
  const mountedPhase = normalizeToMountedPhase(phase);
  const index = CANONICAL_PHASE_ORDER.indexOf(mountedPhase);
  if (index < 0 || index === CANONICAL_PHASE_ORDER.length - 1) {
    return null;
  }

  return CANONICAL_PHASE_ORDER[index + 1] ?? null;
}
