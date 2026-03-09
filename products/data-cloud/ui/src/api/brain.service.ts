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

import axios, { AxiosInstance } from 'axios';

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
  private client: AxiosInstance;

  constructor(baseURL: string = '/api') {
    this.client = axios.create({
      baseURL,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  // ==================== Workspace & Spotlight ====================

  /**
   * Fetch spotlight items from Global Workspace
   */
  async getSpotlight(): Promise<SpotlightItem[]> {
    const response = await this.client.get<SpotlightItem[]>('/workspace/spotlight');
    return response.data;
  }

  /**
   * Broadcast an emergency item to the workspace
   */
  async broadcastEmergency(item: Partial<SpotlightItem>): Promise<SpotlightItem> {
    const response = await this.client.post<SpotlightItem>('/workspace/broadcast', item);
    return response.data;
  }

  /**
   * Get brain statistics
   */
  async getBrainStats(): Promise<BrainStats> {
    const response = await this.client.get<BrainStats>('/workspace/stats');
    return response.data;
  }

  // ==================== Autonomy ====================

  /**
   * Fetch autonomy actions timeline
   */
  async getAutonomyTimeline(
    domain?: string,
    limit: number = 50
  ): Promise<AutonomyAction[]> {
    const response = await this.client.get<AutonomyAction[]>('/autonomy/timeline', {
      params: { domain, limit },
    });
    return response.data;
  }

  /**
   * Get autonomy state for a domain
   */
  async getAutonomyState(domain: string): Promise<AutonomyState> {
    const response = await this.client.get<AutonomyState>(`/autonomy/state/${domain}`);
    return response.data;
  }

  /**
   * Update autonomy policy for a domain
   */
  async updateAutonomyPolicy(
    domain: string,
    policy: Partial<AutonomyState>
  ): Promise<AutonomyState> {
    const response = await this.client.put<AutonomyState>(
      `/autonomy/policy/${domain}`,
      policy
    );
    return response.data;
  }

  // ==================== Memory ====================

  /**
   * Recall episodic memory
   */
  async recallMemory(query: string, tier?: string): Promise<MemoryRecall[]> {
    const response = await this.client.get<MemoryRecall[]>('/memory/recall', {
      params: { query, tier },
    });
    return response.data;
  }

  /**
   * Store memory entry
   */
  async storeMemory(memory: Partial<MemoryRecall>): Promise<MemoryRecall> {
    const response = await this.client.post<MemoryRecall>('/memory/store', memory);
    return response.data;
  }

  // ==================== Patterns ====================

  /**
   * Match patterns to current data
   */
  async matchPatterns(data: any): Promise<PatternMatch[]> {
    const response = await this.client.post<PatternMatch[]>('/patterns/match', data);
    return response.data;
  }

  /**
   * Create a new pattern
   */
  async createPattern(pattern: Partial<PatternMatch>): Promise<PatternMatch> {
    const response = await this.client.post<PatternMatch>('/patterns/create', pattern);
    return response.data;
  }

  /**
   * Get all patterns
   */
  async getPatterns(): Promise<PatternMatch[]> {
    const response = await this.client.get<PatternMatch[]>('/patterns');
    return response.data;
  }

  // ==================== Feedback & Learning ====================

  /**
   * Submit feedback event
   */
  async submitFeedback(feedback: FeedbackEvent): Promise<LearningSignal> {
    const response = await this.client.post<LearningSignal>('/feedback', feedback);
    return response.data;
  }

  /**
   * Get learning signals
   */
  async getLearningSignals(limit: number = 50): Promise<LearningSignal[]> {
    const response = await this.client.get<LearningSignal[]>('/feedback/signals', {
      params: { limit },
    });
    return response.data;
  }
}

/**
 * Default brain service instance
 */
export const brainService = new BrainService();

export default brainService;

