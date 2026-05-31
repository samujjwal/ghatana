import { useMemo } from 'react';

import {
  mapPacketActivity,
  mapPacketBlockers,
  mapPacketEvidence,
  mapPacketGovernance,
  mapPacketSuggestions,
} from './phasePacketMappers';
import type {
  PhaseActionSectionAction,
  PhaseActionSectionGroup,
} from './PhaseActionSection';
import type { MountedPhase } from '../../../services/phase';
import type { PhaseAction, PhaseCockpitPacket } from '../../../types/phasePacket';

interface UsePhaseCockpitViewModelParams {
  readonly phase: MountedPhase;
  readonly packet: PhaseCockpitPacket | null;
  readonly actionText: (value: string | undefined) => string | undefined;
  readonly isActionPending: (action: PhaseAction) => boolean;
  readonly handleSuggestionAction: (action: PhaseAction) => void;
  readonly mutationPending: boolean;
  readonly t: (key: string, options?: Record<string, unknown>) => string;
}

interface UsePhaseCockpitViewModelResult {
  readonly blockers: ReturnType<typeof mapPacketBlockers>;
  readonly evidence: ReturnType<typeof mapPacketEvidence>;
  readonly governance: ReturnType<typeof mapPacketGovernance>;
  readonly suggestions: ReturnType<typeof mapPacketSuggestions>;
  readonly activity: ReturnType<typeof mapPacketActivity>;
  readonly primaryPacketAction: PhaseAction | null;
  readonly isDependencyDegraded: boolean;
  readonly primaryNextActionLabel: string;
  readonly governanceOutcome: string;
  readonly isActionAvailable: boolean;
  readonly primaryActionDisabledReason: string | undefined;
  readonly actionSections: readonly PhaseActionSectionGroup[];
}

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

export function usePhaseCockpitViewModel({
  phase,
  packet,
  actionText,
  isActionPending,
  handleSuggestionAction,
  mutationPending,
  t,
}: UsePhaseCockpitViewModelParams): UsePhaseCockpitViewModelResult {
  return useMemo(() => {
    if (!packet) {
      return {
        blockers: [],
        evidence: [],
        governance: [],
        suggestions: [],
        activity: [],
        primaryPacketAction: null,
        isDependencyDegraded: false,
        primaryNextActionLabel: t('phaseCockpit.contract.noSuggestedAction'),
        governanceOutcome: t('phaseCockpit.contract.readyWithoutReview'),
        isActionAvailable: false,
        primaryActionDisabledReason: t('phaseCockpit.disabled.noBackendAction'),
        actionSections: [],
      };
    }

    const blockers = mapPacketBlockers(packet);
    const evidence = mapPacketEvidence(packet);
    const governance = mapPacketGovernance(packet);
    const suggestions = mapPacketSuggestions(packet, actionText, handleSuggestionAction);
    const activity = mapPacketActivity(packet);

    const primaryPacketAction = packet.availableActions.find(
      (action) => action.actionId === packet.dashboardActions.primaryAction,
    ) ?? packet.availableActions[0] ?? null;

    const isDependencyDegraded = packet.readiness.isDegraded || Boolean(packet.degradedDetails);
    const primaryNextActionLabel = actionText(primaryPacketAction?.label)
      ?? t('phaseCockpit.contract.noSuggestedAction');
    const governanceOutcome = packet.governance[0]?.outcome
      ?? t('phaseCockpit.contract.readyWithoutReview');

    const isActionAvailable = Boolean(primaryPacketAction?.enabled) && !isDependencyDegraded && !mutationPending;
    const primaryActionDisabledReason = mutationPending
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

    return {
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
    };
  }, [actionText, handleSuggestionAction, isActionPending, mutationPending, packet, t, phase]);
}
