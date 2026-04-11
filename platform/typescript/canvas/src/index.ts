// Main exports for Ghatana Canvas
// ==================================
// TYPES (canonical source for core type definitions)
export * from "./types/index.js";
export { Bound } from "./utils/bounds.js";

// ==================================
// PLUGIN SYSTEM
// Explicit re-exports to avoid conflicts with types/index.js and core/element-registry.js
export type {
  PluginManifest,
  PluginCapability,
  PluginState,
  PluginContext,
  CanvasPlugin,
  // ElementDefinition — provided by core/element-registry.js
  // ElementRenderer — provided by core/element-registry.js
  // ElementRenderOptions — provided by core/element-registry.js
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
  // CanvasDocument — provided by types/index.js
  GraphNodeData,
  GraphEdgeData,
  // ViewportState — provided by hybrid/index.js
  PluginEventAPI,
  CanvasEventType,
  CanvasEventHandler,
  CanvasEventData,
  PluginLogger,
} from "./plugins/types";
export {
  PluginRegistrationOptions,
  PluginError,
  PluginErrorCode,
  PluginManager,
  getPluginManager,
} from "./plugins/plugin-manager";
export {
  // ElementRegistry — this is the PLUGIN one (distinct from core/element-registry)
  ElementRegistry as PluginElementRegistry,
  NodeTypeRegistry,
  EdgeTypeRegistry,
  ToolRegistry as PluginToolRegistry,
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
} from "./plugins/plugin-registry";

// ==================================
// HYBRID CANVAS
// Explicitly re-export to avoid conflicts with types/index.js for CanvasElement, CanvasNode, CanvasEdge
export type {
  HybridCanvasProps,
  HybridCanvasState,
  RenderingMode,
  LayerConfig as HybridLayerConfig,
  ViewportState,
  SelectionState,
  CoordinateSystem,
  CanvasElement as HybridCanvasElement,
  CanvasNode as HybridCanvasNode,
  CanvasEdge as HybridCanvasEdge,
} from "./hybrid/types";
export {
  HybridCanvasController,
  type HybridCanvasAPI,
} from "./hybrid/hybrid-canvas-controller";
export {
  hybridCanvasStore,
  useHybridCanvasState,
  useViewport,
  useSelection,
  useRenderingMode,
  useActiveLayer,
} from "./hybrid/state";
export {
  useHybridCanvas,
  useCanvasElements,
  useCanvasNodes,
  useCanvasEdges,
  useCanvasViewport,
  useCanvasSelection,
  useCanvasTool,
} from "./hybrid/hooks";
export { HybridCanvas } from "./hybrid/HybridCanvas";
export { FreeformLayer } from "./hybrid/FreeformLayer";
export { GraphLayer } from "./hybrid/GraphLayer";
export { LayerContainer } from "./hybrid/LayerContainer";
export {
  // Canonical names only
  screenToWorld,
  worldToScreen,
} from "./hybrid/coordinates";

// ==================================
// ELEMENTS (AFFiNE parity + Extended)
// base.js exports CanvasElement class which conflicts with types/index.js and hybrid/types.ts
// The CanvasElement from elements/base.js is the abstract base class for canvas elements
export { CanvasElement as CanvasElementBase } from "./elements/base.js";
export * from "./elements/ui-component.js";
export * from "./elements/shape.js";
export * from "./elements/text.js";
export * from "./elements/brush.js";
export * from "./elements/connector.js";
export * from "./elements/pipeline-node.js";
export * from "./elements/code.js";
export * from "./elements/diagram.js";
export * from "./elements/group.js";
export * from "./elements/frame.js";
export * from "./elements/mindmap.js";
export * from "./elements/highlighter.js";

// Rich content elements (AFFiNE parity)
export * from "./elements/image.js";
export * from "./elements/attachment.js";
export * from "./elements/embed.js";
export * from "./elements/rich-text.js";
export * from "./elements/note.js";
export * from "./elements/table.js";
export * from "./elements/callout.js";
export * from "./elements/list.js";
export * from "./elements/divider.js";
export * from "./elements/latex.js";
export * from "./elements/bookmark.js";

// Multimedia elements
export * from "./elements/video.js";
export * from "./elements/audio.js";

// Interactive / live content elements
export * from "./elements/live-react.js";
export * from "./elements/code-editor.js";

// Data visualization elements
export * from "./elements/data-chart.js";
export * from "./elements/data-metric.js";

// Drawing surface
export * from "./elements/whiteboard.js";

// Hierarchical navigation
export * from "./elements/portal.js";

// ==================================
// UI BUILDER (High-fidelity UI design)
export * from "./ui-builder/index.js";

