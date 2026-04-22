/**
 * @ghatana/ui-builder/core document operations test suite
 * Unit tests for insertNode, moveNode, deleteNode, updateNodeProps,
 * addBinding, removeBinding — including OperationEventBus emission.
 *
 * @test.type unit
 * @test.execution <100ms
 * @test.infra none
 */

import { describe, it, expect, vi } from 'vitest';
import {
  insertNode,
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
  batchUpdate,
  createUndoStack,
  mergeDocuments,
  lastWriteWins,
  noopEventBus,
  type OperationEventBus,
} from '../operations.js';
import {
  createDocumentId,
  createNodeId,
  type BuilderDocument,
  type ComponentInstance,
  type Binding,
  type NodeId,
  type ActionDefinition,
  type ResponsiveVariant,
} from '../types.js';

// ============================================================================
// Test helpers
// ============================================================================

function makeDoc(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Ops Test Doc',
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
    metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z' },
    ...overrides,
  };
}

function makeInstance(
  id: NodeId = createNodeId(),
  contractName = 'Button',
  props: Record<string, unknown> = {}
): ComponentInstance {
  return {
    id,
    contractName,
    props,
    slots: {},
    bindings: [],
    metadata: { layout: {} },
  };
}

function makeBinding(id = 'b1', target = 'label', type = 'data' as Binding['type']): Binding {
  return {
    id,
    type,
    source: 'state.label',
    target
  };
}

// ============================================================================
// insertNode
// ============================================================================

describe('insertNode', () => {
  it('adds the node to rootNodes when no parent is specified', () => {
    const doc = makeDoc();
    const next = insertNode(doc, { contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { layout: {} } });

    expect(next.rootNodes).toHaveLength(1);
    expect(next.nodes.size).toBe(1);
  });

  it('does not mutate the original document (immutability)', () => {
    const doc = makeDoc();
    insertNode(doc, { contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { layout: {} } });
    expect(doc.rootNodes).toHaveLength(0);
    expect(doc.nodes.size).toBe(0);
  });

  it('assigns a new unique NodeId to the inserted instance', () => {
    const doc = makeDoc();
    const next = insertNode(doc, { contractName: 'Card', props: {}, slots: {}, bindings: [], metadata: { layout: {} } });
    const nodeId = next.rootNodes[0];
    expect(nodeId).toBeDefined();
    expect(next.nodes.has(nodeId!)).toBe(true);
  });

  it('adds node into the specified parent slot', () => {
    const parentId = createNodeId();
    const parent = makeInstance(parentId, 'Container');
    const nodes = new Map([[parentId, parent]]);
    const doc = makeDoc({ nodes, rootNodes: [parentId] });

    const next = insertNode(
      doc,
      { contractName: 'Text', props: {}, slots: {}, bindings: [], metadata: { layout: {} } },
      parentId,
      'children',
    );

    const parentNode = next.nodes.get(parentId)!;
    expect(parentNode.slots['children']).toHaveLength(1);
    expect(next.rootNodes).toHaveLength(1); // parent still in root
  });

  it('updates metadata.updatedAt on insert', () => {
    const doc = makeDoc();
    const next = insertNode(doc, { contractName: 'Icon', props: {}, slots: {}, bindings: [], metadata: { layout: {} } });
    expect(next.metadata.updatedAt).not.toBe(doc.metadata.updatedAt);
  });

  it('emits onNodeInserted with correct payload', () => {
    const bus: OperationEventBus = { onNodeInserted: vi.fn() };
    const doc = makeDoc();
    insertNode(doc, { contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { layout: {} } }, undefined, undefined, bus);

    expect(bus.onNodeInserted).toHaveBeenCalledOnce();
    const payload = (bus.onNodeInserted as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.documentId).toBe(doc.id);
    expect(payload.contractName).toBe('Button');
    expect(payload.parentId).toBeUndefined();
  });

  it('emits onNodeInserted with parentId and slotName', () => {
    const parentId = createNodeId();
    const parent = makeInstance(parentId);
    const doc = makeDoc({ nodes: new Map([[parentId, parent]]), rootNodes: [parentId] });
    const bus: OperationEventBus = { onNodeInserted: vi.fn() };

    insertNode(doc, { contractName: 'Icon', props: {}, slots: {}, bindings: [], metadata: { layout: {} } }, parentId, 'prefix', bus);

    const payload = (bus.onNodeInserted as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.parentId).toBe(parentId);
    expect(payload.slotName).toBe('prefix');
  });

  it('does not throw when no bus is provided', () => {
    const doc = makeDoc();
    expect(() => insertNode(doc, { contractName: 'X', props: {}, slots: {}, bindings: [], metadata: { layout: {} } })).not.toThrow();
  });
});

