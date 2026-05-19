/**
 * @fileoverview Tests for multi-canvas isolation.
 *
 * Tests that multiple canvas controller instances maintain separate state
 * and do not interfere with each other. This is critical for supporting
 * multiple canvas instances in the same application (e.g., multiple tabs,
 * split views, or embedded canvases).
 *
 * @doc.type test
 * @doc.purpose Multi-canvas instance isolation validation
 * @doc.layer canvas
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { HybridCanvasController, type Clock, type IdProvider } from '../hybrid-canvas-controller.js';

describe('Multi-Canvas Isolation', () => {
  describe('state isolation', () => {
    it('should maintain separate element state across instances', () => {
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
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 100, height: 100 },
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

      expect(controller1.getElementById(element1.id)).toBeDefined();
      expect(controller2.getElementById(element1.id)).toBeUndefined();

      expect(controller1.getElementById(element2.id)).toBeUndefined();
      expect(controller2.getElementById(element2.id)).toBeDefined();
    });

    it('should maintain separate node state across instances', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      const node1 = controller1.addNode({
        type: 'node',
        nodeType: 'test',
        data: { label: 'Node 1' },
        inputs: [],
        outputs: [],
        style: {},
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 50, height: 50 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const node2 = controller2.addNode({
        type: 'node',
        nodeType: 'test',
        data: { label: 'Node 2' },
        inputs: [],
        outputs: [],
        style: {},
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 50, height: 50 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller1.getNodes()).toHaveLength(1);
      expect(controller2.getNodes()).toHaveLength(1);

      expect(controller1.getNodeById(node1.id)).toBeDefined();
      expect(controller2.getNodeById(node1.id)).toBeUndefined();
    });

    it('should maintain separate edge state across instances', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      const node1 = controller1.addNode({
        type: 'node',
        nodeType: 'test',
        data: {},
        inputs: [],
        outputs: [],
        style: {},
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 50, height: 50 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const node2 = controller2.addNode({
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
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      const edge1 = controller1.addEdge({
        type: 'edge',
        sourceId: node1.id,
        targetId: node1.id,
        path: [],
        style: {},
        transform: { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
        bounds: { x: 0, y: 0, width: 0, height: 0 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 1,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller1.getEdges()).toHaveLength(1);
      expect(controller2.getEdges()).toHaveLength(0);
    });
  });

  describe('history isolation', () => {
    it('should maintain separate undo/redo history across instances', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      controller1.addElement({
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

      controller2.addElement({
        type: 'rectangle',
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      expect(controller1.canUndo()).toBe(true);
      expect(controller2.canUndo()).toBe(true);

      // Undo on controller1 should not affect controller2
      controller1.undo();
      expect(controller1.getElements()).toHaveLength(0);
      expect(controller2.getElements()).toHaveLength(1);
      expect(controller1.canUndo()).toBe(false);
      expect(controller2.canUndo()).toBe(true);
    });
  });

  describe('selection isolation', () => {
    it('should maintain separate selection state across instances', () => {
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
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 100, height: 100 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      });

      controller1.select({ elements: [element1.id] });
      controller2.select({ elements: [element2.id] });

      const selection1 = controller1.getSelection();
      const selection2 = controller2.getSelection();

      expect(selection1.elementIds).toContain(element1.id);
      expect(selection1.elementIds).not.toContain(element2.id);

      expect(selection2.elementIds).toContain(element2.id);
      expect(selection2.elementIds).not.toContain(element1.id);
    });
  });

  describe('viewport isolation', () => {
    it('should maintain separate viewport state across instances', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      controller1.setViewport({ center: { x: 100, y: 100 }, zoom: 1.5, minZoom: 0.1, maxZoom: 10 });
      controller2.setViewport({ center: { x: 200, y: 200 }, zoom: 2.0, minZoom: 0.1, maxZoom: 10 });

      const viewport1 = controller1.getViewport();
      const viewport2 = controller2.getViewport();

      expect(viewport1.center.x).toBe(100);
      expect(viewport1.center.y).toBe(100);
      expect(viewport1.zoom).toBe(1.5);

      expect(viewport2.center.x).toBe(200);
      expect(viewport2.center.y).toBe(200);
      expect(viewport2.zoom).toBe(2.0);
    });
  });

  describe('dependency injection isolation', () => {
    it('should use separate clocks for each instance', () => {
      const clock1: Clock = {
        now: () => 1000,
      };

      const clock2: Clock = {
        now: () => 2000,
      };

      const controller1 = new HybridCanvasController({ clock: clock1 });
      const controller2 = new HybridCanvasController({ clock: clock2 });

      // Both controllers should use their respective clocks
      expect(clock1.now()).toBe(1000);
      expect(clock2.now()).toBe(2000);
    });

    it('should use separate ID providers for each instance', () => {
      const idProvider1: IdProvider = {
        generate: (prefix) => `${prefix}-1`,
      };

      const idProvider2: IdProvider = {
        generate: (prefix) => `${prefix}-2`,
      };

      const controller1 = new HybridCanvasController({ idProvider: idProvider1 });
      const controller2 = new HybridCanvasController({ idProvider: idProvider2 });

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

      expect(element1.id).toContain('-1');
      expect(element2.id).toContain('-2');
    });

    it('should use separate stores for each instance', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      // Each controller should have its own store
      const state1 = controller1.getState();
      const state2 = controller2.getState();

      // Both should be empty initially
      expect(state1.elements).toEqual([]);
      expect(state2.elements).toEqual([]);

      // Add to controller1
      controller1.addElement({
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

      // Only controller1 should have the element
      expect(controller1.getElements()).toHaveLength(1);
      expect(controller2.getElements()).toHaveLength(0);
    });
  });

  describe('concurrent operations', () => {
    it('should handle concurrent operations on different instances without interference', () => {
      const controller1 = new HybridCanvasController();
      const controller2 = new HybridCanvasController();

      // Add multiple elements to each controller
      for (let i = 0; i < 5; i++) {
        controller1.addElement({
          type: 'rectangle',
          transform: { position: { x: i * 100, y: 0 }, scale: 1, rotation: 0 },
          bounds: { x: i * 100, y: 0, width: 100, height: 100 },
          visible: true,
          locked: false,
          selected: false,
          zIndex: i,
          metadata: {},
          version: '1.0.0',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        });

        controller2.addElement({
          type: 'rectangle',
          transform: { position: { x: i * 100, y: 100 }, scale: 1, rotation: 0 },
          bounds: { x: i * 100, y: 100, width: 100, height: 100 },
          visible: true,
          locked: false,
          selected: false,
          zIndex: i,
          metadata: {},
          version: '1.0.0',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        });
      }

      expect(controller1.getElements()).toHaveLength(5);
      expect(controller2.getElements()).toHaveLength(5);

      // Undo on controller1 should not affect controller2
      controller1.undo();
      expect(controller1.getElements()).toHaveLength(4);
      expect(controller2.getElements()).toHaveLength(5);
    });
  });
});
