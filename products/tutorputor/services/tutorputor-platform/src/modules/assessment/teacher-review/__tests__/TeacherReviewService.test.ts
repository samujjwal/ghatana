/**
 * Teacher Review Service Tests
 *
 * @doc.type test
 * @doc.purpose Test teacher review workflow service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TeacherReviewService } from '../TeacherReviewService';

describe('TeacherReviewService', () => {
  let service: TeacherReviewService;
  let mockPrisma: any;

  beforeEach(() => {
    mockPrisma = {
      gradingReviewTask: {
        create: vi.fn(),
        update: vi.fn(),
        findMany: vi.fn(),
        findUnique: vi.fn(),
      },
      assessmentAttempt: {
        findUnique: vi.fn(),
        update: vi.fn(),
      },
    };
    service = new TeacherReviewService(mockPrisma as any);
  });

  describe('createReviewTask', () => {
    it('should create a review task', async () => {
      const input = {
        tenantId: 'tenant-1',
        assessmentId: 'assessment-1',
        attemptId: 'attempt-1',
        itemId: 'item-1',
        studentId: 'student-1',
        reason: 'Low AI confidence',
      };

      mockPrisma.gradingReviewTask.create.mockResolvedValue({
        id: 'task-1',
        ...input,
        status: 'pending',
        priority: 'medium',
        createdAt: new Date(),
      });

      const task = await service.createReviewTask(input);

      expect(mockPrisma.gradingReviewTask.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          tenantId: input.tenantId,
          assessmentId: input.assessmentId,
          itemId: input.itemId,
          studentId: input.studentId,
          reason: input.reason,
        }),
      });
      expect(task.status).toBe('pending');
    });
  });

  describe('assignReviewTask', () => {
    it('should assign a task to a teacher', async () => {
      mockPrisma.gradingReviewTask.update.mockResolvedValue({
        id: 'task-1',
        assignedTo: 'teacher-1',
        status: 'assigned',
        assignedAt: new Date(),
      });

      const task = await service.assignReviewTask('task-1', 'teacher-1');

      expect(task.assignedTo).toBe('teacher-1');
      expect(task.status).toBe('assigned');
    });
  });

  describe('submitReview', () => {
    it('should submit an approved review', async () => {
      mockPrisma.gradingReviewTask.update.mockResolvedValue({
        id: 'task-1',
        status: 'completed',
        reviewedScore: 95,
        completedAt: new Date(),
        attemptId: 'attempt-1',
        itemId: 'item-1',
      });

      mockPrisma.assessmentAttempt.findUnique.mockResolvedValue({
        feedback: [{ itemId: 'item-1', scorePercent: 80 }],
      });

      mockPrisma.assessmentAttempt.update.mockResolvedValue({});

      const task = await service.submitReview({
        taskId: 'task-1',
        teacherId: 'teacher-1',
        approved: true,
        reviewedScore: 95,
        reviewedFeedback: 'Excellent response',
      });

      expect(task.status).toBe('completed');
      expect(task.reviewedScore).toBe(95);
    });

    it('should submit a rejected review', async () => {
      mockPrisma.gradingReviewTask.update.mockResolvedValue({
        id: 'task-1',
        status: 'rejected',
        completedAt: new Date(),
      });

      const task = await service.submitReview({
        taskId: 'task-1',
        teacherId: 'teacher-1',
        approved: false,
        comments: 'Needs more detail',
      });

      expect(task.status).toBe('rejected');
    });
  });

  describe('getReviewStats', () => {
    it('should calculate review statistics', async () => {
      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([
        { status: 'pending', assignedAt: new Date('2024-01-01'), completedAt: new Date('2024-01-02') },
        { status: 'assigned', assignedAt: null, completedAt: null },
        { status: 'in_progress', assignedAt: new Date('2024-01-01'), completedAt: null },
        { status: 'completed', assignedAt: new Date('2024-01-01'), completedAt: new Date('2024-01-02') },
      ]);

      const stats = await service.getReviewStats('tenant-1');

      expect(stats.pending).toBe(2);
      expect(stats.inProgress).toBe(1);
      expect(stats.completed).toBe(1);
    });
  });
});
