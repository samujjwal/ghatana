/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * Mastery Service API
 * 
 * Provides methods for interacting with mastery state, learning deltas,
 * obsolete items, and promotion queue endpoints.
 * 
 * @doc.purpose Mastery and learning API service
 * @doc.layer data-cloud-ui
 */

import axios from 'axios';

// Mastery State Types (aligned with Java MasteryState enum)
export type MasteryState =
  | 'UNKNOWN'
  | 'OBSERVED'
  | 'PRACTICED'
  | 'COMPETENT'
  | 'MASTERED'
  | 'MAINTENANCE_ONLY'
  | 'OBSOLETE'
  | 'RETIRED'
  | 'QUARANTINED';

export interface MasteryItem {
  masteryId: string;
  tenantId: string;
  skillId: string;
  agentId?: string;
  state: MasteryState;
  learningLevel: 'L0' | 'L1' | 'L2' | 'L3' | 'L4';
  versionScope?: {
    kind: string;
    name: string;
    range: string;
    ecosystem: string;
  };
  evidenceCount: number;
  lastTransitionedAt?: string;
  createdAt: string;
}

export interface MasteryQuery {
  tenantId: string;
  skillId?: string;
  agentId?: string;
  state?: string;
  limit?: number;
  offset?: number;
}

// Learning Delta Types
export interface LearningDelta {
  deltaId: string;
  tenantId: string;
  skillId: string;
  targetKind: 'semantic-fact' | 'negative-knowledge' | 'episodic-capture' | 'policy';
  targetId: string;
  state: 'PENDING_EVALUATION' | 'EVALUATED' | 'APPROVED' | 'REJECTED' | 'PROMOTED';
  learningLevel: 'L0' | 'L1' | 'L2' | 'L3' | 'L4';
  delta: Record<string, unknown>;
  evaluationResult?: {
    passed: boolean;
    score: number;
    reasons: string[];
  };
  approvalDecision?: {
    approved: boolean;
    approver: string;
    approvedAt: string;
    reason: string;
  };
  createdAt: string;
  evaluatedAt?: string;
  promotedAt?: string;
}

// Obsolescence Event Types
export interface ObsolescenceEvent {
  eventId: string;
  tenantId: string;
  skillId: string;
  detectionType: 'VERSION_MISMATCH' | 'STALE_EVIDENCE' | 'FAILED_VALIDATION' | 'MANUAL_MARK';
  evidenceRefs: {
    kind: 'semantic-fact' | 'negative-knowledge' | 'episodic-capture' | 'policy';
    ref: string;
  }[];
  metadata: Record<string, unknown>;
  detectedAt: string;
}

// Promotion Queue Types
export interface PromotionQueueItem {
  deltaId: string;
  tenantId: string;
  skillId: string;
  targetKind: string;
  targetId: string;
  state: string;
  evaluationResult?: {
    passed: boolean;
    score: number;
  };
  createdAt: string;
}

const API_BASE = '/api/v1/mastery';

/**
 * Mastery Service
 */
