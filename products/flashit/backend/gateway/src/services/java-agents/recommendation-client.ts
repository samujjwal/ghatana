/**
 * Recommendation Client
 *
 * Provides high-level interface for AI-powered personalized recommendations
 * through the Java Agent Service.
 *
 * @doc.type service
 * @doc.purpose AI recommendation generation client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type {
  RecommendationRequest,
  RecommendationResponse,
  RecommendationItem,
} from './agent-client.js';
import { prisma } from '../../lib/prisma.js';
import { fetchMoments } from './moment-helpers.js';

/**
 * Generate personalized recommendations for a user.
 *
 * @param userId - User requesting recommendations
 * @param strategies - Recommendation strategies to use (revisit, connect, habit, wellbeing, explore)
 * @param limit - Maximum number of recommendations
 * @param sphereIds - Optional sphere filter
 * @returns Ranked recommendations
 */
export async function generateRecommendations(
  userId: string,
  strategies: string[] = ['revisit', 'connect', 'habit', 'wellbeing', 'explore'],
  limit: number = 10,
  sphereIds?: string[]
): Promise<RecommendationResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();

  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot generate recommendations.');
  }

  // Fetch recent moments for context
  const moments = await fetchMoments(userId, { sphereIds, limit: 50 });

  if (moments.length === 0) {
    return {
      recommendations: [],
      totalGenerated: 0,
      strategies,
      processingTimeMs: 0,
      model: 'none',
    };
  }

  // Fetch already-delivered recommendation IDs to exclude
  const existingRecs = await prisma.recommendation.findMany({
    where: {
      userId,
      status: { in: ['ACTIVE', 'ACTED'] },
      createdAt: { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) }, // last 7 days
    },
    select: { id: true },
  });

  const excludeIds = existingRecs.map((r) => r.id);

  const request: RecommendationRequest = {
    userId,
    recentMoments: moments,
    sphereIds: sphereIds || [],
    strategies,
    limit,
    excludeIds,
  };

  const client = getJavaAgentClient();
  const response = await client.generateRecommendations(request);

  // Persist recommendations to database
  if (response.recommendations.length > 0) {
    await persistRecommendations(userId, response.recommendations);
  }

  return response;
}

/**
 * Submit feedback on a recommendation.
 * Verifies the recommendation belongs to the given user before persisting.
 */
export async function submitRecommendationFeedback(
  recommendationId: string,
  userId: string,
  action: string,
  rating?: number,
  comment?: string
): Promise<void> {
  // C1 FIX: Verify ownership — only the recommendation's owner can submit feedback
  const recommendation = await prisma.recommendation.findFirst({
    where: { id: recommendationId, userId },
    select: { id: true },
  });

  if (!recommendation) {
    throw new Error('Recommendation not found or not owned by user');
  }

  await prisma.recommendationFeedback.create({
    data: {
      recommendationId,
      userId,
      action,
      rating,
      comment,
    },
  });

  // Update recommendation status based on action
  const statusMap: Record<string, string> = {
    clicked: 'ACTED',
    dismissed: 'DISMISSED',
    helpful: 'ACTED',
    not_helpful: 'DISMISSED',
  };

  const newStatus = statusMap[action];
  if (newStatus) {
    await prisma.recommendation.update({
      where: { id: recommendationId },
      data: {
        status: newStatus as 'ACTIVE' | 'DISMISSED' | 'ACTED' | 'EXPIRED',
        ...(newStatus === 'ACTED' ? { actedAt: new Date() } : {}),
        ...(newStatus === 'DISMISSED' ? { dismissedAt: new Date() } : {}),
      },
    });
  }
}

/**
 * Get active recommendations for a user.
 */
export async function getActiveRecommendations(
  userId: string,
  limit: number = 10
): Promise<RecommendationItem[]> {
  const recs = await prisma.recommendation.findMany({
    where: {
      userId,
      status: 'ACTIVE',
      OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }],
    },
    orderBy: { score: 'desc' },
    take: limit,
  });

  return recs.map((r) => ({
    type: r.type,
    strategy: r.strategy,
    title: r.title,
    content: r.content,
    score: Number(r.score),
    reasoning: '',
    relatedMomentIds: r.relatedMomentIds,
    actionUrl: undefined,
  }));
}

// =========================================================================
// Internal Helpers
// =========================================================================

async function persistRecommendations(
  userId: string,
  items: RecommendationItem[]
): Promise<void> {
  const data = items.map((item) => ({
    userId,
    type: item.type as 'REVISIT' | 'CONNECT' | 'HABIT' | 'WELLBEING' | 'EXPLORE',
    strategy: item.strategy,
    title: item.title,
    content: item.content,
    score: item.score,
    status: 'ACTIVE' as const,
    relatedMomentIds: item.relatedMomentIds,
    relatedSphereIds: [],
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days
  }));

  await prisma.recommendation.createMany({ data });
}
