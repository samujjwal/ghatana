/**
 * Bookmark Canvas Element
 * 
 * @doc.type class
 * @doc.purpose Renders URL bookmarks with preview cards on canvas
 * @doc.layer core
 * @doc.pattern ValueObject
 * 
 * Features:
 * - URL preview with metadata
 * - Thumbnail/favicon display
 * - Title and description
 * - Multiple display modes (card, compact, link)
 * - Open Graph metadata support
 */

import { CanvasElement } from "./base.js";
import type { BaseElementProps, CanvasElementType } from "../types/index.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

/**
 * Bookmark display mode
 */
export type BookmarkDisplayMode = "card" | "compact" | "link" | "thumbnail";

/**
 * Bookmark metadata from URL
 */
export interface BookmarkMetadata {
  /** Page title */
  title?: string;
  /** Page description */
  description?: string;
  /** Favicon URL */
  favicon?: string;
  /** Thumbnail/og:image URL */
  thumbnail?: string;
  /** Site name */
  siteName?: string;
  /** Author */
  author?: string;
  /** Publication date */
  publishedDate?: string;
}

/**
 * Bookmark element properties
 */
export interface BookmarkElementProps extends BaseElementProps {
  /** URL to bookmark */
  url: string;
  /** Display mode */
  displayMode?: BookmarkDisplayMode;
  /** Metadata (fetched or provided) */
  metadata?: BookmarkMetadata;
  /** Override title */
  customTitle?: string;
  /** Override description */
  customDescription?: string;
  /** Background color */
  backgroundColor?: string;
  /** Border color */
  borderColor?: string;
  /** Border radius */
  borderRadius?: number;
  /** Show thumbnail */
  showThumbnail?: boolean;
  /** Show favicon */
  showFavicon?: boolean;
  /** Show description */
  showDescription?: boolean;
  /** Max description lines */
  maxDescriptionLines?: number;
  /** Loading state */
  isLoading?: boolean;
  /** Error message */
  error?: string;
  /** Hover effect */
  hoverHighlight?: boolean;
}

/**
 * Extract domain from URL
 */
