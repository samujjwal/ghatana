/**
 * React Integration for YAPPC Canvas
 * Provides React hooks and components for canvas integration
 */

import React from "react";
import { useEffect, useRef, useState } from "react";
import { CanvasRenderer } from "../core/canvas-renderer.js";
import { Viewport } from "../core/viewport.js";
import { CanvasOptions } from "../types/index.js";
import { CanvasElement } from "../elements/base.js";

/**
 * Hook for creating and managing a canvas instance
 */
export function useCanvas(
  containerRef: React.RefObject<HTMLElement | null>,
  options: CanvasOptions,
) {
  const canvasRef = useRef<CanvasRenderer | null>(null);
  const optionsRef = useRef(options);
  const [isReady, setIsReady] = useState(false);

  // Update options ref without triggering re-render
  optionsRef.current = options;

  useEffect(() => {
    if (!containerRef.current || canvasRef.current) return;

    console.log("Creating new canvas instance");
    canvasRef.current = new CanvasRenderer(
      containerRef.current,
      optionsRef.current,
    );
    setIsReady(true);

    return () => {
      if (canvasRef.current) {
        console.log("Cleaning up canvas instance");
        canvasRef.current = null;
        setIsReady(false);
      }
    };
  }, []); // Empty deps - create once, cleanup on unmount only

  return { canvas: canvasRef.current, isReady };
}

/**
 * React component for rendering a canvas
 */
export interface CanvasProps {
  width: number;
  height: number;
  theme?: "light" | "dark";
  enableStackingCanvas?: boolean;
  enableDomRenderer?: boolean;
  className?: string;
  style?: React.CSSProperties;
  onCanvasReady?: (canvas: CanvasRenderer) => void;
  onElementSelect?: (element: CanvasElement) => void;
  onElementAdd?: (element: CanvasElement) => void;
  onElementRemove?: (element: CanvasElement) => void;
  children?: React.ReactNode;
}

