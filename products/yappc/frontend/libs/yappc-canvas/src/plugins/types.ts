/**
 * Plugin Architecture API Types
 * 
 * Defines the plugin system for extending Canvas functionality with custom features,
 * tools, renderers, and behaviors. Includes plugin lifecycle, hooks, and sandbox isolation.
 */

import type { CanvasDocument } from '../types';

/**
 * Plugin metadata and manifest
 */
export interface PluginManifest {
  /** Unique plugin identifier (e.g., "com.example.my-plugin") */
  id: string;
  
  /** Display name */
  name: string;
  
  /** Semantic version (e.g., "1.0.0") */
  version: string;
  
  /** Author information */
  author: {
    name: string;
    email?: string;
    url?: string;
  };
  
  /** Plugin description */
  description: string;
  
  /** Minimum required Canvas version */
  minCanvasVersion: string;
  
  /** Plugin dependencies (other plugin IDs) */
  dependencies?: string[];
  
  /** Optional dependencies (won't fail if missing) */
  optionalDependencies?: string[];
  
  /** Plugin capabilities */
  capabilities?: PluginCapability[];
  
  /** Custom metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Plugin capabilities define what the plugin can do
 */
export type PluginCapability =
  | 'custom-elements'      // Register custom element types
  | 'custom-tools'         // Add custom drawing tools
  | 'custom-commands'      // Register commands
  | 'custom-panels'        // Add UI panels
  | 'custom-exporters'     // Custom export formats
  | 'custom-importers'     // Custom import formats
  | 'event-hooks'          // Subscribe to Canvas events
  | 'state-access'         // Access Canvas state
  | 'api-access';          // Call Canvas APIs

/**
 * Plugin lifecycle states
 */
export type PluginState =
  | 'uninitialized'  // Plugin loaded but not initialized
  | 'initializing'   // Plugin initialization in progress
  | 'active'         // Plugin running normally
  | 'paused'         // Plugin temporarily suspended
  | 'error'          // Plugin encountered error
  | 'disabled'       // Plugin disabled by user
  | 'uninstalling';  // Plugin being removed

/**
 * Plugin lifecycle hooks
 */
export interface PluginLifecycle {
  /**
   * Called when plugin is first loaded
   * Use for registration and setup
   */
  onLoad?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called when plugin is activated
   * Use for starting background tasks
   */
  onActivate?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called when plugin is paused
   * Use for cleaning up resources
   */
  onPause?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called when plugin is resumed from pause
   */
  onResume?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called when plugin is disabled
   * Use for cleanup and state saving
   */
  onDeactivate?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called before plugin is uninstalled
   * Use for final cleanup
   */
  onUninstall?(context: PluginContext): void | Promise<void>;
  
  /**
   * Called when plugin encounters error
   */
  onError?(error: Error, context: PluginContext): void;
}

/**
 * Plugin context provides safe access to Canvas APIs
 */
export interface PluginContext {
  /** Plugin manifest */
  manifest: PluginManifest;
  
  /** Current plugin state */
  state: PluginState;
  
  /** Canvas API access (sandboxed) */
  canvas: PluginCanvasAPI;
  
  /** Storage API for plugin data */
  storage: PluginStorage;
  
  /** Event subscription API */
  events: PluginEventAPI;
  
  /** UI extension API */
  ui: PluginUIAPI;
  
  /** Logger for debugging */
  logger: PluginLogger;
}

/**
 * Sandboxed Canvas API for plugins
 */
export interface PluginCanvasAPI {
  /** Get current canvas document (read-only) */
  getDocument(): Readonly<CanvasDocument>;
  
  /** Execute a command on the canvas */
  executeCommand(commandId: string, args?: unknown): Promise<void>;
  
  /** Register a custom command */
  registerCommand(command: PluginCommand): void;
  
  /** Register a custom element type */
  registerElementType(elementType: PluginElementType): void;
  
  /** Register a custom tool */
  registerTool(tool: PluginTool): void;
  
  /** Register a custom exporter */
  registerExporter(exporter: PluginExporter): void;
  
  /** Register a custom importer */
  registerImporter(importer: PluginImporter): void;
}

/**
 * Plugin storage API for persisting data
 */
export interface PluginStorage {
  /** Get value from storage */
  get<T = unknown>(key: string): Promise<T | undefined>;
  
  /** Set value in storage */
  set<T = unknown>(key: string, value: T): Promise<void>;
  
  /** Delete value from storage */
  delete(key: string): Promise<void>;
  
  /** Clear all plugin storage */
  clear(): Promise<void>;
  
  /** Get all keys */
  keys(): Promise<string[]>;
}

/**
 * Plugin event subscription API
 */
export interface PluginEventAPI {
  /** Subscribe to Canvas event */
  on(event: CanvasEvent, handler: EventHandler): () => void;
  
  /** Subscribe once */
  once(event: CanvasEvent, handler: EventHandler): () => void;
  
