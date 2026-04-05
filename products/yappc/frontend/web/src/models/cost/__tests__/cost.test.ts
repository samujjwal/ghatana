/**
 * Cost Model Tests
 *
 * Unit tests for CloudCost, CostAnalysis, CostRecommendation, and CostForecast.
 *
 * @doc.type test
 * @doc.purpose Unit tests for cost domain models
 * @doc.layer product
 * @doc.pattern Entity Tests
 */

import { describe, it, expect } from 'vitest';
import { CloudCost } from '../CloudCost.entity';
import { createCostAnalysis } from '../CostAnalysis.dto';
import { CostRecommendation } from '../CostRecommendation.entity';
import { createCostForecast } from '../CostForecast.dto';

// ============================================================================
// CloudCost Tests
// ============================================================================

describe('CloudCost', () => {
  function validCloudCost(): CloudCost {
    const cost = new CloudCost();
    cost.id = 'cc-1';
    cost.date = new Date('2025-01-15');
    cost.provider = 'AWS';
    cost.service = 'EC2';
    cost.cost = 125.5;
    cost.currency = 'USD';
    cost.tags = { Environment: 'Production' };
    cost.createdAt = new Date();
    cost.updatedAt = new Date();
    return cost;
  }

  describe('validate()', () => {
    it('returns true for a valid entity', () => {
      expect(validCloudCost().validate()).toBe(true);
    });

    it('throws when date is missing', () => {
      const cc = validCloudCost();
      cc.date = undefined as unknown as Date;
      expect(() => cc.validate()).toThrow('date is required');
    });

    it('throws when provider is empty', () => {
      const cc = validCloudCost();
      cc.provider = '  ';
      expect(() => cc.validate()).toThrow('provider is required');
    });

    it('throws when service is empty', () => {
      const cc = validCloudCost();
      cc.service = '';
      expect(() => cc.validate()).toThrow('service is required');
    });

    it('throws when cost is negative', () => {
      const cc = validCloudCost();
      cc.cost = -1;
      expect(() => cc.validate()).toThrow('cost must be non-negative');
    });

    it('allows cost of zero', () => {
      const cc = validCloudCost();
      cc.cost = 0;
      expect(cc.validate()).toBe(true);
    });

    it('throws when currency is missing', () => {
      const cc = validCloudCost();
      cc.currency = '';
      expect(() => cc.validate()).toThrow('currency is required');
    });

    it('throws when tags is not an object', () => {
      const cc = validCloudCost();
      cc.tags = null as unknown as Record<string, string>;
      expect(() => cc.validate()).toThrow('tags must be object');
    });
  });
});

// ============================================================================
// CostAnalysis DTO Tests
// ============================================================================

describe('createCostAnalysis()', () => {
  const period = { start: new Date('2025-01-01'), end: new Date('2025-01-31') };

  it('creates a valid analysis with minimum required fields', () => {
    const analysis = createCostAnalysis({ period, totalCost: 500 });
    expect(analysis.period).toEqual(period);
    expect(analysis.totalCost).toBe(500);
    expect(analysis.currency).toBe('USD');
  });

  it('uses provided currency', () => {
    const analysis = createCostAnalysis({
      period,
      totalCost: 200,
      currency: 'EUR',
    });
    expect(analysis.currency).toBe('EUR');
  });

  it('defaults empty breakdown maps', () => {
    const analysis = createCostAnalysis({ period, totalCost: 0 });
    expect(analysis.costByService).toEqual({});
    expect(analysis.costByProvider).toEqual({});
    expect(analysis.costByTag).toEqual({});
    expect(analysis.dailyTrend).toEqual([]);
    expect(analysis.anomalies).toEqual([]);
  });

  it('defaults metrics to zeros', () => {
    const analysis = createCostAnalysis({ period, totalCost: 0 });
    expect(analysis.metrics.averageDailyCost).toBe(0);
    expect(analysis.metrics.maxDailyCost).toBe(0);
    expect(analysis.metrics.standardDeviation).toBe(0);
  });

  it('throws when period is missing', () => {
    expect(() => createCostAnalysis({ totalCost: 100 })).toThrow(
      'period is required'
    );
  });

  it('throws when totalCost is negative', () => {
    expect(() => createCostAnalysis({ period, totalCost: -1 })).toThrow(
      'totalCost must be non-negative'
    );
  });

  it('throws when totalCost is undefined', () => {
    expect(() => createCostAnalysis({ period })).toThrow(
      'totalCost must be non-negative'
    );
  });

  it('preserves provided metrics', () => {
    const metrics = {
      averageDailyCost: 16.13,
      maxDailyCost: 25,
      minDailyCost: 8,
      standardDeviation: 4.5,
    };
    const analysis = createCostAnalysis({ period, totalCost: 500, metrics });
    expect(analysis.metrics).toEqual(metrics);
  });
});

// ============================================================================
// CostRecommendation Tests
// ============================================================================

