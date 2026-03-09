/**
 * @doc.type component
 * @doc.purpose Smart alignment guides that appear during object dragging
 * @doc.layer core
 * @doc.pattern Visual Feedback
 */

import { Box } from '@ghatana/ui';
import React, { useMemo } from 'react';

import { CANVAS_TOKENS } from '../tokens/canvas-tokens';

import type { Node } from '@xyflow/react';


/**
 *
 */
export interface SmartGuidesProps {
  /** Currently dragged node */
  draggedNode: Node | null;
  
  /** All nodes on canvas */
  allNodes: Node[];
  
  /** Current viewport */
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  
  /** Snap threshold in pixels (default: 8) */
  snapThreshold?: number;
  
  /** Whether snapping is enabled */
  snapEnabled?: boolean;
}

/**
 *
 */
interface Guide {
  type: 'vertical' | 'horizontal';
  position: number; // Canvas coordinate
  snapPosition: number; // Adjusted position for snapping
}

/**
 * SmartGuides - Miro-style alignment guides during drag
 * 
 * Shows visual guides when dragging objects align with other objects
 * or canvas grid. Helps users create precise, aligned layouts.
 * 
 * @example
 * ```tsx
 * <SmartGuides
 *   draggedNode={currentlyDragging}
 *   allNodes={nodes}
 *   viewport={viewport}
 *   snapEnabled={snapToObjects}
 * />
 * ```
 */
export function SmartGuides({
  draggedNode,
  allNodes,
  viewport,
  snapThreshold = 8,
  snapEnabled = true,
}: SmartGuidesProps) {
  // Calculate guides based on alignment with other nodes
  const guides = useMemo(() => {
    if (!draggedNode || !snapEnabled) return [];
    
    const guides: Guide[] = [];
    const draggedWidth = (draggedNode.width as number) || 100;
    const draggedHeight = (draggedNode.height as number) || 100;
    
    allNodes.forEach((node) => {
      if (node.id === draggedNode.id) return;
      
      const nodeWidth = (node.width as number) || 100;
      const nodeHeight = (node.height as number) || 100;
      
      // Check horizontal alignment (vertical guides)
      // Left edge alignment
      const leftDiff = Math.abs(node.position.x - draggedNode.position.x);
      if (leftDiff < snapThreshold) {
        guides.push({
          type: 'vertical',
          position: node.position.x,
          snapPosition: node.position.x,
        });
      }
      
      // Center alignment
      const draggedCenterX = draggedNode.position.x + draggedWidth / 2;
      const nodeCenterX = node.position.x + nodeWidth / 2;
      const centerXDiff = Math.abs(nodeCenterX - draggedCenterX);
      if (centerXDiff < snapThreshold) {
        guides.push({
          type: 'vertical',
          position: nodeCenterX,
          snapPosition: nodeCenterX,
        });
      }
      
      // Right edge alignment
      const draggedRight = draggedNode.position.x + draggedWidth;
      const nodeRight = node.position.x + nodeWidth;
      const rightDiff = Math.abs(nodeRight - draggedRight);
      if (rightDiff < snapThreshold) {
        guides.push({
          type: 'vertical',
          position: nodeRight,
          snapPosition: nodeRight,
        });
      }
      
      // Check vertical alignment (horizontal guides)
      // Top edge alignment
      const topDiff = Math.abs(node.position.y - draggedNode.position.y);
      if (topDiff < snapThreshold) {
        guides.push({
          type: 'horizontal',
          position: node.position.y,
          snapPosition: node.position.y,
        });
      }
      
      // Middle alignment
      const draggedCenterY = draggedNode.position.y + draggedHeight / 2;
      const nodeCenterY = node.position.y + nodeHeight / 2;
      const centerYDiff = Math.abs(nodeCenterY - draggedCenterY);
      if (centerYDiff < snapThreshold) {
        guides.push({
          type: 'horizontal',
          position: nodeCenterY,
          snapPosition: nodeCenterY,
        });
      }
      
      // Bottom edge alignment
      const draggedBottom = draggedNode.position.y + draggedHeight;
      const nodeBottom = node.position.y + nodeHeight;
      const bottomDiff = Math.abs(nodeBottom - draggedBottom);
      if (bottomDiff < snapThreshold) {
        guides.push({
          type: 'horizontal',
          position: nodeBottom,
          snapPosition: nodeBottom,
        });
      }
    });
    
    // Remove duplicates
    return guides.filter(
      (guide, index, self) =>
        index ===
        self.findIndex(
          (g) =>
            g.type === guide.type &&
            Math.abs(g.position - guide.position) < 1
        )
    );
  }, [draggedNode, allNodes, snapThreshold, snapEnabled]);
  
  if (!draggedNode || guides.length === 0) {
    return null;
  }
  
  return (
    <Box
      className="absolute top-[0px] left-[0px] w-full h-full pointer-events-none" style={{ zIndex: CANVAS_TOKENS.Z_INDEX.SMART_GUIDES || 250 }}
    >
      <svg
        style={{
          width: '100%',
          height: '100%',
          overflow: 'visible',
        }}
      >
        {guides.map((guide, index) => {
          if (guide.type === 'vertical') {
            const screenX = (guide.position - viewport.x) * viewport.zoom;
            return (
              <line
                key={`v-${index}`}
                x1={screenX}
                y1={0}
                x2={screenX}
                y2="100%"
                stroke="#4F46E5"
                strokeWidth={1}
                strokeDasharray="4 4"
                opacity={0.8}
              />
            );
          } else {
            const screenY = (guide.position - viewport.y) * viewport.zoom;
            return (
              <line
                key={`h-${index}`}
                x1={0}
                y1={screenY}
                x2="100%"
                y2={screenY}
                stroke="#4F46E5"
                strokeWidth={1}
                strokeDasharray="4 4"
                opacity={0.8}
              />
            );
          }
        })}
      </svg>
    </Box>
  );
}

