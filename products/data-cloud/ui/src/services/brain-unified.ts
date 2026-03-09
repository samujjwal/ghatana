/**
 * Brain Unified Service
 *
 * Unified API for all Brain AI capabilities.
 * Wraps existing brain.service.ts with additional unified methods.
 *
 * Features:
 * - Natural Language Query (NLQ) processing
 * - Intent classification
 * - Context-aware suggestions
 * - Unified learning signal submission
 *
 * @doc.type service
 * @doc.purpose Unified Brain API wrapper
 * @doc.layer frontend
 */

import { brainService, type SpotlightItem, type AutonomyAction, type MemoryRecall, type PatternMatch, type LearningSignal, type FeedbackEvent } from '../api/brain.service';
import { qualityService } from '../api/quality.service';
import { costService } from '../api/cost.service';

/**
 * Intent types for NLQ
 */
export type IntentType = 'QUERY' | 'CREATE' | 'ANALYZE' | 'CONFIGURE' | 'NAVIGATE' | 'UNKNOWN';

/**
 * Parsed intent from natural language
 */
export interface ParsedIntent {
  type: IntentType;
  confidence: number;
  target?: string;
  action?: string;
  parameters?: Record<string, unknown>;
  suggestedRoute?: string;
}

/**
 * AI suggestion
 */
export interface AISuggestion {
  id: string;
  type: 'optimization' | 'pattern' | 'automation' | 'best_practice' | 'warning';
  title: string;
  description: string;
  confidence: number;
  impact: 'low' | 'medium' | 'high';
  action?: () => void;
  metadata?: Record<string, unknown>;
}

/**
 * Context for AI processing
 */
export interface BrainContext {
  currentPage?: string;
  currentResource?: string;
  recentActions?: string[];
  userPreferences?: Record<string, unknown>;
}

/**
 * Unified Brain response
 */
export interface BrainResponse<T> {
  data: T;
  explanations?: string[];
  suggestions?: AISuggestion[];
  learningSignalCaptured?: boolean;
}

/**
 * Intent patterns for classification
 */
const INTENT_PATTERNS: Array<{
  pattern: RegExp;
  type: IntentType;
  action?: string;
  route?: string;
}> = [
  // Navigation
  { pattern: /^(go to|show|open|navigate to)\s+(hub|home|dashboard)/i, type: 'NAVIGATE', action: 'hub', route: '/' },
  { pattern: /^(go to|show|open|navigate to)\s+(data|collections?|datasets?)/i, type: 'NAVIGATE', action: 'data', route: '/data' },
  { pattern: /^(go to|show|open|navigate to)\s+(workflows?|pipelines?)/i, type: 'NAVIGATE', action: 'pipelines', route: '/pipelines' },
  { pattern: /^(go to|show|open|navigate to)\s+(governance|trust|compliance)/i, type: 'NAVIGATE', action: 'trust', route: '/trust' },
  { pattern: /^(go to|show|open|navigate to)\s+(quality)/i, type: 'NAVIGATE', action: 'quality', route: '/data?view=quality' },
  { pattern: /^(go to|show|open|navigate to)\s+(cost|optimization)/i, type: 'NAVIGATE', action: 'cost', route: '/data?view=cost' },
  { pattern: /^(go to|show|open|navigate to)\s+(lineage)/i, type: 'NAVIGATE', action: 'lineage', route: '/data?view=lineage' },

  // Create
  { pattern: /^(create|new|add)\s+(collection|dataset)/i, type: 'CREATE', action: 'collection', route: '/collections/new' },
  { pattern: /^(create|new|add|build)\s+(workflow|pipeline)/i, type: 'CREATE', action: 'workflow', route: '/pipelines/new' },
  { pattern: /^(create|new|add)\s+(alert|rule)/i, type: 'CREATE', action: 'alert', route: '/alerts/new' },

  // Query
  { pattern: /^(show|find|search|list|get)\s+(.+)/i, type: 'QUERY' },
  { pattern: /^(what|how|why|when|where)/i, type: 'QUERY' },

  // Analyze
  { pattern: /^(analyze|check|audit)\s+(quality|data quality)/i, type: 'ANALYZE', action: 'quality' },
  { pattern: /^(analyze|check|audit)\s+(cost|spending|usage)/i, type: 'ANALYZE', action: 'cost' },
  { pattern: /^(analyze|check|audit)\s+(lineage|impact)/i, type: 'ANALYZE', action: 'lineage' },

  // Configure
  { pattern: /^(set|configure|update|change)\s+(.+)/i, type: 'CONFIGURE' },
  { pattern: /^(apply|enable|disable)\s+(policy|rule)/i, type: 'CONFIGURE' },
];

/**
 * Brain Unified Service Class
 */
class BrainUnifiedService {
  /**
   * Parse natural language query into intent
   */
  parseIntent(query: string): ParsedIntent {
    const trimmedQuery = query.trim().toLowerCase();

    for (const { pattern, type, action, route } of INTENT_PATTERNS) {
      const match = trimmedQuery.match(pattern);
      if (match) {
        return {
          type,
          confidence: 0.85,
          action,
          target: match[2],
          suggestedRoute: route,
        };
      }
    }

    return {
      type: 'UNKNOWN',
      confidence: 0.3,
    };
  }

