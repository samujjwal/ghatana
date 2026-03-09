/**
 * Attachment Element - Display file attachments on the canvas
 * 
 * @doc.type class
 * @doc.purpose Canvas element for displaying and interacting with file attachments
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style attachment handling for:
 * - File display with icons
 * - Download/open actions
 * - Preview thumbnails for supported types
 * - File metadata display
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type AttachmentDisplayMode = "card" | "inline" | "preview";

export interface AttachmentProps extends BaseElementProps {
  /** File name */
  name: string;
  /** File size in bytes */
  size: number;
  /** MIME type */
  mimeType: string;
  /** File URL or data URL */
  url?: string;
  /** Source ID (for blob storage) */
  sourceId?: string;
  /** Caption/description */
  caption?: string;
  /** Display mode */
  displayMode?: AttachmentDisplayMode;
  /** Whether the file is being uploaded */
  uploading?: boolean;
  /** Upload progress (0-100) */
  uploadProgress?: number;
  /** Preview image URL (for PDFs, documents) */
  previewUrl?: string;
  /** Whether to show file info */
  showInfo?: boolean;
  /** Background color */
  backgroundColor?: string;
  /** Border radius */
  borderRadius?: number;
}

// File type categories and their icons
const FILE_ICONS: Record<string, string> = {
  // Documents
  "application/pdf": "📄",
  "application/msword": "📝",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "📝",
  "application/vnd.ms-excel": "📊",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "📊",
  "application/vnd.ms-powerpoint": "📽️",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation": "📽️",
  // Images
  "image/jpeg": "🖼️",
  "image/png": "🖼️",
  "image/gif": "🖼️",
  "image/svg+xml": "🖼️",
  "image/webp": "🖼️",
  // Videos
  "video/mp4": "🎬",
  "video/webm": "🎬",
  "video/quicktime": "🎬",
  // Audio
  "audio/mpeg": "🎵",
  "audio/wav": "🎵",
  "audio/ogg": "🎵",
  // Archives
  "application/zip": "📦",
  "application/x-rar-compressed": "📦",
  "application/x-7z-compressed": "📦",
  "application/gzip": "📦",
  // Code
  "text/javascript": "📜",
  "application/json": "📜",
  "text/html": "📜",
  "text/css": "📜",
  "text/plain": "📝",
  // Default
  "default": "📎",
};

export class AttachmentElement extends CanvasElement {
  public name: string;
  public size: number;
  public mimeType: string;
  public url?: string;
  public sourceId?: string;
  public caption: string;
  public displayMode: AttachmentDisplayMode;
  public uploading: boolean;
  public uploadProgress: number;
  public previewUrl?: string;
  public showInfo: boolean;
  public backgroundColor: string;
  public borderRadius: number;

  private _previewImageCache: HTMLImageElement | null = null;

  private static readonly CARD_MIN_WIDTH = 200;
  private static readonly CARD_HEIGHT = 80;
  private static readonly INLINE_HEIGHT = 32;
  private static readonly ICON_SIZE = 40;

  constructor(props: AttachmentProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.name = props.name;
    this.size = props.size;
    this.mimeType = props.mimeType;
    this.url = props.url;
    this.sourceId = props.sourceId;
    this.caption = props.caption || "";
    this.displayMode = props.displayMode || "card";
    this.uploading = props.uploading || false;
    this.uploadProgress = props.uploadProgress || 0;
    this.previewUrl = props.previewUrl;
    this.showInfo = props.showInfo ?? true;
    this.backgroundColor = props.backgroundColor || theme.colors.surface;
    this.borderRadius = props.borderRadius ?? 8;

    // Load preview if available
    if (this.previewUrl) {
      this.loadPreview();
    }
  }

  get type(): CanvasElementType {
    return "attachment" as CanvasElementType;
  }

  get fileExtension(): string {
    const parts = this.name.split(".");
    return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : "";
  }

  get fileIcon(): string {
    return FILE_ICONS[this.mimeType] || FILE_ICONS["default"];
  }

  get formattedSize(): string {
    return this.formatFileSize(this.size);
  }

  private formatFileSize(bytes: number): string {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + " " + sizes[i];
  }

