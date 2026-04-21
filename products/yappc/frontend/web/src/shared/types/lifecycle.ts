export const LIFECYCLE_PHASE_ORDER = [
  'INTENT',
  'CONTEXT',
  'PLAN',
  'EXECUTE',
  'VERIFY',
  'OBSERVE',
  'LEARN',
  'INSTITUTIONALIZE',
] as const;

export type LifecyclePhase = (typeof LIFECYCLE_PHASE_ORDER)[number];

export const LIFECYCLE_PHASE_LABELS: Record<LifecyclePhase, string> = {
  INTENT: 'Intent',
  CONTEXT: 'Context',
  PLAN: 'Plan',
  EXECUTE: 'Execute',
  VERIFY: 'Verify',
  OBSERVE: 'Observe',
  LEARN: 'Learn',
  INSTITUTIONALIZE: 'Institutionalize',
};

export function isLifecyclePhase(value: string): value is LifecyclePhase {
  return (LIFECYCLE_PHASE_ORDER as readonly string[]).includes(value);
}

export function getLifecyclePhaseLabel(phase: LifecyclePhase): string {
  return LIFECYCLE_PHASE_LABELS[phase];
}
