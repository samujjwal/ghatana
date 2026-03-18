/**
 * Lifecycle API Service
 *
 * Service layer for all lifecycle-related data operations.
 * Uses TanStack Query for caching and state management.
 *
 * @doc.type service
 * @doc.purpose Lifecycle data access layer
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { FOWStage, ArtifactType } from '@/types/fow-stages';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface Artifact {
  id: string;
  type: ArtifactType;
  title: string;
  description?: string;
  status: 'draft' | 'review' | 'approved';
  createdAt: Date;
  updatedAt: Date;
  createdBy: string;
  projectId: string;
  phase: LifecyclePhase;
  fowStage: FOWStage;
  content?: unknown;
  linkedArtifacts?: string[];
  version: number;
}

export interface Evidence {
  id: string;
  type: 'artifact' | 'decision' | 'insight' | 'audit';
  title: string;
  description?: string;
  timestamp: Date;
  phase: LifecyclePhase;
  fowStage: FOWStage;
  status?: 'draft' | 'review' | 'approved';
  artifactId?: string;
  metadata?: Record<string, unknown>;
}

export interface Task {
  id: string;
  title: string;
  description: string;
  phase: LifecyclePhase;
  fowStage: FOWStage;
  persona: string;
  priority: 'low' | 'medium' | 'high' | 'critical';
  estimatedEffort?: number;
  requiredInputs?: string[];
  expectedOutputs?: ArtifactType[];
  aiConfidence?: number;
  status: 'pending' | 'in-progress' | 'completed' | 'failed';
}

export interface GateStatus {
  stage: FOWStage;
  readiness: number;
  canProceed: boolean;
  missingArtifacts: {
    type: ArtifactType;
    required: number;
    current: number;
  }[];
  completedArtifacts: Artifact[];
  lastChecked: Date;
}

export interface AuditEvent {
  id: string;
  type: string; // ARTIFACT_VERB pattern
  timestamp: Date;
  userId: string;
  projectId: string;
  artifactId?: string;
  fowStage: FOWStage;
  phase: LifecyclePhase;
  metadata?: Record<string, unknown>;
  description: string;
}

export interface AIRecommendation {
  id: string;
  type: 'task' | 'insight' | 'validation' | 'enhancement';
  title: string;
  description: string;
  confidence: number;
  phase: LifecyclePhase;
  fowStage: FOWStage;
  persona: string;
  priority: 'low' | 'medium' | 'high';
  actionable: boolean;
  relatedArtifacts?: string[];
}

export interface DevSecOpsItem {
  id: string;
  type: 'task' | 'bug' | 'vulnerability' | 'debt';
  title: string;
  priority: 'low' | 'medium' | 'high' | 'critical';
  status: 'backlog' | 'todo' | 'in-progress' | 'done';
  assignee?: string;
  dueDate?: string;
  risk?: number;
}

// ============================================================================
// API Configuration
// ============================================================================

// Use VITE_API_ORIGIN for single-port architecture (Gateway on port 7002)
const API_BASE_URL = import.meta.env.DEV
  ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
  : '/api';

class APIError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string
  ) {
    super(message);
    this.name = 'APIError';
  }
}

async function fetchAPI<T>(
  endpoint: string,
  options?: RequestInit
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new APIError(
      error.message || 'API request failed',
      response.status,
      error.code
    );
  }

  return response.json();
}

// ============================================================================
// Artifact Operations
// ============================================================================

export const artifactAPI = {
  /**
   * Get all artifacts for a project
   */
  getArtifacts: async (projectId: string): Promise<Artifact[]> => {
    return fetchAPI<Artifact[]>(`/projects/${projectId}/artifacts`);
  },

  /**
   * Get artifact by ID
   */
  getArtifact: async (artifactId: string): Promise<Artifact> => {
    return fetchAPI<Artifact>(`/artifacts/${artifactId}`);
  },

  /**
   * Create new artifact
   */
  createArtifact: async (
    data: Omit<Artifact, 'id' | 'createdAt' | 'updatedAt' | 'version'>
  ): Promise<Artifact> => {
    return fetchAPI<Artifact>('/artifacts', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Update artifact
   */
  updateArtifact: async (
    artifactId: string,
    data: Partial<Artifact>
  ): Promise<Artifact> => {
    return fetchAPI<Artifact>(`/artifacts/${artifactId}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  },

  /**
   * Delete artifact
   */
  deleteArtifact: async (artifactId: string): Promise<void> => {
    return fetchAPI<void>(`/artifacts/${artifactId}`, {
      method: 'DELETE',
    });
  },

  /**
   * Get artifacts by type
   */
  getArtifactsByType: async (
    projectId: string,
    type: ArtifactType
  ): Promise<Artifact[]> => {
    return fetchAPI<Artifact[]>(
      `/projects/${projectId}/artifacts?type=${type}`
    );
  },

  /**
   * Get artifacts by FOW stage
   */
  getArtifactsByStage: async (
    projectId: string,
    stage: FOWStage
  ): Promise<Artifact[]> => {
    return fetchAPI<Artifact[]>(
      `/projects/${projectId}/artifacts?fowStage=${stage}`
    );
  },
};

// ============================================================================
// Evidence Operations
// ============================================================================

export const evidenceAPI = {
  /**
   * Get all evidence for a project
   */
  getEvidence: async (projectId: string): Promise<Evidence[]> => {
    return fetchAPI<Evidence[]>(`/projects/${projectId}/evidence`);
  },

  /**
   * Get evidence by type
   */
  getEvidenceByType: async (
    projectId: string,
    type: Evidence['type']
  ): Promise<Evidence[]> => {
    return fetchAPI<Evidence[]>(`/projects/${projectId}/evidence?type=${type}`);
  },

  /**
   * Create evidence entry
   */
  createEvidence: async (data: Omit<Evidence, 'id'>): Promise<Evidence> => {
    return fetchAPI<Evidence>('/evidence', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },
};

// ============================================================================
// Gate Operations
// ============================================================================

export const gateAPI = {
  /**
   * Check gate readiness for a FOW stage
   */
  checkGate: async (
    projectId: string,
    stage: FOWStage
  ): Promise<GateStatus> => {
    return fetchAPI<GateStatus>(`/projects/${projectId}/gates/${stage}`);
  },

  /**
   * Transition to next FOW stage (requires gate passing)
   */
  transitionStage: async (
    projectId: string,
    targetStage: FOWStage
  ): Promise<{ success: boolean; currentStage: FOWStage }> => {
    return fetchAPI(`/projects/${projectId}/stages/transition`, {
      method: 'POST',
      body: JSON.stringify({ targetStage }),
    });
  },
};

// ============================================================================
// Task Operations
// ============================================================================

export const taskAPI = {
  /**
   * Get next best task recommendation
   */
  getNextBestTask: async (
    projectId: string,
    phase: LifecyclePhase,
    persona?: string
  ): Promise<Task> => {
    const params = new URLSearchParams({ phase });
    if (persona) params.append('persona', persona);
    return fetchAPI<Task>(`/projects/${projectId}/tasks/next?${params}`);
  },

  /**
   * Execute task
   */
  executeTask: async (
    taskId: string,
    input: Record<string, unknown>
  ): Promise<{
    taskId: string;
    status: 'completed' | 'failed';
    steps: Array<{ id: string; status: string; output?: string }>;
    artifacts: Artifact[];
    logs: string[];
  }> => {
    return fetchAPI(`/tasks/${taskId}/execute`, {
      method: 'POST',
      body: JSON.stringify({ input }),
    });
  },

  /**
   * Get task status
   */
  getTaskStatus: async (taskId: string): Promise<Task> => {
    return fetchAPI<Task>(`/tasks/${taskId}`);
  },
};

// ============================================================================
// DevSecOps Operations
// ============================================================================

export const devSecOpsAPI = {
  /**
   * Get DevSecOps items for a project
   */
  getItems: async (projectId: string): Promise<DevSecOpsItem[]> => {
    return fetchAPI<DevSecOpsItem[]>(`/projects/${projectId}/devsecops`);
  },
};

// ============================================================================
// AI Operations
// ============================================================================

export const aiAPI = {
  /**
   * Get AI recommendations
   */
  getRecommendations: async (
    projectId: string,
    context: {
      phase: LifecyclePhase;
      fowStage: FOWStage;
      persona?: string;
      recentActivity?: string[];
    }
  ): Promise<AIRecommendation[]> => {
    return fetchAPI<AIRecommendation[]>(
      `/projects/${projectId}/ai/recommendations`,
      {
        method: 'POST',
        body: JSON.stringify(context),
      }
    );
  },

  /**
   * Get AI insights
   */
  getInsights: async (projectId: string): Promise<Evidence[]> => {
    return fetchAPI<Evidence[]>(`/projects/${projectId}/ai/insights`);
  },

  /**
   * Validate artifact with AI
   */
  validateArtifact: async (
    artifactId: string
  ): Promise<{
    valid: boolean;
    issues: Array<{ severity: string; message: string }>;
    suggestions: string[];
  }> => {
    return fetchAPI(`/artifacts/${artifactId}/validate`, {
      method: 'POST',
    });
  },
};

// ============================================================================
// Audit Operations
// ============================================================================

export const auditAPI = {
  /**
   * Get audit events for a project
   */
  getAuditEvents: async (
    projectId: string,
    filters?: {
      fowStage?: FOWStage;
      phase?: LifecyclePhase;
      startDate?: Date;
      endDate?: Date;
    }
  ): Promise<AuditEvent[]> => {
    const params = new URLSearchParams();
    if (filters?.fowStage !== undefined)
      params.append('fowStage', filters.fowStage.toString());
    if (filters?.phase) params.append('phase', filters.phase);
    if (filters?.startDate)
      params.append('startDate', filters.startDate.toISOString());
    if (filters?.endDate)
      params.append('endDate', filters.endDate.toISOString());

    const query = params.toString();
    return fetchAPI<AuditEvent[]>(
      `/projects/${projectId}/audit${query ? `?${query}` : ''}`
    );
  },

  /**
   * Emit audit event
   */
  emitEvent: async (
    event: Omit<AuditEvent, 'id' | 'timestamp'>
  ): Promise<AuditEvent> => {
    return fetchAPI<AuditEvent>('/audit/events', {
      method: 'POST',
      body: JSON.stringify(event),
    });
  },
};

// ============================================================================
// Persona Operations
// ============================================================================

export const personaAPI = {
  /**
   * Derive persona from context
   */
  derivePersona: async (context: {
    projectId: string;
    phase: LifecyclePhase;
    fowStage: FOWStage;
    recentTasks?: string[];
    recentArtifacts?: ArtifactType[];
  }): Promise<{
    persona: string;
    confidence: number;
    reasoning: string;
  }> => {
    return fetchAPI('/personas/derive', {
      method: 'POST',
      body: JSON.stringify(context),
    });
  },
};

// ============================================================================
// Export all APIs
// ============================================================================

export const lifecycleAPI = {
  artifacts: artifactAPI,
  evidence: evidenceAPI,
  gates: gateAPI,
  tasks: taskAPI,
  ai: aiAPI,
  audit: auditAPI,
  personas: personaAPI,
};
