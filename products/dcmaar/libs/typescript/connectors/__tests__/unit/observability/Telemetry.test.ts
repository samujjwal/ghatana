/**
 * @fileoverview Comprehensive unit tests for Telemetry module
 *
 * Tests cover:
 * - Span creation and lifecycle
 * - Span context propagation
 * - Trace ID generation and correlation
 * - Attribute setting and enrichment
 * - Event recording on spans
 * - Span ending and duration calculation
 * - Exporter integration (mocked)
 * - Sampling strategies
 * - Error recording and exception tracking
 * - Multiple concurrent spans
 * - Parent-child span relationships
 * - Batch processing and export
 * - Timer management and cleanup
 */

import { Telemetry, Span, SpanStatus, createTelemetry, StructuredLogger, createLogger } from '../../../src/observability/Telemetry';

describe('Telemetry', () => {
  let telemetry: Telemetry;
  let mockExporter: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    mockExporter = jest.fn().mockResolvedValue(undefined);
  });

  afterEach(async () => {
    if (telemetry) {
      await telemetry.destroy();
    }
    jest.useRealTimers();
  });

  describe('Constructor and Initialization', () => {
    it('should initialize with minimal configuration', () => {
      telemetry = new Telemetry({ serviceName: 'test-service' });

      expect(telemetry).toBeInstanceOf(Telemetry);
      expect(telemetry.getActiveSpans()).toHaveLength(0);
      expect(telemetry.getCompletedSpans()).toHaveLength(0);
    });

    it('should apply default values for optional config', () => {
      const spanStartListener = jest.fn();
      telemetry = new Telemetry({
        serviceName: 'test-service',
      });

      telemetry.on('spanStart', spanStartListener);
      const spanId = telemetry.startSpan('test.operation');

      expect(spanId).toBeTruthy();
      expect(spanStartListener).toHaveBeenCalled();

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].attributes['service.name']).toBe('test-service');
      expect(activeSpans[0].attributes['service.version']).toBe('1.0.0');
      expect(activeSpans[0].attributes['deployment.environment']).toBe('development');
    });

    it('should use provided configuration values', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        serviceVersion: '2.0.0',
        environment: 'production',
        enabled: true,
        sampleRate: 0.5,
        defaultAttributes: { 'custom.attr': 'value' },
        exportSpans: mockExporter,
        batchSize: 50,
        batchTimeout: 3000,
      });

      const spanId = telemetry.startSpan('test.operation');
      const activeSpans = telemetry.getActiveSpans();

      expect(activeSpans[0].attributes['service.name']).toBe('test-service');
      expect(activeSpans[0].attributes['service.version']).toBe('2.0.0');
      expect(activeSpans[0].attributes['deployment.environment']).toBe('production');
      expect(activeSpans[0].attributes['custom.attr']).toBe('value');
    });

    it('should start export timer when enabled', () => {
      const setIntervalSpy = jest.spyOn(global, 'setInterval');

      telemetry = new Telemetry({
        serviceName: 'test-service',
        enabled: true,
        batchTimeout: 5000,
      });

      expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 5000);
    });

    it('should not start export timer when disabled', () => {
      const setIntervalSpy = jest.spyOn(global, 'setInterval');

      telemetry = new Telemetry({
        serviceName: 'test-service',
        enabled: false,
      });

      expect(setIntervalSpy).not.toHaveBeenCalled();
    });
  });

  describe('Span Creation and Lifecycle', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should create a span with generated IDs', () => {
      const spanId = telemetry.startSpan('test.operation');

      expect(spanId).toBeTruthy();
      expect(typeof spanId).toBe('string');

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans).toHaveLength(1);
      expect(activeSpans[0].spanId).toBe(spanId);
      expect(activeSpans[0].traceId).toBeTruthy();
      expect(activeSpans[0].name).toBe('test.operation');
    });

    it('should initialize span with correct structure', () => {
      const spanId = telemetry.startSpan('test.operation', { 'custom.key': 'value' });
      const activeSpans = telemetry.getActiveSpans();
      const span = activeSpans[0];

      expect(span).toMatchObject({
        spanId,
        traceId: expect.any(String),
        name: 'test.operation',
        startTime: expect.any(Number),
        attributes: expect.objectContaining({
          'service.name': 'test-service',
          'custom.key': 'value',
        }),
        events: [],
        status: { code: 'UNSET' },
      });
      expect(span.endTime).toBeUndefined();
      expect(span.parentSpanId).toBeUndefined();
    });

    it('should emit spanStart event on span creation', () => {
      const spanStartListener = jest.fn();
      telemetry.on('spanStart', spanStartListener);

      const spanId = telemetry.startSpan('test.operation');

      expect(spanStartListener).toHaveBeenCalledWith(
        expect.objectContaining({
          spanId,
          traceId: expect.any(String),
          name: 'test.operation',
          timestamp: expect.any(Number),
        })
      );
    });

    it('should end a span and update its state', async () => {
      const spanId = telemetry.startSpan('test.operation');

      // Advance time to ensure duration > 0
      jest.advanceTimersByTime(100);

      telemetry.endSpan(spanId, { code: 'OK' });

      expect(telemetry.getActiveSpans()).toHaveLength(0);

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans).toHaveLength(1);
      expect(completedSpans[0].endTime).toBeDefined();
      expect(completedSpans[0].status).toEqual({ code: 'OK' });
    });

    it('should calculate span duration correctly', () => {
      const spanId = telemetry.startSpan('test.operation');
      const startTime = Date.now();

      jest.advanceTimersByTime(250);

      telemetry.endSpan(spanId, { code: 'OK' });

      const completedSpans = telemetry.getCompletedSpans();
      const duration = completedSpans[0].endTime! - completedSpans[0].startTime;

      expect(duration).toBeGreaterThanOrEqual(250);
    });

    it('should emit spanEnd event on span completion', () => {
      const spanEndListener = jest.fn();
      telemetry.on('spanEnd', spanEndListener);

      const spanId = telemetry.startSpan('test.operation');
      jest.advanceTimersByTime(100);
      telemetry.endSpan(spanId, { code: 'OK' });

      expect(spanEndListener).toHaveBeenCalledWith(
        expect.objectContaining({
          spanId,
          traceId: expect.any(String),
          name: 'test.operation',
          duration: expect.any(Number),
          status: { code: 'OK' },
          timestamp: expect.any(Number),
        })
      );
    });

    it('should set default status to OK if not provided', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.endSpan(spanId);

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].status).toEqual({ code: 'OK' });
    });

    it('should handle ending non-existent span gracefully', () => {
      expect(() => {
        telemetry.endSpan('non-existent-span-id');
      }).not.toThrow();
    });

    it('should handle ending span with empty string ID', () => {
      expect(() => {
        telemetry.endSpan('');
      }).not.toThrow();
    });
  });

  describe('Parent-Child Span Relationships', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should create child span with parent linkage', () => {
      const parentSpanId = telemetry.startSpan('parent.operation');
      const childSpanId = telemetry.startSpan('child.operation', {}, parentSpanId);

      const activeSpans = telemetry.getActiveSpans();
      const parentSpan = activeSpans.find(s => s.spanId === parentSpanId)!;
      const childSpan = activeSpans.find(s => s.spanId === childSpanId)!;

      expect(childSpan.parentSpanId).toBe(parentSpanId);
      expect(childSpan.traceId).toBe(parentSpan.traceId);
    });

    it('should share trace ID across parent and child spans', () => {
      const parentSpanId = telemetry.startSpan('parent.operation');
      const child1SpanId = telemetry.startSpan('child1.operation', {}, parentSpanId);
      const child2SpanId = telemetry.startSpan('child2.operation', {}, parentSpanId);

      const activeSpans = telemetry.getActiveSpans();
      const traceIds = activeSpans.map(s => s.traceId);

      expect(new Set(traceIds).size).toBe(1);
    });

    it('should handle child span with non-existent parent', () => {
      const childSpanId = telemetry.startSpan('child.operation', {}, 'non-existent-parent');

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].parentSpanId).toBe('non-existent-parent');
      expect(activeSpans[0].traceId).toBeTruthy();
    });

    it('should support nested span hierarchies', () => {
      const rootSpanId = telemetry.startSpan('root.operation');
      const child1SpanId = telemetry.startSpan('child1.operation', {}, rootSpanId);
      const grandchildSpanId = telemetry.startSpan('grandchild.operation', {}, child1SpanId);

      const activeSpans = telemetry.getActiveSpans();
      const rootSpan = activeSpans.find(s => s.spanId === rootSpanId)!;
      const child1Span = activeSpans.find(s => s.spanId === child1SpanId)!;
      const grandchildSpan = activeSpans.find(s => s.spanId === grandchildSpanId)!;

      expect(child1Span.parentSpanId).toBe(rootSpanId);
      expect(grandchildSpan.parentSpanId).toBe(child1SpanId);
      expect(rootSpan.traceId).toBe(child1Span.traceId);
      expect(child1Span.traceId).toBe(grandchildSpan.traceId);
    });
  });

  describe('Span Attributes', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
        defaultAttributes: { 'default.key': 'default.value' },
      });
    });

    it('should include default attributes in span', () => {
      const spanId = telemetry.startSpan('test.operation');
      const activeSpans = telemetry.getActiveSpans();

      expect(activeSpans[0].attributes).toMatchObject({
        'default.key': 'default.value',
        'service.name': 'test-service',
      });
    });

    it('should merge custom attributes with defaults', () => {
      const spanId = telemetry.startSpan('test.operation', { 'custom.key': 'custom.value' });
      const activeSpans = telemetry.getActiveSpans();

      expect(activeSpans[0].attributes).toMatchObject({
        'default.key': 'default.value',
        'custom.key': 'custom.value',
        'service.name': 'test-service',
      });
    });

    it('should allow custom attributes to override defaults', () => {
      const spanId = telemetry.startSpan('test.operation', { 'default.key': 'overridden' });
      const activeSpans = telemetry.getActiveSpans();

      expect(activeSpans[0].attributes['default.key']).toBe('overridden');
    });

    it('should update span attributes after creation', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.setSpanAttributes(spanId, { 'new.key': 'new.value' });

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].attributes['new.key']).toBe('new.value');
    });

    it('should merge multiple attribute updates', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.setSpanAttributes(spanId, { 'key1': 'value1' });
      telemetry.setSpanAttributes(spanId, { 'key2': 'value2' });

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].attributes).toMatchObject({
        'key1': 'value1',
        'key2': 'value2',
      });
    });

    it('should handle setting attributes on non-existent span', () => {
      expect(() => {
        telemetry.setSpanAttributes('non-existent', { 'key': 'value' });
      }).not.toThrow();
    });

    it('should handle setting attributes when disabled', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        enabled: false,
      });

      expect(() => {
        telemetry.setSpanAttributes('any-id', { 'key': 'value' });
      }).not.toThrow();
    });
  });

  describe('Span Events', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should add event to active span', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.addSpanEvent(spanId, 'checkpoint.reached');

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events).toHaveLength(1);
      expect(activeSpans[0].events[0]).toMatchObject({
        name: 'checkpoint.reached',
        timestamp: expect.any(Number),
      });
    });

    it('should add event with attributes', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.addSpanEvent(spanId, 'data.processed', { count: 100 });

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events[0]).toMatchObject({
        name: 'data.processed',
        timestamp: expect.any(Number),
        attributes: { count: 100 },
      });
    });

    it('should support multiple events on same span', () => {
      const spanId = telemetry.startSpan('test.operation');

      telemetry.addSpanEvent(spanId, 'event1');
      jest.advanceTimersByTime(10);
      telemetry.addSpanEvent(spanId, 'event2');
      jest.advanceTimersByTime(10);
      telemetry.addSpanEvent(spanId, 'event3');

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events).toHaveLength(3);
      expect(activeSpans[0].events[0].name).toBe('event1');
      expect(activeSpans[0].events[1].name).toBe('event2');
      expect(activeSpans[0].events[2].name).toBe('event3');
    });

    it('should maintain event chronological order', () => {
      const spanId = telemetry.startSpan('test.operation');

      const timestamp1 = Date.now();
      telemetry.addSpanEvent(spanId, 'event1');

      jest.advanceTimersByTime(100);
      const timestamp2 = Date.now();
      telemetry.addSpanEvent(spanId, 'event2');

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events[0].timestamp).toBeLessThan(activeSpans[0].events[1].timestamp);
    });

    it('should handle adding event to non-existent span', () => {
      expect(() => {
        telemetry.addSpanEvent('non-existent', 'event');
      }).not.toThrow();
    });

    it('should preserve events when span ends', () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.addSpanEvent(spanId, 'event1');
      telemetry.addSpanEvent(spanId, 'event2');
      telemetry.endSpan(spanId);

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].events).toHaveLength(2);
    });
  });

  describe('Exception Recording', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should record exception as span event', () => {
      const spanId = telemetry.startSpan('test.operation');
      const error = new Error('Test error');

      telemetry.recordException(spanId, error);

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events).toHaveLength(1);
      expect(activeSpans[0].events[0].name).toBe('exception');
      expect(activeSpans[0].events[0].attributes).toMatchObject({
        'exception.type': 'Error',
        'exception.message': 'Test error',
        'exception.stacktrace': expect.any(String),
      });
    });

    it('should set span status to ERROR on exception', () => {
      const spanId = telemetry.startSpan('test.operation');
      const error = new Error('Test error');

      telemetry.recordException(spanId, error);

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].status).toEqual({
        code: 'ERROR',
        message: 'Test error',
      });
    });

    it('should record custom error types', () => {
      const spanId = telemetry.startSpan('test.operation');

      class CustomError extends Error {
        constructor(message: string) {
          super(message);
          this.name = 'CustomError';
        }
      }

      const error = new CustomError('Custom error message');
      telemetry.recordException(spanId, error);

      const activeSpans = telemetry.getActiveSpans();
      expect(activeSpans[0].events[0].attributes).toMatchObject({
        'exception.type': 'CustomError',
        'exception.message': 'Custom error message',
      });
    });

    it('should handle recording exception on non-existent span', () => {
      expect(() => {
        telemetry.recordException('non-existent', new Error('Test'));
      }).not.toThrow();
    });

    it('should include stack trace in exception event', () => {
      const spanId = telemetry.startSpan('test.operation');
      const error = new Error('Test error');

      telemetry.recordException(spanId, error);

      const activeSpans = telemetry.getActiveSpans();
      const stackTrace = activeSpans[0].events[0].attributes!['exception.stacktrace'];

      expect(stackTrace).toBeTruthy();
      expect(stackTrace).toContain('Error: Test error');
    });
  });

  describe('Sampling', () => {
    it('should sample all spans with sampleRate 1.0', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        sampleRate: 1.0,
      });

      const spanIds = [];
      for (let i = 0; i < 100; i++) {
        spanIds.push(telemetry.startSpan(`operation-${i}`));
      }

      const activeSampledSpans = spanIds.filter(id => id !== '');
      expect(activeSampledSpans.length).toBe(100);
    });

    it('should sample no spans with sampleRate 0.0', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        sampleRate: 0.0,
      });

      const spanIds = [];
      for (let i = 0; i < 100; i++) {
        spanIds.push(telemetry.startSpan(`operation-${i}`));
      }

      const activeSampledSpans = spanIds.filter(id => id !== '');
      expect(activeSampledSpans.length).toBe(0);
    });

    it('should return empty string for non-sampled spans', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        sampleRate: 0.0,
      });

      const spanId = telemetry.startSpan('test.operation');
      expect(spanId).toBe('');
    });

    it('should not create span when not sampled', () => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        sampleRate: 0.0,
      });

      telemetry.startSpan('test.operation');
      expect(telemetry.getActiveSpans()).toHaveLength(0);
    });
  });

  describe('Trace Helper Method', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should create and complete span automatically', async () => {
      const operation = jest.fn().mockResolvedValue('result');

      const result = await telemetry.trace('test.operation', operation);

      expect(result).toBe('result');
      expect(operation).toHaveBeenCalledWith(expect.any(String));
      expect(telemetry.getActiveSpans()).toHaveLength(0);
      expect(telemetry.getCompletedSpans()).toHaveLength(1);
    });

    it('should set span status to OK on success', async () => {
      await telemetry.trace('test.operation', async () => {
        return 'success';
      });

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].status).toEqual({ code: 'OK' });
    });

    it('should record exception and set ERROR status on failure', async () => {
      const error = new Error('Operation failed');
      const operation = jest.fn().mockRejectedValue(error);

      await expect(
        telemetry.trace('test.operation', operation)
      ).rejects.toThrow('Operation failed');

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].status).toEqual({
        code: 'ERROR',
        message: 'Operation failed',
      });
      expect(completedSpans[0].events).toHaveLength(1);
      expect(completedSpans[0].events[0].name).toBe('exception');
    });

    it('should pass span ID to operation', async () => {
      let capturedSpanId: string | undefined;

      await telemetry.trace('test.operation', async (spanId) => {
        capturedSpanId = spanId;
      });

      expect(capturedSpanId).toBeTruthy();
      expect(typeof capturedSpanId).toBe('string');
    });

    it('should support custom attributes', async () => {
      await telemetry.trace('test.operation', async () => {}, { 'custom.key': 'value' });

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].attributes['custom.key']).toBe('value');
    });

    it('should support parent span context', async () => {
      const parentSpanId = telemetry.startSpan('parent.operation');

      await telemetry.trace('child.operation', async () => {}, {}, parentSpanId);

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].parentSpanId).toBe(parentSpanId);
    });

    it('should handle non-Error rejections', async () => {
      await expect(
        telemetry.trace('test.operation', async () => {
          throw 'string error';
        })
      ).rejects.toBe('string error');

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].status.code).toBe('ERROR');
      expect(completedSpans[0].status.message).toBe('string error');
    });
  });

  describe('Batch Export', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
        batchSize: 5,
        batchTimeout: 10000,
      });
    });

    it('should export when batch size is reached', async () => {
      for (let i = 0; i < 5; i++) {
        const spanId = telemetry.startSpan(`operation-${i}`);
        telemetry.endSpan(spanId);
      }

      // Wait for async export to complete
      await jest.runOnlyPendingTimersAsync();

      expect(mockExporter).toHaveBeenCalledTimes(1);
      expect(mockExporter).toHaveBeenCalledWith(expect.arrayContaining([
        expect.objectContaining({ name: 'operation-0' }),
        expect.objectContaining({ name: 'operation-1' }),
        expect.objectContaining({ name: 'operation-2' }),
        expect.objectContaining({ name: 'operation-3' }),
        expect.objectContaining({ name: 'operation-4' }),
      ]));
    });

    it('should export on timer interval', async () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.endSpan(spanId);

      expect(mockExporter).not.toHaveBeenCalled();

      // Advance time to trigger export timer
      jest.advanceTimersByTime(10000);
      await jest.runOnlyPendingTimersAsync();

      expect(mockExporter).toHaveBeenCalledTimes(1);
    });

    it('should emit spansExported event on successful export', async () => {
      const exportListener = jest.fn();
      telemetry.on('spansExported', exportListener);

      for (let i = 0; i < 5; i++) {
        const spanId = telemetry.startSpan(`operation-${i}`);
        telemetry.endSpan(spanId);
      }

      await jest.runOnlyPendingTimersAsync();

      expect(exportListener).toHaveBeenCalledWith(
        expect.objectContaining({
          count: 5,
          timestamp: expect.any(Number),
        })
      );
    });

    it('should clear completed spans after successful export', async () => {
      for (let i = 0; i < 5; i++) {
        const spanId = telemetry.startSpan(`operation-${i}`);
        telemetry.endSpan(spanId);
      }

      await jest.runOnlyPendingTimersAsync();

      expect(telemetry.getCompletedSpans()).toHaveLength(0);
    });

    it('should restore spans on export failure', async () => {
      const exportError = new Error('Export failed');
      mockExporter.mockRejectedValueOnce(exportError);

      const errorListener = jest.fn();
      telemetry.on('error', errorListener);

      for (let i = 0; i < 5; i++) {
        const spanId = telemetry.startSpan(`operation-${i}`);
        telemetry.endSpan(spanId);
      }

      await jest.runOnlyPendingTimersAsync();

      expect(errorListener).toHaveBeenCalledWith(exportError);
      expect(telemetry.getCompletedSpans()).toHaveLength(5);
    });

    it('should handle export errors on timer', async () => {
      const exportError = new Error('Timer export failed');
      mockExporter.mockRejectedValueOnce(exportError);

      const errorListener = jest.fn();
      telemetry.on('error', errorListener);

      const spanId = telemetry.startSpan('test.operation');
      telemetry.endSpan(spanId);

      jest.advanceTimersByTime(10000);
      await jest.runOnlyPendingTimersAsync();

      expect(errorListener).toHaveBeenCalledWith(exportError);
    });

    it('should not export empty batch', async () => {
      jest.advanceTimersByTime(10000);
      await jest.runOnlyPendingTimersAsync();

      expect(mockExporter).not.toHaveBeenCalled();
    });
  });

  describe('Flush and Destroy', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
        batchSize: 100,
      });
    });

    it('should flush pending spans immediately', async () => {
      const spanId1 = telemetry.startSpan('operation-1');
      const spanId2 = telemetry.startSpan('operation-2');
      telemetry.endSpan(spanId1);
      telemetry.endSpan(spanId2);

      await telemetry.flush();

      expect(mockExporter).toHaveBeenCalledTimes(1);
      expect(mockExporter).toHaveBeenCalledWith(expect.arrayContaining([
        expect.objectContaining({ name: 'operation-1' }),
        expect.objectContaining({ name: 'operation-2' }),
      ]));
    });

    it('should clear export timer on destroy', async () => {
      const clearIntervalSpy = jest.spyOn(global, 'clearInterval');

      await telemetry.destroy();

      expect(clearIntervalSpy).toHaveBeenCalled();
    });

    it('should flush spans on destroy', async () => {
      const spanId = telemetry.startSpan('test.operation');
      telemetry.endSpan(spanId);

      await telemetry.destroy();

      expect(mockExporter).toHaveBeenCalledTimes(1);
    });

    it('should clear all state on destroy', async () => {
      const spanId = telemetry.startSpan('test.operation');

      await telemetry.destroy();

      expect(telemetry.getActiveSpans()).toHaveLength(0);
      expect(telemetry.getCompletedSpans()).toHaveLength(0);
    });

    it('should remove all listeners on destroy', async () => {
      const listener = jest.fn();
      telemetry.on('spanStart', listener);

      await telemetry.destroy();

      expect(telemetry.listenerCount('spanStart')).toBe(0);
    });
  });

  describe('Concurrent Operations', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        exportSpans: mockExporter,
      });
    });

    it('should handle multiple concurrent spans', async () => {
      const promises = [];

      for (let i = 0; i < 10; i++) {
        promises.push(
          telemetry.trace(`operation-${i}`, async () => {
            await new Promise(resolve => setTimeout(resolve, 10));
            return i;
          })
        );
      }

      const results = await Promise.all(promises);

      expect(results).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
      expect(telemetry.getCompletedSpans()).toHaveLength(10);
    });

    it('should maintain separate span contexts for concurrent operations', async () => {
      const spanIds: string[] = [];

      await Promise.all([
        telemetry.trace('op1', async (spanId) => {
          spanIds.push(spanId);
          telemetry.addSpanEvent(spanId, 'event1');
        }),
        telemetry.trace('op2', async (spanId) => {
          spanIds.push(spanId);
          telemetry.addSpanEvent(spanId, 'event2');
        }),
      ]);

      expect(new Set(spanIds).size).toBe(2);

      const completedSpans = telemetry.getCompletedSpans();
      expect(completedSpans[0].events[0].name).toBe('event1');
      expect(completedSpans[1].events[0].name).toBe('event2');
    });
  });

  describe('Disabled Telemetry', () => {
    beforeEach(() => {
      telemetry = new Telemetry({
        serviceName: 'test-service',
        enabled: false,
      });
    });

    it('should not create spans when disabled', () => {
      const spanId = telemetry.startSpan('test.operation');

      expect(spanId).toBe('');
      expect(telemetry.getActiveSpans()).toHaveLength(0);
    });

    it('should not end spans when disabled', () => {
      telemetry.endSpan('any-id');

      expect(telemetry.getCompletedSpans()).toHaveLength(0);
    });

    it('should not add events when disabled', () => {
      expect(() => {
        telemetry.addSpanEvent('any-id', 'event');
      }).not.toThrow();
    });

    it('should not set attributes when disabled', () => {
      expect(() => {
        telemetry.setSpanAttributes('any-id', { key: 'value' });
      }).not.toThrow();
    });

    it('should not record exceptions when disabled', () => {
      expect(() => {
        telemetry.recordException('any-id', new Error('test'));
      }).not.toThrow();
    });
  });

  describe('createTelemetry Factory', () => {
    it('should create Telemetry instance', async () => {
      const instance = createTelemetry({ serviceName: 'test-service' });

      expect(instance).toBeInstanceOf(Telemetry);

      await instance.destroy();
    });
  });
});

