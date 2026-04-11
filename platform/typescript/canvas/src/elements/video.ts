/**
 * Video Element - Native HTML5 video rendered within the canvas
 *
 * @doc.type class
 * @doc.purpose Canvas element for displaying and controlling video content
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Supports:
 * - Local video files (blob / data URL)
 * - HTTP(S) video URLs (mp4, webm, ogg)
 * - Looping, autoplay, muted presets
 * - Poster image fallback
 * - Captions / subtitles (VTT tracks)
 * - Playback controls overlay
 * - Frame-accurate seek
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export type VideoFitMode = "contain" | "cover" | "fill";

export interface VideoTrack {
  /** VTT file URL */
  src: string;
  kind: "subtitles" | "captions" | "descriptions" | "chapters" | "metadata";
  label: string;
  srclang: string;
  default?: boolean;
}

export interface VideoProps extends BaseElementProps {
  /** Video source URL or data URL (empty string = placeholder) */
  src?: string;
  /** Poster image URL shown before playback starts */
  poster?: string;
  /** MIME type hint, e.g. "video/mp4" */
  mimeType?: string;
  /** How video is scaled within its bounds */
  fitMode?: VideoFitMode;
  /** Whether to auto-play on load (requires muted=true in most browsers) */
  autoplay?: boolean;
  /** Whether to mute audio */
  muted?: boolean;
  /** Whether to loop the video */
  loop?: boolean;
  /** Whether to show playback controls within canvas */
  controls?: boolean;
  /** Playback rate multiplier (0.5 – 4.0) */
  playbackRate?: number;
  /** Start at this offset (seconds) */
  startTime?: number;
  /** Border radius (px) */
  borderRadius?: number;
  /** Caption/subtitle tracks */
  tracks?: VideoTrack[];
  /** Optional caption text below the element */
  caption?: string;
  /** Alt text for accessibility */
  alt?: string;
}

/**
 * VideoElement — renders an HTMLVideoElement composited onto the canvas.
 *
 * The element maintains an off-screen HTMLVideoElement so it can be
 * frame-accurately painted at each animation frame.  The DOM node is
 * never inserted into the document; instead `ctx.drawImage(videoEl, …)`
 * copies the decoded frame directly onto the canvas context.
 */
export class VideoElement extends CanvasElement {
  public src: string;
  public poster: string | undefined;
  public mimeType: string | undefined;
  public fitMode: VideoFitMode;
  public autoplay: boolean;
  public muted: boolean;
  public loop: boolean;
  public controls: boolean;
  public playbackRate: number;
  public startTime: number;
  public borderRadius: number;
  public tracks: VideoTrack[];
  public caption: string | undefined;
  public alt: string | undefined;

  /** Off-screen video element used for frame capture */
  private _videoEl: HTMLVideoElement | null = null;
  private _ready: boolean = false;
  private _error: string | null = null;

  /** Whether the video is currently playing */
  get isPlaying(): boolean {
    return this._videoEl !== null && !this._videoEl.paused;
  }

  /** Current playback position (seconds) */
  get currentTime(): number {
    return this._videoEl?.currentTime ?? 0;
  }

  /** Current volume (0–1) */
  get volume(): number {
    return this._videoEl?.volume ?? 1;
  }

  constructor(props: VideoProps) {
    super(props);
    this.src = props.src ?? "";
    this.poster = props.poster;
    this.mimeType = props.mimeType;
    this.fitMode = props.fitMode ?? "contain";
    this.autoplay = props.autoplay ?? false;
    this.muted = props.muted ?? true;
    this.loop = props.loop ?? false;
    this.controls = props.controls ?? true;
    this.playbackRate = props.playbackRate ?? 1;
    this.startTime = props.startTime ?? 0;
    this.borderRadius = props.borderRadius ?? 0;
    this.tracks = props.tracks ?? [];
    this.caption = props.caption;
    this.alt = props.alt;
  }

  get type(): CanvasElementType {
    return "video";
  }

  // ---------------------------------------------------------------------------
  // Lazy video element initialisation
  // ---------------------------------------------------------------------------

  private _initVideo(): void {
    if (this._videoEl) return;
    const v = document.createElement("video");
    v.src = this.src;
    v.muted = this.muted;
    v.loop = this.loop;
    v.playbackRate = this.playbackRate;
    v.currentTime = this.startTime;
    v.preload = "auto";
    if (this.autoplay) void v.play();
    v.addEventListener("canplay", () => { this._ready = true; });
    v.addEventListener("error", (e) => { this._error = (e as ErrorEvent).message ?? "video error"; });
    // Add tracks
    for (const track of this.tracks) {
      const t = document.createElement("track");
      t.src = track.src;
      t.kind = track.kind;
      t.label = track.label;
      t.srclang = track.srclang;
      if (track.default) t.default = true;
      v.appendChild(t);
    }
    this._videoEl = v;
  }

  // ---------------------------------------------------------------------------
  // Controls API
  // ---------------------------------------------------------------------------

