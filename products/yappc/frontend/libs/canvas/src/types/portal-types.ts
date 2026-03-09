/**
 * Canvas Portal Types
 * 
 * Shared type definitions for portal-based navigation between canvases.
 * Extracted to prevent circular dependencies between hooks and state.
 * 
 * @doc.type types
 * @doc.purpose Portal navigation type definitions
 * @doc.layer canvas/types
 */

/**
 * Represents a portal connection between two canvases
 */
export interface PortalElement {
  id: string;
  type: 'portal';
  parentCanvasId: string;
  targetCanvasId: string;
  position: { x: number; y: number };
  data: {
    label: string;
    description?: string;
    thumbnail?: string;
    connectionPoint: 'entry' | 'exit';
  };
}

/**
 * Context for hierarchical canvas navigation
 */
export interface DrillDownContext {
  canvasStack: string[];
  currentCanvasId: string;
  parentCanvasId?: string;
  breadcrumbPath: Array<{ id: string; label: string }>;
}
