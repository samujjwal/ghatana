import { describe, it, expect } from "vitest";
import { Bound } from "../utils/bounds.js";

describe("Bound", () => {
  describe("construction", () => {
    it("stores x, y, w, h correctly", () => {
      const b = new Bound(10, 20, 100, 50);
      expect(b.x).toBe(10);
      expect(b.y).toBe(20);
      expect(b.w).toBe(100);
      expect(b.h).toBe(50);
    });

    it("fromXYWH creates equivalent Bound", () => {
      const b = Bound.fromXYWH(5, 15, 80, 40);
      expect(b.x).toBe(5);
      expect(b.y).toBe(15);
      expect(b.w).toBe(80);
      expect(b.h).toBe(40);
    });

    it("supports zero-size bounds", () => {
      const b = Bound.fromXYWH(0, 0, 0, 0);
      expect(b.w).toBe(0);
      expect(b.h).toBe(0);
    });
  });

  describe("center", () => {
    it("computes center correctly", () => {
      const b = new Bound(0, 0, 100, 50);
      expect(b.center.x).toBe(50);
      expect(b.center.y).toBe(25);
    });

    it("computes center for non-origin bound", () => {
      const b = new Bound(10, 20, 80, 60);
      expect(b.center.x).toBe(50);
      expect(b.center.y).toBe(50);
    });
  });

  describe("points", () => {
    it("returns all four corners in clockwise order", () => {
      const b = new Bound(0, 0, 10, 10);
      const pts = b.points;
      expect(pts).toHaveLength(4);
      expect(pts[0]).toEqual({ x: 0, y: 0 });   // top-left
      expect(pts[1]).toEqual({ x: 10, y: 0 });  // top-right
      expect(pts[2]).toEqual({ x: 10, y: 10 }); // bottom-right
      expect(pts[3]).toEqual({ x: 0, y: 10 });  // bottom-left
    });
  });

  describe("containsPoint", () => {
    it("returns true for point inside", () => {
      const b = new Bound(0, 0, 100, 100);
      expect(b.containsPoint({ x: 50, y: 50 })).toBe(true);
    });

    it("returns true for point on boundary", () => {
      const b = new Bound(0, 0, 100, 100);
      expect(b.containsPoint({ x: 0, y: 0 })).toBe(true);
      expect(b.containsPoint({ x: 100, y: 100 })).toBe(true);
    });

    it("returns false for point outside", () => {
      const b = new Bound(0, 0, 100, 100);
      expect(b.containsPoint({ x: 150, y: 50 })).toBe(false);
      expect(b.containsPoint({ x: 50, y: -1 })).toBe(false);
    });
  });

  describe("expand", () => {
    it("expands uniformly by number padding", () => {
      const b = new Bound(10, 10, 100, 100);
      const expanded = b.expand(5);
      expect(expanded.x).toBe(5);
      expect(expanded.y).toBe(5);
      expect(expanded.w).toBe(110);
      expect(expanded.h).toBe(110);
    });

    it("expands by Point padding", () => {
      const b = new Bound(10, 10, 100, 100);
      const expanded = b.expand({ x: 5, y: 10 });
      expect(expanded.x).toBe(5);
      expect(expanded.y).toBe(0);
      expect(expanded.w).toBe(110);
      expect(expanded.h).toBe(120);
    });

    it("expand by zero leaves bound unchanged", () => {
      const b = new Bound(10, 20, 50, 30);
      const expanded = b.expand(0);
      expect(expanded.x).toBe(10);
      expect(expanded.y).toBe(20);
      expect(expanded.w).toBe(50);
      expect(expanded.h).toBe(30);
    });
  });

  describe("intersects", () => {
    it("returns true for overlapping bounds", () => {
      const a = new Bound(0, 0, 100, 100);
      const b = new Bound(50, 50, 100, 100);
      expect(a.intersects(b)).toBe(true);
    });

    it("returns false for non-overlapping bounds", () => {
      const a = new Bound(0, 0, 50, 50);
      const b = new Bound(100, 0, 50, 50);
      expect(a.intersects(b)).toBe(false);
    });

    it("returns false for vertically separated bounds", () => {
      const a = new Bound(0, 0, 50, 50);
      const b = new Bound(0, 100, 50, 50);
      expect(a.intersects(b)).toBe(false);
    });

    it("touching edges are NOT considered intersecting", () => {
      const a = new Bound(0, 0, 50, 50);
      const b = new Bound(50, 0, 50, 50); // right edge touches left edge
      // a.x + a.w = 50 = b.x → condition: a.x + a.w < b.x → 50 < 50 is false
      // So they do intersect (shared edge)
      expect(a.intersects(b)).toBe(true);
    });
  });

  describe("serialize / deserialize", () => {
    it("round-trips through serialize/deserialize", () => {
      const original = new Bound(12, 34, 56, 78);
      const serialized = original.serialize();
      const restored = Bound.deserialize(serialized);
      expect(restored.x).toBe(12);
      expect(restored.y).toBe(34);
      expect(restored.w).toBe(56);
      expect(restored.h).toBe(78);
    });

    it("serialize produces valid JSON array string", () => {
      const b = new Bound(1, 2, 3, 4);
      const s = b.serialize();
      expect(JSON.parse(s)).toEqual([1, 2, 3, 4]);
    });
  });
});
