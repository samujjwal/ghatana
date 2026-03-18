import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface BrushProps extends BaseElementProps {
  points: { x: number; y: number; pressure?: number }[];
  color: string;
  lineWidth: number;
}

export class BrushElement extends CanvasElement {
  public points: { x: number; y: number; pressure?: number }[];
  public color: string;
  public lineWidth: number;

  constructor(props: BrushProps) {
    super(props);
    this.points = props.points;
    this.color = props.color;
    this.lineWidth = props.lineWidth;
  }

  get type(): CanvasElementType {
    return "brush";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    if (this.points.length < 2) return;

    ctx.save();
    this.applyTransform(ctx);

    ctx.strokeStyle = this.color;
    ctx.lineWidth = this.lineWidth / zoom; // Maintain consistent visual width
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    ctx.beginPath();
    ctx.moveTo(this.points[0].x, this.points[0].y);

    for (let i = 1; i < this.points.length; i++) {
      const point = this.points[i];
      const prevPoint = this.points[i - 1];

      // Use pressure if available
      const pressure = point.pressure || 1;
      ctx.lineWidth = (this.lineWidth * pressure) / zoom;

      // Smooth curve using quadratic bezier
      const midX = (prevPoint.x + point.x) / 2;
      const midY = (prevPoint.y + point.y) / 2;
      ctx.quadraticCurveTo(prevPoint.x, prevPoint.y, midX, midY);
    }

    ctx.stroke();
    ctx.restore();
  }

  includesPoint(x: number, y: number): boolean {
    if (this.points.length < 2) return false;

    const tolerance = this.lineWidth + 2;

    for (let i = 1; i < this.points.length; i++) {
      const p1 = this.points[i - 1];
      const p2 = this.points[i];

      const distance = this.pointToLineDistance({ x, y }, p1, p2);
      if (distance <= tolerance) {
        return true;
      }
    }

    return false;
  }

  private pointToLineDistance(
    point: { x: number; y: number },
    lineStart: { x: number; y: number },
    lineEnd: { x: number; y: number },
  ): number {
    const A = point.x - lineStart.x;
    const B = point.y - lineStart.y;
    const C = lineEnd.x - lineStart.x;
    const D = lineEnd.y - lineStart.y;

    const dot = A * C + B * D;
    const lenSq = C * C + D * D;
    let param = -1;

    if (lenSq !== 0) {
      param = dot / lenSq;
    }

    let xx, yy;

    if (param < 0) {
      xx = lineStart.x;
      yy = lineStart.y;
    } else if (param > 1) {
      xx = lineEnd.x;
      yy = lineEnd.y;
    } else {
      xx = lineStart.x + param * C;
      yy = lineStart.y + param * D;
    }

    const dx = point.x - xx;
    const dy = point.y - yy;

    return Math.sqrt(dx * dx + dy * dy);
  }

  addPoint(point: { x: number; y: number; pressure?: number }): void {
    this.points.push(point);

    // Update bounds to include new point
    const bound = this.getBounds();
    const minX = Math.min(bound.x, ...this.points.map((p) => p.x));
    const minY = Math.min(bound.y, ...this.points.map((p) => p.y));
    const maxX = Math.max(bound.x + bound.w, ...this.points.map((p) => p.x));
    const maxY = Math.max(bound.y + bound.h, ...this.points.map((p) => p.y));

    this.xywh = Bound.fromXYWH(
      minX,
      minY,
      maxX - minX,
      maxY - minY,
    ).serialize();
  }
}
