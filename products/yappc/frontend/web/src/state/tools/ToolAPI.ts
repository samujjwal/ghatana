/**
 * Canvas Tool API
 *
 * Extensible tool system for the Implement Canvas.
 * Provides a plugin architecture for custom tools and interactions.
 */

import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';
import type {
  KeyboardEvent as ReactKeyboardEvent,
  PointerEvent as ReactPointerEvent,
  ReactNode,
} from 'react';

/**
 *
 */
export interface CanvasContext {
  // State access
  getCanvasState(): CanvasState;
  updateCanvasState(updates: Partial<CanvasState>): void;

  // Element manipulation
  addElement(element: Omit<CanvasElement, 'id'>): string;
  updateElement(id: string, updates: Partial<CanvasElement>): void;
  deleteElement(id: string): void;
  duplicateElement(id: string): string;

  // Selection
  getSelection(): string[];
  setSelection(elementIds: string[]): void;
  addToSelection(elementIds: string[]): void;
  clearSelection(): void;

  // Connections
  addConnection(connection: Omit<CanvasConnection, 'id'>): string;
  updateConnection(id: string, updates: Partial<CanvasConnection>): void;
  deleteConnection(id: string): void;

  // Viewport
  getViewport(): { x: number; y: number; zoom: number };
  setViewport(viewport: { x: number; y: number; zoom: number }): void;
  fitView(elementIds?: string[]): void;

  // Layers
  createLayer(name: string): string;
  deleteLayer(id: string): void;
  moveToLayer(elementIds: string[], layerId: string): void;

  // Utilities
  exportSnapshot(): string;
  importSnapshot(data: string): void;
  undo(): void;
  redo(): void;

  // Events
  on(event: string, handler: (...args: unknown[]) => void): () => void;
  emit(event: string, ...args: unknown[]): void;
}

/**
 *
 */
export interface CanvasTool {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  category?: 'selection' | 'drawing' | 'analysis' | 'export' | 'custom';

  // Lifecycle
  initialize?(context: CanvasContext): void;
  cleanup?(): void;

  // UI
  renderToolbar?(context: CanvasContext): ReactNode;
  renderPanel?(context: CanvasContext): ReactNode;
  renderOverlay?(context: CanvasContext): ReactNode;

  // Event handlers
  onActivate?(context: CanvasContext): void;
  onDeactivate?(context: CanvasContext): void;
  onPointerDown?(event: ReactPointerEvent, context: CanvasContext): boolean;
  onPointerMove?(event: ReactPointerEvent, context: CanvasContext): boolean;
  onPointerUp?(event: ReactPointerEvent, context: CanvasContext): boolean;
  onKeyDown?(event: ReactKeyboardEvent, context: CanvasContext): boolean;
  onKeyUp?(event: ReactKeyboardEvent, context: CanvasContext): boolean;
  onSelectionChange?(selection: string[], context: CanvasContext): void;
  onElementsChange?(changes: ElementChange[], context: CanvasContext): void;
  onConnectionsChange?(
    changes: ConnectionChange[],
    context: CanvasContext
  ): void;

  // Configuration
  settings?: Record<string, unknown>;
  shortcuts?: KeyboardShortcut[];
}

/**
 *
 */
export interface ElementChange {
  type: 'add' | 'update' | 'remove';
  element: CanvasElement;
  oldElement?: CanvasElement;
}

/**
 *
 */
export interface ConnectionChange {
  type: 'add' | 'update' | 'remove';
  connection: CanvasConnection;
  oldConnection?: CanvasConnection;
}

/**
 *
 */
export interface KeyboardShortcut {
  key: string;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  altKey?: boolean;
  metaKey?: boolean;
  description: string;
  action: (context: CanvasContext) => void;
}

/**
 *
 */
export interface CanvasElement {
  id: string;
  type: string;
  position: { x: number; y: number };
  size?: { width: number; height: number };
  data: Record<string, unknown>;
  style?: Record<string, unknown>;
  selected?: boolean;
  locked?: boolean;
  layerId?: string;
  metadata?: Record<string, unknown>;
}