// ============================================================================
// moveNode
// ============================================================================

describe('moveNode', () => {
  it('moves a node from root to a parent slot', () => {
    const nodeId = createNodeId();
    const parentId = createNodeId();
    const parent = makeInstance(parentId, 'Container');
    const node = makeInstance(nodeId);
    const doc = makeDoc({
      nodes: new Map([[nodeId, node], [parentId, parent]]),
      rootNodes: [nodeId, parentId],
    });

    const next = moveNode(doc, nodeId, parentId, 'children');

    expect(next.rootNodes).not.toContain(nodeId);
    expect(next.nodes.get(parentId)!.slots['children']).toContain(nodeId);
  });

  it('moves a node from a slot back to root (newParentId=null)', () => {
    const nodeId = createNodeId();
    const parentId = createNodeId();
    const parent = makeInstance(parentId, 'Container');
    parent.slots['children'] = [nodeId];
    const node = makeInstance(nodeId);
    const doc = makeDoc({
      nodes: new Map([[nodeId, node], [parentId, parent]]),
      rootNodes: [parentId],
    });

    const next = moveNode(doc, nodeId, null);

    expect(next.rootNodes).toContain(nodeId);
    expect(next.nodes.get(parentId)!.slots['children']).not.toContain(nodeId);
  });

  it('does not mutate the original document', () => {
    const nodeId = createNodeId();
    const parentId = createNodeId();
    const parent = makeInstance(parentId);
    const node = makeInstance(nodeId);
    const doc = makeDoc({
      nodes: new Map([[nodeId, node], [parentId, parent]]),
      rootNodes: [nodeId, parentId],
    });
    moveNode(doc, nodeId, parentId, 'children');
    expect(doc.rootNodes).toContain(nodeId);
  });

  it('emits onNodeMoved with correct payload', () => {
    const nodeId = createNodeId();
    const parentId = createNodeId();
    const parent = makeInstance(parentId);
    const node = makeInstance(nodeId);
    const doc = makeDoc({
      nodes: new Map([[nodeId, node], [parentId, parent]]),
      rootNodes: [nodeId, parentId],
    });
    const bus: OperationEventBus = { onNodeMoved: vi.fn() };

    moveNode(doc, nodeId, parentId, 'slot', bus);

    expect(bus.onNodeMoved).toHaveBeenCalledOnce();
    const payload = (bus.onNodeMoved as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.documentId).toBe(doc.id);
    expect(payload.nodeId).toBe(nodeId);
    expect(payload.newParentId).toBe(parentId);
    expect(payload.newSlotName).toBe('slot');
  });
});

// ============================================================================
// deleteNode
// ============================================================================

