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
import { useCallback } from "react";
import { useAtomValue } from 'jotai';
import { currentUserAtom } from '../../../stores/user.store';

import { RouteErrorBoundary } from "../../../components/route/ErrorBoundary";
import { DeployPanelHost } from "../../../components/deploy";
import { useLifecycleArtifacts, usePhaseGates } from "../../../services/canvas/lifecycle";
import { LifecyclePhase } from '@/types/lifecycle';

/**
 * Project Deploy Component
 */
export default function Component() {
    const { projectId } = useParams();

    // Initialize lifecycle services
    const { createArtifact, updateArtifact, artifacts } = useLifecycleArtifacts(projectId || '');
    const { transition, service: phaseGateService } = usePhaseGates(projectId || '');

    const currentUser = useAtomValue(currentUserAtom);
    const userId = currentUser?.id ?? 'anonymous';

    type ArtifactKindValue = Parameters<typeof createArtifact>[0];
    const DELIVERY_PLAN_KIND = 'delivery_plan' as ArtifactKindValue;
    const RELEASE_STRATEGY_KIND = 'release_strategy' as ArtifactKindValue;
    const INCIDENT_REPORT_KIND = 'incident_report' as ArtifactKindValue;

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
        if (!projectId) return;

        const state = await phaseGateService.getProjectState(projectId);
        if (!state) return;

        const nextPhaseMap: Record<LifecyclePhase, LifecyclePhase> = {
            [LifecyclePhase.INTENT]: LifecyclePhase.SHAPE,
            [LifecyclePhase.SHAPE]: LifecyclePhase.VALIDATE,
            [LifecyclePhase.VALIDATE]: LifecyclePhase.GENERATE,
            [LifecyclePhase.GENERATE]: LifecyclePhase.RUN,
            [LifecyclePhase.RUN]: LifecyclePhase.OBSERVE,
            [LifecyclePhase.OBSERVE]: LifecyclePhase.IMPROVE,
            [LifecyclePhase.IMPROVE]: LifecyclePhase.IMPROVE,
        };

        const nextPhase = nextPhaseMap[state.currentPhase as LifecyclePhase] ?? LifecyclePhase.IMPROVE;

        await transition(nextPhase, userId, {
            bypass: false,
            bypassReason: comments,
        });
    }, [projectId, phaseGateService, transition]);

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
                </div>
            </div>

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