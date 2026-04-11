/**
 * CodeEditorOverlay — renders a Monaco-compatible code editor over a CodeEditorElement.
 *
 * @doc.type component
 * @doc.purpose DOM overlay rendering an interactive code editor in canvas space
 * @doc.layer react
 * @doc.pattern Overlay
 *
 * The host must provide a `editorFactory` prop — a function that instantiates the
 * editor UI into a container <div>.  This keeps the Monaco / CodeMirror dependencies
 * exclusively in the product layer while the platform just provides the architecture.
 *
 * Minimal usage with Monaco (in a product):
 * ```tsx
 * import * as monaco from "monaco-editor";
 *
 * const factory: EditorFactory = (container, opts) => {
 *   const ed = monaco.editor.create(container, {
 *     value: opts.initialCode,
 *     language: opts.language,
 *     theme: opts.theme === "vs-dark" ? "vs-dark" : "vs",
 *     fontSize: opts.fontSize,
 *     readOnly: opts.readOnly,
 *     minimap: { enabled: opts.minimap },
 *     lineNumbers: opts.lineNumbers ? "on" : "off",
 *   });
 *   ed.onDidChangeModelContent(() => opts.onChange(ed.getValue()));
 *   return () => ed.dispose();
 * };
 *
 * <CodeEditorOverlay element={el} screenBounds={...} editorFactory={factory} />
 * ```
 */

import React, { useEffect, useRef, useCallback } from "react";
import type { CodeEditorElement } from "../elements/code-editor.js";

// ---------------------------------------------------------------------------
// Editor factory contract (implemented by products)
// ---------------------------------------------------------------------------

export interface EditorFactoryOptions {
  initialCode: string;
  language: string;
  theme: string;
  fontSize: number;
  readOnly: boolean;
  minimap: boolean;
  lineNumbers: boolean;
  wordWrap: boolean;
  tabSize: number;
  onChange: (code: string) => void;
}

/** Returns a cleanup / dispose function */
export type EditorFactory = (
  container: HTMLDivElement,
  options: EditorFactoryOptions,
) => () => void;

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface CodeEditorOverlayProps {
  element: CodeEditorElement;
  screenBounds: { x: number; y: number; width: number; height: number };
  /** Product-provided editor factory (Monaco, CodeMirror, etc.) */
  editorFactory: EditorFactory;
  onCodeChange?: (elementId: string, code: string) => void;
  onRunRequest?: (elementId: string, code: string) => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

const HEADER_H = 32;

export const CodeEditorOverlay = React.memo(function CodeEditorOverlay({
  element,
  screenBounds,
  editorFactory,
  onCodeChange,
  onRunRequest,
}: CodeEditorOverlayProps): React.ReactElement {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const disposeRef = useRef<(() => void) | null>(null);
  const elementIdRef = useRef(element.id);

  useEffect(() => {
    if (!containerRef.current) return;

    const dispose = editorFactory(containerRef.current, {
      initialCode: element.code,
      language: element.language,
      theme: element.theme,
      fontSize: element.fontSize,
      readOnly: element.readOnly,
      minimap: element.minimap,
      lineNumbers: element.lineNumbers,
      wordWrap: element.wordWrap,
      tabSize: element.tabSize,
      onChange: (code: string) => {
        onCodeChange?.(elementIdRef.current, code);
      },
    });

    disposeRef.current = dispose;
    return () => {
      dispose();
      disposeRef.current = null;
    };
  // Mount once — subsequent prop changes are handled by the editor's own API
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleRun = useCallback(() => {
    onRunRequest?.(element.id, element.code);
  }, [element, onRunRequest]);

  const isDark =
    element.theme.includes("dark") ||
    element.theme === "dracula" ||
    element.theme === "monokai";
  const headerBg = isDark ? "#252526" : "#f3f3f3";
  const errorCount = element.diagnostics.filter((d) => d.severity === "error").length;
  const warnCount = element.diagnostics.filter((d) => d.severity === "warning").length;

  return (
    <div
      style={{
        position: "absolute",
        left: screenBounds.x,
        top: screenBounds.y,
        width: screenBounds.width,
        height: screenBounds.height,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        border: `1px solid ${isDark ? "#3c3c3c" : "#cccccc"}`,
        boxSizing: "border-box",
        pointerEvents: "auto",
      }}
    >
      {/* Header bar */}
      <div
        style={{
          height: HEADER_H,
          background: headerBg,
          display: "flex",
          alignItems: "center",
          padding: "0 8px",
          gap: 8,
          flexShrink: 0,
          userSelect: "none",
          fontSize: 11,
          fontFamily: "monospace",
        }}
      >
        <span
          style={{
            background: "#007acc",
            color: "#fff",
            padding: "2px 6px",
            borderRadius: 3,
            fontSize: 10,
          }}
        >
          {element.language}
        </span>

        {errorCount > 0 && (
          <span style={{ color: "#f48771" }}>✗ {errorCount}</span>
        )}
        {warnCount > 0 && (
          <span style={{ color: "#cca700" }}>⚠ {warnCount}</span>
        )}

        <div style={{ flex: 1 }} />

        {element.runConfig.runnable && (
          <button
            onClick={handleRun}
            style={{
              background: "#4ade80",
              color: "#052e16",
              border: "none",
              borderRadius: 3,
              padding: "2px 8px",
              cursor: "pointer",
              fontSize: 11,
              fontWeight: "bold",
            }}
          >
            ▶ Run
          </button>
        )}
      </div>

      {/* Editor area */}
      <div
        ref={containerRef}
        style={{ flex: 1, overflow: "hidden" }}
      />

      {/* Output pane */}
      {element.showOutput && element.output && (
        <div
          style={{
            height: Math.min(120, screenBounds.height * 0.3),
            background: isDark ? "#1e1e1e" : "#f8f8f8",
            borderTop: `1px solid ${isDark ? "#3c3c3c" : "#e0e0e0"}`,
            padding: "4px 8px",
            overflow: "auto",
            fontSize: 11,
            fontFamily: "monospace",
            color: isDark ? "#d4d4d4" : "#333",
            flexShrink: 0,
            whiteSpace: "pre-wrap",
          }}
        >
          <div style={{ color: "#94a3b8", marginBottom: 2 }}>Output:</div>
          {element.output}
        </div>
      )}
    </div>
  );
});
