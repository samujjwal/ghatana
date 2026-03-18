import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

export interface GroupProps extends BaseElementProps {
  title?: string;
  backgroundColor?: string;
  borderColor?: string;
  borderWidth?: number;
  borderRadius?: number;
  collapsed?: boolean;
}

export class GroupElement extends CanvasElement {
  public title?: string;
  public backgroundColor: string;
  public borderColor: string;
  public borderWidth: number;
  public borderRadius: number;
  public collapsed: boolean;
  public childElements: CanvasElement[] = [];

  constructor(props: GroupProps) {
    super(props);
    this.title = props.title;
    this.backgroundColor =
      props.backgroundColor || themeManager.getColor("colors.surface");
    this.borderColor =
      props.borderColor || themeManager.getColor("colors.border.medium");
    this.borderWidth = props.borderWidth || 2;
    this.borderRadius =
      props.borderRadius || themeManager.getBorderRadius("medium");
    this.collapsed = props.collapsed || false;
  }

  get type(): CanvasElementType {
    return "group";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    // Draw group background
    this.drawBackground(ctx, bound);

    // Draw border
    this.drawBorder(ctx, bound, zoom);

    // Semantic zoom: hide titles if too small
    const showTitle = this.title && !this.collapsed && zoom > 0.4;

    // Draw title if present
    if (showTitle) {
      this.drawTitle(ctx, bound);
    }

    // Draw collapse/expand indicator
    this.drawCollapseIndicator(ctx, bound);

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
    ctx.fillStyle = this.backgroundColor;

    if (this.borderRadius > 0) {
      this.drawRoundedRect(
        ctx,
        bound.x,
        bound.y,
        bound.w,
        bound.h,
        this.borderRadius,
      );
      ctx.fill();
    } else {
      ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
    }
  }

  private drawBorder(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
    zoom: number = 1
  ): void {
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = this.borderWidth / zoom;

    if (this.borderRadius > 0) {
      this.drawRoundedRect(
        ctx,
        bound.x,
        bound.y,
        bound.w,
        bound.h,
        this.borderRadius,
      );
      ctx.stroke();
    } else {
      ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
    }
  }

  private drawTitle(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
  ): void {
    const theme = themeManager.getTheme();
    const titleHeight = 30;

    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `${theme.typography.fontWeight.medium} ${theme.typography.fontSize.medium}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";

    const padding = themeManager.getSpacing("md");
    ctx.fillText(this.title!, bound.x + padding, bound.y + titleHeight / 2);

    // Draw separator line
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(bound.x + padding, bound.y + titleHeight);
    ctx.lineTo(bound.x + bound.w - padding, bound.y + titleHeight);
    ctx.stroke();
  }

  private drawCollapseIndicator(
    ctx: CanvasRenderingContext2D,
    bound: { x: number; y: number; w: number; h: number },
  ): void {
    const theme = themeManager.getTheme();
    const indicatorSize = 16;
    const padding = themeManager.getSpacing("md");
    const x = bound.x + bound.w - padding - indicatorSize;
    const y = this.title ? bound.y + 7 : bound.y + padding;

    ctx.fillStyle = theme.colors.text.secondary;
    ctx.strokeStyle = theme.colors.border.medium;
    ctx.lineWidth = 1;

    // Draw minus/plus icon
    ctx.beginPath();
    ctx.moveTo(x + 4, y + indicatorSize / 2);
    ctx.lineTo(x + indicatorSize - 4, y + indicatorSize / 2);
    ctx.stroke();

    if (this.collapsed) {
      ctx.beginPath();
      ctx.moveTo(x + indicatorSize / 2, y + 4);
      ctx.lineTo(x + indicatorSize / 2, y + indicatorSize - 4);
      ctx.stroke();
    }
  }

  private drawRoundedRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    radius: number,
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + w - radius, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
    ctx.lineTo(x + w, y + h - radius);
    ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
    ctx.lineTo(x + radius, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();
  }

  addChild(element: CanvasElement): void {
    this.childElements.push(element);
    this.updateBounds();
  }

  removeChild(element: CanvasElement): void {
    const index = this.childElements.indexOf(element);
    if (index !== -1) {
      this.childElements.splice(index, 1);
      this.updateBounds();
    }
  }

  toggleCollapse(): void {
    this.collapsed = !this.collapsed;
  }

  setTitle(title: string): void {
    this.title = title;
  }

  private updateBounds(): void {
    if (this.childElements.length === 0) return;

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const child of this.childElements) {
      const childBound = child.getBounds();
      minX = Math.min(minX, childBound.x);
      minY = Math.min(minY, childBound.y);
      maxX = Math.max(maxX, childBound.x + childBound.w);
      maxY = Math.max(maxY, childBound.y + childBound.h);
    }

    const padding = 20;
    const titleHeight = this.title ? 30 : 0;

    this.xywh = Bound.fromXYWH(
      minX - padding,
      minY - padding - titleHeight,
      maxX - minX + padding * 2,
      maxY - minY + padding * 2 + titleHeight,
    ).serialize();
  }

  getChildElements(): CanvasElement[] {
    return [...this.childElements];
  }

  isCollapsed(): boolean {
    return this.collapsed;
  }
}