describe('deleteNode', () => {
  it('removes the node from the document', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });

    const next = deleteNode(doc, nodeId);

    expect(next.nodes.has(nodeId)).toBe(false);
    expect(next.rootNodes).not.toContain(nodeId);
  });

  it('recursively deletes child nodes', () => {
    const childId = createNodeId();
    const parentId = createNodeId();
    const child = makeInstance(childId);
    const parent = makeInstance(parentId, 'Container');
    parent.slots['children'] = [childId];
    const doc = makeDoc({
      nodes: new Map([[parentId, parent], [childId, child]]),
      rootNodes: [parentId],
    });

    const next = deleteNode(doc, parentId);

    expect(next.nodes.has(parentId)).toBe(false);
    expect(next.nodes.has(childId)).toBe(false);
    expect(next.rootNodes).not.toContain(parentId);
  });

  it('does not mutate the original document', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    deleteNode(doc, nodeId);
    expect(doc.nodes.has(nodeId)).toBe(true);
  });

  it('removes the node reference from its parent slot', () => {
    const childId = createNodeId();
    const parentId = createNodeId();
    const child = makeInstance(childId);
    const parent = makeInstance(parentId, 'Container');
    parent.slots['children'] = [childId];
    const doc = makeDoc({
      nodes: new Map([[parentId, parent], [childId, child]]),
      rootNodes: [parentId],
    });

    const next = deleteNode(doc, childId);

    expect(next.nodes.get(parentId)!.slots['children']).not.toContain(childId);
  });

  it('emits onNodeDeleted with correct deletedCount', () => {
    const childId = createNodeId();
    const parentId = createNodeId();
    const child = makeInstance(childId);
    const parent = makeInstance(parentId, 'Container');
    parent.slots['children'] = [childId];
    const doc = makeDoc({
      nodes: new Map([[parentId, parent], [childId, child]]),
      rootNodes: [parentId],
    });
    const bus: OperationEventBus = { onNodeDeleted: vi.fn() };

    deleteNode(doc, parentId, bus);

    const payload = (bus.onNodeDeleted as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.nodeId).toBe(parentId);
    expect(payload.deletedCount).toBe(2); // parent + child
  });
});

// ============================================================================
// updateNodeProps
// ============================================================================

describe('updateNodeProps', () => {
  it('merges new props into existing props', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId, 'Button', { label: 'Click me', disabled: false });
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });

    const next = updateNodeProps(doc, nodeId, { label: 'Submit', loading: true });

    const updated = next.nodes.get(nodeId)!;
    expect(updated.props['label']).toBe('Submit');
    expect(updated.props['disabled']).toBe(false); // preserved
    expect(updated.props['loading']).toBe(true); // new
  });

  it('does not mutate the original document', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId, 'Button', { label: 'Old' });
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    updateNodeProps(doc, nodeId, { label: 'New' });
    expect(doc.nodes.get(nodeId)!.props['label']).toBe('Old');
  });

  it('is a no-op for unknown node IDs', () => {
    const doc = makeDoc();
    const unknownId = createNodeId();
    const next = updateNodeProps(doc, unknownId, { label: 'X' });
    expect(next.nodes.size).toBe(0);
  });

  it('emits onNodePropsUpdated with updatedKeys', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId, 'Input', {});
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    const bus: OperationEventBus = { onNodePropsUpdated: vi.fn() };

    updateNodeProps(doc, nodeId, { value: 'hello', placeholder: 'Enter...' }, bus);

    const payload = (bus.onNodePropsUpdated as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.nodeId).toBe(nodeId);
    expect(payload.updatedKeys).toEqual(expect.arrayContaining(['value', 'placeholder']));
    expect(payload.updatedKeys).toHaveLength(2);
  });
});

// ============================================================================
// addBinding
// ============================================================================

describe('addBinding', () => {
  it('appends a binding to the node', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    const binding = makeBinding('bind-1', 'label', 'data');

    const next = addBinding(doc, nodeId, binding);

    expect(next.nodes.get(nodeId)!.bindings).toHaveLength(1);
    expect(next.nodes.get(nodeId)!.bindings[0]!.id).toBe('bind-1');
  });

  it('does not mutate the original document', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    addBinding(doc, nodeId, makeBinding());
    expect(doc.nodes.get(nodeId)!.bindings).toHaveLength(0);
  });

  it('preserves existing bindings when adding a new one', () => {
    const nodeId = createNodeId();
    const existing = makeBinding('b0', 'visible', 'event');
    const node = makeInstance(nodeId, 'Button', {});
    node.bindings = [existing];
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });

    const next = addBinding(doc, nodeId, makeBinding('b1', 'label', 'data'));

    expect(next.nodes.get(nodeId)!.bindings).toHaveLength(2);
  });

  it('emits onBindingAdded with correct payload', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    const binding = makeBinding('b-emit', 'title', 'state');
    const bus: OperationEventBus = { onBindingAdded: vi.fn() };

    addBinding(doc, nodeId, binding, bus);

    const payload = (bus.onBindingAdded as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.bindingId).toBe('b-emit');
    expect(payload.propPath).toBe('title'); // binding.target
    expect(payload.kind).toBe('state'); // binding.type // binding.type
  });
});

