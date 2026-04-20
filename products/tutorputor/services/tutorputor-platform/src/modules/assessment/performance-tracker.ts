/**
 * Performance Tracker Service
 *
 * Tracks learner performance metrics for adaptive assessments including:
 * - Accuracy per question and topic
 * - Time spent per question
 * - Streak tracking (consecutive correct/incorrect)
 * - Topic mastery levels
 * - Performance trends over time
 *
 * @doc.type service
 * @doc.purpose Track and analyze learner performance for adaptive difficulty
 * @doc.layer product
 * @doc.pattern Service
 */
import type { TutorPrismaClient } from "@tutorputor/core/db";

type StoredQuestionAttempt = {
  tenantId: string;
  userId: string;
  questionId: string;
  correct: boolean;
  timeSpentSeconds: number;
  topicId: string | null;
  difficultyLevel: number;
  hintUsed: boolean;
  attemptedAt: Date;
};

type QuestionAttemptDelegate = {
  create(args: { data: Record<string, unknown> }): Promise<StoredQuestionAttempt>;
  findMany(args: {
    where: Record<string, unknown>;
    orderBy: { attemptedAt: 'asc' | 'desc' };
    take: number;
  }): Promise<StoredQuestionAttempt[]>;
};

export interface QuestionAttempt {
  questionId: string;
  correct: boolean;
  timeSpentSeconds: number;
  topicId?: string;
  difficultyLevel: number;
  hintUsed: boolean;
}

export interface PerformanceMetrics {
  totalQuestions: number;
  correctCount: number;
  accuracyRate: number;
  averageTimeSeconds: number;
  currentStreak: number;
  longestStreak: number;
  topicPerformance: Map<string, TopicPerformance>;
  trendDirection: "improving" | "stable" | "declining";
}

export interface TopicPerformance {
  topicId: string;
  totalQuestions: number;
  correctCount: number;
  accuracyRate: number;
  averageTimeSeconds: number;
  masteryLevel: "beginner" | "developing" | "proficient" | "mastered";
}

export interface StreakAnalysis {
  currentStreak: number;
  streakType: "correct" | "incorrect" | "none";
  longestCorrectStreak: number;
  longestIncorrectStreak: number;
  recentAttempts: Array<{
    correct: boolean;
    timestamp: Date;
  }>;
}

export class PerformanceTracker {
  constructor(private readonly prisma: TutorPrismaClient) {}

  private getQuestionAttemptDelegate(): QuestionAttemptDelegate {
    const prismaWithDelegate = this.prisma as TutorPrismaClient & {
      questionAttempt?: QuestionAttemptDelegate;
    };

    if (!prismaWithDelegate.questionAttempt) {
      throw new Error('questionAttempt delegate is unavailable. Regenerate Prisma client or align PerformanceTracker with the current assessment attempt schema.');
    }

    return prismaWithDelegate.questionAttempt;
  }

  /**
   * Record a question attempt and update performance metrics
   */
  async recordAttempt(
    tenantId: string,
    userId: string,
    attempt: QuestionAttempt,
  ): Promise<void> {
    await this.getQuestionAttemptDelegate().create({
      data: {
        tenantId,
        userId,
        questionId: attempt.questionId,
        correct: attempt.correct,
        timeSpentSeconds: attempt.timeSpentSeconds,
        topicId: attempt.topicId,
        difficultyLevel: attempt.difficultyLevel,
        hintUsed: attempt.hintUsed,
        attemptedAt: new Date(),
      },
    });

    // Update cached performance summary
    await this.updatePerformanceSummary(tenantId, userId);
  }

