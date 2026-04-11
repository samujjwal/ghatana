/**
 * Portal Element — hierarchical drill-down gateway.
 *
 * @doc.type class
 * @doc.purpose Canvas element that acts as an entry point into a nested canvas document
 * @doc.layer elements
 * @doc.pattern Element
 *
 * A portal is a rectanglar "window" on the canvas that, when activated
 * (double-click or programmatic entry), navigates the viewer into a
 * child canvas document.  The drill-down manager in `core/drill-down-manager.ts`
 * coordinates the transition animation and breadcrumb trail.
 *
 * Use-cases:
 * - YAPPC: epic → story → task nested canvases
 * - AEP: high-level pipeline → sub-pipeline detail
 * - Data Cloud: schema overview → table detail → column-level lineage
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface PortalPreview {
  /** Thumbnail image URL (png / data-url) */
  thumbnailUrl?: string;
  /** Short description shown in the portal */
  description?: string;
  /** Estimated element count in the child document */
  elementCount?: number;
  /** Last modified date */
  lastModified?: Date | string;
}

export interface PortalProps extends BaseElementProps {
  /** ID of the target canvas document to navigate into */
  targetDocumentId: string;
  /** Human-readable title of the target document */
  title?: string;
  /** Portal preview info (thumbnail, description, etc.) */
  preview?: PortalPreview;
  /** Breadcrumb label used when inside this portal */
  breadcrumbLabel?: string;
  /** Border accent color */
  accentColor?: string;
  /** Whether the portal is locked (cannot be entered) */
  locked?: boolean;
  /** Whether to show an animated "zoom-in" indicator */
  showDrillIndicator?: boolean;
}

export class PortalElement extends CanvasElement {
  public targetDocumentId: string;
  public title: string;
  public preview: PortalPreview;
  public breadcrumbLabel: string;
  public accentColor: string;
  public locked: boolean;
  public showDrillIndicator: boolean;

  private _previewImg: HTMLImageElement | null = null;

  constructor(props: PortalProps) {
    super(props);
    this.targetDocumentId = props.targetDocumentId;
    this.title = props.title ?? "Nested Document";
    this.preview = props.preview ?? {};
    this.breadcrumbLabel = props.breadcrumbLabel ?? this.title;
    this.accentColor = props.accentColor ?? "#818cf8";
    this.locked = props.locked ?? false;
    this.showDrillIndicator = props.showDrillIndicator ?? true;

    if (this.preview.thumbnailUrl) {
      const img = new Image();
      img.src = this.preview.thumbnailUrl;
      img.addEventListener("load", () => { this._previewImg = img; });
    }
  }

  get type(): CanvasElementType {
    return "portal";
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    const locked = this.locked;

    // Outer glow border
    ctx.shadowColor = this.accentColor + "88";
    ctx.shadowBlur = 10 / zoom;
    ctx.strokeStyle = this.accentColor;
    ctx.lineWidth = 2 / zoom;
    ctx.strokeRect(b.x, b.y, b.w, b.h);
    ctx.shadowBlur = 0;
    ctx.shadowColor = "transparent";

    // Background — either thumbnail or gradient placeholder
    if (this._previewImg) {
      ctx.drawImage(this._previewImg, b.x, b.y, b.w, b.h);
      // Dark overlay on top of thumbnail
      ctx.fillStyle = "rgba(0,0,0,0.45)";
      ctx.fillRect(b.x, b.y, b.w, b.h);
    } else {
      // Gradient background
      const grd = ctx.createLinearGradient(b.x, b.y, b.x + b.w, b.y + b.h);
      grd.addColorStop(0, "#1e1b4b");
      grd.addColorStop(1, "#312e81");
      ctx.fillStyle = grd;
      ctx.fillRect(b.x, b.y, b.w, b.h);
    }

    if (zoom < 0.2) {
      // Microscopic: just show accent dot
      ctx.beginPath();
      ctx.arc(b.x + b.w / 2, b.y + b.h / 2, Math.min(6, b.h * 0.3), 0, Math.PI * 2);
      ctx.fillStyle = this.accentColor;
      ctx.fill();
      ctx.restore();
      return;
    }

    // Title bar
    const titleH = Math.min(32, b.h * 0.18);
    ctx.fillStyle = this.accentColor + "cc";
    ctx.fillRect(b.x, b.y, b.w, titleH);

    if (zoom > 0.3) {
      ctx.fillStyle = "#ffffff";
      ctx.font = `bold ${Math.min(12, titleH * 0.5)}px sans-serif`;
      ctx.textBaseline = "middle";
      ctx.fillText(locked ? `🔒 ${this.title}` : `⤵ ${this.title}`, b.x + 8, b.y + titleH / 2, b.w - 16);
    }

    // Description
    if (this.preview.description && zoom > 0.5) {
      ctx.fillStyle = "rgba(255,255,255,0.7)";
      ctx.font = `${Math.min(10, b.h * 0.09)}px sans-serif`;
      ctx.textBaseline = "top";
      ctx.fillText(this.preview.description, b.x + 8, b.y + titleH + 6, b.w - 16);
    }

    // Element count and last-modified
    if (zoom > 0.6) {
      const meta: string[] = [];
      if (this.preview.elementCount !== undefined) {
        meta.push(`${this.preview.elementCount} elements`);
      }
      if (this.preview.lastModified) {
        const d =
          this.preview.lastModified instanceof Date
            ? this.preview.lastModified
            : new Date(this.preview.lastModified);
        meta.push(d.toLocaleDateString());
      }
      if (meta.length > 0) {
        ctx.fillStyle = "rgba(255,255,255,0.45)";
        ctx.font = `${Math.min(9, b.h * 0.08)}px sans-serif`;
        ctx.textBaseline = "bottom";
        ctx.fillText(meta.join("  ·  "), b.x + 8, b.y + b.h - 6, b.w - 16);
      }
    }

    // Drill-down indicator
    if (this.showDrillIndicator && !locked && zoom > 0.4) {
      const r = Math.min(16, b.h * 0.12);
      const cx = b.x + b.w - r - 8;
      const cy = b.y + b.h - r - 8;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = this.accentColor;
      ctx.fill();
      ctx.fillStyle = "#ffffff";
      ctx.font = `bold ${r}px sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("⊕", cx, cy);
      ctx.textAlign = "left";
    }

    ctx.restore();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}
