/**
 * useLifecycleTransition Hook
 *
 * Orchestrates the lifecycle transition workflow:
 * - Approval → AEP submit → audit → live update
 *
 * @doc.type hook
 * @doc.purpose Orchestrates lifecycle phase transitions with approval, AEP submission, and audit
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useState } from 'react';
import type { LifecyclePhase } from '@/types/lifecycle';
import type { LifecycleAutomationPlanRequest } from '@/services/lifecycle/api';

type ApprovalDecisionStatus =
    | 'PENDING'
    | 'APPROVED'
    | 'REJECTED'
    | 'CHANGES_REQUESTED'
    | 'EXPIRED';

interface ApprovalRecord {
    id: string;
    projectId: string;
    requirementId?: string;
    requestedAction: string;
    status: ApprovalDecisionStatus;
    requesterId: string;
    reviewerId?: string;
    createdAt: string;
    reviewedAt?: string;
    decisionReason?: string;
}

interface UseLifecycleTransitionParams {
    projectId: string;
    submitOrchestration: (params: {
        projectId: string;
        requirementId: string;
        approvalId: string;
    }) => void;
    applyAutomationPlan: {
        mutateAsync: (params: { projectId: string; request: LifecycleAutomationPlanRequest }) => Promise<unknown>;
        isPending: boolean;
    };
    executeTask: {
        mutateAsync: (params: { taskId: string; input: Record<string, unknown> }) => Promise<unknown>;
        isPending: boolean;
    };
    currentPhase: LifecyclePhase;
}

interface LifecycleTransitionState {
    automationFeedback: string;
    isTransitioning: boolean;
}

export function useLifecycleTransition(params: UseLifecycleTransitionParams) {
    const { projectId, submitOrchestration, applyAutomationPlan, executeTask, currentPhase } = params;
    const [state, setState] = useState<LifecycleTransitionState>({
        automationFeedback: '',
        isTransitioning: false,
    });

    const handleApprovalTransition = useCallback(
        (
            approvalRecords: ApprovalRecord[],
            setApprovalRecords: React.Dispatch<React.SetStateAction<ApprovalRecord[]>>,
            approvalId: string,
            nextStatus: ApprovalDecisionStatus
        ) => {
            const reviewedAt = new Date().toISOString();
            let approvedRecord: ApprovalRecord | undefined;

            setApprovalRecords((currentRecords) =>
                currentRecords.map((approval) => {
                    if (approval.id !== approvalId) {
                        return approval;
                    }

                    const reason =
                        nextStatus === 'APPROVED'
                            ? 'Approved from lifecycle review panel.'
                            : nextStatus === 'REJECTED'
                              ? 'Rejected from lifecycle review panel.'
                              : 'Requested changes from lifecycle review panel.';

                    const updated = {
                        ...approval,
                        status: nextStatus,
                        reviewerId: 'reviewer:lifecycle-owner',
                        reviewedAt,
                        decisionReason: reason,
                    };

                    if (nextStatus === 'APPROVED') {
                        approvedRecord = updated;
                    }

                    return updated;
                })
            );

            // When a requirement is approved, notify the AEP orchestration agent so
            // it can trigger downstream refinement, versioning, and audit workflows.
            if (nextStatus === 'APPROVED' && approvedRecord?.requirementId) {
                submitOrchestration({
                    projectId,
                    requirementId: approvedRecord.requirementId,
                    approvalId,
                });
            }
        },
        [projectId, submitOrchestration]
    );

    const handleOneClickApproval = useCallback(
        async () => {
            setState((prev) => ({ ...prev, isTransitioning: true }));

            try {
                const result = await applyAutomationPlan.mutateAsync({
                    projectId,
                    request: {
                        phase: currentPhase,
                        oneClickApprove: true,
                        reason: 'Applied from lifecycle route decision support panel',
                    },
                });

                if (result && typeof result === 'object' && 'execution' in result && result.execution && typeof result.execution === 'object' && 'transitioned' in result.execution && result.execution.transitioned) {
                    const execution = result.execution as { transitioned: boolean; previousPhase: string; currentPhase: string };
                    setState((prev) => ({
                        ...prev,
                        automationFeedback: `Guided promotion transitioned ${execution.previousPhase} to ${execution.currentPhase}.`,
                    }));
                } else if (result && typeof result === 'object' && 'canAutoAdvance' in result && result.canAutoAdvance) {
                    setState((prev) => ({
                        ...prev,
                        automationFeedback: 'Decision support refreshed. The project is eligible for guided promotion.',
                    }));
                } else {
                    setState((prev) => ({
                        ...prev,
                        automationFeedback: 'Decision support refreshed. Resolve blockers before guided promotion.',
                    }));
                }
            } catch (error) {
                setState((prev) => ({
                    ...prev,
                    automationFeedback: error instanceof Error ? error.message : 'Unable to apply the suggested lifecycle promotion.',
                }));
            } finally {
                setState((prev) => ({ ...prev, isTransitioning: false }));
            }
        },
        [projectId, currentPhase, applyAutomationPlan]
    );

    const handleAutomationClick = useCallback(
        async (taskId: string, taskTitle: string) => {
            setState((prev) => ({ ...prev, isTransitioning: true }));

            try {
                const result = await executeTask.mutateAsync({
                    taskId,
                    input: {
                        projectId,
                        source: 'lifecycle-route',
                        phase: currentPhase,
                    },
                });

                // Handle queued status when CI/CD adapter is not connected
                if (result && typeof result === 'object' && 'status' in result && result.status === 'queued') {
                    const message = 'message' in result && typeof result.message === 'string'
                        ? result.message
                        : 'Task queued for execution. CI/CD adapter not yet connected.';
                    setState((prev) => ({ ...prev, automationFeedback: message }));
                } else {
                    setState((prev) => ({
                        ...prev,
                        automationFeedback: `Suggested task started for ${taskTitle}.`,
                    }));
                }
            } catch (error) {
                setState((prev) => ({
                    ...prev,
                    automationFeedback: error instanceof Error ? error.message : 'Unable to start the suggested task.',
                }));
            } finally {
                setState((prev) => ({ ...prev, isTransitioning: false }));
            }
        },
        [projectId, currentPhase, executeTask]
    );

    const clearFeedback = useCallback(() => {
        setState((prev) => ({ ...prev, automationFeedback: '' }));
    }, []);

    return {
        handleApprovalTransition,
        handleOneClickApproval,
        handleAutomationClick,
        clearFeedback,
        automationFeedback: state.automationFeedback,
        isTransitioning: state.isTransitioning || applyAutomationPlan.isPending || executeTask.isPending,
    };
}
