/**
 * Teacher Analytics Service Tests
 *
 * @doc.type test
 * @doc.purpose Test teacher analytics service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TeacherAnalyticsService } from '../TeacherAnalyticsService';

describe('TeacherAnalyticsService', () => {
  let service: TeacherAnalyticsService;
  let mockPrisma: any;

  beforeEach(() => {
    mockPrisma = {
      classroom: { findFirst: vi.fn() },
      classroomMember: { findMany: vi.fn().mockResolvedValue([
        { userId: 'user-1' },
        { userId: 'user-2' },
      ]) },
      enrollment: { findMany: vi.fn() },
      assessmentAttempt: { findMany: vi.fn() },
      user: {
        findUnique: vi.fn(),
        findMany: vi.fn().mockResolvedValue([
          { id: 'user-1', displayName: 'Student 1' },
          { id: 'user-2', displayName: 'Student 2' },
        ]),
      },
      learningEvent: { findMany: vi.fn() },
    };
    service = new TeacherAnalyticsService(mockPrisma as any);
  });

  describe('getClassroomAnalytics', () => {
    it('should return classroom analytics', async () => {
      mockPrisma.classroom.findFirst.mockResolvedValue({
        id: 'class-1',
        title: 'Math 101',
      });
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { userId: 'user-1', progressPercent: 50, status: 'IN_PROGRESS', user: { displayName: 'Student 1' } },
        { userId: 'user-2', progressPercent: 80, status: 'COMPLETED', user: { displayName: 'Student 2' } },
      ]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { userId: 'user-1', scorePercent: 75 },
        { userId: 'user-2', scorePercent: 90 },
      ]);

      const analytics = await service.getClassroomAnalytics('tenant-1', 'class-1');

      expect(mockPrisma.classroom.findFirst).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'class-1', tenantId: 'tenant-1' },
        }),
      );
      expect(analytics.classroomId).toBe('class-1');
      expect(analytics.classroomName).toBe('Math 101');
      expect(analytics.totalStudents).toBe(2);
      expect(analytics.averageProgress).toBe(65);
      expect(analytics.averageScore).toBe(82.5);
    });

    it('should throw error if classroom not found', async () => {
      mockPrisma.classroom.findFirst.mockResolvedValue(null);

      await expect(service.getClassroomAnalytics('tenant-1', 'class-1')).rejects.toThrow('Classroom not found');
    });
  });

  describe('getStudentAnalytics', () => {
    it('should return student analytics', async () => {
      mockPrisma.user.findUnique.mockResolvedValue({
        id: 'user-1',
        displayName: 'Student 1',
        email: 'student@example.com',
      });
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { progressPercent: 50, status: 'IN_PROGRESS', timeSpentSeconds: 3600 },
        { progressPercent: 100, status: 'COMPLETED', timeSpentSeconds: 7200 },
      ]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 85 },
        { scorePercent: 90 },
      ]);
      mockPrisma.learningEvent.findMany.mockResolvedValue([
        { timestamp: new Date() },
      ]);

      const analytics = await service.getStudentAnalytics('tenant-1', 'user-1');

      expect(analytics.userId).toBe('user-1');
      expect(analytics.displayName).toBe('Student 1');
      expect(analytics.totalEnrollments).toBe(2);
      expect(analytics.completedModules).toBe(1);
      expect(analytics.averageScore).toBe(87.5);
    });

    it('should throw error if student not found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.getStudentAnalytics('tenant-1', 'user-1')).rejects.toThrow('Student not found');
    });
  });

  describe('getInterventionRecommendations', () => {
    it('should generate intervention recommendations', async () => {
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { userId: 'user-1', progressPercent: 50, status: 'IN_PROGRESS', user: { displayName: 'Student 1' } },
        { userId: 'user-2', progressPercent: 20, status: 'IN_PROGRESS', user: { displayName: 'Student 2' } },
      ]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { userId: 'user-1', scorePercent: 75 },
        { userId: 'user-2', scorePercent: 50 },
      ]);
      mockPrisma.classroom.findFirst.mockResolvedValue({
        id: 'class-1',
        title: 'Math 101',
      });

      const recommendations = await service.getInterventionRecommendations('tenant-1', 'class-1');

      expect(recommendations).toBeDefined();
      expect(Array.isArray(recommendations)).toBe(true);
    });
  });

  describe('getInstructorEvidenceDashboardTiles', () => {
    it('computes instructor tiles from persisted assessment and telemetry evidence', async () => {
      mockPrisma.classroom.findFirst.mockResolvedValue({
        id: 'class-1',
        title: 'Math 101',
      });
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        {
          userId: 'user-1',
          scorePercent: 80,
          feedback: { baselineBrier: 0.3 },
        },
        {
          userId: 'user-2',
          scorePercent: 45,
          feedback: { baselineBrier: 0.3 },
        },
      ]);
      mockPrisma.learningEvent.findMany.mockResolvedValue([
        {
          userId: 'user-1',
          eventType: 'assess.answer',
          payload: {
            object: { claimId: 'claim-1' },
            result: { score: 1, maxScore: 1, confidence: 'high', correct: true },
          },
        },
        {
          userId: 'user-2',
          eventType: 'assess.answer',
          payload: {
            object: { claimId: 'claim-1' },
            result: { score: 0, maxScore: 1, confidence: 'high', correct: false },
          },
        },
        {
          userId: 'user-2',
          eventType: 'sim.capture',
          payload: {
            result: { processFeatures: { processScore: 0.35 } },
          },
        },
        {
          userId: 'user-1',
          eventType: 'sim.capture',
          payload: {
            result: { processFeatures: { processScore: 0.8 } },
          },
        },
        {
          userId: 'user-2',
          eventType: 'assist.hint',
          payload: { object: { hintId: 'hint-1' } },
        },
        {
          userId: 'user-2',
          eventType: 'assist.hint',
          payload: { object: { hintId: 'hint-2' } },
        },
        {
          userId: 'user-2',
          eventType: 'viva.scheduled',
          payload: { priority: 'high' },
        },
        {
          userId: 'user-2',
          eventType: 'remediation.assigned',
          payload: { taskId: 'remediate-1' },
        },
        {
          userId: 'user-2',
          eventType: 'remediation.completed',
          payload: { taskId: 'remediate-1' },
        },
      ]);

      const tiles = await service.getInstructorEvidenceDashboardTiles(
        'tenant-1',
        'class-1',
      );

      expect(mockPrisma.classroom.findFirst).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: 'class-1', tenantId: 'tenant-1' },
        }),
      );
      expect(tiles.brierScore).toBe(0.41);
      expect(tiles.calibrationGain).toBe(0);
      expect(tiles.masteryByClaim).toEqual([
        { claimId: 'claim-1', mastery: 0.5, evidenceCount: 2 },
      ]);
      expect(tiles.processScoreDistribution).toEqual({
        low: 1,
        medium: 0,
        high: 1,
      });
      expect(tiles.vivaQueue).toEqual({ total: 1, highPriority: 1 });
      expect(tiles.atRiskLearners[0]).toEqual(
        expect.objectContaining({
          userId: 'user-2',
          reasons: expect.arrayContaining([
            'low assessment score',
            'repeated hint usage',
          ]),
        }),
      );
      expect(tiles.remediationCompletion).toEqual({
        assigned: 1,
        completed: 1,
        completionRate: 1,
      });
    });

    it('rejects cross-tenant classroom dashboard access', async () => {
      mockPrisma.classroom.findFirst.mockResolvedValue(null);

      await expect(
        service.getInstructorEvidenceDashboardTiles('tenant-2', 'class-1'),
      ).rejects.toThrow('Classroom not found');

      expect(mockPrisma.classroomMember.findMany).not.toHaveBeenCalled();
    });
  });
});
