/**
 * Audio Element - Native HTML5 audio visualizer rendered within the canvas
 *
 * @doc.type class
 * @doc.purpose Canvas element for displaying and controlling audio content
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Supports:
 * - Local audio files and HTTP(S) URLs (mp3, ogg, wav, flac, aac)
 * - Waveform visualization (real-time via Web Audio API)
 * - Playback controls overlay
 * - Podcast/transcript metadata
 * - Loop, autoplay, muted
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface AudioProps extends BaseElementProps {
  /** Audio source URL or data URL (empty string = placeholder) */
  src?: string;
  /** Display title */
  title?: string;
  /** Artist / speaker */
  artist?: string;
  /** Thumbnail / cover art URL */
  artworkUrl?: string;
  /** Whether to auto-play on load */
  autoplay?: boolean;
  /** Loop the audio */
  loop?: boolean;
  /** Start at this offset (seconds) */
  startTime?: number;
  /** Show full waveform visualizer */
  showWaveform?: boolean;
  /** Background fill color */
  backgroundColor?: string;
  /** Waveform bar color */
  waveformColor?: string;
  /** Accent color (progress, controls) */
  accentColor?: string;
}

/**
 * AudioElement — renders a styled audio player with optional waveform within the canvas.
 */
export class AudioElement extends CanvasElement {
  public src: string;
  public title: string | undefined;
  public artist: string | undefined;
  public artworkUrl: string | undefined;
  public autoplay: boolean;
  public loop: boolean;
  public startTime: number;
  public showWaveform: boolean;
  public backgroundColor: string;
  public waveformColor: string;
  public accentColor: string;

  private _audioEl: HTMLAudioElement | null = null;
  private _audioCtx: AudioContext | null = null;
  private _analyser: AnalyserNode | null = null;
  private _source: MediaElementAudioSourceNode | null = null;
  private _frequencyData: Uint8Array<ArrayBuffer> | null = null;
  private _ready: boolean = false;
  private _artworkImg: HTMLImageElement | null = null;

  /** Whether audio is currently playing */
  get isPlaying(): boolean {
    return this._audioEl !== null && !this._audioEl.paused;
  }

  /** Current playback position (seconds) */
  get currentTime(): number {
    return this._audioEl?.currentTime ?? 0;
  }

  constructor(props: AudioProps) {
    super(props);
    this.src = props.src ?? "";
    this.title = props.title;
    this.artist = props.artist;
    this.artworkUrl = props.artworkUrl;
    this.autoplay = props.autoplay ?? false;
    this.loop = props.loop ?? false;
    this.startTime = props.startTime ?? 0;
    this.showWaveform = props.showWaveform ?? true;
    this.backgroundColor = props.backgroundColor ?? "#1e1e2e";
    this.waveformColor = props.waveformColor ?? "rgba(99,179,237,0.6)";
    this.accentColor = props.accentColor ?? "#63b3ed";
  }

  get type(): CanvasElementType {
    return "audio";
  }

  // ---------------------------------------------------------------------------
  // Lazy initialisation
  // ---------------------------------------------------------------------------

  private _initAudio(): void {
    if (this._audioEl) return;
    const a = document.createElement("audio");
    a.src = this.src;
    a.loop = this.loop;
    a.currentTime = this.startTime;
    a.addEventListener("canplay", () => { this._ready = true; });
    if (this.autoplay) void a.play();
    this._audioEl = a;

    // Set up Web Audio API for waveform
    if (this.showWaveform) {
      try {
        this._audioCtx = new AudioContext();
        this._analyser = this._audioCtx.createAnalyser();
        this._analyser.fftSize = 128;
        this._source = this._audioCtx.createMediaElementSource(a);
        this._source.connect(this._analyser);
        this._analyser.connect(this._audioCtx.destination);
        this._frequencyData = new Uint8Array(this._analyser.frequencyBinCount);
      } catch {
        // Web Audio not available in all environments
        this._analyser = null;
      }
    }

    if (this.artworkUrl) {
      const img = new Image();
      img.src = this.artworkUrl;
      img.addEventListener("load", () => { this._artworkImg = img; });
    }
  }

  // ---------------------------------------------------------------------------
  // Controls API
  // ---------------------------------------------------------------------------

  play(): void {
    this._initAudio();
    if (this._audioCtx?.state === "suspended") void this._audioCtx.resume();
    void this._audioEl?.play();
  }

  pause(): void { this._audioEl?.pause(); }
  seek(seconds: number): void { if (this._audioEl) this._audioEl.currentTime = seconds; }
  get duration(): number { return this._audioEl?.duration ?? 0; }
  get paused(): boolean { return this._audioEl?.paused ?? true; }

