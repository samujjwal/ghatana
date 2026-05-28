import React from 'react';

import { type MountedPhase } from '../../../services/phase';
import { YappcPageShell } from '../../../components/layout/YappcPageShell';

import { PhaseCockpitContainer } from './PhaseCockpitContainer';

const PHASE_LABEL: Record<MountedPhase, string> = {
  intent: 'Intent',
  shape: 'Shape',
  validate: 'Validate',
  generate: 'Generate',
  run: 'Run',
  observe: 'Observe',
  learn: 'Learn',
  evolve: 'Evolve',
};

function PhaseRoute({ phase }: { readonly phase: MountedPhase }): React.ReactNode {
  return (
    <YappcPageShell
      title={`${PHASE_LABEL[phase]} Cockpit`}
      description="Backend-owned phase state, readiness, and action controls."
      testId={`phase-${phase}-shell`}
    >
      <PhaseCockpitContainer phase={phase} />
    </YappcPageShell>
  );
}

export function IntentCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="intent" />;
}

export function ShapeCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="shape" />;
}

export function ValidateCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="validate" />;
}

export function GenerateCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="generate" />;
}

export function RunCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="run" />;
}

export function ObserveCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="observe" />;
}

export function LearnCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="learn" />;
}

export function EvolveCockpitRoute(): React.ReactNode {
  return <PhaseRoute phase="evolve" />;
}
