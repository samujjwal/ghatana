/**
 * Teacher Review Workflow Service
 *
 * Manages teacher review workflow for AI-graded and flagged responses.
 *
 * @doc.type class
 * @doc.purpose Manage teacher review workflow for assessment grading
 * @doc.layer product
 * @doc.pattern Workflow Service
 */

import type { PrismaClient } from '@tutorputor/core/db';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'TeacherReviewService' });

export interface CreateReviewTaskInput {
  tenantId: string;
  assessmentId: string;
  attemptId: string;
  itemId: string;
  studentId: string;
  originalScore?: number;
  originalFeedback?: string;
  aiGradingResult?: {
    scorePercent: number;
    confidence: number;
    feedback: string;
  };
  priority?: 'low' | 'medium' | 'high';
  reason: string;
}

export interface ReviewTask {
  id: string;
  tenantId: string;
  assessmentId: string;
  attemptId: string;
  itemId: string;
  studentId: string;
  assignedTo?: string;
  status: 'pending' | 'assigned' | 'in_progress' | 'completed' | 'rejected';
  originalScore?: number;
  originalFeedback?: string;
  aiGradingResult?: {
    scorePercent: number;
    confidence: number;
    feedback: string;
  };
  reviewedScore?: number;
  reviewedFeedback?: string;
  priority: 'low' | 'medium' | 'high';
  reason: string;
  assignedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface SubmitReviewInput {
  taskId: string;
  teacherId: string;
  approved: boolean;
  reviewedScore?: number;
  reviewedFeedback?: string;
  comments?: string;
}

export class TeacherReviewService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Create a review task
   */
  async createReviewTask(input: CreateReviewTaskInput): Promise<ReviewTask> {
    logger.info({
      message: 'Creating review task',
      assessmentId: input.assessmentId,
      itemId: input.itemId,
      studentId: input.studentId,
      priority: input.priority,
    });

    // Create review task in database
    const task = await this.prisma.gradingReviewTask.create({
      data: {
        tenantId: input.tenantId,
        assessmentId: input.assessmentId,
        attemptId: input.attemptId,
        itemId: input.itemId,
        studentId: input.studentId,
        status: 'pending',
        originalScore: input.originalScore,
        originalFeedback: input.originalFeedback,
        aiGradingResult: input.aiGradingResult as unknown as Prisma.InputJsonValue,
        priority: input.priority ?? 'medium',
        reason: input.reason,
      },
    });

    return this.mapToReviewTask(task);
  }

  /**
   * Assign a review task to a teacher
   */
  async assignReviewTask(taskId: string, teacherId: string): Promise<ReviewTask> {
    logger.info({
      message: 'Assigning review task',
      taskId,
      teacherId,
    });

    const task = await this.prisma.gradingReviewTask.update({
      where: { id: taskId },
      data: {
        assignedTo: teacherId,
        status: 'assigned',
        assignedAt: new Date(),
      },
    });

    return this.mapToReviewTask(task);
  }

  /**
   * Get pending review tasks for a teacher
   */
  async getPendingTasks(tenantId: string, teacherId: string): Promise<ReviewTask[]> {
    const tasks = await this.prisma.gradingReviewTask.findMany({
      where: {
        tenantId,
        assignedTo: teacherId,
        status: { in: ['assigned', 'in_progress'] },
      },
      orderBy: [{ priority: 'desc' }, { createdAt: 'asc' }],
    });

    return tasks.map((t) => this.mapToReviewTask(t));
  }

  /**
   * Submit a review
   */
  async submitReview(input: SubmitReviewInput): Promise<ReviewTask> {
    logger.info({
      message: 'Submitting review',
      taskId: input.taskId,
      teacherId: input.teacherId,
      approved: input.approved,
    });

    const task = await this.prisma.gradingReviewTask.update({
      where: { id: input.taskId },
      data: {
        status: input.approved ? 'completed' : 'rejected',
        reviewedScore: input.reviewedScore,
        reviewedFeedback: input.reviewedFeedback,
        completedAt: new Date(),
      },
    });

    // If approved, update the assessment attempt with the reviewed score
    if (input.approved && input.reviewedScore !== undefined) {
      await this.updateAssessmentAttemptScore(task.attemptId, task.itemId, input.reviewedScore, input.reviewedFeedback);
    }

    return this.mapToReviewTask(task);
  }

  /**
   * Get review statistics
   */
  async getReviewStats(tenantId: string): Promise<{
    pending: number;
    inProgress: number;
    completed: number;
    avgCompletionTimeMs: number;
  }> {
    const tasks = await this.prisma.gradingReviewTask.findMany({
      where: { tenantId },
    });

    const pending = tasks.filter((t) => t.status === 'pending' || t.status === 'assigned').length;
    const inProgress = tasks.filter((t) => t.status === 'in_progress').length;
    const completed = tasks.filter((t) => t.status === 'completed').length;

    const completedWithTimes = tasks.filter(
      (t) => t.status === 'completed' && t.assignedAt && t.completedAt,
    );
    const avgCompletionTimeMs =
      completedWithTimes.length > 0
        ? completedWithTimes.reduce((sum, t) => {
            const assigned = new Date(t.assignedAt!).getTime();
            const completed = new Date(t.completedAt!).getTime();
            return sum + (completed - assigned);
          }, 0) / completedWithTimes.length
        : 0;

    return {
      pending,
      inProgress,
      completed,
      avgCompletionTimeMs,
    };
  }

  private async updateAssessmentAttemptScore(
    attemptId: string,
    itemId: string,
    score: number,
    feedback?: string,
  ): Promise<void> {
    // Get the current attempt
    const attempt = await this.prisma.assessmentAttempt.findUnique({
      where: { id: attemptId },
      include: { assessment: { include: { items: true } } },
    });

    if (!attempt) return;

    // Update the feedback for this item
    const feedbackArray = (attempt.feedback as Prisma.InputJsonValue) as Array<{ itemId: string; scorePercent: number; feedback?: string }>;
    const itemIndex = feedbackArray.findIndex((f) => f.itemId === itemId);

    if (itemIndex >= 0) {
      feedbackArray[itemIndex] = {
        itemId,
        scorePercent: score,
        feedback,
      };
    }

    // Recalculate total score
    const totalScore = feedbackArray.reduce((sum, f) => sum + f.scorePercent, 0) / feedbackArray.length;

    await this.prisma.assessmentAttempt.update({
      where: { id: attemptId },
      data: {
        feedback: feedbackArray as unknown as Prisma.InputJsonValue,
        scorePercent: totalScore,
      },
    });
  }

  private mapToReviewTask(task: any): ReviewTask {
    return {
      id: task.id,
      tenantId: task.tenantId,
      assessmentId: task.assessmentId,
      attemptId: task.attemptId,
      itemId: task.itemId,
      studentId: task.studentId,
      assignedTo: task.assignedTo ?? undefined,
      status: task.status,
      originalScore: task.originalScore ?? undefined,
      originalFeedback: task.originalFeedback ?? undefined,
      aiGradingResult: task.aiGradingResult as any,
      reviewedScore: task.reviewedScore ?? undefined,
      reviewedFeedback: task.reviewedFeedback ?? undefined,
      priority: task.priority,
      reason: task.reason,
      assignedAt: task.assignedAt?.toISOString() ?? undefined,
      completedAt: task.completedAt?.toISOString() ?? undefined,
      createdAt: task.createdAt.toISOString(),
    };
  }
}
