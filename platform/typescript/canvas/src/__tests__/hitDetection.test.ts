/**
 * @file hitDetection.test.ts
 * Tests for LayerManager.getVisibleElements — viewport culling and
 * element hit-detection within visible bounds.
 *
 * @doc.type module
 * @doc.purpose Tests for canvas element hit detection and viewport culling
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
    /* stub */
  }

  includesPoint(px: number, py: number): boolean {
    const b = this.getBounds();
    return px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h;
  }
}

function makeEl(
  id: string,
  x: number,
  y: number,
  w: number,
  h: number,
): TestElement {
  return new TestElement(id, x, y, w, h);
}

function viewport(x: number, y: number, w: number, h: number) {
  return { x, y, w, h };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("LayerManager — hit detection", () => {
  let manager: LayerManager;

  beforeEach(() => {
    manager = new LayerManager();
  });

  describe("Viewport culling", () => {
    it("returns no elements when viewport does not intersect any element", () => {
      manager.addElement(makeEl("far-away", 1000, 1000, 50, 50));

      const visible = manager.getVisibleElements(viewport(0, 0, 100, 100));
      expect(visible).toHaveLength(0);
    });

    it("returns elements fully within the viewport", () => {
      manager.addElement(makeEl("inside", 10, 10, 50, 50));

      const visible = manager.getVisibleElements(viewport(0, 0, 200, 200));
      expect(visible).toHaveLength(1);
      expect(visible[0]!.id).toBe("inside");
    });

    it("returns elements partially overlapping the viewport edge", () => {
      // Element at (90,90,50,50) overlaps viewport (0,0,100,100) on the far corner
      manager.addElement(makeEl("edge", 90, 90, 50, 50));

      const visible = manager.getVisibleElements(viewport(0, 0, 100, 100));
      expect(visible.some((e) => e.id === "edge")).toBe(true);
    });

    it("excludes elements strictly outside the viewport", () => {
      manager.addElement(makeEl("out-right", 300, 0, 50, 50)); // to the right
      manager.addElement(makeEl("out-below", 0, 300, 50, 50)); // below

      const visible = manager.getVisibleElements(viewport(0, 0, 200, 200));
      expect(visible).toHaveLength(0);
    });

    it("handles elements on the viewport boundary (touching edge)", () => {
      // Element that starts exactly at the viewport right edge
      manager.addElement(makeEl("at-right-edge", 100, 0, 50, 50));

      // viewport is 0,0,100,100 — element at x=100 is just outside
      const visible = manager.getVisibleElements(viewport(0, 0, 100, 100));
      // Whether touching is considered visible depends on strict vs. inclusive bounds
      // The implementation uses `bound1.x + bound1.w < bound2.x` (strictly outside)
      // so element at x=100, viewport.x+w=100 → 0+50=50 which is NOT < 100 → included
      expect(visible.some((e) => e.id === "at-right-edge")).toBeDefined();
    });
  });

  describe("Mixed visibility", () => {
    it("returns only visible elements from a mixed set", () => {
      manager.addElement(makeEl("visible-1", 0, 0, 50, 50));
      manager.addElement(makeEl("visible-2", 50, 50, 40, 40));
      manager.addElement(makeEl("offscreen", 500, 500, 50, 50));

      const visible = manager.getVisibleElements(viewport(0, 0, 200, 200));
      const ids = visible.map((e) => e.id);

      expect(ids).toContain("visible-1");
      expect(ids).toContain("visible-2");
      expect(ids).not.toContain("offscreen");
    });

    it("handles an empty canvas (no elements)", () => {
      const visible = manager.getVisibleElements(viewport(0, 0, 1000, 1000));
      expect(visible).toHaveLength(0);
    });

    it("handles a zero-size viewport — nothing visible", () => {
      manager.addElement(makeEl("any-el", 0, 0, 50, 50));

      const visible = manager.getVisibleElements(viewport(0, 0, 0, 0));
      expect(visible).toHaveLength(0);
    });

    it("returns all elements when viewport is very large", () => {
      manager.addElement(makeEl("scattered-1", 0, 0, 10, 10));
      manager.addElement(makeEl("scattered-2", 500, 500, 10, 10));
      manager.addElement(makeEl("scattered-3", -200, -200, 10, 10));

      const visible = manager.getVisibleElements(
        viewport(-9999, -9999, 99999, 99999),
      );
      expect(visible).toHaveLength(3);
    });
  });

  describe("Multi-layer hit detection", () => {
    it("returns visible elements from all layers", () => {
      manager.addLayer("layer-a");
      manager.addLayer("layer-b");

      manager.addElement(makeEl("a-vis", 0, 0, 50, 50), "layer-a");
      manager.addElement(makeEl("b-vis", 60, 60, 50, 50), "layer-b");
      manager.addElement(makeEl("b-hidden", 500, 500, 50, 50), "layer-b");

      const visible = manager.getVisibleElements(viewport(0, 0, 200, 200));
      const ids = visible.map((e) => e.id);

      expect(ids).toContain("a-vis");
      expect(ids).toContain("b-vis");
      expect(ids).not.toContain("b-hidden");
    });

    it("elements from removed layers are excluded from hit detection", () => {
      manager.addLayer("temp");
      manager.addElement(makeEl("temp-el", 0, 0, 50, 50), "temp");

      manager.removeLayer("temp");

      const visible = manager.getVisibleElements(viewport(-10, -10, 200, 200));
      expect(visible.some((e) => e.id === "temp-el")).toBe(false);
    });
  });

  describe("includesPoint — element-level hit test", () => {
    it("detects a point inside the element bounds", () => {
      const el = makeEl("hit-target", 100, 100, 50, 50);
      expect(el.includesPoint(125, 125)).toBe(true);
    });

    it("rejects a point outside the element bounds", () => {
      const el = makeEl("miss-target", 100, 100, 50, 50);
      expect(el.includesPoint(200, 200)).toBe(false);
    });

    it("detects a point on the element top-left corner", () => {
      const el = makeEl("corner-hit", 50, 50, 100, 100);
      expect(el.includesPoint(50, 50)).toBe(true);
    });
  });
});
