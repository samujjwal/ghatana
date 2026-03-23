/**
 * Minimap Panel Component
 *
 * Enhanced minimap with viewport navigation, zoom controls, and frame visualization.
 * Provides bird's-eye view of entire canvas with interactive navigation.
 *
 * @doc.type component
 * @doc.purpose Canvas overview and navigation
 * @doc.layer presentation
 */

import React, { useState, useEffect, useRef, useMemo } from "react";
import { useAtomValue } from "jotai";
import {
  chromeZoomLevelAtom,
  chromeCurrentPhaseAtom,
} from "../../chrome";
import { getCanvasConfig, hasCanvasConfig } from '../../core/canvas-config';
import { getCanvasState } from "../../handlers/canvas-handlers";

interface MinimapPanelProps {
  onClose: () => void;
}

interface ViewportBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface CanvasBounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export const MinimapPanel: React.FC<MinimapPanelProps> = ({ onClose }) => {
  const zoomLevel = useAtomValue(chromeZoomLevelAtom);
  const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [showFrames, setShowFrames] = useState(true);
  const [showElements, setShowElements] = useState(true);
  const [showConnections, setShowConnections] = useState(true);
  const [minimapZoom, setMinimapZoom] = useState(0.1); // Minimap's own zoom level

  // Get canvas state
  const canvasState = useMemo(() => {
    return getCanvasState();
  }, []);

