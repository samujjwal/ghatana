/**
 * Enhanced Predictive Analytics Service
 *
 * Advanced predictive analytics for learning optimization.
 *
 * @doc.type class
 * @doc.purpose Provide advanced predictive analytics for learning optimization
 * @doc.layer product
 * @doc.pattern Analytics Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type { TutorPrismaClient } from '@tutorputor/core/db';

const logger = createStandaloneLogger({ component: 'EnhancedPredictiveAnalyticsService' });

export interface LearningPathPrediction {
  userId: string;
  recommendedPath: Array<{
    moduleId: string;
    moduleName: string;
    probability: number;
    estimatedDurationMinutes: number;
    difficulty: 'INTRO' | 'INTERMEDIATE' | 'ADVANCED';
  }>;
  confidence: number;
  reasoning: string[];
}

export interface MasteryPrediction {
  userId: string;
  conceptId: string;
  conceptName: string;
  currentMastery: number;
  predictedMastery: number;
  timeToMasteryDays: number;
  recommendedActions: string[];
}

export interface DropoutPrediction {
  userId: string;
  dropoutProbability: number;
  riskFactors: Array<{
    factor: string;
    impact: number;
    description: string;
  }>;
  interventionWindow: string;
  recommendedActions: string[];
}

export class EnhancedPredictiveAnalyticsService {
  constructor(private readonly prisma: TutorPrismaClient) {}

  /**
   * Predict optimal learning path
   */
  async predictLearningPath(tenantId: string, userId: string, goal: string): Promise<LearningPathPrediction> {
    logger.info({
      message: 'Predicting learning path',
      tenantId,
      userId,
      goal,
    });

    const [enrollments, learnerProfile] = await Promise.all([
      this.prisma.enrollment.findMany({
        where: { tenantId, userId },
        include: { module: { select: { id: true, title: true, difficulty: true } } },
      }),
      this.prisma.learnerProfile.findFirst({
        where: { tenantId, userId },
        include: { masteries: { select: { masteryProbability: true } } },
      }),
    ]);

    const completedModules = enrollments.filter((e) => e.status === 'COMPLETED').map((e) => e.moduleId);
    const currentMastery = learnerProfile == null || learnerProfile.masteries.length === 0
      ? 0.5
      : learnerProfile.masteries.reduce((sum, mastery) => sum + mastery.masteryProbability, 0) /
        learnerProfile.masteries.length;

    // Get available modules
    const availableModules = await this.prisma.module.findMany({
      where: {
        tenantId,
        status: 'PUBLISHED',
        id: { notIn: completedModules },
      },
      select: { id: true, title: true, difficulty: true },
      take: 10,
    });

    // Score and rank modules based on learner profile
    const scoredModules = availableModules.map((module) => {
      const difficultyMatch = this.calculateDifficultyMatch(currentMastery, module.difficulty);
      const probability = Math.min(0.95, difficultyMatch * 0.7 + Math.random() * 0.3);
      return {
        moduleId: module.id,
        moduleName: module.title,
        probability,
        estimatedDurationMinutes: this.estimateDuration(module.difficulty),
        difficulty: module.difficulty,
      };
    });

    const recommendedPath = scoredModules
      .sort((a, b) => b.probability - a.probability)
      .slice(0, 5);

    const reasoning = [
      currentMastery > 0.7 ? 'High mastery - recommend advanced content' : 'Building foundation - recommend intermediate content',
      completedModules.length > 0 ? `Completed ${completedModules.length} modules` : 'No completed modules',
    ];

    return {
      userId,
      recommendedPath,
      confidence: Math.min(0.9, 0.5 + completedModules.length * 0.1),
      reasoning,
    };
  }

  /**
   * Predict mastery for specific concept
   */
  async predictMastery(tenantId: string, userId: string, conceptId: string): Promise<MasteryPrediction> {
    logger.info({
      message: 'Predicting mastery',
      tenantId,
      userId,
      conceptId,
    });

    const [learnerProfile, conceptData] = await Promise.all([
      this.prisma.learnerProfile.findFirst({
        where: { tenantId, userId },
        select: { id: true },
      }),
      this.prisma.learnerMastery.findMany({
        where: {
          tenantId,
          conceptId,
          profile: { userId },
        },
        select: { masteryProbability: true },
      }),
    ]);

    const currentMastery = conceptData[0]?.masteryProbability ?? 0;
    const historicalRate = learnerProfile == null
      ? 0.5
      : await this.calculateAverageMastery(tenantId, userId);

    // Predict future mastery based on historical rate
    const predictedMastery = Math.min(1, currentMastery + (historicalRate * 0.1));
    const daysToMastery = currentMastery >= 0.9 ? 0 : Math.ceil((0.9 - currentMastery) / (historicalRate * 0.01));

    const recommendedActions: string[] = [];
    if (currentMastery < 0.5) {
      recommendedActions.push('Complete prerequisite modules');
      recommendedActions.push('Practice with adaptive difficulty');
    } else if (currentMastery < 0.8) {
      recommendedActions.push('Apply knowledge in simulations');
      recommendedActions.push('Teach concepts to peers');
    }

    return {
      userId,
      conceptId,
      conceptName: conceptId, // In production, fetch actual concept name
      currentMastery,
      predictedMastery,
      timeToMasteryDays: daysToMastery,
      recommendedActions,
    };
  }

  /**
   * Predict dropout risk
   */
  async predictDropout(tenantId: string, userId: string): Promise<DropoutPrediction> {
    logger.info({
      message: 'Predicting dropout risk',
      tenantId,
      userId,
    });

    const [enrollments, attempts, recentEvents] = await Promise.all([
      this.prisma.enrollment.findMany({
        where: { tenantId, userId },
        orderBy: { startedAt: 'desc' },
        take: 10,
      }),
      this.prisma.assessmentAttempt.findMany({
        where: { tenantId, userId },
        orderBy: { startedAt: 'desc' },
        take: 10,
      }),
      this.prisma.learningEvent.findMany({
        where: { tenantId, userId, timestamp: { gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) } },
      }),
    ]);

    const riskFactors: Array<{ factor: string; impact: number; description: string }> = [];
    let dropoutProbability = 0;

    // Factor: Low completion rate
    const completionRate = enrollments.filter((e) => e.status === 'COMPLETED').length / enrollments.length || 0;
    if (completionRate < 0.3) {
      riskFactors.push({
        factor: 'low_completion_rate',
        impact: 0.3,
        description: 'Low module completion rate',
      });
      dropoutProbability += 0.3;
    }

    // Factor: Declining engagement
    if (recentEvents.length < 5) {
      riskFactors.push({
        factor: 'low_engagement',
        impact: 0.25,
        description: 'Low recent activity',
      });
      dropoutProbability += 0.25;
    }

    // Factor: Poor assessment performance
    const avgScore = attempts.reduce((sum, a) => sum + (a.scorePercent || 0), 0) / attempts.length || 0;
    if (avgScore < 60) {
      riskFactors.push({
        factor: 'poor_performance',
        impact: 0.2,
        description: 'Low assessment scores',
      });
      dropoutProbability += 0.2;
    }

    // Factor: Stalled progress
    const recentEnrollments = enrollments.filter((e) => e.status === 'IN_PROGRESS');
    const stalledEnrollments = recentEnrollments.filter((e) => {
      if (e.startedAt == null) {
        return false;
      }
      return e.progressPercent < 20 && (Date.now() - e.startedAt.getTime()) > 7 * 24 * 60 * 60 * 1000;
    });
    if (stalledEnrollments.length > 0) {
      riskFactors.push({
        factor: 'stalled_progress',
        impact: 0.15,
        description: 'Stalled module progress',
      });
      dropoutProbability += 0.15;
    }

    dropoutProbability = Math.min(0.95, dropoutProbability);

    const recommendedActions: string[] = [];
    if (dropoutProbability > 0.7) {
      recommendedActions.push('Immediate intervention: Schedule one-on-one meeting');
      recommendedActions.push('Review learning path and adjust difficulty');
    } else if (dropoutProbability > 0.4) {
      recommendedActions.push('Send personalized encouragement message');
      recommendedActions.push('Offer additional support resources');
    }

    const interventionWindow = dropoutProbability > 0.7 ? 'Immediate (within 24 hours)' : 'Within 1 week';

    return {
      userId,
      dropoutProbability,
      riskFactors,
      interventionWindow,
      recommendedActions,
    };
  }

  /**
   * Analyze content gaps
   */
  async analyzeContentGaps(tenantId: string, classroomId?: string): Promise<Array<{
    conceptId: string;
    conceptName: string;
    gapSeverity: 'low' | 'medium' | 'high';
    affectedStudents: number;
    recommendedContent: string[];
  }>> {
    logger.info({
      message: 'Analyzing content gaps',
      tenantId,
      classroomId,
    });

    // In production, this would analyze mastery data across students
    // For now, return placeholder data
    return [
      {
        conceptId: 'concept-1',
        conceptName: 'Basic Algebra',
        gapSeverity: 'medium',
        affectedStudents: 15,
        recommendedContent: ['Algebra fundamentals module', 'Practice problems'],
      },
    ];
  }

  private calculateDifficultyMatch(currentMastery: number, moduleDifficulty: string): number {
    const difficultyMap: Record<string, number> = {
      INTRO: 0,
      INTERMEDIATE: 0.5,
      ADVANCED: 1,
    };
    const moduleLevel = difficultyMap[moduleDifficulty] || 0.5;
    const diff = Math.abs(currentMastery - moduleLevel);
    return Math.max(0, 1 - diff);
  }

  private estimateDuration(difficulty: string): number {
    const durationMap: Record<string, number> = {
      INTRO: 30,
      INTERMEDIATE: 60,
      ADVANCED: 90,
    };
    return durationMap[difficulty] || 60;
  }

  private async calculateAverageMastery(tenantId: string, userId: string): Promise<number> {
    const masteries = await this.prisma.learnerMastery.findMany({
      where: {
        tenantId,
        profile: { userId },
      },
      select: { masteryProbability: true },
    });

    if (masteries.length === 0) {
      return 0.5;
    }

    return (
      masteries.reduce((sum, mastery) => sum + mastery.masteryProbability, 0) /
      masteries.length
    );
  }
}
