import React from 'react';

import { Badge, Card, CardContent } from '@ghatana/design-system';

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
  if (phase === 'intent') {
    const readinessValue = preview?.readiness;

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="intent-goal-clarity"
          title="Goal clarity"
          content={
            readinessValue != null
              ? `Intent quality is currently ${readinessValue}%. Confirm business outcomes before shaping architecture.`
              : 'Capture the core business outcomes, target users, and measurable success criteria.'
          }
        />
        <Card variant="outlined" data-testid="intent-artifact-snapshot">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Intent artifacts</p>
            {preview?.completedArtifacts?.length ? (
              <div className="mt-3 space-y-2">
                {preview.completedArtifacts.map((item) => (
                  <div key={item} className="flex items-center gap-2 text-sm text-fg" data-testid="intent-artifact">
                    <span className="inline-block h-2 w-2 rounded-full bg-success-color" aria-hidden="true" />
                    {item}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted">No backed intent artifacts have been recorded yet.</p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'shape') {
    const requiredArtifacts = preview?.requiredArtifacts ?? [];

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <StatusPanel
            testId="shape-architecture-readiness"
            title="Architecture readiness"
            content={
              preview?.canAdvance
                ? 'Architecture shape is ready for validation review.'
                : `${blockers.length} blocker(s) are preventing lifecycle promotion.`
            }
          />
          <Card variant="outlined" data-testid="shape-required-artifacts">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Required shape artifacts</p>
              {requiredArtifacts.length > 0 ? (
                <div className="mt-3 space-y-2">
                  {requiredArtifacts.map((item) => (
                    <div key={item} className="flex items-center gap-2 text-sm text-fg" data-testid="shape-artifact">
                      <span className="inline-block h-2 w-2 rounded-full bg-info-color" aria-hidden="true" />
                      {item}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted">No additional shape artifacts are currently required.</p>
              )}
            </CardContent>
          </Card>
        </div>
        <Card variant="outlined" data-testid="shape-activity-summary">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent shaping activity</p>
            {activity.length > 0 ? (
              <div className="mt-3 space-y-2">
                {activity.slice(0, 3).map((event) => (
                  <div key={event.id} className="text-sm text-fg" data-testid="shape-activity-event">
                    {event.summary}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted">No recent shaping activity has been recorded.</p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

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
    const isReady = blockers.length === 0 && preview?.canAdvance === true;
    const recentGenerateActivity = activity.slice(0, 3);

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card variant="outlined" data-testid="codegen-preview-panel">
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Codegen readiness</p>
                <Badge
                  variant={isReady ? 'success' : 'secondary'}
                  data-testid="codegen-readiness-badge"
                >
                  {isReady ? 'Ready' : 'Not ready'}
                </Badge>
              </div>
              <p className="mt-2 text-sm text-fg-muted">
                {isReady
                  ? 'Readiness signal confirmed. Review the planned output bundle and advance to run.'
                  : `${blockers.length} blocker(s) must be resolved before generating artifacts.`}
              </p>
              {preview?.readiness != null && (
                <p className="mt-2 text-xs text-fg-muted" data-testid="codegen-readiness-score">
                  Readiness score: <span className="font-medium text-fg">{preview.readiness}%</span>
                </p>
              )}
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="generated-file-list">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Planned output bundle</p>
              {outputBundle.length > 0 ? (
                <div className="mt-3 space-y-2">
                  {outputBundle.map((item) => (
                    <div key={item} data-testid="generated-file" className="flex items-center gap-2 text-sm text-fg">
                      <span className="inline-block h-2 w-2 rounded-full bg-info-color" aria-hidden="true" />
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
        {recentGenerateActivity.length > 0 && (
          <Card variant="outlined" data-testid="generate-activity-timeline">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent generate activity</p>
              <div className="mt-3 space-y-2">
                {recentGenerateActivity.map((event) => (
                  <div key={event.id} className="flex items-start gap-2 text-sm">
                    <span className="mt-0.5 inline-block h-2 w-2 shrink-0 rounded-full bg-surface-muted" aria-hidden="true" />
                    <span className="text-fg">{event.summary}</span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
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
    const recentEvents = activity.slice(0, 5);

    return (
      <div className="space-y-4">
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
        {recentEvents.length > 0 && (
          <Card variant="outlined" data-testid="observe-signal-timeline">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent signal timeline</p>
              <div className="mt-3 space-y-3">
                {recentEvents.map((event) => (
                  <div key={event.id} className="flex items-start gap-3 text-sm" data-testid="signal-event">
                    <span
                      className={[
                        'mt-0.5 inline-block h-2 w-2 shrink-0 rounded-full',
                        event.severity === 'error' || event.success === false
                          ? 'bg-destructive'
                          : event.severity === 'warning'
                            ? 'bg-warning-color'
                            : 'bg-success-color',
                      ].join(' ')}
                      aria-hidden="true"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="text-fg">{event.summary}</p>
                      <p className="text-xs text-fg-muted">{event.source} · {event.action}</p>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
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
