import { describe, it, expect } from 'vitest';
import { projectToScene, reconcileSceneDeltas } from '../scene-projection.js';
import type { BuilderDocument, ComponentInstance, NodeId } from '../types.js';
import { createDocumentId, createNodeId } from '../types.js';

function makeDoc(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
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
    ...overrides,
  };
}

function makeNode(
  id: NodeId,
  contractName: string,
  overrides: Partial<ComponentInstance> = {},
): ComponentInstance {
  return {
    id,
    contractName,
    props: {},
    slots: {},
    bindings: [],
    metadata: {},
    ...overrides,
  };
}

describe('projectToScene', () => {
  it('produces one scene node per root node', () => {
    const id1 = createNodeId();
    const id2 = createNodeId();
    const doc = makeDoc({
      rootNodes: [id1, id2],
      nodes: new Map([
        [id1, makeNode(id1, 'Button')],
        [id2, makeNode(id2, 'Card')],
      ]),
    });

    const scene = projectToScene(doc);
    expect(scene.nodes).toHaveLength(2);
    expect(scene.nodes.map((n) => n.contractName).sort()).toEqual(['Button', 'Card']);
  });

  it('flattens nested nodes with parent references', () => {
    const parentId = createNodeId();
    const childId = createNodeId();
    const doc = makeDoc({
      rootNodes: [parentId],
      nodes: new Map([
        [parentId, makeNode(parentId, 'Layout', { slots: { default: [childId] } })],
        [childId, makeNode(childId, 'Text')],
      ]),
    });

    const scene = projectToScene(doc);
    expect(scene.nodes).toHaveLength(2);
    const child = scene.nodes.find((n) => n.id === childId);
    expect(child?.parentId).toBe(parentId);
    expect(child?.slotName).toBe('default');
  });

  it('uses metadata position if present', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Box', {
        metadata: { position: { x: 100, y: 200 } },
      })]]),
    });

    const scene = projectToScene(doc);
    expect(scene.nodes[0]?.x).toBe(100);
    expect(scene.nodes[0]?.y).toBe(200);
  });

  it('assigns default auto-x positions when metadata is absent', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Widget')]]),
    });
    const scene = projectToScene(doc);
    expect(scene.nodes[0]?.x).toBeGreaterThanOrEqual(0);
    expect(scene.nodes[0]?.y).toBeGreaterThanOrEqual(0);
  });

  it('sets documentId on projection', () => {
    const doc = makeDoc();
    const scene = projectToScene(doc);
    expect(scene.documentId).toBe(doc.id);
  });
});

describe('reconcileSceneDeltas', () => {
  it('applies move delta to node metadata', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Box')]]),
    });

    const updated = reconcileSceneDeltas(doc, [
      { kind: 'move', nodeId: id, payload: { kind: 'move', x: 50, y: 75 } },
    ]);

    const node = updated.nodes.get(id);
    expect(node?.metadata.position?.x).toBe(50);
    expect(node?.metadata.position?.y).toBe(75);
  });

  it('applies resize delta to node metadata', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Box')]]),
    });

    const updated = reconcileSceneDeltas(doc, [
      { kind: 'resize', nodeId: id, payload: { kind: 'resize', width: 300, height: 150 } },
    ]);

    const node = updated.nodes.get(id);
    expect(node?.metadata.size?.width).toBe(300);
    expect(node?.metadata.size?.height).toBe(150);
  });

  it('applies update-props delta', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Button', { props: { label: 'Old' } })]]),
    });

    const updated = reconcileSceneDeltas(doc, [
      { kind: 'update-props', nodeId: id, payload: { kind: 'update-props', props: { label: 'New', disabled: true } } },
    ]);

    const node = updated.nodes.get(id);
    expect(node?.props['label']).toBe('New');
    expect(node?.props['disabled']).toBe(true);
  });

  it('applies delete delta', () => {
    const id = createNodeId();
    const doc = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Button')]]),
    });

    const updated = reconcileSceneDeltas(doc, [
      { kind: 'delete', nodeId: id, payload: { kind: 'delete' } },
    ]);

    expect(updated.nodes.has(id)).toBe(false);
    expect(updated.rootNodes).not.toContain(id);
  });

  it('updates metadata.updatedAt on any delta', () => {
    const id = createNodeId();
    const original = makeDoc({
      rootNodes: [id],
      nodes: new Map([[id, makeNode(id, 'Box')]]),
    });
    const before = original.metadata.updatedAt;

    const updated = reconcileSceneDeltas(original, [
      { kind: 'move', nodeId: id, payload: { kind: 'move', x: 0, y: 0 } },
    ]);

    expect(updated.metadata.updatedAt).not.toBe(before);
  });
});
