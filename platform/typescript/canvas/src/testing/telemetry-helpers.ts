/**
 * @fileoverview Canvas testing helpers for telemetry and AI contract verification.
 *
 * Provides spy/capture utilities for CanvasTelemetrySink and CanvasAIAdapter
 * so tests can assert exactly which events were emitted and which AI flows ran.
 */

import type {
  CanvasTelemetryEvent,
  CanvasTelemetrySink,
  CanvasOperationEvent,
  CanvasAIFlowEvent,
} from '../telemetry/events.js';
import type {
  CanvasAIAdapter,
  AISuggestion,
  CanvasAIContext,
  AIResult,
  AILayoutResult,
  AIGenerateElementResult,
} from '../ai/types.js';

// ============================================================================
// Telemetry Spy
// ============================================================================

/** Captures emitted telemetry events for assertion in tests. */
export class TelemetrySpy implements CanvasTelemetrySink {
  private readonly _events: CanvasTelemetryEvent[] = [];
  private _flushed = false;

  emit(event: CanvasTelemetryEvent): void {
    this._events.push(event);
  }

  async flush(): Promise<void> {
    this._flushed = true;
  }

  /** All captured events in emission order. */
  get events(): readonly CanvasTelemetryEvent[] {
    return this._events;
  }

  /** Only canvas-operation events. */
  get operationEvents(): readonly CanvasOperationEvent[] {
    return this._events.filter((e): e is CanvasOperationEvent => e.kind === 'canvas-operation');
  }

  /** Only canvas-ai-flow events. */
  get aiFlowEvents(): readonly CanvasAIFlowEvent[] {
    return this._events.filter((e): e is CanvasAIFlowEvent => e.kind === 'canvas-ai-flow');
  }

  /** Whether flush() was called. */
  get wasFlushed(): boolean {
    return this._flushed;
  }

  /** Count of emitted events. */
  get count(): number {
    return this._events.length;
  }

  /** Find events by operation/flow kind. */
  byOperation(op: CanvasOperationEvent['operation']): readonly CanvasOperationEvent[] {
    return this.operationEvents.filter((e) => e.operation === op);
  }

  byFlow(flow: CanvasAIFlowEvent['flow']): readonly CanvasAIFlowEvent[] {
    return this.aiFlowEvents.filter((e) => e.flow === flow);
  }

  /** Assert at least one event of the given operation was emitted. Throws if not. */
  assertEmitted(op: CanvasOperationEvent['operation']): void {
    if (this.byOperation(op).length === 0) {
      throw new Error(
        `TelemetrySpy: expected at least one '${op}' operation event, but none were emitted.\n` +
          `Emitted events: ${this._events.map((e) => ('operation' in e ? e.operation : e.flow)).join(', ')}`,
      );
    }
  }

  /** Assert no events of the given operation were emitted. */
  assertNotEmitted(op: CanvasOperationEvent['operation']): void {
    const found = this.byOperation(op);
    if (found.length > 0) {
      throw new Error(
        `TelemetrySpy: expected no '${op}' events, but found ${found.length}.`,
      );
    }
  }

  /** Assert all emitted operation events were successful. */
  assertAllSucceeded(): void {
    const failed = this._events.filter((e) => !e.success);
    if (failed.length > 0) {
      const codes = failed.map((e) => e.errorCode ?? 'UNKNOWN').join(', ');
      throw new Error(`TelemetrySpy: ${failed.length} event(s) failed with codes: ${codes}`);
    }
  }

  /** Reset all captured state. */
  reset(): void {
    this._events.length = 0;
    this._flushed = false;
  }
}

// ============================================================================
// AI Adapter Spy
// ============================================================================

export interface AIAdapterCall {
  readonly method: string;
  readonly args: readonly unknown[];
  readonly result: unknown;
  readonly durationMs: number;
}

/** Spy wrapper around a CanvasAIAdapter that records all calls. */
export class AIAdapterSpy implements CanvasAIAdapter {
  private readonly _calls: AIAdapterCall[] = [];
  private readonly inner: CanvasAIAdapter;

