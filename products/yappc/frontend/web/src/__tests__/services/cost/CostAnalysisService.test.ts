import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { CostAnalysisService } from '../../src/services/cost/CostAnalysisService';
import { InMemoryCloudCostRepository } from '../../src/repositories/CloudCostRepository';
import { CloudCost } from '../../src/models/cost/CloudCost.entity';
import * as CostCalculations from '../../src/utils/cost/CostCalculations';

/**
 * Unit tests for CostAnalysisService
 *
 * @doc.type test
 * @doc.purpose Verify cost analysis service business logic
 * @doc.layer backend
 * @doc.pattern Unit Test
 *
 * Coverage:
 * - Cost analysis with period and filters
 * - Trend calculation and direction detection
 * - Anomaly detection using statistical methods
 * - Metrics calculation (avg, min, max, stdev)
 * - Service/provider/tag aggregation
 */

describe('CostAnalysisService', () => {
  let service: CostAnalysisService;
  let repository: InMemoryCloudCostRepository;

  beforeEach(() => {
    repository = new InMemoryCloudCostRepository();
    service = new CostAnalysisService(repository);
  });

  afterEach(() => {
    // Cleanup
    repository.clear?.();
  });

  describe('analyzeCosts', () => {
    it('should analyze costs for a given period with aggregations', async () => {
      // GIVEN: Cost data for multiple services and providers
      const costs: ReadonlyArray<CloudCost> = [
        {
          id: '1',
          date: new Date('2024-11-01'),
          provider: 'AWS',
          service: 'EC2',
          cost: 100,
          currency: 'USD',
          tags: ['production'],
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        {
          id: '2',
          date: new Date('2024-11-02'),
          provider: 'AWS',
          service: 'S3',
          cost: 50,
          currency: 'USD',
          tags: ['data'],
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        {
          id: '3',
          date: new Date('2024-11-03'),
          provider: 'GCP',
          service: 'Compute',
          cost: 80,
          currency: 'USD',
          tags: ['ml-training'],
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      ];

      for (const cost of costs) {
        await repository.save(cost);
      }

      // WHEN: Analyzing costs for the period
      const analysis = await service.analyzeCosts({
        start: new Date('2024-11-01'),
        end: new Date('2024-11-30'),
      });

      // THEN: Verify aggregations and totals
      expect(analysis).toBeDefined();
      expect(analysis.totalCost).toBe(230);
      expect(analysis.currency).toBe('USD');
      expect(analysis.costByService).toHaveLength(3);
      expect(analysis.costByProvider).toHaveLength(2);
    });

    it('should filter costs by provider', async () => {
      // GIVEN: Multiple providers
      const costs: ReadonlyArray<CloudCost> = [
        {
          id: '1',
          date: new Date('2024-11-01'),
          provider: 'AWS',
          service: 'EC2',
          cost: 100,
          currency: 'USD',
          tags: [],
          createdAt: new Date(),
          updatedAt: new Date(),
        },
        {
          id: '2',
          date: new Date('2024-11-02'),
          provider: 'GCP',
          service: 'Compute',
          cost: 80,
          currency: 'USD',
          tags: [],
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      ];

      for (const cost of costs) {
        await repository.save(cost);
      }

      // WHEN: Analyzing with provider filter
      const analysis = await service.analyzeCosts(
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
        { providers: ['AWS'] }
      );

      // THEN: Only AWS costs included
      expect(analysis.totalCost).toBe(100);
    });

    it('should handle empty period gracefully', async () => {
      // WHEN: Analyzing period with no data
      const analysis = await service.analyzeCosts({
        start: new Date('2024-11-01'),
        end: new Date('2024-11-30'),
      });

      // THEN: Return empty but valid structure
      expect(analysis.totalCost).toBe(0);
      expect(analysis.costByService).toHaveLength(0);
      expect(analysis.costByProvider).toHaveLength(0);
    });
  });

  describe('calculateTrend', () => {
    it('should detect upward trend when current > previous', async () => {
      // GIVEN: Historical cost progression
      const previousCosts = [
        { id: '1', cost: 1000, date: new Date('2024-10-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '2', cost: 1050, date: new Date('2024-10-02'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];
      const currentCosts = [
        { id: '3', cost: 1100, date: new Date('2024-11-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '4', cost: 1150, date: new Date('2024-11-02'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];

      for (const cost of [...previousCosts, ...currentCosts]) {
        await repository.save(cost as CloudCost);
      }

      // WHEN: Calculating trend
      const trend = await service.calculateTrend(
        { start: new Date('2024-10-01'), end: new Date('2024-10-31') },
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') }
      );

      // THEN: Direction should be 'up'
      expect(trend.direction).toBe('up');
      expect(trend.percentageChange).toBeGreaterThan(0);
    });

    it('should detect downward trend when current < previous', async () => {
      // GIVEN: Decreasing costs
      const previousCosts = [
        { id: '1', cost: 1200, date: new Date('2024-10-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];
      const currentCosts = [
        { id: '2', cost: 1000, date: new Date('2024-11-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];

      for (const cost of [...previousCosts, ...currentCosts]) {
        await repository.save(cost as CloudCost);
      }

      // WHEN: Calculating trend
      const trend = await service.calculateTrend(
        { start: new Date('2024-10-01'), end: new Date('2024-10-31') },
        { start: new Date('2024-11-01'), end: new Date('2024-11-30') }
      );

      // THEN: Direction should be 'down'
      expect(trend.direction).toBe('down');
      expect(trend.percentageChange).toBeLessThan(0);
    });
  });

  describe('detectAnomalies', () => {
    it('should detect statistical outliers (>2 sigma)', async () => {
      // GIVEN: Cost data with outliers
      const costs: CloudCost[] = [
        // Normal range: 100-110
        { id: '1', cost: 100, date: new Date('2024-11-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '2', cost: 105, date: new Date('2024-11-02'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '3', cost: 110, date: new Date('2024-11-03'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        // Outlier: 250
        { id: '4', cost: 250, date: new Date('2024-11-04'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];

      for (const cost of costs) {
        await repository.save(cost);
      }

      const dailyAggregates = costs.map(c => ({
        date: c.date.toISOString().split('T')[0],
        amount: c.cost,
      }));

      // WHEN: Detecting anomalies
      const anomalies = service.detectAnomalies(costs, dailyAggregates);

      // THEN: Should flag the outlier
      expect(anomalies.length).toBeGreaterThan(0);
      expect(anomalies.some(a => a.amount === 250)).toBe(true);
    });

    it('should return empty list when no anomalies', async () => {
      // GIVEN: Stable cost data
      const costs: CloudCost[] = [
        { id: '1', cost: 100, date: new Date('2024-11-01'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '2', cost: 100, date: new Date('2024-11-02'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
        { id: '3', cost: 100, date: new Date('2024-11-03'), provider: 'AWS', service: 'EC2', currency: 'USD', tags: [], createdAt: new Date(), updatedAt: new Date() },
      ];

      const dailyAggregates = costs.map(c => ({
        date: c.date.toISOString().split('T')[0],
        amount: c.cost,
      }));

      // WHEN: Detecting anomalies
      const anomalies = service.detectAnomalies(costs, dailyAggregates);

      // THEN: No anomalies detected
      expect(anomalies).toHaveLength(0);
    });
  });

  describe('calculateMetrics', () => {
    it('should calculate correct statistical metrics', async () => {
      // GIVEN: Known set of daily costs
      const dailyAggregates = [
        { date: '2024-11-01', amount: 100 },
        { date: '2024-11-02', amount: 150 },
        { date: '2024-11-03', amount: 200 },
        { date: '2024-11-04', amount: 250 },
      ];

      // WHEN: Calculating metrics
      const metrics = service.calculateMetrics(dailyAggregates);

      // THEN: Verify calculations
      expect(metrics.averageDailyCost).toBe(175); // (100+150+200+250)/4
      expect(metrics.minimumDailyCost).toBe(100);
      expect(metrics.maximumDailyCost).toBe(250);
      expect(metrics.standardDeviation).toBeGreaterThan(0);
      expect(metrics.percentageChange).toBeGreaterThan(0); // Last vs first
    });

    it('should handle single data point', async () => {
      // GIVEN: Only one cost entry
      const dailyAggregates = [{ date: '2024-11-01', amount: 100 }];

      // WHEN: Calculating metrics
      const metrics = service.calculateMetrics(dailyAggregates);

      // THEN: Metrics should be valid
      expect(metrics.averageDailyCost).toBe(100);
      expect(metrics.minimumDailyCost).toBe(100);
      expect(metrics.maximumDailyCost).toBe(100);
      expect(metrics.standardDeviation).toBe(0);
    });
  });
});
