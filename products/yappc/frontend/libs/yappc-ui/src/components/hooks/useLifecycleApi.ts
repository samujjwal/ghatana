/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - API Hooks for Repository Queries
 */

import { useState, useEffect, useCallback } from 'react';
import { LifecycleStageId } from '../components/lifecycle/LifecycleStage';

// Types matching backend entities
export interface Project {
  id: string;
  name: string;
  description: string;
  status: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
  currentStage: LifecycleStageId;
  startedAt: string;
  completedAt?: string;
  lastActivityAt: string;
  createdBy: string;
  taskIds: string[];
}

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  stage: LifecycleStageId;
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

export interface PhaseState {
  id: string;
  projectId: string;
  stage: LifecycleStageId;
  previousStage?: LifecycleStageId;
  status: 'ACTIVE' | 'COMPLETED' | 'BLOCKED' | 'SKIPPED';
  enteredAt: string;
  exitedAt?: string;
  triggerEvent: string;
  completionEvent?: string;
  entryCriteriaMet: Record<string, boolean>;
  exitCriteriaMet: Record<string, boolean>;
  producedArtifacts: string[];
  gateDecisions: Array<{
    agentId: string;
    condition: string;
    approved: boolean;
    reason: string;
    decidedAt: string;
  }>;
}

// API Client
const API_BASE = '/api/v1';

async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
    },
    ...options,
  });
  
  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`);
  }
  
  return response.json();
}

/**
 * useProjects Hook
 * 
 * Fetches and manages project list with filtering.
 */
export function useProjects(filters?: { status?: string; stage?: string }) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchProjects = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const queryParams = new URLSearchParams();
      if (filters?.status) queryParams.append('status', filters.status);
      if (filters?.stage) queryParams.append('stage', filters.stage);
      
      const data = await fetchApi<Project[]>(`/projects?${queryParams}`);
      setProjects(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [filters?.status, filters?.stage]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  return { projects, loading, error, refetch: fetchProjects };
}

/**
 * useProject Hook
 * 
 * Fetches single project with details.
 */
export function useProject(projectId: string) {
  const [project, setProject] = useState<Project | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchProject = useCallback(async () => {
    if (!projectId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const data = await fetchApi<Project>(`/projects/${projectId}`);
      setProject(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchProject();
  }, [fetchProject]);

  const advanceStage = useCallback(async () => {
    if (!projectId) return;
    
    const data = await fetchApi<Project>(`/projects/${projectId}/advance`, {
      method: 'POST',
    });
    setProject(data);
    return data;
  }, [projectId]);

  return { project, loading, error, refetch: fetchProject, advanceStage };
}

/**
 * useTasks Hook
 * 
 * Fetches tasks with filtering and search.
 */
export function useTasks(filters?: {
  projectId?: string;
  status?: string;
  stage?: string;
  assignedAgentId?: string;
  priority?: string;
}) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const queryParams = new URLSearchParams();
      if (filters?.projectId) queryParams.append('projectId', filters.projectId);
      if (filters?.status) queryParams.append('status', filters.status);
      if (filters?.stage) queryParams.append('stage', filters.stage);
      if (filters?.assignedAgentId) queryParams.append('assignedAgentId', filters.assignedAgentId);
      if (filters?.priority) queryParams.append('priority', filters.priority);
      
      const data = await fetchApi<Task[]>(`/tasks?${queryParams}`);
      setTasks(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [
    filters?.projectId,
    filters?.status,
    filters?.stage,
    filters?.assignedAgentId,
    filters?.priority,
  ]);

  useEffect(() => {
    fetchTasks();
  }, [fetchTasks]);

  const updateTaskStatus = useCallback(async (taskId: string, status: Task['status']) => {
    const data = await fetchApi<Task>(`/tasks/${taskId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
    
    setTasks(prev => prev.map(t => t.id === taskId ? data : t));
    return data;
  }, []);

  const retryTask = useCallback(async (taskId: string) => {
    const data = await fetchApi<Task>(`/tasks/${taskId}/retry`, {
      method: 'POST',
    });
    
    setTasks(prev => prev.map(t => t.id === taskId ? data : t));
    return data;
  }, []);

  return { 
    tasks, 
    loading, 
    error, 
    refetch: fetchTasks,
    updateTaskStatus,
    retryTask,
  };
}

/**
 * useTask Hook
 * 
 * Fetches single task details.
 */
export function useTask(taskId: string) {
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchTask = useCallback(async () => {
    if (!taskId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const data = await fetchApi<Task>(`/tasks/${taskId}`);
      setTask(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    fetchTask();
  }, [fetchTask]);

  return { task, loading, error, refetch: fetchTask };
}

/**
 * usePhaseStates Hook
 * 
 * Fetches phase state history for a project.
 */
export function usePhaseStates(projectId: string) {
  const [phaseStates, setPhaseStates] = useState<PhaseState[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchPhaseStates = useCallback(async () => {
    if (!projectId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const data = await fetchApi<PhaseState[]>(`/projects/${projectId}/phases`);
      setPhaseStates(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchPhaseStates();
  }, [fetchPhaseStates]);

  const currentPhase = phaseStates.find(p => p.status === 'ACTIVE');
  const completedPhases = phaseStates.filter(p => p.status === 'COMPLETED');
  const blockedPhases = phaseStates.filter(p => p.status === 'BLOCKED');

  return { 
    phaseStates, 
    currentPhase,
    completedPhases,
    blockedPhases,
    loading, 
    error, 
    refetch: fetchPhaseStates,
  };
}

/**
 * useProjectMetrics Hook
 * 
 * Fetches aggregated metrics for a project.
 */
export function useProjectMetrics(projectId: string) {
  const [metrics, setMetrics] = useState({
    totalTasks: 0,
    completedTasks: 0,
    inProgressTasks: 0,
    failedTasks: 0,
    averageTaskDuration: 0,
    phaseTransitionCount: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchMetrics = useCallback(async () => {
    if (!projectId) return;
    
    setLoading(true);
    setError(null);
    
    try {
      const data = await fetchApi<typeof metrics>(`/projects/${projectId}/metrics`);
      setMetrics(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchMetrics();
  }, [fetchMetrics]);

  return { metrics, loading, error, refetch: fetchMetrics };
}
