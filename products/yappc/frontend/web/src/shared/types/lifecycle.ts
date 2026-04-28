export const LIFECYCLE_PHASE_ORDER = [
  'INTENT',
  'SHAPE',
  'VALIDATE',
  'GENERATE',
  'RUN',
  'OBSERVE',
  'LEARN',
  'EVOLVE',
] as const;

export type LifecyclePhase = (typeof LIFECYCLE_PHASE_ORDER)[number];

export const LIFECYCLE_PHASE_LABELS: Record<LifecyclePhase, string> = {
  INTENT: 'Intent',
  SHAPE: 'Shape',
  VALIDATE: 'Validate',
  GENERATE: 'Generate',
  RUN: 'Run',
  OBSERVE: 'Observe',
  LEARN: 'Learn',
  EVOLVE: 'Evolve',
};

export function isLifecyclePhase(value: string): value is LifecyclePhase {
  return (LIFECYCLE_PHASE_ORDER as readonly string[]).includes(value);
}

export function getLifecyclePhaseLabel(phase: LifecyclePhase): string {
  return LIFECYCLE_PHASE_LABELS[phase];
}
