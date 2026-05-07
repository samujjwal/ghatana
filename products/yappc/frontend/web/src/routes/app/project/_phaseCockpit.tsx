import React, { useCallback, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { useNavigate, useParams } from 'react-router';

import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import { PhaseSuggestedNextStep } from '../../../components/phase/PhaseSuggestedNextStep';
import {
  describePhaseActionError,
  executePhasePrimaryAction,
  formatTimestamp,
  resolvePhaseIcon,
  usePhaseCockpitData,
} from '../../../services/phase';
import type {
  MountedPhase,
} from '../../../services/phase';
import { PhaseStatusPanels } from './PhaseStatusPanels';
import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { currentUserAtom } from '../../../stores/user.store';
import type { PhaseActionResult } from '../../../services/phase';


/** Human-readable label for the advanced detail panel, per phase. */
const PHASE_DETAIL_LABELS: Record<MountedPhase, string> = {
  intent: 'Intent exploration reference',
  shape: 'Shape configuration reference',
  validate: 'Validation reference',
  generate: 'Generation reference',
  run: 'Run configuration reference',
  observe: 'Observation signals reference',
  learn: 'Retrospective reference',
  evolve: 'Evolution planning reference',
};

function PhaseCockpitRoute({ phase }: { phase: MountedPhase }) {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useAtomValue(currentUserAtom);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionResult, setActionResult] = useState<PhaseActionResult | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

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
  } = usePhaseCockpitData({
    phase,
    projectId,
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

    if (phase === 'generate' || phase === 'run') {
      actionMutation.mutate({
        phase,
        projectId,
        tenantId: currentUser?.tenantId,
        preview,
      });
      return;
    }

    setFeedback(config.actionFeedback);
    scrollToSupportingSurface();
  }, [
    actionMutation,
    config.actionFeedback,
    currentUser?.tenantId,
    navigate,
    phase,
    preview,
    projectId,
    scrollToSupportingSurface,
  ]);

  const handleSecondaryAction = () => {
    setActionResult(null);
    setActionError(null);
    setFeedback(`Reviewing ${config.supportingTitle.toLowerCase()} for ${config.name}.`);
    scrollToSupportingSurface();
  };

  const project = projectQuery.data;

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
          {PHASE_DETAIL_LABELS[phase]}
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
        suggestedAutomation={<PhaseSuggestedNextStep steps={suggestions} />}
        governanceTrace={<PhaseGovernanceTrace records={governance} />}
        advancedTools={advancedDetails}
        advancedToolsLabel={PHASE_DETAIL_LABELS[phase]}
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
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
