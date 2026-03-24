/**
 * Predictions Hook
 *
 * React hook for AI-powered predictions about items, timelines, and outcomes.
 * Provides timeline predictions, risk assessments, and success probability.
 *
 * @module ai/hooks/usePredictions
 * @doc.type hook
 * @doc.purpose AI predictions management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import {
  AIAgentClientFactory,
  type PredictionInput,
  type PredictionOutput,
} from '../agents';

/**
 * Prediction types
 */
export type PredictionType =
  | 'COMPLETION_DATE'
  | 'RISK_SCORE'
  | 'EFFORT_ESTIMATE'
  | 'PRIORITY_SCORE'
  | 'SUCCESS_PROBABILITY'
  | 'BLOCKERS';

/**
 * Single prediction result
 */
export interface Prediction {
  id: string;
  type: PredictionType;
  itemId: string;
  predictedValue: string | number;
  confidence: number;
  range?: {
    low: string | number;
    high: string | number;
  };
  contributingFactors: Array<{
    name: string;
    impact: number;
    description: string;
  }>;
  createdAt: Date;
  expiresAt: Date;
  modelVersion: string;
}

/**
 * Timeline prediction for an item
 */
export interface TimelinePrediction {
  itemId: string;
  estimatedStart?: Date;
  estimatedEnd?: Date;
  confidenceRange: {
    optimistic: Date;
    pessimistic: Date;
  };
  confidence: number;
  blockers: string[];
  dependencies: string[];
}

/**
 * Hook options
 */
export interface UsePredictionsOptions {
  /**
   * Workspace ID
   */
  workspaceId: string;

  /**
   * Item IDs to get predictions for
   */
  itemIds?: string[];

  /**
   * Prediction types to fetch
   */
  types?: PredictionType[];

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Cache predictions
   * @default true
   */
  cache?: boolean;

  /**
   * Cache TTL in milliseconds
   * @default 300000 (5 minutes)
   */
  cacheTTL?: number;
}

/**
 * Hook return type
 */
export interface UsePredictionsReturn {
  /**
   * All predictions
   */
  predictions: Prediction[];

  /**
   * Predictions by item ID
   */
  predictionsByItem: Map<string, Prediction[]>;

  /**
   * Timeline predictions
   */
  timelinePredictions: TimelinePrediction[];

  /**
   * Whether predictions are loading
   */
  isLoading: boolean;

  /**
   * Error message if any
   */
  error: string | null;

  /**
   * Get prediction for a specific item
   */
  getPrediction: (
    itemId: string,
    type: PredictionType
  ) => Promise<Prediction | null>;

  /**
   * Get timeline prediction for an item
   */
  getTimelinePrediction: (itemId: string) => Promise<TimelinePrediction | null>;

  /**
   * Refresh all predictions
   */
  refresh: () => Promise<void>;

  /**
   * Invalidate cache for specific items
   */
  invalidate: (itemIds?: string[]) => void;
}

/**
 * Hook for AI predictions
 *
 * @example
 * ```tsx
 * function ItemTimeline({ itemId }: { itemId: string }) {
 *   const { getPrediction, isLoading } = usePredictions({
 *     workspaceId: 'ws-123',
 *     itemIds: [itemId],
 *     types: ['COMPLETION_DATE', 'RISK_SCORE'],
 *   });
 *
 *   const [prediction, setPrediction] = useState<Prediction | null>(null);
 *
 *   useEffect(() => {
 *     getPrediction(itemId, 'COMPLETION_DATE').then(setPrediction);
 *   }, [itemId]);
 *
 *   if (!prediction) return null;
 *
 *   return (
 *     <div>
 *       <span>Est. Completion: {prediction.predictedValue}</span>
 *       <span>Confidence: {prediction.confidence}%</span>
 *     </div>
 *   );
 * }
 * ```
 */