// ==================================
// TOOLS (Base + Individual tools)
export * from "./tools/index.js";
// Note: base-tool.ts exports are included in tools/index.ts
export {
  ConnectorTool,
  type ConnectorToolOptions,
} from "./tools/connector-tool.js";

// ==================================
// DOMAIN INJECTION API (Product-agnostic canvas configuration)
export {
  createCanvasDomainConfig,
  type CanvasDomainConfig,
  type CreateCanvasDomainConfigOptions,
  type DomainPhase,
  type DomainRole,
} from "./core/domain-injection";

// ==================================
// CORE SYSTEMS
export * from "./core/viewport.js";
export * from "./core/layer-manager.js";
export * from "./core/canvas-renderer.js";
export * from "./core/canvas-advanced.js";
export * from "./core/quick-search.js";
export * from "./core/performance.js";
export * from "./core/accessibility.js";
export * from "./core/action-registry";
// keyboard-shortcuts exports KeyboardShortcutManager which conflicts with core/performance.js
export {
  ShortcutConfig,
  ShortcutGroup,
  formatShortcut,
  KeyboardShortcutManager as CanvasKeyboardShortcutManager,
  getKeyboardShortcutManager,
  resetKeyboardShortcutManager,
  createKeyboardShortcutHook,
  COMMON_SHORTCUTS,
} from "./core/keyboard-shortcuts";

// ==================================
// PLUGGABLE ARCHITECTURE (AFFiNE-style)
export * from "./core/element-registry.js";
export * from "./core/tool-registry.js";
export * from "./core/connection-manager.js";
export * from "./core/element-operations.js";

// ==================================
// ELEMENT REGISTRATIONS (Auto-registers all built-in elements)
export { registerBuiltInElements } from "./core/element-registrations.js";

// ==================================
// THEME
export * from "./theme/index.js";

// ==================================
// UTILITIES
export * from "./utils/export-import.js";
export * from "./utils/layer-detection";
// react/index.js exports useCanvasElements/useCanvasViewport which conflict with hybrid/hooks
export {
  useCanvas,
  Canvas,
  CanvasProps,
  CanvasFlow,
  CanvasFlowProps,
  CanvasFlowElementEvent,
  useCanvasElements as useCanvasRendererElements,
  useCanvasViewport as useCanvasRendererViewport,
} from "./react/index.js";

// ==================================
// CHROME UI (Canvas shell components)
export * from "./chrome";
export { RoleSwitcher } from "./components/RoleSwitcher";
export { SmartContextBar } from "./components/SmartContextBar";
export {
  CollaborationCursors,
  useCollaborationCursor,
} from "./components/CollaborationCursors";

// ==================================
// GENERIC CORE CANVAS (Application-agnostic)
// Configuration System
export * from "./core/canvas-config";
// generic-layer-system re-exports LayerConfig (from canvas-config), getLayerConfig, isZoomInLayer, getRecommendedZoomForLayer
// LayerConfig conflicts with hybrid/types.ts LayerConfig
export {
  GenericLayerDetector,
  getLayerConfig as getGenericLayerConfig,
  isZoomInLayer,
  getRecommendedZoomForLayer,
} from "./core/generic-layer-system";
// generic-chrome re-exports getPhaseColors (conflicts with chrome.tsx) and getAvailableLayers (conflicts with utils/layer-detection)
export {
  genericChromeCalmModeAtom,
  genericChromeLeftRailVisibleAtom,
  genericChromeLeftPanelAtom,
  genericChromeInspectorVisibleAtom,
  genericChromeMinimapVisibleAtom,
  genericChromeCurrentPhaseAtom,
  genericChromeZoomLevelAtom,
  genericChromeActiveLayersAtom,
  genericChromeCollaboratorsAtom,
  genericChromeSemanticLayerAtom,
  genericChromeActiveRolesAtom,
  genericChromeAvailableActionsAtom,
  GenericAction,
  GENERIC_Z_INDEX,
  getPhaseColors as getGenericPhaseColors,
  getRoleConfig as getGenericRoleConfig,
  getAvailablePhases as getGenericAvailablePhases,
  getAvailableRoles as getGenericAvailableRoles,
  getAvailableLayers as getGenericAvailableLayers,
} from "./core/generic-chrome";

// ==================================
// MULTI-LAYER SYSTEM (Generic implementation)
// Layer Detection
// layer-detector exports LayerConfig which conflicts with canvas-config and hybrid/types
export {
  LayerConfig as DetectorLayerConfig,
  LAYER_CONFIGS,
  getLayerFromZoom,
  getLayerConfig,
  isZoomInLayer as isZoomInDetectorLayer,
  getRecommendedZoomForLayer as getDetectorRecommendedZoom,
  LayerTransitionDetector,
  getLayerDetector,
  resetLayerDetector,
} from "./core/layer-detector";
export * from "./hooks/useLayerDetection";

