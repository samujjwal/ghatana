/**
 * SimulationCanvas - WebGL/Canvas rendering component for simulations.
 *
 * @doc.type component
 * @doc.purpose Renders simulation keyframes with entity visualization
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useCallback, useState } from 'react';
import type { SimKeyframe, SimEntityBase, SimVisualStyle } from '@ghatana/tutorputor-contracts/v1/simulation';

/**
 * Canvas props.
 */
interface SimulationCanvasProps {
  /** Current keyframe to render */
  keyframe: SimKeyframe;
  /** Canvas width */
  width?: number;
  /** Canvas height */
  height?: number;
  /** Whether to use WebGL (falls back to 2D) */
  useWebGL?: boolean;
  /** Background color */
  backgroundColor?: string;
  /** Grid visibility */
  showGrid?: boolean;
  /** Click handler for entities */
  onEntityClick?: (entity: SimEntityBase) => void;
  /** Hover handler for entities */
  onEntityHover?: (entity: SimEntityBase | null) => void;
  /** Zoom level (1.0 = 100%) */
  zoom?: number;
  /** Pan offset */
  panOffset?: { x: number; y: number };
  /** Enable pan/zoom controls */
  enableControls?: boolean;
  /** Accessibility label */
  ariaLabel?: string;
}

/**
 * Shape renderers for different entity shapes.
 */
