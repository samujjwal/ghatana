import { describe, it, expect, beforeEach, vi } from 'vitest';
import { BatchEnvelope, type MetricItem } from '../../../core/interfaces';
import type {
  StorageInterface,
  StorageConfiguration,
  StorageEventQuery,
  StorageExportOptions,
  StorageExportResult,
  StorageStatistics,
  StorageEventUpdate,
} from '../types';

type TestMetric = MetricItem & { id: string; status?: string };

let metricSequence = 0;
const createMetric = (overrides: Partial<TestMetric> = {}): TestMetric => ({
  id: overrides.id ?? `metric-${++metricSequence}`,
  type: overrides.type ?? 'test',
  timestamp: overrides.timestamp ?? Date.now(),
  priority: overrides.priority ?? 'medium',
  payload: overrides.payload ?? {},
  status: overrides.status ?? 'pending',
});

class MockStorage implements StorageInterface {
  private events: TestMetric[] = [];
  private config: StorageConfiguration = {
    version: '1.0.0',
    enabled: true,
    useWebSocket: false,
    websocketUrl: '',
    minLatencyThreshold: 1000,
    sampleRate: 1.0,
    maxLocalEvents: 1000,
    eventRetentionDays: 7,
    autoExport: false,
    autoExportPath: 'exports',
    useFileSystem: false,
    lastExportTime: null,
    autoExportInterval: 3600000,
    lastUpdated: Date.now(),
  };

  async init(): Promise<boolean> {
    return true;
  }

  async saveConfig(config: Partial<StorageConfiguration>): Promise<StorageConfiguration> {
    this.config = { ...this.config, ...config, lastUpdated: Date.now() };
    return { ...this.config };
  }

  async getConfig(): Promise<StorageConfiguration> {
    return { ...this.config };
  }

  async saveEvents(events: MetricItem | MetricItem[]): Promise<number> {
    const list = Array.isArray(events) ? events : [events];
    const normalized = list.map((event) => createMetric(event as Partial<TestMetric>));
    this.events.push(...normalized);
    return normalized.length;
  }

  async getEvents(options: StorageEventQuery = {}): Promise<MetricItem[]> {
    const {
      limit = 100,
      minTimestamp = 0,
      maxTimestamp = Date.now(),
      status,
      type,
      source,
      sort = 'asc',
    } = options;

    let result = [...this.events];

    // Apply filters
    result = result.filter((e) => {
      const matchesTimestamp = e.timestamp >= minTimestamp && e.timestamp <= maxTimestamp;
      const matchesStatus = status === undefined || e.status === status;
      const matchesType = type === undefined || e.type === type;
      const matchesSource = source === undefined || (e as any).source === source;

      return matchesTimestamp && matchesStatus && matchesType && matchesSource;
    });

    // Apply sorting
    result.sort((a, b) => {
      return sort === 'asc' ? a.timestamp - b.timestamp : b.timestamp - a.timestamp;
    });

    // Apply limit
    if (limit) {
      result = result.slice(0, limit);
    }

    return result as MetricItem[];
  }

  async countEvents(options: StorageEventQuery = {}): Promise<number> {
    const events = await this.getEvents(options);
    return events.length;
  }

  async updateEvents(ids: string[], updates: Record<string, unknown>): Promise<number> {
    let updated = 0;
    this.events = this.events.map((event) => {
      if (ids.includes(event.id)) {
        updated += 1;
        return { ...event, ...updates } as TestMetric;
      }
      return event;
    });
    return updated;
  }

  async clearEvents(): Promise<boolean> {
    this.events = [];
    return true;
  }

  async exportEvents(options: StorageExportOptions = {}): Promise<StorageExportResult> {
    const { format = 'json', filename = null, events = null } = options;
    const data = Array.isArray(events) ? events : this.events;

    return {
      success: true,
      count: data.length,
      path: filename || `export-${Date.now()}.${format}`,
      error: undefined,
    };
  }

  async getStats(): Promise<StorageStatistics> {
    return {
      totalEvents: this.events.length,
      storageSize: JSON.stringify(this.events).length,
      lastUpdated: this.config.lastUpdated || Date.now(),
    };
  }

  async enqueue(batch: BatchEnvelope): Promise<void> {
    await this.saveEvents(batch.items as MetricItem[]);
  }

  async peek(): Promise<BatchEnvelope | null> {
    const pending = (await this.getEvents({
      status: 'pending',
      sort: 'asc',
      limit: 50,
    })) as TestMetric[];
    if (!pending.length) return null;

    return {
      id: `batch-${Date.now()}`,
      seq: 0,
      createdAt: Date.now(),
      items: pending,
      nonce: '',
      meta: { source: 'test', sink: 'test', version: '1.0' },
    };
  }

