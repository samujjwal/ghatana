/**
 * @fileoverview Builder operation telemetry and rollback export support.
 *
 * Defines structured telemetry events for all builder document operations,
 * with rollback snapshots for session recovery.
 */

import type { BuilderTelemetryEvent } from '@ghatana/platform-events/observability';
import type { BuilderDocument } from './builder-document.js';
import type { NodeId } from './types.js';

export type { BuilderTelemetryEvent };

// ============================================================================
// Operation Telemetry Events
// ============================================================================

export type BuilderOperationKind =
  | 'insert-node'
  | 'move-node'
  | 'delete-node'
  | 'update-props'
  | 'add-binding'
  | 'remove-binding'
  | 'mount-document'
  | 'unmount-document'
  | 'save'
  | 'restore';

export interface BuilderOperationEvent {
  readonly kind: BuilderOperationKind;
  readonly documentId: string;
  readonly sessionId: string;
  readonly timestamp: number;
  readonly durationMs: number;
  readonly nodeId?: NodeId;
  readonly success: boolean;
  readonly errorCode?: string;
  readonly metadata?: Readonly<Record<string, unknown>>;
}

// ============================================================================
// Rollback Snapshot
// ============================================================================

/** A named point-in-time snapshot of a BuilderDocument for rollback. */
export interface RollbackSnapshot {
  readonly snapshotId: string;
  readonly documentId: string;
  readonly label: string;
  readonly capturedAt: number;
  readonly document: BuilderDocument;
}

/** Create a rollback snapshot from a document. */
export function captureSnapshot(
  document: BuilderDocument,
  label: string,
): RollbackSnapshot {
  const legacyDocument = document as BuilderDocument & { id?: string };
  return {
    snapshotId: `snap-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    documentId: document.documentId ?? legacyDocument.id ?? '',
    label,
    capturedAt: Date.now(),
    document,
  };
}

// ============================================================================
// Rollback History Store
// ============================================================================

/** In-memory rollback history for a single builder session. */
export class RollbackHistory {
  private readonly snapshots: RollbackSnapshot[] = [];
  private readonly maxSnapshots: number;

  constructor(maxSnapshots: number = 50) {
    this.maxSnapshots = maxSnapshots;
  }

  /** Push a snapshot. Evicts oldest if over cap. */
  push(snapshot: RollbackSnapshot): void {
    this.snapshots.push(snapshot);
    if (this.snapshots.length > this.maxSnapshots) {
      this.snapshots.shift();
    }
  }

  /** Get all snapshots in oldest-first order. */
  all(): readonly RollbackSnapshot[] {
    return this.snapshots;
  }

  /** Get the most recent snapshot. */
  latest(): RollbackSnapshot | undefined {
    return this.snapshots.at(-1);
  }

  /** Find a snapshot by ID. */
  findById(snapshotId: string): RollbackSnapshot | undefined {
    return this.snapshots.find((s) => s.snapshotId === snapshotId);
  }

  /** Remove all snapshots after the given snapshotId (exclusive). */
  trimAfter(snapshotId: string): void {
    const idx = this.snapshots.findIndex((s) => s.snapshotId === snapshotId);
    if (idx !== -1) {
      this.snapshots.splice(idx + 1);
    }
  }

  /** Export all snapshots to a portable JSON string. */
  export(): string {
    return JSON.stringify(this.snapshots);
  }

  /** Import snapshots from a previously exported JSON string. */
  import(json: string): void {
    const parsed: unknown = JSON.parse(json);
    if (!Array.isArray(parsed)) {
      throw new Error('RollbackHistory.import: expected an array');
    }
    this.snapshots.length = 0;
    this.snapshots.push(...(parsed as RollbackSnapshot[]));
  }
}

// ============================================================================
// Telemetry Sink Interface
// ============================================================================

/** Interface for emitting builder telemetry events to an external backend. */
export interface BuilderTelemetrySink {
  emit(event: BuilderOperationEvent): void;
  flush(): Promise<void>;
}

// ============================================================================
// Platform-Events Bridge
// ============================================================================

const OPERATION_KIND_TO_EVENT_TYPE: Partial<
  Record<BuilderOperationKind, BuilderTelemetryEvent['eventType']>
> = {
  'insert-node': 'component.inserted',
  'move-node': 'component.moved',
  'update-props': 'component.configured',
  'mount-document': 'document.loaded',
  'save': 'codegen.completed',
} as const;

/**
 * Adapts a fine-grained `BuilderOperationEvent` to the coarser-grained
 * `BuilderTelemetryEvent` expected by `@ghatana/platform-events/observability`.
 *
 * Use this when forwarding builder events to a shared platform telemetry sink.
 */
export function toBuilderTelemetryEvent(
  event: BuilderOperationEvent,
  componentCount: number,
  documentSize?: number,
): BuilderTelemetryEvent | null {
  const eventType = OPERATION_KIND_TO_EVENT_TYPE[event.kind];
  if (!eventType) return null;
  return {
    eventType,
    componentCount,
    operationDurationMs: event.durationMs,
    documentSize,
  };
}

/** No-op sink — useful for testing and environments with no telemetry. */
export const noopTelemetrySink: BuilderTelemetrySink = {
  emit: () => undefined,
  flush: () => Promise.resolve(),
};

// ============================================================================
// Telemetry Helper
// ============================================================================

/** Records an operation duration and emits to the given sink. */
export async function withTelemetry<T>(
  sink: BuilderTelemetrySink,
  event: Omit<BuilderOperationEvent, 'durationMs' | 'success' | 'errorCode' | 'timestamp'>,
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