describe('CostRecommendation', () => {
  function validRecommendation(): CostRecommendation {
    const rec = new CostRecommendation();
    rec.id = 'recom-1';
    rec.title = 'Reserve EC2 instances';
    rec.description =
      'Purchase 1-year reserved instances for consistent workloads';
    rec.savings = 5000;
    rec.annualSavings = 60000;
    rec.effort = 'LOW';
    rec.implementation =
      'Navigate to AWS Console → EC2 → Reserved Instances and purchase';
    rec.estimatedMonthsSavings = 12;
    rec.status = 'SUGGESTED';
    rec.resourceIds = ['ec2-i-abc123'];
    rec.tags = { category: 'Reserved Instances', service: 'EC2' };
    rec.suggestedAt = new Date();
    rec.createdAt = new Date();
    rec.updatedAt = new Date();
    return rec;
  }

  describe('validate()', () => {
    it('returns true for a valid entity', () => {
      expect(validRecommendation().validate()).toBe(true);
    });

    it('throws when title is empty', () => {
      const rec = validRecommendation();
      rec.title = '   ';
      expect(() => rec.validate()).toThrow('title is required');
    });

    it('throws when savings is negative', () => {
      const rec = validRecommendation();
      rec.savings = -1;
      expect(() => rec.validate()).toThrow('savings must be non-negative');
    });

    it('allows savings of zero', () => {
      const rec = validRecommendation();
      rec.savings = 0;
      expect(rec.validate()).toBe(true);
    });

    it('throws when effort is missing', () => {
      const rec = validRecommendation();
      rec.effort = undefined as unknown as 'LOW';
      expect(() => rec.validate()).toThrow('effort is required');
    });

    it('throws when status is missing', () => {
      const rec = validRecommendation();
      rec.status = undefined as unknown as 'SUGGESTED';
      expect(() => rec.validate()).toThrow('status is required');
    });

    it('throws when implementation steps are empty', () => {
      const rec = validRecommendation();
      rec.implementation = '';
      expect(() => rec.validate()).toThrow('implementation is required');
    });
  });
});

// ============================================================================
// CostForecast DTO Tests
// ============================================================================

describe('createCostForecast()', () => {
  const period = { start: new Date('2025-02-01'), end: new Date('2025-07-31') };

  it('creates a valid forecast with minimum fields', () => {
    const forecast = createCostForecast({
      period,
      projectedCost: 10000,
      confidence: 0.85,
    });
    expect(forecast.period).toEqual(period);
    expect(forecast.projectedCost).toBe(10000);
    expect(forecast.confidence).toBe(0.85);
    expect(forecast.currency).toBe('USD');
  });

  it('calculates default confidence intervals from projectedCost', () => {
    const forecast = createCostForecast({
      period,
      projectedCost: 1000,
      confidence: 0.9,
    });
    expect(forecast.confidenceLower80).toBeCloseTo(900);
    expect(forecast.confidenceUpper80).toBeCloseTo(1100);
    expect(forecast.confidenceLower95).toBeCloseTo(800);
    expect(forecast.confidenceUpper95).toBeCloseTo(1200);
  });

  it('uses provided confidence intervals', () => {
    const forecast = createCostForecast({
      period,
      projectedCost: 1000,
      confidence: 0.9,
      confidenceLower80: 850,
      confidenceUpper80: 1150,
      confidenceLower95: 750,
      confidenceUpper95: 1250,
    });
    expect(forecast.confidenceLower80).toBe(850);
    expect(forecast.confidenceUpper80).toBe(1150);
  });

  it('defaults to USD, empty arrays, and zero growth rate', () => {
    const forecast = createCostForecast({
      period,
      projectedCost: 500,
      confidence: 0.7,
    });
    expect(forecast.currency).toBe('USD');
    expect(forecast.monthlyProjections).toEqual([]);
    expect(forecast.factors).toEqual([]);
    expect(forecast.risks).toEqual([]);
    expect(forecast.growthRate).toBe(0);
  });

  it('throws when period is missing', () => {
    expect(() =>
      createCostForecast({ projectedCost: 1000, confidence: 0.8 })
    ).toThrow('period is required');
  });

  it('throws when projectedCost is negative', () => {
    expect(() =>
      createCostForecast({ period, projectedCost: -100, confidence: 0.8 })
    ).toThrow('projectedCost must be non-negative');
  });

  it('throws when confidence is out of range', () => {
    expect(() =>
      createCostForecast({ period, projectedCost: 500, confidence: 1.5 })
    ).toThrow('confidence must be between 0 and 1');
    expect(() =>
      createCostForecast({ period, projectedCost: 500, confidence: -0.1 })
    ).toThrow('confidence must be between 0 and 1');
  });

  it('preserves seasonality string when provided', () => {
    const forecast = createCostForecast({
      period,
      projectedCost: 500,
      confidence: 0.8,
      seasonality: 'Higher in Q4',
    });
    expect(forecast.seasonality).toBe('Higher in Q4');
  });
});
