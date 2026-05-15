/**
 * @fileoverview Tests for keyboard traversal functionality.
 */

import { describe, it, expect, beforeEach } from "vitest";
import {
  TraversableRegistry,
  TraversalEngine,
  AriaLabelGenerator,
  FocusVisibleManager,
  getNonColorStatusSignal,
  applyNonColorStatus,
  reconcileFocusPath,
  isFocusPathValid,
  type TraversableElement,
  type FocusPath,
} from "../keyboard-traversal.js";

describe("Keyboard Traversal", () => {
  describe("TraversableRegistry", () => {
    let registry: TraversableRegistry;

    beforeEach(() => {
      registry = new TraversableRegistry();
    });

    it("should register element", () => {
      const element: TraversableElement = {
        id: "node-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
      };
      registry.register(element);
      expect(registry.get("node-1")).toEqual(element);
    });

    it("should reject duplicate registration", () => {
      const element: TraversableElement = {
        id: "node-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
      };
      registry.register(element);
      expect(() => registry.register(element)).toThrow();
    });

    it("should track root elements", () => {
      const root: TraversableElement = {
        id: "root-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
      };
      const child: TraversableElement = {
        id: "child-1",
        x: 10,
        y: 10,
        width: 50,
        height: 25,
        parentId: "root-1",
      };
      registry.register(root);
      registry.register(child);

      expect(registry.getRoots()).toHaveLength(1);
      expect(registry.getRoots()[0].id).toBe("root-1");
    });

    it("should track children", () => {
      const root: TraversableElement = {
        id: "root-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        children: ["child-1", "child-2"],
      };
      const child1: TraversableElement = {
        id: "child-1",
        x: 10,
        y: 10,
        width: 50,
        height: 25,
        parentId: "root-1",
      };
      const child2: TraversableElement = {
        id: "child-2",
        x: 60,
        y: 10,
        width: 50,
        height: 25,
        parentId: "root-1",
      };
      registry.register(root);
      registry.register(child1);
      registry.register(child2);

      const children = registry.getChildren("root-1");
      expect(children).toHaveLength(2);
    });

    it("should maintain focus order by tabIndex", () => {
      const element1: TraversableElement = {
        id: "first",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 1,
      };
      const element2: TraversableElement = {
        id: "second",
        x: 100,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 2,
      };
      const element0: TraversableElement = {
        id: "zero",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 0,
      };

      registry.register(element1);
      registry.register(element2);
      registry.register(element0);

      const order = registry.getFocusOrder();
      expect(order).toEqual(["zero", "first", "second"]);
    });

    it("should exclude disabled elements from focus order", () => {
      const element: TraversableElement = {
        id: "disabled",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        disabled: true,
      };
      registry.register(element);

      expect(registry.getFocusOrder()).toHaveLength(0);
    });

    it("should exclude hidden elements from focus order", () => {
      const element: TraversableElement = {
        id: "hidden",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        hidden: true,
      };
      registry.register(element);

      expect(registry.getFocusOrder()).toHaveLength(0);
    });

    it("should unregister element", () => {
      const element: TraversableElement = {
        id: "node-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
      };
      registry.register(element);
      registry.unregister("node-1");

      expect(registry.get("node-1")).toBeUndefined();
      expect(registry.getFocusOrder()).toHaveLength(0);
    });

    it("should clear all elements", () => {
      registry.register({ id: "node-1", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "node-2", x: 100, y: 0, width: 100, height: 50 });
      registry.clear();

      expect(registry.getAll()).toHaveLength(0);
    });
  });

  describe("TraversalEngine", () => {
    let registry: TraversableRegistry;
    let engine: TraversalEngine;

    beforeEach(() => {
      registry = new TraversableRegistry();
      engine = new TraversalEngine(registry);
    });

    it("should start from first element when no current focus", () => {
      registry.register({ id: "node-1", x: 0, y: 0, width: 100, height: 50 });

      const result = engine.traverse(null, "forward");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("node-1");
    });

    it("should traverse right to nearest element", () => {
      registry.register({ id: "left", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "right", x: 200, y: 0, width: 100, height: 50 });

      const result = engine.traverse("left", "right", "geographic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("right");
    });

    it("should traverse left to nearest element", () => {
      registry.register({ id: "left", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "right", x: 200, y: 0, width: 100, height: 50 });

      const result = engine.traverse("right", "left", "geographic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("left");
    });

    it("should traverse up to nearest element", () => {
      registry.register({ id: "top", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "bottom", x: 0, y: 200, width: 100, height: 50 });

      const result = engine.traverse("bottom", "up", "geographic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("top");
    });

    it("should traverse down to nearest element", () => {
      registry.register({ id: "top", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "bottom", x: 0, y: 200, width: 100, height: 50 });

      const result = engine.traverse("top", "down", "geographic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("bottom");
    });

    it("should fail when no element in direction", () => {
      registry.register({ id: "only", x: 0, y: 0, width: 100, height: 50 });

      const result = engine.traverse("only", "right", "geographic");
      expect(result.success).toBe(false);
      expect(result.targetId).toBeNull();
    });

    it("should traverse hierarchically forward to child", () => {
      registry.register({
        id: "root",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        children: ["child"],
      });
      registry.register({ id: "child", x: 10, y: 10, width: 50, height: 25, parentId: "root" });

      const result = engine.traverse("root", "forward", "hierarchical");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("child");
    });

    it("should traverse hierarchically backward to parent", () => {
      registry.register({
        id: "root",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        children: ["child"],
      });
      registry.register({ id: "child", x: 10, y: 10, width: 50, height: 25, parentId: "root" });

      const result = engine.traverse("child", "backward", "hierarchical");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("root");
    });

    it("should traverse sequentially forward", () => {
      registry.register({
        id: "first",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 1,
      });
      registry.register({
        id: "second",
        x: 100,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 2,
      });

      const result = engine.traverse("first", "forward", "sequential");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("second");
    });

    it("should traverse sequentially backward", () => {
      registry.register({
        id: "first",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 1,
      });
      registry.register({
        id: "second",
        x: 100,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 2,
      });

      const result = engine.traverse("second", "backward", "sequential");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("first");
    });

    it("should traverse home to first element", () => {
      registry.register({
        id: "first",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 1,
      });
      registry.register({
        id: "last",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 2,
      });

      const result = engine.traverse("last", "home", "sequential");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("first");
    });

    it("should traverse end to last element", () => {
      registry.register({
        id: "first",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 1,
      });
      registry.register({
        id: "last",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        tabIndex: 2,
      });

      const result = engine.traverse("first", "end", "sequential");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("last");
    });

    it("should traverse semantically using flow", () => {
      registry.register({
        id: "source",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        semanticFlow: { incoming: [], outgoing: ["target"] },
      });
      registry.register({
        id: "target",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        semanticFlow: { incoming: ["source"], outgoing: [] },
      });

      const result = engine.traverse("source", "forward", "semantic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("target");
    });

    it("should fallback to geographic when no semantic flow", () => {
      registry.register({ id: "left", x: 0, y: 0, width: 100, height: 50 });
      registry.register({ id: "right", x: 200, y: 0, width: 100, height: 50 });

      const result = engine.traverse("left", "right", "semantic");
      expect(result.success).toBe(true);
      expect(result.targetId).toBe("right");
      expect(result.mode).toBe("geographic");
    });
  });

  describe("AriaLabelGenerator", () => {
    let generator: AriaLabelGenerator;

    beforeEach(() => {
      generator = new AriaLabelGenerator();
    });

    it("should generate label with role", () => {
      const element: TraversableElement = {
        id: "node-1",
        x: 100,
        y: 200,
        width: 50,
        height: 30,
        semanticRole: "process",
      };

      const label = generator.generateLabel(element);
      expect(label).toContain("process");
      expect(label).toContain("100");
      expect(label).toContain("200");
    });

    it("should generate label with context role", () => {
      const element: TraversableElement = {
        id: "node-1",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
      };

      const label = generator.generateLabel(element, {
        role: "decision",
        index: 2,
        total: 5,
      });
      expect(label).toContain("decision");
      expect(label).toContain("3 of 5");
    });

    it("should generate traversal description", () => {
      const from: TraversableElement = {
        id: "from",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        semanticRole: "start",
      };
      const to: TraversableElement = {
        id: "to",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        semanticRole: "end",
      };

      const description = generator.generateTraversalDescription(from, to, "right");
      expect(description).toContain("right");
      expect(description).toContain("start");
      expect(description).toContain("end");
    });
  });

  describe("FocusVisibleManager", () => {
    let manager: FocusVisibleManager;

    beforeEach(() => {
      manager = new FocusVisibleManager();
    });

    it("should set focus", () => {
      manager.setFocus("node-1", "keyboard");
      const state = manager.getState();
      expect(state.currentId).toBe("node-1");
      expect(state.focusOrigin).toBe("keyboard");
    });

    it("should track previous focus", () => {
      manager.setFocus("first", "keyboard");
      manager.setFocus("second", "keyboard");
      const state = manager.getState();
      expect(state.currentId).toBe("second");
      expect(state.previousId).toBe("first");
    });

    it("should detect visible focus for keyboard", () => {
      manager.setFocus("node-1", "keyboard");
      expect(manager.isFocusVisible()).toBe(true);
    });

    it("should not detect visible focus for mouse", () => {
      manager.setFocus("node-1", "mouse");
      expect(manager.isFocusVisible()).toBe(false);
    });

    it("should notify subscribers", () => {
      const listener = vi.fn();
      manager.subscribe(listener);

      manager.setFocus("node-1", "keyboard");
      expect(listener).toHaveBeenCalled();
    });

    it("should unsubscribe listener", () => {
      const listener = vi.fn();
      const unsubscribe = manager.subscribe(listener);

      unsubscribe();
      manager.setFocus("node-1", "keyboard");
      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe("Non-Color Status Signals", () => {
    it("should return signal for each status", () => {
      const statuses = [
        "info",
        "success",
        "warning",
        "error",
        "neutral",
        "selected",
        "focused",
        "disabled",
      ] as const;

      for (const status of statuses) {
        const signal = getNonColorStatusSignal(status);
        expect(signal.status).toBe(status);
        expect(signal.pattern).toBeDefined();
      }
    });

    it("should return default for unknown status", () => {
      const signal = getNonColorStatusSignal("unknown" as never);
      expect(signal.status).toBe("neutral");
    });
  });

  describe("Focus Path Stability", () => {
    it("should reconcile focus path with valid IDs", () => {
      const path: FocusPath = {
        segments: [
          { id: "valid-1", type: "group", label: "Group 1" },
          { id: "invalid", type: "node", label: "Invalid" },
          { id: "valid-2", type: "detail", label: "Detail 1" },
        ],
        depth: 3,
      };

      const validIds = new Set(["valid-1", "valid-2"]);
      const reconciled = reconcileFocusPath(path, validIds);

      expect(reconciled.segments).toHaveLength(2);
      expect(reconciled.segments[0].id).toBe("valid-1");
      expect(reconciled.segments[1].id).toBe("valid-2");
    });

    it("should detect valid focus path", () => {
      const path: FocusPath = {
        segments: [
          { id: "node-1", type: "node", label: "Node 1" },
          { id: "node-2", type: "node", label: "Node 2" },
        ],
        depth: 2,
      };

      const validIds = new Set(["node-1", "node-2"]);
      expect(isFocusPathValid(path, validIds)).toBe(true);
    });

    it("should detect invalid focus path", () => {
      const path: FocusPath = {
        segments: [
          { id: "node-1", type: "node", label: "Node 1" },
          { id: "missing", type: "node", label: "Missing" },
        ],
        depth: 2,
      };

      const validIds = new Set(["node-1"]);
      expect(isFocusPathValid(path, validIds)).toBe(false);
    });
  });
});
