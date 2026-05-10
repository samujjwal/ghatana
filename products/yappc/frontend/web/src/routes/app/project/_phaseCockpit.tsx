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
  usePhaseCockpitData,
} from '../../../services/phase';
import type {
  MountedPhase,
  RunPostAction,
} from '../../../services/phase';
import type { GenerateReviewDecision } from '@/lib/api/client';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { PhaseStatusPanels } from './PhaseStatusPanels';
import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { currentUserAtom } from '../../../stores/user.store';
import { Button } from '../../../components/ui/Button';
import type { PhaseActionResult } from '../../../services/phase';
import type { PhaseCockpitContract } from '../../../services/phase/PhaseCockpitContractBuilder';
import type { PhaseCockpitDataWarning } from '../../../services/phase/usePhaseCockpitData';


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

function PhaseContractSummary({ contract }: { readonly contract: PhaseCockpitContract | null }) {
  if (!contract) {
    return null;
  }

  const persistedActivityCount = contract.persisted.activity.length;
  const blockerCount = contract.derived.blockers.length;
  const evidenceCount = contract.derived.evidence.length;
  const governanceCount = contract.derived.governance.length;
  const suggestionCount = contract.suggested.actions.length;

  return (
    <section
      className="grid gap-3 rounded-2xl border border-border bg-surface-raised p-4 text-sm shadow-sm md:grid-cols-4"
      data-testid="phase-contract-summary"
      aria-label="Canonical phase contract summary"
    >
      <div data-testid="phase-contract-persisted">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Persisted</p>
        <p className="mt-2 font-medium text-fg">{contract.persisted.project.name}</p>
        <p className="mt-1 text-xs text-fg-muted">{persistedActivityCount} backed activity event(s)</p>
      </div>
      <div data-testid="phase-contract-derived">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Derived</p>
        <p className="mt-2 font-medium text-fg">
          {blockerCount} blocker(s), {evidenceCount} evidence item(s)
        </p>
        <p className="mt-1 text-xs text-fg-muted">{governanceCount} governance trace record(s)</p>
      </div>
      <div data-testid="phase-contract-suggested">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Suggested</p>
        <p className="mt-2 font-medium text-fg">{suggestionCount} ranked action(s)</p>
        <p className="mt-1 text-xs text-fg-muted">
          {contract.suggested.actions[0]?.title ?? 'No backend suggestion surfaced'}
        </p>
      </div>
      <div data-testid="phase-contract-review">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">Review</p>
        <p className="mt-2 font-medium text-fg">
          {contract.review.required ? 'Review required' : 'Ready without extra review'}
        </p>
        <p className="mt-1 text-xs text-fg-muted">
          {contract.review.reason ?? (contract.review.canAdvance ? 'Lifecycle preview can advance.' : 'Lifecycle preview is not ready.')}
        </p>
      </div>
    </section>
  );
}

