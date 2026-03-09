/**
 * @doc.type service
 * @doc.purpose VR Analytics service for tracking usage and learning outcomes
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';
import type {
  VRAnalyticsService,
  TenantId,
  UserId,
} from '@ghatana/tutorputor-contracts/v1';
import type { VRLabId, VRLabAnalytics, VRDeviceType, VRSessionId } from '@ghatana/tutorputor-contracts/v1';

export class VRAnalyticsServiceImpl implements VRAnalyticsService {
  constructor(private prisma: PrismaClient) {}

  async getLabAnalytics(args: {
    tenantId: TenantId;
    labId: VRLabId;
    period: 'day' | 'week' | 'month' | 'all';
  }): Promise<VRLabAnalytics> {
    const { tenantId, labId, period } = args;

    const startDate = this.getStartDate(period);

    const sessions = await this.prisma.vRSession.findMany({
      where: {
        tenantId,
        labId,
        startedAt: startDate ? { gte: startDate } : undefined,
      },
    });

    const lab = await this.prisma.vRLab.findFirst({
      where: { id: labId },
      include: { objectives: true },
    });

    if (!lab) {
      throw new Error('Lab not found');
    }

    // Calculate metrics
    const uniqueUsers = new Set(sessions.map((s) => s.userId)).size;
    const totalDuration = sessions.reduce((sum, s) => sum + s.totalDuration, 0);
    const avgDuration = sessions.length > 0 ? totalDuration / sessions.length : 0;

    // Completion rate
    const completedSessions = sessions.filter((s) => s.status === 'completed').length;
    const completionRate = sessions.length > 0 ? completedSessions / sessions.length : 0;

    // Average score
    const totalScore = sessions.reduce((sum, s) => {
      const progress = s.progress as any;
      return sum + (progress?.totalPoints || 0);
    }, 0);
    const avgScore = sessions.length > 0 ? totalScore / sessions.length : 0;

    // Objective completion rates
    const objectiveCompletionRates: Record<string, number> = {};
    for (const objective of lab.objectives) {
      const completions = sessions.filter((s) => {
        const progress = s.progress as any;
        return progress?.completedObjectives?.includes(objective.id);
      }).length;
      objectiveCompletionRates[objective.id] = sessions.length > 0 ? completions / sessions.length : 0;
    }

    // Most interacted objects
    const interactionCounts: Map<string, { id: string; name: string; count: number }> = new Map();
    for (const session of sessions) {
      const progress = session.progress as any;
      for (const log of progress?.interactionsLog || []) {
        const existing = interactionCounts.get(log.interactableId);
        if (existing) {
          existing.count++;
        } else {
          interactionCounts.set(log.interactableId, {
            id: log.interactableId,
            name: log.interactableId, // Would need to join with interactables
            count: 1,
          });
        }
      }
    }
    const mostInteractedObjects = Array.from(interactionCounts.values())
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);

    // Scene time distribution
    const sceneTimeDistribution: Record<string, number> = {};
    for (const session of sessions) {
      const progress = session.progress as any;
      for (const sceneId of progress?.scenesVisited || []) {
        sceneTimeDistribution[sceneId] = (sceneTimeDistribution[sceneId] || 0) + 1;
      }
    }

    // Average FPS
    const fpsSum = sessions.reduce((sum, s) => {
      const metrics = s.performanceMetrics as any;
      return sum + (metrics?.averageFps || 0);
    }, 0);
    const avgFps = sessions.length > 0 ? fpsSum / sessions.length : 0;

    // Crash rate
    const crashedSessions = sessions.filter((s) => s.status === 'failed').length;
    const crashRate = sessions.length > 0 ? crashedSessions / sessions.length : 0;

    // Device distribution
    const deviceDistribution: Record<VRDeviceType, number> = {
      quest_2: 0,
      quest_3: 0,
      quest_pro: 0,
      vive: 0,
      index: 0,
      pico: 0,
      desktop: 0,
      mobile: 0,
    };
    for (const session of sessions) {
      const device = session.deviceType as VRDeviceType;
      deviceDistribution[device] = (deviceDistribution[device] || 0) + 1;
    }

    return {
      labId,
      period,
      totalSessions: sessions.length,
      uniqueUsers,
      averageSessionDuration: avgDuration,
      completionRate,
      averageScore: avgScore,
      objectiveCompletionRates,
      mostInteractedObjects,
      sceneTimeDistribution,
      averageFps: avgFps,
      crashRate,
      deviceDistribution,
    };
  }

  async getUserProgress(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<{
    totalLabsCompleted: number;
    totalTimeSpent: number;
    averageScore: number;
    labProgress: Array<{
      labId: VRLabId;
      labTitle: string;
      completionRate: number;
      lastSessionAt: string;
      bestScore: number;
    }>;
  }> {
    const { tenantId, userId } = args;

    // Get all user sessions
    const sessions = await this.prisma.vRSession.findMany({
      where: { tenantId, userId },
      orderBy: { startedAt: 'desc' },
    });

    // Get labs info
    const labIds = [...new Set(sessions.map((s) => s.labId))];
    const labs = await this.prisma.vRLab.findMany({
      where: { id: { in: labIds } },
      include: { objectives: true },
    });

    const labMap = new Map(labs.map((l) => [l.id, l]));

    // Calculate total time spent
    const totalTimeSpent = sessions.reduce((sum, s) => sum + s.totalDuration, 0);

    // Calculate lab progress
    const labProgressMap: Map<
      string,
      {
        labId: string;
        labTitle: string;
        sessions: any[];
        maxPoints: number;
      }
    > = new Map();

    for (const session of sessions) {
      const lab = labMap.get(session.labId);
      if (!lab) continue;

      const existing = labProgressMap.get(session.labId);
      if (existing) {
        existing.sessions.push(session);
      } else {
        labProgressMap.set(session.labId, {
          labId: session.labId,
          labTitle: lab.title,
          sessions: [session],
          maxPoints: lab.objectives.reduce((sum, o) => sum + o.points, 0),
        });
      }
    }

    const labProgress = Array.from(labProgressMap.values()).map((entry) => {
      const completedSessions = entry.sessions.filter((s) => s.status === 'completed');
      const completionRate =
        entry.sessions.length > 0 ? completedSessions.length / entry.sessions.length : 0;

      const scores = entry.sessions.map((s) => {
        const progress = s.progress as any;
        return progress?.totalPoints || 0;
      });
      const bestScore = Math.max(...scores, 0);

      return {
        labId: entry.labId,
        labTitle: entry.labTitle,
        completionRate,
        lastSessionAt: entry.sessions[0].startedAt.toISOString(),
        bestScore,
      };
    });

    // Calculate overall stats
    const completedLabs = labProgress.filter((l) => l.completionRate > 0.8).length;
    const totalScore = sessions.reduce((sum, s) => {
      const progress = s.progress as any;
      return sum + (progress?.totalPoints || 0);
    }, 0);
    const avgScore = sessions.length > 0 ? totalScore / sessions.length : 0;

    return {
      totalLabsCompleted: completedLabs,
      totalTimeSpent,
      averageScore: avgScore,
      labProgress,
    };
  }

  async getAggregatedAnalytics(args: {
    tenantId: TenantId;
    period: 'day' | 'week' | 'month';
  }): Promise<{
    totalSessions: number;
    uniqueUsers: number;
    averageSessionDuration: number;
    popularLabs: Array<{ labId: VRLabId; title: string; sessions: number }>;
    deviceUsage: Record<VRDeviceType, number>;
    completionTrend: Array<{ date: string; completions: number }>;
  }> {
    const { tenantId, period } = args;

    const startDate = this.getStartDate(period);

    const sessions = await this.prisma.vRSession.findMany({
      where: {
        tenantId,
        startedAt: startDate ? { gte: startDate } : undefined,
      },
    });

    // Get labs for titles
    const labIds = [...new Set(sessions.map((s) => s.labId))];
    const labs = await this.prisma.vRLab.findMany({
      where: { id: { in: labIds } },
    });
    const labMap = new Map(labs.map((l) => [l.id, l]));

    // Total sessions and unique users
    const totalSessions = sessions.length;
    const uniqueUsers = new Set(sessions.map((s) => s.userId)).size;

    // Average duration
    const totalDuration = sessions.reduce((sum, s) => sum + s.totalDuration, 0);
    const avgDuration = totalSessions > 0 ? totalDuration / totalSessions : 0;

    // Popular labs
    const labSessionCounts: Map<string, number> = new Map();
    for (const session of sessions) {
      labSessionCounts.set(session.labId, (labSessionCounts.get(session.labId) || 0) + 1);
    }
    const popularLabs = Array.from(labSessionCounts.entries())
      .map(([labId, count]) => ({
        labId,
        title: labMap.get(labId)?.title || 'Unknown',
        sessions: count,
      }))
      .sort((a, b) => b.sessions - a.sessions)
      .slice(0, 10);

    // Device usage
    const deviceUsage: Record<VRDeviceType, number> = {
      quest_2: 0,
      quest_3: 0,
      quest_pro: 0,
      vive: 0,
      index: 0,
      pico: 0,
      desktop: 0,
      mobile: 0,
    };
    for (const session of sessions) {
      const device = session.deviceType as VRDeviceType;
      deviceUsage[device] = (deviceUsage[device] || 0) + 1;
    }

    // Completion trend
    const completionsByDate: Map<string, number> = new Map();
    for (const session of sessions) {
      if (session.status === 'completed' && session.endedAt) {
        const date = session.endedAt.toISOString().split('T')[0];
        completionsByDate.set(date, (completionsByDate.get(date) || 0) + 1);
      }
    }
    const completionTrend = Array.from(completionsByDate.entries())
      .map(([date, completions]) => ({ date, completions }))
      .sort((a, b) => a.date.localeCompare(b.date));

    return {
      totalSessions,
      uniqueUsers,
      averageSessionDuration: avgDuration,
      popularLabs,
      deviceUsage,
      completionTrend,
    };
  }

  async trackEvent(args: {
    tenantId: TenantId;
    userId: UserId;
    event: {
      type: string;
      labId?: VRLabId;
      sessionId?: VRSessionId;
      metadata?: Record<string, unknown>;
    };
  }): Promise<void> {
    const { tenantId, userId, event } = args;

    await this.prisma.vRAnalyticsEvent.create({
      data: {
        id: crypto.randomUUID(),
        tenantId,
        userId,
        eventType: event.type,
        labId: event.labId,
        sessionId: event.sessionId,
        metadata: event.metadata || {},
        timestamp: new Date(),
      },
    });
  }

  // ============================================
  // Private helper methods
  // ============================================

  private getStartDate(period: 'day' | 'week' | 'month' | 'all'): Date | undefined {
    const now = new Date();

    switch (period) {
      case 'day':
        return new Date(now.setHours(0, 0, 0, 0));
      case 'week':
        return new Date(now.setDate(now.getDate() - 7));
      case 'month':
        return new Date(now.setMonth(now.getMonth() - 1));
      case 'all':
        return undefined;
    }
  }
}
