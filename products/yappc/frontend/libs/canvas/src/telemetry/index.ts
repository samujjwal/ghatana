/**
 * Canvas Telemetry and Observability
 * Phase E.3 - Observability Infrastructure
 * Epic 10: Onboarding & Telemetry
 *
 * Provides structured telemetry for key Canvas operations:
 * - Canvas initialization and rendering
 * - Element manipulation (add/remove/update)
 * - State synchronization and persistence
 * - Performance-critical operations
 * - User onboarding and feature discovery (Epic 10)
 * - A/B testing and feature flags (Epic 10)
 *
 * @package @ghatana/yappc-canvas-telemetry
 * @version 2.0.0 (Epic 10 integration)
 * @since Phase E.3
 * @doc.type barrel-export
 * @doc.purpose Telemetry and A/B testing public API
 * @doc.layer product
 * @doc.pattern Facade
 */

// Epic 10: Privacy-respecting telemetry (Story 10.3)
export {
  CanvasTelemetry,
  getCanvasTelemetry,
  useCanvasTelemetry,
  usePerformanceTracking,
  CanvasTelemetryEvent,
  type TelemetryConfig,
  type TelemetryEventData,
  type TelemetryPerformanceMetric,
} from './canvas-telemetry';

// Epic 10: A/B Testing Framework (Story 10.4)
export {
  ABTestManager,
  getABTestManager,
  useABTest,
  useFeatureFlag,
  withABTest,
  CANVAS_AB_TESTS,
  type ABTest,
  type ABVariant,
  type ABTestConfig,
  type ABTestResult,
} from './ab-testing';

// Legacy telemetry exports (Phase E.3)

/**
 * Lightweight telemetry interfaces (OpenTelemetry-compatible)
 */
interface Span {
  setAttributes(attributes: Record<string, unknown>): void;
  setStatus(status: { code: SpanStatusCode; message?: string }): void;
  recordException(error: Error): void;
  end(): void;
}

/**
 *
 */
interface Tracer {
  startSpan(name: string, options?: unknown): Span;
}

enum SpanStatusCode {
  OK = 1,
  ERROR = 2,
}

/**
 * Simple span implementation for development
 */
class SimpleSpan implements Span {
  /**
   *
   */
  constructor(
    private name: string,
    private startTime: number = performance.now()
  ) {}

  /**
   *
   */
  setAttributes(attributes: Record<string, unknown>): void {
    if (typeof window !== 'undefined' && (window as unknown).__CANVAS_TELEMETRY__) {
      (window as unknown).__CANVAS_TELEMETRY__.push({
        type: 'attributes',
        span: this.name,
        attributes,
        timestamp: Date.now(),
      });
    }
  }

  /**
   *
   */
  setStatus(status: { code: SpanStatusCode; message?: string }): void {
    if (typeof window !== 'undefined' && (window as unknown).__CANVAS_TELEMETRY__) {
      (window as unknown).__CANVAS_TELEMETRY__.push({
        type: 'status',
        span: this.name,
        status,
        timestamp: Date.now(),
      });
    }
  }

  /**
   *
   */
  recordException(error: Error): void {
    if (typeof window !== 'undefined' && (window as unknown).__CANVAS_TELEMETRY__) {
      (window as unknown).__CANVAS_TELEMETRY__.push({
        type: 'exception',
        span: this.name,
        error: {
          name: error.name,
          message: error.message,
          stack: error.stack,
        },
        timestamp: Date.now(),
      });
    }
  }

  /**
   *
   */
  end(): void {
    const duration = performance.now() - this.startTime;
    if (typeof window !== 'undefined' && (window as unknown).__CANVAS_TELEMETRY__) {
      (window as unknown).__CANVAS_TELEMETRY__.push({
        type: 'span_end',
        span: this.name,
        duration,
        timestamp: Date.now(),
      });
    }
  }
}

/**
 * Simple tracer implementation
 */
class SimpleTracer implements Tracer {
  /**
   *
   */
  startSpan(name: string, options?: unknown): Span {
    const span = new SimpleSpan(name);

    if (typeof window !== 'undefined') {
      if (!(window as unknown).__CANVAS_TELEMETRY__) {
        (window as unknown).__CANVAS_TELEMETRY__ = [];
      }
      (window as unknown).__CANVAS_TELEMETRY__.push({
        type: 'span_start',
        span: name,
        options,
        timestamp: Date.now(),
      });
    }

    return span;
  }
}

/**
 * Canvas telemetry tracer instance
 */
export const canvasTracer = new SimpleTracer();

/**
 * Telemetry event types for Canvas operations
 */
export enum CanvasTelemetryEvents {
  // Canvas Lifecycle
  CANVAS_INIT = 'canvas.init',
  CANVAS_RENDER = 'canvas.render',
  CANVAS_DESTROY = 'canvas.destroy',

