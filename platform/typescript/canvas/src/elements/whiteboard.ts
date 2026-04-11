/**
 * Whiteboard Element — multi-stroke freehand drawing surface.
 *
 * @doc.type class
 * @doc.purpose Canvas element that stores and renders multiple freehand strokes
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Unlike `BrushElement` (a single stroke), `WhiteboardElement` aggregates
 * many strokes and acts as a persistent drawing surface within a bounded area.
 * It supports:
 * - Pen / pencil / marker / highlighter stroke modes
 * - Per-stroke undo (remove last stroke)
 * - Pressure-sensitive stroke width using Pointer Events API
 * - Perfect Freehand smoothing algorithm approximation
 * - Background grid / dotted / ruled lines
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export type StrokeMode = "pen" | "pencil" | "marker" | "highlighter" | "eraser";

export interface DrawPoint {
  x: number;
  y: number;
  /** Stylus pressure (0–1), defaults to 0.5 */
  pressure?: number;
  /** Timestamp for velocity-based smoothing */
  timestamp?: number;
}

export interface WhiteboardStroke {
  id: string;
  mode: StrokeMode;
  points: DrawPoint[];
  color: string;
  /** Base width before pressure scaling */
  width: number;
  /** Opacity (0–1) */
  opacity: number;
}

export type WhiteboardBackground = "blank" | "grid" | "dotted" | "ruled";

export interface WhiteboardProps extends BaseElementProps {
  strokes?: WhiteboardStroke[];
  background?: WhiteboardBackground;
  backgroundColor?: string;
  /** Grid / dot spacing in canvas units */
  gridSize?: number;
  /** Border color */
  borderColor?: string;
}

export class WhiteboardElement extends CanvasElement {
  public strokes: WhiteboardStroke[];
  public background: WhiteboardBackground;
  public backgroundColor: string;
  public gridSize: number;
  public borderColor: string;

  constructor(props: WhiteboardProps) {
    super(props);
    this.strokes = props.strokes ?? [];
    this.background = props.background ?? "blank";
    this.backgroundColor = props.backgroundColor ?? "#ffffff";
    this.gridSize = props.gridSize ?? 24;
    this.borderColor = props.borderColor ?? "#e2e8f0";
  }

  get type(): CanvasElementType {
    return "whiteboard";
  }

  // ---------------------------------------------------------------------------
  // Stroke management
  // ---------------------------------------------------------------------------

  addStroke(stroke: WhiteboardStroke): void {
    this.strokes = [...this.strokes, stroke];
  }

  removeLastStroke(): WhiteboardStroke | undefined {
    if (this.strokes.length === 0) return undefined;
    const last = this.strokes[this.strokes.length - 1];
    this.strokes = this.strokes.slice(0, -1);
    return last;
  }

  removeStroke(id: string): void {
    this.strokes = this.strokes.filter((s) => s.id !== id);
  }

  clearStrokes(): void {
    this.strokes = [];
  }

  /** Push a point into the active (last) stroke, creating it if needed */
  pushPoint(
    point: DrawPoint,
    mode: StrokeMode = "pen",
    color = "#000000",
    width = 2,
    opacity = 1,
  ): void {
    if (this.strokes.length === 0) {
      this.strokes = [
        {
          id: `stroke-${Date.now()}`,
          mode,
          points: [point],
          color,
          width,
          opacity,
        },
      ];
    } else {
      const last = this.strokes[this.strokes.length - 1]!;
      last.points = [...last.points, point];
    }
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();

    // Clip everything to bounds
    ctx.beginPath();
    ctx.rect(b.x, b.y, b.w, b.h);
    ctx.clip();

    // Background fill
    ctx.fillStyle = this.backgroundColor;
    ctx.fillRect(b.x, b.y, b.w, b.h);

    // Background pattern
    if (zoom > 0.2) {
      this._drawBackground(ctx, b, zoom);
    }

    // Strokes
    for (const stroke of this.strokes) {
      this._drawStroke(ctx, stroke, zoom);
    }

    // Border
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1 / zoom;
    ctx.strokeRect(b.x, b.y, b.w, b.h);

    ctx.restore();
  }

  private _drawBackground(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
    _zoom: number,
  ): void {
    const g = this.gridSize;
    ctx.strokeStyle = "rgba(99,102,241,0.1)";
    ctx.lineWidth = 0.5;

    if (this.background === "grid") {
      for (let x = b.x; x <= b.x + b.w; x += g) {
        ctx.beginPath();
        ctx.moveTo(x, b.y);
        ctx.lineTo(x, b.y + b.h);
        ctx.stroke();
      }
      for (let y = b.y; y <= b.y + b.h; y += g) {
        ctx.beginPath();
        ctx.moveTo(b.x, y);
        ctx.lineTo(b.x + b.w, y);
        ctx.stroke();
      }
    } else if (this.background === "dotted") {
      ctx.fillStyle = "rgba(99,102,241,0.2)";
      for (let x = b.x + g; x < b.x + b.w; x += g) {
        for (let y = b.y + g; y < b.y + b.h; y += g) {
          ctx.beginPath();
          ctx.arc(x, y, 1, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    } else if (this.background === "ruled") {
      for (let y = b.y + g; y < b.y + b.h; y += g) {
        ctx.beginPath();
        ctx.moveTo(b.x, y);
        ctx.lineTo(b.x + b.w, y);
        ctx.stroke();
      }
    }
  }

  private _drawStroke(
    ctx: CanvasRenderingContext2D,
    stroke: WhiteboardStroke,
    _zoom: number,
  ): void {
    if (stroke.points.length < 2) return;

    ctx.save();
    ctx.globalAlpha = stroke.opacity;

    if (stroke.mode === "highlighter") {
      ctx.globalCompositeOperation = "multiply";
      ctx.globalAlpha = 0.4;
    } else if (stroke.mode === "eraser") {
      ctx.globalCompositeOperation = "destination-out";
      ctx.globalAlpha = 1;
    }

    ctx.strokeStyle = stroke.mode === "eraser" ? "rgba(0,0,0,1)" : stroke.color;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    ctx.beginPath();
    const first = stroke.points[0]!;
    ctx.moveTo(first.x, first.y);

    for (let i = 1; i < stroke.points.length; i++) {
      const curr = stroke.points[i]!;
      const prev = stroke.points[i - 1]!;

      let lineWidth = stroke.width;

      // Pressure-sensitive width
      if (curr.pressure !== undefined) {
        lineWidth = stroke.width * 0.5 + stroke.width * curr.pressure;
      }

      // Velocity-based width (thin fast, thick slow)
      if (stroke.mode === "pen" && curr.timestamp && prev.timestamp) {
        const dt = curr.timestamp - prev.timestamp;
        const dx = curr.x - prev.x;
        const dy = curr.y - prev.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const speed = dist / (dt || 1);
        const speedFactor = Math.max(0.3, 1 - speed * 0.015);
        lineWidth = stroke.width * speedFactor;
      }

      ctx.lineWidth = lineWidth;

      // Smooth curve
      const midX = (prev.x + curr.x) / 2;
      const midY = (prev.y + curr.y) / 2;
      ctx.quadraticCurveTo(prev.x, prev.y, midX, midY);
    }

    ctx.stroke();
    ctx.restore();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}