// ============================================================================
// removeBinding
// ============================================================================

describe('removeBinding', () => {
  it('removes the binding from the node by ID', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    const binding = makeBinding('bind-to-remove', 'label', 'data');
    node.bindings = [binding];
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });

    const next = removeBinding(doc, nodeId, 'bind-to-remove');

    expect(next.nodes.get(nodeId)!.bindings).toHaveLength(0);
  });

  it('does not remove unrelated bindings', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    node.bindings = [makeBinding('keep', 'label', 'data'), makeBinding('remove', 'visible', 'event')];
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });

    const next = removeBinding(doc, nodeId, 'remove');

    expect(next.nodes.get(nodeId)!.bindings).toHaveLength(1);
    expect(next.nodes.get(nodeId)!.bindings[0]!.id).toBe('keep');
  });

  it('does not mutate the original document', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    node.bindings = [makeBinding('b1')];
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    removeBinding(doc, nodeId, 'b1');
    expect(doc.nodes.get(nodeId)!.bindings).toHaveLength(1);
  });

  it('emits onBindingRemoved with correct payload', () => {
    const nodeId = createNodeId();
    const node = makeInstance(nodeId);
    node.bindings = [makeBinding('b-rm')];
    const doc = makeDoc({ nodes: new Map([[nodeId, node]]), rootNodes: [nodeId] });
    const bus: OperationEventBus = { onBindingRemoved: vi.fn() };

    removeBinding(doc, nodeId, 'b-rm', bus);

    const payload = (bus.onBindingRemoved as ReturnType<typeof vi.fn>).mock.calls[0][0];
    expect(payload.bindingId).toBe('b-rm');
    expect(payload.nodeId).toBe(nodeId);
  });
});

// ============================================================================
// noopEventBus
// ============================================================================

describe('noopEventBus', () => {
  it('is an empty object (no handlers)', () => {
    expect(Object.keys(noopEventBus)).toHaveLength(0);
  });

  it('does not throw when passed noopEventBus', () => {
    const doc = makeDoc();
    expect(() =>
      insertNode(doc, { contractNamtractName: 'A', props: {}, slots: {}, bindings: [], metadata: { layout: {} } }, undefined, undefined, noopEventBus),
    ).not.toThrow();
  });
});

// ============================================================================
// reorderNode
// ============================================================================

describe('reorderNode', () => {
  it('reorders root-level nodes', () => {
    const a = makeInstance(createNodeId(), 'A');
    const b = makeInstance(createNodeId(), 'B');
    const c = makeInstance(createNodeId(), 'C');
    const doc = makeDoc({
      rootNodes: [a.id, b.id, c.id],
      nodes: new Map([[a.id, a], [b.id, b], [c.id, c]]),
    });
    const next = reorderNode(doc, c.id, 0);
    expect(next.rootNodes).toEqual([c.id, a.id, b.id]);
  });

  it('reorders a node within its parent slot', () => {
    const parent = makeInstance(createNodeId(), 'Container');
    const child1 = makeInstance(createNodeId(), 'A');
    const child2 = makeInstance(createNodeId(), 'B');
    parent.slots = { default: [child1.id, child2.id] };
    const doc = makeDoc({
      rootNodes: [parent.id],
      nodes: new Map([[parent.id, parent], [child1.id, child1], [child2.id, child2]]),
    });
    const next = reorderNode(doc, child2.id, 0);
    expect(next.nodes.get(parent.id)!.slots['default']).toEqual([child2.id, child1.id]);
  });

  it('clamps to-index to the end of the list', () => {
    const a = makeInstance(createNodeId(), 'A');
    const b = makeInstance(createNodeId(), 'B');
    const doc = makeDoc({
      rootNodes: [a.id, b.id],
      nodes: new Map([[a.id, a], [b.id, b]]),
    });
    const next = reorderNode(doc, a.id, 99);
    expect(next.rootNodes).toEqual([b.id, a.id]);
  });

  it('is a no-op when node is not found', () => {
    const doc = makeDoc();
    const next = reorderNode(doc, 'nonexistent' as NodeId, 0);
    expect(next.rootNodes).toEqual([]);
  });
});

