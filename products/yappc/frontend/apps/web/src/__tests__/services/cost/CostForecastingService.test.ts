import { describe, it, expect, beforeEach } from 'vitest';
import { CostForecastingService } from '../../src/services/cost/CostForecastingService';

/**
 * Unit tests for CostForecastingService
 *
 * @doc.type test
 * @doc.purpose Verify cost forecasting and budget planning
 * @doc.layer backend
 * @doc.pattern Unit Test
 *
 * Coverage:
 * - Cost forecasting with 1-36 month horizons
 * - Budget plan generation with risk assessment
 * - Trend analysis and projection accuracy
 * - Seasonality factor calculation
 */

describe('CostForecastingService', () => {
  let service: CostForecastingService;

  beforeEach(() => {
    service = new CostForecastingService();
  });

  describe('forecastCosts', () => {
    it('should forecast costs for 12 months', async () => {
      // WHEN: Forecasting costs
      const forecast = await service.forecastCosts(12);

      // THEN: Should have 12 month projections
      expect(forecast).toBeDefined();
      expect(forecast.monthlyProjections).toBeDefined();
      expect(forecast.monthlyProjections.length).toBe(12);
      expect(forecast.projectedCost).toBeGreaterThan(0);
      expect(forecast.confidence).toBeGreaterThanOrEqual(0.7);
      expect(forecast.confidence).toBeLessThanOrEqual(0.99);
    });

    it('should support variable forecast horizons', async () => {
      // Test different forecast periods
      const testCases = [1, 3, 6, 12, 24, 36];

      for (const months of testCases) {
        // WHEN: Forecasting
        const forecast = await service.forecastCosts(months);

        // THEN: Should have correct number of projections
        expect(forecast.monthlyProjections.length).toBe(months);
        expect(forecast.projectedCost).toBeGreaterThan(0);
      }
    });

    it('should include confidence intervals in projections', async () => {
      // WHEN: Forecasting
      const forecast = await service.forecastCosts(12);

      // THEN: Each projection should have confidence bounds
      forecast.monthlyProjections.forEach((proj) => {
        expect(proj).toHaveProperty('projectedCost');
        expect(proj).toHaveProperty('confidenceLower');
        expect(proj).toHaveProperty('confidenceUpper');
        expect(proj.confidenceLower).toBeLessThanOrEqual(proj.projectedCost);
        expect(proj.projectedCost).toBeLessThanOrEqual(proj.confidenceUpper);
      });
    });

    it('should identify seasonality factors', async () => {
      // WHEN: Forecasting
      const forecast = await service.forecastCosts(24);

      // THEN: Should detect seasonality
      expect(forecast.seasonalityFactors).toBeDefined();
      expect(forecast.seasonalityFactors.length).toBeGreaterThan(0);
      forecast.seasonalityFactors.forEach((factor) => {
        expect(factor.factor).toBeGreaterThan(0);
      });
    });

    it('should include risk assessment', async () => {
      // WHEN: Forecasting
      const forecast = await service.forecastCosts(12);

      // THEN: Should have identified risks
      expect(forecast.risks).toBeDefined();
      expect(forecast.risks.length).toBeGreaterThanOrEqual(0);
      forecast.risks.forEach((risk) => {
        expect(risk).toHaveProperty('description');
        expect(risk).toHaveProperty('probability');
        expect(risk).toHaveProperty('impact');
        expect(risk.probability).toBeGreaterThanOrEqual(0);
        expect(risk.probability).toBeLessThanOrEqual(1);
      });
    });
  });

  describe('generateBudgetPlan', () => {
    it('should generate budget plan with buffer', async () => {
      // WHEN: Generating budget plan
      const plan = await service.generateBudgetPlan(12, 10);

      // THEN: Should have budget allocations
      expect(plan).toBeDefined();
      expect(plan.monthlyBudget).toBeGreaterThan(0);
      expect(plan.totalBudget).toBeGreaterThan(plan.forecastedTotal);
      expect(plan.bufferAmount).toBeGreaterThan(0);
    });

    it('should calculate buffer correctly', async () => {
      // WHEN: Generating budget with 15% buffer
      const plan = await service.generateBudgetPlan(12, 15);

      // THEN: Buffer should be ~15% of forecasted amount
      const bufferPercentage = (plan.bufferAmount / plan.forecastedTotal) * 100;
      expect(bufferPercentage).toBeCloseTo(15, 1);
    });

    it('should assess risk levels', async () => {
      // WHEN: Generating budget plans with different horizons
      const shortTermPlan = await service.generateBudgetPlan(3, 10);
      const longTermPlan = await service.generateBudgetPlan(36, 10);

      // THEN: Both should have risk assessment
      expect(['LOW', 'MEDIUM', 'HIGH']).toContain(shortTermPlan.riskLevel);
      expect(['LOW', 'MEDIUM', 'HIGH']).toContain(longTermPlan.riskLevel);
    });

    it('should generate alerts for high-risk budgets', async () => {
      // WHEN: Generating budget plan
      const plan = await service.generateBudgetPlan(36, 5); // Long term, low buffer

      // THEN: May have alerts for high-risk scenario
      if (plan.riskLevel === 'HIGH') {
        expect(plan.alerts).toBeDefined();
        expect(plan.alerts.length).toBeGreaterThan(0);
      }
    });
  });

  describe('analyzeTrend', () => {
    it('should analyze historical trends', async () => {
      // WHEN: Analyzing trend
      const trend = await service.analyzeTrend();

      // THEN: Should return trend analysis
      expect(trend).toBeDefined();
      expect(trend).toHaveProperty('direction');
      expect(trend).toHaveProperty('monthlyPercentageChange');
      expect(trend).toHaveProperty('projectedChange');
    });

    it('should identify trend direction', async () => {
      // WHEN: Analyzing trend
      const trend = await service.analyzeTrend();

      // THEN: Direction should be one of valid values
      expect(['up', 'down', 'flat']).toContain(trend.direction);
    });
  });

  describe('edge cases', () => {
    it('should handle zero forecast months gracefully', async () => {
      // WHEN: Forecasting 0 months
      const forecast = await service.forecastCosts(0);

      // THEN: Should return valid structure
      expect(forecast).toBeDefined();
      expect(forecast.monthlyProjections.length).toBe(0);
    });

    it('should handle very large forecast horizons', async () => {
      // WHEN: Forecasting 60 months (5 years)
      const forecast = await service.forecastCosts(60);

      // THEN: Should handle gracefully with confidence decline
      expect(forecast.monthlyProjections.length).toBe(60);
      expect(forecast.confidence).toBeLessThanOrEqual(0.7);
    });

    it('should require positive buffer percentage', async () => {
      // WHEN: Attempting to create budget with invalid buffer
      // This would typically throw or return zero buffer
      const plan = await service.generateBudgetPlan(12, 0);

      // THEN: Budget should equal forecast exactly
      expect(plan.totalBudget).toBe(plan.forecastedTotal);
      expect(plan.bufferAmount).toBe(0);
    });
  });
});
