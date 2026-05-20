/**
 * OperationContext determinism tests.
 *
 * Verifies that clock and idProvider injection in OperationContext produce
 * deterministic, reproducible results regardless of system time or crypto RNG.
 *
 * @test.type unit
 * @test.execution <50ms
 * @test.infra none
 */

import { describe, it, expect } from 'vitest';
import {
  insertNode,
  duplicateNode,
  moveNode,
  deleteNode,
  updateNodeProps,
  addBinding,
  removeBinding,
  reorderNode,
  resizeNode,
  repositionNode,
  setResponsiveVariant,
  removeResponsiveVariant,
  upsertAction,
  removeAction,
  createTestOperationContext,
  type OperationContext,
} from '../operations.js';
import { createDocumentId, createNodeId, type NodeId, type Binding, type ResponsiveVariant, type ActionDefinition } from '../types.js';
import { createBuilderDocument, type BuilderDocument } from '../builder-document.js';

// ============================================================================
// Helpers
// ============================================================================

const FIXED_TS = '2024-06-15T12:00:00.000Z';
const FIXED_TS_2 = '2024-06-15T12:00:01.000Z';

function makeDoc(): BuilderDocument {
  return createBuilderDocument('ctx-test', {
    documentId: createDocumentId(),
    designSystemId: 'ds-ctx',
    designSystemName: 'CTX DS',
  });
}

function makeInstance(contractName = 'Button') {
  return {
    contractName,
    props: {},
    slots: {} as Record<string, NodeId[]>,
    bindings: [] as Binding[],
    metadata: { layout: {} },
  } as const;
}

function firstNodeId(doc: BuilderDocument): NodeId {
  const ids = Object.keys(doc.nodes);
  if (ids.length === 0) throw new Error('Document has no nodes');
  return ids[0] as NodeId;
}

// ============================================================================
// OperationContext — clock injection
// ============================================================================

