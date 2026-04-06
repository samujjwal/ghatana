/**
 * @file multiLayerRendering.test.ts
 * Tests for LayerManager — layer creation, removal, ordering, and element
 * retrieval across multiple layers.
 *
 * @doc.type module
 * @doc.purpose Tests for multi-layer canvas rendering management
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, beforeEach } from "vitest";
import { LayerManager } from "../core/layer-manager.js";
import { CanvasElement } from "../elements/base.js";
import { Bound } from "../utils/bounds.js";
import type { CanvasElementType, BaseElementProps } from "../types/index.js";

// ── Test stub element ────────────────────────────────────────────────────────

class TestElement extends CanvasElement {
  readonly type: CanvasElementType = "shape" as CanvasElementType;

  constructor(
    id: string,
    x: number,
    y: number,
    w: number,
    h: number,
    index = "0",
  ) {
    const xywh = JSON.stringify([x, y, w, h]) as BaseElementProps["xywh"];
    super({ id, xywh, rotate: 0, index });
  }

  render(_ctx: CanvasRenderingContext2D): void {
    // stub
  }

  includesPoint(px: number, py: number): boolean {
    const b = this.getBounds();
    return px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h;
  }
}

function makeElement(
  id: string,
  x = 0,
  y = 0,
  w = 100,
  h = 100,
  index = "0",
): TestElement {
  return new TestElement(id, x, y, w, h, index);
}

// ── Layer lifecycle ───────────────────────────────────────────────────────────

describe("LayerManager — multi-layer rendering", () => {
  let manager: LayerManager;

  beforeEach(() => {
    manager = new LayerManager();
  });

  describe("Layer creation", () => {
    it("creates default layer on construction", () => {
      // LayerManager initialises with a "default" layer
      const elements = manager.getElements("default");
      expect(elements).toBeDefined();
      expect(elements).toHaveLength(0);
    });

    it("addLayer creates a new empty layer", () => {
      manager.addLayer("layer-a");
      expect(manager.getElements("layer-a")).toHaveLength(0);
    });

    it("addLayer is idempotent — duplicate calls do not reset the layer", () => {
      manager.addLayer("layer-dup");
      const el = makeElement("el-1");
      manager.addElement(el, "layer-dup");

      manager.addLayer("layer-dup"); // second call should be a no-op

      expect(manager.getElements("layer-dup")).toHaveLength(1);
    });

    it("multiple layers can coexist independently", () => {
      manager.addLayer("foreground");
      manager.addLayer("background");

      manager.addElement(makeElement("fg-1"), "foreground");
      manager.addElement(makeElement("bg-1"), "background");
      manager.addElement(makeElement("bg-2"), "background");

      expect(manager.getElements("foreground")).toHaveLength(1);
      expect(manager.getElements("background")).toHaveLength(2);
    });
  });

  describe("Layer removal", () => {
    it("removeLayer removes a non-default layer", () => {
      manager.addLayer("temp-layer");
      manager.removeLayer("temp-layer");

      expect(manager.getElements("temp-layer")).toHaveLength(0);
    });

    it("removeLayer does not remove the default layer", () => {
      manager.addElement(makeElement("default-el"), "default");
      manager.removeLayer("default");

      expect(manager.getElements("default")).toHaveLength(1);
    });

    it("removing a layer removes its elements from future queries", () => {
      manager.addLayer("doomed");
      manager.addElement(makeElement("doomed-el-1"), "doomed");
      manager.addElement(makeElement("doomed-el-2"), "doomed");

      manager.removeLayer("doomed");

      const allVisible = manager.getVisibleElements({
        x: -9999,
        y: -9999,
        w: 99999,
        h: 99999,
      });
      const doomedIds = allVisible.filter((e) => e.id.startsWith("doomed-"));
      expect(doomedIds).toHaveLength(0);
    });
  });

  describe("Element ordering within a layer", () => {
    it("elements in a layer are sorted by index (string sort)", () => {
      manager.addLayer("ordered");

      const el1 = makeElement("el-index-b", 0, 0, 10, 10, "b");
      const el2 = makeElement("el-index-a", 0, 0, 10, 10, "a");
      const el3 = makeElement("el-index-c", 0, 0, 10, 10, "c");

      manager.addElement(el1, "ordered");
      manager.addElement(el3, "ordered");
      manager.addElement(el2, "ordered");

      const elements = manager.getElements("ordered");
      expect(elements[0]!.index).toBe("a");
      expect(elements[1]!.index).toBe("b");
      expect(elements[2]!.index).toBe("c");
    });
  });

  describe("getElements", () => {
    it("returns empty array for unknown layer", () => {
      expect(manager.getElements("non-existent")).toHaveLength(0);
    });

    it("returns elements added to a specific layer", () => {
      manager.addLayer("layer-x");
      const el = makeElement("x-el");
      manager.addElement(el, "layer-x");

      const result = manager.getElements("layer-x");
      expect(result).toContain(el);
    });
  });

  describe("getVisibleElements layer ordering", () => {
    it("returns elements from all layers within viewport bounds", () => {
      manager.addLayer("layer-1");
      manager.addLayer("layer-2");

      manager.addElement(makeElement("a", 0, 0, 50, 50), "layer-1");
      manager.addElement(makeElement("b", 100, 100, 50, 50), "layer-2");

      const visible = manager.getVisibleElements({
        x: -10,
        y: -10,
        w: 300,
        h: 300,
      });
      const ids = visible.map((e) => e.id);

      expect(ids).toContain("a");
      expect(ids).toContain("b");
    });

    it("respects layer order when returning visible elements", () => {
      manager.addLayer("bottom");
      manager.addLayer("top");

      manager.addElement(makeElement("bottom-el", 0, 0, 50, 50), "bottom");
      manager.addElement(makeElement("top-el", 5, 5, 30, 30), "top");

      const visible = manager.getVisibleElements({
        x: -10,
        y: -10,
        w: 200,
        h: 200,
      });
      const ids = visible.map((e) => e.id);

      // Bottom should appear before top in the ordered result
      expect(ids.indexOf("bottom-el")).toBeLessThan(ids.indexOf("top-el"));
    });
  });
});
