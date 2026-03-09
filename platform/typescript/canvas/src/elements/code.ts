import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface CodeProps extends BaseElementProps {
  code: string;
  language: string;
  theme: "light" | "dark";
  fontSize: number;
}

export class CodeElement extends CanvasElement {
  public code: string;
  public language: string;
  public theme: "light" | "dark";
  public fontSize: number;

  constructor(props: CodeProps) {
    super(props);
    this.code = props.code;
    this.language = props.language;
    this.theme = props.theme;
    this.fontSize = props.fontSize;
  }

  get type(): CanvasElementType {
    return "code";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    const isLowDetail = zoom < 0.4;

    // Draw background
    this.drawBackground(ctx, bound);

    // Skip code if too zoomed out
    if (!isLowDetail) {
      // Draw code content
      this.drawCode(ctx, bound);
    }

    // Draw border
    this.drawBorder(ctx, bound, zoom);

    ctx.restore();
  }

  includesPoint(x: number, y: number): boolean {
    const bound = this.getBounds();
    return bound.containsPoint({ x, y });
  }

  private drawBackground(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
  ): void {
    const bgColor = this.theme === "dark" ? "#1e1e1e" : "#f8f8f8";
    ctx.fillStyle = bgColor;
    ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
  }

  private drawCode(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
  ): void {
    const textColor = this.theme === "dark" ? "#d4d4d4" : "#333333";
    const keywordColor = this.theme === "dark" ? "#569cd6" : "#0000ff";
    const stringColor = this.theme === "dark" ? "#ce9178" : "#a31515";
    const commentColor = this.theme === "dark" ? "#6a9955" : "#008000";

    ctx.font = `${this.fontSize}px 'Consolas', 'Monaco', monospace`;

    const lines = this.code.split("\n");
    const lineHeight = this.fontSize * 1.4;
    const padding = 12;

    lines.forEach((line, index) => {
      const y = bound.y + padding + (index + 1) * lineHeight;

      if (y > bound.y + bound.h - padding) return; // Don't draw beyond bounds

      // Simple syntax highlighting (basic keywords)
      const highlightedLine = this.highlightSyntax(line);

      highlightedLine.forEach((segment) => {
        ctx.fillStyle = this.getColorForSegment(
          segment.type,
          textColor,
          keywordColor,
          stringColor,
          commentColor,
        );
        ctx.fillText(segment.text, bound.x + padding, y);
      });
    });
  }

  private highlightSyntax(
    line: string,
  ): Array<{
    text: string;
    type: "normal" | "keyword" | "string" | "comment";
  }> {
    const segments: Array<{
      text: string;
      type: "normal" | "keyword" | "string" | "comment";
    }> = [];

    // Simple keyword detection
    const keywords = [
      "function",
      "const",
      "let",
      "var",
      "if",
      "else",
      "for",
      "while",
      "return",
      "class",
      "import",
      "export",
    ];

    // Check for comment
    if (line.trim().startsWith("//")) {
      segments.push({ text: line, type: "comment" });
      return segments;
    }

    // Simple tokenization
    const tokens = line.split(/(\s+)/);
    let currentText = "";
    let currentType: "normal" | "keyword" | "string" | "comment" = "normal";
    let inString = false;

    for (const token of tokens) {
      if (token === "") continue;

      if (token.match(/\s+/)) {
        if (currentText) {
          segments.push({ text: currentText, type: currentType });
          currentText = "";
        }
        segments.push({ text: token, type: "normal" });
      } else if (token === '"' || token === "'") {
        if (!inString) {
          if (currentText) {
            segments.push({ text: currentText, type: currentType });
            currentText = "";
          }
          currentText = token;
          currentType = "string";
          inString = true;
        } else {
          currentText += token;
          segments.push({ text: currentText, type: currentType });
          currentText = "";
          currentType = "normal";
          inString = false;
        }
      } else if (inString) {
        currentText += token;
      } else if (keywords.includes(token)) {
        if (currentText) {
          segments.push({ text: currentText, type: currentType });
        }
        segments.push({ text: token, type: "keyword" });
        currentText = "";
        currentType = "normal";
      } else {
        currentText += token;
      }
    }

    if (currentText) {
      segments.push({ text: currentText, type: currentType });
    }

    return segments;
  }

  private getColorForSegment(
    type: "normal" | "keyword" | "string" | "comment",
    defaultColor: string,
    keywordColor: string,
    stringColor: string,
    commentColor: string,
  ): string {
    switch (type) {
      case "keyword":
        return keywordColor;
      case "string":
        return stringColor;
      case "comment":
        return commentColor;
      default:
        return defaultColor;
    }
  }

  private drawBorder(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
    zoom: number = 1
  ): void {
    const borderColor = this.theme === "dark" ? "#3c3c3c" : "#d1d5db";
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 1 / zoom;
    ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
  }

  updateCode(newCode: string): void {
    this.code = newCode;

    // Auto-resize bounds if needed
    const lines = this.code.split("\n");
    const maxLineLength = Math.max(...lines.map((line) => line.length));

    const bound = this.getBounds();
    const estimatedWidth = maxLineLength * this.fontSize * 0.6 + 24; // Rough character width + padding
    const estimatedHeight = lines.length * this.fontSize * 1.4 + 24; // Line height + padding

    if (estimatedWidth > bound.w || estimatedHeight > bound.h) {
      const newW = Math.max(estimatedWidth, bound.w);
      const newH = Math.max(estimatedHeight, bound.h);
      this.xywh = Bound.fromXYWH(bound.x, bound.y, newW, newH).serialize();
    }
  }

  setTheme(theme: "light" | "dark"): void {
    this.theme = theme;
  }

  setLanguage(language: string): void {
    this.language = language;
  }
}
