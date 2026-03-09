/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Frontend - Real-time Lifecycle Hook
 * 
 * Integrates WebSocket service with lifecycle API hooks to provide
 * real-time updates for project lifecycle state changes.
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { LifecycleWebSocketService, LifecycleStateUpdate } from '../services/LifecycleWebSocketService';

// Import types from existing lifecycle API
interface Project {
  id: string;
  name: string;
  description: string;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
  currentStage: string;
  startedAt: string;
  completedAt?: string;
  lastActivityAt: string;
  createdBy: string;
  taskIds: string[];
}

interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  stage: string;
  assignedAgentId?: string;
  requiredCapabilities: string[];
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  deadlineAt?: string;
  retryCount: number;
  maxRetries: number;
  resultSummary?: string;
  errorMessage?: string;
  labels: Record<string, string>;
}

interface PhaseState {
  id: string;
  projectId: string;
  stage: string;
  previousStage?: string;
  status: 'ACTIVE' | 'COMPLETED' | 'BLOCKED' | 'SKIPPED';
  enteredAt: string;
  completedAt?: string;
  blockedReason?: string;
  gateAgentId?: string;
  gateResult?: 'PASSED' | 'FAILED' | 'SKIPPED';
}

// Mock API functions (replace with actual API calls)
const useLifecycleApi = () => ({
  getProject: async (projectId: string): Promise<Project | null> => {
    // Mock implementation - replace with actual API call
    return {
      id: projectId,
      name: `Project ${projectId}`,
      description: 'Mock project description',
      status: 'ACTIVE',
      currentStage: 'plan',
      startedAt: new Date().toISOString(),
      lastActivityAt: new Date().toISOString(),
      createdBy: 'user-123',
      taskIds: []
    };
  },
  getProjectTasks: async (projectId: string): Promise<Task[]> => {
    // Mock implementation - replace with actual API call
    console.log('Fetching tasks for project:', projectId);
    return [];
  },
  getProjectPhases: async (projectId: string): Promise<PhaseState[]> => {
    // Mock implementation - replace with actual API call
    console.log('Fetching phases for project:', projectId);
    return [];
  }
});

export interface RealtimeLifecycleState {
  project: Project | null;
  tasks: Task[];
  phases: PhaseState[];
  isConnected: boolean;
  lastUpdate: LifecycleStateUpdate | null;
  error: string | null;
}

export interface UseRealtimeLifecycleOptions {
  projectId: string;
  enableRealtime?: boolean;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
}

/**
 * Hook for real-time lifecycle state management.
 * 
 * Combines REST API data with WebSocket updates to provide
 * live synchronization of project lifecycle state.
 * 
 * @example
 * ```tsx
 * const {
 *   project,
 *   tasks,
 *   phases,
 *   isConnected,
 *   lastUpdate,
 *   error,
 *   refresh
 * } = useRealtimeLifecycle({
 *   projectId: 'project-123',
 *   enableRealtime: true
 * });
 * ```
 */
