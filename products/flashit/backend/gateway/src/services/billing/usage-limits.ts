/**
 * Usage limits enforcement for Flashit tiers
 * 
 * @doc.type service
 * @doc.purpose Track and enforce tier-based usage limits with soft/hard boundaries
 * @doc.layer product
 * @doc.pattern Service
 */

import { prisma } from '../../lib/prisma.js';
import type { PrismaClient } from '../../../generated/prisma/index.js';

/**
 * Tier definitions with usage limits
 */
export const TIER_LIMITS = {
  free: {
    momentsPerMonth: 100,
    storageGB: 1,
    transcriptionHoursPerMonth: 10,
    aiInsightsPerMonth: 5,
    memoryExpansionsPerMonth: 2,
    spheres: 3,
    collaborators: 0,
    auditLogDays: 30,
  },
  pro: {
    momentsPerMonth: -1, // unlimited
    storageGB: 50,
    transcriptionHoursPerMonth: 50,
    aiInsightsPerMonth: 50,
    memoryExpansionsPerMonth: 20,
    spheres: 20,
    collaborators: 3,
    auditLogDays: 365,
  },
  teams: {
    momentsPerMonth: -1, // unlimited
    storageGB: 500,
    transcriptionHoursPerMonth: 200,
    aiInsightsPerMonth: -1, // unlimited
    memoryExpansionsPerMonth: -1, // unlimited
    spheres: -1, // unlimited
    collaborators: -1, // unlimited
    auditLogDays: 1095, // 3 years
  },
} as const;

export type TierName = keyof typeof TIER_LIMITS;

/**
 * Usage check result with upgrade prompts
 */
export interface UsageCheckResult {
  allowed: boolean;
  limitReached: boolean;
  currentUsage: number;
  limit: number;
  percentUsed: number;
  upgradePrompt?: {
    title: string;
    message: string;
    actionUrl: string;
    tier: TierName;
  };
}

/**
 * Monthly usage summary for a user
 */
export interface UsageSummary {
  userId: string;
  tier: TierName;
  period: {
    start: Date;
    end: Date;
  };
  moments: {
    current: number;
    limit: number;
    percentUsed: number;
  };
  storage: {
    currentGB: number;
    limitGB: number;
    percentUsed: number;
  };
  transcription: {
    currentHours: number;
    limitHours: number;
    percentUsed: number;
  };
  aiInsights: {
    current: number;
    limit: number;
    percentUsed: number;
  };
  memoryExpansions: {
    current: number;
    limit: number;
    percentUsed: number;
  };
}

/**
 * Check if user can create a moment
 */
