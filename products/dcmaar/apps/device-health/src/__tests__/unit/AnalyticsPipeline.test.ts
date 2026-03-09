/**
 * @fileoverview AnalyticsPipeline unit tests for web vital budget alerts.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { AnalyticsPipeline } from '../../analytics/AnalyticsPipeline';
import type { StorageAdapter, StorageChangeListener, StorageOptions, StorageQuota } from '../../core/interfaces/StorageAdapter';
import type { CollectedMetrics } from '../../analytics/AnalyticsPipeline';

class InMemoryStorageAdapter implements StorageAdapter {
  private data = new Map<string, unknown>();
  private listeners = new Set<StorageChangeListener>();

  async get<T>(key: string, _options?: StorageOptions): Promise<T | undefined> {
    return this.data.get(key) as T | undefined;
  }

  async getMany<T extends Record<string, unknown>>(keys: string[], _options?: StorageOptions): Promise<Partial<T>> {
    const result: Partial<T> = {};
    for (const key of keys) {
      if (this.data.has(key)) {
        (result as Record<string, unknown>)[key] = this.data.get(key);
      }
    }
    return result;
  }

  async getAll<T extends Record<string, unknown>>(_options?: StorageOptions): Promise<T> {
    const result: Record<string, unknown> = {};
    for (const [key, value] of this.data.entries()) {
      result[key] = value;
    }
    return result as T;
  }

  async set<T>(key: string, value: T, _options?: StorageOptions): Promise<void> {
    const oldValue = this.data.get(key);
    this.data.set(key, value);
    this.emitChange(key, oldValue, value);
  }

  async setMany<T extends Record<string, unknown>>(items: T, _options?: StorageOptions): Promise<void> {
    for (const [key, value] of Object.entries(items)) {
      await this.set(key, value);
    }
  }

  async remove(key: string, _options?: StorageOptions): Promise<void> {
    const oldValue = this.data.get(key);
    this.data.delete(key);
    this.emitChange(key, oldValue, undefined);
  }

  async removeMany(keys: string[], _options?: StorageOptions): Promise<void> {
    for (const key of keys) {
      await this.remove(key);
    }
  }

  async clear(_options?: StorageOptions): Promise<void> {
    const previous = Array.from(this.data.entries());
    this.data.clear();
    for (const [key, value] of previous) {
      this.emitChange(key, value, undefined);
    }
  }

  async has(key: string, _options?: StorageOptions): Promise<boolean> {
    return this.data.has(key);
  }

  async getQuota(_options?: StorageOptions): Promise<StorageQuota> {
    return {
      quota: Number.POSITIVE_INFINITY,
      used: this.data.size,
      remaining: Number.POSITIVE_INFINITY,
    };
  }

  onChange(listener: StorageChangeListener): void {
    this.listeners.add(listener);
  }

  offChange(listener: StorageChangeListener): void {
    this.listeners.delete(listener);
  }

  private emitChange(key: string, oldValue: unknown, newValue: unknown): void {
    const change = { [key]: { oldValue, newValue } };
    for (const listener of this.listeners) {
      listener(change, 'local');
    }
  }
}

describe('AnalyticsPipeline budget evaluation', () => {
  let pipeline: AnalyticsPipeline;

  beforeEach(() => {
    pipeline = new AnalyticsPipeline(new InMemoryStorageAdapter());
  });

  const createMetrics = (overrides: Partial<CollectedMetrics['page']>): CollectedMetrics => ({
    page: {
      url: 'https://example.com',
      title: 'Example',
      timestamp: Date.now(),
      lcp: 2500,
      fcp: 1200,
      cls: 0.1,
      fid: 80,
      inp: 150,
      tbt: 200,
      diagnostics: {
        longTaskCount: 1,
        totalBlockingTime: 180,
        maxInteractionLatency: 180,
      },
      ratings: {
        lcp: 'needs-improvement',
        cls: 'good',
        inp: 'good',
        fid: 'good',
      },
      overallRating: 'good',
      ...overrides,
    },
    resources: [],
    interactions: [],
  });

  it('should emit critical alerts when metrics exceed critical budgets', async () => {
    const metrics = createMetrics({
      lcp: 4200,
      inp: 620,
      cls: 0.32,
      tbt: 780,
      diagnostics: {
        longTaskCount: 6,
        totalBlockingTime: 840,
        maxInteractionLatency: 680,
      },
      ratings: {
        lcp: 'poor',
        cls: 'poor',
        inp: 'poor',
        fid: 'needs-improvement',
      },
      overallRating: 'poor',
    });

    const result = await pipeline.processMetrics(metrics);

    expect(result.summary.budgetViolations).toBe(4);
    expect(result.summary['budgetStatus:lcp']).toBe(2);
    expect(result.summary['budgetStatus:inp']).toBe(2);
    expect(result.summary['budgetStatus:cls']).toBe(2);
    expect(result.summary['budgetStatus:tbt']).toBe(2);
    expect(result.summary.longTaskCount).toBe(6);
    expect(result.summary.totalBlockingTime).toBe(840);
    expect(result.summary.maxInteractionLatency).toBe(680);
    expect(result.summary.overallVitalScore).toBe(2);

    expect(result.alerts).toBeDefined();
    expect(result.alerts).toHaveLength(4);
    for (const alert of result.alerts ?? []) {
      expect(alert.severity).toBe('critical');
    }
  });

  it('should emit warning alerts when metrics exceed warning budgets', async () => {
    const metrics = createMetrics({
      lcp: 3200,
      inp: 260,
      cls: 0.18,
      tbt: 450,
      ratings: {
        lcp: 'needs-improvement',
        cls: 'needs-improvement',
        inp: 'needs-improvement',
        fid: 'good',
      },
      overallRating: 'needs-improvement',
    });

    const result = await pipeline.processMetrics(metrics);

    expect(result.summary.budgetViolations).toBe(4);
    expect(result.summary['budgetStatus:lcp']).toBe(1);
    expect(result.summary['budgetStatus:inp']).toBe(1);
    expect(result.summary['budgetStatus:cls']).toBe(1);
    expect(result.summary['budgetStatus:tbt']).toBe(1);
    expect(result.summary.overallVitalScore).toBe(1);

    expect(result.alerts).toBeDefined();
    expect(result.alerts?.length).toBe(4);
    for (const alert of result.alerts ?? []) {
      expect(alert.severity).toBe('warning');
    }
  });

  it('should not emit alerts when metrics are within budgets', async () => {
    const metrics = createMetrics({
      lcp: 1800,
      inp: 120,
      cls: 0.05,
      tbt: 150,
      diagnostics: {
        longTaskCount: 0,
        totalBlockingTime: 0,
        maxInteractionLatency: 120,
      },
      ratings: {
        lcp: 'good',
        cls: 'good',
        inp: 'good',
        fid: 'good',
      },
      overallRating: 'good',
    });

    const result = await pipeline.processMetrics(metrics);

    expect(result.summary.budgetViolations).toBe(0);
    expect(result.summary['budgetStatus:lcp']).toBe(0);
    expect(result.summary['budgetStatus:inp']).toBe(0);
    expect(result.summary['budgetStatus:cls']).toBe(0);
    expect(result.summary['budgetStatus:tbt']).toBe(0);
    expect(result.alerts ?? []).toHaveLength(0);
  });
});
