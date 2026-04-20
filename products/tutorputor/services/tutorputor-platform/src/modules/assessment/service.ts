/**
 * Assessment Service
 *
 * Main service for adaptive assessments integrating:
 * - Performance tracking
 * - Adaptive difficulty adjustment
 * - Question selection based on difficulty
 * - Session management
 *
 * @doc.type service
 * @doc.purpose Orchestrate adaptive assessment sessions
 * @doc.layer product
 * @doc.pattern Service
 */
import type { TutorPrismaClient } from "@tutorputor/core/db";
import { PerformanceTracker } from "./performance-tracker";
import { AdaptiveDifficultyEngine } from "./adaptive-engine";
import type { DifficultyAdjustment } from "./adaptive-engine";

export interface AssessmentSession {
  sessionId: string;
  userId: string;
  tenantId: string;
  currentQuestionIndex: number;
  currentDifficulty: number;
  questions: string[];
  responses: Array<{
    questionId: string;
    correct: boolean;
    timeSpentSeconds: number;
    difficultyAtStart: number;
  }>;
  startTime: Date;
  status: "active" | "paused" | "completed";
}

export interface QuestionSelectionCriteria {
  topicId?: string;
  difficultyRange: {
    min: number;
    max: number;
  };
  excludeIds: string[];
  limit: number;
}

export interface AssessmentResult {
  sessionId: string;
  totalQuestions: number;
  correctCount: number;
  accuracyRate: number;
  averageTimeSeconds: number;
  difficultyProgression: number[];
  finalDifficulty: number;
  weakTopics: string[];
  strongTopics: string[];
  completedAt: Date;
}

export class AssessmentService {
  private performanceTracker: PerformanceTracker;
  private adaptiveEngine: AdaptiveDifficultyEngine;
  private activeSessions: Map<string, AssessmentSession> = new Map();

  constructor(private readonly prisma: TutorPrismaClient) {
    this.performanceTracker = new PerformanceTracker(prisma);
    this.adaptiveEngine = new AdaptiveDifficultyEngine(prisma);
  }