describe('StructuredLogger', () => {
  let logger: StructuredLogger;
  let consoleLogSpy: jest.SpyInstance;

  beforeEach(() => {
    consoleLogSpy = jest.spyOn(console, 'log').mockImplementation();
  });

  afterEach(() => {
    consoleLogSpy.mockRestore();
  });

  describe('Constructor', () => {
    it('should initialize with service name', () => {
      logger = new StructuredLogger('test-service');

      logger.info('test message');

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"service":"test-service"')
      );
    });

    it('should initialize with default context', () => {
      logger = new StructuredLogger('test-service', { env: 'test', region: 'us-east-1' });

      logger.info('test message');

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"env":"test"')
      );
      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"region":"us-east-1"')
      );
    });
  });

  describe('Log Levels', () => {
    beforeEach(() => {
      logger = new StructuredLogger('test-service');
    });

    it('should log debug messages', () => {
      logger.debug('debug message', { key: 'value' });

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"level":"debug"')
      );
      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"message":"debug message"')
      );
    });

    it('should log info messages', () => {
      logger.info('info message', { key: 'value' });

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"level":"info"')
      );
      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"message":"info message"')
      );
    });

    it('should log warn messages', () => {
      logger.warn('warn message', { key: 'value' });

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"level":"warn"')
      );
      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"message":"warn message"')
      );
    });

    it('should log error messages', () => {
      logger.error('error message', undefined, { key: 'value' });

      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"level":"error"')
      );
      expect(consoleLogSpy).toHaveBeenCalledWith(
        expect.stringContaining('"message":"error message"')
      );
    });
  });

  describe('Error Logging', () => {
    beforeEach(() => {
      logger = new StructuredLogger('test-service');
    });

    it('should serialize error objects', () => {
      const error = new Error('test error');
      error.stack = 'Error: test error\n    at Test.it';

      logger.error('operation failed', error);

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry.error).toMatchObject({
        name: 'Error',
        message: 'test error',
        stack: expect.stringContaining('Error: test error'),
      });
    });

    it('should handle error without stack trace', () => {
      const error = new Error('test error');
      delete error.stack;

      logger.error('operation failed', error);

      expect(consoleLogSpy).toHaveBeenCalled();
    });

    it('should log error with additional context', () => {
      const error = new Error('test error');

      logger.error('operation failed', error, { userId: '123', action: 'update' });

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry.userId).toBe('123');
      expect(logEntry.action).toBe('update');
    });
  });

  describe('Context Merging', () => {
    it('should merge default context with log context', () => {
      logger = new StructuredLogger('test-service', { default: 'value' });

      logger.info('test', { custom: 'context' });

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry.default).toBe('value');
      expect(logEntry.custom).toBe('context');
    });

    it('should allow log context to override default context', () => {
      logger = new StructuredLogger('test-service', { key: 'default' });

      logger.info('test', { key: 'overridden' });

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry.key).toBe('overridden');
    });
  });

  describe('Log Format', () => {
    beforeEach(() => {
      logger = new StructuredLogger('test-service');
    });

    it('should output valid JSON', () => {
      logger.info('test message');

      const logOutput = consoleLogSpy.mock.calls[0][0];

      expect(() => JSON.parse(logOutput)).not.toThrow();
    });

    it('should include timestamp in ISO format', () => {
      logger.info('test message');

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    });

    it('should include all required fields', () => {
      logger.info('test message', { custom: 'data' });

      const logOutput = consoleLogSpy.mock.calls[0][0];
      const logEntry = JSON.parse(logOutput);

      expect(logEntry).toMatchObject({
        timestamp: expect.any(String),
        level: 'info',
        service: 'test-service',
        message: 'test message',
        custom: 'data',
      });
    });
  });

  describe('createLogger Factory', () => {
    it('should create StructuredLogger instance', () => {
      const instance = createLogger('test-service');

      expect(instance).toBeInstanceOf(StructuredLogger);
    });

    it('should create logger with default context', () => {
      const instance = createLogger('test-service', { env: 'test' });

      instance.info('test');

      const logOutput = consoleLogSpy.mock.calls[0][0];
      expect(logOutput).toContain('"env":"test"');
    });
  });
});
