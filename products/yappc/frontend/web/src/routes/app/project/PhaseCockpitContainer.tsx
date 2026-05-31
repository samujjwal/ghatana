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
import { usePhaseActionHandlers, type PhaseCurrentUser } from './usePhaseActionHandlers';
import { usePhaseCockpitViewModel } from './usePhaseCockpitViewModel';

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

  const {
    blockers,
    evidence,
    governance,
    suggestions,
    activity,
    primaryPacketAction,
    isDependencyDegraded,
    primaryNextActionLabel,
    governanceOutcome,
    isActionAvailable,
    primaryActionDisabledReason,
    actionSections,
  } = usePhaseCockpitViewModel({
    phase,
    packet,
    actionText,
    isActionPending,
    handleSuggestionAction,
    mutationPending: actionMutation.isPending,
    t,
  });

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

  const statusPanels = (
    <PhaseStatusPanelsCanonical
      phase={phase}
      phasePanels={packet.phasePanels ?? []}
    />
  );

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
      packet={packet}
    />
  );
}