const shapeRenderers: Record<string, (ctx: CanvasRenderingContext2D, x: number, y: number, size: number, style: SimVisualStyle) => void> = {
  rectangle: (ctx, x, y, size, style) => {
    const w = size * 40;
    const h = size * 30;
    ctx.fillRect(x - w / 2, y - h / 2, w, h);
    if (style.stroke) {
      ctx.strokeStyle = style.stroke.color ?? '#000';
      ctx.lineWidth = style.stroke.width ?? 1;
      ctx.strokeRect(x - w / 2, y - h / 2, w, h);
    }
  },
  circle: (ctx, x, y, size, style) => {
    const radius = size * 20;
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fill();
    if (style.stroke) {
      ctx.strokeStyle = style.stroke.color ?? '#000';
      ctx.lineWidth = style.stroke.width ?? 1;
      ctx.stroke();
    }
  },
  triangle: (ctx, x, y, size, style) => {
    const s = size * 30;
    ctx.beginPath();
    ctx.moveTo(x, y - s);
    ctx.lineTo(x + s, y + s);
    ctx.lineTo(x - s, y + s);
    ctx.closePath();
    ctx.fill();
    if (style.stroke) {
      ctx.strokeStyle = style.stroke.color ?? '#000';
      ctx.lineWidth = style.stroke.width ?? 1;
      ctx.stroke();
    }
  },
  diamond: (ctx, x, y, size, style) => {
    const s = size * 25;
    ctx.beginPath();
    ctx.moveTo(x, y - s);
    ctx.lineTo(x + s, y);
    ctx.lineTo(x, y + s);
    ctx.lineTo(x - s, y);
    ctx.closePath();
    ctx.fill();
    if (style.stroke) {
      ctx.strokeStyle = style.stroke.color ?? '#000';
      ctx.lineWidth = style.stroke.width ?? 1;
      ctx.stroke();
    }
  },
  hexagon: (ctx, x, y, size, style) => {
    const r = size * 20;
    ctx.beginPath();
    for (let i = 0; i < 6; i++) {
      const angle = (Math.PI / 3) * i - Math.PI / 6;
      const px = x + r * Math.cos(angle);
      const py = y + r * Math.sin(angle);
      if (i === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fill();
    if (style.stroke) {
      ctx.strokeStyle = style.stroke.color ?? '#000';
      ctx.lineWidth = style.stroke.width ?? 1;
      ctx.stroke();
    }
  },
};

/**
 * SimulationCanvas component.
 */
export const SimulationCanvas: React.FC<SimulationCanvasProps> = ({
  keyframe,
  width = 800,
  height = 600,
  useWebGL = false,
  backgroundColor = '#f8fafc',
  showGrid = true,
  onEntityClick,
  onEntityHover,
  zoom = 1,
  panOffset = { x: 0, y: 0 },
  enableControls = true,
  ariaLabel = 'Simulation Canvas',
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [hoveredEntity, setHoveredEntity] = useState<SimEntityBase | null>(null);
  const [localZoom, setLocalZoom] = useState(zoom);
  const [localPan, setLocalPan] = useState(panOffset);
  const [isDragging, setIsDragging] = useState(false);
  const dragStart = useRef({ x: 0, y: 0 });

  /**
   * Convert screen coordinates to world coordinates.
   */
  const screenToWorld = useCallback(
    (screenX: number, screenY: number) => {
      const centerX = width / 2;
      const centerY = height / 2;
      return {
        x: (screenX - centerX - localPan.x) / localZoom,
        y: (screenY - centerY - localPan.y) / localZoom,
      };
    },
    [width, height, localZoom, localPan]
  );

  /**
   * Convert world coordinates to screen coordinates.
   */
  const worldToScreen = useCallback(
    (worldX: number, worldY: number) => {
      const centerX = width / 2;
      const centerY = height / 2;
      return {
        x: worldX * localZoom + centerX + localPan.x,
        y: worldY * localZoom + centerY + localPan.y,
      };
    },
    [width, height, localZoom, localPan]
  );

  /**
   * Find entity at screen position.
   */
  const findEntityAtPosition = useCallback(
    (screenX: number, screenY: number): SimEntityBase | null => {
      const world = screenToWorld(screenX, screenY);

      for (let i = keyframe.entities.length - 1; i >= 0; i--) {
        const entity = keyframe.entities[i];
        if (!entity.position) continue;

        const size = (entity.visual?.size ?? 1) * 20;
        const dx = Math.abs(world.x - entity.position.x);
        const dy = Math.abs(world.y - entity.position.y);

        if (dx < size && dy < size) {
          return entity;
        }
      }

      return null;
    },
    [keyframe.entities, screenToWorld]
  );

  /**
   * Draw grid.
   */
  const drawGrid = useCallback(
    (ctx: CanvasRenderingContext2D) => {
      if (!showGrid) return;

      const gridSize = 50 * localZoom;
      const offsetX = (localPan.x % gridSize + gridSize) % gridSize;
      const offsetY = (localPan.y % gridSize + gridSize) % gridSize;

      ctx.strokeStyle = '#e2e8f0';
      ctx.lineWidth = 1;
      ctx.beginPath();

      // Vertical lines
      for (let x = offsetX; x < width; x += gridSize) {
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
      }

      // Horizontal lines
      for (let y = offsetY; y < height; y += gridSize) {
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
      }

      ctx.stroke();
    },
    [showGrid, width, height, localZoom, localPan]
  );

  /**
   * Draw entity.
   */
  const drawEntity = useCallback(
    (ctx: CanvasRenderingContext2D, entity: SimEntityBase, isHovered: boolean) => {
      if (!entity.position) return;

      const screen = worldToScreen(entity.position.x, entity.position.y);
      const style: SimVisualStyle = {
        color: '#4A90D9',
        size: 1,
        shape: 'rectangle',
        opacity: 1,
        ...entity.visual,
      };

      const scaledSize = (style.size ?? 1) * localZoom;

      // Apply opacity
      ctx.globalAlpha = style.opacity ?? 1;

      // Fill color
      ctx.fillStyle = style.color ?? '#4A90D9';

      // Highlight if hovered
      if (isHovered) {
        ctx.shadowColor = style.color ?? '#4A90D9';
        ctx.shadowBlur = 15;
      }

      // Render shape
      const renderer = shapeRenderers[style.shape ?? 'rectangle'] ?? shapeRenderers.rectangle;
      renderer(ctx, screen.x, screen.y, scaledSize, style);

      // Reset shadow
      ctx.shadowBlur = 0;

      // Draw label
      if (style.label?.visible !== false && entity.label) {
        ctx.globalAlpha = 1;
        ctx.fillStyle = style.label?.color ?? '#1e293b';
        ctx.font = `${(style.label?.fontSize ?? 12) * localZoom}px ${style.label?.fontFamily ?? 'Inter, sans-serif'}`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText(entity.label, screen.x, screen.y + scaledSize * 25);
      }

      ctx.globalAlpha = 1;
    },
    [worldToScreen, localZoom]
  );

  /**
   * Draw annotations.
   */
  const drawAnnotations = useCallback(
    (ctx: CanvasRenderingContext2D) => {
      if (!keyframe.annotations) return;

      for (const annotation of keyframe.annotations) {
        const screen = worldToScreen(
          annotation.position?.x ?? 0,
          annotation.position?.y ?? 0
        );

        // Draw callout background
        ctx.fillStyle = 'rgba(255, 255, 255, 0.95)';
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 1;

        const padding = 10;
        ctx.font = '14px Inter, sans-serif';
        const textWidth = ctx.measureText(annotation.text).width;
        const boxWidth = textWidth + padding * 2;
        const boxHeight = 28;

        ctx.beginPath();
        ctx.roundRect(screen.x - boxWidth / 2, screen.y - boxHeight / 2, boxWidth, boxHeight, 4);
        ctx.fill();
        ctx.stroke();

        // Draw text
        ctx.fillStyle = '#1e293b';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(annotation.text, screen.x, screen.y);
      }
    },
    [keyframe.annotations, worldToScreen]
  );

  /**
   * Main render loop.
   */
  const render = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Clear canvas
    ctx.fillStyle = backgroundColor;
    ctx.fillRect(0, 0, width, height);

    // Draw grid
    drawGrid(ctx);

    // Draw entities
    for (const entity of keyframe.entities) {
      drawEntity(ctx, entity, entity.id === hoveredEntity?.id);
    }

    // Draw annotations
    drawAnnotations(ctx);
  }, [keyframe, width, height, backgroundColor, drawGrid, drawEntity, drawAnnotations, hoveredEntity]);

  /**
   * Handle mouse move.
   */
  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      const rect = canvasRef.current?.getBoundingClientRect();
      if (!rect) return;

      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;

      if (isDragging && enableControls) {
        setLocalPan({
          x: localPan.x + (x - dragStart.current.x),
          y: localPan.y + (y - dragStart.current.y),
        });
        dragStart.current = { x, y };
      } else {
        const entity = findEntityAtPosition(x, y);
        if (entity !== hoveredEntity) {
          setHoveredEntity(entity);
          onEntityHover?.(entity);
        }
      }
    },
    [isDragging, enableControls, localPan, findEntityAtPosition, hoveredEntity, onEntityHover]
  );

  /**
   * Handle mouse down.
   */
  const handleMouseDown = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      const rect = canvasRef.current?.getBoundingClientRect();
      if (!rect) return;

      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;

      if (e.button === 0) {
        const entity = findEntityAtPosition(x, y);
        if (entity) {
          onEntityClick?.(entity);
        } else if (enableControls) {
          setIsDragging(true);
          dragStart.current = { x, y };
        }
      }
    },
    [findEntityAtPosition, onEntityClick, enableControls]
  );

  /**
   * Handle mouse up.
   */
  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  /**
   * Handle wheel zoom.
   */
  const handleWheel = useCallback(
    (e: React.WheelEvent<HTMLCanvasElement>) => {
      if (!enableControls) return;

      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      setLocalZoom((prev) => Math.max(0.1, Math.min(5, prev * delta)));
    },
    [enableControls]
  );

  // Render on state changes
  useEffect(() => {
    render();
  }, [render]);

  // Update local state when props change
  useEffect(() => {
    setLocalZoom(zoom);
  }, [zoom]);

  useEffect(() => {
    setLocalPan(panOffset);
  }, [panOffset]);

  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={height}
      onMouseMove={handleMouseMove}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
      onWheel={handleWheel}
      style={{
        cursor: isDragging ? 'grabbing' : hoveredEntity ? 'pointer' : enableControls ? 'grab' : 'default',
        borderRadius: '8px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      }}
      role="img"
      aria-label={ariaLabel}
      tabIndex={0}
    />
  );
};

export default SimulationCanvas;
