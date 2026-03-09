/**
 * @ghatana/canvas Freeform Layer
 *
 * Custom HTML5 Canvas layer for freeform elements.
 * This wraps the existing Lit-based canvas infrastructure.
 *
 * @doc.type component
 * @doc.purpose Freeform rendering layer
 * @doc.layer core
 * @doc.pattern Component
 */

import React, {
  useEffect,
  useRef,
  useCallback,
  forwardRef,
  useImperativeHandle,
  type CSSProperties,
} from "react";
import { useAtomValue, useSetAtom } from "jotai";
import type { CanvasElement, Point, Rect, ViewportState } from "./types";
import {
  elementsAtom,
  visibleElementsAtom,
  viewportAtom,
  selectionAtom,
  toolAtom,
  gridAtom,
} from "./state";
import { screenToCanvas, snapToGrid } from "./coordinates";

export interface FreeformLayerProps {
  /** Read-only mode */
  readOnly?: boolean;
  /** Custom render function for elements */
  renderElement?: (
    element: CanvasElement,
    ctx: CanvasRenderingContext2D,
  ) => void;
  /** Element click handler */
  onElementClick?: (element: CanvasElement, event: MouseEvent) => void;
  /** Element double-click handler */
  onElementDoubleClick?: (element: CanvasElement, event: MouseEvent) => void;
  /** Canvas click handler (background) */
  onCanvasClick?: (point: Point, event: MouseEvent) => void;
  /** Drag start handler */
  onDragStart?: (elements: CanvasElement[], point: Point) => void;
  /** Drag handler */
  onDrag?: (elements: CanvasElement[], delta: Point) => void;
  /** Drag end handler */
  onDragEnd?: (elements: CanvasElement[], point: Point) => void;
  /** Class name */
  className?: string;
}

export interface FreeformLayerRef {
  /** Get the canvas element */
  getCanvas(): HTMLCanvasElement | null;
  /** Force re-render */
  render(): void;
  /** Get element at point */
  getElementAt(point: Point): CanvasElement | undefined;
  /** Get elements in rect */
  getElementsInRect(rect: Rect): CanvasElement[];
}

/**
 * Freeform Layer Component
 *
 * Renders custom canvas elements using HTML5 Canvas.
 */
