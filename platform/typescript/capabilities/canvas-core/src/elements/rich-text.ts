/**
 * Rich Text Element - Advanced text with inline formatting
 * 
 * @doc.type class
 * @doc.purpose Canvas element for rich text with inline formatting (bold, italic, links, etc.)
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style rich text for:
 * - Inline formatting (bold, italic, underline, strikethrough)
 * - Links and mentions
 * - Code spans
 * - Headings
 * - Text colors and highlights
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type TextMark = 
  | { type: "bold" }
  | { type: "italic" }
  | { type: "underline" }
  | { type: "strikethrough" }
  | { type: "code" }
  | { type: "link"; href: string; title?: string }
  | { type: "mention"; id: string; name: string }
  | { type: "color"; value: string }
  | { type: "highlight"; value: string }
  | { type: "superscript" }
  | { type: "subscript" };

export interface TextSpan {
  /** Text content */
  text: string;
  /** Applied marks/formatting */
  marks?: TextMark[];
}

export type HeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

export interface RichTextProps extends BaseElementProps {
  /** Content as spans with formatting */
  content: TextSpan[];
  /** Font size (base) */
  fontSize?: number;
  /** Font family */
  fontFamily?: string;
  /** Line height multiplier */
  lineHeight?: number;
  /** Text color */
  color?: string;
  /** Text alignment */
  textAlign?: "left" | "center" | "right" | "justify";
  /** Vertical alignment */
  verticalAlign?: "top" | "middle" | "bottom";
  /** Padding */
  padding?: [number, number, number, number]; // [top, right, bottom, left]
  /** Word wrap */
  wordWrap?: boolean;
  /** Heading level (null for regular paragraph) */
  headingLevel?: HeadingLevel | null;
  /** Whether text is editable */
  editable?: boolean;
  /** Background color */
  backgroundColor?: string;
  /** Border radius */
  borderRadius?: number;
  /** Letter spacing */
  letterSpacing?: number;
}

interface MeasuredLine {
  spans: Array<{
    text: string;
    marks: TextMark[];
    x: number;
    width: number;
  }>;
  y: number;
  height: number;
  width: number;
}

export class RichTextElement extends CanvasElement {
  public content: TextSpan[];
  public fontSize: number;
  public fontFamily: string;
  public lineHeight: number;
  public color: string;
  public textAlign: "left" | "center" | "right" | "justify";
  public verticalAlign: "top" | "middle" | "bottom";
  public padding: [number, number, number, number];
  public wordWrap: boolean;
  public headingLevel: HeadingLevel | null;
  public editable: boolean;
  public backgroundColor?: string;
  public borderRadius: number;
  public letterSpacing: number;

  // Cached layout
  private _measuredLines: MeasuredLine[] = [];
  private _layoutDirty: boolean = true;

  // Heading size multipliers
  private static readonly HEADING_SIZES: Record<HeadingLevel, number> = {
    1: 2.0,
    2: 1.5,
    3: 1.25,
    4: 1.0,
    5: 0.875,
    6: 0.75,
  };

  constructor(props: RichTextProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.content = props.content || [{ text: "" }];
    this.fontSize = props.fontSize ?? (typeof theme.typography.fontSize === 'number' ? theme.typography.fontSize : theme.typography.fontSize.medium);
    this.fontFamily = props.fontFamily ?? theme.typography.fontFamily;
    this.lineHeight = props.lineHeight ?? 1.5;
    this.color = props.color ?? theme.colors.text.primary;
    this.textAlign = props.textAlign ?? "left";
    this.verticalAlign = props.verticalAlign ?? "top";
    this.padding = props.padding ?? [8, 12, 8, 12];
    this.wordWrap = props.wordWrap ?? true;
    this.headingLevel = props.headingLevel ?? null;
    this.editable = props.editable ?? true;
    this.backgroundColor = props.backgroundColor;
    this.borderRadius = props.borderRadius ?? 0;
    this.letterSpacing = props.letterSpacing ?? 0;
  }

  get type(): CanvasElementType {
    return "rich-text" as CanvasElementType;
  }

  get effectiveFontSize(): number {
    if (this.headingLevel) {
      return this.fontSize * RichTextElement.HEADING_SIZES[this.headingLevel];
    }
    return this.fontSize;
  }

  get plainText(): string {
    return this.content.map(span => span.text).join("");
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    // Skip rendering if too small to read
    if (zoom < 0.15) return;

    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();
    const theme = themeManager.getTheme();

    // Draw background if present
    if (this.backgroundColor) {
      this.drawBackground(ctx, bound);
    }

    // Simplified rendering at low zoom
    if (zoom < 0.3) {
      this.renderSimplified(ctx, bound, zoom);
      ctx.restore();
      return;
    }

    // Measure and layout text
    if (this._layoutDirty) {
      this.measureText(ctx, bound);
      this._layoutDirty = false;
    }

    // Draw text content
    this.drawText(ctx, bound, zoom, theme);

    ctx.restore();
  }

