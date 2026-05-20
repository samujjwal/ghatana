import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import {
  InMemoryPersistenceAdapter,
  AutosaveOrchestrator,
  serializeDocument,
  deserializeDocument,
  recoverSession,
} from '../persistence.js';
import type { BuilderDocument } from '../types.js';
import { createDocumentId, createNodeId } from '../types.js';

// ============================================================================
// Test fixtures
// ============================================================================

function makeDoc(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Test Document',
    designSystem: {
      id: 'ds-1',
      name: 'Test DS',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'theme-1',
    },
    rootNodes: [],
    nodes: {},
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    ...overrides,
  };
}

// ============================================================================
// serialization round-trip
// ============================================================================

describe('serializeDocument / deserializeDocument', () => {
  it('round-trips a document with nodes', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: {
        [id]: {
          id,
          contractName: 'Button',
          props: { label: 'Click me' },
          slots: {},
          bindings: [],
          metadata: {},
        },
      },
    });

    const serialized = serializeDocument(doc);
    expect(serialized.nodes).toBeTypeOf('object');
    expect(serialized.nodes[id]).toBeDefined();

    const restored = deserializeDocument(serialized);
    // nodes is a canonical Record — not a Map instance.
    expect(restored.nodes[id]).toBeDefined();
    expect(restored.nodes[id]?.contractName).toBe('Button');
    expect(restored.rootNodes).toEqual([id]);
  });

  it('preserves document metadata through round-trip', () => {
    const doc = makeDoc({ name: 'My Page' });
    const restored = deserializeDocument(serializeDocument(doc));
    // In the canonical model, 'name' derives from metadata.description.
    expect(restored.name).toBe('My Page');
    // schemaVersion is always the canonical version constant.
    expect(restored.schemaVersion).toBe('1.0.0');
  });
});

// ============================================================================
// InMemoryPersistenceAdapter
// ============================================================================

describe('InMemoryPersistenceAdapter', () => {
  let adapter: InMemoryPersistenceAdapter;

  beforeEach(() => {
    adapter = new InMemoryPersistenceAdapter();
  });

  it('saves and loads a document', async () => {
    const doc = makeDoc({ name: 'Persist Me' });
    await adapter.save(doc, 'initial');
    const loaded = await adapter.load(doc.id);
    expect(loaded).not.toBeNull();
    expect(loaded!.name).toBe('Persist Me');
  });

  it('returns null for unknown document ID', async () => {
    const result = await adapter.load('does-not-exist' as ReturnType<typeof createDocumentId>);
    expect(result).toBeNull();
  });

  it('lists versions in newest-first order', async () => {
    const doc = makeDoc();
    await adapter.save(doc, 'v1');
    await adapter.save(doc, 'v2');
    const versions = await adapter.listVersions(doc.id);
    expect(versions).toHaveLength(2);
    expect(versions[0]?.label).toBe('v2');
    expect(versions[1]?.label).toBe('v1');
  });

  it('restores a specific version', async () => {
    const doc1 = makeDoc({ name: 'Original' });
    const doc2 = { ...doc1, name: 'Mutated' };
    const v1 = await adapter.save(doc1, 'v1');
    await adapter.save(doc2 as BuilderDocument, 'v2');

    const restored = await adapter.restoreVersion(doc1.id, v1);
    expect(restored).not.toBeNull();
    expect(restored!.name).toBe('Original');
  });

  it('deletes a version', async () => {
    const doc = makeDoc();
    const vId = await adapter.save(doc, 'to-delete');
    await adapter.deleteVersion(doc.id, vId);
    const versions = await adapter.listVersions(doc.id);
    expect(versions.find((v) => v.versionId === vId)).toBeUndefined();
  });

  it('clears all document data', async () => {
    const doc = makeDoc();
    await adapter.save(doc, 'label');
    await adapter.clearDocument(doc.id);
    expect(await adapter.load(doc.id)).toBeNull();
    expect(await adapter.listVersions(doc.id)).toHaveLength(0);
  });
});

// ============================================================================
// AutosaveOrchestrator
// ============================================================================

describe('AutosaveOrchestrator', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('saves after debounce delay', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const onSaved = vi.fn();
    const orchestrator = new AutosaveOrchestrator(adapter, {
      debounceMs: 500,
      labelFn: () => 'auto',
      onSaved,
    });

    const doc = makeDoc();
    orchestrator.schedule(doc);
    expect(onSaved).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(600);
    expect(onSaved).toHaveBeenCalledOnce();
    orchestrator.dispose();
  });

  it('debounces rapid calls — only saves once', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const onSaved = vi.fn();
    const orchestrator = new AutosaveOrchestrator(adapter, {
      debounceMs: 500,
      labelFn: () => 'auto',
      onSaved,
    });

    const doc = makeDoc();
    orchestrator.schedule(doc);
    orchestrator.schedule(doc);
    orchestrator.schedule(doc);

    await vi.advanceTimersByTimeAsync(600);
    expect(onSaved).toHaveBeenCalledOnce();
    orchestrator.dispose();
  });

  it('flush() forces immediate save', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const onSaved = vi.fn();
    const orchestrator = new AutosaveOrchestrator(adapter, {
      debounceMs: 5000,
      labelFn: () => 'flush',
      onSaved,
    });

    const doc = makeDoc();
    orchestrator.schedule(doc);
    await orchestrator.flush();

    expect(onSaved).toHaveBeenCalledOnce();
    const loaded = await adapter.load(doc.id);
    expect(loaded).not.toBeNull();
    orchestrator.dispose();
  });

  it('dispose() cancels pending save', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const onSaved = vi.fn();
    const orchestrator = new AutosaveOrchestrator(adapter, {
      debounceMs: 500,
      labelFn: () => 'auto',
      onSaved,
    });

    const doc = makeDoc();
    orchestrator.schedule(doc);
    orchestrator.dispose();

    await vi.advanceTimersByTimeAsync(600);
    expect(onSaved).not.toHaveBeenCalled();
  });
});

// ============================================================================
// recoverSession
// ============================================================================

describe('recoverSession', () => {
  it('returns null when no session exists', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const result = await recoverSession('unknown-id' as ReturnType<typeof createDocumentId>, adapter);
    expect(result).toBeNull();
  });

  it('returns the saved document when a session exists', async () => {
    const adapter = new InMemoryPersistenceAdapter();
    const doc = makeDoc({ name: 'Recovered' });
    await adapter.save(doc, 'session');
    const result = await recoverSession(doc.id, adapter);
    expect(result?.name).toBe('Recovered');
  });
});