// Hooks
export * from "./hooks/useAvailableActions";
// useTelemetry exports PerformanceMetrics which conflicts with core/performance.js
export {
  TelemetryEvent,
  PerformanceMetrics as TelemetryPerformanceMetrics,
  UserAction,
  TelemetryConfig,
  useTelemetry,
  useElementTelemetry,
  usePanelTelemetry,
  useShortcutTelemetry,
  useDrawingTelemetry,
  initializeTelemetry,
  destroyTelemetry,
} from "./hooks/useTelemetry";

// Theme System
export * from "./theme/theme";
export * from "./theme/ThemeProvider";

// Accessibility System
export * from "./accessibility/accessibility";
export * from "./accessibility/AccessibilityProvider";

// Performance System
// PerformanceMetrics and PerformanceMonitor conflict with core/performance.js
export {
  PerformanceMetrics as OptimizationMetrics,
  ViewportBounds as OptViewportBounds,
  PerformanceConfig,
  PerformanceMonitor as OptimizationPerformanceMonitor,
  ViewportCuller,
  LazyLoader,
  smartMemo,
  useMemoize,
  deepEqual,
  usePerformanceMonitor,
  useViewportCulling,
  useLazyLoader,
  withPerformanceOptimizations,
  debounce,
  throttle,
  rafThrottle,
  getMemoryUsage,
  performanceMark,
  performanceMeasure,
  globalPerformanceMonitor,
} from "./performance/optimizations";

// Testing System
export * from "./testing/test-utils";

// Panel Components (Generic)
export { OutlinePanel } from "./components/panels/OutlinePanel";
export { LayersPanel } from "./components/panels/LayersPanel";
export { PalettePanel } from "./components/panels/PalettePanel";
export { TasksPanel } from "./components/panels/TasksPanel";
export { MinimapPanel } from "./components/panels/MinimapPanel";

// Command & Context (Generic)
export { CommandPalette } from "./components/CommandPalette";
export { EnhancedContextMenu } from "./components/EnhancedContextMenu";

// Integrated Chrome (Generic)
export { IntegratedCanvasChrome } from "./components/IntegratedCanvasChrome";

// ==================================
// APPLICATION-SPECIFIC IMPLEMENTATIONS
// For custom applications, use the generic configuration system:
// import { setCanvasConfig, CanvasConfig } from '@ghatana/canvas';
//
// See ARCHITECTURE_SEPARATION.md for details on creating custom canvas applications.
//
// Product-specific implementations are now in their respective product libraries:
// - AEP: @aep/canvas
// - Data-Cloud: @datacloud/canvas  
// - YAPPC: @ghatana/yappc-canvas

// ==================================
// AI / ML INTEGRATION
export {
  AICanvasProvider,
  useCanvasAI,
  useCanvasAISuggestions,
  useCanvasAILoading,
  type AICanvasProviderProps,
  type CanvasAIContextValue,
  type AISuggestionKind,
  type AISuggestion,
  type CanvasAIContext,
  type AIGenerateElementResult,
  type AILayoutResult,
  type AISummarizeResult,
  type AICodeResult,
  type AIResult,
  type CanvasAIAdapter,
  type CanvasAICapabilities,
  type CanvasAIState,
} from "./ai/index.js";

// ==================================
// EXPORT SYSTEM
export {
  exportToPng,
  exportToPdf,
  dataUrlToBlob,
  downloadExportResult,
  type ExportFormat,
  type ExportRegion,
  type ExportOptions,
  type ExportResult,
} from "./export/index.js";

// ==================================
// DRILL-DOWN NAVIGATION
export {
  DrillDownManager,
  type DrillDownEntry,
  type DrillDownEntryType,
  type DrillDownListener,
} from "./core/drill-down-manager.js";

// ==================================
// COLLABORATION
export {
  CollaborationProvider,
  useCollaboration,
  useCollaborators,
  useRemoteCollaborators,
  noopCollaborationAdapter,
  type CollaborationContextValue,
  type CollaborationProviderProps,
  type CollaboratorPresence,
  type CollaborativeChangeType,
  type CollaborativeChange,
  type CollaborationSession,
  type CanvasCollaborationAdapter,
} from "./collaboration/index.js";

// ==================================
// REACT OVERLAYS (for live elements)
export { LiveReactOverlay, type LiveReactOverlayProps } from "./react/LiveReactOverlay.js";
export {
  CodeEditorOverlay,
  type CodeEditorOverlayProps,
  type EditorFactory,
  type EditorFactoryOptions,
} from "./react/CodeEditorOverlay.js";
