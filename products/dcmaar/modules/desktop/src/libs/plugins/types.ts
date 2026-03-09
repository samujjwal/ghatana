/**
 * Plugin System Type Definitions
 * 
 * Core types for the WASM-based plugin system following DCMaar standards.
 */

/**
 * Plugin metadata
 */
export interface PluginMetadata {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  homepage?: string;
  repository?: string;
  license: string;
  keywords?: string[];
  icon?: string;
}

/**
 * Plugin capabilities
 */
export interface PluginCapabilities {
  // Data processing
  canProcessMetrics?: boolean;
  canProcessEvents?: boolean;
  canProcessLogs?: boolean;

  // UI extensions
  canProvideWidgets?: boolean;
  canProvideCommands?: boolean;
  canProvideViews?: boolean;

  // Integrations
  canConnectToServices?: boolean;
  canExportData?: boolean;
  canImportData?: boolean;

  // Resource limits
  maxMemoryMB?: number;
  maxCpuPercent?: number;
  maxNetworkRequests?: number;
}

/**
 * Plugin configuration schema
 */
export interface PluginConfigSchema {
  type: 'object';
  properties: Record<string, {
    type: 'string' | 'number' | 'boolean' | 'array' | 'object';
    description?: string;
    default?: unknown;
    required?: boolean;
    enum?: unknown[];
  }>;
}

/**
 * Plugin context provided to plugins
 */
export interface PluginContext {
  // Plugin info
  pluginId: string;
  config: Record<string, unknown>;

  // API access
  api: PluginAPI;

  // Logging
  logger: PluginLogger;

  // Storage
  storage: PluginStorage;
}

/**
 * Plugin API for interacting with the host application
 */
export interface PluginAPI {
  // Data access
  getMetrics(query: MetricQuery): Promise<MetricData[]>;
  getEvents(query: EventQuery): Promise<EventData[]>;

  // UI extensions
  registerWidget(widget: WidgetDefinition): void;
  registerCommand(command: CommandDefinition): void;
  registerView(view: ViewDefinition): void;

  // Notifications
  showNotification(message: string, type: 'info' | 'success' | 'warning' | 'error'): void;

  // HTTP requests (with restrictions)
  fetch(url: string, options?: RequestInit): Promise<Response>;
}

/**
 * Plugin logger
 */
export interface PluginLogger {
  debug(message: string, ...args: unknown[]): void;
  info(message: string, ...args: unknown[]): void;
  warn(message: string, ...args: unknown[]): void;
  error(message: string, ...args: unknown[]): void;
}

/**
 * Plugin storage (sandboxed)
 */
export interface PluginStorage {
  get(key: string): Promise<unknown>;
  set(key: string, value: unknown): Promise<void>;
  delete(key: string): Promise<void>;
  clear(): Promise<void>;
  keys(): Promise<string[]>;
}

/**
 * Metric query
 */
export interface MetricQuery {
  name: string;
  startTime?: number;
  endTime?: number;
  filters?: Record<string, unknown>;
}

/**
 * Metric data
 */
export interface MetricData {
  name: string;
  value: number;
  timestamp: number;
  labels?: Record<string, string>;
}

/**
 * Event query
 */
export interface EventQuery {
  type?: string;
  startTime?: number;
  endTime?: number;
  filters?: Record<string, unknown>;
}

/**
 * Event data
 */
export interface EventData {
  id: string;
  type: string;
  timestamp: number;
  data: Record<string, unknown>;
}

/**
 * Widget definition
 */
export interface WidgetDefinition {
  id: string;
  title: string;
  description?: string;
  icon?: string;
  render: () => HTMLElement;
  onMount?: () => void;
  onUnmount?: () => void;
}

/**
 * Command definition
 */
export interface CommandDefinition {
  id: string;
  title: string;
  description?: string;
  icon?: string;
  shortcut?: string;
  execute: () => void | Promise<void>;
}

/**
 * View definition
 */
export interface ViewDefinition {
  id: string;
  title: string;
  icon?: string;
  render: () => HTMLElement;
}

/**
 * Plugin manifest (loaded from plugin package)
 */
export interface PluginManifest {
  metadata: PluginMetadata;
  capabilities: PluginCapabilities;
  configSchema?: PluginConfigSchema;
  main: string; // Entry point (WASM file)
  dependencies?: Record<string, string>;
}

/**
 * Plugin instance
 */
export interface Plugin {
  manifest: PluginManifest;
  instance: WebAssembly.Instance;
  context: PluginContext;
  state: PluginState;
}

/**
 * Plugin state
 */
export enum PluginState {
  UNLOADED = 'unloaded',
  LOADING = 'loading',
  LOADED = 'loaded',
  ACTIVATING = 'activating',
  ACTIVE = 'active',
  DEACTIVATING = 'deactivating',
  ERROR = 'error',
}

/**
 * Plugin error
 */
export class PluginError extends Error {
  constructor(
    message: string,
    public pluginId: string,
    public code: string,
    public details?: unknown
  ) {
    super(message);
    this.name = 'PluginError';
  }
}

/**
 * Plugin lifecycle hooks
 */
export interface PluginLifecycle {
  onLoad?: () => void | Promise<void>;
  onActivate?: (context: PluginContext) => void | Promise<void>;
  onDeactivate?: () => void | Promise<void>;
  onUnload?: () => void | Promise<void>;
  onConfigChange?: (config: Record<string, unknown>) => void | Promise<void>;
}

/**
 * Plugin sandbox configuration
 */
export interface PluginSandboxConfig {
  allowedDomains?: string[];
  allowedAPIs?: string[];
  maxMemoryMB: number;
  maxCpuPercent: number;
  maxNetworkRequests: number;
  timeout: number;
}