  // ---------------------------------------------------------------------------
  // Rendering
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    this._initAudio();
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    this._drawBackground(ctx, b);

    if (this._artworkImg) {
      this._drawArtwork(ctx, b);
    }

    this._drawMetadata(ctx, b, zoom);
    this._drawWaveform(ctx, b);
    this._drawControls(ctx, b);

    ctx.restore();
  }

  private _drawBackground(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    ctx.fillStyle = this.backgroundColor;
    ctx.beginPath();
    const r = 8;
    ctx.moveTo(b.x + r, b.y);
    ctx.lineTo(b.x + b.w - r, b.y);
    ctx.arcTo(b.x + b.w, b.y, b.x + b.w, b.y + r, r);
    ctx.lineTo(b.x + b.w, b.y + b.h - r);
    ctx.arcTo(b.x + b.w, b.y + b.h, b.x + b.w - r, b.y + b.h, r);
    ctx.lineTo(b.x + r, b.y + b.h);
    ctx.arcTo(b.x, b.y + b.h, b.x, b.y + b.h - r, r);
    ctx.lineTo(b.x, b.y + r);
    ctx.arcTo(b.x, b.y, b.x + r, b.y, r);
    ctx.closePath();
    ctx.fill();
  }

  private _drawArtwork(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    const size = Math.min(b.h - 16, 48);
    ctx.drawImage(this._artworkImg!, b.x + 8, b.y + (b.h - size) / 2, size, size);
  }

  private _drawMetadata(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
    zoom: number,
  ): void {
    if (zoom < 0.4) return;
    const xOff = this._artworkImg ? 64 : 12;
    ctx.fillStyle = "#ffffff";
    ctx.font = `bold ${Math.min(13, b.h * 0.18)}px sans-serif`;
    ctx.textBaseline = "top";
    if (this.title) ctx.fillText(this.title, b.x + xOff, b.y + 8, b.w - xOff - 8);
    if (this.artist) {
      ctx.fillStyle = "rgba(255,255,255,0.6)";
      ctx.font = `${Math.min(11, b.h * 0.14)}px sans-serif`;
      ctx.fillText(this.artist, b.x + xOff, b.y + 24, b.w - xOff - 8);
    }
  }

  private _drawWaveform(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    const wY = b.y + b.h * 0.5;
    const wH = b.h * 0.3;

    if (this._analyser && this._frequencyData && !this.paused) {
      // Live frequency bars
      this._analyser.getByteFrequencyData(this._frequencyData);
      const bars = this._frequencyData.length;
      const barW = b.w / bars;
      ctx.fillStyle = this.waveformColor;
      for (let i = 0; i < bars; i++) {
        const barH = (this._frequencyData[i]! / 255) * wH;
        ctx.fillRect(b.x + i * barW, wY - barH / 2, Math.max(1, barW - 1), barH);
      }
    } else {
      // Static waveform placeholder
      ctx.strokeStyle = this.waveformColor;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      const steps = 60;
      for (let i = 0; i <= steps; i++) {
        const xPos = b.x + (i / steps) * b.w;
        const amp = Math.sin(i * 0.5) * wH * 0.5;
        if (i === 0) ctx.moveTo(xPos, wY + amp);
        else ctx.lineTo(xPos, wY + amp);
      }
      ctx.stroke();
    }

    // Progress overlay
    const duration = this._audioEl?.duration ?? 1;
    const progress = (this._audioEl?.currentTime ?? 0) / duration;
    const pbY = b.y + b.h - 10;
    ctx.fillStyle = "rgba(255,255,255,0.15)";
    ctx.fillRect(b.x + 8, pbY, b.w - 16, 3);
    ctx.fillStyle = this.accentColor;
    ctx.fillRect(b.x + 8, pbY, (b.w - 16) * progress, 3);
  }

  private _drawControls(
    ctx: CanvasRenderingContext2D,
    b: { x: number; y: number; w: number; h: number },
  ): void {
    const isPaused = this._audioEl?.paused ?? true;
    ctx.fillStyle = this.accentColor;
    ctx.font = `${Math.min(18, b.h * 0.22)}px sans-serif`;
    ctx.textBaseline = "middle";
    ctx.textAlign = "center";
    ctx.fillText(isPaused ? "▶" : "⏸", b.x + b.w - 20, b.y + b.h / 2);
    ctx.textAlign = "left";
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }

  dispose(): void {
    this._audioEl?.pause();
    this._audioEl?.removeAttribute("src");
    this._source?.disconnect();
    void this._audioCtx?.close();
    this._audioEl = null;
    this._audioCtx = null;
    this._analyser = null;
    this._source = null;
  }
}
