/**
 * Canvas Collaboration Module
 *
 * @description Yjs bindings for real-time canvas collaboration,
 * synchronizing nodes, edges, and viewport state.
 */

import * as Y from 'yjs';
import { CollaborationManager, CollaborationUser } from './CollaborationManager';

// =============================================================================
// Types
// =============================================================================

export interface CanvasNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  style?: Record<string, unknown>;
  dragging?: boolean;
  selected?: boolean;
  width?: number;
  height?: number;
}

export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  type?: string;
  data?: Record<string, unknown>;
  style?: Record<string, unknown>;
  animated?: boolean;
  label?: string;
}

export interface CanvasViewport {
  x: number;
  y: number;
  zoom: number;
}

export interface CanvasSelection {
  nodeIds: string[];
  edgeIds: string[];
}

export interface UserCursor {
  userId: string;
  userName: string;
  userColor: string;
  position: { x: number; y: number };
  viewportPosition: { x: number; y: number };
  timestamp: number;
}

export interface CanvasCollaborationState {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  viewport: CanvasViewport;
  cursors: UserCursor[];
  selections: Map<string, CanvasSelection>;
}

export type CanvasChangeType =
  | 'nodes-change'
  | 'edges-change'
  | 'viewport-change'
  | 'cursors-change'
  | 'selections-change';

export interface CanvasChangeEvent {
  type: CanvasChangeType;
  data: unknown;
}

// =============================================================================
// Canvas Collaboration Class
// =============================================================================

export class CanvasCollaboration {
  private collab: CollaborationManager;
  private nodesMap: Y.Map<unknown>;
  private edgesMap: Y.Map<unknown>;
  private viewportMap: Y.Map<unknown>;
  private selectionsMap: Y.Map<unknown>;
  private userId: string;
  private listeners: Map<CanvasChangeType, Set<(event: CanvasChangeEvent) => void>>;
  private cursors: Map<string, UserCursor>;
  private cursorUpdateInterval: number | null = null;
  private localCursorPosition: { x: number; y: number } | null = null;

  constructor(collab: CollaborationManager, userId: string) {
    this.collab = collab;
    this.userId = userId;
    this.listeners = new Map();
    this.cursors = new Map();

    // Get shared data structures
    const doc = collab.getDocument();
    this.nodesMap = doc.getMap('canvas-nodes');
    this.edgesMap = doc.getMap('canvas-edges');
    this.viewportMap = doc.getMap('canvas-viewport');
    this.selectionsMap = doc.getMap('canvas-selections');

    // Set up observers
    this.setupObservers();
  }

  /**
   * Set up Yjs observers for changes
   */
  private setupObservers(): void {
    // Nodes observer
    this.nodesMap.observe((event) => {
      const nodes = this.getNodes();
      this.emit('nodes-change', { nodes, event });
    });

    // Edges observer
    this.edgesMap.observe((event) => {
      const edges = this.getEdges();
      this.emit('edges-change', { edges, event });
    });

    // Viewport observer (only emit for remote changes)
    this.viewportMap.observe((event) => {
      if (event.transaction.local) return;
      const viewport = this.getViewport();
      this.emit('viewport-change', { viewport, event });
    });

    // Selections observer
    this.selectionsMap.observe((event) => {
      const selections = this.getSelections();
      this.emit('selections-change', { selections, event });
    });

    // Awareness (cursors) observer
    this.collab.on('awareness-change', ({ users }) => {
      this.updateCursors(users);
    });
  }

  /**
   * Update cursor positions from awareness
   */
  private updateCursors(users: CollaborationUser[]): void {
    const newCursors = new Map<string, UserCursor>();

    users.forEach((user) => {
      if (user.cursor && user.id !== this.userId) {
        newCursors.set(user.id, {
          userId: user.id,
          userName: user.name,
          userColor: user.color,
          position: user.cursor,
          viewportPosition: user.cursor, // Transform based on viewport
          timestamp: user.lastActive,
        });
      }
    });

    this.cursors = newCursors;
    this.emit('cursors-change', { cursors: Array.from(newCursors.values()) });
  }

  // ===========================================================================
  // Nodes API
  // ===========================================================================

