import { Point, SerializedXYWH } from "../types/index.js";

export class Bound {
  constructor(
    public x: number,
    public y: number,
    public w: number,
    public h: number,
  ) {}

  containsPoint(point: Point): boolean {
    return (
      point.x >= this.x &&
      point.x <= this.x + this.w &&
      point.y >= this.y &&
      point.y <= this.y + this.h
    );
  }

  expand(padding: number | Point): Bound {
    if (typeof padding === "number") {
      return new Bound(
        this.x - padding,
        this.y - padding,
        this.w + padding * 2,
        this.h + padding * 2,
      );
    } else {
      return new Bound(
        this.x - padding.x,
        this.y - padding.y,
        this.w + padding.x * 2,
        this.h + padding.y * 2,
      );
    }
  }

  serialize(): SerializedXYWH {
    return JSON.stringify([this.x, this.y, this.w, this.h]);
  }

  static deserialize(xywh: SerializedXYWH): Bound {
    const [x, y, w, h] = JSON.parse(xywh);
    return new Bound(x, y, w, h);
  }

  static fromXYWH(x: number, y: number, w: number, h: number): Bound {
    return new Bound(x, y, w, h);
  }

  get center(): Point {
    return {
      x: this.x + this.w / 2,
      y: this.y + this.h / 2,
    };
  }

  get points(): Point[] {
    return [
      { x: this.x, y: this.y },
      { x: this.x + this.w, y: this.y },
      { x: this.x + this.w, y: this.y + this.h },
      { x: this.x, y: this.y + this.h },
    ];
  }

  intersects(other: Bound): boolean {
    return !(
      this.x + this.w < other.x ||
      other.x + other.w < this.x ||
      this.y + this.h < other.y ||
      other.y + other.h < this.y
    );
  }
}
