import { useCallback, useRef, useState } from 'react';

// Import from shared library instead of local files
import { simplifyPoints } from '@ghatana/yappc-canvas/sketch';
import type { SketchTool, SketchToolConfig, StrokeData, ShapeData, Point } from '@ghatana/yappc-canvas/sketch';

/**
 *
 */
interface UseSketchToolsParams {
  activeTool: SketchTool;
  config: SketchToolConfig;
  onStrokeComplete: (stroke: StrokeData) => void;
  onShapeComplete: (shape: ShapeData) => void;
}

/**
 *
 */
export interface UseSketchToolsResult {
  isDrawing: boolean;
  currentStroke: StrokeData | null;
  currentShape: ShapeData | null;
  handlePointerDown: (point: Point) => void;
  handlePointerMove: (point: Point) => void;
  handlePointerUp: () => void;
  cancelDrawing: () => void;
}

export const useSketchTools = ({
  activeTool,
  config,
  onStrokeComplete,
  onShapeComplete,
}: UseSketchToolsParams): UseSketchToolsResult => {
  const [isDrawing, setIsDrawing] = useState(false);
  const [currentStroke, setCurrentStroke] = useState<StrokeData | null>(null);
  const [currentShape, setCurrentShape] = useState<ShapeData | null>(null);

  const startPointRef = useRef<Point | null>(null);
  const pointsBufferRef = useRef<number[]>([]);

  const handlePointerDown = useCallback(
    (point: Point) => {
      if (activeTool === 'select' || activeTool === 'sticky') {
        return;
      }

      setIsDrawing(true);
      startPointRef.current = point;
      pointsBufferRef.current = [point.x, point.y];

      if (activeTool === 'pen' || activeTool === 'eraser') {
        const strokeId = `stroke-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
        setCurrentStroke({
          id: strokeId,
          points: [point.x, point.y],
          color: config.color,
          strokeWidth: config.strokeWidth,
          tool: activeTool,
          smoothed: false,
        });
      } else if (activeTool === 'rectangle' || activeTool === 'ellipse') {
        const shapeId = `shape-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
        setCurrentShape({
          id: shapeId,
          type: activeTool,
          x: point.x,
          y: point.y,
          width: 0,
          height: 0,
          fill: config.fill,
          stroke: config.color,
          strokeWidth: config.strokeWidth,
        });
      }
    },
    [activeTool, config],
  );

  const handlePointerMove = useCallback(
    (point: Point) => {
      if (!isDrawing || !startPointRef.current) {
        return;
      }

      if (activeTool === 'pen' || activeTool === 'eraser') {
        pointsBufferRef.current.push(point.x, point.y);

        // Throttle updates for performance
        if (pointsBufferRef.current.length % 4 === 0) {
          setCurrentStroke((prev) => {
            if (!prev) return null;
            return {
              ...prev,
              points: [...pointsBufferRef.current],
            };
          });
        }
      } else if (activeTool === 'rectangle' || activeTool === 'ellipse') {
        const startPoint = startPointRef.current;
        const width = point.x - startPoint.x;
        const height = point.y - startPoint.y;

        setCurrentShape((prev) => {
          if (!prev) return null;
          return {
            ...prev,
            x: width < 0 ? point.x : startPoint.x,
            y: height < 0 ? point.y : startPoint.y,
            width: Math.abs(width),
            height: Math.abs(height),
          };
        });
      }
    },
    [isDrawing, activeTool],
  );

  const handlePointerUp = useCallback(() => {
    if (!isDrawing) {
      return;
    }

    setIsDrawing(false);

    if (currentStroke && (activeTool === 'pen' || activeTool === 'eraser')) {
      // Simplify points before saving
      const simplifiedPoints = simplifyPoints(pointsBufferRef.current, 2);
      const finalStroke: StrokeData = {
        ...currentStroke,
        points: simplifiedPoints,
        smoothed: true,
      };
      onStrokeComplete(finalStroke);
      setCurrentStroke(null);
    } else if (currentShape && (activeTool === 'rectangle' || activeTool === 'ellipse')) {
      // Only save if shape has meaningful size
      if (currentShape.width > 5 && currentShape.height > 5) {
        onShapeComplete(currentShape);
      }
      setCurrentShape(null);
    }

    startPointRef.current = null;
    pointsBufferRef.current = [];
  }, [isDrawing, currentStroke, currentShape, activeTool, onStrokeComplete, onShapeComplete]);

  const cancelDrawing = useCallback(() => {
    setIsDrawing(false);
    setCurrentStroke(null);
    setCurrentShape(null);
    startPointRef.current = null;
    pointsBufferRef.current = [];
  }, []);

  return {
    isDrawing,
    currentStroke,
    currentShape,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    cancelDrawing,
  };
};