export const FreeformLayer = forwardRef<FreeformLayerRef, FreeformLayerProps>(
  function FreeformLayer(
    {
      readOnly = false,
      renderElement,
      onElementClick,
      onElementDoubleClick,
      onCanvasClick,
      onDragStart,
      onDrag,
      onDragEnd,
      className = "",
    },
    ref,
  ) {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const animationFrameRef = useRef<number | null>(null);
    const isDraggingRef = useRef(false);
    const dragStartRef = useRef<Point | null>(null);
    const dragElementsRef = useRef<CanvasElement[]>([]);

    const elements = useAtomValue(visibleElementsAtom);
    const viewport = useAtomValue(viewportAtom);
    const selection = useAtomValue(selectionAtom);
    const tool = useAtomValue(toolAtom);
    const grid = useAtomValue(gridAtom);
    const setSelection = useSetAtom(selectionAtom);

    // Expose imperative methods
    useImperativeHandle(ref, () => ({
      getCanvas: () => canvasRef.current,
      render: () => requestRender(),
      getElementAt: (point: Point) => hitTest(point),
      getElementsInRect: (rect: Rect) => hitTestRect(rect),
    }));

    // Hit test for single point
    const hitTest = useCallback(
      (canvasPoint: Point): CanvasElement | undefined => {
        // Test in reverse order (top to bottom)
        for (let i = elements.length - 1; i >= 0; i--) {
          const element = elements[i];
          if (
            canvasPoint.x >= element.position.x &&
            canvasPoint.x <= element.position.x + element.size.width &&
            canvasPoint.y >= element.position.y &&
            canvasPoint.y <= element.position.y + element.size.height
          ) {
            return element;
          }
        }
        return undefined;
      },
      [elements],
    );

    // Hit test for rectangle
    const hitTestRect = useCallback(
      (rect: Rect): CanvasElement[] => {
        return elements.filter((element) => {
          const elemRect = {
            x: element.position.x,
            y: element.position.y,
            width: element.size.width,
            height: element.size.height,
          };
          return !(
            elemRect.x + elemRect.width < rect.x ||
            rect.x + rect.width < elemRect.x ||
            elemRect.y + elemRect.height < rect.y ||
            rect.y + rect.height < elemRect.y
          );
        });
      },
      [elements],
    );

    // Render function
    const render = useCallback(() => {
      const canvas = canvasRef.current;
      const container = containerRef.current;
      if (!canvas || !container) return;

      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      // Resize canvas to match container
      const rect = container.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;

      if (
        canvas.width !== rect.width * dpr ||
        canvas.height !== rect.height * dpr
      ) {
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        canvas.style.width = `${rect.width}px`;
        canvas.style.height = `${rect.height}px`;
        ctx.scale(dpr, dpr);
      }

      // Clear canvas
      ctx.clearRect(0, 0, rect.width, rect.height);

      // Apply viewport transform
      ctx.save();
      ctx.translate(viewport.x, viewport.y);
      ctx.scale(viewport.zoom, viewport.zoom);

      // Render elements
      for (const element of elements) {
        ctx.save();

        // Apply element transform
        ctx.translate(element.position.x, element.position.y);
        if (element.rotation) {
          ctx.rotate((element.rotation * Math.PI) / 180);
        }
        if (element.opacity !== undefined) {
          ctx.globalAlpha = element.opacity;
        }

        // Custom render or default
        if (renderElement) {
          renderElement(element, ctx);
        } else {
          renderDefaultElement(element, ctx);
        }

        // Render selection outline
        if (selection.elementIds.includes(element.id)) {
          ctx.strokeStyle = "#0066ff";
          ctx.lineWidth = 2 / viewport.zoom;
          ctx.setLineDash([]);
          ctx.strokeRect(0, 0, element.size.width, element.size.height);

          // Render resize handles
          if (!readOnly) {
            renderResizeHandles(
              ctx,
              element.size.width,
              element.size.height,
              viewport.zoom,
            );
          }
        }

        ctx.restore();
      }

      ctx.restore();

      animationFrameRef.current = null;
    }, [elements, viewport, selection, renderElement, readOnly]);

    // Request render (debounced via RAF)
    const requestRender = useCallback(() => {
      if (animationFrameRef.current === null) {
        animationFrameRef.current = requestAnimationFrame(render);
      }
    }, [render]);

    // Render on state changes
    useEffect(() => {
      requestRender();
    }, [elements, viewport, selection, requestRender]);

    // Handle resize
    useEffect(() => {
      const container = containerRef.current;
      if (!container) return;

      const observer = new ResizeObserver(() => {
        requestRender();
      });

      observer.observe(container);
      return () => observer.disconnect();
    }, [requestRender]);

    // Mouse event handlers
    const handleMouseDown = useCallback(
      (e: React.MouseEvent) => {
        if (readOnly) return;

        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const screenPoint = {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        };
        const canvasPoint = screenToCanvas(screenPoint, viewport);

        const element = hitTest(canvasPoint);

        if (element) {
          // Select element
          if (e.shiftKey) {
            // Toggle selection
            if (selection.elementIds.includes(element.id)) {
              setSelection({
                ...selection,
                elementIds: selection.elementIds.filter(
                  (id) => id !== element.id,
                ),
              });
            } else {
              setSelection({
                ...selection,
                elementIds: [...selection.elementIds, element.id],
                isMultiSelect: true,
              });
            }
          } else if (!selection.elementIds.includes(element.id)) {
            setSelection({
              ...selection,
              elementIds: [element.id],
              isMultiSelect: false,
            });
          }

          // Start drag
          if (tool === "select") {
            isDraggingRef.current = true;
            dragStartRef.current = canvasPoint;
            dragElementsRef.current = elements.filter(
              (el) =>
                selection.elementIds.includes(el.id) || el.id === element.id,
            );
            onDragStart?.(dragElementsRef.current, canvasPoint);
          }

          onElementClick?.(element, e.nativeEvent);
        } else {
          // Click on canvas
          if (!e.shiftKey) {
            setSelection({
              ...selection,
              elementIds: [],
              isMultiSelect: false,
            });
          }
          onCanvasClick?.(canvasPoint, e.nativeEvent);
        }
      },
      [
        readOnly,
        viewport,
        hitTest,
        selection,
        setSelection,
        elements,
        tool,
        onDragStart,
        onElementClick,
        onCanvasClick,
      ],
    );

    const handleMouseMove = useCallback(
      (e: React.MouseEvent) => {
        if (!isDraggingRef.current || !dragStartRef.current) return;

        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const screenPoint = {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        };
        const canvasPoint = screenToCanvas(screenPoint, viewport);

        const delta = {
          x: canvasPoint.x - dragStartRef.current.x,
          y: canvasPoint.y - dragStartRef.current.y,
        };

        // Snap to grid if enabled
        const snappedDelta = grid.snap
          ? {
              x: Math.round(delta.x / grid.size) * grid.size,
              y: Math.round(delta.y / grid.size) * grid.size,
            }
          : delta;

        onDrag?.(dragElementsRef.current, snappedDelta);
      },
      [viewport, grid, onDrag],
    );

    const handleMouseUp = useCallback(
      (e: React.MouseEvent) => {
        if (!isDraggingRef.current) return;

        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const screenPoint = {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        };
        const canvasPoint = screenToCanvas(screenPoint, viewport);

        onDragEnd?.(dragElementsRef.current, canvasPoint);

        isDraggingRef.current = false;
        dragStartRef.current = null;
        dragElementsRef.current = [];
      },
      [viewport, onDragEnd],
    );

    const handleDoubleClick = useCallback(
      (e: React.MouseEvent) => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const rect = canvas.getBoundingClientRect();
        const screenPoint = {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        };
        const canvasPoint = screenToCanvas(screenPoint, viewport);

        const element = hitTest(canvasPoint);
        if (element) {
          onElementDoubleClick?.(element, e.nativeEvent);
        }
      },
      [viewport, hitTest, onElementDoubleClick],
    );

    const containerStyle: CSSProperties = {
      position: "absolute",
      inset: 0,
      width: "100%",
      height: "100%",
    };

    const canvasStyle: CSSProperties = {
      display: "block",
      width: "100%",
      height: "100%",
    };

    return (
      <div
        ref={containerRef}
        className={`ghatana-freeform-layer ${className}`}
        style={containerStyle}
      >
        <canvas
          ref={canvasRef}
          style={canvasStyle}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
          onDoubleClick={handleDoubleClick}
        />
      </div>
    );
  },
);