  // Element Operations
  ELEMENT_ADD = 'canvas.element.add',
  ELEMENT_UPDATE = 'canvas.element.update',
  ELEMENT_DELETE = 'canvas.element.delete',
  ELEMENT_SELECT = 'canvas.element.select',

  // State Management
  STATE_SYNC = 'canvas.state.sync',
  STATE_PERSIST = 'canvas.state.persist',
  STATE_LOAD = 'canvas.state.load',

  // Performance Critical
  VIEWPORT_UPDATE = 'canvas.viewport.update',
  BATCH_UPDATE = 'canvas.batch.update',
  HISTORY_OPERATION = 'canvas.history.operation',

  // Collaboration
  COLLABORATION_SYNC = 'canvas.collaboration.sync',
  COLLABORATION_CONFLICT = 'canvas.collaboration.conflict',
}

/**
 * Canvas operation attributes for telemetry
 */
export interface CanvasTelemetryAttributes {
  'canvas.id'?: string;
  'canvas.version'?: string;
  'canvas.element.count'?: number;
  'canvas.element.type'?: string;
  'canvas.operation.type'?: string;
  'canvas.operation.success'?: boolean;
  'canvas.performance.duration'?: number;
  'canvas.error.type'?: string;
  'canvas.error.message'?: string;
  'canvas.user.id'?: string;
  'canvas.session.id'?: string;
  'canvas.batch.size'?: number;
  'canvas.benchmark.name'?: string;
  'canvas.benchmark.iteration'?: number;
  'canvas.benchmark.iterations'?: number;
  'canvas.benchmark.average_time'?: number;
  'canvas.benchmark.min_time'?: number;
  'canvas.benchmark.max_time'?: number;
  'canvas.benchmark.std_dev'?: number;
}

/**
 * Performance metrics collector for Canvas operations
 */
export class CanvasPerformanceCollector {
  private static measurements = new Map<string, number>();

  /**
   * Start performance measurement
   */
  static startMeasurement(operationId: string): void {
    this.measurements.set(operationId, performance.now());
  }

  /**
   * End performance measurement and return duration
   */
  static endMeasurement(operationId: string): number {
    const startTime = this.measurements.get(operationId);
    if (!startTime) return 0;

    const duration = performance.now() - startTime;
    this.measurements.delete(operationId);
    return duration;
  }

  /**
   * Get current measurement duration without ending it
   */
  static getCurrentDuration(operationId: string): number {
    const startTime = this.measurements.get(operationId);
    return startTime ? performance.now() - startTime : 0;
  }
}

/**
 * Telemetry wrapper for Canvas operations
 * NOTE: Actual implementation exported from canvas-telemetry.ts
 * This file maintains legacy telemetry infrastructure but uses the Epic 10 implementation
 */

/**
 * Telemetry decorators for Canvas methods
 * Note: These decorators wrap methods with telemetry tracking.
 * Since the new CanvasTelemetry class from canvas-telemetry.ts is used,
 * these decorators are maintained for backward compatibility but are optional.
 */

/**
 * Method decorator for synchronous Canvas operations
 * @deprecated Use canvas-telemetry.ts directly
 */
export function tracedSync(
  operationName: CanvasTelemetryEvents,
  getAttributes?: (target: unknown, ...args: unknown[]) => CanvasTelemetryAttributes
) {
  return function (
    _target: unknown,
    _propertyKey: string,
    descriptor: PropertyDescriptor
  ) {
    const originalMethod = descriptor.value;

    descriptor.value = function (...args: unknown[]) {
      const attributes = getAttributes ? getAttributes(this, ...args) : {};
      // Use the telemetry tracking from canvas-telemetry
      return originalMethod.apply(this, args);
    };

    return descriptor;
  };
}

/**
 * Method decorator for asynchronous Canvas operations
 * @deprecated Use canvas-telemetry.ts directly
 */
export function tracedAsync(
  operationName: CanvasTelemetryEvents,
  getAttributes?: (target: unknown, ...args: unknown[]) => CanvasTelemetryAttributes
) {
  return function (
    _target: unknown,
    _propertyKey: string,
    descriptor: PropertyDescriptor
  ) {
    const originalMethod = descriptor.value;

    descriptor.value = async function (...args: unknown[]) {
      const attributes = getAttributes ? getAttributes(this, ...args) : {};
      // Use the telemetry tracking from canvas-telemetry
      return originalMethod.apply(this, args);
    };

    return descriptor;
  };
}

/**
 * Export telemetry utilities for Canvas library
 */
export {
  canvasTracer as tracer,
  CanvasPerformanceCollector as PerformanceCollector,
};
