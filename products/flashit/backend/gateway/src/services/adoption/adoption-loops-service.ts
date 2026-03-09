/**
 * Adoption Loops Service
 * Week 14 Day 68 - Smart suggestions, reminders, and usage nudges
 * 
 * @doc.type service
 * @doc.purpose Drive user engagement through intelligent suggestions
 * @doc.layer product
 * @doc.pattern AdoptionService
 */

import { prisma } from '../../lib/prisma.js';
import { differenceInDays, subDays } from 'date-fns';

// ============================================================================
// Types
// ============================================================================

export interface Suggestion {
  type: 'moment_reminder' | 'link_suggestion' | 'reflection_prompt' | 'expansion_opportunity';
  title: string;
  description: string;
  actionText: string;
  actionUrl?: string;
  metadata?: Record<string, any>;
  priority: 'high' | 'medium' | 'low';
}

export interface OnboardingStep {
  id: string;
  title: string;
  description: string;
  completed: boolean;
  completedAt?: Date;
}

// ============================================================================
// Smart Suggestions Engine
// ============================================================================

export class AdoptionLoopsService {
  /**
   * Generate personalized suggestions for a user
   */
  static async generateSuggestions(userId: string): Promise<Suggestion[]> {
    const suggestions: Suggestion[] = [];

    // Check last capture activity
    const lastMoment = await prisma.moment.findFirst({
      where: { userId, deletedAt: null },
      orderBy: { capturedAt: 'desc' },
    });

    if (lastMoment) {
      const daysSinceLastCapture = differenceInDays(new Date(), lastMoment.capturedAt);

      // Reminder to capture moments
      if (daysSinceLastCapture > 3) {
        suggestions.push({
          type: 'moment_reminder',
          title: 'Time to capture your day',
          description: `It's been ${daysSinceLastCapture} days since your last moment. What's been happening?`,
          actionText: 'Capture a moment',
          actionUrl: '/app/capture',
          priority: 'high',
        });
      }
    }

    // Check for unlinkable moments (no links)
    const unlinkedMoments = await prisma.moment.count({
      where: {
        userId,
        deletedAt: null,
        sourceMomentLinks: { none: {} },
        targetMomentLinks: { none: {} },
      },
    });

    if (unlinkedMoments > 5) {
      suggestions.push({
        type: 'link_suggestion',
        title: 'Connect your memories',
        description: `You have ${unlinkedMoments} unlinked moments. Linking helps discover patterns over time.`,
        actionText: 'Explore connections',
        actionUrl: '/app/moments',
        priority: 'medium',
      });
    }

    // Check for reflection opportunities
    const moments = await prisma.moment.count({
      where: {
        userId,
        deletedAt: null,
        capturedAt: { gte: subDays(new Date(), 30) },
      },
    });

    if (moments >= 10) {
      suggestions.push({
        type: 'expansion_opportunity',
        title: 'Discover insights from your month',
        description: `You've captured ${moments} moments this month. Request an AI expansion to identify themes and patterns.`,
        actionText: 'Generate insights',
        actionUrl: '/app/moments',
        metadata: { momentCount: moments },
        priority: 'high',
      });
    }

    // Weekly reflection prompt
    const now = new Date();
    const dayOfWeek = now.getDay();
    if (dayOfWeek === 0) {
      // Sunday
      suggestions.push({
        type: 'reflection_prompt',
        title: 'Weekly reflection time',
        description: 'What was the most meaningful moment of your week? Capture it before it fades.',
        actionText: 'Reflect now',
        actionUrl: '/app/capture',
        priority: 'medium',
      });
    }

    // Language evolution insights
    const hasLanguageData = await prisma.moment.count({
      where: {
        userId,
        deletedAt: null,
        capturedAt: { gte: subDays(new Date(), 60) },
      },
    });

    if (hasLanguageData >= 20) {
      suggestions.push({
        type: 'reflection_prompt',
        title: 'Your language is evolving',
        description: 'See how your vocabulary and emotions have changed over time.',
        actionText: 'View evolution',
        actionUrl: '/app/language-insights',
        priority: 'low',
      });
    }

    return suggestions;
  }

  /**
   * Get onboarding checklist for user
   */
  static async getOnboardingStatus(userId: string): Promise<OnboardingStep[]> {
    // Check various completion criteria
    const [firstMoment, firstLink, firstSearch, firstExpansion] = await Promise.all([
      prisma.moment.findFirst({
        where: { userId },
        orderBy: { capturedAt: 'asc' },
      }),
      prisma.momentLink.findFirst({
        where: {
          OR: [{ sourceMoment: { userId } }, { targetMoment: { userId } }],
        },
      }),
      prisma.auditEvent.findFirst({
        where: {
          userId,
          eventType: 'SEARCH_PERFORMED',
        },
      }),
      prisma.$queryRaw<any[]>`
        SELECT id FROM expansion_results
        WHERE user_id = ${userId}
        LIMIT 1
      `,
    ]);

    const steps: OnboardingStep[] = [
      {
        id: 'first_moment',
        title: 'Capture your first moment',
        description: 'Share a thought, feeling, or experience',
        completed: !!firstMoment,
        completedAt: firstMoment?.capturedAt,
      },
      {
        id: 'capture_10_moments',
        title: 'Capture 10 moments',
        description: 'Build your personal context library',
        completed: false, // Check count
      },
      {
        id: 'first_link',
        title: 'Link two moments',
        description: 'Connect related memories to discover patterns',
        completed: !!firstLink,
        completedAt: firstLink?.createdAt,
      },
      {
        id: 'first_search',
        title: 'Search your moments',
        description: 'Use semantic search to find past experiences',
        completed: !!firstSearch,
        completedAt: firstSearch?.timestamp,
      },
      {
        id: 'first_expansion',
        title: 'Request an AI insight',
        description: 'Get deeper analysis of your moment patterns',
        completed: firstExpansion.length > 0,
      },
      {
        id: 'explore_language',
        title: 'View language evolution',
        description: 'See how your expression has changed',
        completed: false, // Manual tracking required
      },
    ];

    // Get moment count for step 2
    const momentCount = await prisma.moment.count({ where: { userId } });
    steps[1].completed = momentCount >= 10;

    return steps;
  }

