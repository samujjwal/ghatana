/**
 * Divider Canvas Element
 * 
 * @doc.type class
 * @doc.purpose Renders horizontal/vertical dividers and separators on canvas
 * @doc.layer core
 * @doc.pattern ValueObject
 * 
 * Features:
 * - Horizontal and vertical orientation
 * - Multiple line styles (solid, dashed, dotted, double)
 * - Custom colors and thickness
 * - Optional decorations (diamond, circle, text)
 * - Semantic zoom support
 */

import { CanvasElement } from "./base.js";
import type { BaseElementProps, CanvasElementType } from "../types/index.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

/**
 * Divider orientation
 */
export type DividerOrientation = "horizontal" | "vertical";

/**
 * Divider line style
 */
export type DividerLineStyle = "solid" | "dashed" | "dotted" | "double" | "gradient";

/**
 * Divider decoration type
 */
export type DividerDecoration = "none" | "diamond" | "circle" | "star" | "text" | "icon";

/**
 * Divider element properties
 */
export interface DividerElementProps extends BaseElementProps {
  /** Divider orientation */
  orientation?: DividerOrientation;
  /** Line style */
  lineStyle?: DividerLineStyle;
  /** Line color */
  color?: string;
  /** Secondary color (for gradient) */
  secondaryColor?: string;
  /** Line thickness */
  thickness?: number;
  /** Dash length (for dashed style) */
  dashLength?: number;
  /** Gap length (for dashed style) */
  dashGap?: number;
  /** Dot spacing (for dotted style) */
  dotSpacing?: number;
  /** Decoration type */
  decoration?: DividerDecoration;
  /** Decoration text (when decoration is 'text') */
  decorationText?: string;
  /** Decoration icon (when decoration is 'icon') */
  decorationIcon?: string;
  /** Decoration color */
  decorationColor?: string;
  /** Decoration size */
  decorationSize?: number;
  /** Decoration position (0-1, where 0.5 is center) */
  decorationPosition?: number;
  /** Line cap style */
  lineCap?: CanvasLineCap;
  /** Opacity */
  opacity?: number;
  /** Margin from edges */
  margin?: number;
}

/**
 * Divider Canvas Element
 */
export class DividerElement extends CanvasElement {
  orientation: DividerOrientation;
  lineStyle: DividerLineStyle;
  color: string;
  secondaryColor: string;
  thickness: number;
  dashLength: number;
  dashGap: number;
  dotSpacing: number;
  decoration: DividerDecoration;
  decorationText: string;
  decorationIcon: string;
  decorationColor: string;
  decorationSize: number;
  decorationPosition: number;
  lineCap: CanvasLineCap;
  opacity: number;
  margin: number;

  constructor(props: DividerElementProps) {
    super(props);
    this.orientation = props.orientation || "horizontal";
    this.lineStyle = props.lineStyle || "solid";
    this.color = props.color || themeManager.getTheme().colors.border.light;
    this.secondaryColor = props.secondaryColor || "transparent";
    this.thickness = props.thickness || 1;
    this.dashLength = props.dashLength || 8;
    this.dashGap = props.dashGap || 4;
    this.dotSpacing = props.dotSpacing || 4;
    this.decoration = props.decoration || "none";
    this.decorationText = props.decorationText || "";
    this.decorationIcon = props.decorationIcon || "★";
    this.decorationColor = props.decorationColor || this.color;
    this.decorationSize = props.decorationSize || 16;
    this.decorationPosition = props.decorationPosition || 0.5;
    this.lineCap = props.lineCap || "round";
    this.opacity = props.opacity || 1;
    this.margin = props.margin || 0;
  }

  get type(): CanvasElementType {
    return "divider";
  }

