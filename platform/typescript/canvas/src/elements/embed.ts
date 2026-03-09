/**
 * Embed Element - Embed external content (YouTube, Figma, websites, etc.)
 * 
 * @doc.type class
 * @doc.purpose Canvas element for embedding external content and iframes
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style embeds for:
 * - YouTube videos
 * - Figma designs
 * - Generic websites
 * - HTML content
 * - Bookmark cards
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type EmbedType = 
  | "youtube" 
  | "figma" 
  | "loom" 
  | "github" 
  | "twitter" 
  | "codepen"
  | "iframe" 
  | "bookmark" 
  | "html";

export interface EmbedMetadata {
  title?: string;
  description?: string;
  image?: string;
  siteName?: string;
  favicon?: string;
  author?: string;
  publishedAt?: string;
}

export interface EmbedProps extends BaseElementProps {
  /** Embed type */
  embedType: EmbedType;
  /** Source URL */
  url: string;
  /** Embed title */
  title?: string;
  /** Caption */
  caption?: string;
  /** HTML content (for html type) */
  html?: string;
  /** Metadata for bookmark display */
  metadata?: EmbedMetadata;
  /** Whether to show controls (for videos) */
  showControls?: boolean;
  /** Auto-play (for videos) */
  autoplay?: boolean;
  /** Start time (for videos, in seconds) */
  startTime?: number;
  /** Border radius */
  borderRadius?: number;
  /** Background color */
  backgroundColor?: string;
  /** Loading state */
  loading?: "idle" | "loading" | "loaded" | "error";
  /** Error message */
  errorMessage?: string;
}

export class EmbedElement extends CanvasElement {
  public embedType: EmbedType;
  public url: string;
  public title: string;
  public caption: string;
  public html?: string;
  public metadata: EmbedMetadata;
  public showControls: boolean;
  public autoplay: boolean;
  public startTime: number;
  public borderRadius: number;
  public backgroundColor: string;
  public loading: "idle" | "loading" | "loaded" | "error";
  public errorMessage?: string;

  private _thumbnailCache: HTMLImageElement | null = null;

  private static readonly BOOKMARK_HEIGHT = 120;
  private static readonly CAPTION_HEIGHT = 28;

  constructor(props: EmbedProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.embedType = props.embedType;
    this.url = props.url;
    this.title = props.title || "";
    this.caption = props.caption || "";
    this.html = props.html;
    this.metadata = props.metadata || {};
    this.showControls = props.showControls ?? true;
    this.autoplay = props.autoplay ?? false;
    this.startTime = props.startTime || 0;
    this.borderRadius = props.borderRadius ?? 8;
    this.backgroundColor = props.backgroundColor || theme.colors.surface;
    this.loading = props.loading || "idle";
    this.errorMessage = props.errorMessage;

    // Load thumbnail if available
    this.loadThumbnail();
  }

  get type(): CanvasElementType {
    return "embed" as CanvasElementType;
  }

  /**
   * Get the embed URL for iframe rendering (processed for specific services)
   */
  get embedUrl(): string {
    switch (this.embedType) {
      case "youtube":
        return this.getYouTubeEmbedUrl();
      case "figma":
        return this.getFigmaEmbedUrl();
      case "loom":
        return this.getLoomEmbedUrl();
      case "github":
        return this.url; // GitHub embeds handled differently
      case "codepen":
        return this.getCodePenEmbedUrl();
      default:
        return this.url;
    }
  }

  private getYouTubeEmbedUrl(): string {
    const videoId = this.extractYouTubeId(this.url);
    if (!videoId) return this.url;

    let embedUrl = `https://www.youtube.com/embed/${videoId}`;
    const params: string[] = [];

    if (this.startTime > 0) {
      params.push(`start=${this.startTime}`);
    }
    if (this.autoplay) {
      params.push("autoplay=1");
    }
    if (!this.showControls) {
      params.push("controls=0");
    }

    if (params.length > 0) {
      embedUrl += "?" + params.join("&");
    }

    return embedUrl;
  }

  private extractYouTubeId(url: string): string | null {
    const patterns = [
      /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([^&\?\/]+)/,
      /youtube\.com\/shorts\/([^&\?\/]+)/,
    ];

    for (const pattern of patterns) {
      const match = url.match(pattern);
      if (match) return match[1];
    }

    return null;
  }

