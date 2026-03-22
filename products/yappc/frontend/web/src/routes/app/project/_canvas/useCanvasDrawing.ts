/**
 * useCanvasDrawing Hook
 *
 * Manages all drawing state and handlers for freehand canvas drawing.
 * Includes pointer event handling, canvas 2D rendering, stroke persistence,
 * and RAF-based smooth drawing.
 *
 * @doc.type hook
 * @doc.purpose Canvas drawing state and event handlers
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import type { DrawingTool, Point } from './types';

interface UseCanvasDrawingOptions {
  canvas: {
    activeTool: string;
    drawings?: unknown[];
    startDrawing: (
      point: Point,
      tool: DrawingTool,
      color: string,
      width: number,
      opacity: number
    ) => void;
    continueDrawing: (point: Point) => void;
    endDrawing: () => any;
  };
  canvasRef: React.RefObject<HTMLDivElement | null>;
  drawingCanvasRef: React.RefObject<HTMLCanvasElement | null>;
}

export function useCanvasDrawing({
  canvas,
  canvasRef,
  drawingCanvasRef,
}: UseCanvasDrawingOptions) {
  // Drawing state
  const [isDrawing, setIsDrawing] = useState(false);
  const [drawingPath, setDrawingPath] = useState<Point[]>([]);
  const drawingPathRef = useRef<Point[]>([]);
  const [drawingTool, setDrawingTool] = useState<DrawingTool>('pen');
  const [drawingColor, setDrawingColor] = useState('#000000');
  const [drawingWidth, setDrawingWidth] = useState(2);
  const [drawingOpacity, setDrawingOpacity] = useState<number>(0.9);
  const [colorAnchor, setColorAnchor] = useState<HTMLElement | null>(null);

  const handleDrawingStyleChange = useCallback(
    (payload: {
      type?: DrawingTool;
      color?: string;
      width?: number;
      opacity?: number;
    }) => {
      if (payload.type) setDrawingTool(payload.type);
      if (payload.color) setDrawingColor(payload.color);
      if (typeof payload.width === 'number') setDrawingWidth(payload.width);
      if (typeof payload.opacity === 'number') setDrawingOpacity(payload.opacity);
    },
    []
  );

  // Pointer event handlers
  const handlePointerDown = useCallback(
    (event: React.PointerEvent) => {
      if (canvas.activeTool === 'draw') {
        event.preventDefault();
        event.stopPropagation();
        const bounds = canvasRef.current?.getBoundingClientRect();
        if (bounds) {
          const point = {
            x: event.clientX - bounds.left,
            y: event.clientY - bounds.top,
          };
          setIsDrawing(true);
          drawingPathRef.current = [point];
          setDrawingPath([point]);
          canvas.startDrawing(point, drawingTool, drawingColor, drawingWidth, drawingOpacity);
        }
      }
    },
    [canvas, drawingTool, drawingColor, drawingWidth, drawingOpacity, canvasRef]
  );

  const handlePointerMove = useCallback(
    (event: React.PointerEvent) => {
      if (isDrawing && canvas.activeTool === 'draw') {
        event.preventDefault();
        event.stopPropagation();

        const bounds = canvasRef.current?.getBoundingClientRect();
        if (bounds) {
          const point = {
            x: event.clientX - bounds.left,
            y: event.clientY - bounds.top,
          };
          drawingPathRef.current.push(point);
          canvas.continueDrawing(point);
        }
      }
    },
    [isDrawing, canvas, canvasRef]
  );

  // Draw to canvas - much faster than SVG for real-time drawing
  const drawToCanvas = useCallback(() => {
    const canvasEl = drawingCanvasRef.current;
    if (!canvasEl) return;

    const ctx = canvasEl.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvasEl.width, canvasEl.height);

    // Draw active drawing from drawingPathRef
    const activePath = isDrawing ? drawingPathRef.current : drawingPath;
    if (activePath && activePath.length > 1) {
      const isEraserActive = drawingTool === 'eraser';
      ctx.globalCompositeOperation = isEraserActive ? 'destination-out' : 'source-over';
      ctx.strokeStyle = drawingColor;
      ctx.lineWidth = drawingWidth;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.globalAlpha = drawingTool === 'highlighter' ? drawingOpacity : 1;
      ctx.beginPath();
      ctx.moveTo(activePath[0].x, activePath[0].y);
      for (let i = 1; i < activePath.length; i++) {
        ctx.lineTo(activePath[i].x, activePath[i].y);
      }
      ctx.stroke();
      ctx.globalAlpha = 1;
      ctx.globalCompositeOperation = 'source-over';
    }

    // Draw persisted drawings
    canvas.drawings?.forEach((stroke) => {
      ctx.globalCompositeOperation =
        stroke.tool === 'eraser' ? 'destination-out' : 'source-over';
      ctx.strokeStyle = stroke.color;
      ctx.lineWidth = stroke.width;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.globalAlpha =
        stroke.tool === 'highlighter'
          ? (stroke.opacity ?? 0.4)
          : (stroke.opacity ?? 1);
      ctx.beginPath();
      if (stroke.points.length > 0) {
        ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
        for (let i = 1; i < stroke.points.length; i++) {
          ctx.lineTo(stroke.points[i].x, stroke.points[i].y);
        }
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
      ctx.globalCompositeOperation = 'source-over';
    });
  }, [isDrawing, drawingPath, drawingColor, drawingWidth, drawingOpacity, drawingTool, canvas.drawings, drawingCanvasRef]);

  // Continuous RAF redraw during drawing
  useEffect(() => {
    if (!isDrawing) return;
    let rafId: number;
    const redraw = () => {
      drawToCanvas();
      rafId = requestAnimationFrame(redraw);
    };
    rafId = requestAnimationFrame(redraw);
    return () => cancelAnimationFrame(rafId);
  }, [isDrawing, drawToCanvas]);

  // Final redraw when drawing completes
  useEffect(() => {
    if (isDrawing) return;
    drawToCanvas();
  }, [isDrawing, canvas.drawings, drawToCanvas]);

  // Setup canvas size
  useEffect(() => {
    const canvasEl = drawingCanvasRef.current;
    if (!canvasEl) return;

    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;

    canvasEl.width = rect.width * window.devicePixelRatio;
    canvasEl.height = rect.height * window.devicePixelRatio;

    const ctx = canvasEl.getContext('2d');
    if (ctx) {
      ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    }
    drawToCanvas();
  }, [drawToCanvas, canvasRef, drawingCanvasRef]);

  // Redraw on window resize
  useEffect(() => {
    const handleResize = () => {
      const canvasEl = drawingCanvasRef.current;
      if (!canvasEl) return;

      const rect = canvasRef.current?.getBoundingClientRect();
      if (!rect) return;

      canvasEl.width = rect.width * window.devicePixelRatio;
      canvasEl.height = rect.height * window.devicePixelRatio;

      const ctx = canvasEl.getContext('2d');
      if (ctx) {
        ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
      }
      drawToCanvas();
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [drawToCanvas, canvasRef, drawingCanvasRef]);

  const handlePointerUp = useCallback(() => {
    if (isDrawing && drawingPathRef.current.length > 1) {
      const stroke = canvas.endDrawing();
      if (stroke) {
        const canvasEl = drawingCanvasRef.current;
        if (canvasEl) {
          const ctx = canvasEl.getContext('2d');
          if (ctx) {
            ctx.strokeStyle = stroke.color;
            ctx.lineWidth = stroke.width;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.globalAlpha = stroke.tool === 'highlighter' ? 0.4 : 1;
            ctx.beginPath();
            if (stroke.points.length > 0) {
              ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
              for (let i = 1; i < stroke.points.length; i++) {
                ctx.lineTo(stroke.points[i].x, stroke.points[i].y);
              }
              ctx.stroke();
            }
            ctx.globalAlpha = 1;
          }
        }
      }
    }

    setIsDrawing(false);
    setDrawingPath([]);
    drawingPathRef.current = [];
  }, [isDrawing, canvas, drawingCanvasRef]);

  return {
    isDrawing,
    drawingTool,
    drawingColor,
    drawingWidth,
    drawingOpacity,
    colorAnchor,
    setDrawingTool,
    setDrawingColor,
    setDrawingWidth,
    setDrawingOpacity,
    setColorAnchor,
    handleDrawingStyleChange,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
  };
}
