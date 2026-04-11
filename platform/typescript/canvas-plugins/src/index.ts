/**
 * @ghatana/canvas-plugins
 *
 * Plugin system for the Ghatana canvas:
 * - Plugin registry (element, node, edge, tool, panel, shortcut, context-menu)
 * - Plugin manager (lifecycle: install, activate, deactivate, uninstall)
 * - Plugin API types and error handling
 *
 * @doc.type module
 * @doc.purpose Canvas plugin registry, manager, and extension API
 * @doc.layer platform
 * @doc.pattern Facade
 */

// ─── Plugin types ──────────────────────────────────────────────────────────
export type {
  PluginManifest,
  PluginCapability,
  PluginState,
  PluginContext,
  CanvasPlugin,
  CanvasElementData,
  NodeTypeDefinition,
  NodeComponentProps,
  PortDefinition,
  EdgeTypeDefinition,
  EdgeComponentProps,
  ToolDefinition,
  ToolContext,
  ToolHandlers,
  CanvasPointerEvent,
  PanelDefinition,
  PanelComponentProps,
  PluginCanvasAPI,
  GraphNodeData,
  GraphEdgeData,
  PluginEventAPI,
  CanvasEventType,
  CanvasEventHandler,
  CanvasEventData,
  PluginLogger,
} from "@ghatana/canvas";

// ─── Plugin manager ────────────────────────────────────────────────────────
export {
  PluginRegistrationOptions,
  PluginError,
  PluginErrorCode,
  PluginManager,
  getPluginManager,
} from "@ghatana/canvas";

// ─── Plugin registries ─────────────────────────────────────────────────────
export {
  PluginElementRegistry,
  NodeTypeRegistry,
  EdgeTypeRegistry,
  PluginToolRegistry,
  PanelRegistry,
  ShortcutRegistry,
  ContextMenuRegistry,
  getElementRegistry,
  getNodeTypeRegistry,
  getEdgeTypeRegistry,
  getToolRegistry,
  getPanelRegistry,
  getShortcutRegistry,
  getContextMenuRegistry,
  resetAllRegistries,
  registerPluginContributions,
  unregisterPluginContributions,
} from "@ghatana/canvas";
