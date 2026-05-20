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
import {
  AutonomyLevelOverrideRequestSchema,
  AutonomyLevelOverrideResponseSchema,
  AutonomyLevelStatusSchema,
  AutonomyLogsResponseSchema,
  AutonomyStateResponseSchema,
  BrainAttentionThresholdsSchema,
  BrainAttentionThresholdUpdateResponseSchema,
  BrainElevationResultSchema,
  BrainPatternListResponseSchema,
  BrainPatternMatchResponseSchema,
  BrainRuntimeStatsSchema,
  BrainSalienceResponseSchema,
  BrainWorkspaceStatusSchema,
  LearningSignalSchema,
  LearningStatusResponseSchema,
  MemoryRootListResponseSchema,
  type AutonomyLevelOverrideRequest as BackendAutonomyLevelOverrideRequest,
  type AutonomyLevelOverrideResponse as BackendAutonomyLevelOverrideResponse,
  type AutonomyLevelStatus as BackendAutonomyLevelStatus,
  type AutonomyLog as BackendAutonomyLog,
  type AutonomyLogsResponse as BackendAutonomyLogsResponse,
  type AutonomyStateResponse as BackendAutonomyStateResponse,
  type BrainAttentionThresholds as BackendAttentionThresholdsResponse,
  type BrainAttentionThresholdUpdateResponse as BackendAttentionThresholdUpdateResponse,
  type BrainElevationResult as BackendBrainElevationResponse,
  type BrainPattern as BackendBrainPattern,
  type BrainPatternListResponse as BackendBrainPatternListResponse,
  type BrainPatternMatch as BackendBrainPatternMatch,
  type BrainPatternMatchResponse as BackendBrainPatternMatchResponse,
  type BrainRuntimeStats as BackendBrainStatsResponse,
  type BrainSalienceResponse as BackendBrainSalienceResponse,
  type BrainWorkspaceStatus as BackendWorkspaceStatusResponse,
  type LearningSignal as BackendLearningSignal,
  type LearningStatusResponse as BackendLearningStatusResponse,
  type MemoryItem as BackendMemoryItem,
  type MemoryRootListResponse as BackendMemoryRootListResponse,
} from '../contracts/schemas';
import {
  AUTONOMY_POLICY_UPDATE_BOUNDARY_MESSAGE,
  BRAIN_MEMORY_STORE_BOUNDARY_MESSAGE,
  BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE,
  UnsupportedRuntimeBoundaryError,
} from '@/lib/runtime-boundaries';
import { isBrainAutonomyEnabled } from '../lib/feature-gates';

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
  metadata: Record<string, unknown>;
}

const NO_SPOTLIGHT_ITEMS: readonly SpotlightItem[] = Object.freeze([]);

export interface AutonomyAction {
  id: string;
  timestamp: string;
  domain: string;
  action: string;
  // Autonomy decision status (different domain from execution lifecycle)
  status: 'SUCCESS' | 'FAILED' | 'ADVISORY';
  confidence: number;
  outcome?: string;
  metadata: Record<string, unknown>;
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
  tier: 'EPISODIC' | 'SEMANTIC' | 'PROCEDURAL' | 'PREFERENCE';
  similarity: number;
  content: string;
  context: Record<string, unknown>;
}

export interface PatternMatch {
  patternId: string;
  name: string;
  confidence: number;
  description: string;
  historicalOccurrences: number;
  lastSeen?: string;
  metadata: Record<string, unknown>;
}

export interface FeedbackEvent {
  eventType: string;
  correctValue: string;
  incorrectValue?: string;
  context: Record<string, unknown>;
  tags?: string[];
}

export type LearningSignal = BackendLearningSignal;

export interface BrainStats {
  totalRecordsProcessed: number;
  activePatterns: number;
  activeRules: number;
  hotTierRecords: number;
  warmTierRecords: number;
  avgProcessingTimeMs: number;
  uptimeSeconds: number;
  tenantId: string;
  timestamp: string;
}

export interface AttentionThresholds {
  elevation: number;
  emergency: number;
  salience: number;
}

/**
 * Brain API Client
 */
export class BrainService {
  private mapAutonomyMode(level: BackendAutonomyStateResponse['state']['currentLevel']): AutonomyState['mode'] {
    switch (level) {
      case 'AUTONOMOUS':
        return 'AUTONOMOUS';
      case 'NOTIFY':
        return 'CHATTY';
      case 'SUGGEST':
      case 'CONFIRM':
      default:
        return 'ADVISORY';
    }
  }