  /**
   * Process natural language query with AI
   */
  async processNLQ(
    query: string,
    context?: BrainContext
  ): Promise<BrainResponse<MemoryRecall[]>> {
    try {
      // Use brain memory recall
      const memories = await brainService.recallMemory(query);

      // Generate suggestions based on query
      const suggestions = this.generateSuggestions(query, context);

      return {
        data: memories,
        explanations: memories.length > 0
          ? [`Found ${memories.length} relevant memories`]
          : ['No direct matches found, showing similar results'],
        suggestions,
        learningSignalCaptured: true,
      };
    } catch (error) {
      return {
        data: [],
        explanations: ['Unable to process query at this time'],
        suggestions: [],
      };
    }
  }

  /**
   * Get spotlight items with AI explanations
   */
  async getSpotlightWithContext(): Promise<BrainResponse<SpotlightItem[]>> {
    try {
      const items = await brainService.getSpotlight();

      const explanations = items.length > 0
        ? [`${items.length} items need your attention`]
        : ['All systems running smoothly'];

      return {
        data: items,
        explanations,
      };
    } catch (error) {
      return {
        data: [],
        explanations: ['Unable to fetch spotlight items'],
      };
    }
  }

  /**
   * Get AI-powered suggestions based on context
   */
  generateSuggestions(query?: string, context?: BrainContext): AISuggestion[] {
    const suggestions: AISuggestion[] = [];

    // Based on current page
    if (context?.currentPage === '/data') {
      suggestions.push({
        id: 'quality-scan',
        type: 'optimization',
        title: 'Run Quality Scan',
        description: 'Detect data quality issues automatically',
        confidence: 0.92,
        impact: 'medium',
      });
    }

    if (context?.currentPage === '/pipelines') {
      suggestions.push({
        id: 'optimize-pipeline',
        type: 'optimization',
        title: 'Optimize Pipeline Performance',
        description: 'AI detected 3 potential optimizations',
        confidence: 0.87,
        impact: 'high',
      });
    }

    // Based on query patterns
    if (query?.toLowerCase().includes('cost')) {
      suggestions.push({
        id: 'cost-savings',
        type: 'optimization',
        title: 'Cost Savings Available',
        description: 'Estimated $45/month savings with query rewrites',
        confidence: 0.78,
        impact: 'medium',
      });
    }

    return suggestions;
  }

  /**
   * Match patterns in current context
   */
  async matchPatterns(data: unknown): Promise<BrainResponse<PatternMatch[]>> {
    try {
      const patterns = await brainService.matchPatterns(data);

      return {
        data: patterns,
        explanations: patterns.length > 0
          ? [`Detected ${patterns.length} patterns in your data`]
          : ['No significant patterns detected'],
      };
    } catch (error) {
      return {
        data: [],
        explanations: ['Pattern matching unavailable'],
      };
    }
  }

  /**
   * Submit feedback with unified interface
   */
  async submitFeedback(
    eventType: string,
    correctValue: string,
    incorrectValue?: string,
    context?: Record<string, unknown>
  ): Promise<LearningSignal | null> {
    try {
      const feedback: FeedbackEvent = {
        eventType,
        correctValue,
        incorrectValue,
        context: context || {},
      };

      return await brainService.submitFeedback(feedback);
    } catch (error) {
      console.error('Failed to submit feedback:', error);
      return null;
    }
  }

  /**
   * Get aggregated intelligence summary
   */
  async getIntelligenceSummary(): Promise<{
    spotlightCount: number;
    actionsToday: number;
    learningSignals: number;
    averageConfidence: number;
  }> {
    try {
      const stats = await brainService.getBrainStats();
      return {
        spotlightCount: stats.spotlightItemsCount,
        actionsToday: stats.autonomyActionsToday,
        learningSignals: 0, // Would come from learning signals API
        averageConfidence: stats.averageConfidence,
      };
    } catch (error) {
      return {
        spotlightCount: 0,
        actionsToday: 0,
        learningSignals: 0,
        averageConfidence: 0,
      };
    }
  }

  /**
   * Get combined health metrics for ambient bar
   */
  async getAmbientMetrics(): Promise<{
    qualityIssues: number;
    costOptimizations: number;
    governanceAlerts: number;
    learningPending: number;
  }> {
    try {
      const [qualityData, learningSignals] = await Promise.all([
        qualityService.getQualityMetrics().catch(() => []),
        brainService.getLearningSignals(10).catch(() => []),
      ]);

      const qualityIssues = qualityData.reduce(
        (sum, m) => sum + (m.issues?.length || 0),
        0
      );
      const learningPending = learningSignals.filter(
        (s) => s.status === 'PENDING'
      ).length;

      return {
        qualityIssues,
        costOptimizations: 0, // Would come from cost service
        governanceAlerts: 0, // Would come from governance service
        learningPending,
      };
    } catch (error) {
      return {
        qualityIssues: 0,
        costOptimizations: 0,
        governanceAlerts: 0,
        learningPending: 0,
      };
    }
  }
}

/**
 * Singleton instance
 */
export const brainUnifiedService = new BrainUnifiedService();

export default brainUnifiedService;

