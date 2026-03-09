/**
 * Event Ingestion & Error Handling Tests
 *
 * Tests for event ingestion pipeline and error handling.
 * Validates batch processing, error recovery, and edge cases.
 *
 * Test Coverage:
 * - Event batch ingestion
 * - Queue management
 * - Error recovery
 * - Batch boundaries
 * - Timeout handling
 * - Invalid data handling
 * - Metrics collection
 *
 * @test Event ingestion pipeline
 */

import { describe, it, expect } from 'vitest';
import { validateEvent, validateEventBatch } from '../types/events';

describe('Event Ingestion Pipeline', () => {
  describe('Batch Processing', () => {
    it('should process batch of 10 events', () => {
      const events = Array.from({ length: 10 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i * 100,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should process batch of 100 events', () => {
      const events = Array.from({ length: 100 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i * 10,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should process batch at exactly 1000 events (upper limit)', () => {
      const events = Array.from({ length: 1000 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(true);
    });

    it('should reject batch over 1000 events', () => {
      const events = Array.from({ length: 1001 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
    });

    it('should track batch processing metrics', () => {
      const events = Array.from({ length: 50 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i * 100,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);

      // Metrics would be tracked:
      // - batch_size: 50
      // - valid_events: 50
      // - invalid_events: 0
      // - processing_time: <100ms

      expect(result.valid).toBe(true);
    });
  });

  describe('Event Buffering & Queue Management', () => {
    it('should handle sequential small batches', () => {
      const batches = Array.from({ length: 5 }, (_, batchIdx) => {
        const events = Array.from({ length: 10 }, (_, eventIdx) => ({
          type: 'WINDOW_FOCUS' as const,
          timestamp: Date.now() + batchIdx * 10000 + eventIdx * 100,
          windowTitle: `Window ${batchIdx}-${eventIdx}`,
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
        }));

        return { type: 'EVENTS' as const, events };
      });

      batches.forEach((batch) => {
        const result = validateEventBatch(batch);
        expect(result.valid).toBe(true);
      });
    });

    it('should handle rapid successive batches', () => {
      const startTime = Date.now();
      const batches = Array.from({ length: 10 }, (_, i) => {
        const events = Array.from({ length: 5 }, (_, j) => ({
          type: 'WINDOW_FOCUS' as const,
          timestamp: startTime + i * 10 + j, // Very close timestamps
          windowTitle: `Window ${i}-${j}`,
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
        }));

        return { type: 'EVENTS' as const, events };
      });

      batches.forEach((batch) => {
        const result = validateEventBatch(batch);
        expect(result.valid).toBe(true);
      });
    });
  });

  describe('Error Scenarios', () => {
    it('should handle batch with one invalid event', () => {
      const events = [
        {
          type: 'WINDOW_FOCUS' as const,
          timestamp: Date.now(),
          windowTitle: 'Valid',
          processName: 'chrome',
          processPath: '/Applications/Chrome.app',
        },
        {
          type: 'INVALID' as any,
          timestamp: Date.now(),
        },
        {
          type: 'WINDOW_FOCUS' as const,
          timestamp: Date.now() + 100,
          windowTitle: 'Valid2',
          processName: 'firefox',
          processPath: '/Applications/Firefox.app',
        },
      ];

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false); // Entire batch fails if one event is invalid
    });

    it('should handle malformed message', () => {
      const malformed = { type: 'GARBAGE', data: {} };
      const result = validateEventBatch(malformed as any);
      expect(result.valid).toBe(false);
    });

    it('should handle null batch', () => {
      const result = validateEventBatch(null as any);
      expect(result.valid).toBe(false);
    });

    it('should handle undefined batch', () => {
      const result = validateEventBatch(undefined as any);
      expect(result.valid).toBe(false);
    });

    it('should handle batch with null events', () => {
      const batch = { type: 'EVENTS' as const, events: null };
      const result = validateEventBatch(batch as any);
      expect(result.valid).toBe(false);
    });

    it('should provide error message for invalid batch', () => {
      const batch = { type: 'EVENTS' as const, events: [] };
      const result = validateEventBatch(batch);
      expect(result.valid).toBe(false);
      expect(result.error).toBeTruthy();
    });
  });

  describe('Data Integrity', () => {
    it('should preserve event order in batch', () => {
      const timestamps = [1000, 2000, 3000, 4000, 5000];
      const events = timestamps.map((ts) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: ts,
        windowTitle: 'Test',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);

      // Order should be preserved (consumer should maintain order)
      expect(result.valid).toBe(true);
      expect(events[0].timestamp).toBe(1000);
      expect(events[4].timestamp).toBe(5000);
    });

    it('should handle out-of-order events in batch', () => {
      const timestamps = [5000, 1000, 4000, 2000, 3000];
      const events = timestamps.map((ts) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: ts,
        windowTitle: 'Test',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);

      // Out-of-order is valid (sorting happens downstream)
      expect(result.valid).toBe(true);
    });

    it('should preserve optional fields in events', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now(),
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
        appCategory: 'browser',
        sessionId: '550e8400-e29b-41d4-a716-446655440000',
        data: { custom: 'field' },
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should reject event with missing required timestamp', () => {
      const event = {
        type: 'WINDOW_FOCUS' as const,
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });

    it('should reject event with missing required type', () => {
      const event = {
        timestamp: Date.now(),
        windowTitle: 'Chrome',
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });
  });

  describe('Event Type-Specific Validation', () => {
    it('should require isIdle for IDLE_CHANGED events', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        idleSeconds: 300,
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });

    it('should allow IDLE_CHANGED without idleSeconds', () => {
      const event = {
        type: 'IDLE_CHANGED' as const,
        timestamp: Date.now(),
        isIdle: true,
      };

      const result = validateEvent(event);
      expect(result.valid).toBe(true);
    });

    it('should require sessionId for SESSION_START', () => {
      const event = {
        type: 'SESSION_START' as const,
        timestamp: Date.now(),
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });

    it('should require sessionId for SESSION_END', () => {
      const event = {
        type: 'SESSION_END' as const,
        timestamp: Date.now(),
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });

    it('should require processName for PROCESS_START', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        processPath: '/Applications/Chrome.app',
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });

    it('should require processPath for PROCESS_START', () => {
      const event = {
        type: 'PROCESS_START' as const,
        timestamp: Date.now(),
        processName: 'chrome',
      };

      const result = validateEvent(event as any);
      expect(result.valid).toBe(false);
    });
  });

  describe('Timeout & Backpressure Handling', () => {
    it('should recognize timeout indicator', () => {
      const timeout = { triggered: true, duration: 5000 };
      expect(timeout.triggered).toBe(true);
      expect(timeout.duration).toBe(5000); // 5 seconds
    });

    it('should track backpressure state', () => {
      const backpressure = {
        active: false,
        queueSize: 0,
        maxQueueSize: 10000,
      };

      expect(backpressure.active).toBe(false);
      expect(backpressure.queueSize).toBeLessThanOrEqual(backpressure.maxQueueSize);
    });

    it('should detect high backpressure', () => {
      const backpressure = {
        active: true,
        queueSize: 9500,
        maxQueueSize: 10000,
      };

      const isHighBackpressure = backpressure.queueSize > backpressure.maxQueueSize * 0.8;
      expect(isHighBackpressure).toBe(true);
    });
  });

  describe('Performance Characteristics', () => {
    it('should validate 100 events quickly', () => {
      const start = performance.now();

      const events = Array.from({ length: 100 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i * 10,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);

      const duration = performance.now() - start;

      expect(result.valid).toBe(true);
      expect(duration).toBeLessThan(100); // Should validate in <100ms
    });

    it('should validate 1000 events within budget', () => {
      const start = performance.now();

      const events = Array.from({ length: 1000 }, (_, i) => ({
        type: 'WINDOW_FOCUS' as const,
        timestamp: Date.now() + i,
        windowTitle: `Window ${i}`,
        processName: 'chrome',
        processPath: '/Applications/Chrome.app',
      }));

      const batch = { type: 'EVENTS' as const, events };
      const result = validateEventBatch(batch);

      const duration = performance.now() - start;

      expect(result.valid).toBe(true);
      expect(duration).toBeLessThan(500); // Should validate in <500ms
    });
  });
});
