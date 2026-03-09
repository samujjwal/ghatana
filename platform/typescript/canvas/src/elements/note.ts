/**
 * Note Element - Document-like container on the canvas
 * 
 * @doc.type class
 * @doc.purpose Canvas element for document-style content blocks
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style note blocks for:
 * - Document-like content areas on canvas
 * - Rich text editing
 * - Nested blocks
 * - Page-like appearance
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type NoteDisplayMode = "edgeless" | "page";

export interface NoteChild {
  id: string;
  type: "paragraph" | "heading" | "list" | "code" | "quote" | "divider" | "image" | "embed";
  content?: string;
  props?: Record<string, unknown>;
}

export interface NoteProps extends BaseElementProps {
  /** Note title */
  title?: string;
  /** Background color */
  backgroundColor?: string;
  /** Border color */
  borderColor?: string;
  /** Border width */
  borderWidth?: number;
  /** Border radius */
  borderRadius?: number;
  /** Shadow */
  shadow?: {
    offsetX: number;
    offsetY: number;
    blur: number;
    spread?: number;
    color: string;
  };
  /** Display mode */
  displayMode?: NoteDisplayMode;
  /** Whether collapsed */
  collapsed?: boolean;
  /** Child content blocks */
  children?: NoteChild[];
  /** Whether to show header */
  showHeader?: boolean;
  /** Header background color */
  headerBackgroundColor?: string;
  /** Min width */
  minWidth?: number;
  /** Max width */
  maxWidth?: number;
  /** Opacity */
  opacity?: number;
  /** Hidden (for presentation) */
  hidden?: boolean;
  /** Editable state */
  editable?: boolean;
}

export class NoteElement extends CanvasElement {
  public title: string;
  public backgroundColor: string;
  public borderColor: string;
  public borderWidth: number;
  public borderRadius: number;
  public shadow?: NoteProps["shadow"];
  public displayMode: NoteDisplayMode;
  public collapsed: boolean;
  public children: NoteChild[];
  public showHeader: boolean;
  public headerBackgroundColor: string;
  public minWidth: number;
  public maxWidth: number;
  public opacity: number;
  public hidden: boolean;
  public editable: boolean;

  private static readonly HEADER_HEIGHT = 40;
  private static readonly CONTENT_PADDING = 24;
  private static readonly LINE_HEIGHT = 24;

  constructor(props: NoteProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.title = props.title || "";
    this.backgroundColor = props.backgroundColor || "#ffffff";
    this.borderColor = props.borderColor || theme.colors.border.light;
    this.borderWidth = props.borderWidth ?? 1;
    this.borderRadius = props.borderRadius ?? 8;
    this.shadow = props.shadow || {
      offsetX: 0,
      offsetY: 4,
      blur: 16,
      spread: -4,
      color: "rgba(0, 0, 0, 0.1)",
    };
    this.displayMode = props.displayMode || "edgeless";
    this.collapsed = props.collapsed ?? false;
    this.children = props.children || [];
    this.showHeader = props.showHeader ?? false;
    this.headerBackgroundColor = props.headerBackgroundColor || "rgba(0, 0, 0, 0.02)";
    this.minWidth = props.minWidth ?? 200;
    this.maxWidth = props.maxWidth ?? 800;
    this.opacity = props.opacity ?? 1;
    this.hidden = props.hidden ?? false;
    this.editable = props.editable ?? true;
  }

  get type(): CanvasElementType {
    return "note" as CanvasElementType;
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    if (this.hidden) return;

    ctx.save();
    this.applyTransform(ctx);
    ctx.globalAlpha = this.opacity;

    const bound = this.getBounds();
    const theme = themeManager.getTheme();

    // Draw shadow
    if (this.shadow) {
      this.drawShadow(ctx, bound);
    }

    // Draw background
    this.drawBackground(ctx, bound);

    // Draw border
    this.drawBorder(ctx, bound, zoom);

    // Draw header if enabled
    if (this.showHeader && !this.collapsed) {
      this.drawHeader(ctx, bound, zoom, theme);
    }

    // Draw content (only if not collapsed and zoom is sufficient)
    if (!this.collapsed && zoom > 0.2) {
      this.drawContent(ctx, bound, zoom, theme);
    }

    // Draw collapsed indicator
    if (this.collapsed) {
      this.drawCollapsedIndicator(ctx, bound, zoom, theme);
    }

    ctx.restore();
  }

