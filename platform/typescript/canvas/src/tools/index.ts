import { Point, ToolOptions } from "../types/index.js";
import { CanvasElement } from "../elements/base.js";
import { ShapeElement, ShapeType } from "../elements/shape.js";
import { TextElement } from "../elements/text.js";
import { BrushElement } from "../elements/brush.js";
import { ConnectorTool } from "./connector-tool.js";
import { BaseTool } from "./base-tool.js";
import { nanoid } from "nanoid";

export { BaseTool } from "./base-tool.js";

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

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[ShapeTool] create', { id: ((this.currentElement as unknown as Record<string, unknown>)).id, xywh: this.currentElement.xywh });

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

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[ShapeTool] update', { id: ((this.currentElement as unknown as Record<string, unknown>)).id, xywh: this.currentElement.xywh });
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
    // Debug
    // eslint-disable-next-line no-console
    console.debug('[ShapeTool] finalize');
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

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[BrushTool] create', { id: ((this.currentElement as unknown as Record<string, unknown>)).id, xywh: this.currentElement.xywh });
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

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[BrushTool] update', { id: (this.currentElement as unknown as Record<string, unknown>).id, points: ((this.currentElement as unknown as Record<string, unknown>).points as unknown[]).length });
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

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[BrushTool] finalize');
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

    // Import dynamically to avoid circular deps
    const { HighlighterElement } = require("../elements/highlighter.js");

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

    // Register ConnectorTool
    this.registerTool(
      "connector",
      new ConnectorTool({ type: "connector", color: "#1e40af", strokeWidth: 2 }),
    );

    // Default to select tool for pointer interactions
    this.setActiveTool("select");
  }

  registerTool(name: string, tool: BaseTool): void {
    this.tools.set(name, tool);
  }

  setActiveTool(name: string): void {
    // Debug
    // eslint-disable-next-line no-console
    console.debug('[ToolManager] setActiveTool ->', name);

    if (this.activeTool) {
      this.activeTool.deactivate();
    }

    const tool = this.tools.get(name);
    if (tool) {
      this.activeTool = tool;
      this.activeToolName = name;
      tool.activate();
    } else {
      // eslint-disable-next-line no-console
      console.warn('[ToolManager] requested tool not found:', name);
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
    // Debug
    // eslint-disable-next-line no-console
    console.debug('[ToolManager] pointerDown activeTool=', this.activeToolName);
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