  /**
   * Record suggestion interaction
   */
  static async recordSuggestionInteraction(
    userId: string,
    suggestionType: Suggestion['type'],
    action: 'viewed' | 'clicked' | 'dismissed'
  ) {
    await prisma.auditEvent.create({
      data: {
        userId,
        eventType: 'SUGGESTION_INTERACTION',
        entityType: 'suggestion',
        entityId: suggestionType,
        metadata: { action },
        timestamp: new Date(),
      },
    });
  }

  /**
   * Get usage statistics for engagement tracking
   */
  static async getUserEngagement(userId: string): Promise<{
    totalMoments: number;
    momentsLast7Days: number;
    momentsLast30Days: number;
    totalLinks: number;
    totalSearches: number;
    totalExpansions: number;
    avgMomentsPerWeek: number;
    lastActiveDate: Date | null;
    engagementLevel: 'high' | 'medium' | 'low' | 'inactive';
  }> {
    const [totalMoments, momentsLast7Days, momentsLast30Days, links, searches, expansions, lastMoment] =
      await Promise.all([
        prisma.moment.count({ where: { userId, deletedAt: null } }),
        prisma.moment.count({
          where: {
            userId,
            deletedAt: null,
            capturedAt: { gte: subDays(new Date(), 7) },
          },
        }),
        prisma.moment.count({
          where: {
            userId,
            deletedAt: null,
            capturedAt: { gte: subDays(new Date(), 30) },
          },
        }),
        prisma.momentLink.count({
          where: {
            OR: [{ sourceMoment: { userId } }, { targetMoment: { userId } }],
          },
        }),
        prisma.auditEvent.count({
          where: { userId, eventType: 'SEARCH_PERFORMED' },
        }),
        prisma.$queryRaw<any[]>`
          SELECT COUNT(*) as count FROM expansion_results WHERE user_id = ${userId}
        `,
        prisma.moment.findFirst({
          where: { userId, deletedAt: null },
          orderBy: { capturedAt: 'desc' },
        }),
      ]);

    // Calculate average moments per week (last 8 weeks)
    const weeksCount = 8;
    const avgMomentsPerWeek = momentsLast30Days / 4; // Approximate

    // Determine engagement level
    let engagementLevel: 'high' | 'medium' | 'low' | 'inactive';
    if (momentsLast7Days >= 5) {
      engagementLevel = 'high';
    } else if (momentsLast7Days >= 2) {
      engagementLevel = 'medium';
    } else if (momentsLast30Days > 0) {
      engagementLevel = 'low';
    } else {
      engagementLevel = 'inactive';
    }

    return {
      totalMoments,
      momentsLast7Days,
      momentsLast30Days,
      totalLinks: links,
      totalSearches: searches,
      totalExpansions: expansions[0]?.count || 0,
      avgMomentsPerWeek: Math.round(avgMomentsPerWeek * 10) / 10,
      lastActiveDate: lastMoment?.capturedAt || null,
      engagementLevel,
    };
  }

  /**
   * Generate personalized reminder cadence
   */
  static async getRecommendedReminderCadence(
    userId: string
  ): Promise<{
    frequency: 'daily' | 'every_3_days' | 'weekly' | 'as_needed';
    bestTimeOfDay: 'morning' | 'afternoon' | 'evening';
    reasoning: string;
  }> {
    const engagement = await this.getUserEngagement(userId);

    // Analyze capture patterns to suggest timing
    const captureHours = await prisma.$queryRaw<any[]>`
      SELECT EXTRACT(HOUR FROM captured_at) as hour, COUNT(*) as count
      FROM moments
      WHERE user_id = ${userId} AND deleted_at IS NULL
      GROUP BY hour
      ORDER BY count DESC
      LIMIT 1
    `;

    const preferredHour = captureHours[0]?.hour || 18; // Default to 6pm
    let bestTimeOfDay: 'morning' | 'afternoon' | 'evening';
    if (preferredHour < 12) {
      bestTimeOfDay = 'morning';
    } else if (preferredHour < 18) {
      bestTimeOfDay = 'afternoon';
    } else {
      bestTimeOfDay = 'evening';
    }

    // Suggest frequency based on engagement
    let frequency: 'daily' | 'every_3_days' | 'weekly' | 'as_needed';
    let reasoning: string;

    if (engagement.engagementLevel === 'high') {
      frequency = 'as_needed';
      reasoning = "You're highly engaged! We'll only remind you if you miss a day.";
    } else if (engagement.engagementLevel === 'medium') {
      frequency = 'every_3_days';
      reasoning = 'Gentle reminders every few days to help you stay consistent.';
    } else if (engagement.engagementLevel === 'low') {
      frequency = 'weekly';
      reasoning = 'Weekly check-ins to help re-establish your journaling habit.';
    } else {
      frequency = 'daily';
      reasoning = 'Daily reminders to help you build momentum.';
    }

    return {
      frequency,
      bestTimeOfDay,
      reasoning,
    };
  }
}
