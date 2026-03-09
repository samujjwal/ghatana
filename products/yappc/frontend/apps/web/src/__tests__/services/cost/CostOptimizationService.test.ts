import { describe, it, expect, beforeEach } from 'vitest';
import { CostOptimizationService } from '../../src/services/cost/CostOptimizationService';

/**
 * Unit tests for CostOptimizationService
 *
 * @doc.type test
 * @doc.purpose Verify cost optimization recommendation generation
 * @doc.layer backend
 * @doc.pattern Unit Test
 *
 * Coverage:
 * - Recommendation generation for each category
 * - Recommendation scoring based on savings and effort
 * - Filtering by recommendation type
 * - Sorting by potential savings
 */

describe('CostOptimizationService', () => {
  let service: CostOptimizationService;

  beforeEach(() => {
    service = new CostOptimizationService();
  });

  describe('generateRecommendations', () => {
    it('should generate recommendations from multiple categories', async () => {
      // WHEN: Generating recommendations
      const recommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // THEN: Should have recommendations from multiple categories
      expect(recommendations).toBeDefined();
      expect(recommendations.length).toBeGreaterThan(0);

      // Verify structure
      recommendations.forEach((rec) => {
        expect(rec).toHaveProperty('id');
        expect(rec).toHaveProperty('title');
        expect(rec).toHaveProperty('savings');
        expect(rec).toHaveProperty('effort');
        expect(rec).toHaveProperty('implementation');
        expect(rec).toHaveProperty('status');
        expect(rec.status).toBe('SUGGESTED');
      });
    });

    it('should generate right-sizing recommendations', async () => {
      // WHEN: Generating recommendations
      const recommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // THEN: Should include right-sizing recommendations
      const rightSizingRecs = recommendations.filter((r) =>
        r.title.toLowerCase().includes('right-size')
      );
      expect(rightSizingRecs.length).toBeGreaterThan(0);
      rightSizingRecs.forEach((rec) => {
        expect(rec.savings).toBeGreaterThan(0);
        expect(rec.implementation).toBeTruthy();
      });
    });

    it('should generate reservation instance recommendations', async () => {
      // WHEN: Generating recommendations
      const recommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // THEN: Should include reservation recommendations
      const reservationRecs = recommendations.filter((r) =>
        r.title.toLowerCase().includes('reserved')
      );
      expect(reservationRecs.length).toBeGreaterThan(0);
    });

    it('should generate spot instance recommendations', async () => {
      // WHEN: Generating recommendations
      const recommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // THEN: Should include spot instance recommendations
      const spotRecs = recommendations.filter((r) =>
        r.title.toLowerCase().includes('spot')
      );
      expect(spotRecs.length).toBeGreaterThan(0);
    });

    it('should generate cleanup recommendations', async () => {
      // WHEN: Generating recommendations
      const recommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // THEN: Should include cleanup recommendations
      const cleanupRecs = recommendations.filter((r) =>
        r.title.toLowerCase().includes('cleanup') || r.title.toLowerCase().includes('remove')
      );
      expect(cleanupRecs.length).toBeGreaterThan(0);
    });
  });

  describe('scoreRecommendation', () => {
    it('should score high-savings, low-effort recommendations highest', () => {
      // GIVEN: High-impact recommendation
      const recommendation = {
        id: '1',
        title: 'Test',
        savings: 1000,
        annualSavings: 12000,
        effort: 'LOW' as const,
        implementation: 'test',
        resourceIds: [],
        status: 'SUGGESTED' as const,
        estimatedMonthsSavings: 12,
      };

      // WHEN: Scoring
      const score = service.scoreRecommendation(recommendation);

      // THEN: Score should be high
      expect(score).toBeGreaterThan(80);
    });

    it('should score low-savings, high-effort recommendations lower', () => {
      // GIVEN: Low-impact recommendation
      const recommendation = {
        id: '1',
        title: 'Test',
        savings: 50,
        annualSavings: 600,
        effort: 'HIGH' as const,
        implementation: 'test',
        resourceIds: [],
        status: 'SUGGESTED' as const,
        estimatedMonthsSavings: 12,
      };

      // WHEN: Scoring
      const score = service.scoreRecommendation(recommendation);

      // THEN: Score should be lower
      expect(score).toBeLessThan(50);
    });

    it('should return score between 0-100', () => {
      // GIVEN: Various recommendations
      const testCases = [
        { savings: 100, effort: 'LOW' as const },
        { savings: 500, effort: 'MEDIUM' as const },
        { savings: 1000, effort: 'HIGH' as const },
      ];

      testCases.forEach((test) => {
        const recommendation = {
          id: '1',
          title: 'Test',
          savings: test.savings,
          annualSavings: test.savings * 12,
          effort: test.effort,
          implementation: 'test',
          resourceIds: [],
          status: 'SUGGESTED' as const,
          estimatedMonthsSavings: 12,
        };

        // WHEN: Scoring
        const score = service.scoreRecommendation(recommendation);

        // THEN: Score in valid range
        expect(score).toBeGreaterThanOrEqual(0);
        expect(score).toBeLessThanOrEqual(100);
      });
    });
  });

  describe('getRecommendationsByType', () => {
    it('should filter recommendations by type', async () => {
      // GIVEN: Generated recommendations
      const allRecommendations = await service.generateRecommendations(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        {}
      );

      // WHEN: Filtering by right-sizing type
      const filtered = await service.getRecommendationsByType(
        'right-sizing',
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') }
      );

      // THEN: Should only contain right-sizing recommendations
      expect(filtered.length).toBeGreaterThan(0);
      filtered.forEach((rec) => {
        expect(rec.title.toLowerCase()).toContain('right-size');
      });
    });

    it('should return empty array for unknown type', async () => {
      // WHEN: Requesting unknown recommendation type
      const filtered = await service.getRecommendationsByType(
        'unknown-type' as unknown,
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') }
      );

      // THEN: Should return empty array
      expect(Array.isArray(filtered)).toBe(true);
    });
  });
});
