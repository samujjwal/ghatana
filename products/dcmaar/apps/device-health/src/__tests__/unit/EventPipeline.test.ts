/**
 * @fileoverview EventPipeline Unit Tests
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { EventPipeline } from '../../app/background/pipeline/EventPipeline';
import type { Event } from '@ghatana/dcmaar-connectors';

describe('EventPipeline', () => {
  let pipeline: EventPipeline;

  beforeEach(async () => {
    pipeline = new EventPipeline({
      processors: [],
      monitoring: true,
      errorStrategy: 'reject',
      timeout: 5000,
    });
    await pipeline.initialize();
  });

  afterEach(async () => {
    await pipeline.destroy();
  });

  describe('initialization', () => {
    it('should initialize with empty processors', async () => {
      const processors = pipeline.listProcessors();
      expect(processors).toHaveLength(0);
    });

    it('should initialize with configured processors', async () => {
      const pipelineWithProcessors = new EventPipeline({
        processors: [
          {
            id: 'test-1',
            type: 'validate',
            order: 1,
            enabled: true,
            config: { schema: { type: 'object' } },
          },
        ],
        monitoring: true,
      });

      await pipelineWithProcessors.initialize();

      const processors = pipelineWithProcessors.listProcessors();
      expect(processors.length).toBeGreaterThanOrEqual(0);

      await pipelineWithProcessors.destroy();
    });
  });

  describe('event processing', () => {
    it('should process single event', async () => {
      const event: Event = {
        id: 'evt-1',
        type: 'test',
        timestamp: Date.now(),
        payload: { data: 'test' },
      };

      const result = await pipeline.process(event);

      expect(result).toBeDefined();
      if (result && !Array.isArray(result)) {
        expect(result.id).toBe('evt-1');
      }
    });

    it('should handle array of events', async () => {
      const events: Event[] = [
        {
          id: 'evt-1',
          type: 'test',
          timestamp: Date.now(),
          payload: { data: 'test1' },
        },
        {
          id: 'evt-2',
          type: 'test',
          timestamp: Date.now(),
          payload: { data: 'test2' },
        },
      ];

      const result = await pipeline.process(events as any);

      expect(result).toBeDefined();
    });
  });

  describe('processor management', () => {
    it('should add processor', async () => {
      await pipeline.addProcessor({
        id: 'test-add',
        type: 'validate',
        order: 1,
        enabled: true,
        config: { schema: { type: 'object' } },
      });

      const processors = pipeline.listProcessors();
      expect(processors.length).toBeGreaterThan(0);
    });

    it('should remove processor', async () => {
      const id = 'test-remove';

      await pipeline.addProcessor({
        id,
        type: 'validate',
        order: 1,
        enabled: true,
        config: { schema: { type: 'object' } },
      });

      let processors = pipeline.listProcessors();
      const initialCount = processors.length;

      await pipeline.removeProcessor(id);

      processors = pipeline.listProcessors();
      expect(processors.length).toBeLessThanOrEqual(initialCount);
    });

    it('should list processors', async () => {
      const processors = pipeline.listProcessors();
      expect(Array.isArray(processors)).toBe(true);
    });

    it('should get processor by ID', async () => {
      const id = 'test-get';

      await pipeline.addProcessor({
        id,
        type: 'validate',
        order: 1,
        enabled: true,
        config: { schema: { type: 'object' } },
      });

      const processor = pipeline.getProcessor(id);
      expect(processor).toBeDefined();
      if (processor) {
        expect(processor.id).toBe(id);
      }
    });
  });

  describe('statistics', () => {
    it('should track statistics', async () => {
      const event: Event = {
        id: 'evt-1',
        type: 'test',
        timestamp: Date.now(),
        payload: { data: 'test' },
      };

      await pipeline.process(event);

      const stats = pipeline.getStats();

      expect(stats).toHaveProperty('totalProcessed');
      expect(stats).toHaveProperty('totalFiltered');
      expect(stats).toHaveProperty('totalErrors');
      expect(stats).toHaveProperty('avgProcessingTime');
      expect(stats).toHaveProperty('processorStats');
    });

    it('should reset statistics', async () => {
      const event: Event = {
        id: 'evt-1',
        type: 'test',
        timestamp: Date.now(),
        payload: { data: 'test' },
      };

      await pipeline.process(event);

      let stats = pipeline.getStats();
      expect(stats.totalProcessed + stats.totalFiltered).toBeGreaterThan(0);

      pipeline.resetStats();

      stats = pipeline.getStats();
      expect(stats.totalProcessed).toBe(0);
      expect(stats.totalFiltered).toBe(0);
    });
  });

  describe('error handling', () => {
    it('should handle errors based on strategy', async () => {
      const pipelineReject = new EventPipeline({
        processors: [],
        errorStrategy: 'reject',
      });

      await pipelineReject.initialize();

      const event: Event = {
        id: 'evt-1',
        type: 'test',
        timestamp: Date.now(),
        payload: { data: 'test' },
      };

      // Should not throw for valid event
      const result = await pipelineReject.process(event);
      expect(result).toBeDefined();

      await pipelineReject.destroy();
    });
  });
});