// ============================================================================
// resizeNode
// ============================================================================

describe('resizeNode', () => {
  it('updates the node size', () => {
    const node = makeInstance(createNodeId(), 'Box');
    const doc = makeDoc({
      rootNodes: [node.id],
      nodes: new Map([[node.id, node]]),
    });
    const next = resizeNode(doc, node.id, { width: 320, height: 180 });
    expect(next.nodes.get(node.id)!.metadata.size).toEqual({ width: 320, height: 180 });
  });

  it('does not mutate original document', () => {
    const node = makeInstance(createNodeId(), 'Box');
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    resizeNode(doc, node.id, { width: 100, height: 100 });
    expect(doc.nodes.get(node.id)!.metadata.size).toBeUndefined();
  });

  it('is a no-op for unknown node', () => {
    const doc = makeDoc();
    expect(() => resizeNode(doc, 'x' as NodeId, { width: 1, height: 1 })).not.toThrow();
  });
});

// ============================================================================
// repositionNode
// ============================================================================

describe('repositionNode', () => {
  it('updates the node position', () => {
    const node = makeInstance(createNodeId(), 'Box');
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = repositionNode(doc, node.id, { x: 50, y: 75 });
    expect(next.nodes.get(node.id)!.metadata.position).toEqual({ x: 50, y: 75 });
  });
});

// ============================================================================
// setResponsiveVariant / removeResponsiveVariant
// ============================================================================

describe('setResponsiveVariant', () => {
  it('adds a responsive variant', () => {
    const node = makeInstance(createNodeId(), 'Text');
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const variant: ResponsiveVariant = { breakpoint: 'md', props: { size: 'lg' } };
    const next = setResponsiveVariant(doc, node.id, variant);
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants).toHaveLength(1);
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants![0].breakpoint).toBe('md');
  });

  it('replaces an existing variant at the same breakpoint', () => {
    const node = makeInstance(createNodeId(), 'Text');
    node.metadata = { layout: {}, responsiveVariants: [{ breakpoint: 'md', props: { size: 'sm' } }] };
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = setResponsiveVariant(doc, node.id, { breakpoint: 'md', props: { size: 'xl' } });
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants).toHaveLength(1);
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants![0].props?.size).toBe('xl');
  });

  it('preserves variants at other breakpoints', () => {
    const node = makeInstance(createNodeId(), 'Text');
    node.metadata = { layout: {}, responsiveVariants: [{ breakpoint: 'sm', props: { size: 'xs' } }] };
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = setResponsiveVariant(doc, node.id, { breakpoint: 'lg', props: { size: '2xl' } });
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants).toHaveLength(2);
  });
});

describe('removeResponsiveVariant', () => {
  it('removes the variant for the specified breakpoint', () => {
    const node = makeInstance(createNodeId(), 'Text');
    node.metadata = { layout: {}, responsiveVariants: [{ breakpoint: 'md', props: {} }, { breakpoint: 'lg', props: {} }] };
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = removeResponsiveVariant(doc, node.id, 'md');
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants).toHaveLength(1);
    expect(next.nodes.get(node.id)!.metadata.responsiveVariants![0].breakpoint).toBe('lg');
  });

  it('is safe when no variants exist', () => {
    const node = makeInstance(createNodeId(), 'Text');
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    expect(() => removeResponsiveVariant(doc, node.id, 'md')).not.toThrow();
  });
});

// ============================================================================
// upsertAction / removeAction
// ============================================================================

function makeAction(id: string, triggerEvent: string): ActionDefinition {
  return { id, label: 'Click', triggerEvent, targetKind: 'event', payload: {}, condition: undefined };
}

