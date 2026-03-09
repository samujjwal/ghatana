/**
 * @ghatana/yappc-sketch - SketchCanvas Component
 *
 * Production-grade canvas component for freehand drawing and whiteboarding.
 * Uses Konva for high-performance 2D rendering.
 *
 * @doc.type component
 * @doc.purpose Main sketch canvas for drawing
 * @doc.layer presentation
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useCallback, useMemo, forwardRef, useImperativeHandle } from 'react';
import { Stage, Layer, Line, Rect, Ellipse, Arrow } from 'react-konva';
import type { Stage as StageType } from 'konva/lib/Stage';
import type { KonvaEventObject } from 'konva/lib/Node';

import { useSketchTools } from '../hooks/useSketchTools';
import type {
  SketchTool,
  SketchToolConfig,
  SketchElement,
  StrokeData,
  ShapeData,
  Point,
  SketchViewport,
} from '../types';
import { DEFAULT_TOOL_CONFIGS } from '../types';

/**
 * Props for SketchCanvas component
 */
export interface SketchCanvasProps {
  /** Canvas width */
  width: number;
  /** Canvas height */
  height: number;
  /** Currently active tool */
  activeTool: SketchTool;
  /** Tool configuration */
  toolConfig?: SketchToolConfig;
  /** Existing sketch elements to render */
  elements?: SketchElement[];
  /** Callback when element is added */
  onElementAdd?: (element: SketchElement) => void;
  /** Callback when element is updated */
  onElementUpdate?: (id: string, data: Partial<SketchElement>) => void;
  /** Callback when element is deleted */
  onElementDelete?: (id: string) => void;
  /** Viewport state for pan/zoom */
  viewport?: SketchViewport;
  /** Callback when viewport changes */
  onViewportChange?: (viewport: SketchViewport) => void;
  /** Whether canvas is interactive */
  interactive?: boolean;
  /** Background color */
  backgroundColor?: string;
  /** Show grid */
  showGrid?: boolean;
  /** Grid size in pixels */
  gridSize?: number;
  /** Custom class name */
  className?: string;
  /** Custom styles */
  style?: React.CSSProperties;
}

/**
 * Ref handle for SketchCanvas
 */
export interface SketchCanvasRef {
  /** Get the Konva stage instance */
  getStage: () => StageType | null;
  /** Export canvas to data URL */
  toDataURL: (options?: { pixelRatio?: number; mimeType?: string }) => string;
  /** Clear all elements */
  clear: () => void;
}


/**
 * Main sketch canvas component for freehand drawing and whiteboarding.
 *
 * Features:
 * - Freehand drawing with pen/highlighter
 * - Shape drawing (rectangle, ellipse, line, arrow)
 * - Eraser tool
 * - Pan and zoom support
 * - Grid overlay
 * - Export to image
 *
 * @param props - Component properties
 * @returns Rendered sketch canvas
 *
 * @example
 * ```tsx
 * <SketchCanvas
 *   width={800}
 *   height={600}
 *   activeTool="pen"
 *   elements={elements}
 *   onElementAdd={(el) => setElements([...elements, el])}
 * />
 * ```
 */
