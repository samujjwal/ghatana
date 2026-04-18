/**
 * Enhanced Predictive Analytics Service Tests
 *
 * @doc.type test
 * @doc.purpose Test enhanced predictive analytics service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { EnhancedPredictiveAnalyticsService } from '../EnhancedPredictiveAnalyticsService';

describe('EnhancedPredictiveAnalyticsService', () => {
  let service: EnhancedPredictiveAnalyticsService;
  let mockPrisma: any;

  beforeEach(() => {
    mockPrisma = {
      enrollment: { findMany: vi.fn() },
      learnerProfile: { findUnique: vi.fn() },
      module: { findMany: vi.fn() },
      conceptMastery: { findMany: vi.fn() },
      learningEvent: { findMany: vi.fn() },
      assessmentAttempt: { findMany: vi.fn() },
    };
    service = new EnhancedPredictiveAnalyticsService(mockPrisma as any);
  });

  describe('predictLearningPath', () => {
    it('should predict learning path', async () => {
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { status: 'COMPLETED', moduleId: 'mod-1', module: { id: 'mod-1', title: 'Module 1', difficulty: 'INTRO' } },
      ]);
      mockPrisma.learnerProfile.findUnique.mockResolvedValue({
        masterySummary: { averageMastery: 0.7 },
      });
      mockPrisma.module.findMany.mockResolvedValue([
        { id: 'mod-2', title: 'Module 2', difficulty: 'INTERMEDIATE' },
        { id: 'mod-3', title: 'Module 3', difficulty: 'ADVANCED' },
      ]);

      const prediction = await service.predictLearningPath('tenant-1', 'user-1', 'Learn Python');

      expect(prediction.userId).toBe('user-1');
      expect(prediction.recommendedPath).toBeDefined();
      expect(Array.isArray(prediction.recommendedPath)).toBe(true);
      expect(prediction.confidence).toBeGreaterThan(0);
    });

    it('should handle no enrollments', async () => {
      mockPrisma.enrollment.findMany.mockResolvedValue([]);
      mockPrisma.learnerProfile.findUnique.mockResolvedValue(null);
      mockPrisma.module.findMany.mockResolvedValue([
        { id: 'mod-1', title: 'Module 1', difficulty: 'INTRO' },
      ]);

      const prediction = await service.predictLearningPath('tenant-1', 'user-1', 'Learn Python');

      expect(prediction.recommendedPath).toBeDefined();
      expect(prediction.confidence).toBeGreaterThan(0);
    });
  });

  describe('predictMastery', () => {
    it('should predict mastery', async () => {
      mockPrisma.learnerProfile.findUnique.mockResolvedValue({
        masterySummary: { averageMastery: 0.7 },
      });
      mockPrisma.conceptMastery.findMany.mockResolvedValue([
        { masteryLevel: 0.6 },
      ]);

      const prediction = await service.predictMastery('tenant-1', 'user-1', 'concept-1');

      expect(prediction.userId).toBe('user-1');
      expect(prediction.conceptId).toBe('concept-1');
      expect(prediction.currentMastery).toBe(0.6);
      expect(prediction.predictedMastery).toBeGreaterThan(0);
    });

    it('should handle no mastery data', async () => {
      mockPrisma.learnerProfile.findUnique.mockResolvedValue(null);
      mockPrisma.conceptMastery.findMany.mockResolvedValue([]);

      const prediction = await service.predictMastery('tenant-1', 'user-1', 'concept-1');

      expect(prediction.currentMastery).toBe(0);
      expect(prediction.predictedMastery).toBeGreaterThan(0);
    });
  });

  describe('predictDropout', () => {
    it('should predict dropout risk', async () => {
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { status: 'IN_PROGRESS', progressPercent: 10, startedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000) },
      ]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 50 },
      ]);
      mockPrisma.learningEvent.findMany.mockResolvedValue([]);

      const prediction = await service.predictDropout('tenant-1', 'user-1');

      expect(prediction.userId).toBe('user-1');
      expect(prediction.dropoutProbability).toBeGreaterThan(0);
      expect(prediction.riskFactors).toBeDefined();
      expect(prediction.interventionWindow).toBeDefined();
    });

    it('should predict low risk for engaged students', async () => {
      mockPrisma.enrollment.findMany.mockResolvedValue([
        { status: 'COMPLETED', progressPercent: 100, startedAt: new Date() },
      ]);
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 85 },
      ]);
      mockPrisma.learningEvent.findMany.mockResolvedValue([
        { timestamp: new Date() },
        { timestamp: new Date() },
        { timestamp: new Date() },
        { timestamp: new Date() },
        { timestamp: new Date() },
      ]);

      const prediction = await service.predictDropout('tenant-1', 'user-1');

      expect(prediction.dropoutProbability).toBeLessThan(0.5);
    });
  });

  describe('analyzeContentGaps', () => {
    it('should analyze content gaps', async () => {
      const gaps = await service.analyzeContentGaps('tenant-1');

      expect(Array.isArray(gaps)).toBe(true);
      expect(gaps.length).toBeGreaterThan(0);
      expect(gaps[0].conceptId).toBeDefined();
      expect(gaps[0].gapSeverity).toBeDefined();
    });
  });
});
