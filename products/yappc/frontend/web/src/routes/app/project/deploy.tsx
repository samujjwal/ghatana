/**
 * Project Deploy Route
 * 
 * Provides deployment pipeline management for projects with environment
 * configurations, deployment history, and rollback capabilities.
 * Uses DeployPanelHost for URL-driven segment navigation.
 * 
 * @doc.type route
 * @doc.purpose Deployment pipeline view
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { useParams } from "react-router";
import { useCallback, useEffect, useState } from "react";
import { useAtomValue } from 'jotai';
import { currentUserAtom } from '../../../stores/user.store';
import type {
    ReleasePlanningStatusContract,
    ReleasePlanningStatusViewContract,
} from '@/contracts/workspace-project';

import { RouteErrorBoundary } from "../../../components/route/ErrorBoundary";
import { DeployPanelHost } from '../../../components/deploy/DeployPanelHost';
import type { CapacityRecommendationView } from '../../../components/deploy/CapacityDashboard';
import type { DeploymentPlanSummary } from '../../../components/deploy/DeploymentPanel';
import { useLifecycleArtifacts } from "../../../services/canvas/lifecycle/LifecycleArtifactService";
import { usePhaseGates } from "../../../services/canvas/lifecycle/PhaseGateService";
import type { TransitionResult } from '../../../services/canvas/lifecycle/PhaseGateService';
import { phaseTransitionAPI, type PhaseTransitionPreview } from '@/services/lifecycle/phase-transition-api';

function getReleasePlanningStatusView(
    preview: PhaseTransitionPreview | null,
    previewError: string | null,
): ReleasePlanningStatusViewContract {
    if (previewError) {
        return {
            status: 'blocked',
            label: 'Release planning blocked',
            detail: previewError,
        };
    }

    if (!preview) {
        return {
            status: 'approval-needed',
            label: 'Review pending',
            detail: 'Waiting for lifecycle readiness before the planning posture can be finalized.',
        };
    }

    if (!preview.nextPhase) {
        return {
            status: 'final-phase',
            label: 'Lifecycle complete',
            detail: 'No further promotion step is available from this project state.',
        };
    }

    if (!preview.canAdvance) {
        return {
            status: 'blocked',
            label: 'Blocked by lifecycle gates',
            detail: preview.blockers[0] ?? 'Resolve the listed blockers before planning promotion.',
        };
    }

    if ((preview.readiness ?? 0) < 90) {
        return {
            status: 'approval-needed',
            label: 'Approval-needed',
            detail: 'The route can plan promotion, but an operator should review readiness before proceeding.',
        };
    }

    return {
        status: 'planning-ready',
        label: 'Planning-ready',
        detail: 'Lifecycle evidence supports the next promotion step with standard operator checks.',
    };
}

function getReleasePlanningStatusClassName(status: ReleasePlanningStatusContract): string {
    switch (status) {
        case 'planning-ready':
        case 'final-phase':
            return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200';
        case 'approval-needed':
            return 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200';
        default:
            return 'border-red-200 bg-red-50 text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200';
    }
}

function buildDeploymentPlanSummary(preview: PhaseTransitionPreview | null, currentPhase: string): DeploymentPlanSummary {
    const readiness = Math.round(preview?.readiness ?? 0);
    const riskScore = Number(((100 - readiness) / 10).toFixed(1));

    return {
        strategy: preview?.canAdvance ? (readiness >= 90 ? 'CANARY' : 'ROLLING') : 'BLUE_GREEN',
        riskScore,
        readiness,
        rationale: preview?.canAdvance
            ? `Lifecycle checks for ${currentPhase} are mostly satisfied. Roll out with explicit observation gates.`
            : 'Pending lifecycle blockers increase rollout risk and require a safer promotion path.',
        riskFactors: preview?.blockers.length
            ? ['lifecycle-blockers', 'phase-gate-pending']
            : ['readiness-derived', 'monitor-post-release'],
        blockers: preview?.blockers ?? [],
        requiresApproval: !preview?.canAdvance || riskScore >= 7,
        canaryPercent: preview?.canAdvance ? 5 : 0,
    };
}

function buildCapacityRecommendation(preview: PhaseTransitionPreview | null): CapacityRecommendationView {
    const readiness = preview?.readiness ?? 0;
    const targetReplicas = readiness >= 90 ? 4 : readiness >= 70 ? 3 : 2;

    return {
        action: readiness >= 90 ? 'SCALE_UP' : readiness >= 70 ? 'HOLD' : 'RIGHTSIZE',
        currentReplicas: 3,
        targetReplicas,
        avgCpuUtilization: readiness >= 90 ? 0.72 : readiness >= 70 ? 0.56 : 0.42,
        peakCpuUtilization: readiness >= 90 ? 0.88 : readiness >= 70 ? 0.69 : 0.58,
        avgMemoryUtilization: readiness >= 90 ? 0.68 : readiness >= 70 ? 0.53 : 0.39,
        currentMonthlyCost: 3200,
        projectedMonthlyCost: readiness >= 90 ? 3625 : readiness >= 70 ? 3200 : 2960,
        confidence: preview?.predictionConfidence ?? 0.68,
        rationale: preview?.canAdvance
            ? 'Capacity remains healthy, but rollout readiness suggests keeping short-term scale headroom during promotion.'
            : 'Use a smaller footprint until lifecycle blockers are resolved and post-release demand is clearer.',
    };
}

/**
 * Project Deploy Component
 */
