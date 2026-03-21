/**
 * Brain API Service
 *
 * Provides API client for Data Cloud Brain operations.
 * Supports Global Workspace, Autonomy, Memory, Patterns, and Feedback.
 *
 * @doc.type service
 * @doc.purpose Brain API client for Glass Box Intelligence
 * @doc.layer frontend
 */

import { apiClient } from '../lib/api/client';

export interface SalienceScore {
  score: number;
  breakdown: {
    recency: number;
    novelty: number;
    impact: number;
    urgency: number;
  };
}

export interface SpotlightItem {
  id: string;
  tenantId: string;
  summary: string;
  salienceScore: SalienceScore;
  emergency: boolean;
  priority: number;
  category: string;
  spotlightedAt: string;
  expiresAt: string;
  accessCount: number;
  tags: string[];
  metadata: Record<string, any>;
}

export interface AutonomyAction {
  id: string;
  timestamp: string;
  domain: string;
  action: string;
  status: 'SUCCESS' | 'FAILED' | 'PENDING' | 'ADVISORY';
  confidence: number;
  outcome?: string;
  metadata: Record<string, any>;
}

export interface AutonomyState {
  domain: string;
  mode: 'ADVISORY' | 'CHATTY' | 'AUTONOMOUS';
  enabled: boolean;
  confidenceThreshold: number;
  lastAction?: string;
}

export interface MemoryRecall {
  id: string;
  timestamp: string;
  tier: 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL';
  similarity: number;
  content: string;
  context: Record<string, any>;
}

export interface PatternMatch {
  patternId: string;
  name: string;
  confidence: number;
  description: string;
  historicalOccurrences: number;
  lastSeen?: string;
  metadata: Record<string, any>;
}

export interface FeedbackEvent {
  eventType: string;
  correctValue: string;
  incorrectValue?: string;
  context: Record<string, any>;
  tags?: string[];
}

export interface LearningSignal {
  id: string;
  timestamp: string;
  signalType: string;
  impact: number;
  status: 'PENDING' | 'PROCESSED' | 'APPLIED';
  affectedComponents: string[];
}

export interface BrainStats {
  spotlightItemsCount: number;
  autonomyActionsToday: number;
  averageConfidence: number;
  activeSubsystems: number;
}

/**
 * Brain API Client
 */
export class BrainService {
  // ==================== Workspace & Spotlight ====================

  /**
   * Fetch current GlobalWorkspace spotlight entries
   */
  async getSpotlight(): Promise<SpotlightItem[]> {
    return apiClient.get<SpotlightItem[]>('/brain/workspace');
  }

  /**
   * Elevate salience of an item in the workspace
   */
  async broadcastEmergency(item: Partial<SpotlightItem>): Promise<SpotlightItem> {
    return apiClient.post<SpotlightItem>('/brain/attention/elevate', item);
  }

  /**
   * Get brain statistics
   */
  async getBrainStats(): Promise<BrainStats> {
    return apiClient.get<BrainStats>('/brain/stats');
  }

  /**
   * Get current attention thresholds
   */
  async getAttentionThresholds(): Promise<{ elevation: number; emergency: number }> {
    return apiClient.get<{ elevation: number; emergency: number }>('/brain/attention/thresholds');
  }

  /**
   * Update attention thresholds
   */
  async updateAttentionThresholds(thresholds: { elevation?: number; emergency?: number }): Promise<void> {
    await apiClient.put<void>('/brain/attention/thresholds', thresholds);
  }

  /**
   * Get salience score for a specific item
   */
  async getSalienceScore(itemId: string): Promise<SalienceScore> {
    return apiClient.get<SalienceScore>(`/brain/salience/${itemId}`);
  }

  // ==================== Autonomy ====================

  /**
   * Fetch autonomy actions timeline
   */
  async getAutonomyTimeline(
    domain?: string,
    limit: number = 50
  ): Promise<AutonomyAction[]> {
    return apiClient.get<AutonomyAction[]>('/brain/autonomy/timeline', {
      params: { domain, limit },
    });
  }

  /**
   * Get autonomy state for a domain
   */
  async getAutonomyState(domain: string): Promise<AutonomyState> {
    return apiClient.get<AutonomyState>(`/brain/autonomy/state/${domain}`);
  }

  /**
   * Update autonomy policy for a domain
   */
  async updateAutonomyPolicy(
    domain: string,
    policy: Partial<AutonomyState>
  ): Promise<AutonomyState> {
    return apiClient.put<AutonomyState>(
      `/brain/autonomy/policy/${domain}`,
      policy
    );
  }

  // ==================== Memory ====================

  /**
   * Recall episodic memory (delegates to memory plane)
   */
  async recallMemory(query: string, tier?: string): Promise<MemoryRecall[]> {
    return apiClient.get<MemoryRecall[]>('/memory/recall', {
      params: { query, tier },
    });
  }

  /**
   * Store memory entry
   */
  async storeMemory(memory: Partial<MemoryRecall>): Promise<MemoryRecall> {
    return apiClient.post<MemoryRecall>('/memory/store', memory);
  }

  // ==================== Patterns ====================

  /**
   * Match patterns via ReflexEngine
   */
  async matchPatterns(data: Record<string, unknown>): Promise<PatternMatch[]> {
    return apiClient.post<PatternMatch[]>('/brain/patterns/match', data);
  }

  /**
   * Get all patterns from PatternCatalog
   */
  async getPatterns(): Promise<PatternMatch[]> {
    return apiClient.get<PatternMatch[]>('/brain/patterns');
  }

  // ==================== Feedback & Learning ====================

  /**
   * Submit feedback event
   */
  async submitFeedback(feedback: FeedbackEvent): Promise<LearningSignal> {
    return apiClient.post<LearningSignal>('/learning/trigger', feedback);
  }

  /**
   * Get learning signals
   */
  async getLearningSignals(limit: number = 50): Promise<LearningSignal[]> {
    return apiClient.get<LearningSignal[]>('/learning/status', {
      params: { limit },
    });
  }
}

/**
 * Default brain service instance
 */
export const brainService = new BrainService();

export default brainService;