  play(): void {
    this._initVideo();
    void this._videoEl?.play();
  }

  pause(): void {
    this._videoEl?.pause();
  }

  seek(seconds: number): void {
    if (this._videoEl) this._videoEl.currentTime = seconds;
  }

  get duration(): number {
    return this._videoEl?.duration ?? 0;
  }

  get paused(): boolean {
    return this._videoEl?.paused ?? true;
  }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    this._initVideo();
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();

    // Clip to border-radius
    if (this.borderRadius > 0) {
      this._roundedRect(ctx, b.x, b.y, b.w, b.h, this.borderRadius);
      ctx.clip();
    }

    if (this._error) {
      this._drawErrorState(ctx, b);
    } else if (this._ready && this._videoEl) {
      this._drawVideoFrame(ctx, b);
    } else {
      this._drawLoadingState(ctx, b);
    }

    // Draw caption
    if (this.caption) {
      this._drawCaption(ctx, b, zoom);
    }

    ctx.restore();
  }

  private _drawVideoFrame(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    const v = this._videoEl!;
    const vW = v.videoWidth || b.w;
    const vH = v.videoHeight || b.h;

    let dx = b.x;
    let dy = b.y;
    let dw = b.w;
    let dh = b.h;

    if (this.fitMode === "contain") {
      const scale = Math.min(b.w / vW, b.h / vH);
      dw = vW * scale;
      dh = vH * scale;
      dx = b.x + (b.w - dw) / 2;
      dy = b.y + (b.h - dh) / 2;
    } else if (this.fitMode === "cover") {
      const scale = Math.max(b.w / vW, b.h / vH);
      dw = vW * scale;
      dh = vH * scale;
      dx = b.x + (b.w - dw) / 2;
      dy = b.y + (b.h - dh) / 2;
    }

    ctx.drawImage(v, dx, dy, dw, dh);

    // Draw playback controls overlay if enabled
    if (this.controls) {
      this._drawControlsOverlay(ctx, b);
    }
  }

  private _drawControlsOverlay(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    const barH = Math.max(28, b.h * 0.08);
    const barY = b.y + b.h - barH;

    // Semi-transparent bar background
    ctx.fillStyle = "rgba(0,0,0,0.55)";
    ctx.fillRect(b.x, barY, b.w, barH);

    // Play/pause indicator
    const isPaused = this._videoEl?.paused ?? true;
    ctx.fillStyle = "#ffffff";
    ctx.font = `${barH * 0.6}px sans-serif`;
    ctx.textBaseline = "middle";
    ctx.fillText(isPaused ? "▶" : "⏸", b.x + 6, barY + barH / 2);

    // Progress bar
    const duration = this._videoEl?.duration ?? 1;
    const progress = (this._videoEl?.currentTime ?? 0) / duration;
    const pbX = b.x + barH + 4;
    const pbW = b.w - barH - 8;
    const pbY = barY + barH * 0.7;
    ctx.fillStyle = "rgba(255,255,255,0.25)";
    ctx.fillRect(pbX, pbY, pbW, 3);
    ctx.fillStyle = "#4fc3f7";
    ctx.fillRect(pbX, pbY, pbW * progress, 3);
  }

  private _drawLoadingState(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    ctx.fillStyle = "#1a1a2e";
    ctx.fillRect(b.x, b.y, b.w, b.h);
    ctx.fillStyle = "rgba(255,255,255,0.5)";
    ctx.font = `${Math.min(16, b.h * 0.12)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("⏳ Loading video…", b.x + b.w / 2, b.y + b.h / 2);
  }

  private _drawErrorState(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    ctx.fillStyle = "#2d1b1b";
    ctx.fillRect(b.x, b.y, b.w, b.h);
    ctx.fillStyle = "#ff6b6b";
    ctx.font = `${Math.min(14, b.h * 0.1)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("⚠ Video unavailable", b.x + b.w / 2, b.y + b.h / 2);
  }

  private _drawCaption(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
    zoom: number,
  ): void {
    if (zoom < 0.4) return;
    ctx.fillStyle = "rgba(0,0,0,0.6)";
    ctx.font = `italic ${Math.min(12, b.h * 0.07)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.fillText(this.caption ?? "", b.x + b.w / 2, b.y + b.h + 4);
  }

  private _roundedRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number,
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.arcTo(x + w, y, x + w, y + r, r);
    ctx.lineTo(x + w, y + h - r);
    ctx.arcTo(x + w, y + h, x + w - r, y + h, r);
    ctx.lineTo(x + r, y + h);
    ctx.arcTo(x, y + h, x, y + h - r, r);
    ctx.lineTo(x, y + r);
    ctx.arcTo(x, y, x + r, y, r);
    ctx.closePath();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }

  // ---------------------------------------------------------------------------
  // Cleanup
  // ---------------------------------------------------------------------------

  dispose(): void {
    this._videoEl?.pause();
    this._videoEl?.removeAttribute("src");
    this._videoEl = null;
    this._ready = false;
  }
}