export default function Component() {
    const { projectId } = useParams();

    // Initialize lifecycle services
    const { createArtifact, updateArtifact, artifacts } = useLifecycleArtifacts(projectId || '');
    const { currentPhase, transition } = usePhaseGates(projectId || '');

    const currentUser = useAtomValue(currentUserAtom);
    const userId = currentUser?.id ?? 'anonymous';
    const [phasePreview, setPhasePreview] = useState<PhaseTransitionPreview | null>(null);
    const [phasePreviewError, setPhasePreviewError] = useState<string | null>(null);
    const [isPhasePreviewLoading, setIsPhasePreviewLoading] = useState<boolean>(false);
    const [isAdvancing, setIsAdvancing] = useState<boolean>(false);
    const [operatorNote, setOperatorNote] = useState<string>('');
    const [operatorFeedback, setOperatorFeedback] = useState<string | null>(null);
    const [showConfirmAdvance, setShowConfirmAdvance] = useState<boolean>(false);
    const [rejectionHistory, setRejectionHistory] = useState<Array<{
        gateId: string;
        reason: string;
        actor: string;
        timestamp: string;
    }>>([]);

    const phasePredictionSummary = phasePreview?.estimatedReadyIn
        ? phasePreview.estimatedReadyIn === 'Ready now'
            ? `Ready now (${Math.round((phasePreview.predictionConfidence ?? 0) * 100)}% confidence)`
            : `Ready in ${phasePreview.estimatedReadyIn} (${Math.round((phasePreview.predictionConfidence ?? 0) * 100)}% confidence)`
        : 'No readiness prediction available.';

    const deploymentPlan = buildDeploymentPlanSummary(phasePreview, currentPhase);
    const capacityRecommendation = buildCapacityRecommendation(phasePreview);
    const releasePlanningStatus = getReleasePlanningStatusView(phasePreview, phasePreviewError);

    type ArtifactKindValue = Parameters<typeof createArtifact>[0];
    const DELIVERY_PLAN_KIND = 'delivery_plan' as ArtifactKindValue;
    const RELEASE_STRATEGY_KIND = 'release_strategy' as ArtifactKindValue;
    const INCIDENT_REPORT_KIND = 'incident_report' as ArtifactKindValue;

    useEffect(() => {
        if (!projectId) {
            setPhasePreview(null);
            return;
        }

        let isMounted = true;
        setIsPhasePreviewLoading(true);
        setPhasePreviewError(null);

        void (async () => {
            try {
                const preview = await phaseTransitionAPI.getNextPhase(currentPhase, projectId);

                if (!isMounted) {
                    return;
                }

                setPhasePreview(preview);
            } catch (error: unknown) {
                if (!isMounted) {
                    return;
                }

                setPhasePreview(null);
                setPhasePreviewError(
                    error instanceof Error
                        ? error.message
                        : 'Unable to load lifecycle readiness.'
                );
            } finally {
                if (isMounted) {
                    setIsPhasePreviewLoading(false);
                }
            }
        })();

        return () => {
            isMounted = false;
        };
    }, [currentPhase, projectId]);

    const handleAdvancePhase = useCallback(async (comments?: string): Promise<TransitionResult | void> => {
        if (!projectId) return;

        if (!phasePreview?.nextPhase) {
            setPhasePreviewError('No next lifecycle phase is available for this project.');
            return;
        }

        if (!phasePreview.canAdvance) {
            setPhasePreviewError('Resolve the listed blockers before advancing the phase.');
            return;
        }

        setIsAdvancing(true);
        setPhasePreviewError(null);

        try {
            const result = await transition(phasePreview.nextPhase, userId, {
                bypass: false,
                bypassReason: comments,
            });

            if (!result.success) {
                setPhasePreviewError(result.errors.join(' ') || 'Unable to advance lifecycle phase.');
            }

            return result;
        } finally {
            setIsAdvancing(false);
        }
    }, [phasePreview, projectId, transition, userId]);

    // Handler: Save delivery plan
    const handleSaveDeliveryPlan = useCallback(async (data: unknown) => {
        if (!projectId) return;

        const existingArtifact = artifacts.find(a => a.kind === DELIVERY_PLAN_KIND);

        if (existingArtifact) {
            await updateArtifact(existingArtifact.id, { payload: data as Record<string, unknown> }, userId);
        } else {
            await createArtifact(DELIVERY_PLAN_KIND, userId);
        }
    }, [projectId, artifacts, createArtifact, updateArtifact]);

    // Handler: Save release strategy
    const handleSaveReleaseStrategy = useCallback(async (data: unknown) => {
        if (!projectId) return;

        const existingArtifact = artifacts.find(a => a.kind === RELEASE_STRATEGY_KIND);

        if (existingArtifact) {
            await updateArtifact(existingArtifact.id, { payload: data as Record<string, unknown> }, userId);
        } else {
            await createArtifact(RELEASE_STRATEGY_KIND, userId);
        }
    }, [projectId, artifacts, createArtifact, updateArtifact]);

    // Handler: Approve gate with bypass support
    const handleApprove = useCallback(async (_gateId: string, comments?: string) => {
        await handleAdvancePhase(comments);
    }, [handleAdvancePhase]);

    // Handler: Reject gate transition — persists decision as artifact and local history
    const handleReject = useCallback(async (gateId: string, reason: string) => {
        if (!projectId) return;

        const timestamp = new Date().toISOString();

        // Record in local decision history for immediate UI feedback
        setRejectionHistory((prev) => [
            ...prev,
            { gateId, reason, actor: userId, timestamp },
        ]);

        // Persist as incident report artifact for durability
        try {
            const artifact = await createArtifact(INCIDENT_REPORT_KIND, userId);
            await updateArtifact(
                artifact.id,
                {
                    payload: {
                        type: 'rejection_decision',
                        gateId,
                        reason,
                        actor: userId,
                        timestamp,
                        projectId,
                    },
                },
                userId
            );
            setOperatorFeedback(`Rejection recorded for gate ${gateId}. Decision is durable.`);
        } catch (err) {
            setOperatorFeedback(`Rejection noted for gate ${gateId} (artifact persistence failed).`);
        }
    }, [projectId, createArtifact, updateArtifact, userId]);

    // Handler: Create incident (creates artifact)
    const handleCreateIncident = useCallback(async () => {
        if (!projectId) return;
        await createArtifact(INCIDENT_REPORT_KIND, userId);
    }, [projectId, createArtifact]);

    // Handler: Update incident status
    const handleUpdateIncidentStatus = useCallback(async (id: string, status: string) => {
        await updateArtifact(id, {
            payload: { status, updatedAt: new Date().toISOString() },
        }, userId);
    }, [updateArtifact]);

    // Handler: Add note to incident
    const handleAddIncidentNote = useCallback(async (id: string, note: string) => {
        await updateArtifact(id, {
            payload: {
                notes: [note],
                updatedAt: new Date().toISOString(),
            },
        }, userId);
    }, [updateArtifact]);

    const handleCreateIncidentFromOperatorSurface = useCallback(async () => {
        await handleCreateIncident();
        setOperatorFeedback('Incident report created for the current deployment posture.');
    }, [handleCreateIncident]);

    const handleOperatorAdvance = useCallback(async () => {
        await handleAdvancePhase(operatorNote.trim() || undefined);
        if (phasePreview?.canAdvance) {
            setOperatorFeedback('Operator advance request submitted to the lifecycle gate.');
        }
    }, [handleAdvancePhase, operatorNote, phasePreview?.canAdvance]);

    return (
        <div className="h-full flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-divider bg-bg-paper">
                <div>
                    <h1 className="text-xl font-semibold text-text-primary">
                        Release Planning
                    </h1>
                    <p className="text-sm text-text-secondary mt-0.5">
                        Lifecycle promotion guidance and operator planning
                    </p>
                    <div className="mt-3">
                        <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-medium ${getReleasePlanningStatusClassName(releasePlanningStatus.status)}`} data-testid="release-planning-status-badge">
                            {releasePlanningStatus.label}
                        </span>
                    </div>
                    <p className="text-xs text-text-secondary mt-2" data-testid="phase-preview-summary">
                        {isPhasePreviewLoading
                            ? 'Loading lifecycle readiness...'
                            : phasePreview?.nextPhase
                                ? `${phasePreview.currentPhase} -> ${phasePreview.nextPhase}`
                                : `${currentPhase} is the final lifecycle phase`}
                    </p>
                    <p className="text-xs text-text-secondary mt-1" data-testid="phase-prediction-summary">
                        {isPhasePreviewLoading ? 'Predicting readiness window...' : phasePredictionSummary}
                    </p>
                    <p className="text-xs text-text-secondary mt-1" data-testid="release-planning-status-detail">
                        {releasePlanningStatus.detail}
                    </p>
                </div>
                <button
                    type="button"
                    data-testid="advance-phase-trigger"
                    onClick={() => {
                        setShowConfirmAdvance(true);
                    }}
                    disabled={
                        isAdvancing ||
                        isPhasePreviewLoading ||
                        !phasePreview?.canAdvance ||
                        !phasePreview?.nextPhase
                    }
                    className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                    {isAdvancing
                        ? 'Advancing...'
                        : phasePreview?.nextPhase
                            ? `Advance to ${phasePreview.nextPhase}`
                            : 'Advance Phase'}
                </button>
            </div>

            {/* Advance confirmation with risk controls */}
            {showConfirmAdvance && phasePreview?.canAdvance && phasePreview.nextPhase && (
                <div
                    className="border-b border-divider bg-bg-paper px-6 py-4"
                    data-testid="advance-confirmation-panel"
                >
                    <h3 className="text-sm font-semibold text-text-primary">
                        Confirm lifecycle promotion
                    </h3>
                    <div className="mt-3 grid gap-3 sm:grid-cols-3 text-xs text-text-secondary">
                        <div className="rounded-lg border border-divider p-3">
                            <span className="block text-[10px] uppercase tracking-wide text-text-secondary">Readiness</span>
                            <span className="block text-lg font-semibold text-text-primary">{deploymentPlan.readiness}%</span>
                        </div>
                        <div className="rounded-lg border border-divider p-3">
                            <span className="block text-[10px] uppercase tracking-wide text-text-secondary">Risk score</span>
                            <span className={`block text-lg font-semibold ${deploymentPlan.riskScore >= 7 ? 'text-red-600' : deploymentPlan.riskScore >= 4 ? 'text-amber-600' : 'text-emerald-600'}`}>
                                {deploymentPlan.riskScore}/10
                            </span>
                        </div>
                        <div className="rounded-lg border border-divider p-3">
                            <span className="block text-[10px] uppercase tracking-wide text-text-secondary">Prediction confidence</span>
                            <span className="block text-lg font-semibold text-text-primary">
                                {Math.round((phasePreview.predictionConfidence ?? 0) * 100)}%
                            </span>
                        </div>
                    </div>
                    {phasePreview.blockers.length > 0 && (
                        <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
                            <p className="text-xs font-medium text-amber-800">Remaining blockers:</p>
                            <ul className="mt-1 list-disc pl-4 text-xs text-amber-700">
                                {phasePreview.blockers.map((blocker) => (
                                    <li key={blocker}>{blocker}</li>
                                ))}
                            </ul>
                        </div>
                    )}
                    <p className="mt-3 text-xs text-text-secondary">
                        Strategy: {deploymentPlan.strategy}. Rollback: an incident report artifact will be auto-created if the promotion triggers an anomaly. You can also create one manually below.
                    </p>
                    <div className="mt-3 flex gap-3">
                        <button
                            type="button"
                            data-testid="confirm-advance-button"
                            onClick={() => {
                                setShowConfirmAdvance(false);
                                void handleAdvancePhase(operatorNote.trim() || undefined);
                            }}
                            disabled={isAdvancing}
                            className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {isAdvancing ? 'Advancing...' : 'Confirm advance'}
                        </button>
                        <button
                            type="button"
                            data-testid="cancel-advance-button"
                            onClick={() => setShowConfirmAdvance(false)}
                            disabled={isAdvancing}
                            className="rounded-lg border border-divider px-4 py-2 text-sm font-medium text-text-primary transition-colors hover:bg-grey-100 dark:hover:bg-grey-800 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            Cancel
                        </button>
                    </div>
                </div>
            )}

            {(phasePreviewError || (phasePreview?.blockers.length ?? 0) > 0) && (
                <div className="border-b border-divider bg-amber-50 px-6 py-3 text-sm text-amber-900">
                    {phasePreviewError && (
                        <p className="font-medium" data-testid="phase-preview-error">{phasePreviewError}</p>
                    )}
                    {(phasePreview?.blockers.length ?? 0) > 0 && (
                        <ul className="mt-2 list-disc pl-5" data-testid="phase-blockers">
                            {phasePreview?.blockers.map((blocker) => (
                                <li key={blocker}>{blocker}</li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            <section
                className="border-b border-divider bg-bg-paper px-6 py-4"
                data-testid="operator-controls-card"
            >
                <div className="grid gap-4 lg:grid-cols-[1.4fr,1fr]">
                    <div>
                        <h2 className="text-sm font-semibold uppercase tracking-[0.14em] text-primary-600">Operator control surface</h2>
                        <p className="mt-2 text-base font-semibold text-text-primary">Release posture for lifecycle promotion planning</p>
                        <p className="mt-1 text-sm text-text-secondary">
                            Planning recommendation: strategy {deploymentPlan.strategy}, estimated risk score {deploymentPlan.riskScore}, readiness {deploymentPlan.readiness}%.
                        </p>
                        <p className="mt-2 text-sm text-text-secondary">
                            Current release status: {releasePlanningStatus.label}. {releasePlanningStatus.detail}
                        </p>
                        <div className="mt-3 flex flex-wrap gap-3 text-xs text-text-secondary">
                            <span>Approval {deploymentPlan.requiresApproval ? 'required' : 'not required'}</span>
                            <span>Suggested replica posture {capacityRecommendation.targetReplicas}</span>
                            <span>Estimated monthly cost ${capacityRecommendation.projectedMonthlyCost}</span>
                        </div>
                        <p className="mt-3 text-xs text-text-secondary">
                            These figures are planning estimates derived from lifecycle readiness, not live deployment telemetry.
                        </p>
                    </div>
                    <div>
                        <label className="block text-xs font-medium uppercase tracking-[0.14em] text-text-secondary" htmlFor="operator-note">
                            Operator note
                        </label>
                        <textarea
                            id="operator-note"
                            data-testid="operator-note-input"
                            rows={3}
                            value={operatorNote}
                            onChange={(event) => setOperatorNote(event.target.value)}
                            className="mt-2 w-full rounded-lg border border-divider bg-bg-default px-3 py-2 text-sm text-text-primary"
                            placeholder="Capture rollout context, approval notes, or incident clues."
                        />
                        <div className="mt-3 flex flex-wrap gap-3">
                            <button
                                type="button"
                                data-testid="operator-create-incident"
                                onClick={() => {
                                    void handleCreateIncidentFromOperatorSurface();
                                }}
                                className="rounded-lg border border-divider px-3 py-2 text-sm font-medium text-text-primary transition-colors hover:bg-grey-100 dark:hover:bg-grey-800"
                            >
                                Create incident report
                            </button>
                            <button
                                type="button"
                                data-testid="operator-advance-with-note"
                                disabled={
                                    isAdvancing ||
                                    isPhasePreviewLoading ||
                                    !phasePreview?.canAdvance ||
                                    !phasePreview?.nextPhase
                                }
                                onClick={() => {
                                    void handleOperatorAdvance();
                                }}
                                className="rounded-lg bg-primary-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
                            >
                                Advance with operator note
                            </button>
                        </div>
                        {operatorFeedback && (
                            <p className="mt-3 text-xs text-text-secondary" data-testid="operator-action-feedback">
                                {operatorFeedback}
                            </p>
                        )}
                    </div>
                </div>
            </section>

            {/* Rejection decision history */}
            {rejectionHistory.length > 0 && (
                <section
                    className="border-b border-divider bg-bg-paper px-6 py-3"
                    data-testid="rejection-history-card"
                >
                    <h3 className="text-xs font-semibold uppercase tracking-[0.14em] text-text-secondary">
                        Decision history ({rejectionHistory.length})
                    </h3>
                    <ul className="mt-2 space-y-2">
                        {rejectionHistory.map((entry, index) => (
                            <li
                                key={`${entry.gateId}-${entry.timestamp}-${index}`}
                                className="text-xs text-text-secondary"
                            >
                                <span className="font-medium text-text-primary">
                                    {new Date(entry.timestamp).toLocaleString()}
                                </span>{' '}
                                — Gate <span className="font-medium">{entry.gateId}</span> rejected by{' '}
                                <span className="font-medium">{entry.actor}</span>
                                {entry.reason && (
                                    <span className="block mt-0.5 italic">Reason: {entry.reason}</span>
                                )}
                            </li>
                        ))}
                    </ul>
                </section>
            )}

            {/* DeployPanelHost - URL-driven segment navigation */}
            <div className="flex-1 overflow-hidden">
                <DeployPanelHost
                    projectId={projectId || ''}
                    dataContext={{
                        onSaveDeliveryPlan: handleSaveDeliveryPlan,
                        onSaveReleaseStrategy: handleSaveReleaseStrategy,
                        onApprove: handleApprove,
                        onReject: handleReject,
                        onCreateIncident: handleCreateIncident,
                        onUpdateIncidentStatus: handleUpdateIncidentStatus,
                        onAddIncidentNote: handleAddIncidentNote,
                        deploymentPlan,
                        capacityRecommendation,
                    }}
                />
            </div>
        </div>
    );
}

/**
 * Error boundary for deployment route
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Deployment Pipeline Error"
            message="Unable to load the deployment pipeline. Please try refreshing the page."
        />
    );
}