  render(ctx: CanvasRenderingContext2D, _viewport: unknown): void {
    const bounds = this.getBounds();
    const { x, y, w, h } = bounds;

    ctx.save();
    ctx.globalAlpha = this.opacity;

    const isHorizontal = this.orientation === "horizontal";
    
    // Calculate line positions with margin
    const startX = x + (isHorizontal ? this.margin : w / 2);
    const startY = y + (isHorizontal ? h / 2 : this.margin);
    const endX = x + (isHorizontal ? w - this.margin : w / 2);
    const endY = y + (isHorizontal ? h / 2 : h - this.margin);

    // Calculate decoration position
    const decorationX = startX + (endX - startX) * this.decorationPosition;
    const decorationY = startY + (endY - startY) * this.decorationPosition;

    // Set line style
    ctx.strokeStyle = this.color;
    ctx.lineWidth = this.thickness;
    ctx.lineCap = this.lineCap;

    // Apply line style
    switch (this.lineStyle) {
      case "dashed":
        ctx.setLineDash([this.dashLength, this.dashGap]);
        break;
      case "dotted":
        ctx.setLineDash([this.thickness, this.dotSpacing]);
        ctx.lineCap = "round";
        break;
      case "double":
        // Draw double line
        this.drawDoubleLine(ctx, startX, startY, endX, endY, isHorizontal);
        ctx.restore();
        return;
      case "gradient":
        this.drawGradientLine(ctx, startX, startY, endX, endY);
        ctx.restore();
        return;
      default:
        ctx.setLineDash([]);
    }

    // Draw main line (with gap for decoration if needed)
    if (this.decoration !== "none") {
      const gap = this.decorationSize + 8;
      
      // Line before decoration
      ctx.beginPath();
      if (isHorizontal) {
        ctx.moveTo(startX, startY);
        ctx.lineTo(decorationX - gap / 2, decorationY);
      } else {
        ctx.moveTo(startX, startY);
        ctx.lineTo(decorationX, decorationY - gap / 2);
      }
      ctx.stroke();

      // Line after decoration
      ctx.beginPath();
      if (isHorizontal) {
        ctx.moveTo(decorationX + gap / 2, decorationY);
        ctx.lineTo(endX, endY);
      } else {
        ctx.moveTo(decorationX, decorationY + gap / 2);
        ctx.lineTo(endX, endY);
      }
      ctx.stroke();

      // Draw decoration
      this.drawDecoration(ctx, decorationX, decorationY);
    } else {
      // Simple line
      ctx.beginPath();
      ctx.moveTo(startX, startY);
      ctx.lineTo(endX, endY);
      ctx.stroke();
    }

    ctx.restore();
  }

  /**
   * Draw double line style
   */
  private drawDoubleLine(
    ctx: CanvasRenderingContext2D,
    startX: number,
    startY: number,
    endX: number,
    endY: number,
    isHorizontal: boolean
  ): void {
    const offset = this.thickness + 1;
    
    ctx.beginPath();
    if (isHorizontal) {
      ctx.moveTo(startX, startY - offset);
      ctx.lineTo(endX, endY - offset);
      ctx.moveTo(startX, startY + offset);
      ctx.lineTo(endX, endY + offset);
    } else {
      ctx.moveTo(startX - offset, startY);
      ctx.lineTo(endX - offset, endY);
      ctx.moveTo(startX + offset, startY);
      ctx.lineTo(endX + offset, endY);
    }
    ctx.stroke();
  }

  /**
   * Draw gradient line style
   */
  private drawGradientLine(
    ctx: CanvasRenderingContext2D,
    startX: number,
    startY: number,
    endX: number,
    endY: number
  ): void {
    const gradient = ctx.createLinearGradient(startX, startY, endX, endY);
    gradient.addColorStop(0, "transparent");
    gradient.addColorStop(0.2, this.color);
    gradient.addColorStop(0.8, this.secondaryColor || this.color);
    gradient.addColorStop(1, "transparent");

    ctx.strokeStyle = gradient;
    ctx.beginPath();
    ctx.moveTo(startX, startY);
    ctx.lineTo(endX, endY);
    ctx.stroke();
  }

