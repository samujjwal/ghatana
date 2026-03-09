/**
 * Image Element - Display images on the canvas
 * 
 * @doc.type class
 * @doc.purpose Canvas element for displaying and manipulating images
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style image handling for:
 * - Image display with lazy loading
 * - Resize with aspect ratio lock
 * - Caption support
 * - Cropping and filters
 * - Various fit modes
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type ImageFitMode = "contain" | "cover" | "fill" | "none" | "scale-down";

export interface ImageCrop {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ImageFilter {
  brightness?: number;  // 0-200, default 100
  contrast?: number;    // 0-200, default 100
  saturation?: number;  // 0-200, default 100
  blur?: number;        // 0-20, default 0
  grayscale?: number;   // 0-100, default 0
  opacity?: number;     // 0-100, default 100
}

export interface ImageProps extends BaseElementProps {
  /** Image source URL or data URL */
  src: string;
  /** Alternative text for accessibility */
  alt?: string;
  /** Image caption */
  caption?: string;
  /** How the image should fit within its bounds */
  fitMode?: ImageFitMode;
  /** Border radius for rounded corners */
  borderRadius?: number;
  /** Border color */
  borderColor?: string;
  /** Border width */
  borderWidth?: number;
  /** Shadow configuration */
  shadow?: {
    offsetX: number;
    offsetY: number;
    blur: number;
    color: string;
  };
  /** Crop region */
  crop?: ImageCrop;
  /** Image filters */
  filters?: ImageFilter;
  /** Whether to maintain aspect ratio when resizing */
  lockAspectRatio?: boolean;
  /** Original image dimensions */
  naturalWidth?: number;
  naturalHeight?: number;
  /** Loading state */
  loading?: "idle" | "loading" | "loaded" | "error";
  /** Placeholder color while loading */
  placeholderColor?: string;
}

export class ImageElement extends CanvasElement {
  public src: string;
  public alt: string;
  public caption: string;
  public fitMode: ImageFitMode;
  public borderRadius: number;
  public borderColor: string;
  public borderWidth: number;
  public shadow?: ImageProps["shadow"];
  public crop?: ImageCrop;
  public filters: ImageFilter;
  public lockAspectRatio: boolean;
  public naturalWidth: number;
  public naturalHeight: number;
  public loading: "idle" | "loading" | "loaded" | "error";
  public placeholderColor: string;

  private _imageCache: HTMLImageElement | null = null;
  private _loadPromise: Promise<void> | null = null;

  private static readonly CAPTION_HEIGHT = 24;
  private static readonly CAPTION_PADDING = 8;

  constructor(props: ImageProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.src = props.src;
    this.alt = props.alt || "";
    this.caption = props.caption || "";
    this.fitMode = props.fitMode || "contain";
    this.borderRadius = props.borderRadius ?? 0;
    this.borderColor = props.borderColor || theme.colors.border.medium;
    this.borderWidth = props.borderWidth ?? 0;
    this.shadow = props.shadow;
    this.crop = props.crop;
    this.filters = props.filters || {};
    this.lockAspectRatio = props.lockAspectRatio ?? true;
    this.naturalWidth = props.naturalWidth || 0;
    this.naturalHeight = props.naturalHeight || 0;
    this.loading = props.loading || "idle";
    this.placeholderColor = props.placeholderColor || theme.colors.surface;

    // Start loading the image
    this.loadImage();
  }

  get type(): CanvasElementType {
    return "image" as CanvasElementType;
  }

  get aspectRatio(): number {
    if (this.naturalWidth && this.naturalHeight) {
      return this.naturalWidth / this.naturalHeight;
    }
    const bound = this.getBounds();
    return bound.w / bound.h;
  }

