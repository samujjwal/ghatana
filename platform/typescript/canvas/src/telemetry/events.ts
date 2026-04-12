/**
 * @fileoverview Structured telemetry events for canvas operations and AI flows.
 *
 * All canvas telemetry events use discriminated unions for safe exhaustive
 * handling in sinks and analytics pipelines.
 */

// ============================================================================
// Canvas Operation Events
// ============================================================================

export type CanvasOperationKind =
  | 'element-add'
  | 'element-move'
  | 'element-resize'
  | 'element-delete'
  | 'element-select'
  | 'element-deselect'
  | 'element-group'
  | 'element-ungroup'
  | 'connection-add'
  | 'connection-delete'
  | 'viewport-pan'
  | 'viewport-zoom'
  | 'undo'
  | 'redo'
  | 'mode-change'
  | 'canvas-mount'
  | 'canvas-unmount';

export interface CanvasOperationEvent {
  readonly kind: 'canvas-operation';
  readonly operation: CanvasOperationKind;
  readonly canvasId: string;
  readonly sessionId: string;
  readonly timestamp: number;
  readonly durationMs: number;
  readonly elementId?: string;
  readonly elementType?: string;
  readonly success: boolean;
  readonly errorCode?: string;
  readonly metadata?: Readonly<Record<string, unknown>>;
}

// ============================================================================
// Canvas AI Flow Events
// ============================================================================

export type CanvasAIFlowKind =
  | 'suggestion-requested'
  | 'suggestion-accepted'
  | 'suggestion-rejected'
  | 'suggestion-modified'
  | 'generate-requested'
  | 'generate-completed'
  | 'autolayout-applied'
  | 'review-started'
  | 'review-completed';

export interface CanvasAIFlowEvent {
  readonly kind: 'canvas-ai-flow';
  readonly flow: CanvasAIFlowKind;
  readonly canvasId: string;
  readonly sessionId: string;
  readonly timestamp: number;
  readonly durationMs: number;
  readonly capability?: string;
  readonly suggestionId?: string;
  readonly confidence?: number;
  readonly success: boolean;
  readonly errorCode?: string;
  readonly metadata?: Readonly<Record<string, unknown>>;
}

// ============================================================================
// Union
// ============================================================================

export type CanvasTelemetryEvent = CanvasOperationEvent | CanvasAIFlowEvent;

// ============================================================================
// Sink Interface
// ============================================================================

export interface CanvasTelemetrySink {
  emit(event: CanvasTelemetryEvent): void;
  flush(): Promise<void>;
}

export const noopCanvasTelemetrySink: CanvasTelemetrySink = {
  emit: () => undefined,
  flush: () => Promise.resolve(),
};

// ============================================================================
// Helper
// ============================================================================

export async function withCanvasTelemetry<T>(
  sink: CanvasTelemetrySink,
  event: Omit<CanvasOperationEvent, 'durationMs' | 'success' | 'errorCode' | 'timestamp'>,
  fn: () => T | Promise<T>,
): Promise<T> {
  const start = Date.now();
  try {
    const result = await fn();
    sink.emit({
      ...event,
      timestamp: start,
      durationMs: Date.now() - start,
      success: true,
    });
    return result;
  } catch (err: unknown) {
    sink.emit({
      ...event,
      timestamp: start,
      durationMs: Date.now() - start,
      success: false,
      errorCode: err instanceof Error ? err.name : 'UNKNOWN',
    });
    throw err;
  }
}
