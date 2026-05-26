import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import React, { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { useTranslation } from '@ghatana/i18n';


import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import type { Blocker } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import { PhaseSuggestedNextStep, type SuggestedStep } from '../../../components/phase/PhaseSuggestedNextStep';
import { Button } from '../../../components/ui/Button';
import { usePhasePacket } from '../../../hooks/usePhasePacket';
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
import type {
  PhaseActionResult,
  PhaseActivityEvent,
  PhaseIconId,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';
import { currentUserAtom } from '../../../stores/user.store';
import type { PhaseCockpitPacket, PhaseAction } from '../../../types/phasePacket';

import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';
import { PhaseStatusPanels } from './PhaseStatusPanels';

import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';



interface PhaseDetailCopy {
  readonly labelKey: string;
  readonly descriptionKey: string;
}

interface PhaseCurrentUser {
  readonly id: string;
  readonly tenantId?: string;
  readonly email?: string;
}

type GenerateReviewDecision = 'apply' | 'reject' | 'rollback';

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

function phaseBlockerSeverity(severity: string): Blocker['severity'] {
  if (severity === 'CRITICAL') {
    return 'critical';
  }
  if (severity === 'ERROR' || severity === 'HIGH') {
    return 'high';
  }
  if (severity === 'WARNING' || severity === 'MEDIUM') {
    return 'medium';
  }
  return 'low';
}

function phasePacketToPreview(packet: PhaseCockpitPacket): PhaseTransitionPreviewSnapshot {
  return {
    projectId: packet.projectId,
    currentPhase: packet.lifecyclePhase ?? '',
    nextPhase: packet.readiness.nextPhase ?? null,
    canAdvance: packet.readiness.canAdvance,
    readiness: Math.round(packet.readiness.completenessScore * 100),
    blockers: packet.blockers.map((blocker) => blocker.title),
    requiredArtifacts: packet.requiredArtifacts.map((artifact) => artifact.title),
    completedArtifacts: packet.completedArtifacts.map((artifact) => artifact.title),
    estimatedReadyIn: null,
    estimatedReadyInHours: null,
    predictionConfidence: null,
    checkedAt: new Date(packet.timestamp).toISOString(),
  };
}

function PhasePacketSummary({ packet }: { readonly packet: PhaseCockpitPacket | null }) {
  const { t } = useTranslation('common');
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
      aria-label={t('phaseCockpit.summary.aria')}
    >
      <div data-testid="phase-packet-context">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.context')}</p>
        <p className="mt-2 font-medium text-fg">{packet.projectName ?? t('phaseCockpit.fallback.project')}</p>
        <p className="mt-1 text-xs text-fg-muted">{t('phaseCockpit.summary.activityCount', { count: activityCount })}</p>
      </div>
      <div data-testid="phase-packet-state">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.state')}</p>
        <p className="mt-2 font-medium text-fg">
          {t('phaseCockpit.summary.stateCounts', { blockers: blockerCount, evidence: evidenceCount })}
        </p>
        <p className="mt-1 text-xs text-fg-muted">{t('phaseCockpit.summary.governanceCount', { count: governanceCount })}</p>
      </div>
      <div data-testid="phase-packet-actions">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.actions')}</p>
        <p className="mt-2 font-medium text-fg">{t('phaseCockpit.summary.actionCount', { count: actionCount })}</p>
        <p className="mt-1 text-xs text-fg-muted">
          {packet.dashboardActions.primaryAction ?? t('phaseCockpit.summary.noPrimaryAction')}
        </p>
      </div>
      <div data-testid="phase-packet-readiness">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">{t('phaseCockpit.summary.readiness')}</p>
        <p className="mt-2 font-medium text-fg">
          {packet.readiness.canAdvance ? t('phaseCockpit.summary.canAdvance') : t('phaseCockpit.summary.blocked')}
        </p>
        <p className="mt-1 text-xs text-fg-muted">
          {t('phaseCockpit.summary.score', { score: Math.round(packet.readiness.completenessScore * 100) })}
        </p>
      </div>
    </section>
  );
}

function PhasePacketErrorPanel({ error, onRetry }: { readonly error: Error | null; readonly onRetry: () => void }) {
  const { t } = useTranslation('common');
  if (!error) {
    return null;
  }

  return (
    <section
      className="rounded-2xl border border-warning-border bg-warning-bg p-4 text-sm text-warning-color"
      data-testid="phase-packet-error"
      aria-label={t('phaseCockpit.error.aria')}
    >
      <p className="font-semibold text-warning-color">{t('phaseCockpit.error.title')}</p>
      <p className="mt-1 text-xs text-fg-muted">{error.message}</p>
      <Button
        type="button"
        variant="outline"
        tone="warning"
        size="small"
        className="mt-2 border-warning-border bg-warning-bg text-warning-color"
        onClick={onRetry}
      >
        {t('phaseCockpit.error.retry')}
      </Button>
    </section>
  );
}

function PhaseCockpitRoute({ phase }: { phase: MountedPhase }) {
  const { t } = useTranslation('common');
  const { projectId } = useParams<{ projectId: string }>();
  const rawWorkspaceId = useAtomValue(currentWorkspaceIdAtom) as unknown;
  const currentWorkspaceId = asString(rawWorkspaceId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const rawCurrentUser = useAtomValue(currentUserAtom) as unknown;
  const currentUser = toPhaseCurrentUser(rawCurrentUser);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionResult, setActionResult] = useState<PhaseActionResult | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const actionText = useCallback((value: string | undefined): string | undefined => {
    if (!value) {
      return undefined;
    }
    return value.startsWith('phaseAction.') ? t(value) : value;
  }, [t]);

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
      setActionError(t('phaseCockpit.errors.missingProjectContext'));
      return;
    }

    if (phase === 'validate' && packet && (!packet.lifecyclePhase || !packet.readiness.nextPhase)) {
      setActionError(t('phaseCockpit.errors.lifecyclePreviewUnavailable'));
      return;
    }

    actionMutation.mutate({
      phase,
      projectId,
      tenantId: currentUser?.tenantId,
      actorId: currentUser?.id,
      preview: packet ? phasePacketToPreview(packet) : null,
    });
  }, [
    actionMutation,
    currentUser?.tenantId,
    currentUser?.id,
    navigate,
    phase,
    packet,
    projectId,
    t,
  ]);

  const handleSecondaryAction = () => {
    setActionResult(null);
    setActionError(null);
    setFeedback(t('phaseCockpit.feedback.reviewingPhase', { phase }));
    scrollToSupportingSurface();
  };

  const handleSuggestionAction = useCallback((action: PhaseAction) => {
    if (action.enabled && !action.disabledReason) {
      handlePrimaryAction();
      return;
    }

    setFeedback(t('phaseCockpit.feedback.reviewingAction', { label: actionText(action.label) ?? action.actionId }));
    scrollToSupportingSurface();
  }, [handlePrimaryAction, scrollToSupportingSurface, t]);

  const handleGenerateReviewDecision = (decision: GenerateReviewDecision) => {
    if (!projectId || !actionResult?.runId) {
      setActionError(t('phaseCockpit.errors.missingGenerationRunContext'));
      return;
    }
    if (!currentUser?.id) {
      setActionError(t('phaseCockpit.errors.authenticatedReviewerRequired'));
      return;
    }

    generateReviewMutation.mutate({
      projectId,
      runId: actionResult.runId,
      decision,
      actorId: currentUser.id,
      reason: t('phaseCockpit.generateReview.reason', { reviewer: currentUser.email ?? currentUser.id }),
    });
  };

  const handleRunPostAction = (action: RunPostAction) => {
    if (!projectId || !actionResult?.runId) {
      setActionError(t('phaseCockpit.errors.missingRunContext'));
      return;
    }

    runPostActionMutation.mutate({
      projectId,
      runId: actionResult.runId,
      action,
    });
  };

  const phaseDetailCopy = PHASE_DETAIL_COPY[phase];
  const phaseDetailLabel = t(phaseDetailCopy.labelKey);
  const phaseDetailDescription = t(phaseDetailCopy.descriptionKey);

  // TRACK-008: Verify scope and phase capability before allowing access.
  // Keep this after hooks so async workspace hydration cannot change hook order.
  if (!projectId) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">{t('phaseCockpit.errors.projectContextTitle')}</h2>
          <p className="mt-1 text-sm">{t('phaseCockpit.errors.projectContextBody')}</p>
        </div>
      </div>
    );
  }

  if (!currentWorkspaceId) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">{t('phaseCockpit.errors.workspaceContextTitle')}</h2>
          <p className="mt-1 text-sm">{t('phaseCockpit.errors.workspaceContextBody')}</p>
        </div>
      </div>
    );
  }

  // Check phase capability from packet
  if (packet && !packet.capabilities.canRead) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive bg-destructive/10 p-4 text-destructive">
          <h2 className="font-semibold">{t('phaseCockpit.errors.accessDeniedTitle')}</h2>
          <p className="mt-1 text-sm">
            {t('phaseCockpit.errors.accessDeniedBody', { phase })}
          </p>
        </div>
      </div>
    );
  }

  if (isLoading) {
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
  const preview = phasePacketToPreview(packet);
  
  // Map packet blockers to component format
  const blockers = packet.blockers.map(b => ({
    id: b.id,
    title: b.title,
    severity: phaseBlockerSeverity(b.severity),
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
  const suggestions: SuggestedStep[] = packet.availableActions.map(a => ({
    id: a.actionId,
    title: actionText(a.label) ?? a.actionId,
    type: a.enabled ? 'automation' : 'review',
    description: actionText(a.description) ?? '',
    confidence: 0.5,
    evidence: [],
    riskLevel: a.enabled ? 'low' : 'medium',
    applyMode: a.enabled ? 'one-click' : 'manual',
    approvalRequired: !a.enabled,
    rollbackSupported: false,
    onAccept: () => handleSuggestionAction(a),
  }));
  
  // Map packet activity to component format
  const activity: PhaseActivityEvent[] = packet.activityFeed.map(entry => ({
    id: entry.id,
    source: 'lifecycle',
    action: entry.action,
    summary: entry.summary,
    timestamp: new Date(entry.timestamp).toISOString(),
    actor: entry.actor,
    severity: entry.severity,
    success: true,
  }));
  
  const statusPanels = (
    <PhaseStatusPanels
      phase={phase}
      preview={preview}
      previewHealth={packet.healthSignals.preview}
      agentGovernance={packet.healthSignals.agentGovernance}
      blockers={blockers}
      activity={activity}
    />
  );
  const primaryPacketAction = packet.availableActions.find(
    (action) => action.actionId === packet.dashboardActions.primaryAction
  ) ?? packet.availableActions[0] ?? null;
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

  const isCtaDisabled = !primaryPacketAction?.enabled
    || actionMutation.isPending;
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
    : !primaryPacketAction
      ? t('phaseCockpit.disabled.noBackendAction')
      : undefined;

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
            actionLabel={primaryPacketAction?.enabled ? actionText(primaryPacketAction.label) ?? primaryPacketAction.actionId : t('phaseCockpit.primary.viewBlockers')}
            onAction={primaryPacketAction?.enabled ? handlePrimaryAction : scrollToBlockerPanel}
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
