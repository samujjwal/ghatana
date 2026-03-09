import { useMemo } from 'react';

import type { OptimizationRecommendation } from '../../../hooks/ai/useAIInsights/types';
import type { RecommendationsByType, RecommendationCounts } from '../types';

/**
 * Create grouped recommendations and counts from the provided getRecommendations callback.
 * This memoizes the grouping so callers can use it in render without recomputing.
 */
export function useAIInsightsSummary(
  getRecommendations: () => OptimizationRecommendation[]
) {
  return useMemo(() => {
    const all = getRecommendations();
    const grouped: RecommendationsByType = {};
    all.forEach((r) => {
      if (!grouped[r.type]) grouped[r.type] = [];
      grouped[r.type].push(r);
    });

    const counts: RecommendationCounts = {
      critical: all.filter((r) => r.priority === 'critical').length,
      high: all.filter((r) => r.priority === 'high').length,
      medium: all.filter((r) => r.priority === 'medium').length,
      low: all.filter((r) => r.priority === 'low').length,
      total: all.length,
    };

    return { grouped, counts } as const;
  }, [getRecommendations]);
}

export default useAIInsightsSummary;
