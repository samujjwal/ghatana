import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface ConnectorProps extends BaseElementProps {
  startElementId?: string;
  endElementId?: string;
  startPoint?: { x: number; y: number };
  endPoint?: { x: number; y: number };
  strokeColor: string;
  strokeWidth: number;
  connectorType: "straight" | "orthogonal" | "curved";
  arrowStyle?: "none" | "arrow" | "diamond";
}

export class ConnectorElement extends CanvasElement {
  public startElementId?: string;
  public endElementId?: string;
  public startPoint: { x: number; y: number };
  public endPoint: { x: number; y: number };
  public strokeColor: string;
  public strokeWidth: number;
  public connectorType: "straight" | "orthogonal" | "curved";
  public arrowStyle: "none" | "arrow" | "diamond";

  constructor(props: ConnectorProps) {
    super(props);
    this.startElementId = props.startElementId;
    this.endElementId = props.endElementId;
    this.startPoint = props.startPoint || { x: 0, y: 0 };
    this.endPoint = props.endPoint || { x: 100, y: 100 };
    this.strokeColor = props.strokeColor;
    this.strokeWidth = props.strokeWidth;
    this.connectorType = props.connectorType;
    this.arrowStyle = props.arrowStyle || "arrow";
  }

  get type(): CanvasElementType {
    return "connector";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    ctx.strokeStyle = this.strokeColor;
    ctx.lineWidth = this.strokeWidth / zoom; // Maintain consistent visual width
    ctx.lineCap = "round";
    ctx.lineJoin = "round";

    // Semantic zoom: hide arrows if too zoomed out
    const showArrow = this.arrowStyle !== "none" && zoom > 0.4;

    // Draw connector path
    this.drawConnectorPath(ctx);

    // Draw arrow if needed
    if (showArrow) {
      this.drawArrow(ctx, zoom);
    }

    ctx.restore();
  }

  includesPoint(x: number, y: number): boolean {
    const tolerance = this.strokeWidth + 4;

    // Check if point is near the connector path
    const distance = this.pointToLineDistance(
      { x, y },
      this.startPoint,
      this.endPoint,
    );
    return distance <= tolerance;
  }

  private drawConnectorPath(ctx: CanvasRenderingContext2D): void {
    ctx.beginPath();

    switch (this.connectorType) {
      case "straight":
        ctx.moveTo(this.startPoint.x, this.startPoint.y);
        ctx.lineTo(this.endPoint.x, this.endPoint.y);
        break;

      case "orthogonal":
        this.drawOrthogonalPath(ctx);
        break;

      case "curved":
        this.drawCurvedPath(ctx);
        break;
    }

    ctx.stroke();
  }

  private drawOrthogonalPath(ctx: CanvasRenderingContext2D): void {
    const midX = this.startPoint.x + (this.endPoint.x - this.startPoint.x) / 2;
    const midY = this.startPoint.y + (this.endPoint.y - this.startPoint.y) / 2;

    ctx.moveTo(this.startPoint.x, this.startPoint.y);

    // Determine path based on relative positions
    const dx = Math.abs(this.endPoint.x - this.startPoint.x);
    const dy = Math.abs(this.endPoint.y - this.startPoint.y);

    if (dx > dy) {
      // Horizontal dominant
      ctx.lineTo(midX, this.startPoint.y);
      ctx.lineTo(midX, this.endPoint.y);
      ctx.lineTo(this.endPoint.x, this.endPoint.y);
    } else {
      // Vertical dominant
      ctx.lineTo(this.startPoint.x, midY);
      ctx.lineTo(this.endPoint.x, midY);
      ctx.lineTo(this.endPoint.x, this.endPoint.y);
    }
  }

  private drawCurvedPath(ctx: CanvasRenderingContext2D): void {
    const midX = this.startPoint.x + (this.endPoint.x - this.startPoint.x) / 2;
    const midY = this.startPoint.y + (this.endPoint.y - this.startPoint.y) / 2;

    ctx.moveTo(this.startPoint.x, this.startPoint.y);
    ctx.quadraticCurveTo(midX, midY, this.endPoint.x, this.endPoint.y);
  }

  private drawArrow(ctx: CanvasRenderingContext2D, zoom: number): void {
    const angle = Math.atan2(
      this.endPoint.y - this.startPoint.y,
      this.endPoint.x - this.startPoint.x,
    );

    const arrowLength = 12 / zoom;
    const arrowAngle = Math.PI / 6;

    ctx.save();
    ctx.translate(this.endPoint.x, this.endPoint.y);
    ctx.rotate(angle);

    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(
      -arrowLength * Math.cos(arrowAngle),
      -arrowLength * Math.sin(arrowAngle),
    );
    ctx.moveTo(0, 0);
    ctx.lineTo(
      -arrowLength * Math.cos(arrowAngle),
      arrowLength * Math.sin(arrowAngle),
    );
    ctx.stroke();

    ctx.restore();
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

  setStartPoint(point: { x: number; y: number }): void {
    this.startPoint = point;
    this.updateBounds();
  }

  setEndPoint(point: { x: number; y: number }): void {
    this.endPoint = point;
    this.updateBounds();
  }

  private updateBounds(): void {
    const minX = Math.min(this.startPoint.x, this.endPoint.x);
    const minY = Math.min(this.startPoint.y, this.endPoint.y);
    const maxX = Math.max(this.startPoint.x, this.endPoint.x);
    const maxY = Math.max(this.startPoint.y, this.endPoint.y);

    // Add padding for stroke width
    const padding = this.strokeWidth + 10;

    this.xywh = Bound.fromXYWH(
      minX - padding,
      minY - padding,
      maxX - minX + padding * 2,
      maxY - minY + padding * 2,
    ).serialize();
  }
}