export const masteryService = {
  /**
   * Query mastery items
   */
  async queryMastery(query: MasteryQuery): Promise<MasteryItem[]> {
    const params = new URLSearchParams();
    params.append('tenantId', query.tenantId);
    if (query.skillId) params.append('skillId', query.skillId);
    if (query.agentId) params.append('agentId', query.agentId);
    if (query.state) params.append('state', query.state);
    if (query.limit) params.append('limit', query.limit.toString());
    if (query.offset) params.append('offset', query.offset.toString());

    const response = await axios.get<MasteryItem[]>(`${API_BASE}/query?${params.toString()}`);
    return response.data;
  },

  /**
   * Get mastery item by ID
   */
  async getMasteryItem(masteryId: string, tenantId: string): Promise<MasteryItem> {
    const response = await axios.get<MasteryItem>(`${API_BASE}/items/${masteryId}?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Find stale mastery items
   */
  async findStale(tenantId: string): Promise<MasteryItem[]> {
    const response = await axios.get<MasteryItem[]>(`${API_BASE}/stale?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Find obsolete or quarantined mastery items
   */
  async findObsoleteOrQuarantined(tenantId: string): Promise<MasteryItem[]> {
    const response = await axios.get<MasteryItem[]>(`${API_BASE}/obsolete-quarantined?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * List evidence for a mastery item
   */
  async listEvidence(masteryId: string, tenantId: string): Promise<{
    evidenceRefs: readonly string[];
    evaluationRefs: readonly string[];
    knownFailureModeIds: readonly string[];
  }> {
    const response = await axios.get(`${API_BASE}/items/${masteryId}/evidence?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * List transitions for a mastery item
   */
  async listTransitions(masteryId: string, tenantId: string): Promise<{
    state: MasteryState;
    stateHistory: readonly string[];
  }> {
    const response = await axios.get(`${API_BASE}/items/${masteryId}/transitions?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Get state distribution
   */
  async getStateDistribution(tenantId: string): Promise<Record<MasteryState, number>> {
    const response = await axios.get(`${API_BASE}/distribution?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Get version compatibility for a mastery item
   */
  async getVersionCompatibility(masteryId: string, tenantId: string): Promise<{
    versionScope?: {
      kind: string;
      name: string;
      range: string;
      ecosystem: string;
    };
    applicability: {
      tenantId: string;
      environmentFingerprint?: string;
    };
    staleAfter?: string;
  }> {
    const response = await axios.get(`${API_BASE}/items/${masteryId}/version-compatibility?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Get promotion history for a mastery item
   */
  async getPromotionHistory(masteryId: string, tenantId: string): Promise<{
    stateHistory: readonly string[];
    evidenceRefs: readonly string[];
    evaluationRefs: readonly string[];
  }> {
    const response = await axios.get(`${API_BASE}/items/${masteryId}/promotion-history?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Get skill evaluation status
   */
  async getSkillEvalStatus(skillId: string, tenantId: string): Promise<{
    skillId: string;
    hasMastery: boolean;
    state: MasteryState;
    confidence?: number;
    evaluationRefs?: readonly string[];
    knownFailureModeIds?: readonly string[];
  }> {
    const response = await axios.get(`${API_BASE}/skills/${skillId}/eval-status?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Scan for obsolescence events
   */
  async scanObsolescence(tenantId: string, env?: {
    repoId?: string;
    projectType?: string;
    dependencies?: Record<string, string>;
    runtimes?: Record<string, string>;
  }): Promise<ObsolescenceEvent[]> {
    const params = new URLSearchParams();
    params.append('tenantId', tenantId);
    if (env?.repoId) params.append('repoId', env.repoId);
    if (env?.projectType) params.append('projectType', env.projectType);
    if (env?.dependencies) {
      params.append('dependencies', Object.entries(env.dependencies)
        .map(([k, v]) => `${k}:${v}`).join(','));
    }
    if (env?.runtimes) {
      params.append('runtimes', Object.entries(env.runtimes)
        .map(([k, v]) => `${k}:${v}`).join(','));
    }

    const response = await axios.get<ObsolescenceEvent[]>(`${API_BASE}/obsolescence/scan?${params.toString()}`);
    return response.data;
  },

  /**
   * Get mode explanation for agent/skill
   */
  async getModeExplanation(agentId: string, skillId: string, tenantId: string): Promise<{
    agentId: string;
    skillId: string;
    tenantId: string;
    masteryState: MasteryState;
    confidence: number;
    isExecutable: boolean;
    executionMode: MasteryState;
    requiresApproval: boolean;
    requiresVerification: boolean;
    reasoning: string;
    versionScope?: {
      kind: string;
      name: string;
      range: string;
      ecosystem: string;
    };
  }> {
    const response = await axios.get(`${API_BASE}/mode-explanation?tenantId=${tenantId}&agentId=${agentId}&skillId=${skillId}`);
    return response.data;
  },

  /**
   * Query learning deltas (aligned with MasteryController.listLearningDeltas)
   */
  async queryLearningDeltas(tenantId: string, agentId?: string, skillId?: string, limit?: number, offset?: number): Promise<LearningDelta[]> {
    const params = new URLSearchParams();
    params.append('tenantId', tenantId);
    if (agentId) params.append('agentId', agentId);
    if (skillId) params.append('skillId', skillId);
    if (limit) params.append('limit', limit.toString());
    if (offset) params.append('offset', offset.toString());

    const response = await axios.get<LearningDelta[]>(`/api/v1/learning-deltas?${params.toString()}`);
    return response.data;
  },

  /**
   * Get learning delta by ID
   */
  async getLearningDelta(deltaId: string): Promise<LearningDelta> {
    const response = await axios.get<LearningDelta>(`/api/v1/learning-deltas/${deltaId}`);
    return response.data;
  },

  /**
   * Evaluate a learning delta (aligned with MasteryController.evaluateLearningDelta)
   */
  async evaluateLearningDelta(deltaId: string, tenantId: string): Promise<{
    deltaId: string;
    evaluationResult: {
      passed: boolean;
      score: number;
      reasons: string[];
    };
  }> {
    const response = await axios.post(`/api/v1/learning-deltas/${deltaId}/evaluate?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Promote a learning delta (aligned with MasteryController.promoteLearningDelta)
   */
  async promoteLearningDelta(deltaId: string, tenantId: string): Promise<LearningDelta> {
    const response = await axios.post<LearningDelta>(`/api/v1/learning-deltas/${deltaId}/promote?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Propose a learning delta
   */
  async proposeLearningDelta(delta: Partial<LearningDelta>): Promise<LearningDelta> {
    const response = await axios.post<LearningDelta>('/api/v1/learning-deltas', delta);
    return response.data;
  },

  /**
   * Approve a learning delta
   */
  async approveDelta(deltaId: string, reason: string): Promise<LearningDelta> {
    const response = await axios.post<LearningDelta>(`/api/v1/learning-deltas/${deltaId}/approve`, { reason });
    return response.data;
  },

  /**
   * Reject a learning delta
   */
  async rejectDelta(deltaId: string, reason: string): Promise<LearningDelta> {
    const response = await axios.post<LearningDelta>(`/api/v1/learning-deltas/${deltaId}/reject`, { reason });
    return response.data;
  },

  /**
   * Get pending learning deltas for an agent
   */
  async getPendingDeltas(agentId: string): Promise<LearningDelta[]> {
    const response = await axios.get<LearningDelta[]>(`/api/v1/learning-deltas/pending/${agentId}`);
    return response.data;
  },

  /**
   * Query obsolescence events
   */
  async queryObsolescenceEvents(tenantId: string, skillId?: string): Promise<ObsolescenceEvent[]> {
    const params = new URLSearchParams();
    params.append('tenantId', tenantId);
    if (skillId) params.append('skillId', skillId);

    const response = await axios.get<ObsolescenceEvent[]>(`${API_BASE}/obsolescence?${params.toString()}`);
    return response.data;
  },

  /**
   * Get promotion queue
   */
  async getPromotionQueue(tenantId: string): Promise<PromotionQueueItem[]> {
    const response = await axios.get<PromotionQueueItem[]>(`${API_BASE}/promotion-queue?tenantId=${tenantId}`);
    return response.data;
  },

  /**
   * Promote a learning delta
   */
  async promoteDelta(deltaId: string): Promise<LearningDelta> {
    const response = await axios.post<LearningDelta>(`/api/v1/learning-deltas/${deltaId}/promote`);
    return response.data;
  },

  /**
   * Get mastery statistics
   */
  async getMasteryStats(tenantId: string): Promise<{
    total: number;
    byState: Record<string, number>;
    byLearningLevel: Record<string, number>;
    pendingDeltas: number;
    obsoleteItems: number;
  }> {
    const response = await axios.get(`${API_BASE}/stats?tenantId=${tenantId}`);
    return response.data;
  },
};
