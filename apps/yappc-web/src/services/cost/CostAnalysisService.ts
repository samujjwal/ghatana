/**
 * Cost Analysis Service - Orchestrates cost analysis operations
 *
 * <p><b>Purpose</b><br>
 * Core service for analyzing cloud costs, calculating trends, and identifying anomalies.
 * Coordinates between repository layer and utility functions to provide rich cost insights.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new CostAnalysisService(repository);
 * const analysis = await service.analyzeCosts(
 *   { start: new Date('2024-01'), end: new Date('2024-02') },
 *   { providers: ['AWS'], services: ['EC2'] }
 * );
 * console.log(`Total: ${analysis.totalCost}, Anomalies: ${analysis.anomalies.length}`);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of cost management domain. Implements use cases for:
 * - Cost analysis and breakdown
 * - Trend detection
 * - Anomaly identification
 * Coordinates with CostOptimizationService for recommendations.
 *
 * @doc.type class
 * @doc.purpose Orchestrates cost analysis operations
 * @doc.layer product
 * @doc.pattern Service
 */

import { CloudCostRepository, DateRange, CloudCostFilters } from '../../repositories/CloudCostRepository';
import { CloudCost } from '../../models/cost/CloudCost.entity';
import { CostAnalysis, createCostAnalysis } from '../../models/cost/CostAnalysis.dto';
import {
  calculateTotalCost,
  calculateAverageCost,
  calculateStandardDeviation,
  findOutliers,
} from '../../utils/cost/CostCalculations';

/**
 * Cost trend analysis result
 */
export interface CostTrend {
  readonly period: DateRange;
  readonly previousCost: number;
  readonly currentCost: number;
  readonly change: number;
  readonly percentChange: number;
  readonly trend: 'increasing' | 'decreasing' | 'stable';
}

/**
 * CostAnalysisService implementation
 */
export class CostAnalysisService {
  /**
   * Initialize service with repository
   * @param repository Data access layer for costs
   */
  constructor(private readonly repository: CloudCostRepository) {}

  /**
   * Analyze costs for specified period
   * @param period Date range for analysis
   * @param filters Optional cost filters (providers, services, tags)
   * @returns Comprehensive cost analysis
   */
  async analyzeCosts(
    period: DateRange,
    filters?: CloudCostFilters
  ): Promise<CostAnalysis> {
    // Fetch costs for period
    const costs = await this.repository.findByPeriod(period, filters);

    if (costs.length === 0) {
      return createCostAnalysis({
        period,
        totalCost: 0,
        costByService: {},
        costByProvider: {},
        costByTag: {},
        dailyTrend: [],
        anomalies: [],
        metrics: {
          averageDailyCost: 0,
          minDailyCost: 0,
          maxDailyCost: 0,
          standardDeviation: 0,
        },
        analyzedAt: new Date(),
      });
    }

    // Calculate aggregations
    const totalCost = this.calculateTotalCost(costs);
    const costByService = await this.repository.aggregateByDimension('service', period, filters);
    const costByProvider = await this.repository.aggregateByDimension('provider', period, filters);
    const tagAggregation = await this.repository.aggregateByDimension('tag', period, filters);
    // Transform tag aggregation to expected DTO format (tag value -> {tag value -> cost})
    const costByTag: Record<string, Record<string, number>> = {};
    for (const [tagValue, cost] of Object.entries(tagAggregation)) {
      costByTag[tagValue] = { [tagValue]: cost };
    }
    const dailyAggregates = await this.repository.getDailyAggregates(period, filters);
    const dailyTrend = dailyAggregates.map(({ date, totalCost: cost }) => ({ date, cost }));
    const anomalies = this.detectAnomalies(costs, dailyAggregates);
    const metrics = this.calculateMetrics(dailyAggregates);

    return createCostAnalysis({
      period,
      totalCost,
      costByService,
      costByProvider,
      costByTag,
      dailyTrend,
      anomalies,
      metrics,
      analyzedAt: new Date(),
    });
  }