describe('upsertAction', () => {
  it('adds an action when none exists', () => {
    const node = makeInstance(createNodeId(), 'Button');
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = upsertAction(doc, node.id, makeAction('a1', 'onClick'));
    expect(next.nodes.get(node.id)!.metadata.actions).toHaveLength(1);
  });

  it('replaces an existing action by id', () => {
    const node = makeInstance(createNodeId(), 'Button');
    node.metadata = { layout: {}, actions: [makeAction('a1', 'onClick')] };
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = upsertAction(doc, node.id, { ...makeAction('a1', 'onDoubleClick'), label: 'DoubleClick' });
    expect(next.nodes.get(node.id)!.metadata.actions).toHaveLength(1);
    expect(next.nodes.get(node.id)!.metadata.actions![0].label).toBe('DoubleClick');
  });
});

describe('removeAction', () => {
  it('removes action by id', () => {
    const node = makeInstance(createNodeId(), 'Button');
    node.metadata = { layout: {}, actions: [makeAction('a1', 'onClick'), makeAction('a2', 'onHover')] };
    const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
    const next = removeAction(doc, node.id, 'a1');
    expect(next.nodes.get(node.id)!.metadata.actions).toHaveLength(1);
    expect(next.nodes.get(node.id)!.metadata.actions![0].id).toBe('a2');
  });
});

// ============================================================================
// batchUpdate
// ============================================================================

describe('batchUpdate', () => {
  it('applies all operations in sequence', () => {
    const nodeA = makeInstance(createNodeId(), 'A');
    const nodeB = makeInstance(createNodeId(), 'B');
    const doc = makeDoc();
    const final = batchUpdate(doc, [
      (d) => insertNode(d, { ...nodeA }),
      (d) => insertNode(d, { ...nodeB }),
    ]);
    expect(final.rootNodes).toHaveLength(2);
  });

  it('returns the original document when given an empty list', () => {
    const doc = makeDoc();
    const next = batchUpdate(doc, []);
    expect(next).toBe(doc);
  });
});

// ============================================================================
// createUndoStack
// ============================================================================

describe('createUndoStack', () => {
  it('starts with canUndo=false and canRedo=false', () => {
    const doc = makeDoc();
    const stack = createUndoStack(doc);
    expect(stack.canUndo).toBe(false);
    expect(stack.canRedo).toBe(false);
    expect(stack.current).toBe(doc);
  });

  it('enables canUndo after push', () => {
    const doc1 = makeDoc();
    const doc2 = insertNode(doc1, makeInstance());
    const stack = createUndoStack(doc1);
    stack.push(doc2);
    expect(stack.canUndo).toBe(true);
    expect(stack.canRedo).toBe(false);
  });

  it('undo returns previous snapshot', () => {
    const doc1 = makeDoc();
    const doc2 = insertNode(doc1, makeInstance());
    const stack = createUndoStack(doc1);
    stack.push(doc2);
    const prev = stack.undo();
    expect(prev).toBe(doc1);
    expect(stack.canUndo).toBe(false);
    expect(stack.canRedo).toBe(true);
  });

  it('redo returns the next snapshot', () => {
    const doc1 = makeDoc();
    const doc2 = insertNode(doc1, makeInstance());
    const stack = createUndoStack(doc1);
    stack.push(doc2);
    stack.undo();
    const redone = stack.redo();
    expect(redone).toBe(doc2);
    expect(stack.canRedo).toBe(false);
  });

  it('undo returns undefined when nothing to undo', () => {
    const stack = createUndoStack(makeDoc());
    expect(stack.undo()).toBeUndefined();
  });

  it('redo returns undefined when nothing to redo', () => {
    const stack = createUndoStack(makeDoc());
    expect(stack.redo()).toBeUndefined();
  });

  it('push discards redo history', () => {
    const doc1 = makeDoc();
    const doc2 = insertNode(doc1, makeInstance());
    const doc3 = insertNode(doc2, makeInstance());
    const stack = createUndoStack(doc1);
    stack.push(doc2);
    stack.undo();
    stack.push(doc3); // should clear redo stack
    expect(stack.canRedo).toBe(false);
  });

  it('respects maxDepth and evicts the oldest snapshot', () => {
    const stack = createUndoStack(makeDoc(), { maxDepth: 2 });
    const doc2 = makeDoc();
    const doc3 = makeDoc();
    const doc4 = makeDoc();
    stack.push(doc2);
    stack.push(doc3);
    stack.push(doc4);
    // undo twice — the 3rd undo should be exhausted (maxDepth 2 keeps only last 2)
    stack.undo();
    stack.undo();
    expect(stack.canUndo).toBe(false);
  });

  it('clear empties past and future', () => {
    const doc1 = makeDoc();
    const doc2 = makeDoc();
    const stack = createUndoStack(doc1);
    stack.push(doc2);
    stack.undo();
    stack.clear();
    expect(stack.canUndo).toBe(false);
    expect(stack.canRedo).toBe(false);
  });
});