function extractDomain(url: string): string {
  try {
    const parsed = new URL(url);
    return parsed.hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

/**
 * Bookmark Canvas Element
 */
export class BookmarkElement extends CanvasElement {
  url: string;
  displayMode: BookmarkDisplayMode;
  metadata: BookmarkMetadata;
  customTitle: string;
  customDescription: string;
  backgroundColor: string;
  borderColor: string;
  borderRadius: number;
  showThumbnail: boolean;
  showFavicon: boolean;
  showDescription: boolean;
  maxDescriptionLines: number;
  isLoading: boolean;
  error: string;
  hoverHighlight: boolean;

  // Cached images
  private thumbnailImage: HTMLImageElement | null = null;
  private faviconImage: HTMLImageElement | null = null;
  private imagesLoaded: boolean = false;

  constructor(props: BookmarkElementProps) {
    super(props);
    this.url = props.url || "";
    this.displayMode = props.displayMode || "card";
    this.metadata = props.metadata || {};
    this.customTitle = props.customTitle || "";
    this.customDescription = props.customDescription || "";
    this.backgroundColor = props.backgroundColor || themeManager.getTheme().colors.surface;
    this.borderColor = props.borderColor || themeManager.getTheme().colors.border.light;
    this.borderRadius = props.borderRadius || 8;
    this.showThumbnail = props.showThumbnail !== false;
    this.showFavicon = props.showFavicon !== false;
    this.showDescription = props.showDescription !== false;
    this.maxDescriptionLines = props.maxDescriptionLines || 2;
    this.isLoading = props.isLoading || false;
    this.error = props.error || "";
    this.hoverHighlight = props.hoverHighlight !== false;

    // Load images
    this.loadImages();
  }

  get type(): CanvasElementType {
    return "bookmark";
  }

  /**
   * Load thumbnail and favicon images
   */
  private loadImages(): void {
    if (typeof window === "undefined") return;

    // Load thumbnail
    if (this.metadata.thumbnail && this.showThumbnail) {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        this.thumbnailImage = img;
        this.imagesLoaded = true;
      };
      img.onerror = () => {
        this.thumbnailImage = null;
      };
      img.src = this.metadata.thumbnail;
    }

    // Load favicon
    if (this.metadata.favicon && this.showFavicon) {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        this.faviconImage = img;
      };
      img.onerror = () => {
        // Use fallback favicon service
        const domain = extractDomain(this.url);
        const fallbackImg = new Image();
        fallbackImg.crossOrigin = "anonymous";
        fallbackImg.onload = () => {
          this.faviconImage = fallbackImg;
        };
        fallbackImg.src = `https://www.google.com/s2/favicons?domain=${domain}&sz=32`;
      };
      img.src = this.metadata.favicon;
    } else if (this.showFavicon && this.url) {
      // Use Google favicon service as default
      const domain = extractDomain(this.url);
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => {
        this.faviconImage = img;
      };
      img.src = `https://www.google.com/s2/favicons?domain=${domain}&sz=32`;
    }
  }

  /**
   * Get effective title
   */
  private getTitle(): string {
    return this.customTitle || this.metadata.title || extractDomain(this.url);
  }

  /**
   * Get effective description
   */
  private getDescription(): string {
    return this.customDescription || this.metadata.description || "";
  }

  render(ctx: CanvasRenderingContext2D, _viewport: unknown): void {
    const bounds = this.getBounds();
    const { x, y, w, h } = bounds;

    switch (this.displayMode) {
      case "card":
        this.renderCard(ctx, x, y, w, h);
        break;
      case "compact":
        this.renderCompact(ctx, x, y, w, h);
        break;
      case "link":
        this.renderLink(ctx, x, y, w, h);
        break;
      case "thumbnail":
        this.renderThumbnail(ctx, x, y, w, h);
        break;
    }
  }

  /**
   * Render card mode (full preview)
   */
  private renderCard(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    const theme = themeManager.getTheme();
    const padding = 16;
    const thumbnailHeight = this.showThumbnail && this.thumbnailImage ? 140 : 0;

    // Background
    ctx.fillStyle = this.backgroundColor;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.fill();

    // Border
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.stroke();

    // Thumbnail
    if (this.showThumbnail && this.thumbnailImage) {
      ctx.save();
      this.roundRect(ctx, x, y, w, thumbnailHeight, this.borderRadius);
      ctx.clip();
      
      // Draw image covering the area
      const imgRatio = this.thumbnailImage.width / this.thumbnailImage.height;
      const areaRatio = w / thumbnailHeight;
      
      let drawW = w;
      let drawH = thumbnailHeight;
      let drawX = x;
      let drawY = y;
      
      if (imgRatio > areaRatio) {
        drawH = w / imgRatio;
        drawY = y + (thumbnailHeight - drawH) / 2;
      } else {
        drawW = thumbnailHeight * imgRatio;
        drawX = x + (w - drawW) / 2;
      }
      
      ctx.drawImage(this.thumbnailImage, drawX, drawY, drawW, drawH);
      ctx.restore();
    }

    // Content area
    let contentY = y + thumbnailHeight + padding;

    // Favicon and site name
    if (this.showFavicon) {
      const faviconSize = 16;
      if (this.faviconImage) {
        ctx.drawImage(this.faviconImage, x + padding, contentY, faviconSize, faviconSize);
      } else {
        // Draw placeholder
        ctx.fillStyle = theme.colors.border.light;
        ctx.fillRect(x + padding, contentY, faviconSize, faviconSize);
      }

      // Site name
      ctx.fillStyle = theme.colors.text.muted;
      ctx.font = "12px Inter, -apple-system, sans-serif";
      ctx.textBaseline = "middle";
      const siteName = this.metadata.siteName || extractDomain(this.url);
      ctx.fillText(siteName, x + padding + faviconSize + 8, contentY + faviconSize / 2);
      
      contentY += faviconSize + 12;
    }

    // Title
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = "bold 16px Inter, -apple-system, sans-serif";
    ctx.textBaseline = "top";
    const title = this.truncateText(ctx, this.getTitle(), w - padding * 2);
    ctx.fillText(title, x + padding, contentY);
    contentY += 24;

    // Description
    if (this.showDescription && this.getDescription()) {
      ctx.fillStyle = theme.colors.text.muted;
      ctx.font = "14px Inter, -apple-system, sans-serif";
      const desc = this.truncateText(ctx, this.getDescription(), w - padding * 2, this.maxDescriptionLines);
      
      const lines = desc.split("\n");
      for (const line of lines) {
        ctx.fillText(line, x + padding, contentY);
        contentY += 20;
      }
    }

    // Loading state
    if (this.isLoading) {
      this.renderLoadingOverlay(ctx, x, y, w, h);
    }

    // Error state
    if (this.error) {
      this.renderErrorOverlay(ctx, x, y, w, h);
    }
  }

  /**
   * Render compact mode (single line)
   */
  private renderCompact(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    const theme = themeManager.getTheme();
    const padding = 12;

    // Background
    ctx.fillStyle = this.backgroundColor;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.fill();

    // Border
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.stroke();

    // Favicon
    let contentX = x + padding;
    if (this.showFavicon && this.faviconImage) {
      const faviconSize = 20;
      ctx.drawImage(this.faviconImage, contentX, y + (h - faviconSize) / 2, faviconSize, faviconSize);
      contentX += faviconSize + 10;
    }

    // Title
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = "14px Inter, -apple-system, sans-serif";
    ctx.textBaseline = "middle";
    const title = this.truncateText(ctx, this.getTitle(), w - (contentX - x) - padding);
    ctx.fillText(title, contentX, y + h / 2);
  }

  /**
   * Render link mode (simple link text)
   */
  private renderLink(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    const theme = themeManager.getTheme();

    ctx.fillStyle = theme.colors.primary;
    ctx.font = "14px Inter, -apple-system, sans-serif";
    ctx.textBaseline = "middle";
    ctx.textAlign = "left";

    const title = this.truncateText(ctx, this.getTitle(), w);
    ctx.fillText(title, x, y + h / 2);

    // Underline
    const textWidth = ctx.measureText(title).width;
    ctx.strokeStyle = theme.colors.primary;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x, y + h / 2 + 8);
    ctx.lineTo(x + textWidth, y + h / 2 + 8);
    ctx.stroke();
  }

  /**
   * Render thumbnail mode (image only)
   */
  private renderThumbnail(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    const theme = themeManager.getTheme();

    // Background
    ctx.fillStyle = theme.colors.surface;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.fill();

    // Thumbnail
    if (this.thumbnailImage) {
      ctx.save();
      this.roundRect(ctx, x, y, w, h, this.borderRadius);
      ctx.clip();
      ctx.drawImage(this.thumbnailImage, x, y, w, h);
      ctx.restore();
    } else {
      // Placeholder
      ctx.fillStyle = theme.colors.text.muted;
      ctx.font = "32px serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("🔗", x + w / 2, y + h / 2);
    }

    // Border
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1;
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.stroke();
  }

  /**
   * Render loading overlay
   */
  private renderLoadingOverlay(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    ctx.fillStyle = "rgba(255, 255, 255, 0.8)";
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.fill();

    ctx.fillStyle = themeManager.getTheme().colors.text.muted;
    ctx.font = "14px Inter, -apple-system, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("Loading...", x + w / 2, y + h / 2);
  }

  /**
   * Render error overlay
   */
  private renderErrorOverlay(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number
  ): void {
    ctx.fillStyle = "rgba(254, 226, 226, 0.9)";
    this.roundRect(ctx, x, y, w, h, this.borderRadius);
    ctx.fill();

    ctx.fillStyle = "#dc2626";
    ctx.font = "14px Inter, -apple-system, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(this.error || "Failed to load", x + w / 2, y + h / 2);
  }

  /**
   * Truncate text to fit width
   */
  private truncateText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number = 1): string {
    if (maxLines === 1) {
      if (ctx.measureText(text).width <= maxWidth) {
        return text;
      }
      
      let truncated = text;
      while (ctx.measureText(truncated + "...").width > maxWidth && truncated.length > 0) {
        truncated = truncated.slice(0, -1);
      }
      return truncated + "...";
    }

    // Multi-line truncation
    const words = text.split(" ");
    const lines: string[] = [];
    let currentLine = "";

    for (const word of words) {
      const testLine = currentLine ? `${currentLine} ${word}` : word;
      if (ctx.measureText(testLine).width > maxWidth) {
        if (currentLine) {
          lines.push(currentLine);
          currentLine = word;
        } else {
          lines.push(this.truncateText(ctx, word, maxWidth, 1));
          currentLine = "";
        }
      } else {
        currentLine = testLine;
      }

      if (lines.length >= maxLines) {
        break;
      }
    }

    if (currentLine && lines.length < maxLines) {
      lines.push(currentLine);
    }

    return lines.join("\n");
  }

  /**
   * Helper to draw rounded rectangles
   */
  private roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  includesPoint(px: number, py: number): boolean {
    const bounds = this.getBounds();
    return (
      px >= bounds.x &&
      px <= bounds.x + bounds.w &&
      py >= bounds.y &&
      py <= bounds.y + bounds.h
    );
  }

  /**
   * Update metadata
   */
  setMetadata(metadata: BookmarkMetadata): void {
    this.metadata = { ...this.metadata, ...metadata };
    this.imagesLoaded = false;
    this.thumbnailImage = null;
    this.faviconImage = null;
    this.loadImages();
  }

  /**
   * Convert to Markdown link
   */
  toMarkdown(): string {
    const title = this.getTitle();
    return `[${title}](${this.url})`;
  }

  /**
   * Convert to HTML anchor
   */
  toHtml(): string {
    const title = this.getTitle();
    return `<a href="${this.url}" target="_blank" rel="noopener noreferrer">${title}</a>`;
  }
}