export function usePredictions(
  options: UsePredictionsOptions
): UsePredictionsReturn {
  const {
    workspaceId,
    itemIds = [],
    types = ['COMPLETION_DATE', 'RISK_SCORE', 'EFFORT_ESTIMATE'],
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    cache = true,
    cacheTTL = 300000,
  } = options;

  const [predictions, setPredictions] = useState<Prediction[]>([]);
  const [timelinePredictions, setTimelinePredictions] = useState<
    TimelinePrediction[]
  >([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const factoryRef = useRef<AIAgentClientFactory | null>(null);
  const cacheRef = useRef<Map<string, { data: Prediction; expiresAt: number }>>(
    new Map()
  );

  // Initialize factory
  useEffect(() => {
    factoryRef.current = new AIAgentClientFactory({ baseUrl });
  }, [baseUrl]);

  // Fetch predictions for items
  const fetchPredictions = useCallback(async () => {
    if (!factoryRef.current || itemIds.length === 0) return;

    setIsLoading(true);
    setError(null);

    try {
      const client = factoryRef.current.createPredictionClient();
      const allPredictions: Prediction[] = [];
      const allTimelinePredictions: TimelinePrediction[] = [];

      for (const itemId of itemIds) {
        for (const type of types) {
          const cacheKey = `${itemId}-${type}`;

          // Check cache
          if (cache) {
            const cached = cacheRef.current.get(cacheKey);
            if (cached && cached.expiresAt > Date.now()) {
              allPredictions.push(cached.data);
              continue;
            }
          }

          const input: PredictionInput = {
            itemId,
            workspaceId,
            predictionType: type,
            features: {},
          };

          const result = await client.execute(input, {
            userId: 'current-user',
            workspaceId,
            traceId: generateId(),
            spanId: generateId().substring(0, 16),
          });

          if (result.success && result.data) {
            const prediction: Prediction = {
              id: generateId(),
              type,
              itemId,
              predictedValue: result.data.predictedValue,
              confidence: result.data.confidence,
              range: result.data.confidenceInterval
                ? {
                    low: result.data.confidenceInterval.low,
                    high: result.data.confidenceInterval.high,
                  }
                : undefined,
              contributingFactors: Object.entries(
                result.data.contributingFactors || {}
              ).map(([name, value]) => ({
                name,
                impact: typeof value === 'number' ? value : 0,
                description: String(value),
              })),
              createdAt: new Date(),
              expiresAt: new Date(Date.now() + cacheTTL),
              modelVersion: result.metrics?.model || 'unknown',
            };

            allPredictions.push(prediction);

            // Cache the prediction
            if (cache) {
              cacheRef.current.set(cacheKey, {
                data: prediction,
                expiresAt: Date.now() + cacheTTL,
              });
            }

            // Build timeline prediction for COMPLETION_DATE
            if (type === 'COMPLETION_DATE') {
              allTimelinePredictions.push({
                itemId,
                estimatedEnd: parseDate(result.data.predictedValue),
                confidenceRange: {
                  optimistic: parseDate(result.data.confidenceInterval?.low),
                  pessimistic: parseDate(result.data.confidenceInterval?.high),
                },
                confidence: result.data.confidence,
                blockers: [],
                dependencies: [],
              });
            }
          }
        }
      }

      setPredictions(allPredictions);
      setTimelinePredictions(allTimelinePredictions);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to fetch predictions'
      );
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId, itemIds, types, cache, cacheTTL]);

  // Initial fetch
  useEffect(() => {
    if (itemIds.length > 0) {
      fetchPredictions();
    }
  }, [fetchPredictions, itemIds.length]);

  // Group predictions by item
  const predictionsByItem = new Map<string, Prediction[]>();
  for (const pred of predictions) {
    const existing = predictionsByItem.get(pred.itemId) || [];
    existing.push(pred);
    predictionsByItem.set(pred.itemId, existing);
  }

  const getPrediction = useCallback(
    async (
      itemId: string,
      type: PredictionType
    ): Promise<Prediction | null> => {
      if (!factoryRef.current) return null;

      const cacheKey = `${itemId}-${type}`;

      // Check cache
      if (cache) {
        const cached = cacheRef.current.get(cacheKey);
        if (cached && cached.expiresAt > Date.now()) {
          return cached.data;
        }
      }

      try {
        const client = factoryRef.current.createPredictionClient();
        const result = await client.execute(
          {
            itemId,
            workspaceId,
            predictionType: type,
            features: {},
          },
          {
            userId: 'current-user',
            workspaceId,
            traceId: generateId(),
            spanId: generateId().substring(0, 16),
          }
        );

        if (result.success && result.data) {
          const prediction: Prediction = {
            id: generateId(),
            type,
            itemId,
            predictedValue: result.data.predictedValue,
            confidence: result.data.confidence,
            contributingFactors: [],
            createdAt: new Date(),
            expiresAt: new Date(Date.now() + cacheTTL),
            modelVersion: result.metrics?.model || 'unknown',
          };

          if (cache) {
            cacheRef.current.set(cacheKey, {
              data: prediction,
              expiresAt: Date.now() + cacheTTL,
            });
          }

          return prediction;
        }

        return null;
      } catch {
        return null;
      }
    },
    [workspaceId, cache, cacheTTL]
  );

  const getTimelinePrediction = useCallback(
    async (itemId: string): Promise<TimelinePrediction | null> => {
      const prediction = await getPrediction(itemId, 'COMPLETION_DATE');
      if (!prediction) return null;

      return {
        itemId,
        estimatedEnd: parseDate(prediction.predictedValue),
        confidenceRange: {
          optimistic: parseDate(prediction.range?.low),
          pessimistic: parseDate(prediction.range?.high),
        },
        confidence: prediction.confidence,
        blockers: [],
        dependencies: [],
      };
    },
    [getPrediction]
  );

  const invalidate = useCallback((ids?: string[]) => {
    if (!ids) {
      cacheRef.current.clear();
    } else {
      for (const id of ids) {
        for (const key of cacheRef.current.keys()) {
          if (key.startsWith(`${id}-`)) {
            cacheRef.current.delete(key);
          }
        }
      }
    }
  }, []);

  return {
    predictions,
    predictionsByItem,
    timelinePredictions,
    isLoading,
    error,
    getPrediction,
    getTimelinePrediction,
    refresh: fetchPredictions,
    invalidate,
  };
}

// Helper functions
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

function parseDate(value: unknown): Date {
  if (value instanceof Date) return value;
  if (typeof value === 'string') return new Date(value);
  if (typeof value === 'number') return new Date(value);
  return new Date();
}
