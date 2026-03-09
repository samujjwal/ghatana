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
import { useGetProjectsQuery } from "@ghatana/yappc-api";

import { RouteErrorBoundary } from "../../../components/route/ErrorBoundary";
import { DeployPanelHost } from "../../../components/deploy";
import { useLifecycleArtifacts, usePhaseGates } from "../../../services/canvas/lifecycle";
import { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

/**
 * Project Deploy Component
 */
export default function Component() {
    const { workspaceId, projectId } = useParams();

    // Fetch project data for deployment context
    const {
        data: projectsData,
        loading: projectsLoading,
        error: projectsError
    } = useGetProjectsQuery({
        variables: { workspaceId: workspaceId! },
        skip: !workspaceId,
        errorPolicy: 'all'
    });

    // Get current project
    const currentProject = projectsData?.projects?.find(p => p.id === projectId);

    // Initialize lifecycle services
    const { createArtifact, updateArtifact, artifacts } = useLifecycleArtifacts(projectId || '');
    const { transition, service: phaseGateService } = usePhaseGates(projectId || '');

    const currentUser = useAtomValue(currentUserAtom);
    const userId = currentUser?.id ?? 'anonymous';

    // Handler: Save delivery plan
    const handleSaveDeliveryPlan = useCallback(async (data: unknown) => {
        if (!projectId) return;

        const existingArtifact = artifacts.find(a => a.kind === LifecycleArtifactKind.DELIVERY_PLAN);

        if (existingArtifact) {
            await updateArtifact(existingArtifact.id, { payload: data as Record<string, unknown> }, userId);
        } else {
            await createArtifact(LifecycleArtifactKind.DELIVERY_PLAN, userId);
        }
    }, [projectId, artifacts, createArtifact, updateArtifact]);

    // Handler: Save release strategy
    const handleSaveReleaseStrategy = useCallback(async (data: unknown) => {
        if (!projectId) return;

        const existingArtifact = artifacts.find(a => a.kind === LifecycleArtifactKind.RELEASE_STRATEGY);

        if (existingArtifact) {
            await updateArtifact(existingArtifact.id, { payload: data as Record<string, unknown> }, userId);
        } else {
            await createArtifact(LifecycleArtifactKind.RELEASE_STRATEGY, userId);
        }
    }, [projectId, artifacts, createArtifact, updateArtifact]);

    // Handler: Approve gate with bypass support
    const handleApprove = useCallback(async (_gateId: string, comments?: string) => {
        if (!projectId) return;

        const state = await phaseGateService.getProjectState(projectId);
        if (!state) return;

        const nextPhase = state.currentPhase === 'INTENT' ? 'SHAPE' :
            state.currentPhase === 'SHAPE' ? 'VALIDATE' :
                state.currentPhase === 'VALIDATE' ? 'GENERATE' :
                    state.currentPhase === 'GENERATE' ? 'RUN' :
                        state.currentPhase === 'RUN' ? 'OBSERVE' : 'IMPROVE';

        await transition(nextPhase as unknown, userId, {
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
        await createArtifact(LifecycleArtifactKind.INCIDENT_REPORT, userId);
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

    // Loading state
    if (projectsLoading) {
        return (
            <div className="flex justify-center items-center min-h-[400px]">
                <div className="text-text-secondary">Loading deployment pipeline...</div>
            </div>
        );
    }

    // Error state
    if (projectsError && !projectsData) {
        return (
            <div className="p-8 text-center bg-bg-paper border border-error-color rounded-xl">
                <div className="text-error-color text-lg font-semibold mb-2">
                    Error Loading Deployment Pipeline
                </div>
                <div className="text-text-secondary mb-4">
                    Unable to load deployment data. Please refresh the page.
                </div>
            </div>
        );
    }

    return (
        <div className="h-full flex flex-col">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-divider bg-bg-paper">
                <div>
                    <h1 className="text-xl font-semibold text-text-primary">
                        Deployment Pipeline
                    </h1>
                    <p className="text-sm text-text-secondary mt-0.5">
                        {currentProject ? `${currentProject.name} - ` : ''}Multi-environment deployment management
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