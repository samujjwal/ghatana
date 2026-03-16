/**
 * Lifecycle Explorer Route
 *
 * Provides an interactive view of all 7 lifecycle phases, artifacts, and transitions.
 * Accessible from the project's main navigation.
 *
 * @doc.type route
 * @doc.purpose Lifecycle phase and artifact navigator
 * @doc.layer product
 * @doc.pattern Route Component
 */

import { useParams, useNavigate } from 'react-router';
import { LifecycleExplorer } from '../../../components/lifecycle';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

/**
 * Lifecycle Explorer Route Component
 */
export default function Component() {
    const { projectId } = useParams();
    const navigate = useNavigate();

    if (!projectId) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="text-center">
                    <h1 className="text-2xl font-bold text-text-primary mb-2">Project Not Found</h1>
                    <p className="text-text-secondary">Please select a project first.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="h-full overflow-auto bg-bg-default">
            <LifecycleExplorer
                projectId={projectId}
                onPhaseSelect={(phase: LifecyclePhase) => {
                    void navigate(`/p/${projectId}/lifecycle/${phase.id}`);
                }}
                onArtifactSelect={(kind: LifecycleArtifactKind) => {
                    void navigate(`/p/${projectId}/lifecycle/artifacts/${kind}`);
                }}
            />
        </div>
    );
}

/**
 * Error boundary for lifecycle explorer
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Lifecycle Explorer Error"
            message="Unable to load the lifecycle explorer. Please try refreshing the page."
        />
    );
}
