/**
 * Grading Quality Monitoring Service Tests
 *
 * @doc.type test
 * @doc.purpose Test grading quality monitoring service
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { GradingQualityMonitoringService } from '../GradingQualityMonitoringService';

describe('GradingQualityMonitoringService', () => {
  let service: GradingQualityMonitoringService;
  let mockPrisma: any;

  beforeEach(() => {
    mockPrisma = {
      assessmentAttempt: {
        findMany: vi.fn(),
      },
      gradingReviewTask: {
        findMany: vi.fn(),
      },
    };
    service = new GradingQualityMonitoringService(mockPrisma as any);
  });

  describe('calculateQualityMetrics', () => {
    it('should calculate quality metrics', async () => {
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 85, gradedAt: new Date() },
        { scorePercent: 90, gradedAt: new Date() },
      ]);

      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([
        {
          status: 'completed',
          aiGradingResult: { scorePercent: 85, confidence: 0.9 },
          reviewedScore: 88,
          completedAt: new Date(),
        },
      ]);

      const metrics = await service.calculateQualityMetrics('tenant-1');

      expect(metrics.aiGradingAccuracy).toBeGreaterThan(0);
      expect(metrics.teacherAiAgreement).toBeGreaterThan(0);
      expect(metrics.gradingConsistency).toBeGreaterThan(0);
    });

    it('should handle empty data', async () => {
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([]);
      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([]);

      const metrics = await service.calculateQualityMetrics('tenant-1');

      expect(metrics.aiGradingAccuracy).toBe(1);
      expect(metrics.teacherAiAgreement).toBe(1);
    });
  });

  describe('checkQualityAlerts', () => {
    it('should generate alerts for low metrics', async () => {
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 50, gradedAt: new Date() },
      ]);

      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([]);

      const alerts = await service.checkQualityAlerts('tenant-1');

      expect(alerts).toHaveLength(1);
      expect(alerts[0].type).toBe('low_accuracy');
    });

    it('should return no alerts when metrics are healthy', async () => {
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 90, gradedAt: new Date() },
      ]);

      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([]);

      const alerts = await service.checkQualityAlerts('tenant-1');

      expect(alerts).toHaveLength(0);
    });
  });

  describe('getQualityDashboard', () => {
    it('should return dashboard data with metrics, alerts, and trends', async () => {
      mockPrisma.assessmentAttempt.findMany.mockResolvedValue([
        { scorePercent: 85, gradedAt: new Date() },
      ]);

      mockPrisma.gradingReviewTask.findMany.mockResolvedValue([]);

      const dashboard = await service.getQualityDashboard('tenant-1');

      expect(dashboard.metrics).toBeDefined();
      expect(dashboard.alerts).toBeDefined();
      expect(dashboard.trends).toBeDefined();
      expect(dashboard.trends).toHaveLength(7);
    });
  });
});