  private getFigmaEmbedUrl(): string {
    // Figma URLs need to be converted to embed URLs
    if (this.url.includes("figma.com/file/") || this.url.includes("figma.com/design/")) {
      return `https://www.figma.com/embed?embed_host=share&url=${encodeURIComponent(this.url)}`;
    }
    return this.url;
  }

  private getLoomEmbedUrl(): string {
    const loomMatch = this.url.match(/loom\.com\/share\/([^?]+)/);
    if (loomMatch) {
      return `https://www.loom.com/embed/${loomMatch[1]}`;
    }
    return this.url;
  }

  private getCodePenEmbedUrl(): string {
    const match = this.url.match(/codepen\.io\/([^\/]+)\/pen\/([^?]+)/);
    if (match) {
      return `https://codepen.io/${match[1]}/embed/${match[2]}?default-tab=result`;
    }
    return this.url;
  }

  private async loadThumbnail(): Promise<void> {
    const thumbnailUrl = this.getThumbnailUrl();
    if (!thumbnailUrl) return;

    return new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = "anonymous";

      img.onload = () => {
        this._thumbnailCache = img;
        this.loading = "loaded";
        resolve();
      };

      img.onerror = () => {
        this._thumbnailCache = null;
        resolve();
      };

      img.src = thumbnailUrl;
    });
  }

  private getThumbnailUrl(): string | null {
    // Return metadata image if available
    if (this.metadata.image) {
      return this.metadata.image;
    }

    // Generate service-specific thumbnails
    switch (this.embedType) {
      case "youtube": {
        const videoId = this.extractYouTubeId(this.url);
        if (videoId) {
          return `https://img.youtube.com/vi/${videoId}/maxresdefault.jpg`;
        }
        break;
      }
      // Other services don't have public thumbnail APIs
    }

    return null;
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();

    switch (this.embedType) {
      case "bookmark":
        this.renderBookmark(ctx, bound, zoom);
        break;
      default:
        this.renderEmbed(ctx, bound, zoom);
    }

    ctx.restore();
  }

  private renderEmbed(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
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

    // Calculate content area (minus caption if present)
    const contentHeight = this.caption ? bound.h - EmbedElement.CAPTION_HEIGHT : bound.h;

    // Draw thumbnail or placeholder
    if (this._thumbnailCache) {
      this.drawThumbnail(ctx, bound, contentHeight, zoom);
    } else {
      this.drawPlaceholder(ctx, bound, contentHeight, zoom);
    }

    // Draw play button for videos
    if (this.isVideoEmbed() && zoom > 0.3) {
      this.drawPlayButton(ctx, bound, contentHeight);
    }

    // Draw caption
    if (this.caption && zoom > 0.3) {
      this.drawCaption(ctx, bound, zoom, theme);
    }

    // Draw loading/error overlay
    if (this.loading === "loading" || this.loading === "error") {
      this.drawStatusOverlay(ctx, bound, zoom, theme);
    }
  }

  private renderBookmark(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number): void {
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

    if (zoom < 0.3) {
      // Simplified view at low zoom
      return;
    }

    const padding = 16;
    const imageWidth = this._thumbnailCache ? 120 : 0;
    const textX = bound.x + padding;
    const textWidth = bound.w - padding * 2 - imageWidth - (imageWidth > 0 ? padding : 0);

    // Draw thumbnail on the right
    if (this._thumbnailCache) {
      const imgX = bound.x + bound.w - imageWidth - padding;
      const imgY = bound.y + padding;
      const imgH = bound.h - padding * 2;

      ctx.save();
      ctx.beginPath();
      this.drawRoundedRect(ctx, imgX, imgY, imageWidth, imgH, 4);
      ctx.clip();

      const img = this._thumbnailCache;
      const imgAspect = img.naturalWidth / img.naturalHeight;
      const targetAspect = imageWidth / imgH;

      let sx = 0, sy = 0, sw = img.naturalWidth, sh = img.naturalHeight;

      if (imgAspect > targetAspect) {
        sw = img.naturalHeight * targetAspect;
        sx = (img.naturalWidth - sw) / 2;
      } else {
        sh = img.naturalWidth / targetAspect;
        sy = (img.naturalHeight - sh) / 2;
      }

      ctx.drawImage(img, sx, sy, sw, sh, imgX, imgY, imageWidth, imgH);
      ctx.restore();
    }

    // Draw favicon and site name
    let currentY = bound.y + padding;

    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `${12}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";

    const siteName = this.metadata.siteName || this.getDomainFromUrl();
    ctx.fillText(siteName, textX, currentY);
    currentY += 20;

    // Draw title
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `bold ${14}px ${theme.typography.fontFamily}`;
    const displayTitle = this.truncateText(ctx, this.title || this.metadata.title || this.url, textWidth);
    ctx.fillText(displayTitle, textX, currentY);
    currentY += 24;

    // Draw description
    if (this.metadata.description) {
      ctx.fillStyle = theme.colors.text.secondary;
      ctx.font = `${12}px ${theme.typography.fontFamily}`;
      const lines = this.wrapText(ctx, this.metadata.description, textWidth, 2);
      lines.forEach((line) => {
        ctx.fillText(line, textX, currentY);
        currentY += 16;
      });
    }

    // Draw URL
    ctx.fillStyle = theme.colors.text.muted;
    ctx.font = `${11}px ${theme.typography.fontFamily}`;
    const displayUrl = this.truncateText(ctx, this.url, textWidth);
    ctx.fillText(displayUrl, textX, bound.y + bound.h - padding - 12);
  }

  private drawThumbnail(
    ctx: CanvasRenderingContext2D,
    bound: Bound,
    contentHeight: number,
    zoom: number
  ): void {
    if (!this._thumbnailCache) return;

    ctx.save();
    ctx.beginPath();
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, contentHeight, this.borderRadius);
    ctx.clip();

    const img = this._thumbnailCache;
    const imgAspect = img.naturalWidth / img.naturalHeight;
    const boundAspect = bound.w / contentHeight;

    let dx = bound.x, dy = bound.y, dw = bound.w, dh = contentHeight;

    // Cover fit
    if (imgAspect > boundAspect) {
      dw = contentHeight * imgAspect;
      dx = bound.x + (bound.w - dw) / 2;
    } else {
      dh = bound.w / imgAspect;
      dy = bound.y + (contentHeight - dh) / 2;
    }

    ctx.drawImage(img, dx, dy, dw, dh);
    ctx.restore();
  }

  private drawPlaceholder(
    ctx: CanvasRenderingContext2D,
    bound: Bound,
    contentHeight: number,
    zoom: number
  ): void {
    const theme = themeManager.getTheme();

    // Draw service icon
    if (zoom > 0.3) {
      const icon = this.getServiceIcon();
      const iconSize = Math.min(48, Math.min(bound.w, contentHeight) * 0.2);

      ctx.font = `${iconSize}px Arial`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillStyle = theme.colors.text.secondary;
      ctx.fillText(icon, bound.x + bound.w / 2, bound.y + contentHeight / 2);

      // Draw title below icon
      if (this.title) {
        ctx.font = `${14}px ${theme.typography.fontFamily}`;
        const displayTitle = this.truncateText(ctx, this.title, bound.w - 32);
        ctx.fillText(displayTitle, bound.x + bound.w / 2, bound.y + contentHeight / 2 + iconSize);
      }
    }
  }

  private drawPlayButton(ctx: CanvasRenderingContext2D, bound: Bound, contentHeight: number): void {
    const centerX = bound.x + bound.w / 2;
    const centerY = bound.y + contentHeight / 2;
    const buttonRadius = 30;

    // Semi-transparent background
    ctx.fillStyle = "rgba(0, 0, 0, 0.6)";
    ctx.beginPath();
    ctx.arc(centerX, centerY, buttonRadius, 0, Math.PI * 2);
    ctx.fill();

    // Play triangle
    ctx.fillStyle = "white";
    ctx.beginPath();
    ctx.moveTo(centerX - 10, centerY - 15);
    ctx.lineTo(centerX - 10, centerY + 15);
    ctx.lineTo(centerX + 15, centerY);
    ctx.closePath();
    ctx.fill();
  }

  private drawCaption(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    const captionY = bound.y + bound.h - EmbedElement.CAPTION_HEIGHT;

    ctx.fillStyle = theme.colors.text.secondary;
    ctx.font = `${12}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    const displayCaption = this.truncateText(ctx, this.caption, bound.w - 24);
    ctx.fillText(displayCaption, bound.x + bound.w / 2, captionY + EmbedElement.CAPTION_HEIGHT / 2);
  }

  private drawStatusOverlay(ctx: CanvasRenderingContext2D, bound: Bound, zoom: number, theme: YAPPCTheme): void {
    // Semi-transparent overlay
    ctx.fillStyle = "rgba(0, 0, 0, 0.5)";
    this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
    ctx.fill();

    if (zoom < 0.3) return;

    ctx.fillStyle = "white";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    if (this.loading === "loading") {
      ctx.font = `32px Arial`;
      ctx.fillText("⏳", bound.x + bound.w / 2, bound.y + bound.h / 2);
    } else if (this.loading === "error") {
      ctx.font = `32px Arial`;
      ctx.fillText("⚠️", bound.x + bound.w / 2, bound.y + bound.h / 2 - 16);

      if (this.errorMessage) {
        ctx.font = `${12}px ${theme.typography.fontFamily}`;
        ctx.fillText(this.errorMessage, bound.x + bound.w / 2, bound.y + bound.h / 2 + 20);
      }
    }
  }

  private getServiceIcon(): string {
    const icons: Record<EmbedType, string> = {
      youtube: "▶️",
      figma: "🎨",
      loom: "🎬",
      github: "🐙",
      twitter: "🐦",
      codepen: "💻",
      iframe: "🌐",
      bookmark: "🔗",
      html: "📝",
    };
    return icons[this.embedType] || "🔗";
  }

  private getDomainFromUrl(): string {
    try {
      const url = new URL(this.url);
      return url.hostname.replace("www.", "");
    } catch {
      return this.url;
    }
  }

  private isVideoEmbed(): boolean {
    return ["youtube", "loom"].includes(this.embedType);
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

  private wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
    const words = text.split(" ");
    const lines: string[] = [];
    let currentLine = "";

    for (const word of words) {
      const testLine = currentLine ? `${currentLine} ${word}` : word;
      if (ctx.measureText(testLine).width > maxWidth) {
        if (currentLine) {
          lines.push(currentLine);
          if (lines.length >= maxLines) {
            const lastLine = lines[lines.length - 1];
            lines[lines.length - 1] = this.truncateText(ctx, lastLine + " " + word, maxWidth);
            return lines;
          }
          currentLine = word;
        } else {
          lines.push(this.truncateText(ctx, word, maxWidth));
          if (lines.length >= maxLines) return lines;
        }
      } else {
        currentLine = testLine;
      }
    }

    if (currentLine && lines.length < maxLines) {
      lines.push(currentLine);
    }

    return lines;
  }

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Public API methods

  /**
   * Update URL and reload
   */
  async setUrl(url: string): Promise<void> {
    this.url = url;
    this._thumbnailCache = null;
    this.loading = "loading";
    await this.loadThumbnail();
  }

  /**
   * Set metadata (for bookmarks)
   */
  setMetadata(metadata: EmbedMetadata): void {
    this.metadata = metadata;
    if (metadata.image) {
      this.loadThumbnail();
    }
  }

  /**
   * Get the HTML for iframe embedding
   */
  getIframeHtml(): string {
    const bound = this.getBounds();
    return `<iframe 
      src="${this.embedUrl}" 
      width="${bound.w}" 
      height="${bound.h}" 
      frameborder="0" 
      allowfullscreen
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
    ></iframe>`;
  }

  /**
   * Static factory methods for common embed types
   */
  static createYouTubeEmbed(props: Omit<EmbedProps, "embedType">): EmbedElement {
    return new EmbedElement({ ...props, embedType: "youtube" });
  }

  static createFigmaEmbed(props: Omit<EmbedProps, "embedType">): EmbedElement {
    return new EmbedElement({ ...props, embedType: "figma" });
  }

  static createBookmark(props: Omit<EmbedProps, "embedType">): EmbedElement {
    return new EmbedElement({ ...props, embedType: "bookmark" });
  }
}
