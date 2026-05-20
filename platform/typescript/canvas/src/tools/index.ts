import { Point, ToolOptions } from "../types/index.js";
import { CanvasElement } from "../elements/base.js";
import { ShapeElement, ShapeType } from "../elements/shape.js";
import { TextElement } from "../elements/text.js";
import { BrushElement } from "../elements/brush.js";
import { FrameElement } from "../elements/frame.js";
import { NoteElement } from "../elements/note.js";
import { ImageElement } from "../elements/image.js";
import { HighlighterElement } from "../elements/highlighter.js";
import { ConnectorTool } from "./connector-tool.js";
import { BaseTool } from "./base-tool.js";
import { nanoid } from "nanoid";

export { BaseTool } from "./base-tool.js";

type CanvasToolDiagnosticLevel = "debug" | "warn" | "error";

const emitToolDiagnostic = (
  canvas: HTMLCanvasElement,
  level: CanvasToolDiagnosticLevel,
  message: string,
  detail?: Record<string, unknown>,
): void => {
  canvas.dispatchEvent(
    new CustomEvent("canvas-tool-diagnostic", {
      detail: {
        level,
        message,
        ...detail,
      },
    }),
  );
};

export class SelectTool extends BaseTool {
  private selectedElements: CanvasElement[] = [];
  private isDragging = false;
  private dragStartPoint: Point | null = null;
  private draggedElement: CanvasElement | null = null;
  private elementStartPos: Point | null = null;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;
    this.dragStartPoint = point;

    // Check if clicking on an element
    canvas.dispatchEvent(
      new CustomEvent("canvas-select", {
        detail: { point },
      }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.startPoint) return;

    const point = this.getMousePosition(event, canvas);

    if (!this.isDragging && this.startPoint) {
      const distance = Math.sqrt(
        Math.pow(point.x - this.startPoint.x, 2) +
        Math.pow(point.y - this.startPoint.y, 2),
      );

      if (distance > 5 && this.draggedElement) {
        this.isDragging = true;
      }
    }

    if (this.isDragging && this.draggedElement && this.elementStartPos && this.dragStartPoint) {
      // Calculate element movement
      const deltaX = point.x - this.dragStartPoint.x;
      const deltaY = point.y - this.dragStartPoint.y;

      // Emit element drag event
      canvas.dispatchEvent(
        new CustomEvent("canvas-element-drag", {
          detail: {
            element: this.draggedElement,
            deltaX,
            deltaY,
            startPos: this.elementStartPos
          },
        }),
      );
    }
  }

  onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isDragging && this.startPoint) {
      // Click for selection
      const point = this.getMousePosition(event, canvas);

      canvas.dispatchEvent(
        new CustomEvent("canvas-select", {
          detail: { point },
        }),
      );
    }

    if (this.isDragging && this.draggedElement) {
      // Emit element drag end event
      canvas.dispatchEvent(
        new CustomEvent("canvas-element-drag-end", {
          detail: { element: this.draggedElement },
        }),
      );
    }

    this.isDragging = false;
    this.dragStartPoint = null;
    this.draggedElement = null;
    this.elementStartPos = null;
    this.cleanup();
  }

  getCursor(): string {
    return this.isDragging ? "grabbing" : "default";
  }

  // Method to set the dragged element (called by canvas renderer)
  setDraggedElement(element: CanvasElement | null, startPos: Point | null): void {
    this.draggedElement = element;
    this.elementStartPos = startPos;
  }
}

export class ShapeTool extends BaseTool {
  private shapeType: ShapeType = "rect";
  private initialStartPoint: Point | null = null;

  constructor(options: ToolOptions, shapeType: ShapeType = "rect") {
    super(options);
    this.shapeType = shapeType;
  }

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;
    this.initialStartPoint = point;

