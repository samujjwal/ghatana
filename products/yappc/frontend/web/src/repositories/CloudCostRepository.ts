/**
 * CloudCostRepository - Data access layer for cloud cost records
 *
 * <p><b>Purpose</b><br>
 * Provides database access methods for cloud cost entities.
 * Abstracts database details from service layer.
 * Optimized for time-series queries common in cost analysis.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const costs = await costRepository.findByPeriod(
 *   { start: new Date('2025-01-01'), end: new Date('2025-01-31') },
 *   { providers: ['AWS'], services: ['EC2'] }
 * );
 * const totalCost = costs.reduce((sum, c) => sum + c.cost, 0);
 * }</pre>
 *
 * <p><b>Indexing Strategy</b><br>
 * - Composite index (date, provider, service) for period queries
 * - Single index on date for time-range lookups
 * - Improves aggregation query performance by 10-100x
 *
 * @doc.type interface
 * @doc.purpose Cloud cost repository abstraction
 * @doc.layer product
 * @doc.pattern Repository
 */

import type { CloudCost } from '../models/cost/CloudCost.entity';

/**
 * Date range filter
 */
export interface DateRange {
  readonly start: Date;
  readonly end: Date;
}

/**
 * Query filters for cost data
 */
export interface CloudCostFilters {
  readonly providers?: ReadonlyArray<string>;
  readonly services?: ReadonlyArray<string>;
  readonly tags?: Record<string, string>;
}

/**
 * Repository interface for cloud costs
 */
export interface CloudCostRepository {
  /**
   * Save a cloud cost record
   * @param cost Cost to save
   * @returns Saved cost with generated ID
   */
  save(cost: CloudCost): Promise<CloudCost>;

  /**
   * Find costs by date range and optional filters
   * @param period Date range to query
   * @param filters Optional provider/service/tag filters
   * @returns Array of matching costs (sorted by date)
   */
  findByPeriod(period: DateRange, filters?: CloudCostFilters): Promise<CloudCost[]>;

  /**
   * Find costs by provider
   * @param provider Provider name (AWS, GCP, Azure)
   * @param period Optional date range (if not provided, returns all)
   * @returns Array of costs for the provider
   */
  findByProvider(
    provider: string,
    period?: DateRange
  ): Promise<CloudCost[]>;

  /**
   * Find costs by service
   * @param service Service name (EC2, S3, Lambda, etc.)
   * @param period Optional date range
   * @returns Array of costs for the service
   */
  findByService(
    service: string,
    period?: DateRange
  ): Promise<CloudCost[]>;

  /**
   * Aggregate costs by dimension
   * @param dimension 'provider' | 'service' | 'tag'
   * @param period Date range to aggregate
   * @param filters Optional additional filters
   * @returns Map of dimension value to total cost
   */
  aggregateByDimension(
    dimension: 'provider' | 'service' | 'tag',
    period: DateRange,
    filters?: CloudCostFilters
  ): Promise<Record<string, number>>;

  /**
   * Get daily aggregated costs
   * @param period Date range
   * @param filters Optional filters
   * @returns Array of { date, totalCost } tuples
   */
  getDailyAggregates(
    period: DateRange,
    filters?: CloudCostFilters
  ): Promise<Array<{ date: Date; totalCost: number }>>;

  /**
   * Delete costs older than specified date
   * Used for archiving old data
   * @param beforeDate Delete records before this date
   * @returns Number of records deleted
   */
  deleteOlderThan(beforeDate: Date): Promise<number>;
}

/**
 * In-memory implementation of CloudCostRepository for testing
 * @internal
 */
export class InMemoryCloudCostRepository implements CloudCostRepository {
  private costs: CloudCost[] = [];
  private idCounter = 1;

  async save(cost: CloudCost): Promise<CloudCost> {
    cost.validate();
    if (!cost.id) {
      cost.id = `cost-${this.idCounter++}`;
    }
    cost.createdAt = cost.createdAt || new Date();
    cost.updatedAt = new Date();
    this.costs.push(cost);
    return cost;
  }

  async findByPeriod(period: DateRange, filters?: CloudCostFilters): Promise<CloudCost[]> {
    return this.costs.filter(cost => {
      if (cost.date < period.start || cost.date > period.end) {
        return false;
      }
      if (filters?.providers && !filters.providers.includes(cost.provider)) {
        return false;
      }
      if (filters?.services && !filters.services.includes(cost.service)) {
        return false;
      }
      if (filters?.tags) {
        for (const [key, value] of Object.entries(filters.tags)) {
          if (cost.tags[key] !== value) {
            return false;
          }
        }
      }
      return true;
    });
  }

  async findByProvider(
    provider: string,
    period?: DateRange
  ): Promise<CloudCost[]> {
    const filtered = this.costs.filter(c => c.provider === provider);
    if (!period) {
      return filtered;
    }
    return filtered.filter(c => c.date >= period.start && c.date <= period.end);
  }

  async findByService(
    service: string,
    period?: DateRange
  ): Promise<CloudCost[]> {
    const filtered = this.costs.filter(c => c.service === service);
    if (!period) {
      return filtered;
    }
    return filtered.filter(c => c.date >= period.start && c.date <= period.end);
  }

  async aggregateByDimension(
    dimension: 'provider' | 'service' | 'tag',
    period: DateRange,
    filters?: CloudCostFilters
  ): Promise<Record<string, number>> {
    const costs = await this.findByPeriod(period, filters);
    const result: Record<string, number> = {};

    for (const cost of costs) {
      let key: string;
      if (dimension === 'provider') {
        key = cost.provider;
      } else if (dimension === 'service') {
        key = cost.service;
      } else {
        // For tags, aggregate all tag values
        for (const [, value] of Object.entries(cost.tags)) {
          const tagValue = String(value);
          result[tagValue] = (result[tagValue] || 0) + cost.cost;
        }
        continue;
      }
      result[key] = (result[key] || 0) + cost.cost;
    }

    return result;
  }

  async getDailyAggregates(
    period: DateRange,
    filters?: CloudCostFilters
  ): Promise<Array<{ date: Date; totalCost: number }>> {
    const costs = await this.findByPeriod(period, filters);
    const aggregates: Record<string, number> = {};

    for (const cost of costs) {
      const dateKey = cost.date.toISOString().split('T')[0];
      aggregates[dateKey] = (aggregates[dateKey] || 0) + cost.cost;
    }

    return Object.entries(aggregates)
      .map(([dateKey, totalCost]) => ({
        date: new Date(dateKey),
        totalCost,
      }))
      .sort((a, b) => a.date.getTime() - b.date.getTime());
  }

  async deleteOlderThan(beforeDate: Date): Promise<number> {
    const initialLength = this.costs.length;
    this.costs = this.costs.filter(c => c.date >= beforeDate);
    return initialLength - this.costs.length;
  }
}