  private drawShadow(ctx: CanvasRenderingContext2D, bound: Bound): void {
    if (!this.shadow) return;

    ctx.shadowColor = this.shadow.color;
    ctx.shadowBlur = this.shadow.blur;
    ctx.shadowOffsetX = this.shadow.offsetX;
    ctx.shadowOffsetY = this.shadow.offsetY;

    // Draw a rect to cast the shadow
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();

    // Reset shadow for other drawing
    ctx.shadowColor = "transparent";
    ctx.shadowBlur = 0;
    ctx.shadowOffsetX = 0;
    ctx.shadowOffsetY = 0;
  }

  private drawBackground(ctx: CanvasRenderingContext2D, bound: Bound): void {
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();
  }

  private drawBorder(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    if (this.borderWidth <= 0) return;

    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = this.borderWidth / zoom;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.stroke();
  }

  private drawHeader(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const headerHeight = NoteElement.HEADER_HEIGHT;

    // Draw header background
    ctx.fillStyle = this.headerBackgroundColor;
    ctx.beginPath();
    ctx.moveTo(bound.x + this.borderRadius, bound.y);
    ctx.arcTo(bound.x + bound.w, bound.y, bound.x + bound.w, bound.y + headerHeight, this.borderRadius);
    ctx.lineTo(bound.x + bound.w, bound.y + headerHeight);
    ctx.lineTo(bound.x, bound.y + headerHeight);
    ctx.arcTo(bound.x, bound.y, bound.x + this.borderRadius, bound.y, this.borderRadius);
    ctx.closePath();
    ctx.fill();

    // Draw header border
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1 / zoom;
    ctx.beginPath();
    ctx.moveTo(bound.x, bound.y + headerHeight);
    ctx.lineTo(bound.x + bound.w, bound.y + headerHeight);
    ctx.stroke();

    // Draw title
    if (this.title && zoom > 0.3) {
      ctx.fillStyle = theme.colors.text.primary;
      ctx.font = `bold ${14}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "left";
      ctx.textBaseline = "middle";

      const maxTitleWidth = bound.w - NoteElement.CONTENT_PADDING * 2;
      const displayTitle = this.truncateText(ctx, this.title, maxTitleWidth);
      ctx.fillText(displayTitle, bound.x + NoteElement.CONTENT_PADDING, bound.y + headerHeight / 2);
    }
  }

  private drawContent(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const padding = NoteElement.CONTENT_PADDING;
    const lineHeight = NoteElement.LINE_HEIGHT;
    const startY = this.showHeader 
      ? bound.y + NoteElement.HEADER_HEIGHT + padding 
      : bound.y + padding;
    const contentWidth = bound.w - padding * 2;

    let currentY = startY;

    // Draw simplified content representation at low zoom
    if (zoom < 0.4) {
      this.drawSimplifiedContent(ctx, bound, theme, startY, contentWidth);
      return;
    }

    // Draw children
    for (const child of this.children) {
      if (currentY > bound.y + bound.h - padding) break;

      switch (child.type) {
        case "paragraph":
          currentY = this.drawParagraph(ctx, child, bound.x + padding, currentY, contentWidth, lineHeight, theme);
          break;
        case "heading":
          currentY = this.drawHeading(ctx, child, bound.x + padding, currentY, contentWidth, theme);
          break;
        case "list":
          currentY = this.drawList(ctx, child, bound.x + padding, currentY, contentWidth, lineHeight, theme);
          break;
        case "code":
          currentY = this.drawCodeBlock(ctx, child, bound.x + padding, currentY, contentWidth, theme);
          break;
        case "quote":
          currentY = this.drawQuote(ctx, child, bound.x + padding, currentY, contentWidth, lineHeight, theme);
          break;
        case "divider":
          currentY = this.drawDivider(ctx, bound.x + padding, currentY, contentWidth, theme);
          break;
        default:
          currentY += lineHeight;
      }

      currentY += 8; // Gap between blocks
    }
  }

  private drawSimplifiedContent(ctx: CanvasRenderingContext2D, bound: Bound, theme: YAPPCTheme, startY: number, contentWidth: number): void {
    // Draw lines to represent content
    ctx.fillStyle = theme.colors.text.muted || "rgba(0, 0, 0, 0.1)";
    
    const lineHeight = 16;
    const maxLines = Math.floor((bound.h - startY + bound.y - NoteElement.CONTENT_PADDING) / lineHeight);
    
    for (let i = 0; i < Math.min(maxLines, this.children.length * 2); i++) {
      const y = startY + i * lineHeight;
      const width = contentWidth * (0.5 + Math.random() * 0.4);
      ctx.fillRect(bound.x + NoteElement.CONTENT_PADDING, y, width, 8);
    }
  }

  private drawParagraph(
    ctx: CanvasRenderingContext2D,
    child: NoteChild,
    x: number,
    y: number,
    maxWidth: number,
    lineHeight: number,
    theme: YAPPCTheme
  ): number {
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `${14}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const text = child.content || "";
    const lines = this.wrapText(ctx, text, maxWidth);

    for (const line of lines) {
      ctx.fillText(line, x, y);
      y += lineHeight;
    }

    return y;
  }

  private drawHeading(
    ctx: CanvasRenderingContext2D,
    child: NoteChild,
    x: number,
    y: number,
    maxWidth: number,
    theme: YAPPCTheme
  ): number {
    const level = (child.props?.level as number) || 2;
    const sizes: Record<number, number> = { 1: 24, 2: 20, 3: 16, 4: 14, 5: 13, 6: 12 };
    const fontSize = sizes[level] || 16;

    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `bold ${fontSize}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const text = child.content || "";
    const displayText = this.truncateText(ctx, text, maxWidth);
    ctx.fillText(displayText, x, y);

    return y + fontSize * 1.5;
  }

  private drawList(
    ctx: CanvasRenderingContext2D,
    child: NoteChild,
    x: number,
    y: number,
    maxWidth: number,
    lineHeight: number,
    theme: YAPPCTheme
  ): number {
    const items = (child.props?.items as string[]) || [child.content];
    const ordered = child.props?.ordered || false;

    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `${14}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    items.forEach((item: string, index: number) => {
      const bullet = ordered ? `${index + 1}.` : "•";
      const bulletWidth = ctx.measureText(bullet + " ").width;

      ctx.fillText(bullet, x, y);
      ctx.fillText(item, x + bulletWidth, y);
      y += lineHeight;
    });

    return y;
  }

  private drawCodeBlock(
    ctx: CanvasRenderingContext2D,
    child: NoteChild,
    x: number,
    y: number,
    maxWidth: number,
    theme: YAPPCTheme
  ): number {
    const code = child.content || "";
    const lines = code.split("\n");
    const padding = 12;
    const lineHeight = 20;
    const blockHeight = lines.length * lineHeight + padding * 2;

    // Draw background
    ctx.fillStyle = "rgba(0, 0, 0, 0.03)";
    ctx.fillRect(x, y, maxWidth, blockHeight);

    // Draw border
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1;
    ctx.strokeRect(x, y, maxWidth, blockHeight);

    // Draw code
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `13px 'Consolas', 'Monaco', monospace`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    let codeY = y + padding;
    for (const line of lines) {
      ctx.fillText(line, x + padding, codeY);
      codeY += lineHeight;
    }

    return y + blockHeight;
  }

  private drawQuote(
    ctx: CanvasRenderingContext2D,
    child: NoteChild,
    x: number,
    y: number,
    maxWidth: number,
    lineHeight: number,
    theme: YAPPCTheme
  ): number {
    const padding = 16;
    const borderWidth = 3;

    // Draw left border
    ctx.fillStyle = theme.colors.primary;
    ctx.fillRect(x, y, borderWidth, lineHeight * 1.5);

    // Draw text
    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `italic ${14}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const text = child.content || "";
    const displayText = this.truncateText(ctx, text, maxWidth - padding - borderWidth);
    ctx.fillText(displayText, x + borderWidth + padding, y);

    return y + lineHeight * 1.5;
  }

  private drawDivider(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    theme: YAPPCTheme
  ): number {
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x, y + 12);
    ctx.lineTo(x + width, y + 12);
    ctx.stroke();

    return y + 24;
  }

  private drawCollapsedIndicator(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    if (zoom < 0.3) return;

    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `${14}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("...", bound.x + bound.w / 2, bound.y + bound.h / 2);
  }

  private drawRoundedRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    r = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  private truncateText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
    if (ctx.measureText(text).width <= maxWidth) {
      return text;
    }

    let truncated = text;
    while (ctx.measureText(truncated + "...").width > maxWidth && truncated.length > 0) {
      truncated = truncated.slice(0, -1);
    }
    return truncated + "...";
  }

