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

import type { Prisma, PrismaClient } from '@tutorputor/core/db';
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

type StoredReviewTask = {
  id: string;
  tenantId: string;
  assessmentId: string;
  attemptId: string;
  itemId: string;
  studentId: string;
  assignedTo: string | null;
  status: ReviewTask['status'];
  originalScore: number | null;
  originalFeedback: string | null;
  aiGradingResult: Prisma.JsonValue | null;
  reviewedScore: number | null;
  reviewedFeedback: string | null;
  priority: ReviewTask['priority'];
  reason: string;
  assignedAt: Date | null;
  completedAt: Date | null;
  createdAt: Date;
};

type ReviewTaskDelegate = {
  create(args: { data: Record<string, unknown> }): Promise<StoredReviewTask>;
  update(args: { where: { id: string }; data: Record<string, unknown> }): Promise<StoredReviewTask>;
  findMany(args: {
    where: Record<string, unknown>;
    orderBy?: Array<Record<string, 'asc' | 'desc'>>;
  }): Promise<StoredReviewTask[]>;
};

type AttemptFeedbackItem = {
  itemId: string;
  scorePercent: number;
  feedback?: string;
};

type AIGradingResult = NonNullable<ReviewTask['aiGradingResult']>;

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
    const task = await this.getReviewTaskDelegate().create({
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

    const task = await this.getReviewTaskDelegate().update({
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
    const tasks = await this.getReviewTaskDelegate().findMany({
      where: {
        tenantId,
        assignedTo: teacherId,
        status: { in: ['assigned', 'in_progress'] },
      },
      orderBy: [{ priority: 'desc' }, { createdAt: 'asc' }],
    });

    return tasks.map((task) => this.mapToReviewTask(task));
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

    const task = await this.getReviewTaskDelegate().update({
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
    const tasks = await this.getReviewTaskDelegate().findMany({
      where: { tenantId },
    });

    const pending = tasks.filter((task) => task.status === 'pending' || task.status === 'assigned').length;
    const inProgress = tasks.filter((task) => task.status === 'in_progress').length;
    const completed = tasks.filter((task) => task.status === 'completed').length;

    const completedWithTimes = tasks.filter(
      (task) => task.status === 'completed' && task.assignedAt && task.completedAt,
    );
    const avgCompletionTimeMs =
      completedWithTimes.length > 0
        ? completedWithTimes.reduce((sum: number, task) => {
            const assigned = task.assignedAt?.getTime() ?? 0;
            const completedAt = task.completedAt?.getTime() ?? 0;
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
    const feedbackArray = this.parseFeedbackArray(attempt.feedback);
    const itemIndex = feedbackArray.findIndex((f) => f.itemId === itemId);

    if (itemIndex >= 0) {
      const updatedItem: AttemptFeedbackItem = {
        itemId,
        scorePercent: score,
      };

      if (feedback) {
        updatedItem.feedback = feedback;
      }

      feedbackArray[itemIndex] = updatedItem;
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

  private parseFeedbackArray(feedback: Prisma.JsonValue | null): AttemptFeedbackItem[] {
    if (!Array.isArray(feedback)) {
      return [];
    }

    return feedback.flatMap((entry): AttemptFeedbackItem[] => {
      if (!entry || typeof entry !== 'object') {
        return [];
      }

      const itemId = 'itemId' in entry && typeof entry.itemId === 'string' ? entry.itemId : null;
      const scorePercent =
        'scorePercent' in entry && typeof entry.scorePercent === 'number'
          ? entry.scorePercent
          : null;
      const entryFeedback =
        'feedback' in entry && typeof entry.feedback === 'string'
          ? entry.feedback
          : null;

      if (!itemId || scorePercent == null) {
        return [];
      }

      return [
        entryFeedback ? { itemId, scorePercent, feedback: entryFeedback } : { itemId, scorePercent },
      ];
    });
  }

  private getReviewTaskDelegate(): ReviewTaskDelegate {
    const prismaWithDelegate = this.prisma as PrismaClient & {
      gradingReviewTask?: ReviewTaskDelegate;
    };

    if (!prismaWithDelegate.gradingReviewTask) {
      throw new Error('gradingReviewTask delegate is unavailable. Regenerate Tutorputor Prisma client or align TeacherReviewService with the current assessment review schema.');
    }

    return prismaWithDelegate.gradingReviewTask;
  }

  private parseAiGradingResult(value: Prisma.JsonValue | null): AIGradingResult | null {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return null;
    }

    const scorePercent = 'scorePercent' in value && typeof value.scorePercent === 'number'
      ? value.scorePercent
      : null;
    const confidence = 'confidence' in value && typeof value.confidence === 'number'
      ? value.confidence
      : null;
    const feedback = 'feedback' in value && typeof value.feedback === 'string'
      ? value.feedback
      : null;

    if (scorePercent == null || confidence == null || !feedback) {
      return null;
    }

    return {
      scorePercent,
      confidence,
      feedback,
    };
  }

  private mapToReviewTask(task: StoredReviewTask): ReviewTask {
    const mappedTask: ReviewTask = {
      id: task.id,
      tenantId: task.tenantId,
      assessmentId: task.assessmentId,
      attemptId: task.attemptId,
      itemId: task.itemId,
      studentId: task.studentId,
      status: task.status,
      priority: task.priority,
      reason: task.reason,
      createdAt: task.createdAt.toISOString(),
    };

    if (task.assignedTo) {
      mappedTask.assignedTo = task.assignedTo;
    }
    if (task.originalScore != null) {
      mappedTask.originalScore = task.originalScore;
    }
    if (task.originalFeedback) {
      mappedTask.originalFeedback = task.originalFeedback;
    }
    const aiGradingResult = this.parseAiGradingResult(task.aiGradingResult);
    if (aiGradingResult) {
      mappedTask.aiGradingResult = aiGradingResult;
    }
    if (task.reviewedScore != null) {
      mappedTask.reviewedScore = task.reviewedScore;
    }
    if (task.reviewedFeedback) {
      mappedTask.reviewedFeedback = task.reviewedFeedback;
    }
    if (task.assignedAt) {
      mappedTask.assignedAt = task.assignedAt.toISOString();
    }
    if (task.completedAt) {
      mappedTask.completedAt = task.completedAt.toISOString();
    }

    return mappedTask;
  }
}
