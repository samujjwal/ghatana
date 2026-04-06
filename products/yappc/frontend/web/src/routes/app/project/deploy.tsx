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

import { RouteErrorBoundary } from "../../../components/route/ErrorBoundary";
import { DeployPanelHost } from '../../../components/deploy/DeployPanelHost';
import type { CapacityRecommendationView } from '../../../components/deploy/CapacityDashboard';
import type { DeploymentPlanSummary } from '../../../components/deploy/DeploymentPanel';
import { useLifecycleArtifacts, usePhaseGates } from "../../../services/canvas/lifecycle";
import type { TransitionResult } from '../../../services/canvas/lifecycle';
import { phaseTransitionAPI, type PhaseTransitionPreview } from '@/services/lifecycle/phase-transition-api';

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

    const phasePredictionSummary = phasePreview?.estimatedReadyIn
        ? phasePreview.estimatedReadyIn === 'Ready now'
            ? `Ready now (${Math.round((phasePreview.predictionConfidence ?? 0) * 100)}% confidence)`
            : `Ready in ${phasePreview.estimatedReadyIn} (${Math.round((phasePreview.predictionConfidence ?? 0) * 100)}% confidence)`
        : 'No readiness prediction available.';

    const deploymentPlan = buildDeploymentPlanSummary(phasePreview, currentPhase);
    const capacityRecommendation = buildCapacityRecommendation(phasePreview);

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

        void phaseTransitionAPI
            .getNextPhase(currentPhase, projectId)
            .then((preview) => {
                if (!isMounted) {
                    return;
                }

                setPhasePreview(preview);
            })
            .catch((error: unknown) => {
                if (!isMounted) {
                    return;
                }

                setPhasePreview(null);
                setPhasePreviewError(
                    error instanceof Error
                        ? error.message
                        : 'Unable to load lifecycle readiness.'
                );
            })
            .finally(() => {
                if (isMounted) {
                    setIsPhasePreviewLoading(false);
                }
            });

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

    // Handler: Reject gate transition
    const handleReject = useCallback(async (gateId: string, reason: string) => {
        console.log('Gate rejected:', gateId, 'Reason:', reason);
        // Gate rejections prevent transitions - no action needed beyond logging
    }, []);

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

    return (
        <div className="h-full flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-divider bg-bg-paper">
                <div>
                    <h1 className="text-xl font-semibold text-text-primary">
                        Deployment Pipeline
                    </h1>
                    <p className="text-sm text-text-secondary mt-0.5">
                        Multi-environment deployment management
                    </p>
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
                </div>
                <button
                    type="button"
                    onClick={() => {
                        void handleAdvancePhase();
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