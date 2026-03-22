/**
 * Navigation types for portal and hierarchical canvas navigation
 */

/**
 *
 */
export interface PortalLink {
  kind: 'canvas' | 'url' | 'doc';
  targetId?: string;
  url?: string;
  openMode?: 'replace' | 'split' | 'modal';
  label?: string;
}

/**
 *
 */
export interface NavigationNode {
  canvasId: string;
  title?: string;
  parentId?: string;
}

/**
 *
 */
export interface BreadcrumbItem {
  canvasId: string;
  title: string;
  path: string;
}

/**
 *
 */
export interface NavigationState {
  currentCanvasId: string;
  history: string[];
  historyIndex: number;
  breadcrumbs: BreadcrumbItem[];
  splitView?: {
    leftCanvasId: string;
    rightCanvasId: string;
  };
}

/**
 *
 */
export interface LinkIndex {
  canvasId: string;
  outgoing: Map<string, PortalLink[]>; // elementId -> links
  incoming: Map<string, string[]>; // targetCanvasId -> sourceElementIds
}

/**
 *
 */
export interface CycleDetectionResult {
  hasCycle: boolean;
  cyclePath?: string[];
}

/**
 *
 */
export interface DeepLinkParams {
  canvasId: string;
  elementId?: string;
  viewport?: { x: number; y: number; zoom: number };
}