  private mapAutonomyAction(log: BackendAutonomyLog): AutonomyAction {
    const status: AutonomyAction['status'] = log.decision === 'ALLOWED'
      ? 'SUCCESS'
      : log.decision === 'BLOCKED'
        ? 'FAILED'
        : 'ADVISORY';

    return {
      id: log.id,
      timestamp: log.timestamp,
      domain: log.actionType,
      action: `${log.decision} (${log.level})`,
      status,
      confidence: log.confidence,
      outcome: log.level,
      metadata: log.context ?? {},
    };
  }

  private mapAutonomyState(domain: string, response: BackendAutonomyStateResponse): AutonomyState {
    return {
      domain,
      mode: this.mapAutonomyMode(response.state.currentLevel),
      enabled: true,
      confidenceThreshold: response.state.confidence ?? 0.8,
      lastAction: response.state.lastActionAt,
    };
  }

  private mapMemoryRecall(item: BackendMemoryItem): MemoryRecall {
    return {
      id: item.id,
      timestamp: item.createdAt,
      tier: item.type,
      similarity: item.salience,
      content: item.content,
      context: item.metadata,
    };
  }

  private mapBrainStats(response: BackendBrainStatsResponse): BrainStats {
    return {
      totalRecordsProcessed: response.totalRecordsProcessed,
      activePatterns: response.activePatterns,
      activeRules: response.activeRules,
      hotTierRecords: response.hotTierRecords,
      warmTierRecords: response.warmTierRecords,
      avgProcessingTimeMs: response.avgProcessingTimeMs,
      uptimeSeconds: response.uptimeSeconds,
      tenantId: response.tenantId,
      timestamp: response.timestamp,
    };
  }

  private mapAttentionThresholds(response: BackendAttentionThresholdsResponse): AttentionThresholds {
    return {
      elevation: response.elevationThreshold,
      emergency: response.emergencyThreshold,
      salience: response.salienceThreshold,
    };
  }

  private mapStoredPattern(pattern: BackendBrainPattern): PatternMatch {
    return {
      patternId: pattern.id,
      name: pattern.name,
      confidence: pattern.confidence,
      description: pattern.description,
      historicalOccurrences: pattern.observations,
      lastSeen: pattern.updatedAt,
      metadata: {
        type: pattern.type,
        discoveredAt: pattern.discoveredAt,
      },
    };
  }

  private mapMatchedPattern(match: BackendBrainPatternMatch): PatternMatch {
    return {
      patternId: match.patternId ?? 'unmatched',
      name: match.patternName ?? 'Unmatched pattern',
      confidence: match.confidence,
      description: match.explanation,
      historicalOccurrences: 0,
      metadata: {
        score: match.score,
      },
    };
  }

  private mapLearningSignals(response: BackendLearningStatusResponse, limit: number): LearningSignal[] {
    const signals: LearningSignal[] = [];
    const lastResult = response.lastResult;

    if (lastResult?.ranAt) {
      signals.push({
        id: `learning-run-${lastResult.ranAt}`,
        timestamp: lastResult.ranAt,
        signalType: lastResult.manual ? 'manual-learning-cycle' : 'scheduled-learning-cycle',
        impact: Math.min(
          1,
          ((lastResult.patternsDiscovered ?? 0) + (lastResult.patternsUpdated ?? 0)) / 10,
        ),
        status: lastResult.status === 'COMPLETED'
          ? 'APPLIED'
          : response.running
            ? 'PENDING'
            : 'PROCESSED',
        affectedComponents: ['brain', 'learning-bridge'],
      });
    }

    if (response.pendingReviews > 0) {
      signals.push({
        id: 'learning-pending-reviews',
        timestamp: response.timestamp ?? response.nextScheduledRun,
        signalType: 'pending-review-queue',
        impact: Math.min(1, response.pendingReviews / 10),
        status: 'PENDING',
        affectedComponents: ['brain', 'review-queue'],
      });
    }

    return signals.slice(0, limit);
  }

  // ==================== Workspace & Spotlight ====================

  /**
   * Fetch current GlobalWorkspace spotlight entries
   */
  async getSpotlight(): Promise<SpotlightItem[]> {
    const response = await apiClient.get<BackendWorkspaceStatusResponse>('/brain/workspace');
    BrainWorkspaceStatusSchema.parse(response);
    void response;
    return Array.from(NO_SPOTLIGHT_ITEMS);
  }

