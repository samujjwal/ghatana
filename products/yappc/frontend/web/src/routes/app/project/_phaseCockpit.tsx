import React from 'react';

import { type MountedPhase } from '../../../services/phase';
import { YappcPageShell } from '../../../components/layout/YappcPageShell';
import { translate } from '../../../i18n/messages';

import { PhaseCockpitContainer } from './PhaseCockpitContainer';

const PHASE_LABEL_KEY: Record<MountedPhase, string> = {
  intent: 'phase.intent.label',
  shape: 'phase.shape.label',
  validate: 'phase.validate.label',
  generate: 'phase.generate.label',
  run: 'phase.run.label',
  observe: 'phase.observe.label',
  learn: 'phase.learn.label',
  evolve: 'phase.evolve.label',
};

function PhaseRoute({ phase }: { readonly phase: MountedPhase }): React.ReactNode {
  const phaseLabel = translate(PHASE_LABEL_KEY[phase]);
  return (
    <YappcPageShell
      title={translate('phase.cockpit.title').replace('${phase}', phaseLabel)}
      description={translate('phase.cockpit.description')}
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
