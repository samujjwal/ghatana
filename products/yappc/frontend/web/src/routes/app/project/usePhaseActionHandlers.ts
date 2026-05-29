import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import type { NavigateFunction } from 'react-router';

import {
  describePhaseActionError,
  executeGenerateReviewDecision,
  executePhasePrimaryAction,
  executeRunPostAction,
  type MountedPhase,
  type PhaseActionResult,
} from '../../../services/phase';
import type { PhaseAction, PhaseCockpitPacket } from '../../../types/phasePacket';

import { phasePacketToPreview } from './phasePacketMappers';

export interface PhaseCurrentUser {
  readonly id: string;
  readonly tenantId?: string;
  readonly email?: string;
}

type GenerateReviewDecision = 'apply' | 'reject' | 'rollback';
type RunPostDecision = 'retry' | 'rollback' | 'promote' | 'observe';

type TranslationFunction = (key: string, options?: Record<string, unknown>) => string;

interface UsePhaseActionHandlersParams {
  readonly phase: MountedPhase;
  readonly projectId?: string;
  readonly packet: PhaseCockpitPacket | null;
  readonly currentUser: PhaseCurrentUser | null;
  readonly t: TranslationFunction;
  readonly navigate: NavigateFunction;
  readonly refetch: () => Promise<unknown>;
  readonly scrollToSupportingSurface: () => void;
  readonly scrollToBlockerPanel: () => void;
}

const GENERATE_OPERATION_MAP: Record<string, GenerateReviewDecision> = {
  'generate.apply': 'apply',
  'generate.reject': 'reject',
  'generate.rollback': 'rollback',
  'generate.review.apply': 'apply',
  'generate.review.reject': 'reject',
  'generate.review.rollback': 'rollback',
};

const RUN_OPERATION_MAP: Record<string, RunPostDecision> = {
  'run.retry': 'retry',
  'run.rollback': 'rollback',
  'run.promote': 'promote',
  'run.observe': 'observe',
};


/**
 * Resolves the run context from multiple sources in priority order.
 *
 * Priority:
 * 1. action.parameters.runId (backend-provided run ID for this specific action)
 * 2. action.parameters.latestRunId (backend-provided latest run ID)
 * 3. packet.platformRunStatus?.runId (backend packet platform run status)
 * 4. actionResult?.runId (frontend action result fallback)
 *
 * This ensures that backend-provided run context is used when available,
 * allowing actions to work correctly after page refresh when the backend
 * packet contains the platform run status.
 */
