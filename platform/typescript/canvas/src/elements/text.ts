import { BaseElementProps, Point, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface TextProps extends BaseElementProps {
  text: string;
  fontSize: number;
  fontFamily: string;
  color: string;
  textAlign?: "left" | "center" | "right";
  textVerticalAlign?: "top" | "middle" | "bottom";
}

export class TextElement extends CanvasElement {
  public text: string;
  public fontSize: number;
  public fontFamily: string;
  public color: string;
  public textAlign: "left" | "center" | "right";
  public textVerticalAlign: "top" | "middle" | "bottom";

  constructor(props: TextProps) {
    super(props);
    this.text = props.text;
    this.fontSize = props.fontSize;
    this.fontFamily = props.fontFamily;
    this.color = props.color;
    this.textAlign = props.textAlign || "left";
    this.textVerticalAlign = props.textVerticalAlign || "top";
  }

  get type(): CanvasElementType {
    return "text";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    // Semantic zoom: hide text if too small
    if (zoom < 0.2) return;

    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    // Set font
    ctx.font = `${this.fontSize}px ${this.fontFamily}`;
    ctx.fillStyle = this.color;
    ctx.textAlign = this.textAlign;
    ctx.textBaseline = this.textVerticalAlign;

    // Calculate text position
    let x = bound.x;
    let y = bound.y;

    if (this.textAlign === "center") {
      x = bound.x + bound.w / 2;
    } else if (this.textAlign === "right") {
      x = bound.x + bound.w;
    }

    if (this.textVerticalAlign === "middle") {
      y = bound.y + bound.h / 2;
    } else if (this.textVerticalAlign === "bottom") {
      y = bound.y + bound.h;
    } else {
      y = bound.y + this.fontSize;
    }

    // Handle multiline text
    const lines = this.text.split("\n");
    const lineHeight = this.fontSize * 1.2;

    lines.forEach((line, index) => {
      const lineY =
        this.textVerticalAlign === "middle"
          ? y - ((lines.length - 1) * lineHeight) / 2 + index * lineHeight
          : y + index * lineHeight;

      ctx.fillText(line, x, lineY);
    });

    ctx.restore();
  }

  includesPoint(x: number, y: number): boolean {
    const bound = this.getBounds();
    return bound.containsPoint({ x, y });
  }

  // Update text and adjust bounds if needed
  updateText(newText: string): void {
    this.text = newText;

    // Auto-resize bounds if text is too large
    const ctx = document.createElement("canvas").getContext("2d");
    if (ctx) {
      ctx.font = `${this.fontSize}px ${this.fontFamily}`;
      const lines = this.text.split("\n");
      const maxWidth = Math.max(
        ...lines.map((line) => ctx.measureText(line).width),
      );
      const totalHeight = lines.length * this.fontSize * 1.2;

      const bound = this.getBounds();
      if (maxWidth > bound.w || totalHeight > bound.h) {
        const newW = Math.max(maxWidth, bound.w);
        const newH = Math.max(totalHeight, bound.h);
        this.xywh = Bound.fromXYWH(bound.x, bound.y, newW, newH).serialize();
      }
    }
  }
}
