import React from 'react';
import { useParams } from 'react-router';

import { CanvasWorkspaceProvider } from '@/components/canvas/CanvasWorkspaceProvider';
import { useCanvasLifecycle } from '@/services/canvas/lifecycle/CanvasLifecycle';
import { getFOWStageForPhase } from '@/types/fow-stages';
import { useWorkspaceContext } from '@/hooks/useWorkspaceData';

/**
 * Canvas Route - Main entry point for production canvas
 * Provides Canvas-First UX with spatial zones and task guidance
 */
export const CanvasRoute: React.FC = () => {
  const params = useParams<{ projectId: string; canvasId: string }>();
  const projectId = params.projectId || 'untitled';

  // Get lifecycle state
  const { currentPhase } = useCanvasLifecycle();
  const flowStage = getFOWStageForPhase(currentPhase);
  const { ownedProjects, includedProjects } = useWorkspaceContext();
  const projectAccess = React.useMemo(
    () => [...ownedProjects, ...includedProjects].find((project) => project.id === projectId),
    [includedProjects, ownedProjects, projectId]
  );

  return (
    <CanvasWorkspaceProvider
      projectId={projectId}
      currentPhase={currentPhase}
      flowStage={flowStage}
      projectAccess={projectAccess}
    />
  );
};

// Export for React Router v7 lazy loading
export { CanvasRoute as Component };
export default CanvasRoute;

