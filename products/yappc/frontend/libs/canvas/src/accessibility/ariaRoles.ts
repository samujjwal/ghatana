/**
 * ARIA Roles and Semantic Accessibility for Canvas
 *
 * Provides ARIA role management, relationship tracking, and screen reader
 * announcements for canvas diagram elements.
 *
 * @module canvas/accessibility/ariaRoles
 */

import { useEffect, useRef, useCallback } from 'react';

import type { Node, Edge } from '@xyflow/react';

/**
 * ARIA role for canvas diagram elements
 */
export type CanvasAriaRole =
  | 'graphnode'
  | 'graphedge'
  | 'graph'
  | 'group'
  | 'toolbar'
  | 'menu'
  | 'menuitem';

/**
 * ARIA properties for canvas elements
 */
export interface AriaProperties {
  /** Element role */
  role: CanvasAriaRole;
  /** Accessible label */
  'aria-label'?: string;
  /** Accessible description */
  'aria-describedby'?: string;
  /** Selected state */
  'aria-selected'?: boolean;
  /** Expanded state (for groups) */
  'aria-expanded'?: boolean;
  /** Position in set */
  'aria-posinset'?: number;
  /** Total set size */
  'aria-setsize'?: number;
  /** Flow relationships (connected nodes) */
  'aria-flowto'?: string;
  /** Owns relationships (child elements) */
  'aria-owns'?: string;
  /** Level in hierarchy */
  'aria-level'?: number;
}

/**
 * Generate ARIA properties for a canvas node
 */
export function getNodeAriaProps(
  node: Node,
  context: {
    selectedIds?: Set<string>;
    totalNodes?: number;
    nodeIndex?: number;
    connectedNodeIds?: string[];
    childNodeIds?: string[];
  } = {}
): AriaProperties {
  const {
    selectedIds = new Set(),
    totalNodes,
    nodeIndex,
    connectedNodeIds = [],
    childNodeIds = [],
  } = context;

  const label = node.data?.label || node.id;
  const isSelected = selectedIds.has(node.id);

  const props: AriaProperties = {
    role: 'graphnode',
    'aria-label': `Node: ${label}`,
    'aria-selected': isSelected,
  };

  if (totalNodes !== undefined && nodeIndex !== undefined) {
    props['aria-posinset'] = nodeIndex + 1;
    props['aria-setsize'] = totalNodes;
  }

  if (connectedNodeIds.length > 0) {
    props['aria-flowto'] = connectedNodeIds.join(' ');
  }

  if (childNodeIds.length > 0) {
    props['aria-owns'] = childNodeIds.join(' ');
    props['aria-expanded'] = true;
  }

  if (node.data?.description) {
    props['aria-describedby'] = `${node.id}-description`;
  }

  return props;
}

/**
 * Generate ARIA properties for a canvas edge
 */
export function getEdgeAriaProps(
  edge: Edge,
  context: {
    sourceNode?: Node;
    targetNode?: Node;
  } = {}
): AriaProperties {
  const { sourceNode, targetNode } = context;

  const sourceLabel = sourceNode?.data?.label || edge.source;
  const targetLabel = targetNode?.data?.label || edge.target;
  const edgeLabel = edge.label || 'connection';

  return {
    role: 'graphedge',
    'aria-label': `Edge: ${sourceLabel} to ${targetLabel} (${edgeLabel})`,
  };
}

/**
 * Generate ARIA properties for canvas container
 */
export function getCanvasAriaProps(
  totalNodes: number,
  totalEdges: number,
  canvasName?: string
): AriaProperties {
  const label = canvasName || 'Diagram canvas';
  const description = `Contains ${totalNodes} nodes and ${totalEdges} connections`;

  return {
    role: 'graph',
    'aria-label': label,
    'aria-describedby': description,
  };
}

/**
 * Politeness level for screen reader announcements
 */
export type AnnouncePolite = 'off' | 'polite' | 'assertive';

/**
 * Screen reader announcement manager
 */
export class AriaAnnouncer {
  private liveRegion: HTMLDivElement | null = null;

  /**
   * Initialize the live region for announcements
   */
  initialize(): void {
    if (this.liveRegion || typeof document === 'undefined') return;

    this.liveRegion = document.createElement('div');
    this.liveRegion.setAttribute('role', 'status');
    this.liveRegion.setAttribute('aria-live', 'polite');
    this.liveRegion.setAttribute('aria-atomic', 'true');
    this.liveRegion.style.position = 'absolute';
    this.liveRegion.style.left = '-10000px';
    this.liveRegion.style.width = '1px';
    this.liveRegion.style.height = '1px';
    this.liveRegion.style.overflow = 'hidden';

    document.body.appendChild(this.liveRegion);
  }