  /**
   * Get comprehensive performance metrics for a user
   */
  async getPerformanceMetrics(
    tenantId: string,
    userId: string,
  ): Promise<PerformanceMetrics> {
    const attempts = await this.getQuestionAttemptDelegate().findMany({
      where: { tenantId, userId },
      orderBy: { attemptedAt: "desc" },
      take: 100, // Recent 100 attempts
    });

    if (attempts.length === 0) {
      return this.getDefaultMetrics();
    }

    const correctCount = attempts.filter((attemptRecord) => attemptRecord.correct).length;
    const totalTime = attempts.reduce(
      (sum: number, attemptRecord) => sum + attemptRecord.timeSpentSeconds,
      0,
    );
    const streaks = this.calculateStreaks(attempts);

    // Calculate topic performance
    const topicMap = new Map<string, StoredQuestionAttempt[]>();
    for (const attempt of attempts) {
      if (attempt.topicId) {
        const list = topicMap.get(attempt.topicId) ?? [];
        list.push(attempt);
        topicMap.set(attempt.topicId, list);
      }
    }

    const topicPerformance = new Map<string, TopicPerformance>();
    for (const [topicId, topicAttempts] of topicMap) {
      const topicCorrect = topicAttempts.filter((a) => a.correct).length;
      const topicTime = topicAttempts.reduce((sum, a) => sum + a.timeSpentSeconds, 0);
      const accuracy = topicCorrect / topicAttempts.length;

      topicPerformance.set(topicId, {
        topicId,
        totalQuestions: topicAttempts.length,
        correctCount: topicCorrect,
        accuracyRate: accuracy,
        averageTimeSeconds: topicTime / topicAttempts.length,
        masteryLevel: this.calculateMasteryLevel(accuracy, topicAttempts.length),
      });
    }

    // Calculate trend
    const trendDirection = this.calculateTrend(attempts);

    return {
      totalQuestions: attempts.length,
      correctCount,
      accuracyRate: correctCount / attempts.length,
      averageTimeSeconds: totalTime / attempts.length,
      currentStreak: streaks.currentStreak,
      longestStreak: streaks.longestCorrectStreak,
      topicPerformance,
      trendDirection,
    };
  }

  /**
   * Get streak analysis for adaptive difficulty
   */
  async getStreakAnalysis(
    tenantId: string,
    userId: string,
  ): Promise<StreakAnalysis> {
    const attempts = await this.getQuestionAttemptDelegate().findMany({
      where: { tenantId, userId },
      orderBy: { attemptedAt: "desc" },
      take: 20, // Recent 20 for streak analysis
    });

    const streaks = this.calculateStreaks(attempts);

    return {
      currentStreak: streaks.currentStreak,
      streakType: streaks.streakType,
      longestCorrectStreak: streaks.longestCorrectStreak,
      longestIncorrectStreak: streaks.longestIncorrectStreak,
      recentAttempts: attempts.slice(0, 5).map((attemptRecord) => ({
        correct: attemptRecord.correct,
        timestamp: attemptRecord.attemptedAt,
      })),
    };
  }

  /**
   * Get recommended difficulty level based on performance
   */
  async getRecommendedDifficulty(
    tenantId: string,
    userId: string,
    currentDifficulty: number,
  ): Promise<{ newDifficulty: number; reason: string }> {
    const streaks = await this.getStreakAnalysis(tenantId, userId);
    const metrics = await this.getPerformanceMetrics(tenantId, userId);

    // Too many consecutive wrong answers - decrease difficulty
    if (streaks.streakType === "incorrect" && streaks.currentStreak >= 3) {
      const newDifficulty = Math.max(1, currentDifficulty - 1);
      return {
        newDifficulty,
        reason: `Decreasing difficulty due to ${streaks.currentStreak} incorrect answers in a row`,
      };
    }

    // Strong performance streak - increase difficulty
    if (streaks.streakType === "correct" && streaks.currentStreak >= 3) {
      const newDifficulty = Math.min(10, currentDifficulty + 1);
      return {
        newDifficulty,
        reason: `Increasing difficulty due to ${streaks.currentStreak} correct answers in a row`,
      };
    }

    // High accuracy overall - gradually increase
    if (metrics.accuracyRate > 0.8 && metrics.totalQuestions >= 10) {
      const newDifficulty = Math.min(10, currentDifficulty + 0.5);
      return {
        newDifficulty,
        reason: "High accuracy rate suggests readiness for harder questions",
      };
    }

    // Low accuracy - decrease difficulty
    if (metrics.accuracyRate < 0.4 && metrics.totalQuestions >= 5) {
      const newDifficulty = Math.max(1, currentDifficulty - 1);
      return {
        newDifficulty,
        reason: "Low accuracy rate suggests need for easier questions",
      };
    }

    // Stable - maintain current
    return {
      newDifficulty: currentDifficulty,
      reason: "Performance is stable, maintaining current difficulty",
    };
  }

