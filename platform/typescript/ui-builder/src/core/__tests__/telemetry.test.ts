import { describe, it, expect, vi } from 'vitest';
import {
  captureSnapshot,
  RollbackHistory,
  withTelemetry,
  toBuilderTelemetryEvent,
  noopTelemetrySink,
} from '../telemetry.js';
import type { BuilderOperationEvent, BuilderTelemetrySink } from '../telemetry.js';
import type { BuilderDocument } from '../types.js';
import { createDocumentId, createNodeId } from '../types.js';

function makeDoc(): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Test',
    designSystem: {
      id: 'ds-1',
      name: 'DS',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'theme-1',
    },
    rootNodes: [],
    nodes: new Map(),
    metadata: { createdAt: '', updatedAt: '' },
  };
}

function makeEvent(kind: BuilderOperationEvent['kind'], success = true): BuilderOperationEvent {
  return {
    kind,
    documentId: 'doc-1',
    sessionId: 'sess-1',
    timestamp: Date.now(),
    durationMs: 12,
    success,
  };
}

// ============================================================================
// captureSnapshot
// ============================================================================

describe('captureSnapshot', () => {
  it('returns a snapshot with the given label', () => {
    const doc = makeDoc();
    const snap = captureSnapshot(doc, 'before-delete');
    expect(snap.label).toBe('before-delete');
    expect(snap.documentId).toBe(doc.id);
    expect(snap.snapshotId).toMatch(/^snap-/);
  });

  it('stores the document reference', () => {
    const doc = makeDoc();
    const snap = captureSnapshot(doc, 'x');
    expect(snap.document).toBe(doc);
  });
});

// ============================================================================
// RollbackHistory
// ============================================================================

describe('RollbackHistory', () => {
  it('pushes snapshots and retrieves them', () => {
    const history = new RollbackHistory(10);
    const doc = makeDoc();
    history.push(captureSnapshot(doc, 'a'));
    history.push(captureSnapshot(doc, 'b'));
    expect(history.all()).toHaveLength(2);
    expect(history.latest()?.label).toBe('b');
  });

  it('evicts oldest when max is exceeded', () => {
    const history = new RollbackHistory(2);
    const doc = makeDoc();
    history.push(captureSnapshot(doc, 'a'));
    history.push(captureSnapshot(doc, 'b'));
    history.push(captureSnapshot(doc, 'c'));
    expect(history.all()).toHaveLength(2);
    expect(history.all()[0]?.label).toBe('b');
  });

  it('findById returns the correct snapshot', () => {
    const history = new RollbackHistory();
    const doc = makeDoc();
    const snap = captureSnapshot(doc, 'target');
    history.push(snap);
    const found = history.findById(snap.snapshotId);
    expect(found?.label).toBe('target');
  });

  it('trimAfter removes snapshots after the given id', () => {
    const history = new RollbackHistory();
    const doc = makeDoc();
    const a = captureSnapshot(doc, 'a');
    const b = captureSnapshot(doc, 'b');
    const c = captureSnapshot(doc, 'c');
    history.push(a);
    history.push(b);
    history.push(c);
    history.trimAfter(b.snapshotId);
    expect(history.all().map((s) => s.label)).toEqual(['a', 'b']);
  });

  it('export/import round-trips correctly', () => {
    const history = new RollbackHistory();
    const doc = makeDoc();
    history.push(captureSnapshot(doc, 'export-me'));
    const json = history.export();
    const restored = new RollbackHistory();
    restored.import(json);
    expect(restored.latest()?.label).toBe('export-me');
  });
});

// ============================================================================
// withTelemetry
// ============================================================================

describe('withTelemetry', () => {
  it('emits a success event on successful operation', async () => {
    const emitted: BuilderOperationEvent[] = [];
    const sink: BuilderTelemetrySink = {
      emit: (e) => emitted.push(e),
      flush: () => Promise.resolve(),
    };

    await withTelemetry(
      sink,
      { kind: 'insert-node', documentId: 'doc-1', sessionId: 's1' },
      () => 'result',
    );

    expect(emitted).toHaveLength(1);
    expect(emitted[0]?.success).toBe(true);
    expect(emitted[0]?.kind).toBe('insert-node');
    expect(emitted[0]?.durationMs).toBeGreaterThanOrEqual(0);
  });

  it('emits a failure event and rethrows on error', async () => {
    const emitted: BuilderOperationEvent[] = [];
    const sink: BuilderTelemetrySink = {
      emit: (e) => emitted.push(e),
      flush: () => Promise.resolve(),
    };

    await expect(
      withTelemetry(
        sink,
        { kind: 'delete-node', documentId: 'doc-1', sessionId: 's1' },
        () => { throw new Error('Boom'); },
      ),
    ).rejects.toThrow('Boom');

    expect(emitted[0]?.success).toBe(false);
    expect(emitted[0]?.errorCode).toBe('Error');
  });

  it('noopTelemetrySink does not throw', async () => {
    await expect(
      withTelemetry(
        noopTelemetrySink,
        { kind: 'save', documentId: 'doc', sessionId: 's' },
        () => 42,
      ),
    ).resolves.toBe(42);
  });
});

// ============================================================================
// toBuilderTelemetryEvent (5.5 bridge)
// ============================================================================

describe('toBuilderTelemetryEvent', () => {
  it('maps insert-node → component.inserted', () => {
    const event = makeEvent('insert-node');
    const mapped = toBuilderTelemetryEvent(event, 5);
    expect(mapped?.eventType).toBe('component.inserted');
    expect(mapped?.componentCount).toBe(5);
    expect(mapped?.operationDurationMs).toBe(12);
  });

  it('maps move-node → component.moved', () => {
    const event = makeEvent('move-node');
    expect(toBuilderTelemetryEvent(event, 1)?.eventType).toBe('component.moved');
  });

  it('maps update-props → component.configured', () => {
    const event = makeEvent('update-props');
    expect(toBuilderTelemetryEvent(event, 0)?.eventType).toBe('component.configured');
  });

  it('maps mount-document → document.loaded', () => {
    const event = makeEvent('mount-document');
    expect(toBuilderTelemetryEvent(event, 0)?.eventType).toBe('document.loaded');
  });

  it('returns null for unmapped kinds', () => {
    const event = makeEvent('add-binding');
    expect(toBuilderTelemetryEvent(event, 0)).toBeNull();
  });

  it('includes documentSize when provided', () => {
    const event = makeEvent('insert-node');
    const mapped = toBuilderTelemetryEvent(event, 3, 4096);
    expect(mapped?.documentSize).toBe(4096);
  });
});
