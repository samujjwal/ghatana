/**
 * @file coordinateTransformations.test.ts
 * Tests for Viewport — screenToCanvas, canvasToScreen, setZoom, pan,
 * getSemanticLevel (ZoomLevel enum), and getVisibleBounds.
 *
 * @doc.type module
 * @doc.purpose Tests for canvas coordinate transformation and viewport management
 * @doc.layer platform
 * @doc.pattern Test
 */
import { describe, it, expect, beforeEach } from "vitest";
import { Viewport, ZoomLevel } from "../core/viewport.js";

// ── Helpers ───────────────────────────────────────────────────────────────────

function makeViewport(width = 800, height = 600): Viewport {
  return new Viewport({ width, height });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe("Viewport", () => {
  let vp: Viewport;

  beforeEach(() => {
    vp = makeViewport(800, 600);
  });

  // ── Initial state ──────────────────────────────────────────────────────────

  describe("Initial state", () => {
    it("initialises zoom to 1", () => {
      expect(vp.zoom).toBe(1);
    });

    it("initialises width and height from options", () => {
      expect(vp.width).toBe(800);
      expect(vp.height).toBe(600);
    });

    it("initialises centerX to half width", () => {
      expect(vp.centerX).toBe(400);
    });

    it("initialises centerY to half height", () => {
      expect(vp.centerY).toBe(300);
    });

    it("initialises zoom$ observable with current zoom", () => {
      expect(vp.zoom$.getValue()).toBe(1);
    });

    it("initialises semanticLevel$ with DETAILED at zoom 1", () => {
      expect(vp.semanticLevel$.getValue()).toBe(ZoomLevel.DETAILED);
    });
  });

  // ── screenToCanvas ─────────────────────────────────────────────────────────

  describe("screenToCanvas", () => {
    it("maps screen center to canvas center at zoom 1", () => {
      const canvas = vp.screenToCanvas({ x: 400, y: 300 });
      expect(canvas.x).toBeCloseTo(vp.centerX);
      expect(canvas.y).toBeCloseTo(vp.centerY);
    });

    it("maps screen origin (0,0) to a canvas point left/above center", () => {
      const canvas = vp.screenToCanvas({ x: 0, y: 0 });
      // At zoom 1, centerX=400: canvas.x = (0 - 400) / 1 + 400 = 0
      expect(canvas.x).toBeCloseTo(0);
      expect(canvas.y).toBeCloseTo(0);
    });

    it("maps correctly at zoom 2", () => {
      vp.setZoom(2);
      // centerX stays at 400 when no focus point given
      const canvas = vp.screenToCanvas({ x: 400, y: 300 });
      // (400 - 400) / 2 + 400 = 400
      expect(canvas.x).toBeCloseTo(400);
      expect(canvas.y).toBeCloseTo(300);
    });

    it("maps correctly at zoom 0.5", () => {
      vp.setZoom(0.5);
      const canvas = vp.screenToCanvas({ x: 400, y: 300 });
      // Center maps to center
      expect(canvas.x).toBeCloseTo(400);
      expect(canvas.y).toBeCloseTo(300);
    });
  });

  // ── canvasToScreen ─────────────────────────────────────────────────────────

  describe("canvasToScreen", () => {
    it("maps canvas center to screen center at zoom 1", () => {
      const screen = vp.canvasToScreen({ x: vp.centerX, y: vp.centerY });
      expect(screen.x).toBeCloseTo(400);
      expect(screen.y).toBeCloseTo(300);
    });

    it("is the inverse of screenToCanvas at zoom 1", () => {
      const screenPoint = { x: 250, y: 150 };
      const canvasPoint = vp.screenToCanvas(screenPoint);
      const backToScreen = vp.canvasToScreen(canvasPoint);

      expect(backToScreen.x).toBeCloseTo(screenPoint.x);
      expect(backToScreen.y).toBeCloseTo(screenPoint.y);
    });

    it("is the inverse of screenToCanvas at zoom 2", () => {
      vp.setZoom(2);
      const screenPoint = { x: 300, y: 200 };
      const canvasPoint = vp.screenToCanvas(screenPoint);
      const backToScreen = vp.canvasToScreen(canvasPoint);

      expect(backToScreen.x).toBeCloseTo(screenPoint.x, 5);
      expect(backToScreen.y).toBeCloseTo(screenPoint.y, 5);
    });

    it("is the inverse of screenToCanvas at zoom 0.25", () => {
      vp.setZoom(0.25);
      const screenPoint = { x: 100, y: 400 };
      const canvasPoint = vp.screenToCanvas(screenPoint);
      const backToScreen = vp.canvasToScreen(canvasPoint);

      expect(backToScreen.x).toBeCloseTo(screenPoint.x, 5);
      expect(backToScreen.y).toBeCloseTo(screenPoint.y, 5);
    });
  });

  // ── setZoom ────────────────────────────────────────────────────────────────

  describe("setZoom", () => {
    it("sets zoom to the given value", () => {
      vp.setZoom(1.5);
      expect(vp.zoom).toBe(1.5);
    });

    it("clamps zoom to minimum of 0.05", () => {
      vp.setZoom(0.001);
      expect(vp.zoom).toBe(0.05);
    });

    it("clamps zoom to maximum of 20", () => {
      vp.setZoom(100);
      expect(vp.zoom).toBe(20);
    });

    it("emits new value on zoom$ observable", () => {
      const emitted: number[] = [];
      vp.zoom$.subscribe((z) => emitted.push(z));

      vp.setZoom(1.5);

      expect(emitted).toContain(1.5);
    });

    it("adjusts center when focus point is provided", () => {
      const initialCenterX = vp.centerX;
      vp.setZoom(2, 0, 0); // zoom to 2x focused on (0,0)

      // Center should shift toward (0,0) relative to initial position
      expect(vp.centerX).not.toBe(initialCenterX); // Center must have moved
    });
  });

  // ── pan ────────────────────────────────────────────────────────────────────

  describe("pan", () => {
    it("pans the viewport by the given delta at zoom 1", () => {
      const initialCX = vp.centerX;
      const initialCY = vp.centerY;

      vp.pan(50, 30);

      // centerX decreases as we pan right (screen delta / zoom)
      expect(vp.centerX).toBeCloseTo(initialCX - 50);
      expect(vp.centerY).toBeCloseTo(initialCY - 30);
    });

    it("panning right (positive deltaX) moves canvas left", () => {
      const before = vp.centerX;
      vp.pan(100, 0);
      expect(vp.centerX).toBeLessThan(before);
    });

    it("panning down (positive deltaY) moves canvas up", () => {
      const before = vp.centerY;
      vp.pan(0, 100);
      expect(vp.centerY).toBeLessThan(before);
    });

    it("panning is scaled by zoom level", () => {
      vp.setZoom(2);
      const initialCX = vp.centerX;
      vp.pan(100, 0);
      // At zoom 2, 100 screen pixels = 50 canvas units
      expect(vp.centerX).toBeCloseTo(initialCX - 50);
    });

    it("panning with zero delta has no effect", () => {
      const cx = vp.centerX;
      const cy = vp.centerY;
      vp.pan(0, 0);
      expect(vp.centerX).toBe(cx);
      expect(vp.centerY).toBe(cy);
    });
  });

  // ── getSemanticLevel ───────────────────────────────────────────────────────

  describe("getSemanticLevel (ZoomLevel)", () => {
    it("returns BIRD_EYE when zoom < 0.3", () => {
      vp.setZoom(0.2);
      expect(vp.getSemanticLevel()).toBe(ZoomLevel.BIRD_EYE);
    });

    it("returns OVERVIEW when zoom is between 0.3 and 0.7", () => {
      vp.setZoom(0.5);
      expect(vp.getSemanticLevel()).toBe(ZoomLevel.OVERVIEW);
    });

    it("returns DETAILED when zoom is between 0.7 and 2.0", () => {
      vp.setZoom(1);
      expect(vp.getSemanticLevel()).toBe(ZoomLevel.DETAILED);
    });

    it("returns MICROSCOPIC when zoom > 2.0", () => {
      vp.setZoom(3);
      expect(vp.getSemanticLevel()).toBe(ZoomLevel.MICROSCOPIC);
    });

    it("emits new semantic level on semanticLevel$ when crossing threshold", () => {
      const levels: ZoomLevel[] = [];
      vp.semanticLevel$.subscribe((l) => levels.push(l));

      vp.setZoom(0.1); // BIRD_EYE

      expect(levels).toContain(ZoomLevel.BIRD_EYE);
    });

    it("does not emit semanticLevel$ when zoom remains in same level", () => {
      const levels: ZoomLevel[] = [];
      vp.semanticLevel$.subscribe((l) => levels.push(l));
      const initialCount = levels.length;

      vp.setZoom(1.1); // still DETAILED
      vp.setZoom(1.5); // still DETAILED

      expect(levels.length).toBe(initialCount); // no new emission
    });
  });

  // ── getVisibleBounds ────────────────────────────────────────────────────────

  describe("getVisibleBounds", () => {
    it("returns a Bound with positive width and height", () => {
      const bounds = vp.getVisibleBounds();
      expect(bounds.w).toBeGreaterThan(0);
      expect(bounds.h).toBeGreaterThan(0);
    });

    it("visible bounds width equals viewport width at zoom 1", () => {
      const bounds = vp.getVisibleBounds();
      expect(bounds.w).toBeCloseTo(vp.width);
    });

    it("visible bounds are larger at lower zoom (more canvas visible)", () => {
      const boundsAt1 = vp.getVisibleBounds();
      vp.setZoom(0.5);
      const boundsAt05 = vp.getVisibleBounds();

      expect(boundsAt05.w).toBeGreaterThan(boundsAt1.w);
    });

    it("visible bounds are smaller at higher zoom (less canvas visible)", () => {
      const boundsAt1 = vp.getVisibleBounds();
      vp.setZoom(2);
      const boundsAt2 = vp.getVisibleBounds();

      expect(boundsAt2.w).toBeLessThan(boundsAt1.w);
    });
  });

  // ── reset ──────────────────────────────────────────────────────────────────

  describe("reset", () => {
    it("resets zoom to 1", () => {
      vp.setZoom(5);
      vp.reset();
      expect(vp.zoom).toBe(1);
    });

    it("resets center to origin", () => {
      vp.pan(200, 100);
      vp.reset();
      expect(vp.centerX).toBe(0);
      expect(vp.centerY).toBe(0);
    });
  });
});
