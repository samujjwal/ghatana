/**
 * Adaptive Difficulty Engine
 *
 * Dynamically adjusts question difficulty based on learner performance using:
 * - Streak-based adjustments (consecutive correct/incorrect)
 * - Accuracy thresholds for gradual difficulty changes
 * - Topic-specific mastery tracking
 * - Personalized pacing based on time spent
 *
 * @doc.type service
 * @doc.purpose Adjust assessment difficulty based on performance
 * @doc.layer product
 * @doc.pattern Service
 */
import type { TutorPrismaClient } from "@tutorputor/core/db";

export interface DifficultyAdjustment {
  currentDifficulty: number;
  newDifficulty: number;
  adjustmentType: "increase" | "decrease" | "maintain";
  reason: string;
  confidence: number;
}

export interface AdaptiveConfig {
  minDifficulty: number;
  maxDifficulty: number;
  streakThreshold: number;
  accuracyIncreaseThreshold: number;
  accuracyDecreaseThreshold: number;
  gradualStepSize: number;
  aggressiveStepSize: number;
  timeMultiplier: number;
}

export interface PerformanceSnapshot {
  recentAccuracy: number;
  currentStreak: number;
  streakType: "correct" | "incorrect" | "none";
  averageTimeSeconds: number;
  topicMastery: Map<string, number>;
  totalQuestionsInSession: number;
}

export const DEFAULT_ADAPTIVE_CONFIG: AdaptiveConfig = {
  minDifficulty: 1,
  maxDifficulty: 10,
  streakThreshold: 3,
  accuracyIncreaseThreshold: 0.8,
  accuracyDecreaseThreshold: 0.4,
  gradualStepSize: 0.5,
  aggressiveStepSize: 1.5,
  timeMultiplier: 1.2,
};

export class AdaptiveDifficultyEngine {
  private config: AdaptiveConfig;

  constructor(
    private readonly prisma: TutorPrismaClient,
    config?: Partial<AdaptiveConfig>,
  ) {
    this.config = { ...DEFAULT_ADAPTIVE_CONFIG, ...config };
  }

  /**
   * Calculate next difficulty level based on performance
   */
  async calculateNextDifficulty(
    tenantId: string,
    userId: string,
    currentDifficulty: number,
    topicId?: string,
  ): Promise<DifficultyAdjustment> {
    const snapshot = await this.getPerformanceSnapshot(tenantId, userId, topicId);

    // Check for strong streak patterns first
    if (snapshot.streakType === "correct" && snapshot.currentStreak >= this.config.streakThreshold) {
      return this.createAdjustment(
        currentDifficulty,
        "increase",
        `Increasing after ${snapshot.currentStreak} correct answers in a row`,
        0.9,
      );
    }

    if (snapshot.streakType === "incorrect" && snapshot.currentStreak >= this.config.streakThreshold) {
      return this.createAdjustment(
        currentDifficulty,
        "decrease",
        `Decreasing after ${snapshot.currentStreak} incorrect answers in a row`,
        0.9,
      );
    }

    // Check accuracy-based adjustments
    if (snapshot.recentAccuracy >= this.config.accuracyIncreaseThreshold && 
        snapshot.totalQuestionsInSession >= 5) {
      return this.createAdjustment(
        currentDifficulty,
        "increase",
        "High accuracy indicates readiness for harder questions",
        0.7,
      );
    }

    if (snapshot.recentAccuracy <= this.config.accuracyDecreaseThreshold && 
        snapshot.totalQuestionsInSession >= 3) {
      return this.createAdjustment(
        currentDifficulty,
        "decrease",
        "Low accuracy suggests easier questions needed",
        0.8,
      );
    }

    // Check topic mastery for targeted adjustments
    if (topicId && snapshot.topicMastery.has(topicId)) {
      const mastery = snapshot.topicMastery.get(topicId)!;
      if (mastery > 0.9) {
        return this.createAdjustment(
          currentDifficulty,
          "increase",
          "Topic mastery achieved - advancing difficulty",
          0.85,
        );
      }
    }

    // Time-based adjustment (too fast = increase, too slow = decrease)
    if (snapshot.averageTimeSeconds < 10 && snapshot.recentAccuracy > 0.7) {
      return this.createAdjustment(
        currentDifficulty,
        "increase",
        "Fast and accurate responses - ready for challenge",
        0.6,
      );
    }

    // No adjustment needed
    return {
      currentDifficulty,
      newDifficulty: currentDifficulty,
      adjustmentType: "maintain",
      reason: "Performance is stable - maintaining current difficulty",
      confidence: 0.5,
    };
  }

