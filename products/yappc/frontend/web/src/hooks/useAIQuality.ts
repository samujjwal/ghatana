/**
 * AI Quality Hook
 *
 * React hook for AI quality telemetry and confidence-based routing.
 *
 * @doc.type hook
 * @doc.purpose AI quality metrics and monitoring
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import type {
  QualityMetric,
  QualitySummary,
  ConfidenceScore,
  AIErrorCode,
  ModelProvider,
} from '../services/ai/types';

// ============================================================================
// Types
// ============================================================================

export interface UseAIQualityResult {
  metrics: QualityMetric[];
  summary: QualitySummary | null;
  recordMetric: (metric: Omit<QualityMetric, 'timestamp'>) => void;
  calculateConfidence: (text: string, tokenProbs?: number[]) => ConfidenceScore;
  getProviderHealth: (provider: ModelProvider) => { healthy: boolean; score: number };
  resetMetrics: () => void;
}

// ============================================================================
// Constants
// ============================================================================

const STORAGE_KEY = 'yappc-ai-quality-metrics';
const MAX_METRICS = 1000;

// ============================================================================
// Helper Functions
// ============================================================================

function loadMetrics(): QualityMetric[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as QualityMetric[]) : [];
  } catch {
    return [];
  }
}

function saveMetrics(metrics: QualityMetric[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(metrics.slice(-MAX_METRICS)));
  } catch {
    // Storage full - silently degrade
  }
}

function calculateQualitySummary(metrics: QualityMetric[]): QualitySummary | null {
  if (metrics.length === 0) return null;

  const now = Date.now();
  const dayAgo = now - 24 * 60 * 60 * 1000;
  const recent = metrics.filter((m) => m.timestamp > dayAgo);

  if (recent.length === 0) return null;

  const successful = recent.filter((m) => m.success);
  const failed = recent.filter((m) => !m.success);
  const cached = recent.filter((m) => m.cached);
  const fallbackUsed = recent.filter((m) => m.fallbackUsed);

  const avgConfidence =
    successful.reduce((sum: number, m: QualityMetric) => sum + m.confidence, 0) /
      successful.length || 0;

  const avgLatency =
    recent.reduce((sum: number, m: QualityMetric) => sum + m.latencyMs, 0) / recent.length;

  const errorBreakdown: Record<AIErrorCode, number> = {
    TIMEOUT: 0,
    RATE_LIMIT: 0,
    INVALID_REQUEST: 0,
    AUTHENTICATION: 0,
    SERVER_ERROR: 0,
    NETWORK_ERROR: 0,
    UNKNOWN: 0,
  };

  failed.forEach((m) => {
    if (m.errorCode) {
      errorBreakdown[m.errorCode] = (errorBreakdown[m.errorCode] || 0) + 1;
    }
  });

  const providerDistribution: Record<ModelProvider, number> = {
    openai: 0,
    anthropic: 0,
    azure: 0,
    local: 0,
  };

  recent.forEach((m) => {
    providerDistribution[m.provider] = (providerDistribution[m.provider] || 0) + 1;
  });

  return {
    period: { start: recent[0].timestamp, end: now },
    totalRequests: recent.length,
    successfulRequests: successful.length,
    failedRequests: failed.length,
    averageConfidence: avgConfidence,
    averageLatencyMs: avgLatency,
    cacheHitRate: cached.length / recent.length,
    fallbackUsageRate: fallbackUsed.length / recent.length,
    errorBreakdown,
    providerDistribution,
  };
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useAIQuality(): UseAIQualityResult {
  const [metrics, setMetrics] = useState<QualityMetric[]>(loadMetrics);

  useEffect(() => {
    setMetrics(loadMetrics());
  }, []);

  const recordMetric = useCallback((metric: Omit<QualityMetric, 'timestamp'>) => {
    const newMetric: QualityMetric = {
      ...metric,
      timestamp: Date.now(),
    };

    setMetrics((prev) => {
      const updated = [...prev, newMetric].slice(-MAX_METRICS);
      saveMetrics(updated);
      return updated;
    });
  }, []);

  const calculateConfidence = useCallback(
    (text: string, tokenProbs?: number[]): ConfidenceScore => {
      // Calculate base confidence from token probabilities
      const tokenConfidence = tokenProbs && tokenProbs.length > 0
        ? tokenProbs.reduce((sum: number, prob: number) => sum + prob, 0) / tokenProbs.length
        : 0.8;

      // Length penalty
      const lengthPenalty = text.length < 10 || text.length > 4000 ? 0.8 : 1.0;

      // Error indicator penalty
      const errorIndicators = ['error', 'failed', 'unable', 'cannot', 'sorry'];
      const hasErrorIndicator = errorIndicators.some((word) =>
        text.toLowerCase().includes(word),
      );
      const errorIndicatorPenalty = hasErrorIndicator ? 0.7 : 1.0;

      const overall = Math.min(1, tokenConfidence * lengthPenalty * errorIndicatorPenalty);

      let reasoning = 'Standard confidence calculation';
      if (hasErrorIndicator) {
        reasoning = 'Reduced confidence due to error indicators in response';
      } else if (text.length < 10) {
        reasoning = 'Reduced confidence due to short response';
      } else if (text.length > 4000) {
        reasoning = 'Reduced confidence due to very long response';
      } else if (tokenConfidence > 0.9) {
        reasoning = 'High confidence based on token probabilities';
      }

      return {
        overall,
        factors: {
          tokenConfidence,
          lengthPenalty,
          errorIndicatorPenalty,
        },
        reasoning,
      };
    },
    [],
  );

  const getProviderHealth = useCallback(
    (provider: ModelProvider): { healthy: boolean; score: number } => {
      const providerMetrics = metrics.filter(
        (m) => m.provider === provider && m.timestamp > Date.now() - 60 * 60 * 1000, // Last hour
      );

      if (providerMetrics.length === 0) {
        return { healthy: true, score: 1 }; // No data, assume healthy
      }

      const successRate =
        providerMetrics.filter((m) => m.success).length / providerMetrics.length;
      const avgLatency =
        providerMetrics.reduce((sum: number, m: QualityMetric) => sum + m.latencyMs, 0) /
        providerMetrics.length;

      // Score based on success rate and latency
      const latencyScore = Math.max(0, 1 - avgLatency / 5000); // 5s threshold
      const score = successRate * 0.7 + latencyScore * 0.3;

      return {
        healthy: successRate > 0.9 && avgLatency < 3000,
        score: Math.min(1, score),
      };
    },
    [metrics],
  );

  const resetMetrics = useCallback(() => {
    setMetrics([]);
    saveMetrics([]);
  }, []);

  const summary = useMemo(() => calculateQualitySummary(metrics), [metrics]);

  return {
    metrics,
    summary,
    recordMetric,
    calculateConfidence,
    getProviderHealth,
    resetMetrics,
  };
}