export const Canvas: React.FC<CanvasProps> = ({
  width,
  height,
  theme = "light",
  enableStackingCanvas = false,
  enableDomRenderer = false,
  className = "",
  style = {},
  onCanvasReady,
  onElementSelect,
  onElementAdd,
  onElementRemove,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const { canvas, isReady } = useCanvas(containerRef as React.RefObject<HTMLElement>, {
    width,
    height,
    theme,
    enableStackingCanvas,
    enableDomRenderer,
  });

  // Use refs for callbacks to avoid re-running effects
  const onCanvasReadyRef = useRef(onCanvasReady);
  const onElementSelectRef = useRef(onElementSelect);
  const onElementAddRef = useRef(onElementAdd);
  const onElementRemoveRef = useRef(onElementRemove);
  const initializedRef = useRef(false);

  onCanvasReadyRef.current = onCanvasReady;
  onElementSelectRef.current = onElementSelect;
  onElementAddRef.current = onElementAdd;
  onElementRemoveRef.current = onElementRemove;

  useEffect(() => {
    if (!canvas || !isReady || initializedRef.current) return;

    initializedRef.current = true;

    // Call onCanvasReady once
    if (onCanvasReadyRef.current) {
      onCanvasReadyRef.current(canvas);
    }

    // Set up event listeners
    const handleSelect = (element: CanvasElement) => {
      if (onElementSelectRef.current) onElementSelectRef.current(element);
    };
    const handleAdd = (element: CanvasElement) => {
      if (onElementAddRef.current) onElementAddRef.current(element);
    };
    const handleRemove = (element: CanvasElement) => {
      if (onElementRemoveRef.current) onElementRemoveRef.current(element);
    };

    canvas.on("elementSelect", handleSelect);
    canvas.on("elementAdd", handleAdd);
    canvas.on("elementRemove", handleRemove);

    return () => {
      canvas.off("elementSelect", handleSelect);
      canvas.off("elementAdd", handleAdd);
      canvas.off("elementRemove", handleRemove);
    };
  }, [canvas, isReady]);

  return React.createElement(
    "div",
    {
      ref: containerRef,
      className: `yappc-canvas ${className}`,
      style: {
        width: `${width}px`,
        height: `${height}px`,
        position: "relative",
        overflow: "hidden",
        pointerEvents: "auto",
        ...style,
      },
    },
    children,
  );
};

/**
 * Hook for managing canvas elements
 */
export function useCanvasElements(canvas: CanvasRenderer | null) {
  const [elements, setElements] = useState<CanvasElement[]>([]);

  useEffect(() => {
    if (!canvas) return;

    const updateElements = () => {
      // Get elements through public methods
      const allElements: CanvasElement[] = [];
      // Note: This would need to be implemented in the renderer
      // For now, we'll track elements manually
      setElements(allElements);
    };

    canvas.on("elementAdd", updateElements);
    canvas.on("elementRemove", updateElements);

    return () => {
      canvas.off("elementAdd", updateElements);
      canvas.off("elementRemove", updateElements);
    };
  }, [canvas]);

  const addElement = (element: CanvasElement) => {
    if (canvas) canvas.addElement(element);
  };

  const removeElement = (element: CanvasElement) => {
    if (canvas) canvas.removeElement(element);
  };

  const selectElement = (element: CanvasElement) => {
    if (canvas) canvas.selectElement(element);
  };

  const clearSelection = () => {
    if (canvas) canvas.clearSelection();
  };

  return {
    elements,
    addElement,
    removeElement,
    selectElement,
    clearSelection,
  };
}

/**
 * Hook for canvas viewport management
 */
export function useCanvasViewport(canvas: CanvasRenderer | null) {
  const [viewport, setViewport] = useState<Viewport | null>(null);

  useEffect(() => {
    if (!canvas) return;

    // Set initial viewport
    setViewport(canvas.viewport);

    const updateViewport = () => {
      setViewport(canvas.viewport);
    };

    canvas.on("viewportChange", updateViewport);

    return () => {
      canvas.off("viewportChange", updateViewport);
    };
  }, [canvas]);

  const panTo = (x: number, y: number) => {
    if (canvas) {
      canvas.viewport.centerX = x;
      canvas.viewport.centerY = y;
      canvas.render();
    }
  };

  const zoomTo = (zoom: number) => {
    if (canvas) {
      canvas.viewport.zoom = zoom;
      canvas.render();
    }
  };

  const reset = () => {
    if (canvas) {
      canvas.viewport.reset();
      canvas.render();
    }
  };

  return {
    viewport,
    panTo,
    zoomTo,
    reset,
  };
}

// =============================================================================
// CANVAS FLOW (High-level component for product integration)
// =============================================================================

import type { CanvasDocument, CanvasElement as DocElement, CanvasElementType } from "../types/index.js";

/**
 * Event type for canvas element interactions
 */
export interface CanvasFlowElementEvent {
  element: DocElement;
  position?: { x: number; y: number };
}

/**
 * Props for the CanvasFlow component
 */
export interface CanvasFlowProps {
  /** Initial canvas document */
  initialDocument?: CanvasDocument;
  /** CSS class name */
  className?: string;
  /** Canvas width */
  width?: number;
  /** Canvas height */
  height?: number;
  /** Accessibility label */
  ariaLabel?: string;
  /** Element click handler */
  onElementClick?: (event: CanvasFlowElementEvent) => void;
  /** Element double-click handler */
  onElementDoubleClick?: (event: CanvasFlowElementEvent) => void;
  /** Read-only mode */
  readOnly?: boolean;
  /** Children */
  children?: React.ReactNode;
}

/**
 * CanvasFlow - High-level canvas component for product integration
 * 
 * Provides a simplified API for embedding canvas functionality.
 * Uses the hybrid canvas system internally.
 */
export const CanvasFlow: React.FC<CanvasFlowProps> = ({
  initialDocument,
  className = "",
  width = 800,
  height = 600,
  ariaLabel = "Canvas",
  onElementClick,
  onElementDoubleClick,
  readOnly = false,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const { canvas, isReady } = useCanvas(containerRef, {
    width,
    height,
    theme: "light",
  });

  // Load initial document
  useEffect(() => {
    if (!isReady || !canvas || !initialDocument) return;
    
    // Convert document elements to canvas elements
    const elements = Object.values(initialDocument.elements || {});
    elements.forEach((el) => {
      if ('nodeType' in el) {
        // It's a node
        canvas.addElement({
          id: el.id,
          type: el.type as CanvasElementType,
          x: el.bounds.x,
          y: el.bounds.y,
          width: el.bounds.width,
          height: el.bounds.height,
          data: (el as Record<string, unknown>).data,
        } as unknown as CanvasElement);
      }
    });
  }, [isReady, canvas, initialDocument]);

  // Handle element clicks
  useEffect(() => {
    if (!canvas || !onElementClick) return;

    const handleClick = (element: CanvasElement) => {
      onElementClick({
        element: {
          id: element.id,
          type: element.type,
          transform: { position: { x: element.x, y: element.y }, scale: 1, rotation: element.rotate || 0 },
          bounds: { x: element.x, y: element.y, width: element.w, height: element.h },
          visible: true,
          locked: false,
          selected: false,
          zIndex: parseInt(element.index, 10) || 0,
          metadata: {},
          version: "1.0.0",
          createdAt: new Date(),
          updatedAt: new Date(),
        },
      });
    };

    canvas.on("elementClick", handleClick);
    return () => canvas.off("elementClick", handleClick);
  }, [canvas, onElementClick]);

  return (
    <div
      ref={containerRef}
      className={className}
      style={{ width, height, position: "relative" }}
      aria-label={ariaLabel}
      role="application"
    >
      {children}
    </div>
  );
};