  private drawBackground(ctx: CanvasRenderingContext2D, bound: Bound): void {
    ctx.fillStyle = this.backgroundColor!;
    
    if (this.borderRadius > 0) {
      ctx.beginPath();
      this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
      ctx.fill();
    } else {
      ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
    }
  }

  private renderSimplified(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    // Draw a simplified representation (colored rectangle showing text presence)
    const theme = themeManager.getTheme();
    
    ctx.fillStyle = this.backgroundColor || "rgba(0, 0, 0, 0.05)";
    ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
    
    // Draw lines to represent text
    ctx.fillStyle = theme.colors.text.secondary;
    const lineHeight = this.effectiveFontSize * this.lineHeight;
    const contentHeight = bound.h - this.padding[0] - this.padding[2];
    const numLines = Math.floor(contentHeight / lineHeight);
    
    for (let i = 0; i < Math.min(numLines, 5); i++) {
      const y = bound.y + this.padding[0] + i * lineHeight + lineHeight * 0.7;
      const width = Math.min(bound.w - this.padding[1] - this.padding[3], 
                            (i === numLines - 1) ? bound.w * 0.6 : bound.w * 0.9);
      ctx.fillRect(
        bound.x + this.padding[3],
        y - 2,
        width,
        4
      );
    }
  }

  private measureText(ctx: CanvasRenderingContext2D, bound: Bound): void {
    this._measuredLines = [];

    const contentWidth = bound.w - this.padding[1] - this.padding[3];
    const fontSize = this.effectiveFontSize;
    const lineHeight = fontSize * this.lineHeight;

    // Flatten content into words with their marks
    const words: Array<{ text: string; marks: TextMark[]; isWhitespace: boolean }> = [];
    
    for (const span of this.content) {
      const marks = span.marks || [];
      const parts = span.text.split(/(\s+)/);
      
      for (const part of parts) {
        if (part) {
          words.push({
            text: part,
            marks: [...marks],
            isWhitespace: /^\s+$/.test(part),
          });
        }
      }
    }

    // Layout words into lines
    let currentLine: MeasuredLine = { spans: [], y: 0, height: lineHeight, width: 0 };
    let currentX = 0;

    for (const word of words) {
      const font = this.getFont(word.marks, fontSize);
      ctx.font = font;
      
      const wordWidth = ctx.measureText(word.text).width;

      // Check if we need to wrap
      if (this.wordWrap && currentX + wordWidth > contentWidth && currentLine.spans.length > 0) {
        // Finish current line
        currentLine.width = currentX;
        this._measuredLines.push(currentLine);
        
        // Start new line
        currentLine = { spans: [], y: this._measuredLines.length * lineHeight, height: lineHeight, width: 0 };
        currentX = 0;

        // Skip leading whitespace on new line
        if (word.isWhitespace) continue;
      }

      // Add word to current line
      currentLine.spans.push({
        text: word.text,
        marks: word.marks,
        x: currentX,
        width: wordWidth,
      });

      currentX += wordWidth;
    }

    // Add the last line
    if (currentLine.spans.length > 0) {
      currentLine.width = currentX;
      this._measuredLines.push(currentLine);
    }
  }

  private getFont(marks: TextMark[], fontSize: number): string {
    let style = "";
    let weight = "400";

    for (const mark of marks) {
      if (mark.type === "bold") {
        weight = "700";
      }
      if (mark.type === "italic") {
        style = "italic ";
      }
    }

    return `${style}${weight} ${fontSize}px ${this.fontFamily}`;
  }

  private drawText(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const contentWidth = bound.w - this.padding[1] - this.padding[3];
    const contentHeight = bound.h - this.padding[0] - this.padding[2];
    const fontSize = this.effectiveFontSize;
    const totalTextHeight = this._measuredLines.length * fontSize * this.lineHeight;

    // Calculate vertical offset based on alignment
    let startY = bound.y + this.padding[0];
    if (this.verticalAlign === "middle") {
      startY = bound.y + this.padding[0] + (contentHeight - totalTextHeight) / 2;
    } else if (this.verticalAlign === "bottom") {
      startY = bound.y + bound.h - this.padding[2] - totalTextHeight;
    }

    // Draw each line
    for (const line of this._measuredLines) {
      // Calculate horizontal offset based on alignment
      let offsetX = 0;
      if (this.textAlign === "center") {
        offsetX = (contentWidth - line.width) / 2;
      } else if (this.textAlign === "right") {
        offsetX = contentWidth - line.width;
      }

      const lineY = startY + line.y + fontSize * this.lineHeight * 0.75;

      // Draw each span in the line
      for (const span of line.spans) {
        const x = bound.x + this.padding[3] + offsetX + span.x;
        this.drawSpan(ctx, span.text, span.marks, x, lineY, fontSize, theme);
      }
    }
  }

