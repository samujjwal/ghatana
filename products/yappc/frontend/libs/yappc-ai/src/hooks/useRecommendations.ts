/**
 * Recommendations Hook
 *
 * React hook for AI-powered recommendations including assignees, tags,
 * similar items, and next actions.
 *
 * @module ai/hooks/useRecommendations
 * @doc.type hook
 * @doc.purpose AI recommendations management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { AIAgentClientFactory } from '../agents';

/**
 * Recommendation types
 */
export type RecommendationType =
  | 'ASSIGNEE'
  | 'TAG'
  | 'PRIORITY'
  | 'PHASE'
  | 'SIMILAR_ITEMS'
  | 'NEXT_ACTION'
  | 'WORKFLOW'
  | 'TIME_ESTIMATE'
  | 'LABEL'
  | 'DEPENDENCY';

/**
 * Single recommendation
 */
export interface Recommendation {
  id: string;
  type: RecommendationType;
  value: string | Record<string, unknown>;
  displayValue: string;
  confidence: number;
  reason: string;
  source: 'collaborative' | 'content' | 'hybrid' | 'rule';
  metadata?: Record<string, unknown>;
}

/**
 * Assignee recommendation
 */
export interface AssigneeRecommendation extends Recommendation {
  type: 'ASSIGNEE';
  value: {
    userId: string;
    name: string;
    avatar?: string;
  };
  reasons: string[];
  workloadScore?: number;
  expertiseScore?: number;
}

/**
 * Similar item recommendation
 */
export interface SimilarItemRecommendation extends Recommendation {
  type: 'SIMILAR_ITEMS';
  value: {
    itemId: string;
    title: string;
    status: string;
  };
  similarity: number;
  sharedTags?: string[];
}

/**
 * Hook options
 */
export interface UseRecommendationsOptions {
  /**
   * Workspace ID
   */
  workspaceId: string;

  /**
   * Item ID to get recommendations for
   */
  itemId?: string;

  /**
   * Types of recommendations to fetch
   */
  types?: RecommendationType[];

  /**
   * Number of recommendations per type
   * @default 5
   */
  limit?: number;

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Auto-fetch on mount
   * @default true
   */
  autoFetch?: boolean;
}

/**
 * Hook return type
 */
export interface UseRecommendationsReturn {
  /**
   * All recommendations
   */
  recommendations: Recommendation[];

  /**
   * Recommendations grouped by type
   */
  byType: Record<RecommendationType, Recommendation[]>;

  /**
   * Assignee recommendations
   */
  assignees: AssigneeRecommendation[];

  /**
   * Tag recommendations
   */
  tags: Recommendation[];

  /**
   * Similar items
   */
  similarItems: SimilarItemRecommendation[];

  /**
   * Next action suggestions
   */
  nextActions: Recommendation[];

  /**
   * Whether recommendations are loading
   */
  isLoading: boolean;

  /**
   * Error message if any
   */
  error: string | null;

  /**
   * Get specific recommendation type
   */
  getRecommendations: (type: RecommendationType) => Promise<Recommendation[]>;

  /**
   * Refresh all recommendations
   */
  refresh: () => Promise<void>;

  /**
   * Accept a recommendation
   */
  accept: (recommendation: Recommendation) => void;

  /**
   * Dismiss a recommendation
   */
  dismiss: (recommendationId: string) => void;

  /**
   * Provide feedback on recommendation
   */
  feedback: (recommendationId: string, helpful: boolean) => void;
}

/**
 * Hook for AI recommendations
 *
 * @example
 * ```tsx
 * function ItemEditor({ workspaceId, itemId }: Props) {
 *   const {
 *     assignees,
 *     tags,
 *     similarItems,
 *     isLoading,
 *     accept,
 *   } = useRecommendations({
 *     workspaceId,
 *     itemId,
 *     types: ['ASSIGNEE', 'TAG', 'SIMILAR_ITEMS'],
 *   });
 *
 *   return (
 *     <div>
 *       <AssigneeSuggestions
 *         recommendations={assignees}
 *         onSelect={accept}
 *       />
 *       <TagSuggestions
 *         recommendations={tags}
 *         onSelect={accept}
 *       />
 *     </div>
 *   );
 * }
 * ```
 */