  /**
   * Elevate salience of an item in the workspace
   */
  async broadcastEmergency(item: Partial<SpotlightItem>): Promise<SpotlightItem> {
    const rawResponse = await apiClient.post<BackendBrainElevationResponse>('/brain/attention/elevate', {
      id: item.id,
      content: item.summary ?? '',
      reason: item.metadata?.reason ?? 'manual-ui-elevation',
      emergency: item.emergency ?? true,
    });
    const response = BrainElevationResultSchema.parse(rawResponse);

    return {
      id: response.recordId,
      tenantId: response.tenantId,
      summary: item.summary ?? response.reason,
      salienceScore: {
        score: response.elevated ? 1 : 0,
        breakdown: {
          recency: response.elevated ? 1 : 0,
          novelty: 0,
          impact: response.emergency ? 1 : 0.5,
          urgency: response.emergency ? 1 : 0.5,
        },
      },
      emergency: response.emergency,
      priority: response.emergency ? 1 : 5,
      category: 'manual-elevation',
      spotlightedAt: response.timestamp,
      expiresAt: response.timestamp,
      accessCount: 0,
      tags: [],
      metadata: {
        action: response.action,
        reason: response.reason,
      },
    };
  }

  /**
   * Get brain statistics
   */
  async getBrainStats(): Promise<BrainStats> {
    const rawResponse = await apiClient.get<BackendBrainStatsResponse>('/brain/stats');
    const response = BrainRuntimeStatsSchema.parse(rawResponse);
    return this.mapBrainStats(response);
  }

  /**
   * Get current attention thresholds
   */
  async getAttentionThresholds(): Promise<AttentionThresholds> {
    const rawResponse = await apiClient.get<BackendAttentionThresholdsResponse>('/brain/attention/thresholds');
    const response = BrainAttentionThresholdsSchema.parse(rawResponse);
    return this.mapAttentionThresholds(response);
  }

  /**
   * Update attention thresholds
   */
  async updateAttentionThresholds(thresholds: { elevation?: number; emergency?: number }): Promise<AttentionThresholds> {
    const rawResponse = await apiClient.put<BackendAttentionThresholdUpdateResponse>('/brain/attention/thresholds', thresholds);
    const response = BrainAttentionThresholdUpdateResponseSchema.parse(rawResponse);
    void response;
    return {
      elevation: thresholds.elevation ?? 0,
      emergency: thresholds.emergency ?? 0,
      salience: 0,
    };
  }

  /**
   * Get salience score for a specific item
   */
  async getSalienceScore(itemId: string): Promise<SalienceScore> {
    const rawResponse = await apiClient.get<BackendBrainSalienceResponse>(`/brain/salience/${itemId}`);
    const response = BrainSalienceResponseSchema.parse(rawResponse);
    return {
      score: response.salienceScore,
      breakdown: {
        recency: response.salienceScore,
        novelty: response.isHigh ? response.salienceScore : 0,
        impact: response.isEmergency ? response.salienceScore : Math.min(response.salienceScore, 0.5),
        urgency: response.priority > 1 ? Math.min(response.salienceScore, 0.5) : response.salienceScore,
      },
    };
  }

  // ==================== Autonomy ====================

  /**
   * Fetch autonomy actions timeline
   */
  async getAutonomyTimeline(
    domain?: string,
    limit: number = 50
  ): Promise<AutonomyAction[]> {
    if (!isBrainAutonomyEnabled()) {
      throw new UnsupportedRuntimeBoundaryError(BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE);
    }
    const rawResponse = await apiClient.get<BackendAutonomyLogsResponse>('/autonomy/logs');
    const response = AutonomyLogsResponseSchema.parse(rawResponse);
    const actions = response.logs.map((log) => this.mapAutonomyAction(log));
    const filtered = domain ? actions.filter((action) => action.domain === domain) : actions;
    return filtered.slice(0, limit);
  }

  /**
   * Get autonomy state for a domain
   */
  async getAutonomyState(domain: string): Promise<AutonomyState> {
    const rawResponse = await apiClient.get<BackendAutonomyStateResponse>(`/autonomy/domains/${domain}`);
    const response = AutonomyStateResponseSchema.parse(rawResponse);
    return this.mapAutonomyState(domain, response);
  }

