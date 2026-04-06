/**
 * @file layerInteractions.test.ts
 * Tests for LayerManager — addElement, removeElement, and moveElement
 * interactions between layers.
 *
 * @doc.type module
 * @doc.purpose Tests for element interactions across canvas layers
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, beforeEach } from "vitest";
import { LayerManager } from "../core/layer-manager.js";
import { CanvasElement } from "../elements/base.js";
import type { CanvasElementType, BaseElementProps } from "../types/index.js";

// ── Test stub element ────────────────────────────────────────────────────────

class TestElement extends CanvasElement {
  readonly type: CanvasElementType = "shape" as CanvasElementType;

  constructor(id: string, x = 0, y = 0, w = 100, h = 100, index = "0") {
    const xywh = JSON.stringify([x, y, w, h]) as BaseElementProps["xywh"];
    super({ id, xywh, rotate: 0, index });
  }

  render(_ctx: CanvasRenderingContext2D): void {
    /* stub */
  }

  includesPoint(px: number, py: number): boolean {
    const b = this.getBounds();
    return px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h;
  }
}

function makeEl(id: string, index = "0"): TestElement {
  return new TestElement(id, 0, 0, 50, 50, index);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("LayerManager — layer interactions", () => {
  let manager: LayerManager;

  beforeEach(() => {
    manager = new LayerManager();
  });

  // ── addElement ─────────────────────────────────────────────────────────────

  describe("addElement", () => {
    it("adds element to the default layer when no layerId specified", () => {
      const el = makeEl("default-add");
      manager.addElement(el);

      expect(manager.getElements("default")).toContain(el);
    });

    it("adds element to the specified layer", () => {
      manager.addLayer("overlay");
      const el = makeEl("overlay-el");
      manager.addElement(el, "overlay");

      expect(manager.getElements("overlay")).toContain(el);
      expect(manager.getElements("default")).not.toContain(el);
    });

    it("creates the layer automatically when target layer does not exist", () => {
      const el = makeEl("auto-layer-el");
      manager.addElement(el, "auto-created");

      expect(manager.getElements("auto-created")).toContain(el);
    });

    it("multiple elements can be added to the same layer", () => {
      manager.addLayer("multi");
      const el1 = makeEl("m1", "a");
      const el2 = makeEl("m2", "b");
      const el3 = makeEl("m3", "c");

      manager.addElement(el1, "multi");
      manager.addElement(el2, "multi");
      manager.addElement(el3, "multi");

      expect(manager.getElements("multi")).toHaveLength(3);
    });

    it("elements across different layers are isolated", () => {
      manager.addLayer("layer-a");
      manager.addLayer("layer-b");

      const elA = makeEl("a-el");
      const elB = makeEl("b-el");

      manager.addElement(elA, "layer-a");
      manager.addElement(elB, "layer-b");

      expect(manager.getElements("layer-a")).not.toContain(elB);
      expect(manager.getElements("layer-b")).not.toContain(elA);
    });
  });

  // ── removeElement ──────────────────────────────────────────────────────────

  describe("removeElement", () => {
    it("removes element from its layer", () => {
      const el = makeEl("removable");
      manager.addElement(el);

      manager.removeElement(el);

      expect(manager.getElements("default")).not.toContain(el);
    });

    it("removing one element does not affect other elements in the same layer", () => {
      const el1 = makeEl("keep", "a");
      const el2 = makeEl("remove", "b");

      manager.addElement(el1);
      manager.addElement(el2);

      manager.removeElement(el2);

      expect(manager.getElements("default")).toContain(el1);
      expect(manager.getElements("default")).not.toContain(el2);
    });

    it("removeElement is a no-op for an element not in any layer", () => {
      const stranger = makeEl("not-added");
      // Should not throw
      expect(() => manager.removeElement(stranger)).not.toThrow();
    });

    it("removes element from the correct layer when multiple layers exist", () => {
      manager.addLayer("layer-x");
      manager.addLayer("layer-y");

      const elX = makeEl("x-removable");
      const elY = makeEl("y-keep");

      manager.addElement(elX, "layer-x");
      manager.addElement(elY, "layer-y");

      manager.removeElement(elX);

      expect(manager.getElements("layer-x")).not.toContain(elX);
      expect(manager.getElements("layer-y")).toContain(elY);
    });
  });

  // ── moveElement ────────────────────────────────────────────────────────────

  describe("moveElement", () => {
    it("moves an element from one layer to another", () => {
      manager.addLayer("source");
      manager.addLayer("destination");

      const el = makeEl("movable");
      manager.addElement(el, "source");

      manager.moveElement(el, "source", "destination");

      expect(manager.getElements("destination")).toContain(el);
      expect(manager.getElements("source")).not.toContain(el);
    });

    it("element retains its identity after being moved", () => {
      manager.addLayer("from");
      manager.addLayer("to");

      const el = makeEl("identity-el");
      el.selected = true;

      manager.addElement(el, "from");
      manager.moveElement(el, "from", "to");

      const movedEl = manager.getElements("to")[0];
      expect(movedEl?.id).toBe("identity-el");
      expect(movedEl?.selected).toBe(true);
    });

    it("moving creates the destination layer if it did not exist", () => {
      const el = makeEl("new-dest-el");
      manager.addElement(el, "default");

      manager.moveElement(el, "default", "brand-new-layer");

      expect(manager.getElements("brand-new-layer")).toContain(el);
    });

    it("moving multiple elements between layers one at a time", () => {
      manager.addLayer("alpha");
      manager.addLayer("beta");

      const el1 = makeEl("a1", "a");
      const el2 = makeEl("a2", "b");

      manager.addElement(el1, "alpha");
      manager.addElement(el2, "alpha");

      manager.moveElement(el1, "alpha", "beta");
      manager.moveElement(el2, "alpha", "beta");

      expect(manager.getElements("alpha")).toHaveLength(0);
      expect(manager.getElements("beta")).toHaveLength(2);
    });
  });
});
