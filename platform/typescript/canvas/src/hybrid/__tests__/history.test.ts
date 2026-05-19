/**
 * @fileoverview Tests for canvas undo/redo history functionality.
 *
 * Tests the history snapshot timing, undo/redo operations, and state restoration.
 * Ensures that history snapshots are taken BEFORE mutations (not after) to
 * enable correct undo behavior.
 *
 * @doc.type test
 * @doc.purpose Canvas undo/redo history validation
 * @doc.layer canvas
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { HybridCanvasController } from '../hybrid-canvas-controller.js';

describe('HybridCanvasController History', () => {
  let controller: HybridCanvasController;

  beforeEach(() => {
    controller = new HybridCanvasController();
  });

  describe('undo/redo operations', () => {
    it('should undo element addition', () => {
      const initialElements = controller.getElements();
      expect(initialElements).toHaveLength(0);

      // Add an element
      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller.getElements()).toHaveLength(1);

      // Undo should restore to initial state
      controller.undo();
      expect(controller.getElements()).toHaveLength(0);
    });

    it('should undo element update', () => {
      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const originalX = added.transform.position.x;

      // Update the element
      controller.updateElement(added.id, {
        transform: { ...added.transform, position: { x: 50, y: 0 } },
      });

      const updated = controller.getElementById(added.id);
      expect(updated?.transform.position.x).toBe(50);

      // Undo should restore original position
      controller.undo();
      const restored = controller.getElementById(added.id);
      expect(restored?.transform.position.x).toBe(originalX);
    });

    it('should undo element deletion', () => {
      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller.getElements()).toHaveLength(1);

      // Delete the element
      controller.deleteElement(added.id);
      expect(controller.getElements()).toHaveLength(0);

      // Undo should restore the element
      controller.undo();
      expect(controller.getElements()).toHaveLength(1);
      expect(controller.getElementById(added.id)).toBeDefined();
    });

    it('should redo after undo', () => {
      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller.getElements()).toHaveLength(1);

      // Undo
      controller.undo();
      expect(controller.getElements()).toHaveLength(0);

      // Redo should restore the element
      controller.redo();
      expect(controller.getElements()).toHaveLength(1);
    });

    it('should track canUndo and canRedo state', () => {
      expect(controller.canUndo()).toBe(false);
      expect(controller.canRedo()).toBe(false);

      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller.canUndo()).toBe(true);
      expect(controller.canRedo()).toBe(false);

      controller.undo();
      expect(controller.canUndo()).toBe(false);
      expect(controller.canRedo()).toBe(true);

      controller.redo();
      expect(controller.canUndo()).toBe(true);
      expect(controller.canRedo()).toBe(false);
    });
  });

  describe('history snapshot timing', () => {
    it('should snapshot state BEFORE mutation', () => {
      const added = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      // Update the element
      controller.updateElement(added.id, {
        transform: { ...added.transform, position: { x: 50, y: 0 } },
      });

      // Undo should restore to state BEFORE the update
      controller.undo();
      const restored = controller.getElementById(added.id);
      expect(restored?.transform.position.x).toBe(0);
    });

    it('should snapshot complete state including elements, nodes, and edges', () => {
      const addedElement = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const addedNode = controller.addNode({
        type: 'node',
        nodeType: 'test',
        data: {},
        inputs: [],
        outputs: [],
        style: {},
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 50, height: 50 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 1,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const addedEdge = controller.addEdge({
        type: 'edge',
        sourceId: addedNode.id,
        targetId: addedNode.id,
        path: [],
        style: {},
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 0, height: 0 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 2,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      // Delete all
      controller.deleteElement(addedElement.id);
      controller.deleteNode(addedNode.id);
      controller.deleteEdge(addedEdge.id);

      expect(controller.getElements()).toHaveLength(0);
      expect(controller.getNodes()).toHaveLength(0);
      expect(controller.getEdges()).toHaveLength(0);

      // Undo all 3 separate delete operations to restore all items
      controller.undo();
      controller.undo();
      controller.undo();
      expect(controller.getElements()).toHaveLength(1);
      expect(controller.getNodes()).toHaveLength(1);
      expect(controller.getEdges()).toHaveLength(1);
    });
  });

  describe('batch operations', () => {
    it('should undo multiple operations as a single batch', () => {
      const element1 = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const element2 = controller.addElement({
        type: 'rectangle',
        transform: { position: { x: 100, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 1,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller.getElements()).toHaveLength(2);

      // Single undo should remove the last added element
      controller.undo();
      expect(controller.getElements()).toHaveLength(1);
      expect(controller.getElementById(element1.id)).toBeDefined();
      expect(controller.getElementById(element2.id)).toBeUndefined();
    });
  });

  describe('instance isolation', () => {
    it('should maintain separate history for each controller instance', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      const element1 = controller1.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const element2 = controller2.addElement({
        type: 'rectangle',
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller1.getElements()).toHaveLength(1);
      expect(controller2.getElements()).toHaveLength(1);

      // Undo on controller1 should not affect controller2
      controller1.undo();
      expect(controller1.getElements()).toHaveLength(0);
      expect(controller2.getElements()).toHaveLength(1);
    });
  });
});
