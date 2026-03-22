import React, { useMemo } from 'react';

import type { CanvasElement } from '../../../components/canvas/workspace/canvasAtoms';

/**
 *
 */
export interface LODConfig {
  enableLOD: boolean;
  highDetailZoom: number; // Above this zoom, show full detail
  mediumDetailZoom: number; // Above this zoom, show medium detail
  lowDetailZoom: number; // Below this zoom, show minimal detail
  maxVisibleElements: number; // Maximum elements to render
}

/**
 *
 */
interface LODRendererProps {
  elements: CanvasElement[];
  viewport: { x: number; y: number; zoom: number };
  config: LODConfig;
  children: (visibleElements: CanvasElement[], lodLevel: 'high' | 'medium' | 'low') => React.ReactNode;
}

export const LODRenderer: React.FC<LODRendererProps> = ({
  elements,
  viewport,
  config,
  children,
}) => {
  const { visibleElements, lodLevel } = useMemo(() => {
    if (!config.enableLOD) {
      return {
        visibleElements: elements,
        lodLevel: 'high' as const,
      };
    }

    // Determine LOD level based on zoom
    let currentLodLevel: 'high' | 'medium' | 'low';
    if (viewport.zoom >= config.highDetailZoom) {
      currentLodLevel = 'high';
    } else if (viewport.zoom >= config.mediumDetailZoom) {
      currentLodLevel = 'medium';
    } else {
      currentLodLevel = 'low';
    }

    // Calculate viewport bounds
    const viewportBounds = {
      left: -viewport.x / viewport.zoom,
      top: -viewport.y / viewport.zoom,
      right: (-viewport.x + window.innerWidth) / viewport.zoom,
      bottom: (-viewport.y + window.innerHeight) / viewport.zoom,
    };

    // Add margin for smooth scrolling
    const margin = 200 / viewport.zoom;
    viewportBounds.left -= margin;
    viewportBounds.top -= margin;
    viewportBounds.right += margin;
    viewportBounds.bottom += margin;

    // Filter elements by visibility and importance
    let filtered = elements.filter((element) => {
      // Skip elements without position
      if (!element.position) return false;

      const elementBounds = {
        left: element.position.x,
        top: element.position.y,
        right: element.position.x + (element.size?.width || 100),
        bottom: element.position.y + (element.size?.height || 60),
      };

      // Check if element intersects with viewport
      return (
        elementBounds.right >= viewportBounds.left &&
        elementBounds.left <= viewportBounds.right &&
        elementBounds.bottom >= viewportBounds.top &&
        elementBounds.top <= viewportBounds.bottom
      );
    });

    // Apply LOD-specific filtering
    if (currentLodLevel === 'low') {
      // At low zoom, only show important elements
      filtered = filtered.filter((element) => {
        // Prioritize larger elements and specific types
        const area = (element.size?.width || 100) * (element.size?.height || 60);
        const isImportant = 
          element.kind === 'node' || 
          element.kind === 'component' ||
          area > 5000; // Large elements
        
        return isImportant;
      });
    } else if (currentLodLevel === 'medium') {
      // At medium zoom, hide very small details
      filtered = filtered.filter((element) => {
        if (element.kind === 'stroke') {
          // Hide small strokes
          const strokeLength = calculateStrokeLength(element);
          return strokeLength > 50;
        }
        return true;
      });
    }

    // Limit total number of visible elements
    if (filtered.length > config.maxVisibleElements) {
      // Sort by importance and take the most important ones
      filtered = filtered
        .sort((a, b) => calculateImportance(b) - calculateImportance(a))
        .slice(0, config.maxVisibleElements);
    }

    return {
      visibleElements: filtered,
      lodLevel: currentLodLevel,
    };
  }, [elements, viewport, config]);

  return <>{children(visibleElements, lodLevel)}</>;
};

/**
 * Calculate stroke length for LOD filtering
 */
function calculateStrokeLength(element: CanvasElement): number {
  if (element.kind !== 'stroke' || !element.data?.points) {
    return 0;
  }

  const points = element.data.points;
  let length = 0;

  for (let i = 1; i < points.length; i++) {
    const dx = points[i].x - points[i - 1].x;
    const dy = points[i].y - points[i - 1].y;
    length += Math.sqrt(dx * dx + dy * dy);
  }

  return length;
}

/**
 * Calculate element importance for LOD prioritization
 */
function calculateImportance(element: CanvasElement): number {
  let importance = 0;

  // Base importance by type
  switch (element.kind) {
    case 'node':
    case 'component':
      importance += 100;
      break;
    case 'shape':
      importance += 50;
      break;
    case 'stroke':
      importance += 10;
      break;
    default:
      importance += 20;
  }

  // Size factor
  if (element.size) {
    const area = element.size.width * element.size.height;
    importance += Math.min(area / 1000, 50); // Cap at 50 points
  }

  // Selection factor
  if (element.selected) {
    importance += 200;
  }

  // Label factor
  if (element.data?.label) {
    importance += 30;
  }

  return importance;
}

/**
 * Default LOD configuration
 */
export const defaultLODConfig: LODConfig = {
  enableLOD: true,
  highDetailZoom: 0.75,
  mediumDetailZoom: 0.5,
  lowDetailZoom: 0.25,
  maxVisibleElements: 500,
};