  /**
   * Update autonomy policy for a domain
   */
  async updateAutonomyPolicy(
    domain: string,
    policy: Partial<AutonomyState>
  ): Promise<AutonomyState> {
    void domain;
    void policy;
    throw new UnsupportedRuntimeBoundaryError(AUTONOMY_POLICY_UPDATE_BOUNDARY_MESSAGE);
  }

  /**
   * Emergency global autonomy shutoff (B9).
   *
   * Sets all domains to SUGGEST (full human-in-the-loop) via
   * PUT /api/v1/autonomy/level.
   *
   * @param level   - target level; defaults to 'SUGGEST' for emergency halt
   * @param reason  - audit reason string
   */
  async setGlobalAutonomyLevel(
    level: 'SUGGEST' | 'CONFIRM' | 'NOTIFY' | 'AUTONOMOUS',
    reason: string
  ): Promise<BackendAutonomyLevelOverrideResponse> {
    if (!isBrainAutonomyEnabled()) {
      throw new UnsupportedRuntimeBoundaryError(BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE);
    }
    const request = AutonomyLevelOverrideRequestSchema.parse({ level, reason }) as BackendAutonomyLevelOverrideRequest;
    const rawResponse = await apiClient.put<BackendAutonomyLevelOverrideResponse, BackendAutonomyLevelOverrideRequest>(
      '/autonomy/level',
      request
    );
    return AutonomyLevelOverrideResponseSchema.parse(rawResponse);
  }

  /**
   * Get current global autonomy override level (B9).
   */
  async getGlobalAutonomyLevel(): Promise<BackendAutonomyLevelStatus> {
    if (!isBrainAutonomyEnabled()) {
      throw new UnsupportedRuntimeBoundaryError(BRAIN_AUTONOMY_DISABLED_BOUNDARY_MESSAGE);
    }
    const rawResponse = await apiClient.get<BackendAutonomyLevelStatus>(
      '/autonomy/level'
    );
    return AutonomyLevelStatusSchema.parse(rawResponse);
  }

  // ==================== Memory ====================

  /**
   * Recall episodic memory (delegates to memory plane)
   */
  async recallMemory(query: string, tier?: string): Promise<MemoryRecall[]> {
    const rawResponse = await apiClient.get<BackendMemoryRootListResponse>('/memory', {
      params: {
        query,
        ...(tier ? { type: tier.toLowerCase() } : {}),
      },
    });
    const response = MemoryRootListResponseSchema.parse(rawResponse);

    return response.items.map((item) => this.mapMemoryRecall(item));
  }

  /**
   * Store memory entry
   */
  async storeMemory(memory: Partial<MemoryRecall>): Promise<MemoryRecall> {
    void memory;
    throw new UnsupportedRuntimeBoundaryError(BRAIN_MEMORY_STORE_BOUNDARY_MESSAGE);
  }

  // ==================== Patterns ====================

  /**
   * Match patterns via ReflexEngine
   */
  async matchPatterns(data: Record<string, unknown>): Promise<PatternMatch[]> {
    const rawResponse = await apiClient.post<BackendBrainPatternMatchResponse>('/brain/patterns/match', data);
    const response = BrainPatternMatchResponseSchema.parse(rawResponse);
    return response.matches.map((match) => this.mapMatchedPattern(match));
  }

  /**
   * Get all patterns from PatternCatalog
   */
  async getPatterns(): Promise<PatternMatch[]> {
    const rawResponse = await apiClient.get<BackendBrainPatternListResponse>('/brain/patterns');
    const response = BrainPatternListResponseSchema.parse(rawResponse);
    return response.patterns.map((pattern) => this.mapStoredPattern(pattern));
  }

  // ==================== Feedback & Learning ====================

  /**
   * Submit feedback event
   */
  async submitFeedback(feedback: FeedbackEvent): Promise<LearningSignal> {
    const rawResponse = await apiClient.post<LearningSignal>('/learning/trigger', feedback);
    return LearningSignalSchema.parse(rawResponse);
  }

  /**
   * Get learning signals
   */
  async getLearningSignals(limit: number = 50): Promise<LearningSignal[]> {
    const rawResponse = await apiClient.get<BackendLearningStatusResponse>('/learning/status', {
      params: { limit },
    });
    const response = LearningStatusResponseSchema.parse(rawResponse);
    return this.mapLearningSignals(response, limit);
  }
}

/**
 * Default brain service instance
 */
export const brainService = new BrainService();

export default brainService;