  /**
   * Load the image asynchronously
   */
  async loadImage(): Promise<void> {
    if (this._loadPromise) {
      return this._loadPromise;
    }

    if (!this.src) {
      this.loading = "error";
      return;
    }

    this.loading = "loading";

    this._loadPromise = new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "anonymous";

      img.onload = () => {
        this._imageCache = img;
        this.naturalWidth = img.naturalWidth;
        this.naturalHeight = img.naturalHeight;
        this.loading = "loaded";
        resolve();
      };

      img.onerror = () => {
        this._imageCache = null;
        this.loading = "error";
        reject(new Error(`Failed to load image: ${this.src}`));
      };

      img.src = this.src;
    });

    return this._loadPromise;
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();
    const theme = themeManager.getTheme();

    // Apply shadow
    if (this.shadow) {
      ctx.shadowColor = this.shadow.color;
      ctx.shadowBlur = this.shadow.blur;
      ctx.shadowOffsetX = this.shadow.offsetX;
      ctx.shadowOffsetY = this.shadow.offsetY;
    }

    // Draw based on loading state
    switch (this.loading) {
      case "loaded":
        this.drawImage(ctx, bound, zoom);
        break;
      case "loading":
        this.drawPlaceholder(ctx, bound, "loading", zoom);
        break;
      case "error":
        this.drawPlaceholder(ctx, bound, "error", zoom);
        break;
      default:
        this.drawPlaceholder(ctx, bound, "idle", zoom);
    }

    // Reset shadow
    if (this.shadow) {
      ctx.shadowColor = "transparent";
      ctx.shadowBlur = 0;
      ctx.shadowOffsetX = 0;
      ctx.shadowOffsetY = 0;
    }

    // Draw border
    if (this.borderWidth > 0) {
      this.drawBorder(ctx, bound, zoom);
    }

    // Draw caption if present and zoom is sufficient
    if (this.caption && zoom > 0.3) {
      this.drawCaption(ctx, bound, zoom, theme);
    }

    ctx.restore();
  }

  private drawImage(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    if (!this._imageCache) return;

    // Apply clip for rounded corners
    if (this.borderRadius > 0) {
      this.applyClipPath(ctx, bound);
    }

    // Apply filters
    ctx.filter = this.getFilterString();

    // Calculate source and destination rectangles based on fit mode
    const { sx, sy, sw, sh, dx, dy, dw, dh } = this.calculateDrawParams(bound);

    // Draw the image
    ctx.drawImage(
      this._imageCache,
      sx, sy, sw, sh,  // Source rectangle (for cropping)
      dx, dy, dw, dh   // Destination rectangle
    );

    // Reset filter
    ctx.filter = "none";

    // Reset clip
    if (this.borderRadius > 0) {
      ctx.restore();
      ctx.save();
      this.applyTransform(ctx);
    }
  }

  private calculateDrawParams(bound: Bound): {
    sx: number; sy: number; sw: number; sh: number;
    dx: number; dy: number; dw: number; dh: number;
  } {
    const img = this._imageCache!;

    // Source dimensions (from image or crop)
    let sx = 0, sy = 0, sw = img.naturalWidth, sh = img.naturalHeight;

    if (this.crop) {
      sx = this.crop.x;
      sy = this.crop.y;
      sw = this.crop.width;
      sh = this.crop.height;
    }

    // Default destination is the full bound
    let dx = bound.x, dy = bound.y, dw = bound.w, dh = bound.h;

    // Adjust based on fit mode
    switch (this.fitMode) {
      case "contain": {
        const imgAspect = sw / sh;
        const boundAspect = bound.w / bound.h;

        if (imgAspect > boundAspect) {
          // Image is wider - fit to width
          dw = bound.w;
          dh = bound.w / imgAspect;
          dy = bound.y + (bound.h - dh) / 2;
        } else {
          // Image is taller - fit to height
          dh = bound.h;
          dw = bound.h * imgAspect;
          dx = bound.x + (bound.w - dw) / 2;
        }
        break;
      }

      case "cover": {
        const imgAspect = sw / sh;
        const boundAspect = bound.w / bound.h;

        if (imgAspect > boundAspect) {
          // Image is wider - fit to height, crop width
          const scale = bound.h / sh;
          const scaledWidth = sw * scale;
          sx += (scaledWidth - bound.w) / scale / 2;
          sw = bound.w / scale;
        } else {
          // Image is taller - fit to width, crop height
          const scale = bound.w / sw;
          const scaledHeight = sh * scale;
          sy += (scaledHeight - bound.h) / scale / 2;
          sh = bound.h / scale;
        }
        break;
      }

      case "fill":
        // Stretch to fill - use defaults
        break;

      case "none":
        // Draw at natural size, centered
        dw = sw;
        dh = sh;
        dx = bound.x + (bound.w - sw) / 2;
        dy = bound.y + (bound.h - sh) / 2;
        break;

      case "scale-down": {
        // Like contain, but never scale up
        const imgAspect = sw / sh;
        const boundAspect = bound.w / bound.h;

        if (sw <= bound.w && sh <= bound.h) {
          // Image fits - draw at natural size, centered
          dw = sw;
          dh = sh;
          dx = bound.x + (bound.w - sw) / 2;
          dy = bound.y + (bound.h - sh) / 2;
        } else {
          // Scale down like contain
          if (imgAspect > boundAspect) {
            dw = bound.w;
            dh = bound.w / imgAspect;
            dy = bound.y + (bound.h - dh) / 2;
          } else {
            dh = bound.h;
            dw = bound.h * imgAspect;
            dx = bound.x + (bound.w - dw) / 2;
          }
        }
        break;
      }
    }

    return { sx, sy, sw, sh, dx, dy, dw, dh };
  }

  private getFilterString(): string {
    const filters: string[] = [];

    if (this.filters.brightness !== undefined && this.filters.brightness !== 100) {
      filters.push(`brightness(${this.filters.brightness}%)`);
    }
    if (this.filters.contrast !== undefined && this.filters.contrast !== 100) {
      filters.push(`contrast(${this.filters.contrast}%)`);
    }
    if (this.filters.saturation !== undefined && this.filters.saturation !== 100) {
      filters.push(`saturate(${this.filters.saturation}%)`);
    }
    if (this.filters.blur && this.filters.blur > 0) {
      filters.push(`blur(${this.filters.blur}px)`);
    }
    if (this.filters.grayscale && this.filters.grayscale > 0) {
      filters.push(`grayscale(${this.filters.grayscale}%)`);
    }
    if (this.filters.opacity !== undefined && this.filters.opacity !== 100) {
      filters.push(`opacity(${this.filters.opacity}%)`);
    }

    return filters.length > 0 ? filters.join(" ") : "none";
  }

  private applyClipPath(ctx: CanvasRenderingContext2D, bound: Bound): void {
    ctx.save();
    ctx.beginPath();
    this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.clip();
  }

  private drawPlaceholder(
    ctx: CanvasRenderingContext2D,
    bound: Bound,
    state: "idle" | "loading" | "error",
    zoom: number
  ): void {
    const theme = themeManager.getTheme();

    // Draw background
    ctx.fillStyle = this.placeholderColor;
    if (this.borderRadius > 0) {
      ctx.beginPath();
      this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
      ctx.fill();
    } else {
      ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
    }

    // Draw icon/text based on state (only if zoom is sufficient)
    if (zoom > 0.3) {
      const centerX = bound.x + bound.w / 2;
      const centerY = bound.y + bound.h / 2;
      const iconSize = Math.min(40, Math.min(bound.w, bound.h) * 0.3);

      ctx.fillStyle = theme.colors.text.secondary;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.font = `${iconSize}px Arial`;

      switch (state) {
        case "loading":
          ctx.fillText("⏳", centerX, centerY);
          break;
        case "error":
          ctx.fillStyle = ((theme.colors as unknown as Record<string, Record<string, string>>).status?.error) || "#ef4444";
          ctx.fillText("⚠️", centerX, centerY);
          // Draw error message
          ctx.font = `${Math.max(12, iconSize / 3)}px ${theme.typography.fontFamily}`;
          ctx.fillText("Failed to load", centerX, centerY + iconSize);
          break;
        default:
          ctx.fillText("🖼️", centerX, centerY);
      }
    }
  }

  private drawBorder(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = this.borderWidth / zoom;

    if (this.borderRadius > 0) {
      ctx.beginPath();
      this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
      ctx.stroke();
    } else {
      ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
    }
  }

  private drawCaption(
    ctx: CanvasRenderingContext2D,
    bound: Bound,
    zoom: number,
    theme: YAPPCTheme
  ): void {
    const captionY = bound.y + bound.h + ImageElement.CAPTION_PADDING;
    const captionHeight = ImageElement.CAPTION_HEIGHT;

    // Draw caption background
    ctx.fillStyle = "rgba(0, 0, 0, 0.05)";
    ctx.fillRect(bound.x, captionY, bound.w, captionHeight);

    // Draw caption text
    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `${14 / zoom}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    // Truncate if too long
    const maxWidth = bound.w - 16;
    const caption = this.truncateText(ctx, this.caption, maxWidth);
    ctx.fillText(caption, bound.x + bound.w / 2, captionY + captionHeight / 2);
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

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();
    const inImage = x >= bound.x && x <= bound.x + bound.w &&
                    y >= bound.y && y <= bound.y + bound.h;

    // Also check caption area
    if (this.caption) {
      const captionY = bound.y + bound.h + ImageElement.CAPTION_PADDING;
      const inCaption = x >= bound.x && x <= bound.x + bound.w &&
                        y >= captionY && y <= captionY + ImageElement.CAPTION_HEIGHT;
      return inImage || inCaption;
    }

    return inImage;
  }

  // Public API methods

  /**
   * Update the image source
   */
  async setSrc(src: string): Promise<void> {
    this.src = src;
    this._imageCache = null;
    this._loadPromise = null;
    this.loading = "idle";
    await this.loadImage();
  }

  /**
   * Set crop region
   */
  setCrop(crop: ImageCrop | undefined): void {
    this.crop = crop;
  }

  /**
   * Apply filters
   */
  setFilters(filters: Partial<ImageFilter>): void {
    this.filters = { ...this.filters, ...filters };
  }

  /**
   * Reset all filters to defaults
   */
  resetFilters(): void {
    this.filters = {};
  }

  /**
   * Get the cached image element (for external use)
   */
  getImageElement(): HTMLImageElement | null {
    return this._imageCache;
  }

  /**
   * Export image as data URL
   */
  toDataURL(format: string = "image/png", quality: number = 0.92): string | null {
    if (!this._imageCache) return null;

    const canvas = document.createElement("canvas");
    const bound = this.getBounds();
    canvas.width = bound.w;
    canvas.height = bound.h;

    const ctx = canvas.getContext("2d");
    if (!ctx) return null;

    // Apply filters
    ctx.filter = this.getFilterString();

    // Draw image
    const { sx, sy, sw, sh, dx, dy, dw, dh } = this.calculateDrawParams(
      Bound.fromXYWH(0, 0, bound.w, bound.h)
    );

    ctx.drawImage(this._imageCache, sx, sy, sw, sh, dx, dy, dw, dh);

    return canvas.toDataURL(format, quality);
  }

  /**
   * Resize while maintaining aspect ratio
   */
  resizeWithAspectRatio(newWidth?: number, newHeight?: number): void {
    if (!this.lockAspectRatio) {
      if (newWidth !== undefined && newHeight !== undefined) {
        const bound = this.getBounds();
        this.xywh = Bound.fromXYWH(bound.x, bound.y, newWidth, newHeight).serialize();
      }
      return;
    }

    const bound = this.getBounds();
    const aspectRatio = this.aspectRatio;

    if (newWidth !== undefined) {
      const height = newWidth / aspectRatio;
      this.xywh = Bound.fromXYWH(bound.x, bound.y, newWidth, height).serialize();
    } else if (newHeight !== undefined) {
      const width = newHeight * aspectRatio;
      this.xywh = Bound.fromXYWH(bound.x, bound.y, width, newHeight).serialize();
    }
  }
}
