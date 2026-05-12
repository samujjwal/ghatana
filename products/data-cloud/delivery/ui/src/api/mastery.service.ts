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

// Mastery State Types
export interface MasteryItem {
  masteryId: string;
  tenantId: string;
  skillId: string;
  agentId?: string;
  state: 'UNKNOWN' | 'LEARNING' | 'KNOWN' | 'MAINTENANCE_ONLY' | 'OBSOLETE';
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
  async getMasteryItem(masteryId: string): Promise<MasteryItem> {
    const response = await axios.get<MasteryItem>(`${API_BASE}/items/${masteryId}`);
    return response.data;
  },

  /**
   * Query learning deltas
   */
  async queryLearningDeltas(tenantId: string, state?: string): Promise<LearningDelta[]> {
    const params = new URLSearchParams();
    params.append('tenantId', tenantId);
    if (state) params.append('state', state);

    const response = await axios.get<LearningDelta[]>(`/api/v1/learning-deltas/query?${params.toString()}`);
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
   * Get pending learning deltas for an agent
   */
  async getPendingDeltas(agentId: string): Promise<LearningDelta[]> {
    const response = await axios.get<LearningDelta[]>(`/api/v1/learning-deltas/pending/${agentId}`);
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
