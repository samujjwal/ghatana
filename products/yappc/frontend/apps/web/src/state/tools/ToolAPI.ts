/**
 * Canvas Tool API
 * 
 * Extensible tool system for the Implement Canvas.
 * Provides a plugin architecture for custom tools and interactions.
 */

import type { CanvasState } from '../components/canvas/workspace/canvasAtoms';
import type React from 'react';


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
  renderToolbar?(context: CanvasContext): React.ReactNode;
  renderPanel?(context: CanvasContext): React.ReactNode;
  renderOverlay?(context: CanvasContext): React.ReactNode;
  
  // Event handlers
  onActivate?(context: CanvasContext): void;
  onDeactivate?(context: CanvasContext): void;
  onPointerDown?(event: React.PointerEvent, context: CanvasContext): boolean;
  onPointerMove?(event: React.PointerEvent, context: CanvasContext): boolean;
  onPointerUp?(event: React.PointerEvent, context: CanvasContext): boolean;
  onKeyDown?(event: React.KeyboardEvent, context: CanvasContext): boolean;
  onKeyUp?(event: React.KeyboardEvent, context: CanvasContext): boolean;
  onSelectionChange?(selection: string[], context: CanvasContext): void;
  onElementsChange?(changes: ElementChange[], context: CanvasContext): void;
  onConnectionsChange?(changes: ConnectionChange[], context: CanvasContext): void;
  
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
    this.tools.forEach(tool => tool.initialize?.(context));
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
    return Array.from(this.tools.values()).filter(tool => tool.category === category);
  }
  
  // Event delegation
  /**
   *
   */
  handlePointerDown(event: React.PointerEvent): boolean {
    const tool = this.getActiveTool();
    return tool?.onPointerDown?.(event, this.context!) || false;
  }
  
  /**
   *
   */
  handlePointerMove(event: React.PointerEvent): boolean {
    const tool = this.getActiveTool();
    return tool?.onPointerMove?.(event, this.context!) || false;
  }
  
  /**
   *
   */
  handlePointerUp(event: React.PointerEvent): boolean {
    const tool = this.getActiveTool();
    return tool?.onPointerUp?.(event, this.context!) || false;
  }
  
  /**
   *
   */
  handleKeyDown(event: React.KeyboardEvent): boolean {
    const tool = this.getActiveTool();
    return tool?.onKeyDown?.(event, this.context!) || false;
  }
  
  /**
   *
   */
  handleKeyUp(event: React.KeyboardEvent): boolean {
    const tool = this.getActiveTool();
    return tool?.onKeyUp?.(event, this.context!) || false;
  }
  
  /**
   *
   */
  handleSelectionChange(selection: string[]): void {
    const tool = this.getActiveTool();
    tool?.onSelectionChange?.(selection, this.context!);
  }
  
  /**
   *
   */
  handleElementsChange(changes: ElementChange[]): void {
    const tool = this.getActiveTool();
    tool?.onElementsChange?.(changes, this.context!);
  }
  
  /**
   *
   */
  handleConnectionsChange(changes: ConnectionChange[]): void {
    const tool = this.getActiveTool();
    tool?.onConnectionsChange?.(changes, this.context!);
  }
}

export const toolRegistry = new ToolRegistry();