/**
 * Calculate snapped position for a node based on guides
 */
export function calculateSnappedPosition(
  position: { x: number; y: number },
  nodeSize: { width: number; height: number },
  allNodes: Node[],
  snapThreshold: number = 8
): { x: number; y: number } {
  let snappedX = position.x;
  let snappedY = position.y;
  
  allNodes.forEach((node) => {
    const nodeWidth = (node.width as number) || 100;
    const nodeHeight = (node.height as number) || 100;
    
    // Snap X
    const leftDiff = Math.abs(node.position.x - position.x);
    if (leftDiff < snapThreshold) {
      snappedX = node.position.x;
    }
    
    const centerXDiff = Math.abs(
      node.position.x + nodeWidth / 2 - (position.x + nodeSize.width / 2)
    );
    if (centerXDiff < snapThreshold) {
      snappedX = node.position.x + nodeWidth / 2 - nodeSize.width / 2;
    }
    
    const rightDiff = Math.abs(
      node.position.x + nodeWidth - (position.x + nodeSize.width)
    );
    if (rightDiff < snapThreshold) {
      snappedX = node.position.x + nodeWidth - nodeSize.width;
    }
    
    // Snap Y
    const topDiff = Math.abs(node.position.y - position.y);
    if (topDiff < snapThreshold) {
      snappedY = node.position.y;
    }
    
    const centerYDiff = Math.abs(
      node.position.y + nodeHeight / 2 - (position.y + nodeSize.height / 2)
    );
    if (centerYDiff < snapThreshold) {
      snappedY = node.position.y + nodeHeight / 2 - nodeSize.height / 2;
    }
    
    const bottomDiff = Math.abs(
      node.position.y + nodeHeight - (position.y + nodeSize.height)
    );
    if (bottomDiff < snapThreshold) {
      snappedY = node.position.y + nodeHeight - nodeSize.height;
    }
  });
  
  return { x: snappedX, y: snappedY };
}

/**
 * Snap position to grid
 */
export function snapToGrid(
  position: { x: number; y: number },
  gridSize: number
): { x: number; y: number } {
  return {
    x: Math.round(position.x / gridSize) * gridSize,
    y: Math.round(position.y / gridSize) * gridSize,
  };
}
