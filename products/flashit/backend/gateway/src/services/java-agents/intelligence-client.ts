/**
 * Intelligence Accumulation Client
 *
 * Provides high-level interface for cross-session learning and
 * knowledge profile computation through the Java Agent Service.
 *
 * @doc.type service
 * @doc.purpose Long-term intelligence and knowledge profile client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type {
  IntelligenceAccumulationRequest,
  IntelligenceAccumulationResponse,
} from './agent-client.js';
import { prisma } from '../../lib/prisma.js';
import { Prisma } from '../../../generated/prisma/index.js';
import { fetchMoments } from './moment-helpers.js';

/**
 * Compute or update the user's knowledge profile.
 *
 * @param userId - User whose profile to compute
 * @param limit - Maximum moments to analyze
 * @returns Updated knowledge profile
 */
export async function computeKnowledgeProfile(
  userId: string,
  limit: number = 200
): Promise<IntelligenceAccumulationResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();

  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot compute knowledge profile.');
  }

  // Fetch all recent moments
  const moments = await fetchMoments(userId, { limit });

  if (moments.length === 0) {
    return {
      userId,
      topTopics: [],
      topEntities: [],
      emotionProfile: {},
      activityPattern: {},
      newInsights: [],
      profileVersion: 1,
      processingTimeMs: 0,
      model: 'none',
    };
  }

  // Get existing profile for context
  const existingProfile = await prisma.userKnowledgeProfile.findUnique({
    where: { userId },
  });

  const existingTopics = existingProfile
    ? (existingProfile.topTopics as Array<{ topic: string }>).map((t) => t.topic)
    : [];
  const existingEntities = existingProfile
    ? (existingProfile.topEntities as Array<{ entity: string }>).map((e) => e.entity)
    : [];
  const profileVersion = existingProfile?.profileVersion || 0;

  const request: IntelligenceAccumulationRequest = {
    userId,
    moments,
    existingTopics,
    existingEntities,
    profileVersion,
  };

  const client = getJavaAgentClient();
  const response = await client.computeProfile(request);

  // Persist updated profile
  await persistProfile(userId, response);

  // Store new insights as AIInsight records
  if (response.newInsights.length > 0) {
    await persistInsights(userId, response.newInsights);
  }

  return response;
}

/**
 * Get the user's current knowledge profile.
 */
export async function getKnowledgeProfile(userId: string): Promise<{
  topTopics: unknown[];
  topEntities: unknown[];
  emotionProfile: unknown;
  activityPattern: unknown;
  profileVersion: number;
  lastComputedAt: Date;
} | null> {
  const profile = await prisma.userKnowledgeProfile.findUnique({
    where: { userId },
  });

  if (!profile) {
    return null;
  }

  return {
    topTopics: profile.topTopics as unknown[],
    topEntities: profile.topEntities as unknown[],
    emotionProfile: profile.emotionProfile,
    activityPattern: profile.activityPattern,
    profileVersion: profile.profileVersion,
    lastComputedAt: profile.lastComputedAt,
  };
}

// =========================================================================
// Internal Helpers
// =========================================================================

async function persistProfile(
  userId: string,
  response: IntelligenceAccumulationResponse
): Promise<void> {
  // Use Prisma.InputJsonValue for type-safe JSON field assignment
  const topTopics = response.topTopics as unknown as Prisma.InputJsonValue;
  const topEntities = response.topEntities as unknown as Prisma.InputJsonValue;
  const emotionProfile = response.emotionProfile as unknown as Prisma.InputJsonValue;
  const activityPattern = response.activityPattern as unknown as Prisma.InputJsonValue;

  await prisma.userKnowledgeProfile.upsert({
    where: { userId },
    update: {
      topTopics,
      topEntities,
      emotionProfile,
      activityPattern,
      profileVersion: response.profileVersion,
      lastComputedAt: new Date(),
    },
    create: {
      userId,
      topTopics,
      topEntities,
      emotionProfile,
      activityPattern,
      profileVersion: response.profileVersion,
      lastComputedAt: new Date(),
    },
  });
}

async function persistInsights(
  userId: string,
  insights: string[]
): Promise<void> {
  const data = insights.map((content) => ({
    userId,
    insightType: 'intelligence-accumulation',
    title: content.substring(0, 100),
    content,
    confidence: 0.7,
    relatedMoments: [],
    version: 1,
  }));

  await prisma.aIInsight.createMany({ data });
}
