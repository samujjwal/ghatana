/**
 * EnhancedMinimap Component
 * 
 * Enhanced minimap with hierarchy visualization
 * 
 * Features:
 * - Hierarchy visualization with heat map
 * - Current viewport indicator (blue rectangle)
 * - Parent/sibling frame outlines
 * - Click to jump, drag to pan
 * - Zoom levels color-coded by depth
 * - Toggle hierarchy view (flat vs nested)
 * - 200×150px size, bottom-right placement
 * 
 * @doc.type component
 * @doc.purpose Enhanced minimap with hierarchy
 * @doc.layer components
 */

import { Box, IconButton, Tooltip } from '@ghatana/ui';
import { Layers as LayersIcon, ListTodo as FlatIcon } from 'lucide-react';
import React, { useState, useRef, useCallback } from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

const { SPACING, COLORS, Z_INDEX, SHADOWS, RADIUS } = CANVAS_TOKENS;

export interface MinimapNode {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
  type: 'frame' | 'node';
  parentId?: string;
  isCurrentFrame?: boolean;
}

export interface MinimapViewport {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface EnhancedMinimapProps {
  /** All nodes to display */
  nodes: MinimapNode[];

  /** Current viewport */
  viewport: MinimapViewport;

  /** Canvas bounds */
  canvasBounds: {
    minX: number;
    maxX: number;
    minY: number;
    maxY: number;
  };

  /** Callback when viewport changed */
  onViewportChange?: (viewport: MinimapViewport) => void;

  /** Callback when node clicked */
  onNodeClick?: (nodeId: string) => void;

  /** Minimap width (default: 200px) */
  width?: number;

  /** Minimap height (default: 150px) */
  height?: number;

  /** Show hierarchy heat map */
  showHierarchy?: boolean;
}

/**
 * Get color for depth level (heat map)
 */
function getDepthColor(depth: number): string {
  const colors = [
    COLORS.PRIMARY,           // Level 0
    COLORS.PHASE_INTENT,      // Level 1
    COLORS.PHASE_SHAPE,       // Level 2
    COLORS.PHASE_BUILD,       // Level 3
    COLORS.PHASE_RUN,         // Level 4
    COLORS.PHASE_OBSERVE,     // Level 5+
  ];

  return colors[Math.min(depth, colors.length - 1)];
}

/**
 * EnhancedMinimap - Minimap with hierarchy visualization
 */
export function EnhancedMinimap({
  nodes,
  viewport,
  canvasBounds,
  onViewportChange,
  onNodeClick,
  width = 200,
  height = 150,
  showHierarchy: initialShowHierarchy = true,
}: EnhancedMinimapProps) {
  const [showHierarchy, setShowHierarchy] = useState(initialShowHierarchy);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Calculate scale to fit canvas bounds in minimap
  const scale = Math.min(
    width / (canvasBounds.maxX - canvasBounds.minX || 1),
    height / (canvasBounds.maxY - canvasBounds.minY || 1)
  );

  // Transform canvas coordinates to minimap coordinates
  const toMinimapCoords = useCallback(
    (x: number, y: number) => ({
      x: (x - canvasBounds.minX) * scale,
      y: (y - canvasBounds.minY) * scale,
    }),
    [canvasBounds, scale]
  );

  // Transform minimap coordinates to canvas coordinates
  const toCanvasCoords = useCallback(
    (x: number, y: number) => ({
      x: x / scale + canvasBounds.minX,
      y: y / scale + canvasBounds.minY,
    }),
    [canvasBounds, scale]
  );

  // Handle minimap click/drag
  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (!containerRef.current) return;

      const rect = containerRef.current.getBoundingClientRect();
      const minimapX = e.clientX - rect.left;
      const minimapY = e.clientY - rect.top;

      const { x, y } = toCanvasCoords(minimapX, minimapY);

      // Center viewport on click point
      onViewportChange?.({
        x: x - viewport.width / 2,
        y: y - viewport.height / 2,
        width: viewport.width,
        height: viewport.height,
      });

      setIsDragging(true);
    },
    [viewport, toCanvasCoords, onViewportChange]
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (!isDragging || !containerRef.current) return;

      const rect = containerRef.current.getBoundingClientRect();
      const minimapX = e.clientX - rect.left;
      const minimapY = e.clientY - rect.top;

      const { x, y } = toCanvasCoords(minimapX, minimapY);

      onViewportChange?.({
        x: x - viewport.width / 2,
        y: y - viewport.height / 2,
        width: viewport.width,
        height: viewport.height,
      });
    },
    [isDragging, viewport, toCanvasCoords, onViewportChange]
  );

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  // Viewport rectangle in minimap coordinates
  const viewportRect = {
    ...toMinimapCoords(viewport.x, viewport.y),
    width: viewport.width * scale,
    height: viewport.height * scale,
  };

  return (
    <Box
      className="fixed" style={{ bottom: SPACING.MD + 50, top: SPACING.XS, right: SPACING.XS }}
    >
      {/* Toggle Button */}
      <Box
        className="absolute z-[1]" >
        <Tooltip title={showHierarchy ? 'Flat view' : 'Hierarchy view'}>
          <IconButton
            size="sm"
            onClick={() => setShowHierarchy(!showHierarchy)}
            className="w-[24px] h-[24px]" style={{ backgroundColor: COLORS.NEUTRAL_100 }}
          >
            {showHierarchy ? <FlatIcon className="text-sm" /> : <LayersIcon className="text-sm" />}
          </IconButton>
        </Tooltip>
      </Box>

      {/* Minimap Canvas */}
      <Box
        ref={containerRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        className="w-full h-full relative" style={{ cursor: isDragging ? 'grabbing' : 'grab', backgroundColor: getDepthColor(depth) }}
      >
        {/* Nodes */}
        {nodes.map((node) => {
          const pos = toMinimapCoords(node.x, node.y);
          const nodeWidth = node.width * scale;
          const nodeHeight = node.height * scale;

          // Skip very small nodes
          if (nodeWidth < 1 || nodeHeight < 1) return null;

          const isFrame = node.type === 'frame';
          const color = showHierarchy ? getDepthColor(node.depth) : COLORS.NEUTRAL_400;
          const opacity = showHierarchy ? 0.6 - node.depth * 0.1 : 0.4;

          return (
            <Box
              key={node.id}
              onClick={(e) => {
                e.stopPropagation();
                onNodeClick?.(node.id);
              }}
              className="absolute" style={{ left: pos.x, top: pos.y, width: nodeWidth, height: nodeHeight, backgroundColor: isFrame ? 'transparent' : color, border: isFrame ? `1px solid ${color}` : 'none' }}
            />
          );
        })}

        {/* Viewport Rectangle */}
        <Box
          className="absolute" style={{ left: viewportRect.x, top: viewportRect.y, width: viewportRect.width, height: viewportRect.height, border: `2px solid ${COLORS.PRIMARY}` }}
        />
      </Box>

      {/* Legend (if hierarchy view) */}
      {showHierarchy && (
        <Box
          className="absolute flex text-[9px]" style={{ bottom: SPACING.XS, left: SPACING.XS, gap: SPACING.XS, color: COLORS.TEXT_SECONDARY, backgroundColor: COLORS.PANEL_BG_LIGHT }}
        >
          {[0, 1, 2, 3].map((depth) => (
            <Box key={depth} className="flex items-center gap-4">
              <Box
                className="rounded-full w-[8px] h-[8px]" />
              <span>L{depth}</span>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
