import React from 'react';

import type { MountedPhase } from '../../../services/phase';
import { translate } from '../../../i18n/messages';

import CanvasRoute from './canvas';
import PreviewRoute from './preview';
import { PhaseStatusPanelsCanonical } from './PhaseStatusPanelsCanonical';

interface PhaseEmbeddedSurfaceProps {
  readonly phase: MountedPhase;
  readonly phasePanels?: readonly import('../../../types/phasePacket').PhasePanelView[];
}

export function PhaseEmbeddedSurface({ phase, phasePanels = [] }: PhaseEmbeddedSurfaceProps): React.ReactNode {
  switch (phase) {
    case 'intent':
      return (
        <div data-testid="intent-embedded-surface">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
        </div>
      );
    case 'shape':
      return (
        <div className="space-y-3" data-testid="shape-advanced-panel">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
          <CanvasRoute />
        </div>
      );
    case 'generate':
      return (
        <div className="space-y-3" data-testid="generate-advanced-panel">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
          <CanvasRoute />
        </div>
      );
    case 'run':
      return (
        <div data-testid="run-advanced-panel">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
        </div>
      );
    case 'observe':
      return (
        <div className="space-y-4">
          <div data-testid="project-preview-iframe">
            <PreviewRoute />
          </div>
          <div data-testid="observe-advanced-panel">
            <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
          </div>
        </div>
      );
    case 'validate':
      return (
        <div data-testid="validate-embedded-surface">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
        </div>
      );
    case 'learn':
      return (
        <div data-testid="learn-embedded-surface">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
        </div>
      );
    case 'evolve':
      return (
        <div data-testid="evolve-embedded-surface">
          <PhaseStatusPanelsCanonical phase={phase} phasePanels={phasePanels} />
        </div>
      );
    default:
      return (
        <div className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted">
          {translate('phase.embedded.genericPlaceholder')}
        </div>
      );
  }
}