  /**
   * Get all nodes
   */
  getNodes(): CanvasNode[] {
    const nodes: CanvasNode[] = [];
    this.nodesMap.forEach((value, key) => {
      nodes.push({ id: key, ...value });
    });
    return nodes;
  }

  /**
   * Get a single node
   */
  getNode(id: string): CanvasNode | null {
    const data = this.nodesMap.get(id);
    return data ? { id, ...data } : null;
  }

  /**
   * Add a node
   */
  addNode(node: CanvasNode): void {
    this.collab.transact(() => {
      const { id, ...data } = node;
      this.nodesMap.set(id, data);
    });
  }

  /**
   * Add multiple nodes
   */
  addNodes(nodes: CanvasNode[]): void {
    this.collab.transact(() => {
      nodes.forEach((node) => {
        const { id, ...data } = node;
        this.nodesMap.set(id, data);
      });
    });
  }

  /**
   * Update a node
   */
  updateNode(id: string, updates: Partial<CanvasNode>): void {
    this.collab.transact(() => {
      const current = this.nodesMap.get(id);
      if (current) {
        this.nodesMap.set(id, { ...current, ...updates });
      }
    });
  }

  /**
   * Update node position
   */
  updateNodePosition(id: string, position: { x: number; y: number }): void {
    this.updateNode(id, { position });
  }

  /**
   * Delete a node
   */
  deleteNode(id: string): void {
    this.collab.transact(() => {
      this.nodesMap.delete(id);
      // Also delete connected edges
      this.edgesMap.forEach((edge, edgeId) => {
        if (edge.source === id || edge.target === id) {
          this.edgesMap.delete(edgeId);
        }
      });
    });
  }

  /**
   * Delete multiple nodes
   */
  deleteNodes(ids: string[]): void {
    this.collab.transact(() => {
      ids.forEach((id) => {
        this.nodesMap.delete(id);
      });
      // Delete connected edges
      this.edgesMap.forEach((edge, edgeId) => {
        if (ids.includes(edge.source) || ids.includes(edge.target)) {
          this.edgesMap.delete(edgeId);
        }
      });
    });
  }

  // ===========================================================================
  // Edges API
  // ===========================================================================

  /**
   * Get all edges
   */
  getEdges(): CanvasEdge[] {
    const edges: CanvasEdge[] = [];
    this.edgesMap.forEach((value, key) => {
      edges.push({ id: key, ...value });
    });
    return edges;
  }

  /**
   * Get a single edge
   */
  getEdge(id: string): CanvasEdge | null {
    const data = this.edgesMap.get(id);
    return data ? { id, ...data } : null;
  }

  /**
   * Add an edge
   */
  addEdge(edge: CanvasEdge): void {
    this.collab.transact(() => {
      const { id, ...data } = edge;
      this.edgesMap.set(id, data);
    });
  }

  /**
   * Add multiple edges
   */
  addEdges(edges: CanvasEdge[]): void {
    this.collab.transact(() => {
      edges.forEach((edge) => {
        const { id, ...data } = edge;
        this.edgesMap.set(id, data);
      });
    });
  }

  /**
   * Update an edge
   */
  updateEdge(id: string, updates: Partial<CanvasEdge>): void {
    this.collab.transact(() => {
      const current = this.edgesMap.get(id);
      if (current) {
        this.edgesMap.set(id, { ...current, ...updates });
      }
    });
  }

  /**
   * Delete an edge
   */
  deleteEdge(id: string): void {
    this.collab.transact(() => {
      this.edgesMap.delete(id);
    });
  }

  /**
   * Delete multiple edges
   */
  deleteEdges(ids: string[]): void {
    this.collab.transact(() => {
      ids.forEach((id) => {
        this.edgesMap.delete(id);
      });
    });
  }

  // ===========================================================================
  // Viewport API
  // ===========================================================================

  /**
   * Get current viewport
   */
  getViewport(): CanvasViewport {
    return {
      x: this.viewportMap.get('x') || 0,
      y: this.viewportMap.get('y') || 0,
      zoom: this.viewportMap.get('zoom') || 1,
    };
  }

  /**
   * Update viewport (local only, doesn't broadcast)
   */
  updateViewport(viewport: CanvasViewport): void {
    // Viewport sync is optional - usually each user controls their own
    this.collab.transact(() => {
      this.viewportMap.set('x', viewport.x);
      this.viewportMap.set('y', viewport.y);
      this.viewportMap.set('zoom', viewport.zoom);
    });
  }

