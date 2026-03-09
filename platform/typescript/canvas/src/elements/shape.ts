import { BaseElementProps, Point, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { getTheme } from "../theme/defaults.js";

export type ShapeType = "rect" | "circle" | "diamond" | "triangle" | "ellipse" | "star";

export interface ShapeShadow {
  offsetX: number;
  offsetY: number;
  blur: number;
  color: string;
}

export interface ShapeProps extends BaseElementProps {
  shapeType: ShapeType;
  fillColor: string;
  strokeColor: string;
  strokeWidth: number;
  filled?: boolean;
  strokeStyle?: "solid" | "dashed" | "none";
  radius?: number;
  shadow?: ShapeShadow;
  text?: string;
  textColor?: string;
  fontSize?: number;
  fontFamily?: string;
  textAlign?: CanvasTextAlign;
  textVerticalAlign?: "top" | "middle" | "bottom";
  padding?: [number, number]; // [vertical, horizontal]
  textWrap?: boolean;
  roughness?: number;
}

export class ShapeElement extends CanvasElement {
  public shapeType: ShapeType;
  public fillColor: string;
  public strokeColor: string;
  public strokeWidth: number;
  public filled: boolean;
  public strokeStyle: "solid" | "dashed" | "none" = "solid";
  public roughness?: number;
  public radius: number;
  public shadow?: ShapeShadow;
  public text?: string;
  public textColor?: string;
  public fontSize: number;
  public fontFamily: string;
  public textAlign: CanvasTextAlign;
  public textVerticalAlign: "top" | "middle" | "bottom";
  public padding: [number, number];
  public textWrap: boolean;

  constructor(props: ShapeProps) {
    super(props);
    const theme = getTheme();
    this.shapeType = props.shapeType ?? "rect";
    this.fillColor = props.fillColor ?? theme.colors.shapeFillColor;
    this.strokeColor = props.strokeColor ?? theme.colors.shapeStrokeColor;
    this.strokeWidth = props.strokeWidth ?? 2;
    this.filled = props.filled ?? true;
    this.strokeStyle = props.strokeStyle || "solid";
    this.roughness = props.roughness;
    this.radius = props.radius ?? 0;
    this.shadow = props.shadow;
    this.text = props.text;
    this.textColor = props.textColor ?? theme.colors.shapeTextColor;
    this.fontSize = props.fontSize ?? theme.typography.fontSize;
    this.fontFamily = props.fontFamily ?? theme.typography.fontFamily;
    this.textAlign = props.textAlign ?? "center";
    this.textVerticalAlign = props.textVerticalAlign ?? "middle";
    this.padding = props.padding ?? theme.spacing.padding;
    this.textWrap = props.textWrap ?? true;
  }

  get type(): CanvasElementType {
    return "shape";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    // Set styles
    if (this.filled) {
      ctx.fillStyle = this.fillColor;
    }

    ctx.strokeStyle = this.strokeStyle === "none" ? "transparent" : this.strokeColor;
    ctx.lineWidth = this.strokeWidth / zoom;

    if (this.strokeStyle === "dashed") {
      ctx.setLineDash([12 / zoom, 12 / zoom]);
    }

    // Shadows
    if (this.shadow) {
      ctx.shadowColor = this.shadow.color;
      ctx.shadowBlur = this.shadow.blur;
      ctx.shadowOffsetX = this.shadow.offsetX;
      ctx.shadowOffsetY = this.shadow.offsetY;
    }

    // Draw shape based on type
    switch (this.shapeType) {
      case "rect":
        this.drawRectangle(ctx, bound);
        break;
      case "circle":
        this.drawCircle(ctx, bound);
        break;
      case "diamond":
        this.drawDiamond(ctx, bound);
        break;
      case "triangle":
        this.drawTriangle(ctx, bound);
        break;
      case "ellipse":
        this.drawEllipse(ctx, bound);
        break;
      case "star":
        this.drawStar(ctx, bound);
        break;
      default:
        this.drawRectangle(ctx, bound);
    }

    // Reset dash/shadow
    if (this.strokeStyle === "dashed") {
      ctx.setLineDash([]);
    }
    if (this.shadow) {
      ctx.shadowColor = "transparent";
      ctx.shadowBlur = 0;
      ctx.shadowOffsetX = 0;
      ctx.shadowOffsetY = 0;
    }

    // Optional text inside shape with padding, vertical alignment, and wrapping
    if (this.text) {
      this.renderText(ctx, bound);
    }

    ctx.restore();
  }

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();

    // For rectangles, check if point is within bounds
    if (this.shapeType === "rect") {
      const result = x >= bound.x && x <= bound.x + bound.w &&
        y >= bound.y && y <= bound.y + bound.h;
      return result;
    }

    let result = false;
    switch (this.shapeType) {
      case "circle":
        const centerX = bound.x + bound.w / 2;
        const centerY = bound.y + bound.h / 2;
        const radius = Math.min(bound.w, bound.h) / 2;
        const distance = Math.sqrt((x - centerX) ** 2 + (y - centerY) ** 2);
        result = distance <= radius;
        break;
      case "diamond":
        result = this.isPointInDiamond(x, y, bound);
        break;
      case "triangle":
        result = this.isPointInTriangle(x, y, bound);
        break;
      case "ellipse":
        result = this.isPointInEllipse(x, y, bound);
        break;
      case "star":
        // Fallback for star, use bound check
        result = bound.containsPoint({ x, y });
        break;
      default:
        result = bound.containsPoint({ x, y });
    }

    return result;
  }

  private drawRectangle(ctx: CanvasRenderingContext2D, bound: Bound): void {
    if (this.radius && this.radius > 0) {
      this.drawRoundedRect(ctx, bound, this.radius);
    } else {
      if (this.filled) {
        ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
      }
      if (this.strokeStyle !== "none") {
        ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
      }
    }
  }

  private drawCircle(ctx: CanvasRenderingContext2D, bound: Bound): void {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;
    const radius = Math.min(bound.w, bound.h) / 2;

    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, 0, 2 * Math.PI);

    if (this.filled) {
      ctx.fill();
    }
    if (this.strokeStyle !== "none") {
      ctx.stroke();
    }
  }

  private drawRoundedRect(ctx: CanvasRenderingContext2D, bound: Bound, radius: number): void {
    const r = Math.max(Math.min(radius, Math.min(bound.w, bound.h) / 2), 0);
    ctx.beginPath();
    ctx.moveTo(bound.x + r, bound.y);
    ctx.lineTo(bound.x + bound.w - r, bound.y);
    ctx.arcTo(bound.x + bound.w, bound.y, bound.x + bound.w, bound.y + r, r);
    ctx.lineTo(bound.x + bound.w, bound.y + bound.h - r);
    ctx.arcTo(bound.x + bound.w, bound.y + bound.h, bound.x + bound.w - r, bound.y + bound.h, r);
    ctx.lineTo(bound.x + r, bound.y + bound.h);
    ctx.arcTo(bound.x, bound.y + bound.h, bound.x, bound.y + bound.h - r, r);
    ctx.lineTo(bound.x, bound.y + r);
    ctx.arcTo(bound.x, bound.y, bound.x + r, bound.y, r);
    ctx.closePath();

    if (this.filled) {
      ctx.fill();
    }
    if (this.strokeStyle !== "none") {
      ctx.stroke();
    }
  }

  private drawDiamond(ctx: CanvasRenderingContext2D, bound: Bound): void {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;

    ctx.beginPath();
    ctx.moveTo(centerX, bound.y);
    ctx.lineTo(bound.x + bound.w, centerY);
    ctx.lineTo(centerX, bound.y + bound.h);
    ctx.lineTo(bound.x, centerY);
    ctx.closePath();

    if (this.filled) {
      ctx.fill();
    }
    if (this.strokeStyle !== "none") {
      ctx.stroke();
    }
  }

  /**
   * Render text with padding, vertical alignment, multiline wrapping, and RTL support
   */
  private renderText(ctx: CanvasRenderingContext2D, bound: Bound): void {
    if (!this.text) return;

    ctx.fillStyle = this.textColor ?? "#111827";
    ctx.font = `${this.fontSize}px ${this.fontFamily}`;
    ctx.textAlign = this.textAlign;

    const [verticalPadding, horizontalPadding] = this.padding;
    const availableWidth = bound.w - horizontalPadding * 2;
    const availableHeight = bound.h - verticalPadding * 2;

    // Split text into lines
    const lines = this.textWrap
      ? this.wrapText(ctx, this.text, availableWidth)
      : this.text.split('\n');

    const lineHeight = this.fontSize * 1.5;
    const totalTextHeight = lines.length * lineHeight;

    // Calculate vertical offset based on alignment
    let startY: number;
    switch (this.textVerticalAlign) {
      case "top":
        startY = bound.y + verticalPadding + this.fontSize;
        break;
      case "bottom":
        startY = bound.y + bound.h - verticalPadding - totalTextHeight + this.fontSize;
        break;
      case "middle":
      default:
        startY = bound.y + (bound.h - totalTextHeight) / 2 + this.fontSize;
        break;
    }

    // Calculate horizontal offset based on alignment
    let textX: number;
    switch (this.textAlign) {
      case "left":
        textX = bound.x + horizontalPadding;
        break;
      case "right":
        textX = bound.x + bound.w - horizontalPadding;
        break;
      case "center":
      default:
        textX = bound.x + bound.w / 2;
        break;
    }

    // Detect RTL and set canvas direction
    const isRTL = this.isRTLText(this.text);
    const originalDir = ctx.canvas.dir;
    if (isRTL) {
      ctx.canvas.dir = 'rtl';
    }

    // Draw each line
    ctx.textBaseline = "top";
    lines.forEach((line, index) => {
      const y = startY + index * lineHeight;
      if (y + lineHeight <= bound.y + bound.h - verticalPadding) {
        ctx.fillText(line, textX, y);
      }
    });

    // Restore canvas direction
    if (isRTL) {
      ctx.canvas.dir = originalDir;
    }
  }

  /**
   * Wrap text to fit within available width
   */
  private wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
    const lines: string[] = [];
    const paragraphs = text.split('\n');

    for (const paragraph of paragraphs) {
      if (!paragraph.trim()) {
        lines.push('');
        continue;
      }

      const words = paragraph.split(' ');
      let currentLine = '';

      for (const word of words) {
        const testLine = currentLine ? `${currentLine} ${word}` : word;
        const metrics = ctx.measureText(testLine);

        if (metrics.width > maxWidth && currentLine) {
          lines.push(currentLine);
          currentLine = word;
        } else {
          currentLine = testLine;
        }
      }

      if (currentLine) {
        lines.push(currentLine);
      }
    }

    return lines;
  }

  /**
   * Detect if text is RTL (Right-to-Left) based on first character
   */
  private isRTLText(text: string): boolean {
    if (!text) return false;
    const firstChar = text.trim()[0];
    if (!firstChar) return false;

    const code = firstChar.charCodeAt(0);
    // Hebrew: 0x0590-0x05FF, Arabic: 0x0600-0x06FF, 0x0750-0x077F
    return (
      (code >= 0x0590 && code <= 0x05ff) ||
      (code >= 0x0600 && code <= 0x06ff) ||
      (code >= 0x0750 && code <= 0x077f)
    );
  }

  private drawTriangle(ctx: CanvasRenderingContext2D, bound: Bound): void {
    ctx.beginPath();
    ctx.moveTo(bound.x + bound.w / 2, bound.y);
    ctx.lineTo(bound.x + bound.w, bound.y + bound.h);
    ctx.lineTo(bound.x, bound.y + bound.h);
    ctx.closePath();

    if (this.filled) {
      ctx.fill();
    }
    ctx.stroke();
  }

  private drawEllipse(ctx: CanvasRenderingContext2D, bound: Bound): void {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;
    const radiusX = bound.w / 2;
    const radiusY = bound.h / 2;

    ctx.beginPath();
    ctx.ellipse(centerX, centerY, radiusX, radiusY, 0, 0, 2 * Math.PI);

    if (this.filled) {
      ctx.fill();
    }
    if (this.strokeStyle !== "none") {
      ctx.stroke();
    }
  }

  private drawStar(ctx: CanvasRenderingContext2D, bound: Bound): void {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;
    const outerRadius = Math.min(bound.w, bound.h) / 2;
    const innerRadius = outerRadius / 2.5;
    const points = 5;

    ctx.beginPath();
    for (let i = 0; i < points * 2; i++) {
      const radius = i % 2 === 0 ? outerRadius : innerRadius;
      const angle = (Math.PI * i) / points - Math.PI / 2;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();

    if (this.filled) {
      ctx.fill();
    }
    if (this.strokeStyle !== "none") {
      ctx.stroke();
    }
  }

  private isPointInDiamond(x: number, y: number, bound: Bound): boolean {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;

    const dx = Math.abs(x - centerX) / (bound.w / 2);
    const dy = Math.abs(y - centerY) / (bound.h / 2);

    return dx + dy <= 1;
  }

  private isPointInTriangle(x: number, y: number, bound: Bound): boolean {
    const p1 = { x: bound.x + bound.w / 2, y: bound.y };
    const p2 = { x: bound.x + bound.w, y: bound.y + bound.h };
    const p3 = { x: bound.x, y: bound.y + bound.h };

    return this.pointInTriangle({ x, y }, p1, p2, p3);
  }

  private pointInTriangle(p: Point, p1: Point, p2: Point, p3: Point): boolean {
    const area =
      0.5 *
      Math.abs((p2.x - p1.x) * (p3.y - p1.y) - (p3.x - p1.x) * (p2.y - p1.y));

    const area1 =
      0.5 *
      Math.abs((p.x - p1.x) * (p2.y - p1.y) - (p2.x - p1.x) * (p.y - p1.y));

    const area2 =
      0.5 * Math.abs((p2.x - p.x) * (p3.y - p.y) - (p3.x - p.x) * (p2.y - p.y));

    const area3 =
      0.5 * Math.abs((p3.x - p.x) * (p1.y - p.y) - (p1.x - p.x) * (p3.y - p.y));

    return Math.abs(area - (area1 + area2 + area3)) < 0.001;
  }

  private isPointInEllipse(x: number, y: number, bound: Bound): boolean {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + bound.h / 2;
    const radiusX = bound.w / 2;
    const radiusY = bound.h / 2;

    const dx = (x - centerX) / radiusX;
    const dy = (y - centerY) / radiusY;

    return dx * dx + dy * dy <= 1;
  }
}
