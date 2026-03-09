/**
 * AI Insights Hook
 *
 * React hook for fetching AI-powered insights about items, workflows, and workspaces.
 * Aggregates predictions, anomalies, and recommendations into actionable insights.
 *
 * @module ai/hooks/useAIInsights
 * @doc.type hook
 * @doc.purpose AI insights aggregation and management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import {
  AIAgentClientFactory,
  type PredictionInput,
  type PredictionOutput,
  type AnomalyInput,
  type AnomalyOutput,
} from '../agents';

/**
 * Types of AI insights
 */
export type InsightType =
  | 'prediction'
  | 'anomaly'
  | 'recommendation'
  | 'trend'
  | 'risk'
  | 'opportunity';

/**
 * Priority levels for insights
 */
export type InsightPriority = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * Single AI insight
 */
export interface AIInsight {
  id: string;
  type: InsightType;
  priority: InsightPriority;
  title: string;
  description: string;
  confidence: number;
  createdAt: Date;
  expiresAt?: Date;
  source: string;
  metadata: Record<string, unknown>;
  actions?: Array<{
    label: string;
    action: string;
    params?: Record<string, unknown>;
  }>;
}

/**
 * Hook options
 */
export interface UseAIInsightsOptions {
  /**
   * Workspace ID for context
   */
  workspaceId: string;

  /**
   * Item ID to get insights for (optional)
   */
  itemId?: string;

  /**
   * Types of insights to fetch
   */
  types?: InsightType[];

  /**
   * Auto-refresh interval in milliseconds
   * @default 60000 (1 minute)
   */
  refreshInterval?: number;

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Enable real-time updates
   * @default true
   */
  realtime?: boolean;
}

/**
 * Hook return type
 */
export interface UseAIInsightsReturn {
  /**
   * List of insights
   */
  insights: AIInsight[];

  /**
   * Insights grouped by type
   */
  insightsByType: Record<InsightType, AIInsight[]>;

  /**
   * Critical insights that need attention
   */
  criticalInsights: AIInsight[];

  /**
   * Whether insights are being loaded
   */
  isLoading: boolean;

  /**
   * Error message if any
   */
  error: string | null;

  /**
   * Manually refresh insights
   */
  refresh: () => Promise<void>;

  /**
   * Dismiss an insight
   */
  dismiss: (insightId: string) => void;

  /**
   * Mark insight as acted upon
   */
  markActedUpon: (insightId: string) => void;
}

