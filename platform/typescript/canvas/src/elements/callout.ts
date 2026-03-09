/**
 * Callout Element - Information blocks with icons
 * 
 * @doc.type class
 * @doc.purpose Canvas element for callout boxes (info, warning, etc.)
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style callout blocks for:
 * - Information blocks
 * - Warning/error messages
 * - Tips and notes
 * - Custom icon callouts
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type CalloutType = "info" | "warning" | "error" | "success" | "tip" | "note" | "custom";

export interface CalloutProps extends BaseElementProps {
  /** Callout type */
  calloutType: CalloutType;
  /** Content text */
  content: string;
  /** Optional title */
  title?: string;
  /** Custom icon (emoji or symbol) */
  icon?: string;
  /** Custom background color */
  backgroundColor?: string;
  /** Custom border color */
  borderColor?: string;
  /** Custom text color */
  textColor?: string;
  /** Custom icon color */
  iconColor?: string;
  /** Border radius */
  borderRadius?: number;
  /** Whether callout is collapsible */
  collapsible?: boolean;
  /** Whether callout is collapsed */
  collapsed?: boolean;
  /** Font size */
  fontSize?: number;
}

// Default configurations for callout types
const CALLOUT_CONFIGS: Record<CalloutType, {
  icon: string;
  backgroundColor: string;
  borderColor: string;
  iconColor: string;
}> = {
  info: {
    icon: "ℹ️",
    backgroundColor: "rgba(59, 130, 246, 0.1)",
    borderColor: "rgba(59, 130, 246, 0.3)",
    iconColor: "#3b82f6",
  },
  warning: {
    icon: "⚠️",
    backgroundColor: "rgba(245, 158, 11, 0.1)",
    borderColor: "rgba(245, 158, 11, 0.3)",
    iconColor: "#f59e0b",
  },
  error: {
    icon: "❌",
    backgroundColor: "rgba(239, 68, 68, 0.1)",
    borderColor: "rgba(239, 68, 68, 0.3)",
    iconColor: "#ef4444",
  },
  success: {
    icon: "✅",
    backgroundColor: "rgba(34, 197, 94, 0.1)",
    borderColor: "rgba(34, 197, 94, 0.3)",
    iconColor: "#22c55e",
  },
  tip: {
    icon: "💡",
    backgroundColor: "rgba(168, 85, 247, 0.1)",
    borderColor: "rgba(168, 85, 247, 0.3)",
    iconColor: "#a855f7",
  },
  note: {
    icon: "📝",
    backgroundColor: "rgba(107, 114, 128, 0.1)",
    borderColor: "rgba(107, 114, 128, 0.3)",
    iconColor: "#6b7280",
  },
  custom: {
    icon: "📌",
    backgroundColor: "rgba(99, 102, 241, 0.1)",
    borderColor: "rgba(99, 102, 241, 0.3)",
    iconColor: "#6366f1",
  },
};

export class CalloutElement extends CanvasElement {
  public calloutType: CalloutType;
  public content: string;
  public title: string;
  public icon: string;
  public backgroundColor: string;
  public borderColor: string;
  public textColor: string;
  public iconColor: string;
  public borderRadius: number;
  public collapsible: boolean;
  public collapsed: boolean;
  public fontSize: number;

  private static readonly ICON_SIZE = 24;
  private static readonly PADDING = 16;
  private static readonly TITLE_FONT_SIZE = 15;
  private static readonly COLLAPSED_HEIGHT = 48;

  constructor(props: CalloutProps) {
    super(props);
    const theme = themeManager.getTheme();
    const config = CALLOUT_CONFIGS[props.calloutType];

    this.calloutType = props.calloutType;
    this.content = props.content;
    this.title = props.title || "";
    this.icon = props.icon || config.icon;
    this.backgroundColor = props.backgroundColor || config.backgroundColor;
    this.borderColor = props.borderColor || config.borderColor;
    this.textColor = props.textColor || theme.colors.text.primary;
    this.iconColor = props.iconColor || config.iconColor;
    this.borderRadius = props.borderRadius ?? 8;
    this.collapsible = props.collapsible ?? false;
    this.collapsed = props.collapsed ?? false;
    this.fontSize = props.fontSize ?? 14;
  }