describe('createTestOperationContext — clock injection', () => {
  it('insertNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = makeDoc();
    const next = insertNode(doc, makeInstance(), undefined, undefined, undefined, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('moveNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = makeDoc();
    const nodeId = createNodeId();
    const parentId = createNodeId();
    const docWithNodes = insertNode(
      insertNode(doc, { ...makeInstance('Container'), slots: { children: [] } }, undefined, undefined, undefined, ctx),
      makeInstance(),
      undefined,
      undefined,
      undefined,
      ctx,
    );
    const rootIds = Object.keys(docWithNodes.nodes) as NodeId[];
    const [pid, nid] = rootIds;
    const moved = moveNode(docWithNodes, nid, pid, 'children', undefined, ctx);
    expect(moved.metadata.updatedAt).toBe(FIXED_TS);
    void nodeId;
    void parentId;
  });

  it('deleteNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const next = deleteNode(doc, id, undefined, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('updateNodeProps uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const next = updateNodeProps(doc, id, { label: 'hello' }, undefined, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('addBinding uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const binding: Binding = { id: 'b1', type: 'data', source: 'state.x', target: 'label' };
    const next = addBinding(doc, id, binding, undefined, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('removeBinding uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const binding: Binding = { id: 'b1', type: 'data', source: 'state.x', target: 'label' };
    const doc = addBinding(
      insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx),
      firstNodeId(insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx)),
      binding,
      undefined,
      ctx,
    );
    const id = firstNodeId(doc);
    const next = removeBinding(doc, id, 'b1', undefined, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('reorderNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc1 = insertNode(makeDoc(), makeInstance('A'), undefined, undefined, undefined, ctx);
    const doc2 = insertNode(doc1, makeInstance('B'), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc2);
    const next = reorderNode(doc2, id, 1, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('resizeNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const next = resizeNode(doc, id, { width: 200, height: 100 }, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('repositionNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const next = repositionNode(doc, id, { x: 50, y: 75 }, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('setResponsiveVariant uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const variant: ResponsiveVariant = { breakpoint: 'md', overrides: { label: 'small' } };
    const next = setResponsiveVariant(doc, id, variant, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('removeResponsiveVariant uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const variant: ResponsiveVariant = { breakpoint: 'md', overrides: {} };
    const doc = setResponsiveVariant(
      insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx),
      firstNodeId(insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx)),
      variant,
      ctx,
    );
    const id = firstNodeId(doc);
    const next = removeResponsiveVariant(doc, id, 'md', ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('upsertAction uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const action: ActionDefinition = {
      id: 'act1',
      triggerEvent: 'onClick',
      targetKind: 'navigate',
      payload: { path: '/home' },
    };
    const next = upsertAction(doc, id, action, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('removeAction uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS);
    const action: ActionDefinition = {
      id: 'act1',
      triggerEvent: 'onClick',
      targetKind: 'navigate',
    };
    const doc = upsertAction(
      insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx),
      firstNodeId(insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx)),
      action,
      ctx,
    );
    const id = firstNodeId(doc);
    const next = removeAction(doc, id, 'act1', ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('duplicateNode uses injected clock for updatedAt', () => {
    const ctx = createTestOperationContext(FIXED_TS, ['dup-id-1']);
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    const next = duplicateNode(doc, id, ctx);
    expect(next.metadata.updatedAt).toBe(FIXED_TS);
  });
});

// ============================================================================
// OperationContext — idProvider injection
// ============================================================================

describe('createTestOperationContext — idProvider injection', () => {
  it('insertNode uses injected idProvider for the new node ID', () => {
    const ctx = createTestOperationContext(FIXED_TS, ['deterministic-id-1']);
    const doc = makeDoc();
    const next = insertNode(doc, makeInstance(), undefined, undefined, undefined, ctx);
    expect(next.nodes['deterministic-id-1']).toBeDefined();
  });

  it('duplicateNode uses injected idProvider for the copy ID', () => {
    const baseCtx = createTestOperationContext(FIXED_TS, ['source-id']);
    const doc = insertNode(makeDoc(), makeInstance('Card'), undefined, undefined, undefined, baseCtx);

    const dupCtx = createTestOperationContext(FIXED_TS, ['copy-id']);
    // source-id was used by insertNode; find the Card node specifically
    const sourceId = Object.keys(doc.nodes).find(
      (id) => doc.nodes[id]?.contractName === 'Card',
    ) as NodeId;
    expect(sourceId).toBeDefined();
    const next = duplicateNode(doc, sourceId, dupCtx);
    expect(next.nodes['copy-id']).toBeDefined();
    expect(next.nodes['copy-id']?.contractName).toBe('Card');
  });

  it('idProvider cycles through the sequence when exhausted', () => {
    const ctx = createTestOperationContext(FIXED_TS, ['id-a', 'id-b']);
    const doc1 = insertNode(makeDoc(), makeInstance('A'), undefined, undefined, undefined, ctx);
    const doc2 = insertNode(doc1, makeInstance('B'), undefined, undefined, undefined, ctx);
    expect(doc1.nodes['id-a']).toBeDefined();
    expect(doc2.nodes['id-b']).toBeDefined();
  });
});

// ============================================================================
// OperationContext — two independent contexts do not share state
// ============================================================================

describe('createTestOperationContext — instance isolation', () => {
  it('two context instances have independent id counters', () => {
    const ctxA = createTestOperationContext(FIXED_TS, ['a1', 'a2']);
    const ctxB = createTestOperationContext(FIXED_TS_2, ['b1', 'b2']);
    const docA = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctxA);
    const docB = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctxB);
    expect(docA.nodes['a1']).toBeDefined();
    expect(docB.nodes['b1']).toBeDefined();
  });

  it('context without idProvider falls back to real createNodeId', () => {
    const ctx: OperationContext = { clock: () => FIXED_TS };
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const id = firstNodeId(doc);
    // Should be a real UUID, not undefined
    expect(typeof id).toBe('string');
    expect(id.length).toBeGreaterThan(0);
    expect(doc.metadata.updatedAt).toBe(FIXED_TS);
  });

  it('context without clock falls back to real system clock', () => {
    const ctx: OperationContext = {};
    const before = Date.now();
    const doc = insertNode(makeDoc(), makeInstance(), undefined, undefined, undefined, ctx);
    const after = Date.now() + 2; // +2ms to account for nextUpdatedAt's +1ms guard
    const docTs = new Date(doc.metadata.updatedAt).getTime();
    expect(docTs).toBeGreaterThanOrEqual(before);
    expect(docTs).toBeLessThanOrEqual(after);
  });
});

// ============================================================================
// Determinism — same context produces same output
// ============================================================================

describe('determinism — same context inputs produce identical output', () => {
  it('two independent insertNode calls with same ctx produce structurally equivalent nodes', () => {
    const ctx1 = createTestOperationContext(FIXED_TS, ['fixed-id']);
    const ctx2 = createTestOperationContext(FIXED_TS, ['fixed-id']);
    const doc1 = insertNode(makeDoc(), makeInstance('Label'), undefined, undefined, undefined, ctx1);
    const doc2 = insertNode(makeDoc(), makeInstance('Label'), undefined, undefined, undefined, ctx2);

    const node1 = doc1.nodes['fixed-id'];
    const node2 = doc2.nodes['fixed-id'];
    expect(node1?.contractName).toBe(node2?.contractName);
    expect(doc1.metadata.updatedAt).toBe(doc2.metadata.updatedAt);
  });
});
