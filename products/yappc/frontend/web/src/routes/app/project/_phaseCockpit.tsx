import React from 'react';

import { type MountedPhase } from '../../../services/phase';

import { PhaseCockpitContainer } from './PhaseCockpitContainer';

function PhaseRoute({ phase }: { readonly phase: MountedPhase }): React.ReactNode {
  return <PhaseCockpitContainer phase={phase} />;
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