  // ===========================================================================
  // Selection API
  // ===========================================================================

  /**
   * Get all user selections
   */
  getSelections(): Map<string, CanvasSelection> {
    const selections = new Map<string, CanvasSelection>();
    this.selectionsMap.forEach((value, key) => {
      selections.set(key, value);
    });
    return selections;
  }

  /**
   * Get selection for a specific user
   */
  getUserSelection(userId: string): CanvasSelection | null {
    return this.selectionsMap.get(userId) || null;
  }

  /**
   * Update local user's selection
   */
  updateSelection(selection: CanvasSelection): void {
    this.collab.transact(() => {
      this.selectionsMap.set(this.userId, selection);
    });
  }

  /**
   * Clear local user's selection
   */
  clearSelection(): void {
    this.collab.transact(() => {
      this.selectionsMap.delete(this.userId);
    });
  }

  // ===========================================================================
  // Cursor API
  // ===========================================================================

  /**
   * Get all remote cursors
   */
  getCursors(): UserCursor[] {
    return Array.from(this.cursors.values());
  }

  /**
   * Update local cursor position
   */
  updateCursorPosition(x: number, y: number): void {
    this.localCursorPosition = { x, y };
    this.collab.updateCursor(x, y);
  }

  /**
   * Start cursor broadcasting
   */
  startCursorBroadcast(interval: number = 50): void {
    if (this.cursorUpdateInterval) {
      clearInterval(this.cursorUpdateInterval);
    }
    this.cursorUpdateInterval = window.setInterval(() => {
      if (this.localCursorPosition) {
        this.collab.updateCursor(
          this.localCursorPosition.x,
          this.localCursorPosition.y
        );
      }
    }, interval);
  }

  /**
   * Stop cursor broadcasting
   */
  stopCursorBroadcast(): void {
    if (this.cursorUpdateInterval) {
      clearInterval(this.cursorUpdateInterval);
      this.cursorUpdateInterval = null;
    }
  }

  // ===========================================================================
  // Bulk Operations
  // ===========================================================================

  /**
   * Set entire canvas state
   */
  setCanvasState(nodes: CanvasNode[], edges: CanvasEdge[]): void {
    this.collab.transact(() => {
      // Clear existing
      this.nodesMap.clear();
      this.edgesMap.clear();

      // Add nodes
      nodes.forEach((node) => {
        const { id, ...data } = node;
        this.nodesMap.set(id, data);
      });

      // Add edges
      edges.forEach((edge) => {
        const { id, ...data } = edge;
        this.edgesMap.set(id, data);
      });
    });
  }

  /**
   * Clear entire canvas
   */
  clearCanvas(): void {
    this.collab.transact(() => {
      this.nodesMap.clear();
      this.edgesMap.clear();
      this.selectionsMap.clear();
    });
  }

  /**
   * Export canvas to JSON
   */
  exportToJSON(): { nodes: CanvasNode[]; edges: CanvasEdge[] } {
    return {
      nodes: this.getNodes(),
      edges: this.getEdges(),
    };
  }

  /**
   * Import canvas from JSON
   */
  importFromJSON(data: { nodes: CanvasNode[]; edges: CanvasEdge[] }): void {
    this.setCanvasState(data.nodes, data.edges);
  }

  // ===========================================================================
  // Event Handling
  // ===========================================================================

  /**
   * Subscribe to canvas changes
   */
  on(
    type: CanvasChangeType,
    callback: (event: CanvasChangeEvent) => void
  ): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    this.listeners.get(type)!.add(callback);

    return () => {
      this.listeners.get(type)?.delete(callback);
    };
  }

  /**
   * Emit a canvas change event
   */
  private emit(type: CanvasChangeType, data: unknown): void {
    const event: CanvasChangeEvent = { type, data };
    this.listeners.get(type)?.forEach((callback) => callback(event));
  }

  // ===========================================================================
  // Cleanup
  // ===========================================================================

  /**
   * Destroy the canvas collaboration instance
   */
  destroy(): void {
    this.stopCursorBroadcast();
    this.listeners.clear();
    this.cursors.clear();
  }
}

export default CanvasCollaboration;
