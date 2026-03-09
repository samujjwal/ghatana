import {
  Point,
  SerializedXYWH,
  BaseElementProps,
  PointTestOptions,
  CanvasElementType,
} from "../types/index.js";
import { Bound } from "../utils/bounds.js";

export abstract class CanvasElement {
  public id: string;
  public xywh: SerializedXYWH;
  public rotate: number = 0;
  public selected: boolean = false;
  public index: string = "0";

  constructor(props: BaseElementProps) {
    this.id = props.id;
    this.xywh = props.xywh;
    this.rotate = props.rotate || 0;
    this.index = props.index || "0";
  }

  abstract get type(): CanvasElementType;

  /**
   * Render the element on the canvas context.
   * @param ctx Canvas rendering context
   * @param zoom Current viewport zoom level (optional, for semantic rendering)
   */
  abstract render(ctx: CanvasRenderingContext2D, zoom?: number): void;

  abstract includesPoint(
    x: number,
    y: number,
    options?: PointTestOptions,
  ): boolean;

  getBounds(): Bound {
    return Bound.deserialize(this.xywh);
  }

  get elementBound(): Bound {
    return this.getBounds();
  }

  get x(): number {
    return this.getBounds().x;
  }

  get y(): number {
    return this.getBounds().y;
  }

  get w(): number {
    return this.getBounds().w;
  }

  get h(): number {
    return this.getBounds().h;
  }

  containsBound(bounds: Bound): boolean {
    const elementBounds = this.getBounds();
    return bounds.points.some((point) => elementBounds.containsPoint(point));
  }

  intersectsBound(bound: Bound): boolean {
    return (
      this.containsBound(bound) ||
      bound.points.some((point, i, points) =>
        this.getLineIntersections(point, points[(i + 1) % points.length]),
      )
    );
  }

  getLineIntersections(start: Point, end: Point): Point[] | null {
    const bound = this.getBounds();
    const points = bound.points;

    for (let i = 0; i < points.length; i++) {
      const p1 = points[i];
      const p2 = points[(i + 1) % points.length];

      const intersection = this.lineIntersection(start, end, p1, p2);
      if (intersection) {
        return [intersection];
      }
    }

    return null;
  }

  private lineIntersection(
    p1: Point,
    p2: Point,
    p3: Point,
    p4: Point,
  ): Point | null {
    const x1 = p1.x,
      y1 = p1.y;
    const x2 = p2.x,
      y2 = p2.y;
    const x3 = p3.x,
      y3 = p3.y;
    const x4 = p4.x,
      y4 = p4.y;

    const denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
    if (Math.abs(denom) < 0.001) return null;

    const t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
    const u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom;

    if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
      return {
        x: x1 + t * (x2 - x1),
        y: y1 + t * (y2 - y1),
      };
    }

    return null;
  }

  applyTransform(ctx: CanvasRenderingContext2D): void {
    const bound = this.getBounds();
    ctx.translate(bound.x + bound.w / 2, bound.y + bound.h / 2);
    ctx.rotate((this.rotate * Math.PI) / 180);
    ctx.translate(-bound.x - bound.w / 2, -bound.y - bound.h / 2);
  }
}