    // Create initial shape
    this.currentElement = new ShapeElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, 1, 1]),
      index: Date.now().toString(),
      shapeType: this.shapeType,
      fillColor: this.options.fill
        ? this.options.color || "#3b82f6"
        : "transparent",
      strokeColor: this.options.color || "#1e40af",
      strokeWidth: this.options.strokeWidth || 2,
      filled: this.options.fill || false,
    });

    emitToolDiagnostic(canvas, "debug", "Shape created", {
      id: ((this.currentElement as unknown as Record<string, unknown>)).id,
      xywh: this.currentElement.xywh,
    });

    // Emit element creation event
    canvas.dispatchEvent(
      new CustomEvent("element-create", {
        detail: { element: this.currentElement },
      }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.currentElement || !this.startPoint || !this.initialStartPoint) return;

    const point = this.getMousePosition(event, canvas);

    // Calculate shape size
    let w = Math.abs(point.x - this.initialStartPoint.x);
    let h = Math.abs(point.y - this.initialStartPoint.y);

    // Shift-constrain: maintain aspect ratio
    if (event.shiftKey) {
      const size = Math.max(w, h);
      w = size;
      h = size;
    }

    const x = point.x > this.initialStartPoint.x ? this.initialStartPoint.x : this.initialStartPoint.x - w;
    const y = point.y > this.initialStartPoint.y ? this.initialStartPoint.y : this.initialStartPoint.y - h;

    this.currentElement.xywh = JSON.stringify([x, y, w, h]);

    // Emit element update event
    canvas.dispatchEvent(
      new CustomEvent("element-update", {
        detail: { element: this.currentElement },
      }),
    );

    emitToolDiagnostic(canvas, "debug", "Shape updated", {
      id: ((this.currentElement as unknown as Record<string, unknown>)).id,
      xywh: this.currentElement.xywh,
    });
  }

  onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.currentElement) {
      // Finalize element
      canvas.dispatchEvent(
        new CustomEvent("element-finalize", {
          detail: { element: this.currentElement },
        }),
      );
    }

    this.cleanup();
    emitToolDiagnostic(canvas, "debug", "Shape finalized");
  }

  getCursor(): string {
    return "crosshair";
  }

  setShapeType(shapeType: ShapeType): void {
    this.shapeType = shapeType;
  }
}

export class TextTool extends BaseTool {
  private isEditing = false;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;

    // Create text element
    this.currentElement = new TextElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, 200, 50]),
      index: Date.now().toString(),
      text: "New Text",
      fontSize: 16,
      fontFamily: "Arial",
      color: this.options.color || "#1f2937",
    });

    // Emit element creation event
    canvas.dispatchEvent(
      new CustomEvent("element-create", {
        detail: { element: this.currentElement },
      }),
    );

    // Start editing
    this.isEditing = true;
    canvas.dispatchEvent(
      new CustomEvent("text-edit-start", {
        detail: { element: this.currentElement },
      }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    // Text tool doesn't need move handling
  }

  onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    this.cleanup();
  }

  getCursor(): string {
    return "text";
  }
}

export class BrushTool extends BaseTool {
  private points: { x: number; y: number; pressure?: number }[] = [];
  private isDrawing = false;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;
    this.points = [point];
    this.isDrawing = true;

    // Create brush element
    this.currentElement = new BrushElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, 1, 1]),
      index: Date.now().toString(),
      points: this.points,
      color: this.options.color || "#1f2937",
      lineWidth: this.options.strokeWidth || 2,
    });

    // Emit element creation event
    canvas.dispatchEvent(
      new CustomEvent("element-create", {
        detail: { element: this.currentElement },
      }),
    );

    emitToolDiagnostic(canvas, "debug", "Brush stroke created", {
      id: ((this.currentElement as unknown as Record<string, unknown>)).id,
      xywh: this.currentElement.xywh,
    });
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isDrawing || !this.currentElement) return;

    const point = this.getMousePosition(event, canvas);

    // Add point to brush
    this.points.push(point);
    (this.currentElement as BrushElement).addPoint(point);

    // Update bounds
    const bounds = this.currentElement.getBounds();
    const minX = Math.min(...this.points.map((p) => p.x));
    const minY = Math.min(...this.points.map((p) => p.y));
    const maxX = Math.max(...this.points.map((p) => p.x));
    const maxY = Math.max(...this.points.map((p) => p.y));

    this.currentElement.xywh = JSON.stringify([
      minX - 10,
      minY - 10,
      maxX - minX + 20,
      maxY - minY + 20,
    ]);

    // Emit element update event
    canvas.dispatchEvent(
      new CustomEvent("element-update", {
        detail: { element: this.currentElement },
      }),
    );

    emitToolDiagnostic(canvas, "debug", "Brush stroke updated", {
      id: (this.currentElement as unknown as Record<string, unknown>).id,
      points: ((this.currentElement as unknown as Record<string, unknown>).points as unknown[]).length,
    });
  }

  onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.currentElement) {
      // Finalize element
      canvas.dispatchEvent(
        new CustomEvent("element-finalize", {
          detail: { element: this.currentElement },
        }),
      );
    }

    this.isDrawing = false;
    this.cleanup();

    emitToolDiagnostic(canvas, "debug", "Brush stroke finalized");
  }

  getCursor(): string {
    return "crosshair";
  }
}