  private wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
    const words = text.split(" ");
    const lines: string[] = [];
    let currentLine = "";

    for (const word of words) {
      const testLine = currentLine ? `${currentLine} ${word}` : word;
      if (ctx.measureText(testLine).width > maxWidth && currentLine) {
        lines.push(currentLine);
        currentLine = word;
      } else {
        currentLine = testLine;
      }
    }

    if (currentLine) {
      lines.push(currentLine);
    }

    return lines;
  }

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    if (this.hidden) return false;
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Public API

  /**
   * Add a child block
   */
  addChild(child: NoteChild): void {
    this.children.push(child);
  }

  /**
   * Remove a child block
   */
  removeChild(childId: string): void {
    this.children = this.children.filter(c => c.id !== childId);
  }

  /**
   * Move child to a new position
   */
  moveChild(childId: string, newIndex: number): void {
    const index = this.children.findIndex(c => c.id === childId);
    if (index === -1) return;

    const [child] = this.children.splice(index, 1);
    this.children.splice(newIndex, 0, child);
  }

  /**
   * Toggle collapsed state
   */
  toggleCollapsed(): void {
    this.collapsed = !this.collapsed;
  }

  /**
   * Set title
   */
  setTitle(title: string): void {
    this.title = title;
  }

  /**
   * Get content as plain text
   */
  getPlainText(): string {
    return this.children
      .filter(c => c.content)
      .map(c => c.content)
      .join("\n\n");
  }

  /**
   * Get content as Markdown
   */
  toMarkdown(): string {
    const lines: string[] = [];

    if (this.title) {
      lines.push(`# ${this.title}`, "");
    }

    for (const child of this.children) {
      switch (child.type) {
        case "paragraph":
          lines.push(child.content || "", "");
          break;
        case "heading":
          const level = (child.props?.level as number) || 2;
          lines.push("#".repeat(level) + " " + (child.content || ""), "");
          break;
        case "list":
          const items = (child.props?.items as string[]) || [child.content];
          const ordered = child.props?.ordered || false;
          items.forEach((item: string, i: number) => {
            lines.push(ordered ? `${i + 1}. ${item}` : `- ${item}`);
          });
          lines.push("");
          break;
        case "code":
          const lang = child.props?.language || "";
          lines.push("```" + lang, child.content || "", "```", "");
          break;
        case "quote":
          lines.push(`> ${child.content || ""}`, "");
          break;
        case "divider":
          lines.push("---", "");
          break;
      }
    }

    return lines.join("\n");
  }

  /**
   * Set content from Markdown (basic)
   */
  setFromMarkdown(markdown: string): void {
    this.children = [];
    const lines = markdown.split("\n");

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Skip empty lines
      if (!line.trim()) continue;

      // Heading
      const headingMatch = line.match(/^(#{1,6})\s+(.+)/);
      if (headingMatch) {
        this.children.push({
          id: `child-${i}`,
          type: "heading",
          content: headingMatch[2],
          props: { level: headingMatch[1].length },
        });
        continue;
      }

      // Quote
      if (line.startsWith("> ")) {
        this.children.push({
          id: `child-${i}`,
          type: "quote",
          content: line.slice(2),
        });
        continue;
      }

      // Divider
      if (line.match(/^-{3,}$/)) {
        this.children.push({
          id: `child-${i}`,
          type: "divider",
        });
        continue;
      }

      // List item
      const listMatch = line.match(/^(\d+\.|-|\*)\s+(.+)/);
      if (listMatch) {
        // Collect all list items
        const items: string[] = [listMatch[2]];
        const ordered = /^\d+\./.test(listMatch[1]);
        
        while (i + 1 < lines.length) {
          const nextLine = lines[i + 1];
          const nextMatch = nextLine.match(/^(\d+\.|-|\*)\s+(.+)/);
          if (nextMatch) {
            items.push(nextMatch[2]);
            i++;
          } else {
            break;
          }
        }

        this.children.push({
          id: `child-${i}`,
          type: "list",
          props: { items, ordered },
        });
        continue;
      }

      // Code block
      if (line.startsWith("```")) {
        const language = line.slice(3);
        const codeLines: string[] = [];
        i++;
        
        while (i < lines.length && !lines[i].startsWith("```")) {
          codeLines.push(lines[i]);
          i++;
        }

        this.children.push({
          id: `child-${i}`,
          type: "code",
          content: codeLines.join("\n"),
          props: { language },
        });
        continue;
      }

      // Regular paragraph
      this.children.push({
        id: `child-${i}`,
        type: "paragraph",
        content: line,
      });
    }
  }
}
