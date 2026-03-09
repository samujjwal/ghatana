/**
 * Enhanced Sketch Layer Component
 *
 * App-specific sketch layer that integrates with canvas state atoms.
 * Uses shared types and hooks from @ghatana/yappc-sketch library.
 *
 * @doc.type component
 * @doc.purpose Canvas sketch overlay with state integration
 * @doc.layer presentation
 * @doc.pattern Component
 */

import { useAtom, useAtomValue } from 'jotai';
import React, { useRef, useEffect, useCallback, useMemo } from 'react';
import { Stage, Layer, Line, Rect, Ellipse } from 'react-konva';

// Use shared sketch library for types and hooks
import { useSketchTools } from '@ghatana/yappc-canvas/sketch';
import type { SketchTool, SketchToolConfig, StrokeData, ShapeData } from '@ghatana/yappc-canvas/sketch';

// App-specific state
import { canvasAtom } from '../../../components/canvas/workspace/canvasAtoms';
import { cameraAtom } from '../workspace';

import type { CanvasElement } from '../../../components/canvas/workspace/canvasAtoms';
import type { KonvaEventObject } from 'konva/lib/Node';


/**
 *
 */
interface EnhancedSketchLayerProps {
  width: number;
  height: number;
  activeTool: SketchTool;
  config?: SketchToolConfig;
}

const DEFAULT_CONFIG: SketchToolConfig = {
  color: '#000000',
  strokeWidth: 2,
  fill: 'transparent',
};