// ============================================================================
// mergeDocuments / lastWriteWins
// ============================================================================

describe('mergeDocuments', () => {
  it('includes remote-only nodes in the merged document', () => {
    const local = makeDoc();
    const remoteNode = makeInstance(createNodeId(), 'Card');
    const remote = makeDoc({
      rootNodes: [remoteNode.id],
      nodes: new Map([[remoteNode.id, remoteNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-02T00:00:00.000Z' },
    });
    const { merged, conflicts } = mergeDocuments(local, remote);
    expect(merged.nodes.has(remoteNode.id)).toBe(true);
    expect(conflicts).toHaveLength(0);
  });

  it('detects a prop conflict and records it', () => {
    const nodeId = createNodeId();
    const localNode = makeInstance(nodeId, 'Text', { content: 'Hello' });
    const remoteNode = makeInstance(nodeId, 'Text', { content: 'World' });
    const local = makeDoc({
      nodes: new Map([[nodeId, localNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T10:00:00.000Z' },
    });
    const remote = makeDoc({
      nodes: new Map([[nodeId, remoteNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T12:00:00.000Z' },
    });
    const { conflicts } = mergeDocuments(local, remote);
    expect(conflicts.length).toBeGreaterThanOrEqual(1);
    expect(conflicts[0].path).toBe('props.content');
  });

  it('lastWriteWins picks the later timestamp', () => {
    const nodeId = createNodeId();
    const localNode = makeInstance(nodeId, 'Text', { content: 'Old' });
    const remoteNode = makeInstance(nodeId, 'Text', { content: 'New' });
    const local = makeDoc({
      nodes: new Map([[nodeId, localNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T09:00:00.000Z' },
    });
    const remote = makeDoc({
      nodes: new Map([[nodeId, remoteNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T12:00:00.000Z' },
    });
    const { merged } = mergeDocuments(local, remote, lastWriteWins);
    expect(merged.nodes.get(nodeId)!.props['content']).toBe('New');
  });

  it('uses accept-local when local timestamp is later', () => {
    const nodeId = createNodeId();
    const localNode = makeInstance(nodeId, 'Text', { content: 'Latest' });
    const remoteNode = makeInstance(nodeId, 'Text', { content: 'Stale' });
    const local = makeDoc({
      nodes: new Map([[nodeId, localNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T15:00:00.000Z' },
    });
    const remote = makeDoc({
      nodes: new Map([[nodeId, remoteNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T12:00:00.000Z' },
    });
    const { merged } = mergeDocuments(local, remote, lastWriteWins);
    expect(merged.nodes.get(nodeId)!.props['content']).toBe('Latest');
  });

  it('uses merge strategy when resolver returns merged value', () => {
    const nodeId = createNodeId();
    const localNode = makeInstance(nodeId, 'Text', { content: 'A' });
    const remoteNode = makeInstance(nodeId, 'Text', { content: 'B' });
    const local = makeDoc({
      nodes: new Map([[nodeId, localNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T09:00:00.000Z' },
    });
    const remote = makeDoc({
      nodes: new Map([[nodeId, remoteNode]]),
      metadata: { createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T12:00:00.000Z' },
    });
    const { merged } = mergeDocuments(local, remote, () => ({ strategy: 'merge', mergedValue: 'A+B' }));
    expect(merged.nodes.get(nodeId)!.props['content']).toBe('A+B');
  });
});