function resolveRunContext(
  action: PhaseAction,
  packet: PhaseCockpitPacket | null,
  actionResult: PhaseActionResult | null,
): string | null {
  // Priority 1: Backend action parameter runId
  const actionRunId = action.parameters.runId as string | undefined;
  if (actionRunId) {
    return actionRunId;
  }

  // Priority 2: Backend action parameter latestRunId
  const latestRunId = action.parameters.latestRunId as string | undefined;
  if (latestRunId) {
    return latestRunId;
  }

  // Priority 3: Backend packet platform run status
  if (packet?.platformRunStatus?.runId) {
    return packet.platformRunStatus.runId;
  }

  // Priority 4: Frontend action result fallback
  if (actionResult?.runId) {
    return actionResult.runId;
  }

  return null;
}export function usePhaseActionHandlers({
  phase,
  projectId,
  packet,
  currentUser,
  t,
  navigate,
  refetch,
  scrollToSupportingSurface,
  scrollToBlockerPanel,
}: UsePhaseActionHandlersParams) {
  const queryClient = useQueryClient();
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionResult, setActionResult] = useState<PhaseActionResult | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const actionText = useCallback((value: string | undefined): string | undefined => {
    if (!value) {
      return undefined;
    }
    return value.startsWith('phaseAction.') ? t(value) : value;
  }, [t]);

  const resolveNavigationPath = useCallback((targetRoute: string | null | undefined): string | null => {
    if (!projectId) {
      return null;
    }
    if (targetRoute && targetRoute.startsWith('/')) {
      return targetRoute;
    }
    if (targetRoute && targetRoute.trim().length > 0) {
      return `/p/${projectId}/${targetRoute.replace(/^\/+/, '')}`;
    }
    return `/p/${projectId}/${phase}`;
  }, [phase, projectId]);

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

  const executeServerOperation = useCallback((action: PhaseAction): boolean => {
    const operation = action.serverOperation ?? action.actionId;
    const generateDecision = GENERATE_OPERATION_MAP[operation];
    if (generateDecision) {
      if (!projectId || !resolveRunContext(action, packet, actionResult)) {
        setActionError(t('phaseCockpit.errors.missingGenerationRunContext'));
        return true;
      }
      if (!currentUser?.id) {
        setActionError(t('phaseCockpit.errors.authenticatedReviewerRequired'));
        return true;
      }

      generateReviewMutation.mutate({
        projectId,
        runId: resolveRunContext(action, packet, actionResult) ?? '',
        decision: generateDecision,
        actorId: currentUser.id,
        reason: t('phaseCockpit.generateReview.reason', { reviewer: currentUser.email ?? currentUser.id }),
      });
      return true;
    }

    const runDecision = RUN_OPERATION_MAP[operation];
    if (runDecision) {
      if (!projectId || !resolveRunContext(action, packet, actionResult)) {
        setActionError(t('phaseCockpit.errors.missingRunContext'));
        return true;
      }

      runPostActionMutation.mutate({
        projectId,
        runId: resolveRunContext(action, packet, actionResult) ?? '',
        action: runDecision,
        targetVersion: action.parameters.targetVersion as string | undefined,
        targetEnvironment: action.parameters.targetEnvironment as string | undefined,
      });
      return true;
    }

    return false;
  }, [actionResult, currentUser?.email, currentUser?.id, generateReviewMutation, packet, projectId, runPostActionMutation, t]);

  const isActionPending = useCallback((action: PhaseAction): boolean => {
    const operation = action.serverOperation ?? action.actionId;
    if (operation in GENERATE_OPERATION_MAP) {
      return generateReviewMutation.isPending;
    }
    if (operation in RUN_OPERATION_MAP) {
      return runPostActionMutation.isPending;
    }
    return actionMutation.isPending;
  }, [actionMutation.isPending, generateReviewMutation.isPending, runPostActionMutation.isPending]);

  const executePacketAction = useCallback((action: PhaseAction) => {
    setActionResult(null);
    setActionError(null);

    if (!action.enabled) {
      // FE-04: Use backend disabled reason as user guidance
      const disabledReason = action.disabledReason || t('phaseCockpit.feedback.reviewingAction', { label: actionText(action.label) ?? action.actionId });
      setFeedback(disabledReason);
      scrollToSupportingSurface();
      return;
    }

    if (action.targetType === 'route') {
      const path = resolveNavigationPath(action.targetRoute);
      if (!path) {
        setActionError(t('phaseCockpit.errors.missingProjectContext'));
        return;
      }
      void navigate(path);
      return;
    }

    if (action.targetType === 'drawer') {
      const path = resolveNavigationPath(action.targetRoute);
      if (!path) {
        setActionError(t('phaseCockpit.errors.missingProjectContext'));
        return;
      }
      const drawer = action.targetDrawer ?? 'idea';
      void navigate(`${path}?drawer=${drawer}`);
      return;
    }

    if (!projectId) {
      setActionError(t('phaseCockpit.errors.missingProjectContext'));
      return;
    }

    if (action.requiresPreview && packet && (!packet.lifecyclePhase || !packet.readiness.nextPhase)) {
      setActionError(t('phaseCockpit.errors.lifecyclePreviewUnavailable'));
      return;
    }

    if (executeServerOperation(action)) {
      return;
    }

    actionMutation.mutate({
      phase,
      projectId,
      tenantId: currentUser?.tenantId,
      actorId: currentUser?.id,
      preview: null, // FE-03: Backend will build authoritative lifecycle preview
    });
  }, [actionMutation, actionText, currentUser?.id, currentUser?.tenantId, executeServerOperation, navigate, packet, phase, projectId, resolveNavigationPath, scrollToSupportingSurface, t]);

  const handlePrimaryAction = useCallback(() => {
    const primaryAction = packet?.availableActions.find(
      (action) => action.actionId === packet.dashboardActions.primaryAction,
    ) ?? packet?.availableActions[0];

    if (!primaryAction) {
      setActionError(t('phaseCockpit.errors.missingProjectContext'));
      return;
    }

    executePacketAction(primaryAction);
  }, [executePacketAction, packet, t]);

  const handleSecondaryAction = useCallback(() => {
    setActionResult(null);
    setActionError(null);
    setFeedback(t('phaseCockpit.feedback.reviewingPhase', { phase }));
    scrollToSupportingSurface();
  }, [phase, scrollToSupportingSurface, t]);

  const handleSuggestionAction = useCallback((action: PhaseAction) => {
    if (action.enabled && !action.disabledReason) {
      executePacketAction(action);
      return;
    }

    // FE-04: Use backend disabled reason as user guidance
    const disabledReason = action.disabledReason || t('phaseCockpit.feedback.reviewingAction', { label: actionText(action.label) ?? action.actionId });
    setFeedback(disabledReason);
    scrollToSupportingSurface();
  }, [actionText, executePacketAction, scrollToSupportingSurface, t]);

  return {
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
  };
}
