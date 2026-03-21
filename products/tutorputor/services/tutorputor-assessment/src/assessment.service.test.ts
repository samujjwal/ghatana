/**
 * Assessment Service Tests
 * Part of Execution Plan item #5: Improve Test Coverage to 60%
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PrismaClient } from '@prisma/client';
import { AssessmentService } from './assessment.service';

// Mock Prisma
vi.mock('@prisma/client', () => ({
  PrismaClient: vi.fn().mockImplementation(() => ({
    assessment: {
      findUnique: vi.fn(),
      findMany: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
    },
    submission: {
      create: vi.fn(),
      findUnique: vi.fn(),
    },
  })),
}));

describe('AssessmentService', () => {
  let service: AssessmentService;
  let prisma: PrismaClient;

  beforeEach(() => {
    prisma = new PrismaClient();
    service = new AssessmentService(prisma);
  });

  describe('getAssessment', () => {
    it('should return an assessment by id', async () => {
      const mockAssessment = {
        id: 'assess-1',
        title: 'Physics Quiz',
        questions: [{ id: 'q1', text: 'What is gravity?' }],
      };

      (prisma.assessment.findUnique as any).mockResolvedValue(mockAssessment);

      const result = await service.getAssessment('assess-1');

      expect(result).toEqual(mockAssessment);
      expect(prisma.assessment.findUnique).toHaveBeenCalledWith({
        where: { id: 'assess-1' },
        include: { questions: true },
      });
    });

    it('should return null if assessment not found', async () => {
      (prisma.assessment.findUnique as any).mockResolvedValue(null);

      const result = await service.getAssessment('non-existent');

      expect(result).toBeNull();
    });
  });

  describe('submitAssessment', () => {
    it('should create a submission and calculate score', async () => {
      const mockAssessment = {
        id: 'assess-1',
        questions: [
          { id: 'q1', correctAnswer: 'A' },
          { id: 'q2', correctAnswer: 'B' },
        ],
      };

      const submissionData = {
        assessmentId: 'assess-1',
        userId: 'user-1',
        answers: [
          { questionId: 'q1', answer: 'A' },
          { questionId: 'q2', answer: 'C' },
        ],
      };

      (prisma.assessment.findUnique as any).mockResolvedValue(mockAssessment);
      (prisma.submission.create as any).mockResolvedValue({
        id: 'sub-1',
        ...submissionData,
        score: 50,
      });

      const result = await service.submitAssessment(submissionData);

      expect(result.score).toBe(50);
      expect(prisma.submission.create).toHaveBeenCalled();
    });

    it('should throw error if assessment not found', async () => {
      (prisma.assessment.findUnique as any).mockResolvedValue(null);

      await expect(
        service.submitAssessment({
          assessmentId: 'non-existent',
          userId: 'user-1',
          answers: [],
        })
      ).rejects.toThrow('Assessment not found');
    });
  });

  describe('listAssessments', () => {
    it('should return paginated assessments', async () => {
      const mockAssessments = [
        { id: 'assess-1', title: 'Quiz 1' },
        { id: 'assess-2', title: 'Quiz 2' },
      ];

      (prisma.assessment.findMany as any).mockResolvedValue(mockAssessments);

      const result = await service.listAssessments({ limit: 10, offset: 0 });

      expect(result).toHaveLength(2);
      expect(prisma.assessment.findMany).toHaveBeenCalledWith({
        take: 10,
        skip: 0,
        orderBy: { createdAt: 'desc' },
      });
    });
  });
});