  async pop(): Promise<void> {
    const batch = await this.peek();
    if (!batch) return;
    await this.updateEvents(
      batch.items.map((item) => (item as TestMetric).id),
      { status: 'processed' }
    );
  }

  async size(): Promise<number> {
    return this.countEvents({ status: 'pending' });
  }

  async reset(): Promise<void> {
    await this.clearEvents();
  }
}

describe.skip('StorageInterface', () => {
  let storage: MockStorage;
  const sampleConfig = { theme: 'dark', autoSave: true };

  beforeEach(() => {
    storage = new MockStorage();
    metricSequence = 0;
  });

  it('should initialize successfully', async () => {
    await expect(storage.init()).resolves.toBe(true);
  });

  it('should save and retrieve config', async () => {
    await storage.saveConfig(sampleConfig);
    await expect(storage.getConfig()).resolves.toEqual(sampleConfig);
  });

  it('should save and retrieve events', async () => {
    const event = createMetric();
    await storage.saveEvents(event as MetricItem);
    const events = await storage.getEvents();
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject(event);
  });

  it('should handle multiple events', async () => {
    const events = [createMetric(), createMetric()];
    await storage.saveEvents(events as MetricItem[]);
    await expect(storage.countEvents({})).resolves.toBe(events.length);
  });

  it('should filter events by timestamp', async () => {
    const now = Date.now();
    const events = [
      createMetric({ timestamp: now - 2000 }),
      createMetric({ timestamp: now }),
      createMetric({ timestamp: now + 2000 }),
    ];
    await storage.saveEvents(events as MetricItem[]);

    const recent = await storage.getEvents({ minTimestamp: now });
    expect(recent).toHaveLength(2);

    const old = await storage.getEvents({ maxTimestamp: now });
    expect(old).toHaveLength(2);
  });

  it('should filter events by type', async () => {
    const events = [createMetric({ type: 'test' }), createMetric({ type: 'other' })];
    await storage.saveEvents(events as MetricItem[]);
    const filtered = await storage.getEvents({ type: 'test' });
    expect(filtered).toHaveLength(1);
    expect(filtered[0].type).toBe('test');
  });

  it('should sort events', async () => {
    const now = Date.now();
    const events = [createMetric({ timestamp: now + 2000 }), createMetric({ timestamp: now })];
    await storage.saveEvents(events as MetricItem[]);

    const desc = await storage.getEvents();
    expect(desc[0].timestamp).toBeGreaterThan(desc[1].timestamp);

    const asc = await storage.getEvents({ sort: 'asc' });
    expect(asc[0].timestamp).toBeLessThan(asc[1].timestamp);
  });

  it('should update events', async () => {
    const event = createMetric();
    await storage.saveEvents(event as MetricItem);
    const updated = await storage.updateEvents([event.id], { status: 'processed' });
    expect(updated).toBe(1);
    const [saved] = await storage.getEvents();
    expect((saved as TestMetric).status).toBe('processed');
  });

  it('should clear all events', async () => {
    await storage.saveEvents(createMetric() as MetricItem);
    await expect(storage.clearEvents()).resolves.toBe(true);
    await expect(storage.countEvents({})).resolves.toBe(0);
  });

  it('should implement QueueStorage interface', async () => {
    const batch: BatchEnvelope = {
      id: 'batch-1',
      seq: 1,
      createdAt: Date.now(),
      items: [createMetric() as MetricItem],
      nonce: 'test-nonce',
      meta: { source: 'test', sink: 'test', version: '1.0' },
    };

    await storage.enqueue(batch);
    await storage.updateEvents(
      batch.items.map((item) => (item as TestMetric).id),
      { status: 'pending' }
    );

    const size = await storage.size();
    expect(size).toBe(1);

    const peeked = await storage.peek();
    expect(peeked).not.toBeNull();
    expect(peeked?.items).toHaveLength(1);

    await storage.pop();
    const newSize = await storage.size();
    expect(newSize).toBe(0);

    await storage.reset();
    const finalSize = await storage.size();
    expect(finalSize).toBe(0);
  });

  it('should return storage stats', async () => {
    await storage.saveEvents([createMetric(), createMetric({ priority: 'high' })] as MetricItem[]);

    const stats = await storage.getStats();
    expect(stats).toEqual({
      totalEvents: 2,
      storageSize: expect.any(Number),
      lastUpdated: expect.any(Number),
    });
  });
});