/**
 *
 */
export interface CanvasConnection {
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

// Tool Registry
/**
 *
 */
export class ToolRegistry {
  private tools = new Map<string, CanvasTool>();
  private activeTool: string | null = null;
  private context: CanvasContext | null = null;

  private getContext(): CanvasContext | null {
    return this.context;
  }

  /**
   *
   */
  register(tool: CanvasTool): void {
    this.tools.set(tool.id, tool);
    if (this.context) {
      tool.initialize?.(this.context);
    }
  }

  /**
   *
   */
  unregister(toolId: string): void {
    const tool = this.tools.get(toolId);
    if (tool) {
      if (this.activeTool === toolId) {
        this.deactivateTool();
      }
      tool.cleanup?.();
      this.tools.delete(toolId);
    }
  }

  /**
   *
   */
  setContext(context: CanvasContext): void {
    this.context = context;
    // Initialize all registered tools
    this.tools.forEach((tool) => tool.initialize?.(context));
  }

  /**
   *
   */
  activateTool(toolId: string): void {
    if (this.activeTool === toolId) return;

    // Deactivate current tool
    this.deactivateTool();

    const tool = this.tools.get(toolId);
    if (tool && this.context) {
      this.activeTool = toolId;
      tool.onActivate?.(this.context);
    }
  }

  /**
   *
   */
  deactivateTool(): void {
    if (this.activeTool && this.context) {
      const tool = this.tools.get(this.activeTool);
      tool?.onDeactivate?.(this.context);
      this.activeTool = null;
    }
  }

  /**
   *
   */
  getActiveTool(): CanvasTool | null {
    return this.activeTool ? this.tools.get(this.activeTool) || null : null;
  }

  /**
   *
   */
  getAllTools(): CanvasTool[] {
    return Array.from(this.tools.values());
  }

  /**
   *
   */
  getToolsByCategory(category: string): CanvasTool[] {
    return Array.from(this.tools.values()).filter(
      (tool) => tool.category === category
    );
  }

  // Event delegation
  /**
   *
   */
  handlePointerDown(event: ReactPointerEvent): boolean {
    const tool = this.getActiveTool();
    const context = this.getContext();
    return tool && context ? tool.onPointerDown?.(event, context) || false : false;
  }

  /**
   *
   */
  handlePointerMove(event: ReactPointerEvent): boolean {
    const tool = this.getActiveTool();
    const context = this.getContext();
    return tool && context ? tool.onPointerMove?.(event, context) || false : false;
  }

  /**
   *
   */
  handlePointerUp(event: ReactPointerEvent): boolean {
    const tool = this.getActiveTool();
    const context = this.getContext();
    return tool && context ? tool.onPointerUp?.(event, context) || false : false;
  }

  /**
   *
   */
  handleKeyDown(event: ReactKeyboardEvent): boolean {
    const tool = this.getActiveTool();
    const context = this.getContext();
    return tool && context ? tool.onKeyDown?.(event, context) || false : false;
  }

  /**
   *
   */
  handleKeyUp(event: ReactKeyboardEvent): boolean {
    const tool = this.getActiveTool();
    const context = this.getContext();
    return tool && context ? tool.onKeyUp?.(event, context) || false : false;
  }

  /**
   *
   */
  handleSelectionChange(selection: string[]): void {
    const tool = this.getActiveTool();
    const context = this.getContext();
    if (tool && context) {
      tool.onSelectionChange?.(selection, context);
    }
  }

  /**
   *
   */
  handleElementsChange(changes: ElementChange[]): void {
    const tool = this.getActiveTool();
    const context = this.getContext();
    if (tool && context) {
      tool.onElementsChange?.(changes, context);
    }
  }

  /**
   *
   */
  handleConnectionsChange(changes: ConnectionChange[]): void {
    const tool = this.getActiveTool();
    const context = this.getContext();
    if (tool && context) {
      tool.onConnectionsChange?.(changes, context);
    }
  }
}

export const toolRegistry = new ToolRegistry();