  get type(): CanvasElementType {
    return "callout" as CanvasElementType;
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();
    const theme = themeManager.getTheme();

    // Draw background
    this.drawBackground(ctx, bound);

    // Draw border
    this.drawBorder(ctx, bound, zoom);

    // Simplified rendering at low zoom
    if (zoom < 0.3) {
      this.renderSimplified(ctx, bound);
      ctx.restore();
      return;
    }

    // Draw icon
    this.drawIcon(ctx, bound, zoom);

    // Draw content
    if (!this.collapsed) {
      this.drawContent(ctx, bound, zoom, theme);
    } else if (this.collapsible) {
      this.drawCollapsedContent(ctx, bound, zoom, theme);
    }

    // Draw collapse/expand indicator
    if (this.collapsible && zoom > 0.4) {
      this.drawCollapseIndicator(ctx, bound, zoom, theme);
    }

    ctx.restore();
  }

  private drawBackground(ctx: CanvasRenderingContext2D, bound: Bound): void {
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();
  }

  private drawBorder(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 2 / zoom;
    
    // Draw left accent border
    ctx.beginPath();
    ctx.moveTo(bound.x + this.borderRadius, bound.y);
    ctx.arcTo(bound.x, bound.y, bound.x, bound.y + this.borderRadius, this.borderRadius);
    ctx.lineTo(bound.x, bound.y + bound.h - this.borderRadius);
    ctx.arcTo(bound.x, bound.y + bound.h, bound.x + this.borderRadius, bound.y + bound.h, this.borderRadius);
    ctx.stroke();

    // Draw full border (lighter)
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1 / zoom;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.stroke();
  }

  private renderSimplified(ctx: CanvasRenderingContext2D, bound: Bound): void {
    // Draw a simple colored bar to represent the callout
    ctx.fillStyle = this.iconColor;
    ctx.fillRect(bound.x, bound.y, 4, bound.h);
  }

  private drawIcon(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    const padding = CalloutElement.PADDING;
    const iconSize = CalloutElement.ICON_SIZE;
    
    ctx.font = `${iconSize}px Arial`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(this.icon, bound.x + padding + iconSize / 2, bound.y + padding + iconSize / 2);
  }

