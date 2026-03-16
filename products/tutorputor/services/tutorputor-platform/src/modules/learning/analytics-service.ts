/**
 * Analytics Service (Migrated)
 *
 * Learning analytics, diversity heatmaps, and predictive risk modeling.
 * Migrated from tutorputor-analytics/src/service.ts
 *
 * @doc.type service
 * @doc.purpose Analytics for learning outcomes
 * @doc.layer product
 * @doc.pattern Service
 */

import type { AnalyticsService } from "@ghatana/tutorputor-contracts/v1/services";
import { featureStoreClient } from "../../clients/feature-store.client.js";
import type {
  AnalyticsSummary,
  AdvancedAnalyticsSummary,
  StudentRiskIndicator,
  RiskFactor,
  RiskLevel,
  ModuleDifficultyHeatmap,
  UsageAnalytics,
  UsagePeriodData,
  LearningEventInput,
  LearningEventType,
  ModuleId,
  TenantId,
  UserId,
  ClassroomId,
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type { Redis } from "ioredis";

// =============================================================================
// Types
// =============================================================================

export type HealthAwareAnalyticsService = AnalyticsService & {
  checkHealth: () => Promise<boolean>;
};

// =============================================================================
// Implementation
// =============================================================================

export function createAnalyticsService(
  prisma: TutorPrismaClient,
  redis?: Redis,
): HealthAwareAnalyticsService {
  return {
    async recordEvent({ tenantId, event }) {
      // 1. Persistent Storage (Postgres)
      await prisma.learningEvent.create({
        data: {
          tenantId,
          userId: event.userId,
          moduleId: event.moduleId ?? null,
          eventType: event.type,
          payload: event.payload as any,
          timestamp: event.timestamp ? new Date(event.timestamp) : new Date(),
        },
      });

      // 2. Real-time Stream (Redis)
      if (redis) {
        try {
          // Add to Stream: learning:events:{tenantId}
          await redis.xadd(
            `learning:events:${tenantId}`,
            "MAXLEN",
            "~",
            10000,
            "*",
            "userId",
            event.userId,
            "type",
            event.type,
            "payload",
            JSON.stringify(event.payload || {}),
          );

          // Also publish to pub/sub for immediate ephemeral listeners
          await redis.publish(
            `learning:events:${tenantId}`,
            JSON.stringify(event),
          );
        } catch (err) {
          console.warn("Failed to publish learning event to Redis:", err);
        }
      }

      // 3. Platform Feature Store (fire-and-forget) — enriches ML feature vectors
      if (featureStoreClient) {
        featureStoreClient.ingestAsync(tenantId, event.userId, {
          eventType: event.type,
          moduleId: event.moduleId ?? "none",
          timestamp: event.timestamp ?? Date.now(),
          payloadSize: JSON.stringify(event.payload ?? {}).length,
        });
      }
    },

    async getSummary({ tenantId, moduleId }) {
      const where: any = { tenantId };
      if (moduleId) {
        where.moduleId = moduleId;
      }

      const [totalEvents, groupedEvents, activeUsers, completions] =
        await Promise.all([
          prisma.learningEvent.count({ where }),
          prisma.learningEvent.groupBy({
            where,
            by: ["eventType"],
            _count: { eventType: true },
          }),
          prisma.learningEvent.groupBy({
            where,
            by: ["userId"],
            _count: { userId: true },
          }),
          prisma.learningEvent.groupBy({
            where: { ...where, eventType: "module_completed" },
            by: ["moduleId"],
            _count: { moduleId: true },
            orderBy: { _count: { moduleId: "desc" } },
            take: 5,
          }),
        ]);

      const eventsByType: AnalyticsSummary["eventsByType"] =
        groupedEvents.reduce(
          (acc: AnalyticsSummary["eventsByType"], group: any) => {
            acc[group.eventType as LearningEventType] = group._count.eventType;
            return acc;
          },
          {
            module_viewed: 0,
            module_completed: 0,
            assessment_started: 0,
            assessment_completed: 0,
            ai_tutor_message: 0,
          },
        );

      return {
        tenantId,
        totalEvents,
        activeLearners: activeUsers.length,
        eventsByType,
        moduleCompletions: completions
          .filter((group: any) => group.moduleId)
          .map((group: any) => ({
            moduleId: group.moduleId as ModuleId,
            count: group._count.moduleId,
          })),
      };
    },

    async getAdvancedAnalytics({ tenantId, classroomId, period = "weekly" }) {
      const [baseSummary, atRiskStudents, difficultyHeatmap, usageTrends] =
        await Promise.all([
          this.getSummary({ tenantId }),
          this.getAtRiskStudents({ tenantId, classroomId }),
          this.getDifficultyHeatmap({ tenantId }),
          this.getUsageTrends({ tenantId, period }),
        ]);

      const predictions = calculatePredictions(usageTrends, atRiskStudents);

      return {
        ...baseSummary,
        atRiskStudents,
        difficultyHeatmap,
        usageTrends,
        predictions,
      };
    },

    async getAtRiskStudents({ tenantId, classroomId, minRiskLevel = "low" }) {
      const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
      const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

      const enrollmentWhere: any = {
        tenantId,
        status: { in: ["NOT_STARTED", "IN_PROGRESS"] },
      };

      if (classroomId) {
        enrollmentWhere.classroom = { id: classroomId };
      }

      const enrollments = await prisma.enrollment.findMany({
        where: enrollmentWhere,
        include: {
          user: { select: { id: true, email: true, displayName: true } },
        } as any,
      });

      const riskIndicators: StudentRiskIndicator[] = [];
      const minRiskScore = getRiskLevelThreshold(minRiskLevel);

      for (const enrollment of enrollments) {
        const recentEvents = await prisma.learningEvent.findMany({
          where: {
            tenantId,
            userId: enrollment.userId,
            timestamp: { gte: sevenDaysAgo },
          },
        });

        const assessmentAttempts = await prisma.assessmentAttempt.findMany({
          where: {
            tenantId,
            userId: enrollment.userId,
            startedAt: { gte: thirtyDaysAgo },
          },
          orderBy: { startedAt: "desc" as any },
          take: 10,
        });

        const factors: RiskFactor[] = [];
        let totalRiskScore = 0;

        // Factor 1: Inactivity
        const daysSinceActivity = recentEvents.length === 0 ? 7 : 0;
        if (daysSinceActivity >= 5) {
          const severity: RiskLevel =
            daysSinceActivity >= 7 ? "high" : "medium";
          factors.push({
            type: "inactivity",
            severity,
            description: `No activity in ${daysSinceActivity} days`,
            value: daysSinceActivity,
            threshold: 5,
          });
          totalRiskScore += severity === "high" ? 30 : 20;
        }

        // Factor 2: Low progress
        if (
          enrollment.progressPercent < 20 &&
          enrollment.timeSpentSeconds > 3600
        ) {
          factors.push({
            type: "low_progress",
            severity: "medium",
            description: "Low progress despite time investment",
            value: enrollment.progressPercent,
            threshold: 20,
          });
          totalRiskScore += 25;
        }

        // Factor 3: Failing assessments
        const failedAttempts = assessmentAttempts.filter(
          (a: any) => (a.scorePercent ?? 0) < 60,
        ).length;
        if (failedAttempts >= 3) {
          factors.push({
            type: "failing_assessments",
            severity: failedAttempts >= 5 ? "high" : "medium",
            description: `${failedAttempts} failed assessments recently`,
            value: failedAttempts,
            threshold: 3,
          });
          totalRiskScore += failedAttempts >= 5 ? 30 : 20;
        }

        // Factor 4: Declining engagement
        const recentEventCount = recentEvents.length;
        const previousWeekEvents = await prisma.learningEvent.count({
          where: {
            tenantId,
            userId: enrollment.userId,
            timestamp: {
              gte: new Date(sevenDaysAgo.getTime() - 7 * 24 * 60 * 60 * 1000),
              lt: sevenDaysAgo,
            },
          },
        });

        if (
          previousWeekEvents > recentEventCount * 2 &&
          previousWeekEvents > 5
        ) {
          factors.push({
            type: "declining_engagement",
            severity: "medium",
            description: "Engagement has dropped significantly",
            value: recentEventCount,
            threshold: previousWeekEvents / 2,
          });
          totalRiskScore += 20;
        }

        if (totalRiskScore < minRiskScore) continue;

        const riskLevel = calculateRiskLevel(totalRiskScore);
        const recommendations = generateRecommendations(factors);

        riskIndicators.push({
          userId: enrollment.userId as UserId,
          riskLevel,
          riskScore: Math.min(totalRiskScore, 100),
          factors,
          lastUpdated: new Date().toISOString(),
          recommendations,
        });
      }

      return riskIndicators.sort((a, b) => b.riskScore - a.riskScore);
    },

    async getDifficultyHeatmap({ tenantId, moduleIds }) {
      const moduleWhere: any = { tenantId, status: "PUBLISHED" };
      if (moduleIds && moduleIds.length > 0) {
        moduleWhere.id = { in: moduleIds };
      }

      const modules = await prisma.module.findMany({
        where: moduleWhere,
        select: { id: true, title: true },
      });

      const heatmapData: ModuleDifficultyHeatmap[] = [];

      for (const module of modules) {
        const enrollments = await prisma.enrollment.findMany({
          where: { tenantId, moduleId: module.id },
          select: {
            status: true,
            progressPercent: true,
            timeSpentSeconds: true,
          },
        });

        if (enrollments.length === 0) continue;

        const completedEnrollments = enrollments.filter(
          (e: any) => e.status === "COMPLETED",
        );
        const avgCompletionTime =
          completedEnrollments.length > 0
            ? completedEnrollments.reduce(
              (sum: number, e: any) => sum + e.timeSpentSeconds,
              0,
            ) /
            completedEnrollments.length /
            60
            : 0;

        const attempts = await prisma.assessmentAttempt.findMany({
          where: {
            tenantId,
            assessment: { moduleId: module.id },
          },
          select: { scorePercent: true, userId: true },
        });

        const avgAttempts =
          attempts.length > 0
            ? attempts.length / new Set(attempts.map((a: any) => a.userId)).size
            : 1;

        const failedAttempts = attempts.filter(
          (a: any) => (a.scorePercent ?? 0) < 60,
        ).length;
        const failureRate =
          attempts.length > 0 ? (failedAttempts / attempts.length) * 100 : 0;

        const startedNotCompleted = enrollments.filter(
          (e: any) => e.status === "IN_PROGRESS" && e.progressPercent < 50,
        ).length;
        const dropOffRate =
          enrollments.length > 0
            ? (startedNotCompleted / enrollments.length) * 100
            : 0;

        const difficultyScore = Math.min(
          100,
          Math.round(
            failureRate * 0.4 +
            dropOffRate * 0.3 +
            (Math.min(avgAttempts, 5) / 5) * 30,
          ),
        );

        heatmapData.push({
          moduleId: module.id as ModuleId,
          moduleTitle: module.title,
          averageCompletionTime: Math.round(avgCompletionTime),
          averageAttempts: Math.round(avgAttempts * 10) / 10,
          failureRate: Math.round(failureRate * 10) / 10,
          dropOffRate: Math.round(dropOffRate * 10) / 10,
          difficultyScore,
        });
      }

      return heatmapData.sort((a, b) => b.difficultyScore - a.difficultyScore);
    },

    async getUsageTrends({ tenantId, period, days = 30 }) {
      const periodDays = period === "daily" ? 1 : period === "weekly" ? 7 : 30;
      const numPeriods = Math.ceil(days / periodDays);

      const data: UsagePeriodData[] = [];

      for (let i = 0; i < numPeriods; i++) {
        const endDate = new Date(
          Date.now() - i * periodDays * 24 * 60 * 60 * 1000,
        );
        const startDate = new Date(
          endDate.getTime() - periodDays * 24 * 60 * 60 * 1000,
        );

        const [events, enrollments] = await Promise.all([
          prisma.learningEvent.findMany({
            where: {
              tenantId,
              timestamp: { gte: startDate, lt: endDate },
            },
            select: { eventType: true, userId: true },
          }),
          prisma.enrollment.findMany({
            where: {
              tenantId,
              startedAt: { gte: startDate, lt: endDate },
            },
          }),
        ]);

        const activeUsers = new Set(events.map((e: any) => e.userId)).size;
        const completions = events.filter(
          (e: any) => e.eventType === "module_completed",
        ).length;
        const assessmentAttempts = events.filter(
          (e: any) => e.eventType === "assessment_completed",
        ).length;
        const aiTutorQueries = events.filter(
          (e: any) => e.eventType === "ai_tutor_message",
        ).length;

        data.push({
          date: startDate.toISOString().split("T")[0]!,
          activeUsers,
          newEnrollments: enrollments.length,
          completions,
          totalTimeMinutes: 0,
          assessmentAttempts,
          aiTutorQueries,
        });
      }

      return {
        period,
        data: data.reverse(),
      };
    },

    async checkHealth() {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    },
  };
}

// =============================================================================
// Helper Functions
// =============================================================================

function getRiskLevelThreshold(level: RiskLevel): number {
  switch (level) {
    case "critical":
      return 80;
    case "high":
      return 60;
    case "medium":
      return 30;
    case "low":
      return 0;
  }
}

function calculateRiskLevel(score: number): RiskLevel {
  if (score >= 80) return "critical";
  if (score >= 60) return "high";
  if (score >= 30) return "medium";
  return "low";
}

function generateRecommendations(factors: RiskFactor[]): string[] {
  const recommendations: string[] = [];
  for (const factor of factors) {
    switch (factor.type) {
      case "inactivity":
        recommendations.push("Send a re-engagement notification");
        recommendations.push("Suggest bite-sized learning activities");
        break;
      case "low_progress":
        recommendations.push("Offer tutoring support or office hours");
        recommendations.push("Review if prerequisites are met");
        break;
      case "failing_assessments":
        recommendations.push("Provide additional practice resources");
        recommendations.push("Consider adaptive difficulty settings");
        break;
      case "declining_engagement":
        recommendations.push("Check in with personalized message");
        recommendations.push("Offer alternative learning paths");
        break;
    }
  }
  return [...new Set(recommendations)];
}

function calculatePredictions(
  usageTrends: UsageAnalytics,
  atRiskStudents: StudentRiskIndicator[],
): AdvancedAnalyticsSummary["predictions"] {
  const recentData = usageTrends.data.slice(-7);
  const completionTrend =
    recentData.length > 1 && recentData[recentData.length - 1] && recentData[0]
      ? recentData[recentData.length - 1]!.completions -
      recentData[0]!.completions
      : 0;

  const avgCompletions =
    recentData.length > 0
      ? recentData.reduce((sum, d) => sum + d.completions, 0) /
      recentData.length
      : 0;

  const projectedCompletions = Math.round(avgCompletions * 7);
  const projectedAtRiskCount = atRiskStudents.filter(
    (s) => s.riskLevel !== "low",
  ).length;

  let trendDirection: "improving" | "stable" | "declining" = "stable";
  if (completionTrend > 2) trendDirection = "improving";
  else if (completionTrend < -2) trendDirection = "declining";

  return {
    projectedCompletions,
    projectedAtRiskCount,
    trendDirection,
    confidenceScore: Math.min(95, 60 + recentData.length * 5),
  };
}
