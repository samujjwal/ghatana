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
      
      // Additional factors for deterministic scoring
      const isNewerContent = (completedModules.length === 0); // Boost newer content for beginners
      const difficultyBonus = module.difficulty === 'INTRO' && currentMastery < 0.3 ? 0.1 : 0;
      const progressionBonus = completedModules.length > 0 ? Math.min(0.1, completedModules.length * 0.02) : 0;
      
      // Deterministic probability calculation without random noise
      const probability = Math.min(0.95, difficultyMatch * 0.7 + difficultyBonus + progressionBonus + (isNewerContent ? 0.05 : 0));
      
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

    try {
      // Get learner mastery data for the tenant/classroom
      // Note: classroomId filtering not implemented as LearnerProfile doesn't have classroomId
      // In production, this would require a ClassroomMembership or similar relation
      const masteryData = await this.prisma.learnerMastery.findMany({
        where: {
          tenantId,
        },
        select: {
          profileId: true,
          conceptId: true,
          masteryProbability: true,
        },
      });

      if (masteryData.length === 0) {
        return [];
      }

      // Group mastery data by concept
      const conceptMastery = new Map<string, { masteryLevels: number[]; profileIds: Set<string> }>();
      
      for (const mastery of masteryData) {
        if (!conceptMastery.has(mastery.conceptId)) {
          conceptMastery.set(mastery.conceptId, {
            masteryLevels: [],
            profileIds: new Set(),
          });
        }
        const data = conceptMastery.get(mastery.conceptId)!;
        data.masteryLevels.push(mastery.masteryProbability);
        data.profileIds.add(mastery.profileId);
      }

      // Calculate gaps for each concept
      const gaps: Array<{
        conceptId: string;
        conceptName: string;
        gapSeverity: 'low' | 'medium' | 'high';
        affectedStudents: number;
        recommendedContent: string[];
      }> = [];

      for (const [conceptId, data] of conceptMastery) {
        const avgMastery = data.masteryLevels.reduce((sum, level) => sum + level, 0) / data.masteryLevels.length;
        const affectedStudents = data.profileIds.size;
        
        // Determine gap severity based on average mastery
        let gapSeverity: 'low' | 'medium' | 'high';
        if (avgMastery < 0.3) {
          gapSeverity = 'high';
        } else if (avgMastery < 0.6) {
          gapSeverity = 'medium';
        } else {
          gapSeverity = 'low';
        }

        // Only include concepts with significant gaps
        if (gapSeverity !== 'low' && affectedStudents > 0) {
          // Try to get concept name from modules or learning objectives
          let conceptName = conceptId;
          
          // Try to find a module or learning objective with this concept
          const module = await this.prisma.module.findFirst({
            where: {
              tenantId,
              OR: [
                { description: { contains: conceptId, mode: 'insensitive' } },
                { title: { contains: conceptId, mode: 'insensitive' } },
              ],
            },
            select: { title: true },
          });

          if (module) {
            conceptName = module.title;
          }

          // Generate content recommendations based on gap severity
          const recommendedContent: string[] = [];
          if (gapSeverity === 'high') {
            recommendedContent.push(`${conceptName} fundamentals module`);
            recommendedContent.push(`${conceptName} practice problems`);
            recommendedContent.push(`${conceptName} interactive simulation`);
          } else if (gapSeverity === 'medium') {
            recommendedContent.push(`${conceptName} reinforcement exercises`);
            recommendedContent.push(`${conceptName} review module`);
          }

          gaps.push({
            conceptId,
            conceptName,
            gapSeverity,
            affectedStudents,
            recommendedContent,
          });
        }
      }

      // Sort by severity and number of affected students
      gaps.sort((a, b) => {
        const severityOrder = { high: 3, medium: 2, low: 1 };
        const severityDiff = severityOrder[b.gapSeverity] - severityOrder[a.gapSeverity];
        if (severityDiff !== 0) return severityDiff;
        return b.affectedStudents - a.affectedStudents;
      });

      return gaps.slice(0, 10); // Return top 10 gaps
    } catch (error) {
      logger.error({ error }, 'Failed to analyze content gaps');
      return [];
    }
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