/**
 * Highlighter Tool - for marker-style highlighting
 */
export class HighlighterTool extends BaseTool {
  private points: { x: number; y: number; pressure?: number }[] = [];
  private isDrawing = false;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;
    this.points = [{ ...point, pressure: event.pressure }];
    this.isDrawing = true;

    this.currentElement = new HighlighterElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, 1, 1]),
      index: Date.now().toString(),
      points: this.points,
      color: this.options.color || "#ffeb3b",
      lineWidth: this.options.strokeWidth || 20,
      opacity: 0.4,
    });

    canvas.dispatchEvent(
      new CustomEvent("element-create", {
        detail: { element: this.currentElement },
      }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isDrawing || !this.currentElement) return;

    const point = this.getMousePosition(event, canvas);
    this.points.push({ ...point, pressure: event.pressure });
    ((this.currentElement as unknown as Record<string, (arg: unknown) => void>)).addPoint({ ...point, pressure: event.pressure });

    canvas.dispatchEvent(
      new CustomEvent("element-update", {
        detail: { element: this.currentElement },
      }),
    );
  }

  onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.currentElement) {
      canvas.dispatchEvent(
        new CustomEvent("element-finalize", {
          detail: { element: this.currentElement },
        }),
      );
    }

    this.isDrawing = false;
    this.cleanup();
  }

  getCursor(): string {
    return "crosshair";
  }
}

export class ToolManager {
  private tools: Map<string, BaseTool> = new Map();
  private activeTool: BaseTool | null = null;
  private activeToolName: string = "";

  constructor() {
    // Register default tools
    this.registerTool("select", new SelectTool({ type: "select" }));
    this.registerTool(
      "shape-rect",
      new ShapeTool({ type: "shape", fill: true }, "rect"),
    );
    this.registerTool(
      "shape-circle",
      new ShapeTool({ type: "shape", fill: true }, "circle"),
    );
    this.registerTool(
      "shape-diamond",
      new ShapeTool({ type: "shape", fill: true }, "diamond"),
    );
    this.registerTool(
      "shape-triangle",
      new ShapeTool({ type: "shape", fill: true }, "triangle"),
    );
    this.registerTool(
      "shape-ellipse",
      new ShapeTool({ type: "shape", fill: true }, "ellipse"),
    );
    this.registerTool("text", new TextTool({ type: "text", color: "#1f2937" }));
    this.registerTool(
      "brush",
      new BrushTool({ type: "brush", color: "#1f2937", strokeWidth: 2 }),
    );
    this.registerTool(
      "highlighter",
      new HighlighterTool({ type: "highlighter", color: "#ffeb3b", strokeWidth: 20 }),
    );

    this.registerTool("connector", new ConnectorTool({ type: "connector", color: "#1e40af", strokeWidth: 2 }));

    // Extended tools
    this.registerTool("pan", new PanTool({ type: "pan" }));
    this.registerTool("eraser", new EraserTool({ type: "eraser" }));
    this.registerTool("zoom", new ZoomTool({ type: "zoom" }));
    this.registerTool("frame", new FrameTool({ type: "frame" }));
    this.registerTool("lasso", new LassoTool({ type: "lasso" }));
    this.registerTool("eyedropper", new EyedropperTool({ type: "eyedropper" }));
    this.registerTool("image", new ImageTool({ type: "image" }));
    this.registerTool("sticky-note", new StickyNoteTool({ type: "sticky-note" }));

    // Default to select tool for pointer interactions
    this.setActiveTool("select");
  }

  registerTool(name: string, tool: BaseTool): void {
    this.tools.set(name, tool);
  }