// Default element rendering
function renderDefaultElement(
  element: CanvasElement,
  ctx: CanvasRenderingContext2D,
): void {
  const { width, height } = element.size;
  const style = element.style ?? {};

  // Fill
  ctx.fillStyle = style.fill ?? "#ffffff";
  if (style.borderRadius) {
    roundRect(ctx, 0, 0, width, height, style.borderRadius);
    ctx.fill();
  } else {
    ctx.fillRect(0, 0, width, height);
  }

  // Stroke
  if (style.stroke) {
    ctx.strokeStyle = style.stroke;
    ctx.lineWidth = style.strokeWidth ?? 1;
    if (style.strokeDasharray) {
      ctx.setLineDash(style.strokeDasharray.split(",").map(Number));
    }
    if (style.borderRadius) {
      roundRect(ctx, 0, 0, width, height, style.borderRadius);
      ctx.stroke();
    } else {
      ctx.strokeRect(0, 0, width, height);
    }
    ctx.setLineDash([]);
  }

  // Type label
  ctx.fillStyle = "#666666";
  ctx.font = "12px sans-serif";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(element.type, width / 2, height / 2);
}

// Render resize handles
function renderResizeHandles(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  zoom: number,
): void {
  const handleSize = 8 / zoom;
  const half = handleSize / 2;

  ctx.fillStyle = "#ffffff";
  ctx.strokeStyle = "#0066ff";
  ctx.lineWidth = 1 / zoom;

  const handles = [
    { x: -half, y: -half }, // top-left
    { x: width / 2 - half, y: -half }, // top-center
    { x: width - half, y: -half }, // top-right
    { x: width - half, y: height / 2 - half }, // right-center
    { x: width - half, y: height - half }, // bottom-right
    { x: width / 2 - half, y: height - half }, // bottom-center
    { x: -half, y: height - half }, // bottom-left
    { x: -half, y: height / 2 - half }, // left-center
  ];

  for (const handle of handles) {
    ctx.fillRect(handle.x, handle.y, handleSize, handleSize);
    ctx.strokeRect(handle.x, handle.y, handleSize, handleSize);
  }
}

// Helper for rounded rectangles
function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
): void {
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.lineTo(x + width - radius, y);
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
  ctx.lineTo(x + width, y + height - radius);
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
  ctx.lineTo(x + radius, y + height);
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
  ctx.lineTo(x, y + radius);
  ctx.quadraticCurveTo(x, y, x + radius, y);
  ctx.closePath();
}

export default FreeformLayer;
