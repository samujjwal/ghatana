import { useAtomValue } from 'jotai';
import React, { useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';

import { useTranslation } from '@ghatana/i18n';

import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import { PhaseSuggestedNextStep } from '../../../components/phase/PhaseSuggestedNextStep';
import { Button } from '../../../components/ui/Button';
import { usePhasePacket } from '../../../hooks/usePhasePacket';
import {
  formatTimestamp,
  resolvePhaseIcon,
  type MountedPhase,
  type PhaseIconId,
  type RunPostAction,
} from '../../../services/phase';
import type { PhaseCockpitPacket } from '../../../types/phasePacket';

import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { PhaseStatusPanelsCanonical } from './PhaseStatusPanelsCanonical';
import { PhaseDegradedPacketPanel } from './PhaseDegradedPacketPanel';
import { PhasePacketErrorPanel } from './PhasePacketErrorPanel';
import { PhasePacketSummary } from './PhasePacketSummary';
import {
  AccessDeniedState,
  MissingProjectState,
  MissingWorkspaceState,
  PhaseRouteLoadingState,
} from './PhaseRouteGuards';
import {
  mapPacketActivity,
  mapPacketBlockers,
  mapPacketEvidence,
  mapPacketGovernance,
  mapPacketSuggestions,
} from './phasePacketMappers';
import { usePhaseActionHandlers, type PhaseCurrentUser } from './usePhaseActionHandlers';

import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import { currentUserAtom } from '@/stores/user.store';

interface PhaseDetailCopy {
  readonly labelKey: string;
  readonly descriptionKey: string;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function toPhaseCurrentUser(value: unknown): PhaseCurrentUser | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const candidate = value as Record<string, unknown>;
  const id = asString(candidate.id);
  if (!id) {
    return null;
  }

  return {
    id,
    tenantId: asString(candidate.tenantId),
    email: asString(candidate.email),
  };
}

const PHASE_DETAIL_COPY: Record<MountedPhase, PhaseDetailCopy> = {
  intent: {
    labelKey: 'phaseCockpit.detail.intent.label',
    descriptionKey: 'phaseCockpit.detail.intent.description',
  },
  shape: {
    labelKey: 'phaseCockpit.detail.shape.label',
    descriptionKey: 'phaseCockpit.detail.shape.description',
  },
  validate: {
    labelKey: 'phaseCockpit.detail.validate.label',
    descriptionKey: 'phaseCockpit.detail.validate.description',
  },
  generate: {
    labelKey: 'phaseCockpit.detail.generate.label',
    descriptionKey: 'phaseCockpit.detail.generate.description',
  },
  run: {
    labelKey: 'phaseCockpit.detail.run.label',
    descriptionKey: 'phaseCockpit.detail.run.description',
  },
  observe: {
    labelKey: 'phaseCockpit.detail.observe.label',
    descriptionKey: 'phaseCockpit.detail.observe.description',
  },
  learn: {
    labelKey: 'phaseCockpit.detail.learn.label',
    descriptionKey: 'phaseCockpit.detail.learn.description',
  },
  evolve: {
    labelKey: 'phaseCockpit.detail.evolve.label',
    descriptionKey: 'phaseCockpit.detail.evolve.description',
  },
};

const PRIMARY_ACTION_TEST_IDS: Partial<Record<MountedPhase, string>> = {
  intent: 'define-requirements',
  shape: 'add-components',
  validate: 'approve-changes',
  generate: 'generate-code',
  run: 'check-readiness',
};

const PHASE_ICON_IDS: Record<MountedPhase, PhaseIconId> = {
  intent: 'target',
  shape: 'layers',
  validate: 'check-circle',
  generate: 'code-2',
  run: 'play-circle',
  observe: 'eye',
  learn: 'lightbulb',
  evolve: 'arrow-up-right',
};

interface PhaseCockpitContainerProps {
  readonly phase: MountedPhase;
}

export function PhaseCockpitContainer({ phase }: PhaseCockpitContainerProps): React.ReactNode {
  const { t } = useTranslation('common');
  const { projectId } = useParams<{ projectId: string }>();
  const rawWorkspaceId = useAtomValue(currentWorkspaceIdAtom) as unknown;
  const currentWorkspaceId = asString(rawWorkspaceId);
  const navigate = useNavigate();
  const rawCurrentUser = useAtomValue(currentUserAtom) as unknown;
  const currentUser = toPhaseCurrentUser(rawCurrentUser);

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
    projectId: projectId ?? '',
    workspaceId: currentWorkspaceId ?? undefined,
  });

  const {
    feedback,
    actionResult,
    actionError,
    actionText,
    actionMutation,
    generateReviewMutation,
    runPostActionMutation,
    handlePrimaryAction,
    handleSecondaryAction,
    handleSuggestionAction,
    handleGenerateReviewDecision,
    handleRunPostAction,
  } = usePhaseActionHandlers({
    phase,
    projectId,
    packet,
    currentUser,
    t,
    navigate,
    refetch,
    scrollToSupportingSurface,
    scrollToBlockerPanel,
  });

  const phaseDetailCopy = PHASE_DETAIL_COPY[phase];
  const phaseDetailLabel = t(phaseDetailCopy.labelKey);
  const phaseDetailDescription = t(phaseDetailCopy.descriptionKey);

  if (!projectId) {
    return <MissingProjectState />;
  }

  if (!currentWorkspaceId) {
    return <MissingWorkspaceState />;
  }

  if (packet && !packet.capabilities.canRead) {
    return <AccessDeniedState phase={phase} />;
  }

  if (isLoading) {
    return <PhaseRouteLoadingState />;
  }

  if (!packet) {
    return (
      <div className="p-6">
        <PhasePacketErrorPanel
          error={error ?? new Error(t('phaseCockpit.error.unavailable'))}
          onRetry={() => {
            void refetch();
          }}
        />
      </div>
    );
  }

  const projectName = packet.projectName ?? t('phaseCockpit.fallback.thisProject');
  const blockers = mapPacketBlockers(packet);
  const evidence = mapPacketEvidence(packet);
  const governance = mapPacketGovernance(packet);
  const suggestions = mapPacketSuggestions(packet, actionText, handleSuggestionAction);
  const activity = mapPacketActivity(packet);

  const statusPanels = (
    <PhaseStatusPanelsCanonical
      phase={phase}
      phasePanels={packet.phasePanels ?? []}
    />
  );

  const primaryPacketAction = packet.availableActions.find(
    (action) => action.actionId === packet.dashboardActions.primaryAction,
  ) ?? packet.availableActions[0] ?? null;

  const isDependencyDegraded = packet.readiness.isDegraded || Boolean(packet.degradedDetails);
  const advancedDetails = (
    <div
      id={`${phase}-supporting-surface`}
      className="rounded-2xl border border-border bg-surface-raised p-4 shadow-sm"
    >
      <div className="mb-4">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
          {phaseDetailLabel}
        </p>
        <h2 className="mt-2 text-lg font-semibold text-fg">{t('phaseCockpit.detail.title')}</h2>
        <p className="mt-1 text-sm text-fg-muted">
          {t('phaseCockpit.detail.body')}
        </p>
      </div>
      <div className="mb-4 text-xs text-fg-muted">
        {t('phaseCockpit.detail.lastActivity')}{' '}
        {activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : t('phaseCockpit.detail.noRecentActivity')}
      </div>
      <PhaseEmbeddedSurface phase={phase} />
    </div>
  );

  const isCtaDisabled = !primaryPacketAction?.enabled || isDependencyDegraded || actionMutation.isPending;
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
    ? t('phaseCockpit.disabled.running')
    : primaryPacketAction?.disabledReason
      ? actionText(primaryPacketAction.disabledReason)
      : isDependencyDegraded
        ? packet.degradedDetails?.recoveryAction ?? t('phaseCockpit.disabled.degradedDependency')
        : !primaryPacketAction
          ? t('phaseCockpit.disabled.noBackendAction')
          : undefined;

  const canExecutePrimaryAction = Boolean(primaryPacketAction?.enabled) && !isDependencyDegraded;

  return (
    <div className="p-6 space-y-6">
      <PhaseCockpitLayout
        testId={`${phase}-cockpit`}
        phaseName={t(`phaseCockpit.phase.${phase}`)}
        phaseDescription={t('phaseCockpit.layout.description', { phase, projectName })}
        primaryAction={(
          <PhasePrimaryActionCard
            title={actionText(primaryPacketAction?.label) ?? t('phaseCockpit.primary.title', { phase })}
            description={actionText(primaryPacketAction?.description) ?? t('phaseCockpit.primary.description')}
            actionLabel={canExecutePrimaryAction ? actionText(primaryPacketAction?.label) ?? primaryPacketAction?.actionId ?? t('phaseCockpit.primary.viewBlockers') : t('phaseCockpit.primary.viewBlockers')}
            onAction={canExecutePrimaryAction ? handlePrimaryAction : scrollToBlockerPanel}
            secondaryActionLabel={t('phaseCockpit.primary.reviewDetails')}
            onSecondaryAction={handleSecondaryAction}
            icon={resolvePhaseIcon(PHASE_ICON_IDS[phase])}
            disabled={isCtaDisabled}
            disabledReason={disabledReason}
            testId={`${phase}-primary-action-card`}
            actionTestId={PRIMARY_ACTION_TEST_IDS[phase] ?? `${phase}-advance-action`}
            secondaryActionTestId={`${phase}-review-action`}
            actionAriaLabel={t('phaseCockpit.primary.aria', { phase })}
          />
        )}
        blockers={<div id={`${phase}-blocker-panel`}><PhaseBlockerPanel blockers={blockers} /></div>}
        evidence={<PhaseEvidencePanel evidence={evidence} />}
        suggestedAutomation={<PhaseSuggestedNextStep steps={suggestions} />}
        governanceTrace={<PhaseGovernanceTrace records={governance} />}
        advancedTools={advancedDetails}
        advancedToolsLabel={phaseDetailLabel}
        advancedToolsDescription={phaseDetailDescription}
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
          <PhasePacketErrorPanel
            error={error}
            onRetry={() => {
              void refetch();
            }}
          />
          <PhasePacketSummary packet={packet} />
          <PhaseDegradedPacketPanel details={packet.degradedDetails} />
          {feedback ? (
            <div className="rounded-xl border border-info-border bg-info-bg p-4 text-sm text-info-color">
              {feedback}
            </div>
          ) : null}
          <section
            className="grid gap-3 rounded-2xl border border-border bg-surface-raised p-4 text-sm shadow-sm md:grid-cols-4"
            data-testid="phase-contract-summary"
            aria-label={t('phaseCockpit.contract.aria')}
          >
            <div data-testid="phase-contract-persisted">{packet.projectName ?? t('phaseCockpit.fallback.project')}</div>
            <div data-testid="phase-contract-derived">{t('phaseCockpit.contract.evidenceCount', { count: packet.evidence.length })}</div>
            <div data-testid="phase-contract-suggested">
              {actionText(packet.availableActions[0]?.label) ?? t('phaseCockpit.contract.noSuggestedAction')}
            </div>
            <div data-testid="phase-contract-review">
              {packet.governance[0]?.outcome ?? t('phaseCockpit.contract.readyWithoutReview')}
            </div>
          </section>
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
              <p className="text-sm font-semibold text-fg">{t('phaseCockpit.generateReview.title')}</p>
              <p className="mt-1 text-xs text-fg-muted">
                {t('phaseCockpit.generateReview.description')}
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
                  {t('phaseCockpit.generateReview.apply')}
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
                  {t('phaseCockpit.generateReview.reject')}
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
                  {t('phaseCockpit.generateReview.rollback')}
                </Button>
              </div>
            </div>
          ) : null}
          {showRunPostActions ? (
            <div
              className="rounded-xl border border-border bg-surface-raised p-4"
              data-testid="run-post-actions"
            >
              <p className="text-sm font-semibold text-fg">{t('phaseCockpit.runPost.title')}</p>
              <p className="mt-1 text-xs text-fg-muted">
                {t('phaseCockpit.runPost.description')}
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  tone="warning"
                  size="small"
                  className="border-warning-border bg-warning-bg text-warning-color"
                  data-testid="run-retry"
                  disabled={isRunPostActionPending}
                  onClick={() => handleRunPostAction('retry')}
                >
                  {t('phaseCockpit.runPost.retry')}
                </Button>
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
                  {t('phaseCockpit.runPost.rollback')}
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
                  {t('phaseCockpit.runPost.promote')}
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
                  {t('phaseCockpit.runPost.observe')}
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