  /**
   * Announce a message to screen readers
   */
  announce(message: string, politeness: AnnouncePolite = 'polite'): void {
    if (!this.liveRegion) {
      this.initialize();
    }

    if (!this.liveRegion) return;

    // Update aria-live attribute
    this.liveRegion.setAttribute('aria-live', politeness);

    // Clear and set new message
    this.liveRegion.textContent = '';
    setTimeout(() => {
      if (this.liveRegion) {
        this.liveRegion.textContent = message;
      }
    }, 100);
  }

  /**
   * Clear announcements
   */
  clear(): void {
    if (this.liveRegion) {
      this.liveRegion.textContent = '';
    }
  }

  /**
   * Cleanup and remove live region
   */
  destroy(): void {
    if (this.liveRegion && this.liveRegion.parentNode) {
      this.liveRegion.parentNode.removeChild(this.liveRegion);
      this.liveRegion = null;
    }
  }
}

/**
 * Global announcer instance
 */
export const globalAnnouncer = new AriaAnnouncer();

/**
 * React hook for screen reader announcements
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const announce = useAriaAnnouncer();
 *
 *   const handleSave = () => {
 *     saveCanvas();
 *     announce('Canvas saved successfully');
 *   };
 *
 *   return <button onClick={handleSave}>Save</button>;
 * }
 * ```
 */
export function useAriaAnnouncer(): (message: string, politeness?: AnnouncePolite) => void {
  const announcerRef = useRef(globalAnnouncer);

  useEffect(() => {
    announcerRef.current.initialize();
    return () => {
      // Don't destroy on unmount as it's shared
    };
  }, []);

  return useCallback((message: string, politeness: AnnouncePolite = 'polite') => {
    announcerRef.current.announce(message, politeness);
  }, []);
}

/**
 * Generate accessible description for node relationships
 */
export function describeNodeRelationships(
  node: Node,
  edges: Edge[],
  nodes: Node[]
): string {
  const incoming = edges.filter(e => e.target === node.id);
  const outgoing = edges.filter(e => e.source === node.id);

  const nodeMap = new Map(nodes.map(n => [n.id, n]));

  const parts: string[] = [];

  if (incoming.length > 0) {
    const sources = incoming
      .map(e => nodeMap.get(e.source)?.data?.label || e.source)
      .join(', ');
    parts.push(`Receives from: ${sources}`);
  }

  if (outgoing.length > 0) {
    const targets = outgoing
      .map(e => nodeMap.get(e.target)?.data?.label || e.target)
      .join(', ');
    parts.push(`Connects to: ${targets}`);
  }

  if (parts.length === 0) {
    return 'No connections';
  }

  return parts.join('. ');
}

/**
 * Common canvas action announcements
 */
export const CANVAS_ANNOUNCEMENTS = {
  NODE_CREATED: (label: string) => `Node created: ${label}`,
  NODE_DELETED: (label: string) => `Node deleted: ${label}`,
  NODE_SELECTED: (label: string) => `Selected: ${label}`,
  MULTI_SELECT: (count: number) => `${count} items selected`,
  EDGE_CREATED: (source: string, target: string) =>
    `Connection created from ${source} to ${target}`,
  EDGE_DELETED: (source: string, target: string) =>
    `Connection deleted from ${source} to ${target}`,
  UNDO: (action: string) => `Undone: ${action}`,
  REDO: (action: string) => `Redone: ${action}`,
  ZOOM_CHANGED: (zoom: number) => `Zoom level: ${Math.round(zoom * 100)}%`,
  CANVAS_SAVED: 'Canvas saved',
  CANVAS_EXPORTED: (format: string) => `Exported as ${format}`,
} as const;

/**
 * Hook for announcing canvas actions
 *
 * @example
 * ```tsx
 * function CanvasEditor() {
 *   const announceAction = useCanvasAnnouncements();
 *
 *   const handleNodeCreate = (node) => {
 *     createNode(node);
 *     announceAction('NODE_CREATED', node.data.label);
 *   };
 * }
 * ```
 */
export function useCanvasAnnouncements() {
  const announce = useAriaAnnouncer();

  return useCallback((
    action: keyof typeof CANVAS_ANNOUNCEMENTS,
    ...args: unknown[]
  ) => {
    const announcementFn = CANVAS_ANNOUNCEMENTS[action];
    const message = typeof announcementFn === 'function'
      ? (announcementFn as (...params: unknown[]) => string)(...args)
      : announcementFn;

    announce(message, 'polite');
  }, [announce]);
}