  /**
   * Draw decoration at position
   */
  private drawDecoration(ctx: CanvasRenderingContext2D, x: number, y: number): void {
    ctx.fillStyle = this.decorationColor;
    
    switch (this.decoration) {
      case "diamond":
        this.drawDiamond(ctx, x, y, this.decorationSize);
        break;
      
      case "circle":
        ctx.beginPath();
        ctx.arc(x, y, this.decorationSize / 2, 0, Math.PI * 2);
        ctx.fill();
        break;
      
      case "star":
        this.drawStar(ctx, x, y, this.decorationSize / 2);
        break;
      
      case "text":
        ctx.font = `${this.decorationSize}px Inter, -apple-system, sans-serif`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(this.decorationText, x, y);
        break;
      
      case "icon":
        ctx.font = `${this.decorationSize}px serif`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(this.decorationIcon, x, y);
        break;
    }
  }

  /**
   * Draw diamond shape
   */
  private drawDiamond(ctx: CanvasRenderingContext2D, x: number, y: number, size: number): void {
    const half = size / 2;
    ctx.beginPath();
    ctx.moveTo(x, y - half);
    ctx.lineTo(x + half, y);
    ctx.lineTo(x, y + half);
    ctx.lineTo(x - half, y);
    ctx.closePath();
    ctx.fill();
  }

  /**
   * Draw star shape
   */
  private drawStar(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number): void {
    const points = 5;
    const innerRadius = radius * 0.5;
    
    ctx.beginPath();
    for (let i = 0; i < points * 2; i++) {
      const r = i % 2 === 0 ? radius : innerRadius;
      const angle = (Math.PI / points) * i - Math.PI / 2;
      const px = x + Math.cos(angle) * r;
      const py = y + Math.sin(angle) * r;
      
      if (i === 0) {
        ctx.moveTo(px, py);
      } else {
        ctx.lineTo(px, py);
      }
    }
    ctx.closePath();
    ctx.fill();
  }

  includesPoint(px: number, py: number): boolean {
    const bounds = this.getBounds();
    const isHorizontal = this.orientation === "horizontal";
    
    // Expand hit area for easier selection
    const hitPadding = Math.max(8, this.thickness * 2);
    
    if (isHorizontal) {
      const lineY = bounds.y + bounds.h / 2;
      return (
        px >= bounds.x &&
        px <= bounds.x + bounds.w &&
        py >= lineY - hitPadding &&
        py <= lineY + hitPadding
      );
    } else {
      const lineX = bounds.x + bounds.w / 2;
      return (
        py >= bounds.y &&
        py <= bounds.y + bounds.h &&
        px >= lineX - hitPadding &&
        px <= lineX + hitPadding
      );
    }
  }

  /**
   * Convert to Markdown (horizontal rule)
   */
  toMarkdown(): string {
    if (this.decoration === "text" && this.decorationText) {
      return `\n--- ${this.decorationText} ---\n`;
    }
    return "\n---\n";
  }

  /**
   * Create common divider presets
   */
  static createPreset(
    preset: "simple" | "dashed" | "dotted" | "double" | "fancy" | "gradient",
    baseProps: BaseElementProps
  ): DividerElement {
    const presets: Record<string, Partial<DividerElementProps>> = {
      simple: {
        lineStyle: "solid",
        thickness: 1,
      },
      dashed: {
        lineStyle: "dashed",
        thickness: 1,
        dashLength: 8,
        dashGap: 4,
      },
      dotted: {
        lineStyle: "dotted",
        thickness: 2,
        dotSpacing: 6,
      },
      double: {
        lineStyle: "double",
        thickness: 1,
      },
      fancy: {
        lineStyle: "solid",
        thickness: 1,
        decoration: "diamond",
        decorationSize: 12,
      },
      gradient: {
        lineStyle: "gradient",
        thickness: 2,
        color: themeManager.getTheme().colors.primary,
        secondaryColor: themeManager.getTheme().colors.secondary,
      },
    };

    return new DividerElement({
      ...baseProps,
      ...presets[preset],
    });
  }
}