  // Calculate canvas bounds
  const canvasBounds = useMemo((): CanvasBounds => {
    const elements = canvasState.getAllElements();
    if (elements.length === 0) {
      return { minX: 0, minY: 0, maxX: 1000, maxY: 1000 };
    }

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    elements.forEach((el) => {
      const x = el.x || 0;
      const y = el.y || 0;
      const width = el.width || 100;
      const height = el.height || 100;

      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + width);
      maxY = Math.max(maxY, y + height);
    });

    // Add padding
    const padding = 100;
    return {
      minX: minX - padding,
      minY: minY - padding,
      maxX: maxX + padding,
      maxY: maxY + padding,
    };
  }, [canvasState]);

  // Get viewport bounds (mock - in real implementation, get from ReactFlow)
  const viewportBounds = useMemo((): ViewportBounds => {
    // This would come from the actual canvas viewport
    // For now, using mock values
    return {
      x: 0,
      y: 0,
      width: 800,
      height: 600,
    };
  }, []);

  // Draw minimap
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Calculate scale
    const canvasWidth = canvasBounds.maxX - canvasBounds.minX;
    const canvasHeight = canvasBounds.maxY - canvasBounds.minY;
    const scaleX = (canvas.width * minimapZoom) / canvasWidth;
    const scaleY = (canvas.height * minimapZoom) / canvasHeight;
    const scale = Math.min(scaleX, scaleY);

    // Center the minimap
    const offsetX = (canvas.width - canvasWidth * scale) / 2;
    const offsetY = (canvas.height - canvasHeight * scale) / 2;

    // Transform function
    const transform = (x: number, y: number) => ({
      x: (x - canvasBounds.minX) * scale + offsetX,
      y: (y - canvasBounds.minY) * scale + offsetY,
    });

    // Draw grid
    ctx.strokeStyle = "#f0f0f0";
    ctx.lineWidth = 1;
    const gridSize = 100;
    for (let x = canvasBounds.minX; x <= canvasBounds.maxX; x += gridSize) {
      const start = transform(x, canvasBounds.minY);
      const end = transform(x, canvasBounds.maxY);
      ctx.beginPath();
      ctx.moveTo(start.x, start.y);
      ctx.lineTo(end.x, end.y);
      ctx.stroke();
    }
    for (let y = canvasBounds.minY; y <= canvasBounds.maxY; y += gridSize) {
      const start = transform(canvasBounds.minX, y);
      const end = transform(canvasBounds.maxX, y);
      ctx.beginPath();
      ctx.moveTo(start.x, start.y);
      ctx.lineTo(end.x, end.y);
      ctx.stroke();
    }

    // Draw connections
    if (showConnections) {
      const connections = canvasState.getAllConnections();
      connections.forEach((conn) => {
        const sourceEl = canvasState.getElementById(conn.from);
        const targetEl = canvasState.getElementById(conn.to);

        if (sourceEl && targetEl) {
          const sourcePos = transform(
            sourceEl.x || 0,
            sourceEl.y || 0,
          );
          const targetPos = transform(
            targetEl.x || 0,
            targetEl.y || 0,
          );

          ctx.strokeStyle = "#9e9e9e";
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(sourcePos.x, sourcePos.y);
          ctx.lineTo(targetPos.x, targetPos.y);
          ctx.stroke();
        }
      });
    }

    // Draw elements
    const elements = canvasState.getAllElements();
    elements.forEach((el) => {
      const pos = transform(el.x || 0, el.y || 0);
      const width = (el.width || 100) * scale;
      const height = (el.height || 100) * scale;

      // Determine color based on type and phase
      let fillColor = "#e3f2fd";
      let strokeColor = "#1976d2";

      if (el.type === "frame" && showFrames) {
        const phase = (el.data?.phase as string) || currentPhase;
        const phaseColor = hasCanvasConfig()
          ? getCanvasConfig().phases[phase]?.color
          : undefined;
        fillColor = phaseColor?.background || fillColor;
        strokeColor = phaseColor?.primary || strokeColor;
        ctx.lineWidth = 2;
      } else if (showElements) {
        ctx.lineWidth = 1;
      } else {
        return; // Skip if elements are hidden
      }

      // Draw element
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.fillRect(pos.x, pos.y, width, height);
      ctx.strokeRect(pos.x, pos.y, width, height);

      // Draw label for frames
      if (el.type === "frame" && width > 20) {
        ctx.fillStyle = strokeColor;
        ctx.font = `${Math.max(8, 10 * scale)}px sans-serif`;
        ctx.fillText(
          ((el.data?.title as string) || (el.data?.label as string) || ""),
          pos.x + 4,
          pos.y + 12,
          width - 8,
        );
      }
    });

    // Draw viewport rectangle
    const vpPos = transform(viewportBounds.x, viewportBounds.y);
    const vpWidth = viewportBounds.width * scale;
    const vpHeight = viewportBounds.height * scale;

    ctx.strokeStyle = "#1976d2";
    ctx.lineWidth = 2;
    ctx.setLineDash([5, 5]);
    ctx.strokeRect(vpPos.x, vpPos.y, vpWidth, vpHeight);
    ctx.setLineDash([]);

    // Draw viewport fill
    ctx.fillStyle = "rgba(25, 118, 210, 0.1)";
    ctx.fillRect(vpPos.x, vpPos.y, vpWidth, vpHeight);
  }, [
    canvasBounds,
    viewportBounds,
    minimapZoom,
    showFrames,
    showElements,
    showConnections,
    canvasState,
    currentPhase,
  ]);

  // Handle minimap click for navigation
  const handleMinimapClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const clickY = e.clientY - rect.top;

    // Calculate scale
    const canvasWidth = canvasBounds.maxX - canvasBounds.minX;
    const canvasHeight = canvasBounds.maxY - canvasBounds.minY;
    const scaleX = (canvas.width * minimapZoom) / canvasWidth;
    const scaleY = (canvas.height * minimapZoom) / canvasHeight;
    const scale = Math.min(scaleX, scaleY);

    const offsetX = (canvas.width - canvasWidth * scale) / 2;
    const offsetY = (canvas.height - canvasHeight * scale) / 2;

    // Convert click position to canvas coordinates
    const canvasX = (clickX - offsetX) / scale + canvasBounds.minX;
    const canvasY = (clickY - offsetY) / scale + canvasBounds.minY;

    // Dispatch navigation event
    const event = new CustomEvent("yappc:navigate-to", {
      detail: { x: canvasX, y: canvasY },
    });
    window.dispatchEvent(event);
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    setIsDragging(true);
    handleMinimapClick(e);
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (isDragging) {
      handleMinimapClick(e);
    }
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  const handleZoomIn = () => {
    setMinimapZoom(Math.min(minimapZoom * 1.2, 1.0));
  };

  const handleZoomOut = () => {
    setMinimapZoom(Math.max(minimapZoom / 1.2, 0.05));
  };

  const handleResetZoom = () => {
    setMinimapZoom(0.1);
  };

  const handleFitToView = () => {
    // Calculate optimal zoom to fit all content
    const canvas = canvasRef.current;
    if (!canvas) return;

    const canvasWidth = canvasBounds.maxX - canvasBounds.minX;
    const canvasHeight = canvasBounds.maxY - canvasBounds.minY;
    const scaleX = canvas.width / canvasWidth;
    const scaleY = canvas.height / canvasHeight;
    const optimalZoom = Math.min(scaleX, scaleY) * 0.9; // 90% to add padding

    setMinimapZoom(Math.max(0.05, Math.min(1.0, optimalZoom)));
  };

  return (
    <div
      style={{
        width: "320px",
        height: "100%",
        backgroundColor: "#ffffff",
        borderRight: "1px solid #e0e0e0",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "16px",
          borderBottom: "1px solid #e0e0e0",
          display: "flex",
          flexDirection: "column",
          gap: "12px",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 600 }}>
            Minimap
          </h3>
          <button
            onClick={onClose}
            style={{
              border: "none",
              background: "transparent",
              cursor: "pointer",
              fontSize: "20px",
              padding: "4px",
            }}
            aria-label="Close panel"
          >
            ×
          </button>
        </div>

        {/* Info */}
        <div
          style={{
            fontSize: "12px",
            color: "#757575",
          }}
        >
          Zoom: {Math.round(zoomLevel * 100)}% • Click to navigate
        </div>

        {/* Controls */}
        <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
          <button
            onClick={handleZoomIn}
            style={{
              padding: "6px 12px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              background: "white",
              cursor: "pointer",
              fontSize: "12px",
            }}
            title="Zoom in minimap"
          >
            🔍+
          </button>
          <button
            onClick={handleZoomOut}
            style={{
              padding: "6px 12px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              background: "white",
              cursor: "pointer",
              fontSize: "12px",
            }}
            title="Zoom out minimap"
          >
            🔍-
          </button>
          <button
            onClick={handleResetZoom}
            style={{
              padding: "6px 12px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              background: "white",
              cursor: "pointer",
              fontSize: "12px",
            }}
            title="Reset minimap zoom"
          >
            Reset
          </button>
          <button
            onClick={handleFitToView}
            style={{
              padding: "6px 12px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              background: "white",
              cursor: "pointer",
              fontSize: "12px",
            }}
            title="Fit to view"
          >
            Fit
          </button>
        </div>

        {/* Visibility toggles */}
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            <input
              type="checkbox"
              checked={showFrames}
              onChange={(e) => setShowFrames(e.target.checked)}
            />
            Show frames
          </label>
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            <input
              type="checkbox"
              checked={showElements}
              onChange={(e) => setShowElements(e.target.checked)}
            />
            Show elements
          </label>
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            <input
              type="checkbox"
              checked={showConnections}
              onChange={(e) => setShowConnections(e.target.checked)}
            />
            Show connections
          </label>
        </div>
      </div>

      {/* Minimap Canvas */}
      <div
        style={{
          flex: 1,
          padding: "16px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#fafafa",
        }}
      >
        <canvas
          ref={canvasRef}
          width={288}
          height={400}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          style={{
            border: "1px solid #e0e0e0",
            borderRadius: "8px",
            cursor: isDragging ? "grabbing" : "pointer",
            background: "white",
          }}
        />
      </div>

      {/* Legend */}
      <div
        style={{
          padding: "16px",
          borderTop: "1px solid #e0e0e0",
          fontSize: "11px",
          color: "#757575",
        }}
      >
        <div style={{ marginBottom: "8px", fontWeight: 600 }}>Legend:</div>
        <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "16px",
                height: "16px",
                border: "2px dashed #1976d2",
                background: "rgba(25, 118, 210, 0.1)",
              }}
            />
            <span>Current viewport</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "16px",
                height: "16px",
                border: "2px solid #1976d2",
                background: "#e3f2fd",
              }}
            />
            <span>Frames</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div
              style={{
                width: "16px",
                height: "16px",
                border: "1px solid #9e9e9e",
                background: "#f5f5f5",
              }}
            />
            <span>Elements</span>
          </div>
        </div>
      </div>
    </div>
  );
};
