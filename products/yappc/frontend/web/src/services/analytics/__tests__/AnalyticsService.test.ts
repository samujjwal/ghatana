/**
 * AnalyticsService Tests
 */

import { describe, it, expect } from 'vitest';
import {
  calculateChange,
  calculateHealthScore,
  identifyBottleneck,
  generateReport,
  aggregateProjectMetrics,
  aggregateTeamUtilisation,
  type ProjectMetrics,
  type TeamMetrics,
  type LifecycleMetrics,
} from '../AnalyticsService';

describe('AnalyticsService', () => {
  describe('calculateChange', () => {
    it('should calculate positive change', () => {
      const result = calculateChange(150, 100);
      expect(result.change).toBe(50);
      expect(result.trend).toBe('up');
    });

    it('should calculate negative change', () => {
      const result = calculateChange(80, 100);
      expect(result.change).toBe(-20);
      expect(result.trend).toBe('down');
    });

    it('should return flat when change is negligible', () => {
      const result = calculateChange(100, 100);
      expect(result.trend).toBe('flat');
    });

    it('should handle zero previous value', () => {
      const result = calculateChange(50, 0);
      expect(result.change).toBe(0);
      expect(result.trend).toBe('flat');
    });
  });

  describe('calculateHealthScore', () => {
    it('should return max 100', () => {
      const score = calculateHealthScore(1.0, 0, 0);
      expect(score).toBeLessThanOrEqual(100);
    });

    it('should reduce score for open issues', () => {
      const clean = calculateHealthScore(0.8, 0, 5);
      const issues = calculateHealthScore(0.8, 10, 5);
      expect(clean).toBeGreaterThan(issues);
    });

    it('should reduce score for long cycle time', () => {
      const fast = calculateHealthScore(0.8, 2, 1);
      const slow = calculateHealthScore(0.8, 2, 15);
      expect(fast).toBeGreaterThan(slow);
    });

    it('should not go below 0', () => {
      const score = calculateHealthScore(0, 100, 100);
      expect(score).toBeGreaterThanOrEqual(0);
    });
  });

  describe('identifyBottleneck', () => {
    it('should return phase with highest bottleneck score above 0.5', () => {
      const metrics: LifecycleMetrics[] = [
        { phase: 'Validate', avgDuration: 3, successRate: 0.9, bottleneckScore: 0.3 },
        { phase: 'Generate', avgDuration: 7, successRate: 0.6, bottleneckScore: 0.8 },
      ];
      expect(identifyBottleneck(metrics)).toBe('Generate');
    });

    it('should return null when no bottleneck exceeds threshold', () => {
      const metrics: LifecycleMetrics[] = [
        { phase: 'Validate', avgDuration: 3, successRate: 0.9, bottleneckScore: 0.2 },
      ];
      expect(identifyBottleneck(metrics)).toBeNull();
    });

    it('should return null for empty array', () => {
      expect(identifyBottleneck([])).toBeNull();
    });
  });

  describe('generateReport', () => {
    it('should return a report with the correct period', async () => {
      const report = await generateReport({
        startDate: '2026-01-01',
        endDate: '2026-01-31',
      });
      expect(report.period.start).toBe('2026-01-01');
      expect(report.period.end).toBe('2026-01-31');
    });

    it('should include all required sections', async () => {
      const report = await generateReport({
        startDate: '2026-01-01',
        endDate: '2026-01-31',
      });
      expect(report.summary).toBeDefined();
      expect(report.projectMetrics).toBeInstanceOf(Array);
      expect(report.teamMetrics).toBeInstanceOf(Array);
      expect(report.lifecycleMetrics).toBeInstanceOf(Array);
      expect(report.timeSeries).toBeDefined();
    });
  });

  describe('aggregateProjectMetrics', () => {
    it('should average health scores', () => {
      const projects: ProjectMetrics[] = [
        { projectId: 'p1', name: 'A', completionRate: 0.8, taskCount: 10, openIssues: 2, avgCycleTime: 3, healthScore: 80 },
        { projectId: 'p2', name: 'B', completionRate: 0.6, taskCount: 8, openIssues: 5, avgCycleTime: 5, healthScore: 60 },
      ];
      const result = aggregateProjectMetrics(projects);
      expect(result.current).toBe(70);
    });

    it('should return 0 for empty projects', () => {
      const result = aggregateProjectMetrics([]);
      expect(result.current).toBe(0);
    });
  });

  describe('aggregateTeamUtilisation', () => {
    it('should average utilisation', () => {
      const team: TeamMetrics[] = [
        { memberId: 'm1', name: 'A', tasksCompleted: 10, avgResponseTime: 2, utilisation: 0.8 },
        { memberId: 'm2', name: 'B', tasksCompleted: 8, avgResponseTime: 3, utilisation: 0.6 },
      ];
      expect(aggregateTeamUtilisation(team)).toBe(0.7);
    });

    it('should return 0 for empty team', () => {
      expect(aggregateTeamUtilisation([])).toBe(0);
    });
  });
});
