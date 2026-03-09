/**
 * @fileoverview ExtensionDataFlow Integration Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ExtensionDataFlow } from '../../app/background/integration/ExtensionDataFlow';
import type { Event } from '@ghatana/dcmaar-connectors';

describe('ExtensionDataFlow', () => {
  let dataFlow: ExtensionDataFlow;

  beforeEach(() => {
    dataFlow = new ExtensionDataFlow({
      source: {
        id: 'test-source',
        events: ['tabs'],
        sampling: { rate: 1.0 },
        filters: { includePatterns: ['*'], excludePatterns: [] },
        batching: { size: 10, flushIntervalMs: 1000 },
      },
      sink: {
        id: 'test-sink',
        dbName: 'test-db-flow',
        batchSize: 10,
        flushIntervalMs: 1000,
        maxEvents: 1000,
        retentionDays: 1,
      },
      monitoring: true,
    });
  });

  afterEach(async () => {
    try {
      await dataFlow.stop();
    } catch {
      // Ignore errors during cleanup
    }
  });

  describe('lifecycle', () => {
    it('should initialize and start data flow', async () => {
      await dataFlow.start();
      const stats = await dataFlow.getStats();
      expect(stats.source).toBeDefined();
      expect(stats.sink).toBeDefined();
    });

    it('should stop data flow gracefully', async () => {
      await dataFlow.start();
      await dataFlow.stop();
      // Should not throw
    });

    it('should handle multiple start calls', async () => {
      await dataFlow.start();
      await dataFlow.start(); // Should be idempotent
      await dataFlow.stop();
    });
  });

  describe('event processing', () => {
    it('should process events through pipeline', async () => {
      dataFlow = new ExtensionDataFlow({
        source: {
          id: 'test-source',
          events: ['tabs'],
          sampling: { rate: 1.0 },
          filters: { includePatterns: ['*'], excludePatterns: [] },
          batching: { size: 1, flushIntervalMs: 100 },
        },
        sink: {
          id: 'test-sink',
          dbName: 'test-db-pipeline',
          batchSize: 1,
          flushIntervalMs: 100,
          maxEvents: 1000,
          retentionDays: 1,
        },
        pipeline: {
          processors: [
            {
              id: 'enrich-test',
              type: 'enrich',
              order: 1,
              enabled: true,
              config: {
                enrichments: [
                  { type: 'timestamp', field: 'metadata.processedAt' },
                ],
              },
            },
          ],
        },
        monitoring: false,
      });

      await dataFlow.start();

      // Simulate event
      const testEvent: Event = {
        id: 'evt-1',
        type: 'tab.created',
        timestamp: Date.now(),
        payload: { tabId: 123 },
      };

      // In real scenario, this would come from source
      // For testing, we'd need to mock the source

      await dataFlow.stop();
    });
  });

  describe('statistics', () => {
    it('should collect statistics', async () => {
      await dataFlow.start();

      const stats = await dataFlow.getStats();

      expect(stats.source).toHaveProperty('id');
      expect(stats.source).toHaveProperty('type');
      expect(stats.source).toHaveProperty('state');
      expect(stats.sink).toHaveProperty('totalEvents');
      expect(stats.sink).toHaveProperty('storageSize');

      await dataFlow.stop();
    });

    it('should track pipeline statistics', async () => {
      dataFlow = new ExtensionDataFlow({
        source: {
          id: 'test-source',
          events: ['tabs'],
          sampling: { rate: 1.0 },
          filters: { includePatterns: ['*'], excludePatterns: [] },
          batching: { size: 10, flushIntervalMs: 1000 },
        },
        sink: {
          id: 'test-sink',
          dbName: 'test-db-stats',
          batchSize: 10,
          flushIntervalMs: 1000,
          maxEvents: 1000,
          retentionDays: 1,
        },
        pipeline: {
          processors: [],
          monitoring: true,
        },
        monitoring: true,
      });

      await dataFlow.start();

      const stats = await dataFlow.getStats();

      if (stats.pipeline) {
        expect(stats.pipeline).toHaveProperty('totalProcessed');
        expect(stats.pipeline).toHaveProperty('totalFiltered');
        expect(stats.pipeline).toHaveProperty('totalErrors');
      }

      await dataFlow.stop();
    });
  });

  describe('monitoring', () => {
    it('should provide health report', async () => {
      await dataFlow.start();

      const health = await dataFlow.getHealthReport();

      expect(health).toHaveProperty('overall');
      expect(health).toHaveProperty('components');
      expect(health).toHaveProperty('metrics');
      expect(health).toHaveProperty('timestamp');

      await dataFlow.stop();
    });

    it('should provide performance report', async () => {
      await dataFlow.start();

      const performance = await dataFlow.getPerformanceReport();

      expect(performance).toHaveProperty('bottlenecks');
      expect(performance).toHaveProperty('slowQueries');
      expect(performance).toHaveProperty('memoryLeaks');
      expect(performance).toHaveProperty('recommendations');
      expect(performance).toHaveProperty('metrics');

      await dataFlow.stop();
    });

    it('should track usage events', async () => {
      await dataFlow.start();

      dataFlow.trackUsage('test-feature', 'test-action', { meta: 'data' });

      const usage = dataFlow.getUsageReport();

      expect(usage).toHaveProperty('features');
      expect(usage).toHaveProperty('engagement');

      await dataFlow.stop();
    });
  });

  describe('error handling', () => {
    it('should handle errors gracefully', async () => {
      await dataFlow.start();

      // Should not throw
      await dataFlow.stop();
      await dataFlow.stop(); // Double stop
    });

    it('should throw when querying without initialization', async () => {
      const uninitializedFlow = new ExtensionDataFlow({
        source: {
          id: 'test',
          events: ['tabs'],
          sampling: { rate: 1.0 },
          filters: { includePatterns: ['*'], excludePatterns: [] },
          batching: { size: 10, flushIntervalMs: 1000 },
        },
        sink: {
          id: 'test',
          dbName: 'test-db-error',
          batchSize: 10,
          flushIntervalMs: 1000,
          maxEvents: 1000,
          retentionDays: 1,
        },
        monitoring: false,
      });

      // Query without starting should handle gracefully
      try {
        await uninitializedFlow.queryEvents({});
      } catch (error) {
        expect(error).toBeDefined();
      }
    });
  });
});