  setActiveTool(name: string): void {
    if (this.activeTool) {
      this.activeTool.deactivate();
    }

    const tool = this.tools.get(name);
    if (tool) {
      this.activeTool = tool;
      this.activeToolName = name;
      tool.activate();
    } else {
      globalThis.dispatchEvent?.(
        new CustomEvent("canvas-tool-diagnostic", {
          detail: {
            level: "warn",
            message: "Requested tool not found",
            toolName: name,
          },
        }),
      );
    }
  }

  getActiveTool(): BaseTool | null {
    return this.activeTool;
  }

  getActiveToolName(): string {
    return this.activeToolName;
  }

  getTool(name: string): BaseTool | undefined {
    return this.tools.get(name);
  }

  getToolNames(): string[] {
    return Array.from(this.tools.keys());
  }

  handlePointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.activeTool) {
      this.activeTool.onPointerDown(event, canvas);
    }
  }

  handlePointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.activeTool) {
      this.activeTool.onPointerMove(event, canvas);
    }
  }

  handlePointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.activeTool) {
      this.activeTool.onPointerUp(event, canvas);
    }
  }

  getCursor(): string {
    return this.activeTool?.getCursor() || "default";
  }
}

// =============================================================================
// PAN TOOL — scrolls the infinite canvas viewport
// =============================================================================

export class PanTool extends BaseTool {
  private isPanning = false;
  private lastPoint: Point | null = null;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    this.isPanning = true;
    this.lastPoint = this.getMousePosition(event, canvas);
    canvas.style.cursor = "grabbing";
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isPanning || !this.lastPoint) return;
    const point = this.getMousePosition(event, canvas);
    const delta = { x: point.x - this.lastPoint.x, y: point.y - this.lastPoint.y };
    canvas.dispatchEvent(new CustomEvent("canvas-pan", { detail: delta }));
    this.lastPoint = point;
  }

  onPointerUp(_event: PointerEvent, canvas: HTMLCanvasElement): void {
    this.isPanning = false;
    this.lastPoint = null;
    canvas.style.cursor = "grab";
    this.cleanup();
  }

  getCursor(): string {
    return "grab";
  }
}

// =============================================================================
// ERASER TOOL — removes elements or whiteboard strokes under the pointer
// =============================================================================

export class EraserTool extends BaseTool {
  private isErasing = false;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    this.isErasing = true;
    const point = this.getMousePosition(event, canvas);
    canvas.dispatchEvent(new CustomEvent("canvas-erase", { detail: { point } }));
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isErasing) return;
    const point = this.getMousePosition(event, canvas);
    canvas.dispatchEvent(new CustomEvent("canvas-erase", { detail: { point } }));
  }

  onPointerUp(_event: PointerEvent, _canvas: HTMLCanvasElement): void {
    this.isErasing = false;
    this.cleanup();
  }

  getCursor(): string {
    return "cell";
  }
}

// =============================================================================
// ZOOM TOOL — clicks to zoom in, alt-click to zoom out
// =============================================================================

export class ZoomTool extends BaseTool {
  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    const zoomIn = !event.altKey;
    canvas.dispatchEvent(
      new CustomEvent("canvas-zoom-click", {
        detail: { point, factor: zoomIn ? 1.5 : 1 / 1.5 },
      }),
    );
  }

  onPointerMove(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }
  onPointerUp(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }

  getCursor(): string {
    return "zoom-in";
  }
}

// =============================================================================
// FRAME TOOL — draws a frame container on pointer drag
// =============================================================================

export class FrameTool extends BaseTool {
  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    this.startPoint = point;

    this.currentElement = new FrameElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, 1, 1]),
      index: Date.now().toString(),
      title: "Frame",
    });

    canvas.dispatchEvent(
      new CustomEvent("element-create", { detail: { element: this.currentElement } }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.currentElement || !this.startPoint) return;
    const point = this.getMousePosition(event, canvas);
    const x = Math.min(point.x, this.startPoint.x);
    const y = Math.min(point.y, this.startPoint.y);
    const w = Math.abs(point.x - this.startPoint.x);
    const h = Math.abs(point.y - this.startPoint.y);
    this.currentElement.xywh = JSON.stringify([x, y, Math.max(w, 8), Math.max(h, 8)]);
    canvas.dispatchEvent(
      new CustomEvent("element-update", { detail: { element: this.currentElement } }),
    );
  }

  onPointerUp(_event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.currentElement) {
      canvas.dispatchEvent(
        new CustomEvent("element-finalize", { detail: { element: this.currentElement } }),
      );
    }
    this.startPoint = null;
    this.cleanup();
  }

  getCursor(): string {
    return "crosshair";
  }
}

