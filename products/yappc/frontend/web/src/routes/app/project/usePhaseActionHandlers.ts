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
  type RunPostAction,
} from '../../../services/phase';
import type { PhaseAction, PhaseCockpitPacket } from '../../../types/phasePacket';

import { phasePacketToPreview } from './phasePacketMappers';

export interface PhaseCurrentUser {
  readonly id: string;
  readonly tenantId?: string;
  readonly email?: string;
}

export type GenerateReviewDecision = 'apply' | 'reject' | 'rollback';

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

export function usePhaseActionHandlers({
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
  }, [actionMutation, currentUser?.id, currentUser?.tenantId, navigate, packet, phase, projectId, t]);

  const handleSecondaryAction = useCallback(() => {
    setActionResult(null);
    setActionError(null);
    setFeedback(t('phaseCockpit.feedback.reviewingPhase', { phase }));
    scrollToSupportingSurface();
  }, [phase, scrollToSupportingSurface, t]);

  const handleSuggestionAction = useCallback((action: PhaseAction) => {
    if (action.enabled && !action.disabledReason) {
      handlePrimaryAction();
      return;
    }

    setFeedback(t('phaseCockpit.feedback.reviewingAction', { label: actionText(action.label) ?? action.actionId }));
    scrollToSupportingSurface();
  }, [actionText, handlePrimaryAction, scrollToSupportingSurface, t]);

  const handleGenerateReviewDecision = useCallback((decision: GenerateReviewDecision) => {
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
  }, [actionResult?.runId, currentUser?.email, currentUser?.id, generateReviewMutation, projectId, t]);

  const handleRunPostAction = useCallback((action: RunPostAction) => {
    if (!projectId || !actionResult?.runId) {
      setActionError(t('phaseCockpit.errors.missingRunContext'));
      return;
    }

    runPostActionMutation.mutate({
      projectId,
      runId: actionResult.runId,
      action,
    });
  }, [actionResult?.runId, projectId, runPostActionMutation, t]);

  return {
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
  };
}