/**
 * Hook for fetching AI insights
 *
 * @example
 * ```tsx
 * function InsightsPanel({ workspaceId }: { workspaceId: string }) {
 *   const { insights, criticalInsights, isLoading } = useAIInsights({
 *     workspaceId,
 *     types: ['prediction', 'anomaly', 'risk'],
 *   });
 *
 *   if (isLoading) return <Spinner />;
 *
 *   return (
 *     <div>
 *       {criticalInsights.length > 0 && (
 *         <AlertBanner insights={criticalInsights} />
 *       )}
 *       <InsightList insights={insights} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useAIInsights(
  options: UseAIInsightsOptions
): UseAIInsightsReturn {
  const {
    workspaceId,
    itemId,
    types = ['prediction', 'anomaly', 'recommendation', 'risk'],
    refreshInterval = 60000,
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    realtime = true,
  } = options;

  const [insights, setInsights] = useState<AIInsight[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const factoryRef = useRef<AIAgentClientFactory | null>(null);
  const dismissedRef = useRef<Set<string>>(new Set());

  // Initialize factory
  useEffect(() => {
    factoryRef.current = new AIAgentClientFactory({ baseUrl });
  }, [baseUrl]);

  // Fetch insights
  const fetchInsights = useCallback(async () => {
    if (!factoryRef.current) return;

    setIsLoading(true);
    setError(null);

    try {
      const allInsights: AIInsight[] = [];

      // Fetch predictions if requested
      if (types.includes('prediction')) {
        const predictionClient = factoryRef.current.createPredictionClient();
        const predictionInput: PredictionInput = {
          itemId: itemId || 'workspace-overview',
          workspaceId,
          predictionType: 'COMPLETION_DATE',
          features: {},
        };

        const predResult = await predictionClient.execute(predictionInput, {
          userId: 'current-user',
          workspaceId,
          traceId: generateId(),
          spanId: generateId().substring(0, 16),
        });

        if (predResult.success && predResult.data) {
          allInsights.push({
            id: generateId(),
            type: 'prediction',
            priority: determinePriority(predResult.data.confidence),
            title: 'Completion Prediction',
            description: `Estimated completion: ${predResult.data.predictedValue}`,
            confidence: predResult.data.confidence,
            createdAt: new Date(),
            source: 'PredictionAgent',
            metadata: predResult.data.contributingFactors || {},
          });
        }
      }

      // Fetch anomalies if requested
      if (types.includes('anomaly')) {
        const anomalyClient = factoryRef.current.createAnomalyDetectorClient();
        const anomalyInput: AnomalyInput = {
          workspaceId,
          metricName: 'velocity',
          currentValue: 0, // Would come from real data
          historicalValues: [], // Would come from real data
          sensitivity: 0.8,
        };

        const anomalyResult = await anomalyClient.execute(anomalyInput, {
          userId: 'current-user',
          workspaceId,
          traceId: generateId(),
          spanId: generateId().substring(0, 16),
        });

        if (anomalyResult.success && anomalyResult.data?.anomalies) {
          for (const anomaly of anomalyResult.data.anomalies) {
            allInsights.push({
              id: generateId(),
              type: 'anomaly',
              priority:
                anomaly.severity === 'critical'
                  ? 'critical'
                  : anomaly.severity === 'high'
                    ? 'high'
                    : 'medium',
              title: `Anomaly Detected: ${anomaly.type}`,
              description: anomaly.description,
              confidence: anomaly.score,
              createdAt: new Date(anomaly.timestamp),
              source: 'AnomalyDetectorAgent',
              metadata: { anomaly },
            });
          }
        }
      }

      // Filter out dismissed insights
      const filteredInsights = allInsights.filter(
        (i) => !dismissedRef.current.has(i.id)
      );

      setInsights(filteredInsights);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch insights');
    } finally {
      setIsLoading(false);
    }
  }, [workspaceId, itemId, types]);

  // Initial fetch
  useEffect(() => {
    fetchInsights();
  }, [fetchInsights]);

  // Auto-refresh
  useEffect(() => {
    if (!realtime || refreshInterval <= 0) return;

    const interval = setInterval(fetchInsights, refreshInterval);
    return () => clearInterval(interval);
  }, [fetchInsights, realtime, refreshInterval]);

  // Group insights by type
  const insightsByType = insights.reduce(
    (acc, insight) => {
      if (!acc[insight.type]) {
        acc[insight.type] = [];
      }
      acc[insight.type].push(insight);
      return acc;
    },
    {} as Record<InsightType, AIInsight[]>
  );

  // Get critical insights
  const criticalInsights = insights.filter(
    (i) => i.priority === 'critical' || i.priority === 'high'
  );

  const dismiss = useCallback((insightId: string) => {
    dismissedRef.current.add(insightId);
    setInsights((prev) => prev.filter((i) => i.id !== insightId));
  }, []);

  const markActedUpon = useCallback((insightId: string) => {
    setInsights((prev) =>
      prev.map((i) =>
        i.id === insightId
          ? { ...i, metadata: { ...i.metadata, actedUpon: true } }
          : i
      )
    );
  }, []);

  return {
    insights,
    insightsByType,
    criticalInsights,
    isLoading,
    error,
    refresh: fetchInsights,
    dismiss,
    markActedUpon,
  };
}

// Helper functions
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

function determinePriority(confidence: number): InsightPriority {
  if (confidence >= 0.9) return 'high';
  if (confidence >= 0.7) return 'medium';
  if (confidence >= 0.5) return 'low';
  return 'info';
}