  /**
   * Start a new adaptive assessment session
   */
  async startSession(
    tenantId: string,
    userId: string,
    options: {
      topicId?: string;
      initialDifficulty?: number;
      maxQuestions?: number;
    } = {},
  ): Promise<AssessmentSession> {
    const sessionId = `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

    // Determine initial difficulty
    let initialDifficulty = options.initialDifficulty ?? 5;

    if (!options.initialDifficulty) {
      // Check user's past performance to set appropriate starting difficulty
      const metrics = await this.performanceTracker.getPerformanceMetrics(tenantId, userId);
      if (metrics.totalQuestions > 0) {
        // Start slightly below their demonstrated level
        initialDifficulty = Math.max(1, Math.min(10, Math.floor(metrics.accuracyRate * 10) - 1));
      }
    }

    // Get initial questions
    const questions = await this.selectQuestions(tenantId, {
      difficultyRange: { min: initialDifficulty - 0.5, max: initialDifficulty + 0.5 },
      excludeIds: [],
      limit: options.maxQuestions ?? 20,
      ...(options.topicId ? { topicId: options.topicId } : {}),
    });

    const session: AssessmentSession = {
      sessionId,
      userId,
      tenantId,
      currentQuestionIndex: 0,
      currentDifficulty: initialDifficulty,
      questions: questions.map((q) => q.id),
      responses: [],
      startTime: new Date(),
      status: "active",
    };

    this.activeSessions.set(sessionId, session);

    return session;
  }

  /**
   * Submit answer and get next question with adaptive difficulty
   */
  async submitAnswer(
    sessionId: string,
    questionId: string,
    correct: boolean,
    timeSpentSeconds: number,
  ): Promise<{
    session: AssessmentSession;
    nextQuestion?: { id: string; difficulty: number };
    adjustment: DifficultyAdjustment;
    isComplete: boolean;
  }> {
    const session = this.activeSessions.get(sessionId);
    if (!session || session.status !== "active") {
      throw new Error("Session not found or not active");
    }

    // Record the response
    session.responses.push({
      questionId,
      correct,
      timeSpentSeconds,
      difficultyAtStart: session.currentDifficulty,
    });

    // Record performance for tracking
    await this.performanceTracker.recordAttempt(session.tenantId, session.userId, {
      questionId,
      correct,
      timeSpentSeconds,
      difficultyLevel: session.currentDifficulty,
      hintUsed: false,
    });

    // Calculate next difficulty
    const adjustment = await this.adaptiveEngine.calculateNextDifficulty(
      session.tenantId,
      session.userId,
      session.currentDifficulty,
    );

    session.currentDifficulty = adjustment.newDifficulty;
    session.currentQuestionIndex++;

    // Check if assessment is complete
    const isComplete = session.currentQuestionIndex >= session.questions.length;

    if (isComplete) {
      session.status = "completed";
      return {
        session,
        adjustment,
        isComplete: true,
      };
    }

    // Get next question at adjusted difficulty
    const nextQuestions = await this.selectQuestions(session.tenantId, {
      difficultyRange: { min: adjustment.newDifficulty - 0.5, max: adjustment.newDifficulty + 0.5 },
      excludeIds: session.questions.slice(0, session.currentQuestionIndex + 1),
      limit: 1,
    });

    const nextQuestion = nextQuestions[0];

    return {
      session,
      ...(nextQuestion
        ? {
            nextQuestion: {
              id: nextQuestion.id,
              difficulty: adjustment.newDifficulty,
            },
          }
        : {}),
      adjustment,
      isComplete: false,
    };
  }

  /**
   * Get session results
   */
  async getResults(sessionId: string): Promise<AssessmentResult> {
    const session = this.activeSessions.get(sessionId);
    if (!session) {
      throw new Error("Session not found");
    }

    const correctCount = session.responses.filter((r) => r.correct).length;
    const totalTime = session.responses.reduce((sum, r) => sum + r.timeSpentSeconds, 0);

    // Identify weak and strong topics
    const metrics = await this.performanceTracker.getPerformanceMetrics(
      session.tenantId,
      session.userId,
    );

    const weakTopics: string[] = [];
    const strongTopics: string[] = [];

    for (const [topicId, perf] of metrics.topicPerformance) {
      if (perf.accuracyRate < 0.6) {
        weakTopics.push(topicId);
      } else if (perf.accuracyRate > 0.85) {
        strongTopics.push(topicId);
      }
    }

    return {
      sessionId,
      totalQuestions: session.responses.length,
      correctCount,
      accuracyRate: correctCount / session.responses.length,
      averageTimeSeconds: totalTime / session.responses.length,
      difficultyProgression: session.responses.map((r) => r.difficultyAtStart),
      finalDifficulty: session.currentDifficulty,
      weakTopics,
      strongTopics,
      completedAt: new Date(),
    };
  }

  /**
   * Pause an active session
   */
  pauseSession(sessionId: string): AssessmentSession {
    const session = this.activeSessions.get(sessionId);
    if (!session) {
      throw new Error("Session not found");
    }

    session.status = "paused";
    return session;
  }

  /**
   * Resume a paused session
   */
  resumeSession(sessionId: string): AssessmentSession {
    const session = this.activeSessions.get(sessionId);
    if (!session) {
      throw new Error("Session not found");
    }

    session.status = "active";
    return session;
  }

  /**
   * End and clean up a session
   */
  endSession(sessionId: string): void {
    this.activeSessions.delete(sessionId);
  }

  /**
   * Select questions based on criteria
   */
  private async selectQuestions(
    tenantId: string,
    criteria: QuestionSelectionCriteria,
  ): Promise<Array<{ id: string; difficulty: number }>> {
    // Query questions matching criteria
    // This is a simplified implementation - real implementation would query Question model
    const questions = await this.prisma.$queryRaw<Array<{ id: string; difficultyLevel: number }>>`
      SELECT id, "difficultyLevel"
      FROM "Question"
      WHERE "tenantId" = ${tenantId}
        AND "difficultyLevel" >= ${criteria.difficultyRange.min}
        AND "difficultyLevel" <= ${criteria.difficultyRange.max}
        ${criteria.topicId ? `AND "topicId" = ${criteria.topicId}` : ""}
        AND id NOT IN (${criteria.excludeIds.length > 0 ? criteria.excludeIds.join(",") : "''"})
      ORDER BY RANDOM()
      LIMIT ${criteria.limit}
    `.catch(() => []);

    return questions.map((q) => ({
      id: q.id,
      difficulty: q.difficultyLevel,
    }));
  }

  /**
   * Get user's assessment history
   */
  async getAssessmentHistory(
    tenantId: string,
    userId: string,
    limit: number = 10,
  ): Promise<Array<{
    sessionId: string;
    completedAt: Date;
    accuracyRate: number;
    averageDifficulty: number;
  }>> {
    // This would query completed sessions from database
    // For now, return empty array as sessions are in-memory
    return [];
  }
}