  /** Emit custom plugin event */
  emit(event: string, data?: unknown): void;
}

/**
 * Canvas events plugins can subscribe to
 */
export type CanvasEvent =
  | 'element:created'
  | 'element:updated'
  | 'element:deleted'
  | 'selection:changed'
  | 'viewport:changed'
  | 'document:loaded'
  | 'document:saved'
  | 'undo'
  | 'redo'
  | 'export:started'
  | 'export:completed'
  | 'export:failed';

/** Event handler function */
export type EventHandler = (data: unknown) => void;

/**
 * Plugin UI extension API
 */
export interface PluginUIAPI {
  /** Register a custom panel */
  registerPanel(panel: PluginPanel): void;
  
  /** Show a notification */
  notify(message: string, type?: 'info' | 'success' | 'warning' | 'error'): void;
  
  /** Show a dialog */
  showDialog(dialog: PluginDialog): Promise<unknown>;
  
  /** Register a context menu item */
  registerContextMenuItem(item: PluginContextMenuItem): void;
}

/**
 * Plugin logger for debugging
 */
export interface PluginLogger {
  debug(message: string, ...args: unknown[]): void;
  info(message: string, ...args: unknown[]): void;
  warn(message: string, ...args: unknown[]): void;
  error(message: string, ...args: unknown[]): void;
}

/**
 * Custom command definition
 */
export interface PluginCommand {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  shortcut?: string;
  execute: (args?: unknown) => void | Promise<void>;
  canExecute?: (args?: unknown) => boolean;
}

/**
 * Custom element type definition
 */
export interface PluginElementType {
  type: string;
  name: string;
  icon?: string;
  defaultProps?: Record<string, unknown>;
  render?: (element: unknown, context: RenderContext) => void;
  onSelect?: (element: unknown) => void;
  onDeselect?: (element: unknown) => void;
}

/**
 * Custom tool definition
 */
export interface PluginTool {
  id: string;
  name: string;
  icon?: string;
  cursor?: string;
  onActivate?: () => void;
  onDeactivate?: () => void;
  onPointerDown?: (event: PointerEvent) => void;
  onPointerMove?: (event: PointerEvent) => void;
  onPointerUp?: (event: PointerEvent) => void;
  onKeyDown?: (event: KeyboardEvent) => void;
  onKeyUp?: (event: KeyboardEvent) => void;
}

/**
 * Custom exporter definition
 */
export interface PluginExporter {
  id: string;
  name: string;
  fileExtension: string;
  mimeType: string;
  export: (document: CanvasDocument, options?: unknown) => Promise<Blob>;
}

/**
 * Custom importer definition
 */
export interface PluginImporter {
  id: string;
  name: string;
  fileExtensions: string[];
  mimeTypes: string[];
  import: (file: File, options?: unknown) => Promise<Partial<CanvasDocument>>;
}

/**
 * Custom panel definition
 */
export interface PluginPanel {
  id: string;
  title: string;
  icon?: string;
  position?: 'left' | 'right' | 'bottom';
  defaultOpen?: boolean;
  render: (container: HTMLElement) => void | (() => void);
}

/**
 * Custom dialog definition
 */
export interface PluginDialog {
  title: string;
  message?: string;
  buttons?: Array<{
    label: string;
    value: unknown;
    primary?: boolean;
  }>;
  content?: (container: HTMLElement) => void | (() => void);
}

/**
 * Context menu item definition
 */
export interface PluginContextMenuItem {
  id: string;
  label: string;
  icon?: string;
  shortcut?: string;
  separator?: boolean;
  submenu?: PluginContextMenuItem[];
  onClick?: () => void;
  condition?: () => boolean;
}

/**
 * Render context for custom elements
 */
export interface RenderContext {
  canvas: CanvasRenderingContext2D | WebGLRenderingContext;
  viewport: {
    zoom: number;
    center: { x: number; y: number };
  };
  isSelected: boolean;
  isHovered: boolean;
}

/**
 * Main plugin class interface
 */
export interface Plugin extends PluginLifecycle {
  /** Plugin manifest */
  manifest: PluginManifest;
}

/**
 * Plugin registration options
 */
export interface PluginRegistrationOptions {
  /** Whether to auto-activate after loading */
  autoActivate?: boolean;
  
  /** Plugin configuration overrides */
  config?: Record<string, unknown>;
  
  /** Sandbox restrictions */
  sandbox?: {
    /** Allow network access */
    allowNetwork?: boolean;
    
    /** Allow file system access */
    allowFileSystem?: boolean;
    
    /** Maximum memory usage (MB) */
    maxMemory?: number;
    
    /** Maximum CPU time (ms) */
    maxCPUTime?: number;
  };
}

/**
 * Plugin error types
 */
export class PluginError extends Error {
  /**
   *
   */
  constructor(
    message: string,
    public readonly pluginId: string,
    public readonly code: PluginErrorCode,
  ) {
    super(message);
    this.name = 'PluginError';
  }
}

/**
 *
 */
export type PluginErrorCode =
  | 'LOAD_FAILED'
  | 'INIT_FAILED'
  | 'ACTIVATION_FAILED'
  | 'RUNTIME_ERROR'
  | 'PERMISSION_DENIED'
  | 'DEPENDENCY_MISSING'
  | 'VERSION_MISMATCH'
  | 'SANDBOX_VIOLATION';