// =============================================================================
// LASSO TOOL — freehand selection region (rubber band select)
// =============================================================================

export class LassoTool extends BaseTool {
  private points: Point[] = [];
  private isSelecting = false;

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    this.isSelecting = true;
    this.points = [this.getMousePosition(event, canvas)];
    canvas.dispatchEvent(
      new CustomEvent("canvas-lasso-start", { detail: { points: this.points } }),
    );
  }

  onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (!this.isSelecting) return;
    this.points.push(this.getMousePosition(event, canvas));
    canvas.dispatchEvent(
      new CustomEvent("canvas-lasso-update", { detail: { points: this.points } }),
    );
  }

  onPointerUp(_event: PointerEvent, canvas: HTMLCanvasElement): void {
    if (this.isSelecting) {
      canvas.dispatchEvent(
        new CustomEvent("canvas-lasso-end", { detail: { points: this.points } }),
      );
    }
    this.isSelecting = false;
    this.points = [];
    this.cleanup();
  }

  getCursor(): string {
    return "crosshair";
  }
}

// =============================================================================
// EYEDROPPER TOOL — samples a color from the canvas at the clicked point
// =============================================================================

export class EyedropperTool extends BaseTool {
  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    // Sample the pixel color from the canvas at this point
    const ctx = canvas.getContext("2d");
    let color: string | null = null;
    if (ctx) {
      const pixel = ctx.getImageData(point.x, point.y, 1, 1).data;
      color = `rgba(${pixel[0]},${pixel[1]},${pixel[2]},${(pixel[3] ?? 255) / 255})`;
    }
    canvas.dispatchEvent(
      new CustomEvent("canvas-color-pick", { detail: { point, color } }),
    );
  }

  onPointerMove(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }
  onPointerUp(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }

  getCursor(): string {
    return "crosshair";
  }
}

// =============================================================================
// IMAGE TOOL — places an image element at the clicked location
// =============================================================================

export class ImageTool extends BaseTool {
  private _src: string;

  constructor(options: ToolOptions, src: string = "") {
    super(options);
    this._src = src;
  }

  /** Update the image source before the next pointer-down. */
  setSrc(src: string): void {
    this._src = src;
  }

  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    const DEFAULT_SIZE = 320;

    this.currentElement = new ImageElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, DEFAULT_SIZE, DEFAULT_SIZE]),
      index: Date.now().toString(),
      src: this._src,
      fitMode: "contain",
    });

    canvas.dispatchEvent(
      new CustomEvent("element-create", { detail: { element: this.currentElement } }),
    );
    canvas.dispatchEvent(
      new CustomEvent("element-finalize", { detail: { element: this.currentElement } }),
    );
    this.cleanup();
  }

  onPointerMove(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }
  onPointerUp(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }

  getCursor(): string {
    return "copy";
  }
}

// =============================================================================
// STICKY NOTE TOOL — places a note element at the clicked location
// =============================================================================

export class StickyNoteTool extends BaseTool {
  onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
    const point = this.getMousePosition(event, canvas);
    const DEFAULT_SIZE = 200;

    this.currentElement = new NoteElement({
      id: nanoid(),
      xywh: JSON.stringify([point.x, point.y, DEFAULT_SIZE, DEFAULT_SIZE]),
      index: Date.now().toString(),
      title: "",
      backgroundColor: "#fef08a",
    });

    canvas.dispatchEvent(
      new CustomEvent("element-create", { detail: { element: this.currentElement } }),
    );
    canvas.dispatchEvent(
      new CustomEvent("element-finalize", { detail: { element: this.currentElement } }),
    );
    this.cleanup();
  }

  onPointerMove(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }
  onPointerUp(_event: PointerEvent, _canvas: HTMLCanvasElement): void { /* no-op */ }

  getCursor(): string {
    return "copy";
  }
}