export function useRecommendations(
  options: UseRecommendationsOptions
): UseRecommendationsReturn {
  const {
    workspaceId,
    itemId,
    types = ['ASSIGNEE', 'TAG', 'SIMILAR_ITEMS', 'NEXT_ACTION'],
    limit = 5,
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    autoFetch = true,
  } = options;

  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const factoryRef = useRef<AIAgentClientFactory | null>(null);
  const dismissedRef = useRef<Set<string>>(new Set());
  const feedbackRef = useRef<Map<string, boolean>>(new Map());

  // Initialize factory
  useEffect(() => {
    factoryRef.current = new AIAgentClientFactory({ baseUrl });
  }, [baseUrl]);

  // Fetch recommendations
  const fetchRecommendations = useCallback(async () => {
    if (!factoryRef.current) return;

    setIsLoading(true);
    setError(null);

    try {
      const allRecommendations: Recommendation[] = [];

      for (const type of types) {
        const response = await fetch(
          `${baseUrl}/api/v1/agents/RECOMMENDATION_AGENT/execute`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              recommendationType: type,
              itemId: itemId || undefined,
              workspaceId,
              limit,
              context: {},
            }),
          }
        );

        if (!response.ok) {
          console.warn(`Failed to fetch ${type} recommendations`);
          continue;
        }

        const data = await response.json();

        if (data.success && data.data?.recommendations) {
          for (const rec of data.data.recommendations) {
            // Skip dismissed recommendations
            if (dismissedRef.current.has(rec.id)) continue;

            allRecommendations.push({
              id: rec.id || generateId(),
              type,
              value: rec.value,
              displayValue: rec.displayValue || String(rec.value),
              confidence: rec.confidence || 0.5,
              reason: rec.reason || rec.reasons?.[0] || '',
              source: rec.source || 'hybrid',
              metadata: rec.metadata || {},
            });
          }
        }
      }

      setRecommendations(allRecommendations);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to fetch recommendations'
      );
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId, itemId, types, limit, baseUrl]);

  // Auto-fetch on mount
  useEffect(() => {
    if (autoFetch) {
      fetchRecommendations();
    }
  }, [fetchRecommendations, autoFetch]);

  // Group recommendations by type
  const byType = recommendations.reduce(
    (acc, rec) => {
      if (!acc[rec.type]) {
        acc[rec.type] = [];
      }
      acc[rec.type].push(rec);
      return acc;
    },
    {} as Record<RecommendationType, Recommendation[]>
  );

  // Typed getters
  const assignees = (byType['ASSIGNEE'] || []) as AssigneeRecommendation[];
  const tags = byType['TAG'] || [];
  const similarItems = (byType['SIMILAR_ITEMS'] ||
    []) as SimilarItemRecommendation[];
  const nextActions = byType['NEXT_ACTION'] || [];

  const getRecommendations = useCallback(
    async (type: RecommendationType): Promise<Recommendation[]> => {
      try {
        const response = await fetch(
          `${baseUrl}/api/v1/agents/RECOMMENDATION_AGENT/execute`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              recommendationType: type,
              itemId: itemId || undefined,
              workspaceId,
              limit,
              context: {},
            }),
          }
        );

        if (!response.ok) {
          return [];
        }

        const data = await response.json();

        if (data.success && data.data?.recommendations) {
          return data.data.recommendations.map(
            (rec: Record<string, unknown>) => ({
              id: rec.id || generateId(),
              type,
              value: rec.value,
              displayValue: rec.displayValue || String(rec.value),
              confidence: rec.confidence || 0.5,
              reason: rec.reason || '',
              source: rec.source || 'hybrid',
              metadata: rec.metadata || {},
            })
          );
        }

        return [];
      } catch {
        return [];
      }
    },
    [workspaceId, itemId, limit, baseUrl]
  );

  const accept = useCallback((recommendation: Recommendation) => {
    // Send acceptance feedback
    feedbackRef.current.set(recommendation.id, true);

    // Remove from recommendations
    setRecommendations((prev) =>
      prev.filter((r) => r.id !== recommendation.id)
    );

    // In real implementation, would also send to backend
    console.log('Accepted recommendation:', recommendation);
  }, []);

  const dismiss = useCallback((recommendationId: string) => {
    dismissedRef.current.add(recommendationId);
    setRecommendations((prev) => prev.filter((r) => r.id !== recommendationId));
  }, []);

  const feedback = useCallback(
    (recommendationId: string, helpful: boolean) => {
      feedbackRef.current.set(recommendationId, helpful);

      // Send feedback to backend
      fetch(`${baseUrl}/api/v1/recommendations/${recommendationId}/feedback`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ helpful }),
      }).catch(console.error);
    },
    [baseUrl]
  );

  return {
    recommendations,
    byType,
    assignees,
    tags,
    similarItems,
    nextActions,
    isLoading,
    error,
    getRecommendations,
    refresh: fetchRecommendations,
    accept,
    dismiss,
    feedback,
  };
}

// Helper functions
function generateId(): string {
  return `rec-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}