  private drawContent(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const padding = CalloutElement.PADDING;
    const iconSize = CalloutElement.ICON_SIZE;
    const textX = bound.x + padding + iconSize + padding;
    const textWidth = bound.w - padding * 3 - iconSize - (this.collapsible ? 24 : 0);

    let currentY = bound.y + padding;

    // Draw title if present
    if (this.title) {
      ctx.fillStyle = this.textColor;
      ctx.font = `bold ${CalloutElement.TITLE_FONT_SIZE}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "left";
      ctx.textBaseline = "top";
      
      const displayTitle = this.truncateText(ctx, this.title, textWidth);
      ctx.fillText(displayTitle, textX, currentY);
      currentY += CalloutElement.TITLE_FONT_SIZE * 1.5;
    }

    // Draw content
    ctx.fillStyle = this.textColor;
    ctx.font = `${this.fontSize}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const lines = this.wrapText(ctx, this.content, textWidth);
    const lineHeight = this.fontSize * 1.5;

    for (const line of lines) {
      if (currentY + lineHeight > bound.y + bound.h - padding) break;
      ctx.fillText(line, textX, currentY);
      currentY += lineHeight;
    }
  }

  private drawCollapsedContent(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const padding = CalloutElement.PADDING;
    const iconSize = CalloutElement.ICON_SIZE;
    const textX = bound.x + padding + iconSize + padding;
    const textWidth = bound.w - padding * 3 - iconSize - 24;

    ctx.fillStyle = this.textColor;
    ctx.font = this.title 
      ? `bold ${CalloutElement.TITLE_FONT_SIZE}px ${theme.typography.fontFamily}`
      : `${this.fontSize}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";

    const displayText = this.title || this.content;
    const truncated = this.truncateText(ctx, displayText, textWidth);
    ctx.fillText(truncated, textX, bound.y + bound.h / 2);
  }

  private drawCollapseIndicator(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const x = bound.x + bound.w - CalloutElement.PADDING - 8;
    const y = bound.y + CalloutElement.PADDING + 8;

    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `${12}px Arial`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(this.collapsed ? "▼" : "▲", x, y);
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
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Public API

  /**
   * Toggle collapsed state
   */
  toggleCollapsed(): void {
    if (this.collapsible) {
      this.collapsed = !this.collapsed;
    }
  }

  /**
   * Set callout type (updates default colors)
   */
  setType(type: CalloutType): void {
    const config = CALLOUT_CONFIGS[type];
    this.calloutType = type;
    this.icon = config.icon;
    this.backgroundColor = config.backgroundColor;
    this.borderColor = config.borderColor;
    this.iconColor = config.iconColor;
  }

  /**
   * Set content
   */
  setContent(content: string): void {
    this.content = content;
  }

  /**
   * Set title
   */
  setTitle(title: string): void {
    this.title = title;
  }

  /**
   * Set custom icon
   */
  setIcon(icon: string): void {
    this.icon = icon;
  }

  /**
   * Calculate required height for content
   */
  calculateRequiredHeight(ctx: CanvasRenderingContext2D): number {
    if (this.collapsed) {
      return CalloutElement.COLLAPSED_HEIGHT;
    }

    const padding = CalloutElement.PADDING;
    const iconSize = CalloutElement.ICON_SIZE;
    const bound = this.getBounds();
    const textWidth = bound.w - padding * 3 - iconSize - (this.collapsible ? 24 : 0);

    let height = padding * 2;

    // Title height
    if (this.title) {
      height += CalloutElement.TITLE_FONT_SIZE * 1.5;
    }

    // Content height
    const theme = themeManager.getTheme();
    ctx.font = `${this.fontSize}px ${theme.typography.fontFamily}`;
    const lines = this.wrapText(ctx, this.content, textWidth);
    height += lines.length * this.fontSize * 1.5;

    return Math.max(height, iconSize + padding * 2);
  }

  /**
   * Create callout from Markdown admonition syntax
   */
  static fromMarkdown(markdown: string, props?: Partial<CalloutProps>): CalloutElement | null {
    // Parse admonition syntax: > [!TYPE] Title
    // > Content
    const match = markdown.match(/^>\s*\[!([\w-]+)\]\s*(.*)?\n?([\s\S]*)?$/);
    if (!match) return null;

    const typeMap: Record<string, CalloutType> = {
      "info": "info",
      "note": "note",
      "tip": "tip",
      "warning": "warning",
      "caution": "warning",
      "danger": "error",
      "error": "error",
      "success": "success",
      "check": "success",
    };

    const calloutType = typeMap[match[1].toLowerCase()] || "custom";
    const title = match[2]?.trim() || "";
    const content = match[3]?.replace(/^>\s*/gm, "").trim() || "";

    return new CalloutElement({
      id: props?.id || `callout-${Date.now()}`,
      xywh: props?.xywh || JSON.stringify([0, 0, 400, 120]),
      index: props?.index || Date.now().toString(),
      calloutType,
      title,
      content,
      ...props,
    });
  }

  /**
   * Export to Markdown admonition syntax
   */
  toMarkdown(): string {
    const typeNames: Record<CalloutType, string> = {
      info: "INFO",
      warning: "WARNING",
      error: "DANGER",
      success: "SUCCESS",
      tip: "TIP",
      note: "NOTE",
      custom: "NOTE",
    };

    const lines = [`> [!${typeNames[this.calloutType]}] ${this.title}`];
    
    const contentLines = this.content.split("\n");
    for (const line of contentLines) {
      lines.push(`> ${line}`);
    }

    return lines.join("\n");
  }
}