  constructor(inner: CanvasAIAdapter) {
    this.inner = inner;
  }

  get calls(): readonly AIAdapterCall[] {
    return this._calls;
  }

  callsFor(method: string): readonly AIAdapterCall[] {
    return this._calls.filter((c) => c.method === method);
  }

  reset(): void {
    this._calls.length = 0;
  }

  async getSuggestions(context: CanvasAIContext): Promise<AISuggestion[]> {
    const start = Date.now();
    const result = await this.inner.getSuggestions(context);
    this._calls.push({ method: 'getSuggestions', args: [context], result, durationMs: Date.now() - start });
    return result;
  }

  async acceptSuggestion(suggestion: AISuggestion, context: CanvasAIContext): Promise<AIResult> {
    const start = Date.now();
    const result = await this.inner.acceptSuggestion(suggestion, context);
    this._calls.push({ method: 'acceptSuggestion', args: [suggestion, context], result, durationMs: Date.now() - start });
    return result;
  }

  async dismissSuggestion(suggestionId: string): Promise<void> {
    const start = Date.now();
    await this.inner.dismissSuggestion(suggestionId);
    this._calls.push({ method: 'dismissSuggestion', args: [suggestionId], result: undefined, durationMs: Date.now() - start });
  }

  async query(context: CanvasAIContext): Promise<AIResult> {
    const start = Date.now();
    const result = await this.inner.query(context);
    this._calls.push({ method: 'query', args: [context], result, durationMs: Date.now() - start });
    return result;
  }

  async autoLayout(context: CanvasAIContext): Promise<AILayoutResult> {
    const start = Date.now();
    const result = await this.inner.autoLayout(context);
    this._calls.push({ method: 'autoLayout', args: [context], result, durationMs: Date.now() - start });
    return result;
  }

  async generateElements(description: string, context: CanvasAIContext): Promise<AIGenerateElementResult[]> {
    const start = Date.now();
    const result = await this.inner.generateElements(description, context);
    this._calls.push({ method: 'generateElements', args: [description, context], result, durationMs: Date.now() - start });
    return result;
  }
}

// ============================================================================
// Stub AI Adapter (no-op for unit tests)
// ============================================================================

const EMPTY_CONTEXT_RESULT: AIResult = { kind: 'suggestions', suggestions: [] };

/** Stub CanvasAIAdapter that returns empty results. Safe for isolated unit tests. */
export function createStubAIAdapter(): CanvasAIAdapter {
  return {
    getSuggestions: async () => [],
    acceptSuggestion: async () => EMPTY_CONTEXT_RESULT,
    dismissSuggestion: async () => undefined,
    query: async () => EMPTY_CONTEXT_RESULT,
    autoLayout: async () => ({ positions: {} }),
    generateElements: async () => [],
  };
}

// ============================================================================
// Telemetry Contract Assertion Helpers
// ============================================================================

/**
 * Assert that a canvas operation was recorded with the expected success status.
 */
export function assertOperationTelemetry(
  spy: TelemetrySpy,
  operation: CanvasOperationEvent['operation'],
  options: { success?: boolean; minCount?: number } = {},
): void {
  const { success = true, minCount = 1 } = options;
  const events = spy.byOperation(operation).filter((e) => e.success === success);
  if (events.length < minCount) {
    throw new Error(
      `assertOperationTelemetry: expected ≥${minCount} '${operation}' event(s) with success=${success}, ` +
        `got ${events.length}`,
    );
  }
}

/**
 * Assert that an AI flow was recorded.
 */
export function assertAIFlowTelemetry(
  spy: TelemetrySpy,
  flow: CanvasAIFlowEvent['flow'],
  options: { minCount?: number } = {},
): void {
  const { minCount = 1 } = options;
  const events = spy.byFlow(flow);
  if (events.length < minCount) {
    throw new Error(
      `assertAIFlowTelemetry: expected ≥${minCount} '${flow}' AI flow event(s), got ${events.length}`,
    );
  }
}
