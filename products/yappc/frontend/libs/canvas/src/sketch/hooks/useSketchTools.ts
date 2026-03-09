/**
 * @ghatana/yappc-sketch - useSketchTools Hook
 *
 * Production-grade hook for managing sketch tool interactions.
 *
 * @doc.type hook
 * @doc.purpose Sketch tool state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useRef, useState } from 'react';
import { simplifyPoints } from '../utils/smoothStroke';
import type { SketchTool, SketchToolConfig, StrokeData, ShapeData, Point } from '../types';

/**
 * Parameters for useSketchTools hook
 */
export interface UseSketchToolsParams {
  /** Currently active tool */
  activeTool: SketchTool;
  /** Tool configuration */
  config: SketchToolConfig;
  /** Callback when stroke is completed */
  onStrokeComplete: (stroke: StrokeData) => void;
  /** Callback when shape is completed */
  onShapeComplete: (shape: ShapeData) => void;
}

/**
 * Return type for useSketchTools hook
 */
export interface UseSketchToolsResult {
  /** Whether currently drawing */
  isDrawing: boolean;
  /** Current stroke being drawn */
  currentStroke: StrokeData | null;
  /** Current shape being drawn */
  currentShape: ShapeData | null;
  /** Handle pointer down event */
  handlePointerDown: (point: Point) => void;
  /** Handle pointer move event */
  handlePointerMove: (point: Point) => void;
  /** Handle pointer up event */
  handlePointerUp: () => void;
  /** Cancel current drawing */
  cancelDrawing: () => void;
}

/**
 * Generate unique ID for sketch elements
 */
const generateId = (prefix: string): string =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

/**
 * Hook for managing sketch tool interactions
 *
 * @param params - Hook parameters
 * @returns Sketch tool state and handlers
 *
 * @example
 * ```tsx
 * const {
 *   isDrawing,
 *   currentStroke,
 *   handlePointerDown,
 *   handlePointerMove,
 *   handlePointerUp
 * } = useSketchTools({
 *   activeTool: 'pen',
 *   config: { color: '#000', strokeWidth: 2 },
 *   onStrokeComplete: (stroke) => addElement(stroke),
 *   onShapeComplete: (shape) => addElement(shape)
 * });
 * ```
 */
export function useSketchTools({
  activeTool,
  config,
  onStrokeComplete,
  onShapeComplete,
}: UseSketchToolsParams): UseSketchToolsResult {
  const [isDrawing, setIsDrawing] = useState(false);
  const [currentStroke, setCurrentStroke] = useState<StrokeData | null>(null);
  const [currentShape, setCurrentShape] = useState<ShapeData | null>(null);

  const startPointRef = useRef<Point | null>(null);
  const pointsBufferRef = useRef<number[]>([]);

  const handlePointerDown = useCallback(
    (point: Point) => {
      // Skip for non-drawing tools
      if (activeTool === 'select' || activeTool === 'sticky' || activeTool === 'text') {
        return;
      }

      setIsDrawing(true);
      startPointRef.current = point;
      pointsBufferRef.current = [point.x, point.y];

      // Handle stroke-based tools
      if (activeTool === 'pen' || activeTool === 'highlighter' || activeTool === 'eraser') {
        setCurrentStroke({
          id: generateId('stroke'),
          points: [point.x, point.y],
          color: config.color,
          strokeWidth: config.strokeWidth,
          tool: activeTool as 'pen' | 'highlighter' | 'eraser',
          opacity: config.opacity,
          smoothed: false,
        });
      }
      // Handle shape-based tools
      else if (
        activeTool === 'rectangle' ||
        activeTool === 'ellipse' ||
        activeTool === 'line' ||
        activeTool === 'arrow'
      ) {
        setCurrentShape({
          id: generateId('shape'),
          type: activeTool as ShapeData['type'],
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
    [activeTool, config]
  );

  const handlePointerMove = useCallback(
    (point: Point) => {
      if (!isDrawing || !startPointRef.current) {
        return;
      }

      // Handle stroke-based tools
      if (activeTool === 'pen' || activeTool === 'highlighter' || activeTool === 'eraser') {
        pointsBufferRef.current.push(point.x, point.y);

        // Throttle updates for performance (update every 4 points)
        if (pointsBufferRef.current.length % 4 === 0) {
          setCurrentStroke((prev) => {
            if (!prev) return null;
            return {
              ...prev,
              points: [...pointsBufferRef.current],
            };
          });
        }
      }
      // Handle shape-based tools
      else if (
        activeTool === 'rectangle' ||
        activeTool === 'ellipse' ||
        activeTool === 'line' ||
        activeTool === 'arrow'
      ) {
        const startPoint = startPointRef.current;
        const width = point.x - startPoint.x;
        const height = point.y - startPoint.y;

        setCurrentShape((prev) => {
          if (!prev) return null;

          // For lines and arrows, store end point in points array
          if (activeTool === 'line' || activeTool === 'arrow') {
            return {
              ...prev,
              points: [startPoint.x, startPoint.y, point.x, point.y],
              width: Math.abs(width),
              height: Math.abs(height),
            };
          }

          // For rectangles and ellipses, handle negative dimensions
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
    [isDrawing, activeTool]
  );

  const handlePointerUp = useCallback(() => {
    if (!isDrawing) {
      return;
    }

    setIsDrawing(false);

    // Complete stroke
    if (currentStroke && (activeTool === 'pen' || activeTool === 'highlighter' || activeTool === 'eraser')) {
      // Simplify points for storage efficiency
      const simplifiedPoints = simplifyPoints(pointsBufferRef.current, 2);
      const finalStroke: StrokeData = {
        ...currentStroke,
        points: simplifiedPoints,
        smoothed: true,
      };
      onStrokeComplete(finalStroke);
      setCurrentStroke(null);
    }
    // Complete shape
    else if (
      currentShape &&
      (activeTool === 'rectangle' ||
        activeTool === 'ellipse' ||
        activeTool === 'line' ||
        activeTool === 'arrow')
    ) {
      // Only save if shape has meaningful size
      const minSize = activeTool === 'line' || activeTool === 'arrow' ? 10 : 5;
      if (currentShape.width > minSize || currentShape.height > minSize) {
        onShapeComplete(currentShape);
      }
      setCurrentShape(null);
    }

    // Reset refs
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
}

export default useSketchTools;
