import { useAtomValue } from 'jotai';
import React, { useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';

import { useTranslation } from '@ghatana/i18n';

import { usePhasePacket } from '../../../hooks/usePhasePacket';
import {
  formatTimestamp,
  resolvePhaseIcon,
  type MountedPhase,
  type PhaseIconId,
} from '../../../services/phase';
import type { PhaseAction } from '../../../types/phasePacket';

import {
  type PhaseActionSectionAction,
  type PhaseActionSectionGroup,
} from './PhaseActionSection';
import { PhaseCockpitView } from './PhaseCockpitView';
import { PhaseCurrentStateCard } from './PhaseCurrentStateCard';
import { PhaseStatusPanelsCanonical } from './PhaseStatusPanelsCanonical';
import { PhaseDegradedPacketPanel } from './PhaseDegradedPacketPanel';
import { PhasePacketErrorPanel } from './PhasePacketErrorPanel';
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

const RUN_CONTEXT_OPERATIONS = new Set([
  'generate.apply',
  'generate.reject',
  'generate.rollback',
  'generate.review.apply',
  'generate.review.reject',
  'generate.review.rollback',
  'run.retry',
  'run.rollback',
  'run.promote',
  'run.observe',
]);

function toTestId(actionId: string): string {
  if (actionId === 'run.observe') {
    return 'run-observe-handoff';
  }
  return actionId.replaceAll('.', '-');
}

function toSectionAction(
  action: PhaseAction,
  actionText: (key: string | undefined) => string | undefined,
  disabled: boolean,
  onClick: () => void,
): PhaseActionSectionAction {
  return {
    actionId: action.actionId,
    testId: toTestId(action.actionId),
    label: actionText(action.label) ?? action.actionId,
    severity: action.severity ?? 'default',
    disabled,
    onClick,
  };
}

function categorySectionMeta(
  category: string,
  t: (key: string, options?: Record<string, unknown>) => string,
): Pick<PhaseActionSectionGroup, 'testId' | 'title' | 'description'> {
  if (category === 'review') {
    return {
      testId: 'generate-review-actions',
      title: t('phaseCockpit.generateReview.title'),
      description: t('phaseCockpit.generateReview.description'),
    };
  }
  if (category === 'post-run') {
    return {
      testId: 'run-post-actions',
      title: t('phaseCockpit.runPost.title'),
      description: t('phaseCockpit.runPost.description'),
    };
  }

  const slug = category.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-');
  return {
    testId: `phase-actions-${slug}`,
    title: t('phaseCockpit.actionSections.title', { category }),
    description: t('phaseCockpit.actionSections.description', { category }),
  };
}

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
    isActionPending,
    handlePrimaryAction,
    handleSecondaryAction,
    handleSuggestionAction,
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
  const primaryNextActionLabel = actionText(primaryPacketAction?.label)
    ?? t('phaseCockpit.contract.noSuggestedAction');
  const governanceOutcome = packet.governance[0]?.outcome
    ?? t('phaseCockpit.contract.readyWithoutReview');

  // FE-06: Reduce fallback/action ambiguity - single source of truth for action availability
  const isActionAvailable = Boolean(primaryPacketAction?.enabled) && !isDependencyDegraded && !actionMutation.isPending;
  const primaryActionDisabledReason = actionMutation.isPending
    ? t('phaseCockpit.disabled.running')
    : primaryPacketAction?.disabledReason
      ? actionText(primaryPacketAction.disabledReason)
      : isDependencyDegraded
        ? packet.degradedDetails?.recoveryAction ?? t('phaseCockpit.disabled.degradedDependency')
        : !primaryPacketAction
          ? t('phaseCockpit.disabled.noBackendAction')
          : undefined;
  const primaryActionId = primaryPacketAction?.actionId ?? null;

  const actionSectionsMap = new Map<string, PhaseActionSectionGroup>();
  packet.availableActions
    .filter((action) => action.actionId !== primaryActionId)
    .forEach((action) => {
      const operation = action.serverOperation ?? action.actionId;
      // FE-05: Do not hide backend-provided run actions due to local run context
      // Backend now provides run context in packet.platformRunStatus, so we dont need to filter based on local runContextId

      const sectionMeta = categorySectionMeta(action.category ?? 'general', t);
      const existingActions = actionSectionsMap.get(sectionMeta.testId)?.actions ?? [];

      actionSectionsMap.set(sectionMeta.testId, {
        ...sectionMeta,
        actions: [
          ...existingActions,
          toSectionAction(action, actionText, isActionPending(action) || !action.enabled, () => {
            handleSuggestionAction(action);
          }),
        ],
      });
    });

  const actionSections: readonly PhaseActionSectionGroup[] = [
    ...actionSectionsMap.values(),
  ].filter((section) => section.actions.length > 0);

  return (
    <PhaseCockpitView
      phase={phase}
      phaseName={t(`phaseCockpit.phase.${phase}`)}
      phaseDescription={t('phaseCockpit.layout.description', { phase, projectName })}
      primaryTitle={actionText(primaryPacketAction?.label) ?? t('phaseCockpit.primary.title', { phase })}
      primaryDescription={actionText(primaryPacketAction?.description) ?? t('phaseCockpit.primary.description')}
      primaryActionLabel={isActionAvailable
        ? actionText(primaryPacketAction?.label) ?? primaryPacketAction?.actionId ?? t('phaseCockpit.primary.viewBlockers')
        : t('phaseCockpit.primary.viewBlockers')}
      primaryIcon={resolvePhaseIcon(PHASE_ICON_IDS[phase])}
      primaryActionTestId={PRIMARY_ACTION_TEST_IDS[phase] ?? `${phase}-advance-action`}
      secondaryActionLabel={t('phaseCockpit.primary.reviewDetails')}
      isPrimaryDisabled={!isActionAvailable}
      disabledReason={primaryActionDisabledReason}
      canExecutePrimaryAction={isActionAvailable}
      onPrimaryAction={handlePrimaryAction}
      onViewBlockers={scrollToBlockerPanel}
      onSecondaryAction={handleSecondaryAction}
      phaseDetailLabel={phaseDetailLabel}
      phaseDetailDescription={phaseDetailDescription}
      phaseDetailBody={t('phaseCockpit.detail.body')}
      phaseDetailTitle={t('phaseCockpit.detail.title')}
      phaseDetailLastActivityLabel={t('phaseCockpit.detail.lastActivity')}
      phaseDetailNoRecentActivityLabel={t('phaseCockpit.detail.noRecentActivity')}
      lastActivityTimestampLabel={activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : ''}
      blockers={blockers}
      evidence={evidence}
      governance={governance}
      suggestions={suggestions}
      statusPanels={statusPanels}
      error={error}
      onRetry={() => {
        void refetch();
      }}
      feedback={feedback}
      actionResultMessage={actionResult?.message ?? null}
      actionError={actionError}
      actionSections={actionSections}
      isDependencyDegraded={isDependencyDegraded}
      currentStateCard={(
        <PhaseCurrentStateCard
          packet={packet}
          primaryNextActionLabel={primaryNextActionLabel}
          governanceOutcome={governanceOutcome}
          isDependencyDegraded={isDependencyDegraded}
        />
      )}
      degradedDetails={<PhaseDegradedPacketPanel details={packet.degradedDetails} />}
      phasePanels={packet.phasePanels ?? []}
    />
  );
}