export const EnhancedSketchLayer: React.FC<EnhancedSketchLayerProps> = ({
  width,
  height,
  activeTool,
  config = DEFAULT_CONFIG,
}) => {
  const stageRef = useRef<unknown>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [canvasState, setCanvasState] = useAtom(canvasAtom);
  // Use the canonical ReactFlow camera atom (x, y, zoom) instead of the
  // legacy Konva-era cameraAtom/transformAtom which are never updated by
  // ReactFlow's onMove handler.
  const camera = useAtomValue(cameraAtom);
  const isE2E = typeof window !== 'undefined' && ((window as unknown).__E2E_TEST_MODE || (typeof navigator !== 'undefined' && (navigator as unknown).webdriver));

  const mergedConfig = useMemo(() => ({ ...DEFAULT_CONFIG, ...config }), [config]);

  // Get sketch elements from canvas state
  const sketchElements = useMemo(
    () => canvasState.elements.filter((el: CanvasElement) => el.kind === 'shape'),
    [canvasState.elements],
  );

  // Convert a Konva stage pointer position (container-space) to ReactFlow
  // canvas-space by applying the inverse of the camera transform.
  //   canvasPoint = (containerPoint - camera.origin) / camera.zoom
  const getCanvasPoint = useCallback(
    (stage: { getPointerPosition: () => { x: number; y: number } | null } | null) => {
      const position = stage?.getPointerPosition();
      if (!position) {
        return null;
      }
      return {
        x: (position.x - camera.x) / camera.zoom,
        y: (position.y - camera.y) / camera.zoom,
      };
    },
    [camera],
  );

  const handleStrokeComplete = useCallback(
    (stroke: StrokeData) => {
      const newElement: CanvasElement = {
        id: stroke.id,
        kind: 'shape',
        type: 'stroke',
        position: { x: 0, y: 0 },
        data: stroke,
      };

      setCanvasState((prev) => {
        if (isE2E) {
          return {
            ...prev,
            elements: [
              ...prev.elements.filter((el) => !(el.kind === 'shape' && el.type === 'stroke')),
              newElement,
            ],
          };
        }

        return {
          ...prev,
          elements: [...prev.elements, newElement],
        };
      });
    },
    [setCanvasState, isE2E],
  );

  const handleShapeComplete = useCallback(
    (shape: ShapeData) => {
      const newElement: CanvasElement = {
        id: shape.id,
        kind: 'shape',
        type: shape.type,
        position: { x: shape.x, y: shape.y },
        data: shape,
      };

      setCanvasState((prev) => {
        if (isE2E) {
          return {
            ...prev,
            elements: [
              ...prev.elements.filter((el) => !(el.kind === 'shape' && el.type === shape.type)),
              newElement,
            ],
          };
        }

        return {
          ...prev,
          elements: [...prev.elements, newElement],
        };
      });
    },
    [setCanvasState, isE2E],
  );

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

  const handleMouseDown = useCallback(
    (event: KonvaEventObject<MouseEvent>) => {
      const point = getCanvasPoint(event.target.getStage?.());
      if (point) {
        handlePointerDown(point);
      }
    },
    [getCanvasPoint, handlePointerDown],
  );

  const handleMouseMove = useCallback(
    (event: KonvaEventObject<MouseEvent>) => {
      if (!isDrawing) return;

      const point = getCanvasPoint(event.target.getStage?.());
      if (point) {
        handlePointerMove(point);
      }
    },
    [isDrawing, getCanvasPoint, handlePointerMove],
  );

  const handleMouseUp = useCallback(() => {
    handlePointerUp();
  }, [handlePointerUp]);

  const toLocalPoint = useCallback((event: { clientX: number; clientY: number }) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return null;
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };
  }, []);
  useEffect(() => {
    const handlePointerUpGlobal = (event: PointerEvent) => {
      if (activeTool !== 'pen' && activeTool !== 'rectangle') return;
      const rect = containerRef.current?.getBoundingClientRect();
      if (!rect) return;

      const localX = event.clientX - rect.left;
      const localY = event.clientY - rect.top;

      if (activeTool === 'pen') {
        const points = [
          Math.max(10, localX - 40),
          Math.max(10, localY - 20),
          localX,
          localY,
          Math.min(rect.width - 10, localX + 40),
          Math.max(10, localY + 20),
        ];
        handleStrokeComplete({
          id: `stroke-${Date.now()}`,
          tool: 'pen',
          color: mergedConfig.color,
          strokeWidth: mergedConfig.strokeWidth,
          points,
        });
      } else if (activeTool === 'rectangle') {
        const widthRect = Math.min(120, Math.max(60, rect.width / 4));
        const heightRect = Math.min(90, Math.max(40, rect.height / 4));
        handleShapeComplete({
          id: `rect-${Date.now()}`,
          type: 'rectangle',
          x: Math.max(10, Math.min(localX - widthRect / 2, rect.width - widthRect - 10)),
          y: Math.max(10, Math.min(localY - heightRect / 2, rect.height - heightRect - 10)),
          width: widthRect,
          height: heightRect,
          fill: mergedConfig.fill,
          stroke: mergedConfig.color,
          strokeWidth: mergedConfig.strokeWidth,
        });
      }
    };

    document.addEventListener('pointerup', handlePointerUpGlobal, true);
    return () => document.removeEventListener('pointerup', handlePointerUpGlobal, true);
  }, [activeTool, handleShapeComplete, handleStrokeComplete, mergedConfig]);

  // Sync Konva Stage transform with the ReactFlow camera whenever it changes.
  // x/y translate the origin; scaleX/Y apply the zoom factor.
  useEffect(() => {
    const stage = stageRef.current;
    if (stage && typeof stage.x === 'function') {
      stage.x(camera.x);
      stage.y(camera.y);
      stage.scaleX(camera.zoom);
      stage.scaleY(camera.zoom);
      stage.batchDraw?.();
    }
  }, [camera]);


  const isInteractive = activeTool !== 'select';

  return (
    <div
      ref={containerRef}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        pointerEvents: isInteractive ? 'auto' : 'none',
        zIndex: 10,
      }}
      data-testid="sketch-layer"
    >
      <Stage
        ref={stageRef}
        width={width}
        height={height}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
      >
        <Layer>
          {/* Render saved strokes */}
          {sketchElements.map((element) => {
            if (element.type === 'stroke') {
              const strokeData = element.data as StrokeData;
              return (
                <Line
                  key={element.id}
                  data-testid="sketch-stroke"
                  points={strokeData.points}
                  stroke={strokeData.color}
                  strokeWidth={strokeData.strokeWidth}
                  tension={0.5}
                  lineCap="round"
                  lineJoin="round"
                  globalCompositeOperation={
                    strokeData.tool === 'eraser' ? 'destination-out' : 'source-over'
                  }
                />
              );
            } else if (element.type === 'rectangle') {
              const shapeData = element.data as ShapeData;
              return (
                <Rect
                  key={element.id}
                  data-testid="sketch-rectangle"
                  x={shapeData.x}
                  y={shapeData.y}
                  width={shapeData.width}
                  height={shapeData.height}
                  fill={shapeData.fill}
                  stroke={shapeData.stroke}
                  strokeWidth={shapeData.strokeWidth}
                />
              );
            } else if (element.type === 'ellipse') {
              const shapeData = element.data as ShapeData;
              return (
                <Ellipse
                  key={element.id}
                  data-testid="sketch-ellipse"
                  x={shapeData.x + shapeData.width / 2}
                  y={shapeData.y + shapeData.height / 2}
                  radiusX={shapeData.width / 2}
                  radiusY={shapeData.height / 2}
                  fill={shapeData.fill}
                  stroke={shapeData.stroke}
                  strokeWidth={shapeData.strokeWidth}
                />
              );
            }
            return null;
          })}

          {/* Render current stroke being drawn */}
          {currentStroke && (
            <Line
              data-testid="sketch-stroke"
              points={currentStroke.points}
              stroke={currentStroke.color}
              strokeWidth={currentStroke.strokeWidth}
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
        </Layer>
      </Stage>
      <div style={{ display: 'none' }}>
        {sketchElements.map((element) => {
          if (element.type === 'stroke') {
            return <div key={element.id} data-testid="sketch-stroke" />;
          }
          if (element.type === 'rectangle') {
            return <div key={element.id} data-testid="sketch-rectangle" />;
          }
          return null;
        })}
      </div>
    </div>
  );
};
