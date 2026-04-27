/**
 * LiveReactOverlay — renders a live React component on top of a LiveReactElement.
 *
 * @doc.type component
 * @doc.purpose DOM overlay that renders a live, editable React component in canvas space
 * @doc.layer react
 * @doc.pattern Overlay
 *
 * This component is rendered by the canvas host (HybridCanvas or a product wrapper)
 * for each `LiveReactElement` in the freeform layer.  It positions a <div> absolutely
 * over the element's canvas bounding box and renders the registered React component
 * inside it with the element's props.
 *
 * The host should map freeform elements to overlays and keep positions synced with
 * the viewport transform.
 */

import React, { Suspense, useCallback, useMemo } from "react";
import {
  LiveReactElement,
  LiveReactRegistry,
  type PropValue,
} from "../elements/live-react.js";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface LiveReactOverlayProps {
  /** The canvas element describing what to render */
  element: LiveReactElement;
  /**
   * The bounding box in screen space (already transformed by viewport).
   * Caller must recompute this when the viewport changes.
   */
  screenBounds: { x: number; y: number; width: number; height: number };
  /** Called when a prop is changed by the component */
  onPropChange?: (elementId: string, key: string, value: PropValue) => void;
  /** Called when element requests to be selected */
  onSelect?: (elementId: string) => void;
}

// ---------------------------------------------------------------------------
// Error boundary
// ---------------------------------------------------------------------------

interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string;
}

class ComponentErrorBoundary extends React.Component<
  React.PropsWithChildren<{ fallback: string }>,
  ErrorBoundaryState
> {
  constructor(props: React.PropsWithChildren<{ fallback: string }>) {
    super(props);
    this.state = { hasError: false, errorMessage: "" };
  }

  static getDerivedStateFromError(e: unknown): ErrorBoundaryState {
    return { hasError: true, errorMessage: e instanceof Error ? e.message : String(e) };
  }

  override render(): React.ReactNode {
    if (this.state.hasError) {
      return (
        <div
          style={{
            padding: "8px",
            background: "#2d1b1b",
            color: "#ff6b6b",
            fontSize: "12px",
            fontFamily: "monospace",
            height: "100%",
            overflow: "auto",
          }}
        >
          <strong>⚠ {this.props.fallback}</strong>
          <br />
          <pre style={{ marginTop: 4, fontSize: 10 }}>{this.state.errorMessage}</pre>
        </div>
      );
    }
    return this.props.children;
  }
}

// ---------------------------------------------------------------------------
// Main overlay component
// ---------------------------------------------------------------------------

/**
 * LiveReactOverlay
 *
 * Renders as a positioned <div> overlaid on the canvas coordinate space.
 * The canvas 2D layer still draws the placeholder frame underneath this overlay.
 */
export const LiveReactOverlay = React.memo(function LiveReactOverlay({
  element,
  screenBounds,
  onPropChange,
  onSelect,
}: LiveReactOverlayProps): React.ReactElement | null {
  const Component = useMemo(() => element.resolveComponent(), [element]);

  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      if (element.interactive) {
        // Allow event to reach the component
        e.stopPropagation();
      } else {
        onSelect?.(element.id);
      }
    },
    [element, onSelect],
  );

  const titleH = element.showTitleBar ? 28 : 0;

  return (
    <div
      style={{
        position: "absolute",
        left: screenBounds.x,
        top: screenBounds.y,
        width: screenBounds.width,
        height: screenBounds.height,
        pointerEvents: element.interactive ? "auto" : "none",
        overflow: "hidden",
        border: "1.5px dashed #6366f1",
        borderRadius: 2,
        boxSizing: "border-box",
      }}
      onClick={handleClick}
    >
      {/* Title bar */}
      {element.showTitleBar && (
        <div
          style={{
            height: titleH,
            background: "#312e81",
            color: "#c7d2fe",
            fontSize: 11,
            fontWeight: "bold",
            display: "flex",
            alignItems: "center",
            paddingLeft: 8,
            userSelect: "none",
          }}
        >
          ⚛ {element.displayName}
          <span
            style={{
              marginLeft: "auto",
              marginRight: 6,
              background: "#4ade80",
              color: "#052e16",
              fontSize: 9,
              padding: "1px 4px",
              borderRadius: 3,
            }}
          >
            LIVE
          </span>
        </div>
      )}

      {/* Component area */}
      <div
        style={{
          position: "relative",
          height: `calc(100% - ${titleH}px)`,
          overflow: "auto",
          background: element.themeOverride === "dark" ? "#0f172a" : "#ffffff",
        }}
      >
        {Component ? (
          <ComponentErrorBoundary fallback={element.errorFallback}>
            <Suspense fallback={<div style={{ padding: 8, color: "#94a3b8" }}>Loading…</div>}>
              <Component
                {...element.componentProps}
                __canvasElementId={element.id}
                __onPropChange={((key: string, value: PropValue) => {
                  onPropChange?.(element.id, key, value);
                }) as unknown as PropValue}
              />
            </Suspense>
          </ComponentErrorBoundary>
        ) : (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
              color: "#94a3b8",
              fontSize: 12,
              fontFamily: "monospace",
            }}
          >
            {element.componentKey
              ? `Component "${element.componentKey}" not registered`
              : "No component configured"}
          </div>
        )}
      </div>
    </div>
  );
});