export const SketchCanvas = forwardRef<SketchCanvasRef, SketchCanvasProps>(
  (
    {
      width,
      height,
      activeTool,
      toolConfig,
      elements = [],
      onElementAdd,
      onElementUpdate: _onElementUpdate,
      onElementDelete,
      viewport = { x: 0, y: 0, zoom: 1 },
      onViewportChange: _onViewportChange,
      interactive = true,
      backgroundColor = 'transparent',
      showGrid = false,
      gridSize = 20,
      className,
      style,
    },
    ref
  ) => {
    const stageRef = useRef<StageType>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Merge tool config with defaults
    const mergedConfig = useMemo(
      () => ({
        ...DEFAULT_TOOL_CONFIGS[activeTool],
        ...toolConfig,
      }),
      [activeTool, toolConfig]
    );

    // Expose imperative handle
    useImperativeHandle(ref, () => ({
      getStage: () => stageRef.current,
      toDataURL: (options) => {
        if (!stageRef.current) return '';
        return stageRef.current.toDataURL({
          pixelRatio: options?.pixelRatio || 2,
          mimeType: options?.mimeType || 'image/png',
        });
      },
      clear: () => {
        elements.forEach((el) => onElementDelete?.(el.id));
      },
    }));

    // Convert screen coordinates to canvas coordinates
    const screenToCanvas = useCallback(
      (point: Point): Point => ({
        x: (point.x - viewport.x) / viewport.zoom,
        y: (point.y - viewport.y) / viewport.zoom,
      }),
      [viewport]
    );

    // Get pointer position in canvas coordinates
    const getCanvasPoint = useCallback(
      (stage: StageType | null): Point | null => {
        const position = stage?.getPointerPosition();
        if (!position) return null;
        return screenToCanvas(position);
      },
      [screenToCanvas]
    );

    // Handle stroke completion
    const handleStrokeComplete = useCallback(
      (stroke: StrokeData) => {
        const element: SketchElement = {
          id: stroke.id,
          kind: 'stroke',
          type: 'stroke',
          position: { x: 0, y: 0 },
          data: stroke,
        };
        onElementAdd?.(element);
      },
      [onElementAdd]
    );

    // Handle shape completion
    const handleShapeComplete = useCallback(
      (shape: ShapeData) => {
        const element: SketchElement = {
          id: shape.id,
          kind: 'shape',
          type: shape.type,
          position: { x: shape.x, y: shape.y },
          data: shape,
        };
        onElementAdd?.(element);
      },
      [onElementAdd]
    );

    // Use sketch tools hook
    const {
      isDrawing,
      currentStroke,
      currentShape,
      handlePointerDown,
      handlePointerMove,
      handlePointerUp,
    } = useSketchTools({
      activeTool,
      config: mergedConfig,
      onStrokeComplete: handleStrokeComplete,
      onShapeComplete: handleShapeComplete,
    });

    // Mouse event handlers
    const handleMouseDown = useCallback(
      (event: KonvaEventObject<MouseEvent>) => {
        if (!interactive) return;
        const point = getCanvasPoint(event.target.getStage());
        if (point) handlePointerDown(point);
      },
      [interactive, getCanvasPoint, handlePointerDown]
    );

    const handleMouseMove = useCallback(
      (event: KonvaEventObject<MouseEvent>) => {
        if (!interactive || !isDrawing) return;
        const point = getCanvasPoint(event.target.getStage());
        if (point) handlePointerMove(point);
      },
      [interactive, isDrawing, getCanvasPoint, handlePointerMove]
    );

    const handleMouseUp = useCallback(() => {
      if (!interactive) return;
      handlePointerUp();
    }, [interactive, handlePointerUp]);

    // Apply viewport transform
    useEffect(() => {
      const stage = stageRef.current;
      if (stage) {
        stage.x(viewport.x);
        stage.y(viewport.y);
        stage.scaleX(viewport.zoom);
        stage.scaleY(viewport.zoom);
        stage.batchDraw();
      }
    }, [viewport]);

    // Render grid
    const renderGrid = useMemo(() => {
      if (!showGrid) return null;
      const lines = [];
      const gridColor = 'rgba(0,0,0,0.1)';

      // Vertical lines
      for (let x = 0; x <= width; x += gridSize) {
        lines.push(
          <Line
            key={`v-${x}`}
            points={[x, 0, x, height]}
            stroke={gridColor}
            strokeWidth={1}
            listening={false}
          />
        );
      }

      // Horizontal lines
      for (let y = 0; y <= height; y += gridSize) {
        lines.push(
          <Line
            key={`h-${y}`}
            points={[0, y, width, y]}
            stroke={gridColor}
            strokeWidth={1}
            listening={false}
          />
        );
      }

      return lines;
    }, [showGrid, width, height, gridSize]);

    // Render element based on type
    const renderElement = useCallback((element: SketchElement) => {
      switch (element.type) {
        case 'stroke': {
          const data = element.data as StrokeData;
          return (
            <Line
              key={element.id}
              points={data.points}
              stroke={data.color}
              strokeWidth={data.strokeWidth}
              opacity={data.opacity || 1}
              tension={0.5}
              lineCap="round"
              lineJoin="round"
              globalCompositeOperation={
                data.tool === 'eraser' ? 'destination-out' : 'source-over'
              }
            />
          );
        }
        case 'rectangle': {
          const data = element.data as ShapeData;
          return (
            <Rect
              key={element.id}
              x={data.x}
              y={data.y}
              width={data.width}
              height={data.height}
              fill={data.fill}
              stroke={data.stroke}
              strokeWidth={data.strokeWidth}
              rotation={data.rotation || 0}
              cornerRadius={data.cornerRadius || 0}
            />
          );
        }
        case 'ellipse': {
          const data = element.data as ShapeData;
          return (
            <Ellipse
              key={element.id}
              x={data.x + data.width / 2}
              y={data.y + data.height / 2}
              radiusX={data.width / 2}
              radiusY={data.height / 2}
              fill={data.fill}
              stroke={data.stroke}
              strokeWidth={data.strokeWidth}
              rotation={data.rotation || 0}
            />
          );
        }
        case 'line': {
          const data = element.data as ShapeData;
          return (
            <Line
              key={element.id}
              points={data.points || [data.x, data.y, data.x + data.width, data.y + data.height]}
              stroke={data.stroke}
              strokeWidth={data.strokeWidth}
              lineCap="round"
            />
          );
        }
        case 'arrow': {
          const data = element.data as ShapeData;
          return (
            <Arrow
              key={element.id}
              points={data.points || [data.x, data.y, data.x + data.width, data.y + data.height]}
              stroke={data.stroke}
              strokeWidth={data.strokeWidth}
              fill={data.stroke}
              pointerLength={10}
              pointerWidth={10}
            />
          );
        }
        default:
          return null;
      }
    }, []);

    const isInteractive = interactive && activeTool !== 'select';

    return (
      <div
        ref={containerRef}
        className={className}
        style={{
          position: 'relative',
          width,
          height,
          backgroundColor,
          pointerEvents: isInteractive ? 'auto' : 'none',
          ...style,
        }}
        data-testid="sketch-canvas"
      >
        <Stage
          ref={stageRef}
          width={width}
          height={height}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
        >
          {/* Grid layer */}
          {showGrid && <Layer listening={false}>{renderGrid}</Layer>}

          {/* Elements layer */}
          <Layer>
            {/* Render saved elements */}
            {elements.map(renderElement)}

            {/* Render current stroke being drawn */}
            {currentStroke && (
              <Line
                points={currentStroke.points}
                stroke={currentStroke.color}
                strokeWidth={currentStroke.strokeWidth}
                opacity={currentStroke.opacity || 1}
                tension={0.5}
                lineCap="round"
                lineJoin="round"
                globalCompositeOperation={
                  currentStroke.tool === 'eraser' ? 'destination-out' : 'source-over'
                }
              />
            )}

            {/* Render current shape being drawn */}
            {currentShape && currentShape.type === 'rectangle' && (
              <Rect
                x={currentShape.x}
                y={currentShape.y}
                width={currentShape.width}
                height={currentShape.height}
                fill={currentShape.fill}
                stroke={currentShape.stroke}
                strokeWidth={currentShape.strokeWidth}
                dash={[5, 5]}
              />
            )}

            {currentShape && currentShape.type === 'ellipse' && (
              <Ellipse
                x={currentShape.x + currentShape.width / 2}
                y={currentShape.y + currentShape.height / 2}
                radiusX={currentShape.width / 2}
                radiusY={currentShape.height / 2}
                fill={currentShape.fill}
                stroke={currentShape.stroke}
                strokeWidth={currentShape.strokeWidth}
                dash={[5, 5]}
              />
            )}

            {currentShape && (currentShape.type === 'line' || currentShape.type === 'arrow') && (
              <Line
                points={currentShape.points || []}
                stroke={currentShape.stroke}
                strokeWidth={currentShape.strokeWidth}
                dash={[5, 5]}
                lineCap="round"
              />
            )}
          </Layer>
        </Stage>
      </div>
    );
  }
);

SketchCanvas.displayName = 'SketchCanvas';

export default SketchCanvas;