  /**
   * Get topic recommendations based on weak areas
   */
  async getWeakAreas(
    tenantId: string,
    userId: string,
  ): Promise<Array<{ topicId: string; accuracyRate: number; priority: number }>> {
    const metrics = await this.getPerformanceMetrics(tenantId, userId);

    const weakAreas: Array<{ topicId: string; accuracyRate: number; priority: number }> = [];

    for (const topicPerf of metrics.topicPerformance.values()) {
      if (topicPerf.accuracyRate < 0.6 && topicPerf.totalQuestions >= 3) {
        weakAreas.push({
          topicId: topicPerf.topicId,
          accuracyRate: topicPerf.accuracyRate,
          priority: (1 - topicPerf.accuracyRate) * topicPerf.totalQuestions,
        });
      }
    }

    // Sort by priority (highest first)
    return weakAreas.sort((a, b) => b.priority - a.priority);
  }

  /**
   * Calculate streaks from attempt history
   */
  private calculateStreaks(
    attempts: Array<{ correct: boolean }>,
  ): {
    currentStreak: number;
    streakType: "correct" | "incorrect" | "none";
    longestCorrectStreak: number;
    longestIncorrectStreak: number;
  } {
    if (attempts.length === 0) {
      return {
        currentStreak: 0,
        streakType: "none",
        longestCorrectStreak: 0,
        longestIncorrectStreak: 0,
      };
    }

    // Reverse to get chronological order
    const chronological = [...attempts].reverse();

    // Calculate current streak
    let currentStreak = 0;
    const currentType = chronological[0]?.correct ?? false;
    for (const attempt of chronological) {
      if (attempt.correct === currentType) {
        currentStreak++;
      } else {
        break;
      }
    }

    // Calculate longest streaks
    let longestCorrect = 0;
    let longestIncorrect = 0;
    let currentCorrect = 0;
    let currentIncorrect = 0;

    for (const attempt of chronological) {
      if (attempt.correct) {
        currentCorrect++;
        currentIncorrect = 0;
        longestCorrect = Math.max(longestCorrect, currentCorrect);
      } else {
        currentIncorrect++;
        currentCorrect = 0;
        longestIncorrect = Math.max(longestIncorrect, currentIncorrect);
      }
    }

    return {
      currentStreak,
      streakType: currentStreak === 0 ? "none" : currentType ? "correct" : "incorrect",
      longestCorrectStreak: longestCorrect,
      longestIncorrectStreak: longestIncorrect,
    };
  }

  /**
   * Calculate mastery level based on accuracy and question count
   */
  private calculateMasteryLevel(
    accuracy: number,
    questionCount: number,
  ): "beginner" | "developing" | "proficient" | "mastered" {
    if (questionCount < 5 || accuracy < 0.4) {
      return "beginner";
    }
    if (accuracy < 0.6) {
      return "developing";
    }
    if (accuracy < 0.85) {
      return "proficient";
    }
    return "mastered";
  }

  /**
   * Calculate performance trend
   */
  private calculateTrend(
    attempts: Array<{ correct: boolean; attemptedAt: Date }>,
  ): "improving" | "stable" | "declining" {
    if (attempts.length < 10) {
      return "stable";
    }

    const chronological = [...attempts].reverse();
    const half = Math.floor(chronological.length / 2);

    const firstHalf = chronological.slice(0, half);
    const secondHalf = chronological.slice(half);

    const firstAccuracy =
      firstHalf.filter((a) => a.correct).length / firstHalf.length;
    const secondAccuracy =
      secondHalf.filter((a) => a.correct).length / secondHalf.length;

    const diff = secondAccuracy - firstAccuracy;

    if (diff > 0.1) return "improving";
    if (diff < -0.1) return "declining";
    return "stable";
  }

  /**
   * Get default metrics for new users
   */
  private getDefaultMetrics(): PerformanceMetrics {
    return {
      totalQuestions: 0,
      correctCount: 0,
      accuracyRate: 0,
      averageTimeSeconds: 0,
      currentStreak: 0,
      longestStreak: 0,
      topicPerformance: new Map(),
      trendDirection: "stable",
    };
  }

  /**
   * Update cached performance summary
   */
  private async updatePerformanceSummary(
    tenantId: string,
    userId: string,
  ): Promise<void> {
    // This could update a materialized view or cache
    // For now, we'll just ensure the data is available
    const summary = await this.getPerformanceMetrics(tenantId, userId);

    // Could emit event for real-time updates
    this.emitPerformanceUpdate(tenantId, userId, summary);
  }

  /**
   * Emit performance update event
   */
  private emitPerformanceUpdate(
    tenantId: string,
    userId: string,
    metrics: PerformanceMetrics,
  ): void {
    // Placeholder for event emission
    // Could integrate with notification service
    console.log(`Performance update for ${userId}: ${metrics.accuracyRate * 100}% accuracy`);
  }
}
