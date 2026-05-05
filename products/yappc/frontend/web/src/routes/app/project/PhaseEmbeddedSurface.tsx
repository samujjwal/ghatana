import React from 'react';

import type { MountedPhase } from '../../../services/phase';

import CanvasRoute from './canvas';
import PreviewRoute from './preview';

export function PhaseEmbeddedSurface({ phase }: { phase: MountedPhase }): React.ReactNode {
  switch (phase) {
    case 'intent':
      return (
        <div
          data-testid="intent-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Intent discovery stays phase-native in the cockpit to keep goals, evidence, and recommendations
          in one place. Use this panel for supporting notes while preserving the primary operator flow.
        </div>
      );
    case 'shape':
      return (
        <div className="space-y-3" data-testid="shape-advanced-panel">
          <div className="rounded-xl border border-border bg-surface p-3 text-sm text-fg-muted">
            Shape-specific tooling is available in the cockpit summary above. Use this advanced surface for direct canvas edits.
          </div>
          <CanvasRoute />
        </div>
      );
    case 'generate':
      return (
        <div className="space-y-3" data-testid="generate-advanced-panel">
          <div className="rounded-xl border border-border bg-surface p-3 text-sm text-fg-muted">
            Generate-specific controls are surfaced in the cockpit. Use this advanced surface for deeper artifact-level edits.
          </div>
          <CanvasRoute />
        </div>
      );
    case 'run':
      return (
        <div
          data-testid="run-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Run execution details are intentionally phase-native in the cockpit to keep the operator flow focused.
          Use Observe for deeper timeline and runtime evidence.
        </div>
      );
    case 'observe':
      return (
        <div className="space-y-4">
          <div data-testid="project-preview-iframe">
            <PreviewRoute />
          </div>
          <div
            data-testid="observe-advanced-panel"
            className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
          >
            Observe lifecycle details are summarized in the cockpit status panels to reduce context switching.
            Use the preview surface above for direct runtime verification.
          </div>
        </div>
      );
    case 'validate':
      return (
        <div
          data-testid="validate-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Validation and approval gates are already surfaced in the phase-native cockpit panels.
          Open Observe for full lifecycle timeline and audit history details.
        </div>
      );
    case 'learn':
      return (
        <div
          data-testid="learn-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Learning summaries and reusable patterns stay phase-native. Use this panel for supporting
          notes while preserving the focused cockpit flow.
        </div>
      );
    case 'evolve':
      return (
        <div
          data-testid="evolve-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Evolution roadmap and backlog prioritization remain in the cockpit surface to avoid context switching.
        </div>
      );
    default:
      return (
        <div className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted">
          This phase is rendered with a phase-native cockpit summary. No generic embedded route is required.
        </div>
      );
  }
}