  /**
   * Get performance snapshot for current session
   */
  private async getPerformanceSnapshot(
    tenantId: string,
    userId: string,
    topicId?: string,
  ): Promise<PerformanceSnapshot> {
    // Get recent attempts (last session or last 20 attempts)
    const recentAttempts = await this.prisma.$queryRaw<Array<{
      correct: boolean;
      timeSpentSeconds: number;
      attemptedAt: Date;
      topicId: string | null;
    }>>`
      SELECT correct, "timeSpentSeconds", "attemptedAt", "topicId"
      FROM "QuestionAttempt"
      WHERE "tenantId" = ${tenantId}
        AND "userId" = ${userId}
        ${topicId ? `
        AND "topicId" = ${topicId}` : ""}
      ORDER BY "attemptedAt" DESC
      LIMIT 20
    `.catch(() => []);

    if (recentAttempts.length === 0) {
      return {
        recentAccuracy: 0.5,
        currentStreak: 0,
        streakType: "none",
        averageTimeSeconds: 30,
        topicMastery: new Map(),
        totalQuestionsInSession: 0,
      };
    }

    // Calculate accuracy
    const correctCount = recentAttempts.filter((a) => a.correct).length;
    const recentAccuracy = correctCount / recentAttempts.length;

    // Calculate streak
    const streaks = this.calculateStreaks(recentAttempts.map((a) => a.correct));

    // Calculate average time
    const totalTime = recentAttempts.reduce((sum, a) => sum + a.timeSpentSeconds, 0);
    const averageTimeSeconds = totalTime / recentAttempts.length;

    // Calculate topic mastery
    const topicMastery = this.calculateTopicMastery(recentAttempts);

    return {
      recentAccuracy,
      currentStreak: streaks.currentStreak,
      streakType: streaks.streakType,
      averageTimeSeconds,
      topicMastery,
      totalQuestionsInSession: recentAttempts.length,
    };
  }

  /**
   * Create difficulty adjustment with proper bounds
   */
  private createAdjustment(
    currentDifficulty: number,
    adjustmentType: "increase" | "decrease" | "maintain",
    reason: string,
    confidence: number,
  ): DifficultyAdjustment {
    let newDifficulty = currentDifficulty;

    if (adjustmentType === "increase") {
      const step = confidence > 0.8 ? this.config.aggressiveStepSize : this.config.gradualStepSize;
      newDifficulty = Math.min(this.config.maxDifficulty, currentDifficulty + step);
    } else if (adjustmentType === "decrease") {
      const step = confidence > 0.8 ? this.config.aggressiveStepSize : this.config.gradualStepSize;
      newDifficulty = Math.max(this.config.minDifficulty, currentDifficulty - step);
    }

    return {
      currentDifficulty,
      newDifficulty: Math.round(newDifficulty * 2) / 2, // Round to nearest 0.5
      adjustmentType,
      reason,
      confidence,
    };
  }

  /**
   * Calculate streak information
   */
  private calculateStreaks(
    attempts: boolean[],
  ): {
    currentStreak: number;
    streakType: "correct" | "incorrect" | "none";
  } {
    if (attempts.length === 0) {
      return { currentStreak: 0, streakType: "none" };
    }

    let currentStreak = 0;
    const currentType = attempts[0];

    for (const attempt of attempts) {
      if (attempt === currentType) {
        currentStreak++;
      } else {
        break;
      }
    }

    return {
      currentStreak,
      streakType: currentStreak === 0 ? "none" : currentType ? "correct" : "incorrect",
    };
  }

  /**
   * Calculate mastery level per topic
   */
  private calculateTopicMastery(
    attempts: Array<{ correct: boolean; topicId: string | null }>,
  ): Map<string, number> {
    const topicMap = new Map<string, Array<boolean>>();

    for (const attempt of attempts) {
      if (attempt.topicId) {
        const list = topicMap.get(attempt.topicId) ?? [];
        list.push(attempt.correct);
        topicMap.set(attempt.topicId, list);
      }
    }

    const masteryMap = new Map<string, number>();
    for (const [topicId, results] of topicMap) {
      const correct = results.filter((r) => r).length;
      masteryMap.set(topicId, correct / results.length);
    }

    return masteryMap;
  }

  /**
   * Get difficulty description for display
   */
  getDifficultyDescription(level: number): string {
    if (level <= 2) return "Beginner";
    if (level <= 4) return "Easy";
    if (level <= 6) return "Intermediate";
    if (level <= 8) return "Advanced";
    return "Expert";
  }

  /**
   * Update engine configuration
   */
  updateConfig(newConfig: Partial<AdaptiveConfig>): void {
    this.config = { ...this.config, ...newConfig };
  }

  /**
   * Get current configuration
   */
  getConfig(): AdaptiveConfig {
    return { ...this.config };
  }
}
