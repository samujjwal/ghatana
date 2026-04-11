/**
 * Code Editor Element — Monaco-powered code editor embedded in the canvas.
 *
 * @doc.type class
 * @doc.purpose Canvas element that hosts a full interactive code editor
 * @doc.layer elements
 * @doc.pattern Element
 *
 * Use-cases:
 * - Code snippet editing in YAPPC (developer persona canvas)
 * - Inline script for AEP agent configuration
 * - Data transformation expression editor in Data Cloud
 *
 * Architecture:
 * Like `LiveReactElement`, the 2D canvas just draws a placeholder frame.
 * The actual Monaco / CodeMirror editor is rendered in a DOM overlay
 * by `CodeEditorOverlay` (see src/react/CodeEditorOverlay.tsx).
 * Language, theme, and code are synced via the element's public API.
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export type CodeEditorLanguage =
  | "typescript"
  | "javascript"
  | "python"
  | "java"
  | "rust"
  | "go"
  | "sql"
  | "json"
  | "yaml"
  | "markdown"
  | "html"
  | "css"
  | "shell"
  | "graphql"
  | "proto"
  | "plaintext";

export type CodeEditorTheme =
  | "vs-dark"
  | "vs-light"
  | "hc-dark"
  | "hc-light"
  | "github-dark"
  | "github-light"
  | "dracula"
  | "monokai";

export interface CodeEditorRunConfig {
  /** Whether execution is supported for this language */
  runnable: boolean;
  /**
   * Endpoint to POST `{ code, language }` for execution.
   * Provided by the product — NOT called by the platform itself.
   */
  executeEndpoint?: string;
  /** Timeout for execution (ms) */
  timeoutMs?: number;
}

export interface CodeEditorDiagnostic {
  line: number;
  column: number;
  message: string;
  severity: "error" | "warning" | "info" | "hint";
  source?: string;
}

export interface CodeEditorProps extends BaseElementProps {
  /** Initial code content */
  code: string;
  /** Programming / markup language */
  language?: CodeEditorLanguage;
  /** Editor color theme */
  theme?: CodeEditorTheme;
  /** Font size (px) */
  fontSize?: number;
  /** Show line numbers */
  lineNumbers?: boolean;
  /** Enable code minimap */
  minimap?: boolean;
  /** Enable word wrap */
  wordWrap?: boolean;
  /** Read-only mode */
  readOnly?: boolean;
  /** Show run button and execution config */
  runConfig?: CodeEditorRunConfig;
  /** External diagnostics to display (from LSP, CI, etc.) */
  diagnostics?: CodeEditorDiagnostic[];
  /** Output pane content (stdout / stderr from last run) */
  output?: string;
  /** Whether the output pane is visible */
  showOutput?: boolean;
  /** Tab size in spaces */
  tabSize?: number;
}

export class CodeEditorElement extends CanvasElement {
  public code: string;
  public language: CodeEditorLanguage;
  public theme: CodeEditorTheme;
  public fontSize: number;
  public lineNumbers: boolean;
  public minimap: boolean;
  public wordWrap: boolean;
  public readOnly: boolean;
  public runConfig: CodeEditorRunConfig;
  public diagnostics: CodeEditorDiagnostic[];
  public output: string;
  public showOutput: boolean;
  public tabSize: number;

  constructor(props: CodeEditorProps) {
    super(props);
    this.code = props.code ?? "";
    this.language = props.language ?? "typescript";
    this.theme = props.theme ?? "vs-dark";
    this.fontSize = props.fontSize ?? 13;
    this.lineNumbers = props.lineNumbers ?? true;
    this.minimap = props.minimap ?? false;
    this.wordWrap = props.wordWrap ?? false;
    this.readOnly = props.readOnly ?? false;
    this.runConfig = props.runConfig ?? { runnable: false };
    this.diagnostics = props.diagnostics ?? [];
    this.output = props.output ?? "";
    this.showOutput = props.showOutput ?? false;
    this.tabSize = props.tabSize ?? 2;
  }