  private drawSpan(
    ctx: CanvasRenderingContext2D,
    text: string,
    marks: TextMark[],
    x: number,
    y: number,
    fontSize: number,
    theme: YAPPCTheme
  ): void {
    // Set font
    ctx.font = this.getFont(marks, fontSize);
    ctx.textBaseline = "alphabetic";

    // Determine colors
    let fillColor = this.color;
    let highlightColor: string | null = null;
    let isLink = false;

    for (const mark of marks) {
      if (mark.type === "color") {
        fillColor = mark.value;
      }
      if (mark.type === "highlight") {
        highlightColor = mark.value;
      }
      if (mark.type === "link") {
        fillColor = theme.colors.primary;
        isLink = true;
      }
      if (mark.type === "code") {
        highlightColor = "rgba(0, 0, 0, 0.05)";
        ctx.font = `${fontSize * 0.9}px 'Consolas', 'Monaco', monospace`;
      }
    }

    // Draw highlight background
    if (highlightColor) {
      const textMetrics = ctx.measureText(text);
      ctx.fillStyle = highlightColor;
      ctx.fillRect(x - 2, y - fontSize * 0.8, textMetrics.width + 4, fontSize * 1.1);
    }

    // Draw text
    ctx.fillStyle = fillColor;
    ctx.fillText(text, x, y);

    // Draw decorations
    const textWidth = ctx.measureText(text).width;

    for (const mark of marks) {
      if (mark.type === "underline" || isLink) {
        ctx.strokeStyle = fillColor;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(x, y + 2);
        ctx.lineTo(x + textWidth, y + 2);
        ctx.stroke();
      }

      if (mark.type === "strikethrough") {
        ctx.strokeStyle = fillColor;
        ctx.lineWidth = 1;
        ctx.beginPath();
        const strikeY = y - fontSize * 0.3;
        ctx.moveTo(x, strikeY);
        ctx.lineTo(x + textWidth, strikeY);
        ctx.stroke();
      }
    }
  }

