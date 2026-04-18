/**
 * AI-Powered Recommendations Hook
 *
 * Fetches personalized module recommendations based on user progress
 * and tenant context.
 *
 * @doc.type hook
 * @doc.purpose Dashboard AI recommendations
 * @doc.layer product
 * @doc.pattern React Query Hook
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../api/tutorputorClient";

interface AiRecommendation {
  id: string;
  title: string;
  slug: string;
  description?: string;
  domain?: string;
  difficultyLevel?: string;
  estimatedTimeMinutes?: number;
  tags: string[];
  isAiRecommended: boolean;
  recommendationReason?: string;
  matchScore: number;
}

interface PersonalizedRecommendationsResponse {
  modules: AiRecommendation[];
  reasoning: {
    basedOn: string;
    userLevel: string;
    suggestedDomains: string[];
  };
}

export function useRecommendations(limit: number = 6) {
  return useQuery({
    queryKey: ["recommendations", "personalized", limit],
    queryFn: async () => {
      const result = await apiClient.getPersonalizedRecommendations(limit);
      return result;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    gcTime: 10 * 60 * 1000, // 10 minutes
  });
}

export function useAssetRecommendations(assetId?: string, limit: number = 4) {
  return useQuery({
    queryKey: ["assetRecommendations", assetId, limit],
    queryFn: () => apiClient.getAssetRecommendations(assetId!, limit),
    enabled: Boolean(assetId),
  });
}

export function useAssetNextSteps(assetId?: string, limit: number = 4) {
  return useQuery({
    queryKey: ["assetNextSteps", assetId, limit],
    queryFn: () => apiClient.getNextSteps(assetId!, limit),
    enabled: Boolean(assetId),
  });
}