  get type(): CanvasElementType {
    return "code-editor";
  }

  /** Update the code content programmatically */
  setCode(code: string): void {
    this.code = code;
  }

  /** Set diagnostics from external language service */
  setDiagnostics(diagnostics: CodeEditorDiagnostic[]): void {
    this.diagnostics = diagnostics;
  }

  /** Set output pane content */
  setOutput(output: string, show?: boolean): void {
    this.output = output;
    if (show !== undefined) this.showOutput = show;
  }

  // ---------------------------------------------------------------------------
  // 2D canvas placeholder (actual editor is a DOM overlay)
  // ---------------------------------------------------------------------------

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const b = this.getBounds();
    const headerH = 32;
    const isDark = this.theme.includes("dark") || this.theme === "dracula" || this.theme === "monokai";
    const bgColor = isDark ? "#1e1e1e" : "#fffffe";
    const headerBg = isDark ? "#252526" : "#f3f3f3";
    const textColor = isDark ? "#d4d4d4" : "#333333";

    // Body
    ctx.fillStyle = bgColor;
    ctx.fillRect(b.x, b.y, b.w, b.h);

    // Header bar
    ctx.fillStyle = headerBg;
    ctx.fillRect(b.x, b.y, b.w, headerH);

    if (zoom > 0.3) {
      // Language badge
      ctx.fillStyle = "#007acc";
      const badgeW = Math.min(70, b.w * 0.2);
      ctx.fillRect(b.x + 8, b.y + (headerH - 16) / 2, badgeW, 16);
      ctx.fillStyle = "#ffffff";
      ctx.font = `bold ${Math.min(10, headerH * 0.35)}px monospace`;
      ctx.textBaseline = "middle";
      ctx.fillText(this.language, b.x + 11, b.y + headerH / 2, badgeW - 6);

      // File indicator
      ctx.fillStyle = textColor;
      ctx.textBaseline = "middle";
      const errCount = this.diagnostics.filter((d) => d.severity === "error").length;
      const warnCount = this.diagnostics.filter((d) => d.severity === "warning").length;
      if (errCount > 0) {
        ctx.fillStyle = "#f48771";
        ctx.fillText(`✗ ${errCount}`, b.x + b.w - 60, b.y + headerH / 2);
      }
      if (warnCount > 0) {
        ctx.fillStyle = "#cca700";
        ctx.fillText(`⚠ ${warnCount}`, b.x + b.w - 30, b.y + headerH / 2);
      }

      if (this.runConfig.runnable) {
        ctx.fillStyle = "#4ade80";
        ctx.fillText("▶ Run", b.x + b.w - 100, b.y + headerH / 2);
      }
    }

    // Code preview (low-fidelity text rendering at small sizes)
    if (zoom > 0.4) {
      const lines = this.code.split("\n").slice(0, 20);
      ctx.font = `${Math.min(this.fontSize, 11)}px 'Consolas', 'Monaco', monospace`;
      ctx.fillStyle = textColor;
      ctx.textBaseline = "top";
      const lineH = (this.fontSize + 4) * Math.min(1, zoom);
      const maxLines = Math.floor((b.h - headerH - 8) / lineH);
      for (let i = 0; i < Math.min(lines.length, maxLines); i++) {
        ctx.fillText(lines[i] ?? "", b.x + 8, b.y + headerH + 4 + i * lineH, b.w - 16);
      }
    } else {
      // Bird's-eye: just show language icon
      ctx.fillStyle = textColor;
      ctx.font = `${Math.min(24, b.h * 0.3)}px monospace`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText("< />", b.x + b.w / 2, b.y + b.h / 2);
      ctx.textAlign = "left";
    }

    // Outer border
    ctx.strokeStyle = isDark ? "#3c3c3c" : "#cccccc";
    ctx.lineWidth = 1 / zoom;
    ctx.strokeRect(b.x, b.y, b.w, b.h);

    ctx.restore();
  }

  includesPoint(x: number, y: number, _opts?: PointTestOptions): boolean {
    return this.getBounds().containsPoint({ x, y });
  }
}