function PhaseDataRecoveryPanel({ warnings }: { readonly warnings: readonly PhaseCockpitDataWarning[] }) {
  if (warnings.length === 0) {
    return null;
  }

  return (
    <section
      className="rounded-2xl border border-warning-border bg-warning-bg p-4 text-sm text-warning-color"
      data-testid="phase-data-recovery"
      aria-label="Phase cockpit partial data recovery"
    >
      <p className="font-semibold text-warning-color">Some cockpit data could not be refreshed.</p>
      <div className="mt-3 space-y-3">
        {warnings.map((warning) => (
          <div key={warning.source} className="rounded-xl border border-warning-border/70 bg-surface/70 p-3">
            <p className="font-medium text-fg" data-testid={`phase-data-warning-${warning.source}`}>
              {warning.title}
            </p>
            <p className="mt-1 text-xs text-fg-muted">{warning.message}</p>
            <Button
              type="button"
              variant="outline"
              tone="warning"
              size="small"
              className="mt-2 border-warning-border bg-warning-bg text-warning-color"
              onClick={warning.retry}
            >
              {warning.retryLabel}
            </Button>
          </div>
        ))}
      </div>
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

  const {
    config,
    projectQuery,
    activity,
    preview,
    blockers,
    evidence,
    governance,
    suggestions,
    contract,
    dataWarnings,
  } = usePhaseCockpitData({
    phase,
    projectId,
    workspaceId: currentWorkspaceId,
    onSuggestionAction: scrollToSupportingSurface,
  });

  const actionMutation = useMutation({
    mutationFn: executePhasePrimaryAction,
    onSuccess: (result) => {
      setActionResult(result);
      setActionError(null);
      setFeedback(null);
      void queryClient.invalidateQueries({ queryKey: ['project-activity', projectId] });
      if (result.kind === 'surface') {
        scrollToSupportingSurface();
      }
    },
    onError: (error) => {
      setActionResult(null);
      setActionError(describePhaseActionError(error));
      if (preview && !preview.canAdvance) {
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
      preview,
    });
  }, [
    actionMutation,
    currentUser?.tenantId,
    currentUser?.id,
    navigate,
    phase,
    preview,
    projectId,
  ]);

  const handleSecondaryAction = () => {
    setActionResult(null);
    setActionError(null);
    setFeedback(`Reviewing ${config.supportingTitle.toLowerCase()} for ${config.name}.`);
    scrollToSupportingSurface();
  };

  const handleSuggestionAction = useCallback((step: SuggestedStep) => {
    if (step.applyMode === 'one-click' && !step.approvalRequired) {
      handlePrimaryAction();
      return;
    }

    setFeedback(`Reviewing suggestion: ${step.title}.`);
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

  const project = projectQuery.data;
  const phaseDetailCopy = PHASE_DETAIL_COPY[phase];
  const cockpitSuggestions = suggestions.map((step) => ({
    ...step,
    onAccept: handleSuggestionAction,
  }));

  // TODO-008: Verify phase capability from backend
  if (project && project.capabilities) {
    const phaseCapability = project.capabilities[phase as keyof typeof project.capabilities];
    if (phaseCapability === false) {
      return (
        <div className="p-6">
          <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
            <h2 className="font-semibold">Phase access denied</h2>
            <p className="mt-1 text-sm">
              You do not have permission to access the {config.name} phase for this project.
              Please contact your workspace administrator or project owner for access.
            </p>
          </div>
        </div>
      );
    }
  }

  if (projectQuery.isLoading || !project) {
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

  const projectName = project.name ?? 'this project';
  const statusPanels = (
    <PhaseStatusPanels
      phase={phase}
      preview={preview}
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
        <h2 className="mt-2 text-lg font-semibold text-fg">{config.supportingTitle}</h2>
        <p className="mt-1 text-sm text-fg-muted">
          Review the existing detailed surface below when you need deeper context beyond the phase-native cockpit.
        </p>
      </div>
      <div className="mb-4 text-xs text-fg-muted">
        Last activity:{' '}
        {activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : 'No recent backed activity'}
      </div>
      <PhaseEmbeddedSurface phase={phase} />
    </div>
  );

  const isCtaDisabled = config.primaryLocked === true || actionMutation.isPending;
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
    : config.primaryLockedReason;

  return (
    <div className="p-6 space-y-6">
      <PhaseCockpitLayout
        testId={`${phase}-cockpit`}
        phaseName={config.name}
        phaseDescription={`${config.description} Project: ${projectName}.`}
        primaryAction={(
          <PhasePrimaryActionCard
            title={config.primaryTitle}
            description={config.primaryDescription}
            actionLabel={config.primaryLabel}
            onAction={handlePrimaryAction}
            secondaryActionLabel={isCtaDisabled ? 'View blockers' : config.secondaryLabel}
            onSecondaryAction={isCtaDisabled ? scrollToBlockerPanel : handleSecondaryAction}
            icon={resolvePhaseIcon(config.icon)}
            disabled={isCtaDisabled}
            disabledReason={
              disabledReason
                ? disabledReason
                : isCtaDisabled
                  ? `${blockers.length} blocker${blockers.length > 1 ? 's' : ''} must be resolved before continuing. Scroll down to review and resolve them.`
                : undefined
            }
            testId={`${phase}-primary-action-card`}
            actionTestId={config.primaryTestId}
            secondaryActionTestId={config.secondaryTestId}
            actionAriaLabel={`${config.name} primary action`}
          />
        )}
        blockers={<div id={`${phase}-blocker-panel`}><PhaseBlockerPanel blockers={blockers} /></div>}
        evidence={<PhaseEvidencePanel evidence={evidence} />}
        suggestedAutomation={<PhaseSuggestedNextStep steps={cockpitSuggestions} />}
        governanceTrace={<PhaseGovernanceTrace records={governance} />}
        advancedTools={advancedDetails}
        advancedToolsLabel={phaseDetailCopy.label}
        advancedToolsDescription={phaseDetailCopy.description}
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
          <PhaseDataRecoveryPanel warnings={dataWarnings} />
          <PhaseContractSummary contract={contract} />
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
