import { describe, it, expect, beforeEach } from "vitest";
import { CanvasRenderer } from "../core/canvas-renderer.js";
import { ShapeElement } from "../elements/shape.js";
import { TextElement } from "../elements/text.js";
import { BrushElement } from "../elements/brush.js";
import { Bound } from "../utils/bounds.js";

describe("YAPPC Canvas Core Tests", () => {
  let container: HTMLElement;
  let canvas: CanvasRenderer;

  beforeEach(() => {
    // Setup test container
    container = document.createElement("div");
    container.style.width = "800px";
    container.style.height = "600px";
    document.body.appendChild(container);

    // Create canvas instance
    canvas = new CanvasRenderer(container, {
      width: 800,
      height: 600,
    });
  });

  afterEach(() => {
    // Cleanup
    canvas.dispose();
    document.body.removeChild(container);
  });

  describe("Canvas Initialization", () => {
    it("should create canvas instance", () => {
      expect(canvas).toBeDefined();
      expect(canvas.getElements()).toHaveLength(0);
    });

    it("should have correct viewport dimensions", () => {
      const viewport = canvas.getViewport();
      expect(viewport.width).toBe(800);
      expect(viewport.height).toBe(600);
      expect(viewport.zoom).toBe(1);
    });
  });

  describe("Element Management", () => {
    it("should add elements to canvas", () => {
      const shape = new ShapeElement({
        id: "test-shape",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);

      expect(canvas.getElements()).toHaveLength(1);
      expect(canvas.getElements()[0].id).toBe("test-shape");
    });

    it("should remove elements from canvas", () => {
      const shape = new ShapeElement({
        id: "test-shape",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);
      expect(canvas.getElements()).toHaveLength(1);

      canvas.removeElement(shape);
      expect(canvas.getElements()).toHaveLength(0);
    });

    it("should select elements", () => {
      const shape = new ShapeElement({
        id: "test-shape",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);
      canvas.selectElement(shape);

      expect(shape.selected).toBe(true);
      expect(canvas.getSelectedElements()).toHaveLength(1);
    });

    it("should clear selection", () => {
      const shape = new ShapeElement({
        id: "test-shape",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);
      canvas.selectElement(shape);
      expect(shape.selected).toBe(true);

      canvas.clearSelection();
      expect(shape.selected).toBe(false);
      expect(canvas.getSelectedElements()).toHaveLength(0);
    });
  });

  describe("Element Types", () => {
    it("should create shape elements", () => {
      const shape = new ShapeElement({
        id: "rect-shape",
        xywh: JSON.stringify([50, 50, 100, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#10b981",
        strokeColor: "#047857",
        strokeWidth: 3,
        filled: true,
      });

      expect(shape.type).toBe("shape");
      expect(shape.shapeType).toBe("rect");
      expect(shape.includesPoint(100, 100)).toBe(true);
      expect(shape.includesPoint(200, 200)).toBe(false);
    });

    it("should create text elements", () => {
      const text = new TextElement({
        id: "text-element",
        xywh: JSON.stringify([100, 100, 200, 50]),
        index: "0",
        text: "Test Text",
        fontSize: 16,
        fontFamily: "Arial",
        color: "#1f2937",
      });

      expect(text.type).toBe("text");
      expect(text.text).toBe("Test Text");
      expect(text.fontSize).toBe(16);
    });

    it("should create brush elements", () => {
      const points = [
        { x: 0, y: 0 },
        { x: 10, y: 10 },
        { x: 20, y: 5 },
      ];

      const brush = new BrushElement({
        id: "brush-element",
        xywh: JSON.stringify([0, 0, 20, 10]),
        index: "0",
        points,
        color: "#ef4444",
        lineWidth: 2,
      });

      expect(brush.type).toBe("brush");
      expect(brush.points).toEqual(points);
      expect(brush.color).toBe("#ef4444");
    });
  });

  describe("Bounds Utility", () => {
    it("should create bounds correctly", () => {
      const bound = Bound.fromXYWH(10, 20, 100, 50);

      expect(bound.x).toBe(10);
      expect(bound.y).toBe(20);
      expect(bound.w).toBe(100);
      expect(bound.h).toBe(50);
    });

    it("should check point containment", () => {
      const bound = Bound.fromXYWH(10, 20, 100, 50);

      expect(bound.containsPoint({ x: 50, y: 40 })).toBe(true);
      expect(bound.containsPoint({ x: 5, y: 40 })).toBe(false);
      expect(bound.containsPoint({ x: 50, y: 80 })).toBe(false);
    });

    it("should serialize and deserialize", () => {
      const original = Bound.fromXYWH(10, 20, 100, 50);
      const serialized = original.serialize();
      const deserialized = Bound.deserialize(serialized);

      expect(deserialized.x).toBe(original.x);
      expect(deserialized.y).toBe(original.y);
      expect(deserialized.w).toBe(original.w);
      expect(deserialized.h).toBe(original.h);
    });

    it("should calculate center point", () => {
      const bound = Bound.fromXYWH(10, 20, 100, 50);
      const center = bound.center;

      expect(center.x).toBe(60);
      expect(center.y).toBe(45);
    });

    it("should expand bounds", () => {
      const bound = Bound.fromXYWH(10, 20, 100, 50);
      const expanded = bound.expand(10);

      expect(expanded.x).toBe(0);
      expect(expanded.y).toBe(10);
      expect(expanded.w).toBe(120);
      expect(expanded.h).toBe(70);
    });
  });

  describe("Viewport Operations", () => {
    it("should set zoom level", () => {
      const viewport = canvas.getViewport();

      viewport.setZoom(2);
      expect(viewport.zoom).toBe(2);

      viewport.setZoom(0.5);
      expect(viewport.zoom).toBe(0.5);
    });

    it("should limit zoom range", () => {
      const viewport = canvas.getViewport();

      viewport.setZoom(10); // Should be limited
      expect(viewport.zoom).toBeLessThanOrEqual(5);

      viewport.setZoom(0.01); // Should be limited
      expect(viewport.zoom).toBeGreaterThanOrEqual(0.1);
    });

    it("should pan viewport", () => {
      const viewport = canvas.getViewport();
      const originalCenterX = viewport.centerX;
      const originalCenterY = viewport.centerY;

      viewport.pan(50, 30);

      expect(viewport.centerX).toBe(originalCenterX + 50);
      expect(viewport.centerY).toBe(originalCenterY + 30);
    });

    it("should transform coordinates", () => {
      const viewport = canvas.getViewport();

      const screenPoint = { x: 400, y: 300 };
      const canvasPoint = viewport.screenToCanvas(screenPoint);

      expect(canvasPoint.x).toBe(0);
      expect(canvasPoint.y).toBe(0);

      const backToScreen = viewport.canvasToScreen(canvasPoint);
      expect(backToScreen.x).toBe(screenPoint.x);
      expect(backToScreen.y).toBe(screenPoint.y);
    });
  });

  describe("Event System", () => {
    it("should emit element add events", (done) => {
      canvas.on("elementAdd", (element) => {
        expect(element.id).toBe("test-element");
        done();
      });

      const shape = new ShapeElement({
        id: "test-element",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);
    });

    it("should emit element select events", (done) => {
      canvas.on("elementSelect", (element) => {
        expect(element.id).toBe("test-element");
        done();
      });

      const shape = new ShapeElement({
        id: "test-element",
        xywh: JSON.stringify([100, 100, 200, 100]),
        index: "0",
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#1e40af",
        strokeWidth: 2,
        filled: true,
      });

      canvas.addElement(shape);
      canvas.selectElement(shape);
    });

    it("should emit viewport change events", (done) => {
      canvas.on("viewportChange", (viewport) => {
        expect(viewport.zoom).toBe(2);
        done();
      });

      const viewport = canvas.getViewport();
      viewport.setZoom(2);
    });
  });

  describe("Performance", () => {
    it("should handle many elements efficiently", () => {
      const startTime = performance.now();

      for (let i = 0; i < 1000; i++) {
        const shape = new ShapeElement({
          id: `shape-${i}`,
          xywh: JSON.stringify([
            Math.random() * 700,
            Math.random() * 500,
            50,
            50,
          ]),
          index: i.toString(),
          shapeType: "rect",
          fillColor: "#3b82f6",
          strokeColor: "#1e40af",
          strokeWidth: 1,
          filled: true,
        });
        canvas.addElement(shape);
      }

      const endTime = performance.now();
      const duration = endTime - startTime;

      expect(canvas.getElements()).toHaveLength(1000);
      expect(duration).toBeLessThan(1000); // Should complete in less than 1 second
    });
  });
});
