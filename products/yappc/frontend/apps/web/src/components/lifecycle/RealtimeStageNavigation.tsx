/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Frontend - Real-time Stage Navigation Component
 * 
 * Enhanced stage navigation with real-time updates and live status indicators.
 * Integrates with WebSocket service for automatic state synchronization.
 */

import { useEffect, useState } from 'react';
import { StageNavigation, StageNavigationProps } from '@ghatana/yappc-ui';
import { useRealtimeLifecycle } from '../../hooks/useRealtimeLifecycle';

// Define lifecycle stage type locally to avoid import issues
type LifecycleStageId = 'intent' | 'context' | 'plan' | 'execute' | 'verify' | 'observe' | 'learn' | 'institutionalize';

export interface RealtimeStageNavigationProps {
  projectId: string;
  className?: string;
  onStageChange?: (stage: LifecycleStageId) => void;
  onProjectAdvance?: () => void;
  onProjectGoBack?: () => void;
}

/**
 * RealtimeStageNavigation Component
 * 
 * Wraps the existing StageNavigation component with real-time capabilities:
 * - Automatic updates when project stage changes
 * - Live connection status indicator
 * - Real-time task status updates
 * - Automatic refresh on reconnection
 * 
 * @example
 * ```tsx
 * <RealtimeStageNavigation
 *   projectId="project-123"
 *   onStageChange={(stage) => console.log('Stage changed to:', stage)}
 * />
 * ```
 */
export function RealtimeStageNavigation({
  projectId,
  className,
  onStageChange,
  onProjectAdvance,
  onProjectGoBack
}: RealtimeStageNavigationProps) {
  const {
    project,
    phases,
    isConnected,
    lastUpdate,
    error,
    refresh
  } = useRealtimeLifecycle({
    projectId,
    enableRealtime: true
  });

  const [localError, setLocalError] = useState<string | null>(null);

  // Handle connection errors
  useEffect(() => {
    if (error) {
      setLocalError(error);
    } else {
      setLocalError(null);
    }
  }, [error]);

  // Handle real-time updates
  useEffect(() => {
    if (lastUpdate) {
      console.log('Real-time update received:', lastUpdate);
      
      // Trigger callbacks for specific update types
      switch (lastUpdate.type) {
        case 'phase_transition':
          if (lastUpdate.data.currentStage && onStageChange) {
            onStageChange(lastUpdate.data.currentStage);
          }
          break;
      }
    }
  }, [lastUpdate, onStageChange]);

  // Calculate navigation props from real-time data
  const getNavigationProps = (): StageNavigationProps => {
    if (!project) {
      // Default props when project not loaded
      return {
        projectId,
        projectName: 'Loading...',
        currentStage: 'plan' as LifecycleStageId,
        availableStages: ['plan'] as LifecycleStageId[],
        canAdvance: false,
        canGoBack: false,
        onNavigate: onStageChange || (() => {}),
        className
      };
    }

    // Extract available stages from phases
    const availableStages = phases
      .filter(phase => phase.status !== 'BLOCKED')
      .map(phase => phase.stage as LifecycleStageId);

    // Extract completed stages
    const completedStages = phases
      .filter(phase => phase.status === 'COMPLETED')
      .map(phase => phase.stage as LifecycleStageId);

    // Extract blocked stages
    const blockedStages = phases
      .filter(phase => phase.status === 'BLOCKED')
      .map(phase => phase.stage as LifecycleStageId);

    // Determine if can advance/go-back based on current phase state
    const currentPhase = phases.find(phase => phase.stage === project.currentStage);
    const canAdvance = currentPhase?.status === 'ACTIVE' && 
                      !blockedStages.includes(project.currentStage as LifecycleStageId);
    const canGoBack = phases.some(phase => 
      phase.status === 'COMPLETED' && 
      phase.stage !== project.currentStage
    );

    // Get block/advance reasons
    const blockReason = currentPhase?.blockedReason;
    const advanceReason = canAdvance ? 'Ready to advance to next stage' : undefined;

    return {
      projectId,
      projectName: project.name,
      currentStage: project.currentStage as LifecycleStageId,
      availableStages,
      blockedStages,
      completedStages,
      canAdvance,
      canGoBack,
      advanceReason,
      blockReason,
      onNavigate: onStageChange || (() => {}),
      onAdvance: onProjectAdvance,
      onGoBack: onProjectGoBack,
      className
    };
  };

  // Handle manual refresh
  const handleRefresh = () => {
    refresh();
  };

  // Render connection status indicator
  const renderConnectionStatus = () => (
    <div className="flex items-center space-x-2 mb-2">
      <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
      <span className="text-sm text-gray-600">
        {isConnected ? 'Connected' : 'Disconnected'}
      </span>
      {!isConnected && (
        <button
          onClick={handleRefresh}
          className="text-xs text-blue-600 hover:text-blue-800 underline"
        >
          Reconnect
        </button>
      )}
    </div>
  );

  // Render error message
  const renderError = () => {
    if (!localError) return null;
    
    return (
      <div className="bg-red-50 border border-red-200 rounded-md p-3 mb-4">
        <div className="flex items-center justify-between">
          <p className="text-sm text-red-600">{localError}</p>
          <button
            onClick={() => setLocalError(null)}
            className="text-red-400 hover:text-red-600"
          >
            ×
          </button>
        </div>
      </div>
    );
  };

  // Render last update info
  const renderLastUpdate = () => {
    if (!lastUpdate) return null;
    
    return (
      <div className="text-xs text-gray-500 mb-2">
        Last update: {new Date(lastUpdate.timestamp).toLocaleTimeString()} - {lastUpdate.type}
      </div>
    );
  };

  return (
    <div className={className}>
      {renderConnectionStatus()}
      {renderError()}
      {renderLastUpdate()}
      <StageNavigation {...getNavigationProps()} />
    </div>
  );
}

export default RealtimeStageNavigation;
