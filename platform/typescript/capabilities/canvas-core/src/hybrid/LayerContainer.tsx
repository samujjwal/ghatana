/**
 * @ghatana/canvas Layer Container
 *
 * Container component that manages the stacking of canvas layers.
 *
 * @doc.type component
 * @doc.purpose Layer management
 * @doc.layer core
 * @doc.pattern Component
 */

import React, { forwardRef, type CSSProperties, type ReactNode } from "react";
import { useAtomValue } from "jotai";
import { layersAtom, viewportAtom, gridAtom } from "./state";
import type { RenderingMode } from "./types";

export interface LayerContainerProps {
  /** Container width */
  width?: number | string;
  /** Container height */
  height?: number | string;
  /** Current rendering mode */
  mode: RenderingMode;
  /** Class name */
  className?: string;
  /** Freeform layer content */
  freeformLayer?: ReactNode;
  /** Graph layer content */
  graphLayer?: ReactNode;
  /** Overlay layer content */
  overlayLayer?: ReactNode;
  /** Children (custom overlays) */
  children?: ReactNode;
}

/**
 * Grid background component
 */
function GridBackground() {
  const grid = useAtomValue(gridAtom);
  const viewport = useAtomValue(viewportAtom);

  if (!grid.visible || grid.type === "none") {
    return null;
  }

  const scaledSize = grid.size * viewport.zoom;
  const offsetX = viewport.x % scaledSize;
  const offsetY = viewport.y % scaledSize;

  if (grid.type === "dots") {
    return (
      <svg
        style={{
          position: "absolute",
          inset: 0,
          width: "100%",
          height: "100%",
          pointerEvents: "none",
        }}
      >
        <defs>
          <pattern
            id="ghatana-grid-dots"
            width={scaledSize}
            height={scaledSize}
            patternUnits="userSpaceOnUse"
            x={offsetX}
            y={offsetY}
          >
            <circle
              cx={scaledSize / 2}
              cy={scaledSize / 2}
              r={1}
              fill={grid.color}
            />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#ghatana-grid-dots)" />
      </svg>
    );
  }

  // Lines grid
  return (
    <svg
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
      }}
    >
      <defs>
        <pattern
          id="ghatana-grid-lines"
          width={scaledSize}
          height={scaledSize}
          patternUnits="userSpaceOnUse"
          x={offsetX}
          y={offsetY}
        >
          <path
            d={`M ${scaledSize} 0 L 0 0 0 ${scaledSize}`}
            fill="none"
            stroke={grid.color}
            strokeWidth={0.5}
          />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#ghatana-grid-lines)" />
    </svg>
  );
}

/**
 * Layer Container Component
 *
 * Manages the stacking and visibility of canvas layers.
 */
export const LayerContainer = forwardRef<HTMLDivElement, LayerContainerProps>(
  function LayerContainer(
    {
      width = "100%",
      height = "100%",
      mode,
      className = "",
      freeformLayer,
      graphLayer,
      overlayLayer,
      children,
    },
    ref,
  ) {
    const layers = useAtomValue(layersAtom);

    // Determine layer visibility based on mode
    const showFreeform = mode !== "graph-only" && layers.freeform.visible;
    const showGraph = mode !== "freeform-only" && layers.graph.visible;
    const showOverlay = layers.overlay.visible;

    const containerStyle: CSSProperties = {
      position: "relative",
      width,
      height,
      overflow: "hidden",
      userSelect: "none",
      touchAction: "none",
    };

    const layerBaseStyle: CSSProperties = {
      position: "absolute",
      inset: 0,
      width: "100%",
      height: "100%",
    };

    return (
      <div
        ref={ref}
        className={`ghatana-canvas-container ${className}`}
        style={containerStyle}
        data-mode={mode}
      >
        {/* Grid Background */}
        <GridBackground />

        {/* Freeform Layer (Custom Canvas) */}
        {showFreeform && (
          <div
            className="ghatana-layer ghatana-layer-freeform"
            style={{
              ...layerBaseStyle,
              zIndex: layers.freeform.zIndex,
              opacity: layers.freeform.opacity,
              pointerEvents: layers.freeform.interactive ? "auto" : "none",
            }}
            data-layer="freeform"
          >
            {freeformLayer}
          </div>
        )}

        {/* Graph Layer (ReactFlow) */}
        {showGraph && (
          <div
            className="ghatana-layer ghatana-layer-graph"
            style={{
              ...layerBaseStyle,
              zIndex: layers.graph.zIndex,
              opacity: layers.graph.opacity,
              pointerEvents: layers.graph.interactive ? "auto" : "none",
            }}
            data-layer="graph"
          >
            {graphLayer}
          </div>
        )}

        {/* Overlay Layer */}
        {showOverlay && overlayLayer && (
          <div
            className="ghatana-layer ghatana-layer-overlay"
            style={{
              ...layerBaseStyle,
              zIndex: layers.overlay.zIndex,
              opacity: layers.overlay.opacity,
              pointerEvents: layers.overlay.interactive ? "auto" : "none",
            }}
            data-layer="overlay"
          >
            {overlayLayer}
          </div>
        )}

        {/* Custom children */}
        {children}
      </div>
    );
  },
);

export default LayerContainer;
