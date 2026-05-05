import React from 'react';

import { Card, CardContent } from '@ghatana/design-system';

import type { Blocker } from '../../../components/phase/PhaseBlockerPanel';
import { ProvenanceBadge } from '../../../components/shared/ProvenanceBadge';
import type {
  MountedPhase,
  PhaseActivityEvent,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';

interface PhaseStatusPanelsProps {
  phase: MountedPhase;
  preview: PhaseTransitionPreviewSnapshot | null;
  blockers: Blocker[];
  activity: PhaseActivityEvent[];
}

function StatusPanel({ testId, title, content }: { testId: string; title: string; content: React.ReactNode }) {
  return (
    <Card variant="outlined" data-testid={testId}>
      <CardContent className="p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">{title}</p>
        <div className="mt-2 text-sm text-fg-muted">{content}</div>
      </CardContent>
    </Card>
  );
}

export function PhaseStatusPanels({ phase, preview, blockers, activity }: PhaseStatusPanelsProps): React.ReactNode {
  if (phase === 'validate') {
    const requiredApprovals = preview?.requiredArtifacts ?? [];

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <Card variant="outlined" data-testid="validation-status">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Validation status</p>
            <p className="mt-2 text-lg font-semibold text-fg">
              {blockers.length === 0 ? 'Passed' : preview?.canAdvance ? 'Pending review' : 'Failed'}
            </p>
          </CardContent>
        </Card>
        <Card variant="outlined" data-testid="approval-gates">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Approval gates</p>
            {requiredApprovals.length > 0 ? (
              <div className="mt-3 space-y-2">
                {requiredApprovals.map((item) => (
                  <div key={item} data-testid="required-approval" className="flex items-center gap-2 text-sm text-fg">
                    <ProvenanceBadge type="backed" size="sm" label="Required" />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="required-approval-empty">
                No explicit approval artifacts are currently reported by lifecycle preview.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'generate') {
    const outputBundle = preview?.requiredArtifacts ?? [];

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="codegen-preview-panel"
          title="Codegen preview"
          content="Review the readiness signal and planned outputs here before opening detailed builder context."
        />
        <Card variant="outlined" data-testid="generated-file-list">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Planned output bundle</p>
            {outputBundle.length > 0 ? (
              <div className="mt-3 space-y-2">
                {outputBundle.map((item) => (
                  <div key={item} data-testid="generated-file" className="text-sm text-fg">
                    {item}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="generated-file-empty">
                Output plan is not ready yet.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'run') {
    const requiredCapabilities = preview?.requiredArtifacts ?? [];

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <Card variant="outlined" data-testid="capability-gates">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Capability gates</p>
            {requiredCapabilities.length > 0 ? (
              <div className="mt-3 space-y-2">
                {requiredCapabilities.map((item) => (
                  <div key={item} data-testid="required-capability" className="text-sm text-fg">
                    {item}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="required-capability-empty">
                No additional capability gates are currently reported for this run transition.
              </p>
            )}
          </CardContent>
        </Card>
        <StatusPanel
          testId="run-plan-panel"
          title="Run plan"
          content={
            <span data-testid="pipeline-readiness">
              {preview?.canAdvance ? 'Ready with operator review' : 'Not ready until blockers are cleared'}
            </span>
          }
        />
      </div>
    );
  }

  if (phase === 'observe') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="metrics-panel"
          title="Metrics summary"
          content={
            activity.length > 0
              ? `Tracking ${activity.length} recent backed lifecycle or audit events.`
              : 'No recent backed events have been recorded yet.'
          }
        />
        <StatusPanel
          testId="incidents-panel"
          title="Incidents"
          content={
            blockers.length > 0
              ? `${blockers.length} issue(s) need review before promotion.`
              : 'No active incident-level blockers are surfaced here.'
          }
        />
      </div>
    );
  }

  if (phase === 'learn') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="retrospective-panel"
          title="Retrospective"
          content="Review the lifecycle evidence below to capture operator learnings and lessons from this cycle."
        />
        <StatusPanel
          testId="reusable-patterns"
          title="Reusable patterns"
          content="Promote stable learnings into repeatable delivery patterns once they have enough backing evidence."
        />
      </div>
    );
  }

  if (phase === 'evolve') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="roadmap-panel"
          title="Roadmap"
          content="Use the learnings and blockers below to decide which work belongs in the next improvement cycle."
        />
        <StatusPanel
          testId="backlog-panel"
          title="Backlog"
          content="The backlog should stay aligned with backed evidence and the highest-value next action."
        />
      </div>
    );
  }

  return null;
}