  private roundedRectPath(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    r = Math.min(r, w / 2, h / 2);
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Content manipulation API

  /**
   * Set plain text content (replaces all formatting)
   */
  setText(text: string): void {
    this.content = [{ text }];
    this._layoutDirty = true;
  }

  /**
   * Set content with formatting
   */
  setContent(content: TextSpan[]): void {
    this.content = content;
    this._layoutDirty = true;
  }

  /**
   * Append text with optional marks
   */
  appendText(text: string, marks?: TextMark[]): void {
    this.content.push({ text, marks });
    this._layoutDirty = true;
  }

  /**
   * Apply marks to a range of the plain text
   */
  applyMarks(startIndex: number, endIndex: number, marks: TextMark[]): void {
    const plainText = this.plainText;
    if (startIndex < 0 || endIndex > plainText.length || startIndex >= endIndex) {
      return;
    }

    // Rebuild content with the new marks applied
    const newContent: TextSpan[] = [];
    let currentIndex = 0;

    for (const span of this.content) {
      const spanStart = currentIndex;
      const spanEnd = currentIndex + span.text.length;

      if (spanEnd <= startIndex || spanStart >= endIndex) {
        // Span is completely outside the range
        newContent.push(span);
      } else {
        // Span overlaps with the range
        const overlapStart = Math.max(spanStart, startIndex);
        const overlapEnd = Math.min(spanEnd, endIndex);

        // Before overlap
        if (spanStart < overlapStart) {
          newContent.push({
            text: span.text.slice(0, overlapStart - spanStart),
            marks: span.marks,
          });
        }

        // The overlap (with merged marks)
        newContent.push({
          text: span.text.slice(overlapStart - spanStart, overlapEnd - spanStart),
          marks: [...(span.marks || []), ...marks],
        });

        // After overlap
        if (spanEnd > overlapEnd) {
          newContent.push({
            text: span.text.slice(overlapEnd - spanStart),
            marks: span.marks,
          });
        }
      }

      currentIndex = spanEnd;
    }

    this.content = newContent;
    this._layoutDirty = true;
  }

  /**
   * Remove marks from a range
   */
  removeMarks(startIndex: number, endIndex: number, markTypes: TextMark["type"][]): void {
    const plainText = this.plainText;
    if (startIndex < 0 || endIndex > plainText.length || startIndex >= endIndex) {
      return;
    }

    const newContent: TextSpan[] = [];
    let currentIndex = 0;

    for (const span of this.content) {
      const spanStart = currentIndex;
      const spanEnd = currentIndex + span.text.length;

      if (spanEnd <= startIndex || spanStart >= endIndex) {
        newContent.push(span);
      } else {
        const overlapStart = Math.max(spanStart, startIndex);
        const overlapEnd = Math.min(spanEnd, endIndex);

        if (spanStart < overlapStart) {
          newContent.push({
            text: span.text.slice(0, overlapStart - spanStart),
            marks: span.marks,
          });
        }

        // Remove specified marks from overlap
        const filteredMarks = (span.marks || []).filter(
          mark => !markTypes.includes(mark.type)
        );

        newContent.push({
          text: span.text.slice(overlapStart - spanStart, overlapEnd - spanStart),
          marks: filteredMarks.length > 0 ? filteredMarks : undefined,
        });

        if (spanEnd > overlapEnd) {
          newContent.push({
            text: span.text.slice(overlapEnd - spanStart),
            marks: span.marks,
          });
        }
      }

      currentIndex = spanEnd;
    }

    this.content = newContent;
    this._layoutDirty = true;
  }

  /**
   * Get marks at a specific index
   */
  getMarksAt(index: number): TextMark[] {
    let currentIndex = 0;

    for (const span of this.content) {
      const spanEnd = currentIndex + span.text.length;
      if (index >= currentIndex && index < spanEnd) {
        return span.marks || [];
      }
      currentIndex = spanEnd;
    }

    return [];
  }

  /**
   * Convert to HTML
   */
  toHtml(): string {
    let html = "";

    for (const span of this.content) {
      let text = this.escapeHtml(span.text);
      
      if (span.marks) {
        for (const mark of span.marks) {
          switch (mark.type) {
            case "bold":
              text = `<strong>${text}</strong>`;
              break;
            case "italic":
              text = `<em>${text}</em>`;
              break;
            case "underline":
              text = `<u>${text}</u>`;
              break;
            case "strikethrough":
              text = `<s>${text}</s>`;
              break;
            case "code":
              text = `<code>${text}</code>`;
              break;
            case "link":
              text = `<a href="${this.escapeHtml(mark.href)}">${text}</a>`;
              break;
            case "color":
              text = `<span style="color: ${mark.value}">${text}</span>`;
              break;
            case "highlight":
              text = `<mark style="background-color: ${mark.value}">${text}</mark>`;
              break;
          }
        }
      }

      html += text;
    }

    // Wrap in heading if needed
    if (this.headingLevel) {
      html = `<h${this.headingLevel}>${html}</h${this.headingLevel}>`;
    }

    return html;
  }

  /**
   * Convert to Markdown
   */
  toMarkdown(): string {
    let md = "";

    for (const span of this.content) {
      let text = span.text;
      
      if (span.marks) {
        for (const mark of span.marks) {
          switch (mark.type) {
            case "bold":
              text = `**${text}**`;
              break;
            case "italic":
              text = `*${text}*`;
              break;
            case "strikethrough":
              text = `~~${text}~~`;
              break;
            case "code":
              text = `\`${text}\``;
              break;
            case "link":
              text = `[${text}](${mark.href})`;
              break;
          }
        }
      }

      md += text;
    }

    // Add heading prefix if needed
    if (this.headingLevel) {
      md = "#".repeat(this.headingLevel) + " " + md;
    }

    return md;
  }

  /**
   * Parse from Markdown (basic support)
   */
  static fromMarkdown(markdown: string, props?: Partial<RichTextProps>): RichTextElement {
    // Basic markdown parsing
    const content: TextSpan[] = [];
    
    // Check for heading
    let headingLevel: HeadingLevel | null = null;
    const headingMatch = markdown.match(/^(#{1,6})\s+/);
    if (headingMatch) {
      headingLevel = headingMatch[1].length as HeadingLevel;
      markdown = markdown.slice(headingMatch[0].length);
    }

    // Very basic inline parsing (would need proper parser for production)
    // For now, just treat as plain text
    content.push({ text: markdown });

    return new RichTextElement({
      id: props?.id || `rich-text-${Date.now()}`,
      xywh: props?.xywh || JSON.stringify([0, 0, 300, 100]),
      index: props?.index || Date.now().toString(),
      content,
      headingLevel,
      ...props,
    });
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  /**
   * Force layout recalculation
   */
  invalidateLayout(): void {
    this._layoutDirty = true;
  }
}