  /**
   * Calculate cost trend between two periods
   * @param previousPeriod Previous period for comparison
   * @param currentPeriod Current period for comparison
   * @returns Cost trend analysis
   */
  async calculateTrend(
    previousPeriod: DateRange,
    currentPeriod: DateRange
  ): Promise<CostTrend> {
    const previousCosts = await this.repository.findByPeriod(previousPeriod);
    const currentCosts = await this.repository.findByPeriod(currentPeriod);

    const previousTotal = this.calculateTotalCost(previousCosts);
    const currentTotal = this.calculateTotalCost(currentCosts);

    const change = currentTotal - previousTotal;
    const percentChange = previousTotal === 0
      ? 0
      : (change / previousTotal) * 100;

    let trend: 'increasing' | 'decreasing' | 'stable';
    if (Math.abs(percentChange) < 5) {
      trend = 'stable';
    } else if (percentChange > 0) {
      trend = 'increasing';
    } else {
      trend = 'decreasing';
    }

    return {
      period: currentPeriod,
      previousCost: previousTotal,
      currentCost: currentTotal,
      change,
      percentChange,
      trend,
    };
  }

  /**
   * Calculate total cost from records
   * @param costs Array of cost records
   * @returns Sum of costs
   */
  private calculateTotalCost(costs: ReadonlyArray<CloudCost>): number {
    if (costs.length === 0) {
      return 0;
    }
    return costs.reduce((sum, cost) => sum + cost.cost, 0);
  }

  /**
   * Detect anomalies in cost data using statistical method
   * @param costs Array of cost records
   * @param dailyAggregates Pre-aggregated daily costs
   * @returns List of detected anomalies
   */
  private detectAnomalies(
    costs: ReadonlyArray<CloudCost>,
    dailyAggregates: Array<{ date: Date; totalCost: number }>
  ): ReadonlyArray<{
    readonly date: Date;
    readonly cost: number;
    readonly expectedCost: number;
    readonly deviation: number;
    readonly severity: 'LOW' | 'MEDIUM' | 'HIGH';
  }> {
    if (dailyAggregates.length < 3) {
      return [];
    }

    const costValues = dailyAggregates.map(d => d.totalCost);
    const average = calculateAverageCost(costValues);
    const stdDev = calculateStandardDeviation(costValues);

    // Find outliers (> 2 std devs from mean)
    const anomalies: Array<{
      readonly date: Date;
      readonly cost: number;
      readonly expectedCost: number;
      readonly deviation: number;
      readonly severity: 'LOW' | 'MEDIUM' | 'HIGH';
    }> = [];

    for (const { date, totalCost } of dailyAggregates) {
      const deviation = totalCost - average;
      const absDeviation = Math.abs(deviation);

      if (absDeviation > 2 * stdDev) {
        const percentDeviation = average === 0 ? 0 : (absDeviation / average) * 100;

        let severity: 'LOW' | 'MEDIUM' | 'HIGH';
        if (percentDeviation > 50) {
          severity = 'HIGH';
        } else if (percentDeviation > 25) {
          severity = 'MEDIUM';
        } else {
          severity = 'LOW';
        }

        anomalies.push({
          date,
          cost: totalCost,
          expectedCost: average,
          deviation: absDeviation,
          severity,
        });
      }
    }

    return anomalies;
  }

  /**
   * Calculate statistical metrics for cost data
   * @param dailyAggregates Pre-aggregated daily costs
   * @returns Cost metrics
   */
  private calculateMetrics(
    dailyAggregates: Array<{ date: Date; totalCost: number }>
  ): {
    readonly averageDailyCost: number;
    readonly maxDailyCost: number;
    readonly minDailyCost: number;
    readonly standardDeviation: number;
  } {
    if (dailyAggregates.length === 0) {
      return {
        averageDailyCost: 0,
        maxDailyCost: 0,
        minDailyCost: 0,
        standardDeviation: 0,
      };
    }

    const costValues = dailyAggregates.map(d => d.totalCost);
    const average = calculateAverageCost(costValues);
    const min = Math.min(...costValues);
    const max = Math.max(...costValues);
    const stdDev = calculateStandardDeviation(costValues);

    return {
      averageDailyCost: average,
      maxDailyCost: max,
      minDailyCost: min,
      standardDeviation: stdDev,
    };
  }
}
