/**
 * Historical Data Service
 * 
 * Manages historical data queries, aggregation, and caching with:
 * - Time-series data aggregation
 * - Query optimization
 * - Result caching
 * - Data sampling for large ranges
 */

import type { HistoricalQuery, HistoricalResult, TimeSeriesData, DataPoint } from '../types';
import { sampleData } from '../utils';

/**
 * Cache entry for historical queries
 */
interface CacheEntry {
  query: HistoricalQuery;
  result: HistoricalResult;
  timestamp: number;
  ttl: number;
}

/**
 * Historical data service for querying and aggregating time-series data
 */
export class HistoricalDataService {
  private cache: Map<string, CacheEntry> = new Map();
  private readonly defaultCacheTTL = 5 * 60 * 1000; // 5 minutes
  private readonly maxCacheSize = 100;

  /**
   * Query historical data with caching and aggregation
   */
  async query(query: HistoricalQuery): Promise<HistoricalResult> {
    // Check cache first
    const cacheKey = this.getCacheKey(query);
    const cached = this.getFromCache(cacheKey);
    if (cached) {
      return cached;
    }

    // Fetch data (this would typically call an API)
    const data = await this.fetchData(query);

    // Apply aggregation if specified
    const aggregatedData = query.aggregation
      ? this.aggregateData(data, query)
      : data;

    // Sample data if needed
    const sampledData = aggregatedData.map(series => ({
      ...series,
      data: query.interval
        ? this.aggregateByInterval(series.data, query.interval)
        : series.data,
    }));

    const result: HistoricalResult = {
      query,
      data: sampledData,
      metadata: {
        totalPoints: data.reduce((sum, series) => sum + series.data.length, 0),
        samplingRate: query.interval,
        cacheHit: false,
      },
    };

    // Cache the result
    this.addToCache(cacheKey, result);

    return result;
  }

  /**
   * Fetch data from storage (placeholder - implement actual data fetching)
   */
  private async fetchData(query: HistoricalQuery): Promise<TimeSeriesData[]> {
    // This would typically call an API or database
    // For now, return mock data
    return [
      {
        id: query.metric,
        name: query.metric,
        data: this.generateMockData(query.startTime, query.endTime),
      },
    ];
  }

  /**
   * Generate mock data for testing
   */
  private generateMockData(startTime: number, endTime: number): DataPoint[] {
    const data: DataPoint[] = [];
    const interval = 60000; // 1 minute
    
    for (let time = startTime; time <= endTime; time += interval) {
      data.push({
        timestamp: time,
        value: Math.random() * 100,
      });
    }

    return data;
  }

  /**
   * Aggregate data by specified method
   */
  private aggregateData(
    data: TimeSeriesData[],
    query: HistoricalQuery
  ): TimeSeriesData[] {
    if (!query.aggregation) return data;

    return data.map(series => ({
      ...series,
      data: this.aggregatePoints(series.data, query.aggregation!),
    }));
  }

  /**
   * Aggregate data points by method
   */
  private aggregatePoints(
    points: DataPoint[],
    method: 'avg' | 'sum' | 'min' | 'max' | 'count'
  ): DataPoint[] {
    if (points.length === 0) return [];

    const values = points.map(p => p.value);
    let aggregatedValue: number;

    switch (method) {
      case 'avg':
        aggregatedValue = values.reduce((sum, v) => sum + v, 0) / values.length;
        break;
      case 'sum':
        aggregatedValue = values.reduce((sum, v) => sum + v, 0);
        break;
      case 'min':
        aggregatedValue = Math.min(...values);
        break;
      case 'max':
        aggregatedValue = Math.max(...values);
        break;
      case 'count':
        aggregatedValue = values.length;
        break;
    }

    return [
      {
        timestamp: points[0].timestamp,
        value: aggregatedValue,
      },
    ];
  }

  /**
   * Aggregate data by time interval
   */
  private aggregateByInterval(points: DataPoint[], interval: number): DataPoint[] {
    if (points.length === 0) return [];

    const buckets = new Map<number, DataPoint[]>();

    // Group points into buckets
    points.forEach(point => {
      const bucketTime = Math.floor(point.timestamp / interval) * interval;
      if (!buckets.has(bucketTime)) {
        buckets.set(bucketTime, []);
      }
      buckets.get(bucketTime)!.push(point);
    });

    // Aggregate each bucket
    return Array.from(buckets.entries())
      .map(([timestamp, bucketPoints]) => ({
        timestamp,
        value: bucketPoints.reduce((sum, p) => sum + p.value, 0) / bucketPoints.length,
      }))
      .sort((a, b) => a.timestamp - b.timestamp);
  }

  /**
   * Generate cache key from query
   */
  private getCacheKey(query: HistoricalQuery): string {
    return JSON.stringify({
      metric: query.metric,
      startTime: query.startTime,
      endTime: query.endTime,
      aggregation: query.aggregation,
      interval: query.interval,
      filters: query.filters,
    });
  }

  /**
   * Get result from cache
   */
  private getFromCache(key: string): HistoricalResult | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    // Check if cache entry is still valid
    if (Date.now() - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }

    return {
      ...entry.result,
      metadata: {
        ...entry.result.metadata,
        cacheHit: true,
      },
    };
  }

  /**
   * Add result to cache
   */
  private addToCache(key: string, result: HistoricalResult): void {
    // Implement LRU eviction if cache is full
    if (this.cache.size >= this.maxCacheSize) {
      const firstKey = this.cache.keys().next().value as string | undefined;
      if (firstKey) {
        this.cache.delete(firstKey);
      }
    }

    this.cache.set(key, {
      query: result.query,
      result,
      timestamp: Date.now(),
      ttl: this.defaultCacheTTL,
    });
  }

  /**
   * Clear cache
   */
  clearCache(): void {
    this.cache.clear();
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): { size: number; maxSize: number } {
    return {
      size: this.cache.size,
      maxSize: this.maxCacheSize,
    };
  }
}

// Export singleton instance
export const historicalDataService = new HistoricalDataService();
