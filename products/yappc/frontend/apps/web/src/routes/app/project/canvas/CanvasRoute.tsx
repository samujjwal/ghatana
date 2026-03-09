import React from 'react';
import { useParams } from 'react-router';

import { CanvasWorkspaceProvider } from '@/components/canvas/CanvasWorkspaceProvider';
import { useCanvasLifecycle } from '@/services/canvas/lifecycle/CanvasLifecycle';
import { getFOWStageForPhase } from '@/types/fow-stages';

/**
 * Canvas Route - Main entry point for production canvas
 * Provides Canvas-First UX with spatial zones and task guidance
 */
export const CanvasRoute: React.FC = () => {
  const params = useParams<{ projectId: string; canvasId: string }>();
  const projectId = params.projectId || 'untitled';

  // Get lifecycle state
  const { currentPhase } = useCanvasLifecycle();
  const fowStage = getFOWStageForPhase(currentPhase);

  return (
    <CanvasWorkspaceProvider
      projectId={projectId}
      currentPhase={currentPhase}
      fowStage={fowStage}
    />
  );
};

// Export for React Router v7 lazy loading
export { CanvasRoute as Component };
export default CanvasRoute;


