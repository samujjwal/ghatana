import React, { useCallback, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { useNavigate, useParams } from 'react-router';

import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import { PhaseSuggestedNextStep, type SuggestedStep } from '../../../components/phase/PhaseSuggestedNextStep';
import {
  describePhaseActionError,
  executeGenerateReviewDecision,
  executePhasePrimaryAction,
  executeRunPostAction,
  formatTimestamp,
  resolvePhaseIcon,
  type MountedPhase,
  type RunPostAction,
} from '../../../services/phase';
import { usePhasePacket } from '../../../hooks/usePhasePacket';
import type { PhaseCockpitPacket, PhaseAction } from '../../../types/phasePacket';
import type { GenerateReviewDecision } from '@/lib/api/client';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { PhaseStatusPanels } from './PhaseStatusPanels';
import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { currentUserAtom } from '../../../stores/user.store';
import { Button } from '../../../components/ui/Button';
import type { PhaseActionResult } from '../../../services/phase';


interface PhaseDetailCopy {
  readonly label: string;
  readonly description: string;
}

/** Human-readable disclosure copy for the supporting phase workspace. */
const PHASE_DETAIL_COPY: Record<MountedPhase, PhaseDetailCopy> = {
  intent: {
    label: 'Open intent notes workspace',
    description: 'Use this only when goals, users, or success criteria need more context than the cockpit summary shows.',
  },
  shape: {
    label: 'Open canvas editing workspace',
    description: 'Use this when you need direct canvas edits after reviewing shape readiness and blockers.',
  },
  validate: {
    label: 'Open validation evidence workspace',
    description: 'Use this when approval gates need deeper artifact, risk, or evidence review.',
  },
  generate: {
    label: 'Open generation artifact workspace',
    description: 'Use this when generated output needs artifact-level inspection or page-builder edits before review.',
  },
  run: {
    label: 'Open run execution notes',
    description: 'Use this when deployment posture or run handoff context needs more detail than the cockpit controls.',
  },
  observe: {
    label: 'Open live preview verification',
    description: 'Use this when preview behavior or runtime signals need direct visual verification.',
  },
  learn: {
    label: 'Open retrospective notes workspace',
    description: 'Use this when lessons, incidents, or reusable patterns need supporting notes.',
  },
  evolve: {
    label: 'Open evolution planning workspace',
    description: 'Use this when roadmap or backlog decisions need deeper planning context.',
  },
};

function PhasePacketSummary({ packet }: { readonly packet: PhaseCockpitPacket | null }) {
  if (!packet) {
    return null;
  }

  const activityCount = packet.activityFeed.length;
  const blockerCount = packet.blockers.length;
  const evidenceCount = packet.evidence.length;
  const governanceCount = packet.governance.length;
  const actionCount = packet.availableActions.length;

  return (
    <section
      className="grid gap-3 rounded-2xl border border-border bg-surface-raised p-4 text-sm shadow-sm md:grid-cols-4"
      data-testid="phase-packet-summary"
      aria-label="Canonical phase packet summary"
    >
      <div data-testid="phase-packet-context">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Context</p>
        <p className="mt-2 font-medium text-fg">{packet.projectName ?? 'Project'}</p>
        <p className="mt-1 text-xs text-fg-muted">{activityCount} activity event(s)</p>
      </div>
      <div data-testid="phase-packet-state">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">State</p>
        <p className="mt-2 font-medium text-fg">
          {blockerCount} blocker(s), {evidenceCount} evidence item(s)
        </p>
        <p className="mt-1 text-xs text-fg-muted">{governanceCount} governance record(s)</p>
      </div>
      <div data-testid="phase-packet-actions">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Actions</p>
        <p className="mt-2 font-medium text-fg">{actionCount} available action(s)</p>
        <p className="mt-1 text-xs text-fg-muted">
          {packet.dashboardActions.primaryAction ?? 'No primary action'}
        </p>
      </div>
      <div data-testid="phase-packet-readiness">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Readiness</p>
        <p className="mt-2 font-medium text-fg">
          {packet.readiness.canAdvance ? 'Can advance' : 'Blocked'}
        </p>
        <p className="mt-1 text-xs text-fg-muted">
          Score: {Math.round(packet.readiness.completenessScore * 100)}%
        </p>
      </div>
    </section>
  );
}

function PhasePacketErrorPanel({ error, onRetry }: { readonly error: Error | null; readonly onRetry: () => void }) {
  if (!error) {
    return null;
  }

  return (
    <section
      className="rounded-2xl border border-warning-border bg-warning-bg p-4 text-sm text-warning-color"
      data-testid="phase-packet-error"
      aria-label="Phase packet error"
    >
      <p className="font-semibold text-warning-color">Phase packet unavailable</p>
      <p className="mt-1 text-xs text-fg-muted">{error.message}</p>
      <Button
        type="button"
        variant="outline"
        tone="warning"
        size="small"
        className="mt-2 border-warning-border bg-warning-bg text-warning-color"
        onClick={onRetry}
      >
        Retry
      </Button>
    </section>
  );
}

function PhaseCockpitRoute({ phase }: { phase: MountedPhase }) {
  const { projectId } = useParams<{ projectId: string }>();
  const currentWorkspaceId = useAtomValue(currentWorkspaceIdAtom);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useAtomValue(currentUserAtom);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionResult, setActionResult] = useState<PhaseActionResult | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);

  // TODO-008: Verify scope and phase capability before allowing access
  if (!projectId) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">Project context required</h2>
          <p className="mt-1 text-sm">Project ID is missing from the URL. Please navigate to a valid project page.</p>
        </div>
      </div>
    );
  }

  if (!currentWorkspaceId) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">Workspace context required</h2>
          <p className="mt-1 text-sm">Workspace context is required to access this phase. Please select a workspace first.</p>
        </div>
      </div>
    );
  }

  const scrollToSupportingSurface = useCallback(() => {
    document.getElementById(`${phase}-supporting-surface`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  }, [phase]);

  const scrollToBlockerPanel = useCallback(() => {
    document.getElementById(`${phase}-blocker-panel`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  }, [phase]);

  const { packet, isLoading, error, refetch } = usePhasePacket({
    phase,
    projectId,
    workspaceId: currentWorkspaceId,
  });

  const actionMutation = useMutation({
    mutationFn: executePhasePrimaryAction,
    onSuccess: (result) => {
      setActionResult(result);
      setActionError(null);
      setFeedback(null);
      void queryClient.invalidateQueries({ queryKey: ['project-activity', projectId] });
      void refetch();
      if (result.kind === 'surface') {
        scrollToSupportingSurface();
      }
    },
    onError: (error) => {
      setActionResult(null);
      setActionError(describePhaseActionError(error));
      if (packet && !packet.readiness.canAdvance) {
        scrollToBlockerPanel();
      }
    },
  });

  const generateReviewMutation = useMutation({
    mutationFn: executeGenerateReviewDecision,
    onSuccess: (result) => {
      setActionResult(result);
      setActionError(null);
      setFeedback(null);
      void queryClient.invalidateQueries({ queryKey: ['project-activity', projectId] });
      void refetch();
    },
    onError: (error) => {
      setActionError(describePhaseActionError(error));
    },
  });

  const runPostActionMutation = useMutation({
    mutationFn: executeRunPostAction,
    onSuccess: (result) => {
      setActionResult(result);
      setActionError(null);
      setFeedback(null);
      void queryClient.invalidateQueries({ queryKey: ['project-activity', projectId] });
      void refetch();
      if (result.kind === 'navigate' && projectId) {
        void navigate(`/p/${projectId}/observe`);
      }
    },
    onError: (error) => {
      setActionError(describePhaseActionError(error));
    },
  });

  const handlePrimaryAction = useCallback(() => {
    setActionResult(null);
    setActionError(null);

    if (phase === 'intent' && projectId) {
      void navigate(`/p/${projectId}/intent?drawer=idea`);
      return;
    }

    if (!projectId) {
      setActionError('Missing project context for this phase action.');
      return;
    }

    actionMutation.mutate({
      phase,
      projectId,
      tenantId: currentUser?.tenantId,
      actorId: currentUser?.id,
      preview: packet ? {
        canAdvance: packet.readiness.canAdvance,
        readiness: Math.round(packet.readiness.completenessScore * 100),
      } as any : undefined,
    });
  }, [
    actionMutation,
    currentUser?.tenantId,
    currentUser?.id,
    navigate,
    phase,
    packet,
    projectId,
  ]);

  const handleSecondaryAction = () => {
    setActionResult(null);
    setActionError(null);
    setFeedback(`Reviewing phase details for ${phase}.`);
    scrollToSupportingSurface();
  };

  const handleSuggestionAction = useCallback((action: PhaseAction) => {
    if (action.enabled && !action.disabledReason) {
      handlePrimaryAction();
      return;
    }

    setFeedback(`Reviewing action: ${action.label}.`);
    scrollToSupportingSurface();
  }, [handlePrimaryAction, scrollToSupportingSurface]);

  const handleGenerateReviewDecision = (decision: GenerateReviewDecision) => {
    if (!projectId || !actionResult?.runId) {
      setActionError('Missing generation run context for review decision.');
      return;
    }
    if (!currentUser?.id) {
      setActionError('Generation review requires an authenticated reviewer.');
      return;
    }

    generateReviewMutation.mutate({
      projectId,
      runId: actionResult.runId,
      decision,
      actorId: currentUser.id,
      reason: `Reviewed from mounted Generate cockpit by ${currentUser.email ?? currentUser.id}.`,
    });
  };

  const handleRunPostAction = (action: RunPostAction) => {
    if (!projectId || !actionResult?.runId) {
      setActionError('Missing run context for this action.');
      return;
    }

    runPostActionMutation.mutate({
      projectId,
      runId: actionResult.runId,
      action,
    });
  };

  const phaseDetailCopy = PHASE_DETAIL_COPY[phase];

  // Check phase capability from packet
  if (packet && !packet.capabilities.canRead) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">Phase access denied</h2>
          <p className="mt-1 text-sm">
            You do not have permission to access the {phase} phase for this project.
            Please contact your workspace administrator or project owner for access.
          </p>
        </div>
      </div>
    );
  }

  if (isLoading || !packet) {
    return (
      <div className="p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-48 rounded bg-surface-muted" />
          <div className="h-28 rounded-xl bg-surface-muted" />
          <div className="grid gap-4 md:grid-cols-2">
            <div className="h-40 rounded-xl bg-surface-muted" />
            <div className="h-40 rounded-xl bg-surface-muted" />
          </div>
        </div>
      </div>
    );
  }

  const projectName = packet.projectName ?? 'this project';
  
  // Map packet blockers to component format
  const blockers = packet.blockers.map(b => ({
    id: b.id,
    title: b.title,
    severity: (b.severity === 'CRITICAL' ? 'critical' : b.severity === 'WARNING' ? 'medium' : 'low') as 'critical' | 'medium' | 'high' | 'low',
    description: b.description,
    source: b.type,
  }));
  
  // Map packet evidence to component format
  const evidence = packet.evidence.map(e => ({
    id: e.id,
    type: (e.type as 'metric' | 'log' | 'artifact' | 'observation' | 'recommendation') || 'observation',
    title: e.title,
    description: e.description,
    timestamp: new Date(e.timestamp).toISOString(),
    metadata: e.metadata,
  }));
  
  // Map packet governance to component format
  const governance = packet.governance.map(g => ({
    id: g.id,
    artifactId: g.id,
    action: g.type,
    actor: g.actor,
    source: (g.type as 'preview' | 'backed' | 'derived' | 'suggested' | 'unavailable') || 'derived',
    summary: g.outcome,
    timestamp: new Date(g.timestamp).toISOString(),
    decision: g.outcome,
  }));
  
  // Map packet actions to suggestions format
  const suggestions = packet.availableActions.map(a => ({
    id: a.actionId,
    title: a.label,
    type: 'suggestion' as const,
    label: a.label,
    description: a.description,
    confidence: 0.5,
    evidence: [] as any,
    action: () => handleSuggestionAction(a),
    priority: a.enabled ? 'high' : 'low',
    kind: 'suggestion' as const,
    enabled: a.enabled,
    metadata: a.parameters,
    applyMode: a.enabled ? 'one-click' as const : 'manual' as const,
    approvalRequired: !a.enabled,
  })) as any;
  
  // Map packet activity to component format
  const activity = packet.activityFeed.map(entry => ({
    id: entry.id,
    source: 'lifecycle' as const,
    action: entry.action,
    summary: entry.summary,
    timestamp: new Date(entry.timestamp).toISOString(),
    actor: entry.actor,
    severity: entry.severity as any,
    success: true,
  }));
  
  const statusPanels = (
    <PhaseStatusPanels
      phase={phase}
      preview={{
        canAdvance: packet.readiness.canAdvance,
        readiness: Math.round(packet.readiness.completenessScore * 100),
      } as any}
      blockers={blockers}
      activity={activity}
    />
  );
  const advancedDetails = (
    <div
      id={`${phase}-supporting-surface`}
      className="rounded-2xl border border-border bg-surface-raised p-4 shadow-sm"
    >
      <div className="mb-4">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
          {phaseDetailCopy.label}
        </p>
        <h2 className="mt-2 text-lg font-semibold text-fg">Phase Details</h2>
        <p className="mt-1 text-sm text-fg-muted">
          Review the existing detailed surface below when you need deeper context beyond the phase-native cockpit.
        </p>
      </div>
      <div className="mb-4 text-xs text-fg-muted">
        Last activity:{' '}
        {activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : 'No recent activity'}
      </div>
      <PhaseEmbeddedSurface phase={phase} />
    </div>
  );

  const isCtaDisabled = !packet.readiness.canAdvance || packet.blockers.length > 0 || actionMutation.isPending;
  const showGenerateReviewActions = phase === 'generate'
    && actionResult?.kind === 'generate-review'
    && Boolean(actionResult.runId)
    && actionResult.reviewRequired !== false;
  const isGenerateReviewPending = generateReviewMutation.isPending;
  const showRunPostActions = phase === 'run'
    && actionResult?.kind === 'run-workflow'
    && Boolean(actionResult.runId);
  const isRunPostActionPending = runPostActionMutation.isPending;
  const disabledReason = actionMutation.isPending
    ? 'The phase action is running. Keep this page open while the backend responds.'
    : packet.blockers.length > 0
      ? `${packet.blockers.length} blocker${packet.blockers.length > 1 ? 's' : ''} must be resolved before continuing. Scroll down to review and resolve them.`
      : undefined;

  if (isLoading || !packet) {
    return (
      <div className="p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-48 rounded bg-surface-muted" />
          <div className="h-28 rounded-xl bg-surface-muted" />
          <div className="grid gap-4 md:grid-cols-2">
            <div className="h-40 rounded-xl bg-surface-muted" />
            <div className="h-40 rounded-xl bg-surface-muted" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <PhaseCockpitLayout
        testId={`${phase}-cockpit`}
        phaseName={phase.charAt(0).toUpperCase() + phase.slice(1)}
        phaseDescription={`Phase cockpit for ${phase}. Project: ${projectName}.`}
        primaryAction={(
          <PhasePrimaryActionCard
            title={`Advance ${phase}`}
            description={packet.readiness.canAdvance 
              ? 'Proceed to the next phase of the lifecycle.' 
              : 'Resolve blockers to advance to the next phase.'}
            actionLabel={packet.readiness.canAdvance ? 'Advance' : 'View Blockers'}
            onAction={packet.readiness.canAdvance ? handlePrimaryAction : scrollToBlockerPanel}
            secondaryActionLabel="Review Details"
            onSecondaryAction={handleSecondaryAction}
            icon={resolvePhaseIcon(phase as any)}
            disabled={isCtaDisabled}
            disabledReason={disabledReason}
            testId={`${phase}-primary-action-card`}
            actionTestId={`${phase}-advance-action`}
            secondaryActionTestId={`${phase}-review-action`}
            actionAriaLabel={`${phase} primary action`}
          />
        )}
        blockers={<div id={`${phase}-blocker-panel`}><PhaseBlockerPanel blockers={blockers} /></div>}
        evidence={<PhaseEvidencePanel evidence={evidence} />}
        suggestedAutomation={<PhaseSuggestedNextStep steps={suggestions} />}
        governanceTrace={<PhaseGovernanceTrace records={governance} />}
        advancedTools={advancedDetails}
        advancedToolsLabel={phaseDetailCopy.label}
        advancedToolsDescription={phaseDetailCopy.description}
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
          <PhasePacketErrorPanel error={error} onRetry={refetch} />
          <PhasePacketSummary packet={packet} />
          {feedback ? (
            <div className="rounded-xl border border-info-border bg-info-bg p-4 text-sm text-info-color">
              {feedback}
            </div>
          ) : null}
          {actionResult ? (
            <div
              className="rounded-xl border border-success-border bg-success-bg p-4 text-sm text-success-color"
              data-testid="phase-action-result"
            >
              {actionResult.message}
            </div>
          ) : null}
          {showGenerateReviewActions ? (
            <div
              className="rounded-xl border border-border bg-surface-raised p-4"
              data-testid="generate-review-actions"
            >
              <p className="text-sm font-semibold text-fg">Review generated changes</p>
              <p className="mt-1 text-xs text-fg-muted">
                Apply approved diffs, reject unsafe changes, or roll back an already-applied generation run.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  tone="success"
                  size="small"
                  className="border-success-border bg-success-bg text-success-color"
                  data-testid="generate-apply"
                  disabled={isGenerateReviewPending}
                  onClick={() => handleGenerateReviewDecision('apply')}
                >
                  Apply
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  tone="warning"
                  size="small"
                  className="border-warning-border bg-warning-bg text-warning-color"
                  data-testid="generate-reject"
                  disabled={isGenerateReviewPending}
                  onClick={() => handleGenerateReviewDecision('reject')}
                >
                  Reject
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  tone="danger"
                  size="small"
                  className="border-destructive bg-destructive-bg text-destructive"
                  data-testid="generate-rollback"
                  disabled={isGenerateReviewPending}
                  onClick={() => handleGenerateReviewDecision('rollback')}
                >
                  Roll back
                </Button>
              </div>
            </div>
          ) : null}
          {showRunPostActions ? (
            <div
              className="rounded-xl border border-border bg-surface-raised p-4"
              data-testid="run-post-actions"
            >
              <p className="text-sm font-semibold text-fg">Post-run controls</p>
              <p className="mt-1 text-xs text-fg-muted">
                Promote a healthy run, roll back an unsafe deployment, or hand off the run to Observe for live signal review.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  tone="danger"
                  size="small"
                  className="border-destructive bg-destructive-bg text-destructive"
                  data-testid="run-rollback"
                  disabled={isRunPostActionPending}
                  onClick={() => handleRunPostAction('rollback')}
                >
                  Roll back
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  tone="success"
                  size="small"
                  className="border-success-border bg-success-bg text-success-color"
                  data-testid="run-promote"
                  disabled={isRunPostActionPending}
                  onClick={() => handleRunPostAction('promote')}
                >
                  Promote
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  tone="info"
                  size="small"
                  className="border-info-border bg-info-bg text-info-color"
                  data-testid="run-observe-handoff"
                  disabled={isRunPostActionPending}
                  onClick={() => handleRunPostAction('observe')}
                >
                  Hand off to Observe
                </Button>
              </div>
            </div>
          ) : null}
          {actionError ? (
            <div
              className="rounded-xl border border-destructive bg-destructive/10 p-4 text-sm text-destructive"
              data-testid="phase-action-error"
            >
              {actionError}
            </div>
          ) : null}
          {statusPanels}
        </div>
      </PhaseCockpitLayout>
    </div>
  );
}

export function IntentCockpitRoute() {
  return <PhaseCockpitRoute phase="intent" />;
}

export function ShapeCockpitRoute() {
  return <PhaseCockpitRoute phase="shape" />;
}

export function ValidateCockpitRoute() {
  return <PhaseCockpitRoute phase="validate" />;
}

export function GenerateCockpitRoute() {
  return <PhaseCockpitRoute phase="generate" />;
}

export function RunCockpitRoute() {
  return <PhaseCockpitRoute phase="run" />;
}

export function ObserveCockpitRoute() {
  return <PhaseCockpitRoute phase="observe" />;
}

export function LearnCockpitRoute() {
  return <PhaseCockpitRoute phase="learn" />;
}

export function EvolveCockpitRoute() {
  return <PhaseCockpitRoute phase="evolve" />;
}