  private async loadPreview(): Promise<void> {
    if (!this.previewUrl) return;

    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "anonymous";

      img.onload = () => {
        this._previewImageCache = img;
        resolve();
      };

      img.onerror = () => {
        this._previewImageCache = null;
        reject(new Error("Failed to load preview"));
      };

      img.src = this.previewUrl!;
    });
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    switch (this.displayMode) {
      case "card":
        this.renderCard(ctx, bound, zoom);
        break;
      case "inline":
        this.renderInline(ctx, bound, zoom);
        break;
      case "preview":
        this.renderPreview(ctx, bound, zoom);
        break;
    }

    ctx.restore();
  }

  private renderCard(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    const theme = themeManager.getTheme();

    // Draw background
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();

    // Draw border
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1 / zoom;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.stroke();

    // Draw icon
    const iconX = bound.x + 16;
    const iconY = bound.y + (bound.h - AttachmentElement.ICON_SIZE) / 2;

    if (zoom > 0.3) {
      ctx.font = `${AttachmentElement.ICON_SIZE}px Arial`;
      ctx.textAlign = "left";
      ctx.textBaseline = "top";
      ctx.fillText(this.fileIcon, iconX, iconY);
    } else {
      // Draw simplified icon at low zoom
      ctx.fillStyle = theme.colors.primary;
      ctx.fillRect(iconX, iconY, AttachmentElement.ICON_SIZE * 0.8, AttachmentElement.ICON_SIZE * 0.8);
    }

    // Draw file info
    if (this.showInfo && zoom > 0.3) {
      const textX = iconX + AttachmentElement.ICON_SIZE + 12;
      const textWidth = bound.w - textX - 16 + bound.x;

      // File name
      ctx.fillStyle = theme.colors.text.primary;
      ctx.font = `bold ${14}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "left";
      ctx.textBaseline = "middle";
      const displayName = this.truncateText(ctx, this.name, textWidth);
      ctx.fillText(displayName, textX, bound.y + bound.h / 2 - 10);

      // File size and type
      ctx.fillStyle = theme.colors.text.secondary;
      ctx.font = `${12}px ${theme.typography.fontFamily}`;
      const infoText = `${this.formattedSize} • ${this.fileExtension.toUpperCase()}`;
      ctx.fillText(infoText, textX, bound.y + bound.h / 2 + 10);
    }

    // Draw upload progress if uploading
    if (this.uploading) {
      this.drawUploadProgress(ctx, bound, zoom, theme);
    }
  }

  private renderInline(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    const theme = themeManager.getTheme();

    // Draw background
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius / 2);
    ctx.fill();

    // Draw border
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1 / zoom;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius / 2);
    ctx.stroke();

    if (zoom > 0.3) {
      // Draw icon
      const iconSize = 20;
      ctx.font = `${iconSize}px Arial`;
      ctx.textAlign = "left";
      ctx.textBaseline = "middle";
      ctx.fillText(this.fileIcon, bound.x + 8, bound.y + bound.h / 2);

      // Draw name
      ctx.fillStyle = theme.colors.text.primary;
      ctx.font = `${12}px ${theme.typography.fontFamily}`;
      const maxTextWidth = bound.w - iconSize - 24;
      const displayName = this.truncateText(ctx, this.name, maxTextWidth);
      ctx.fillText(displayName, bound.x + iconSize + 16, bound.y + bound.h / 2);
    }
  }

  private renderPreview(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
    const theme = themeManager.getTheme();

    // Draw background
    ctx.fillStyle = this.backgroundColor;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();

    // Draw preview image or placeholder
    const previewHeight = bound.h - 48;

    if (this._previewImageCache && previewHeight > 0) {
      // Draw preview image
      ctx.save();
      ctx.beginPath();
      this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, previewHeight, this.borderRadius);
      ctx.clip();

      const img = this._previewImageCache;
      const imgAspect = img.naturalWidth / img.naturalHeight;
      const boundAspect = bound.w / previewHeight;

      let dx = bound.x, dy = bound.y, dw = bound.w, dh = previewHeight;

      if (imgAspect > boundAspect) {
        dh = bound.w / imgAspect;
        dy = bound.y + (previewHeight - dh) / 2;
      } else {
        dw = previewHeight * imgAspect;
        dx = bound.x + (bound.w - dw) / 2;
      }

      ctx.drawImage(img, dx, dy, dw, dh);
      ctx.restore();
    } else {
      // Draw placeholder
      ctx.fillStyle = theme.colors.surface;
      this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, previewHeight, this.borderRadius);
      ctx.fill();

      if (zoom > 0.3) {
        ctx.font = `48px Arial`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(this.fileIcon, bound.x + bound.w / 2, bound.y + previewHeight / 2);
      }
    }

    // Draw file info bar
    const infoY = bound.y + previewHeight;
    ctx.fillStyle = theme.colors.background;
    ctx.fillRect(bound.x, infoY, bound.w, 48);

    // Draw border
    ctx.strokeStyle = theme.colors.border.light;
    ctx.lineWidth = 1 / zoom;
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.stroke();

    if (zoom > 0.3) {
      // Draw file name and size
      ctx.fillStyle = theme.colors.text.primary;
      ctx.font = `bold ${13}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "left";
      ctx.textBaseline = "middle";
      const displayName = this.truncateText(ctx, this.name, bound.w - 32);
      ctx.fillText(displayName, bound.x + 12, infoY + 16);

      ctx.fillStyle = theme.colors.text.secondary;
      ctx.font = `${11}px ${theme.typography.fontFamily}`;
      ctx.fillText(this.formattedSize, bound.x + 12, infoY + 34);
    }

    // Draw upload progress if uploading
    if (this.uploading) {
      this.drawUploadProgress(ctx, bound, zoom, theme);
    }
  }

  private drawUploadProgress(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    // Semi-transparent overlay
    ctx.fillStyle = "rgba(0, 0, 0, 0.5)";
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();

    // Progress bar background
    const barWidth = bound.w - 32;
    const barHeight = 8;
    const barX = bound.x + 16;
    const barY = bound.y + bound.h / 2 - barHeight / 2;

    ctx.fillStyle = "rgba(255, 255, 255, 0.3)";
    this.drawRoundedRect(ctx, barX, barY, barWidth, barHeight, barHeight / 2);
    ctx.fill();

    // Progress bar fill
    const progressWidth = (barWidth * this.uploadProgress) / 100;
    ctx.fillStyle = theme.colors.primary;
    this.drawRoundedRect(ctx, barX, barY, progressWidth, barHeight, barHeight / 2);
    ctx.fill();

    // Progress text
    if (zoom > 0.3) {
      ctx.fillStyle = "white";
      ctx.font = `bold ${14}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "center";
      ctx.textBaseline = "bottom";
      ctx.fillText(`${Math.round(this.uploadProgress)}%`, bound.x + bound.w / 2, barY - 8);
    }
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

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Public API methods

  /**
   * Set upload progress
   */
  setUploadProgress(progress: number): void {
    this.uploadProgress = Math.max(0, Math.min(100, progress));
    if (progress >= 100) {
      this.uploading = false;
    }
  }

  /**
   * Start upload
   */
  startUpload(): void {
    this.uploading = true;
    this.uploadProgress = 0;
  }

  /**
   * Complete upload
   */
  completeUpload(url: string, sourceId?: string): void {
    this.uploading = false;
    this.uploadProgress = 100;
    this.url = url;
    this.sourceId = sourceId;
  }

  /**
   * Set preview image
   */
  async setPreview(previewUrl: string): Promise<void> {
    this.previewUrl = previewUrl;
    await this.loadPreview();
  }

  /**
   * Get download URL
   */
  getDownloadUrl(): string | undefined {
    return this.url;
  }

  /**
   * Check if file is a specific type
   */
  isFileType(category: "document" | "image" | "video" | "audio" | "archive" | "code"): boolean {
    const typeMap: Record<string, string[]> = {
      document: ["application/pdf", "application/msword", "application/vnd.openxmlformats"],
      image: ["image/"],
      video: ["video/"],
      audio: ["audio/"],
      archive: ["application/zip", "application/x-rar", "application/x-7z", "application/gzip"],
      code: ["text/javascript", "application/json", "text/html", "text/css"],
    };

    const prefixes = typeMap[category] || [];
    return prefixes.some(prefix => this.mimeType.startsWith(prefix));
  }
}