export function useRealtimeLifecycle({
  projectId,
  enableRealtime = true,
  reconnectInterval = 5000,
  maxReconnectAttempts = 10
}: UseRealtimeLifecycleOptions) {
  const [state, setState] = useState<RealtimeLifecycleState>({
    project: null,
    tasks: [],
    phases: [],
    isConnected: false,
    lastUpdate: null,
    error: null
  });

  const wsServiceRef = useRef<LifecycleWebSocketService | null>(null);
  const { getProject, getProjectTasks, getProjectPhases } = useLifecycleApi();

  // Initialize WebSocket service
  useEffect(() => {
    if (enableRealtime && projectId) {
      wsServiceRef.current = new LifecycleWebSocketService({
        reconnectIntervalMs: reconnectInterval,
        maxReconnectAttempts
      });
    }

    return () => {
      if (wsServiceRef.current) {
        wsServiceRef.current.disconnect();
      }
    };
  }, [enableRealtime, projectId, reconnectInterval, maxReconnectAttempts]);

  // Load initial data
  const loadInitialData = useCallback(async () => {
    if (!projectId) return;

    try {
      const [project, tasks, phases] = await Promise.all([
        getProject(projectId),
        getProjectTasks(projectId),
        getProjectPhases(projectId)
      ]);

      setState(prev => ({
        ...prev,
        project,
        tasks: tasks || [],
        phases: phases || [],
        error: null
      }));
    } catch (err) {
      setState(prev => ({
        ...prev,
        error: err instanceof Error ? err.message : 'Failed to load data'
      }));
    }
  }, [projectId, getProject, getProjectTasks, getProjectPhases]);

  // Handle WebSocket updates
  const handleWebSocketUpdate = useCallback((update: LifecycleStateUpdate) => {
    setState(prev => {
      const newState = { ...prev, lastUpdate: update };

      switch (update.type) {
        case 'phase_transition':
          // Update phase state
          newState.phases = prev.phases.map(phase => 
            phase.id === update.data.phaseId 
              ? { ...phase, ...update.data }
              : phase
          );

          // Update project current stage if needed
          if (prev.project && update.data.currentStage) {
            newState.project = { ...prev.project, currentStage: update.data.currentStage };
          }
          break;

        case 'task_status':
          // Update task state
          newState.tasks = prev.tasks.map(task => 
            task.id === update.data.taskId 
              ? { ...task, ...update.data }
              : task
          );

          // Update project task activity
          if (prev.project && update.data.lastActivityAt) {
            newState.project = { ...prev.project, lastActivityAt: update.data.lastActivityAt };
          }
          break;

        case 'agent_result':
          // Handle agent execution results
          if (update.data.taskId) {
            newState.tasks = prev.tasks.map(task => 
              task.id === update.data.taskId 
                ? { 
                    ...task, 
                    status: update.data.success ? 'COMPLETED' : 'FAILED',
                    resultSummary: update.data.resultSummary,
                    errorMessage: update.data.errorMessage,
                    completedAt: update.data.completedAt
                  }
                : task
            );
          }
          break;
      }

      return newState;
    });
  }, []);

  // Handle WebSocket connection changes
  const handleConnectionChange = useCallback((connected: boolean) => {
    setState(prev => ({ ...prev, isConnected: connected }));
    
    // Refresh data when reconnecting
    if (connected && projectId) {
      loadInitialData();
    }
  }, [projectId, loadInitialData]);

  // Connect to WebSocket
  useEffect(() => {
    if (enableRealtime && wsServiceRef.current && projectId) {
      const wsService = wsServiceRef.current;

      // Set up event handlers
      const unsubscribeUpdates = wsService.onUpdate(handleWebSocketUpdate);
      const unsubscribeConnection = wsService.onConnectionChange(handleConnectionChange);

      // Connect to project-specific WebSocket
      wsService.connect(projectId);

      return () => {
        unsubscribeUpdates();
        unsubscribeConnection();
      };
    }
  }, [enableRealtime, projectId, handleWebSocketUpdate, handleConnectionChange]);

  // Load initial data when project changes
  useEffect(() => {
    loadInitialData();
  }, [loadInitialData]);

  // Manual refresh function
  const refresh = useCallback(() => {
    loadInitialData();
  }, [loadInitialData]);

  // Update local state functions
  const updateTask = useCallback((taskId: string, updates: Partial<Task>) => {
    setState(prev => ({
      ...prev,
      tasks: prev.tasks.map(task => 
        task.id === taskId ? { ...task, ...updates } : task
      )
    }));
  }, []);

  const updatePhase = useCallback((phaseId: string, updates: Partial<PhaseState>) => {
    setState(prev => ({
      ...prev,
      phases: prev.phases.map(phase => 
        phase.id === phaseId ? { ...phase, ...updates } : phase
      )
    }));
  }, []);

  const updateProject = useCallback((updates: Partial<Project>) => {
    setState(prev => ({
      ...prev,
      project: prev.project ? { ...prev.project, ...updates } : null
    }));
  }, []);

  return {
    // State
    ...state,

    // Actions
    refresh,
    updateTask,
    updatePhase,
    updateProject,

    // WebSocket control
    reconnect: () => {
      if (wsServiceRef.current && projectId) {
        wsServiceRef.current.connect(projectId);
      }
    },
    disconnect: () => {
      if (wsServiceRef.current) {
        wsServiceRef.current.disconnect();
      }
    }
  };
}