export async function checkMomentLimit(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  // Unlimited for pro/teams
  if (limits.momentsPerMonth === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  // Get current month's moment count
  const startOfMonth = new Date();
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);

  const momentCount = await db.moment.count({
    where: {
      userId,
      createdAt: { gte: startOfMonth },
    },
  });

  const percentUsed = (momentCount / limits.momentsPerMonth) * 100;
  const allowed = momentCount < limits.momentsPerMonth;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: momentCount,
    limit: limits.momentsPerMonth,
    percentUsed,
    upgradePrompt: percentUsed >= 80 ? {
      title: 'Approaching Moment Limit',
      message: `You've used ${Math.round(percentUsed)}% of your monthly moments. Upgrade to Pro for unlimited moments.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check if user can use AI insights
 */
export async function checkAIInsightLimit(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  if (limits.aiInsightsPerMonth === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  const startOfMonth = new Date();
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);

  // Count AI insight generations (reflections, themes, patterns)
  const insightCount = await db.aiInsight.count({
    where: {
      userId,
      createdAt: { gte: startOfMonth },
    },
  });

  const percentUsed = (insightCount / limits.aiInsightsPerMonth) * 100;
  const allowed = insightCount < limits.aiInsightsPerMonth;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: insightCount,
    limit: limits.aiInsightsPerMonth,
    percentUsed,
    upgradePrompt: !allowed ? {
      title: 'AI Insight Limit Reached',
      message: `You've used all ${limits.aiInsightsPerMonth} AI insights this month. Upgrade to Pro for 50 insights/month.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check if user can expand memory
 */
export async function checkMemoryExpansionLimit(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  if (limits.memoryExpansionsPerMonth === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  const startOfMonth = new Date();
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);

  const expansionCount = await db.memoryExpansion.count({
    where: {
      userId,
      createdAt: { gte: startOfMonth },
    },
  });

  const percentUsed = (expansionCount / limits.memoryExpansionsPerMonth) * 100;
  const allowed = expansionCount < limits.memoryExpansionsPerMonth;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: expansionCount,
    limit: limits.memoryExpansionsPerMonth,
    percentUsed,
    upgradePrompt: !allowed ? {
      title: 'Memory Expansion Limit Reached',
      message: `You've used all ${limits.memoryExpansionsPerMonth} memory expansions this month. Upgrade to Pro for 20 expansions/month.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check if user can create a sphere
 */
export async function checkSphereLimit(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  if (limits.spheres === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  const sphereCount = await db.sphere.count({
    where: { userId },
  });

  const percentUsed = (sphereCount / limits.spheres) * 100;
  const allowed = sphereCount < limits.spheres;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: sphereCount,
    limit: limits.spheres,
    percentUsed,
    upgradePrompt: !allowed ? {
      title: 'Sphere Limit Reached',
      message: `You've created ${limits.spheres} spheres (max for Free tier). Upgrade to Pro for 20 spheres.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check storage usage
 * Aggregates MediaReference.sizeBytes for all media owned by the user.
 */
export async function checkStorageLimit(
  userId: string,
  tier: TierName = 'free',
  additionalBytes: number = 0,
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  // Calculate current storage (sum of MediaReference file sizes for user's moments)
  const result = await db.mediaReference.aggregate({
    where: {
      moment: { userId },
      uploadStatus: 'COMPLETED',
    },
    _sum: {
      sizeBytes: true,
    },
  });

  const currentBytes = Number(result._sum.sizeBytes || 0) + additionalBytes;
  const currentGB = currentBytes / (1024 ** 3);
  const limitGB = limits.storageGB;
  const percentUsed = (currentGB / limitGB) * 100;
  const allowed = currentGB < limitGB;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: currentGB,
    limit: limitGB,
    percentUsed,
    upgradePrompt: percentUsed >= 80 ? {
      title: 'Storage Almost Full',
      message: `You've used ${Math.round(percentUsed)}% of your ${limitGB}GB storage. Upgrade to Pro for 50GB.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check if user can use transcription
 */
export async function checkTranscriptionLimit(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  if (limits.transcriptionHoursPerMonth === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  const startOfMonth = new Date();
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);

  const result = await db.transcriptionUsage.aggregate({
    where: {
      userId,
      createdAt: { gte: startOfMonth },
      status: 'completed',
    },
    _sum: {
      durationSeconds: true,
    },
  });

  const usedHours = (result._sum.durationSeconds || 0) / 3600;
  const limitHours = limits.transcriptionHoursPerMonth;
  const percentUsed = (usedHours / limitHours) * 100;
  const allowed = usedHours < limitHours;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: usedHours,
    limit: limitHours,
    percentUsed,
    upgradePrompt: !allowed ? {
      title: 'Transcription Limit Reached',
      message: `You've used all ${limitHours} hours of transcription this month. Upgrade to Pro for ${TIER_LIMITS.pro.transcriptionHoursPerMonth} hours/month.`,
      actionUrl: '/settings/billing/upgrade',
      tier: 'pro',
    } : undefined,
  };
}

/**
 * Check if user can add more collaborators to a sphere
 */
export async function checkCollaboratorLimit(
  userId: string,
  sphereId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageCheckResult> {
  const db = tx || prisma;
  const limits = TIER_LIMITS[tier];

  if (limits.collaborators === -1) {
    return {
      allowed: true,
      limitReached: false,
      currentUsage: 0,
      limit: -1,
      percentUsed: 0,
    };
  }

  // Count active collaborators (non-owner access entries that aren't revoked)
  const collaboratorCount = await db.sphereAccess.count({
    where: {
      sphereId,
      revokedAt: null,
      userId: { not: userId }, // exclude the owner
    },
  });

  const percentUsed = limits.collaborators > 0
    ? (collaboratorCount / limits.collaborators) * 100
    : 100;
  const allowed = collaboratorCount < limits.collaborators;

  return {
    allowed,
    limitReached: !allowed,
    currentUsage: collaboratorCount,
    limit: limits.collaborators,
    percentUsed,
    upgradePrompt: !allowed ? {
      title: 'Collaborator Limit Reached',
      message: tier === 'free'
        ? 'Free tier does not allow collaborators. Upgrade to Pro.'
        : `You've used all ${limits.collaborators} collaborator slots. Upgrade to Teams for unlimited collaborators.`,
      actionUrl: '/settings/billing/upgrade',
      tier: tier === 'free' ? 'pro' : 'teams',
    } : undefined,
  };
}

/**
 * Get comprehensive usage summary for user
 */
export async function getUsageSummary(
  userId: string,
  tier: TierName = 'free',
  tx?: PrismaClient
): Promise<UsageSummary> {
  const startOfMonth = new Date();
  startOfMonth.setDate(1);
  startOfMonth.setHours(0, 0, 0, 0);

  const endOfMonth = new Date(startOfMonth);
  endOfMonth.setMonth(endOfMonth.getMonth() + 1);

  const [moments, storage, aiInsights, memoryExpansions] = await Promise.all([
    checkMomentLimit(userId, tier, tx),
    checkStorageLimit(userId, tier, 0, tx),
    checkAIInsightLimit(userId, tier, tx),
    checkMemoryExpansionLimit(userId, tier, tx),
  ]);

  // Calculate transcription hours from usage tracking
  const db = tx || prisma;
  const transcriptionResult = await db.transcriptionUsage.aggregate({
    where: {
      userId,
      createdAt: { gte: startOfMonth },
      status: 'completed',
    },
    _sum: {
      durationSeconds: true,
    },
  });

  const transcriptionHours = (transcriptionResult._sum.durationSeconds || 0) / 3600;

  return {
    userId,
    tier,
    period: {
      start: startOfMonth,
      end: endOfMonth,
    },
    moments: {
      current: moments.currentUsage,
      limit: moments.limit,
      percentUsed: moments.percentUsed,
    },
    storage: {
      currentGB: storage.currentUsage,
      limitGB: storage.limit,
      percentUsed: storage.percentUsed,
    },
    transcription: {
      currentHours: transcriptionHours,
      limitHours: TIER_LIMITS[tier].transcriptionHoursPerMonth,
      percentUsed: 0,
    },
    aiInsights: {
      current: aiInsights.currentUsage,
      limit: aiInsights.limit,
      percentUsed: aiInsights.percentUsed,
    },
    memoryExpansions: {
      current: memoryExpansions.currentUsage,
      limit: memoryExpansions.limit,
      percentUsed: memoryExpansions.percentUsed,
    },
  };
}

/**
 * Middleware helper to enforce limits before operations
 */
export async function enforceLimit(
  userId: string,
  tier: TierName,
  limitType: 'moment' | 'aiInsight' | 'memoryExpansion' | 'sphere' | 'storage' | 'transcription',
  additionalBytes?: number
): Promise<void> {
  let result: UsageCheckResult;

  switch (limitType) {
    case 'moment':
      result = await checkMomentLimit(userId, tier);
      break;
    case 'aiInsight':
      result = await checkAIInsightLimit(userId, tier);
      break;
    case 'memoryExpansion':
      result = await checkMemoryExpansionLimit(userId, tier);
      break;
    case 'sphere':
      result = await checkSphereLimit(userId, tier);
      break;
    case 'storage':
      result = await checkStorageLimit(userId, tier, additionalBytes);
      break;
    case 'transcription':
      result = await checkTranscriptionLimit(userId, tier);
      break;
  }

  if (!result.allowed) {
    throw new Error(
      result.upgradePrompt?.message ||
      `${limitType} limit reached for ${tier} tier`
    );
  }
}
