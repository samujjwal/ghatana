/**
 * @fileoverview @ghatana/canvas — public API surface.
 *
 * This file is the single authoritative source for all platform-level
 * public exports of @ghatana/canvas.  Anything NOT exported here is internal
 * and subject to change without notice.
 *
 * Rule: product code MUST import from the subpath exports listed in
 * package.json only.  Direct imports into src/ subdirectories are forbidden.
 *
 * @doc.type module
 * @doc.purpose Public API barrel for @ghatana/canvas
 * @doc.layer platform
 */

// ── Types ─────────────────────────────────────────────────────────────────
export * from '../types/index.js';
export { Bound } from '../utils/bounds.js';

// ── Plugin system ──────────────────────────────────────────────────────────
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
} from '../plugins/types.js';
export {
  PluginRegistrationOptions,
  PluginError,
  PluginErrorCode,
  PluginManager,
  getPluginManager,
} from '../plugins/plugin-manager.js';
export {
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
} from '../plugins/plugin-registry.js';

// ── Hybrid canvas ──────────────────────────────────────────────────────────
export type {
  HybridCanvasProps,
  HybridCanvasState,
  RenderingMode,
  ViewportState,
  SelectionState,
  CoordinateSystem,
  CanvasElement as HybridCanvasElement,
  CanvasNode as HybridCanvasNode,
  CanvasEdge as HybridCanvasEdge,
  LayerConfig as HybridLayerConfig,
} from '../hybrid/types.js';
export {
  HybridCanvasController,
  type HybridCanvasAPI,
} from '../hybrid/hybrid-canvas-controller.js';
export {
  hybridCanvasStore,
  useHybridCanvasState,
  useViewport,
  useSelection,
  useRenderingMode,
  useActiveLayer,
} from '../hybrid/state.js';
export {
  useHybridCanvas,
  useCanvasElements,
  useCanvasNodes,
  useCanvasEdges,
  useCanvasViewport,
  useCanvasSelection,
  useCanvasTool,
} from '../hybrid/hooks.js';
export { HybridCanvas } from '../hybrid/HybridCanvas.js';
export { FreeformLayer } from '../hybrid/FreeformLayer.js';
export { GraphLayer } from '../hybrid/GraphLayer.js';
export { LayerContainer } from '../hybrid/LayerContainer.js';
export { screenToWorld, worldToScreen } from '../hybrid/coordinates.js';

// ── AI integration ─────────────────────────────────────────────────────────
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
} from '../ai/index.js';

// ── Telemetry ──────────────────────────────────────────────────────────────
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
} from '../hooks/useTelemetry.js';

// ── Domain injection (product configuration API) ───────────────────────────
export {
  createCanvasDomainConfig,
  type CanvasDomainConfig,
  type CreateCanvasDomainConfigOptions,
  type DomainPhase,
  type DomainRole,
} from '../core/domain-injection.js';

// ── Export ─────────────────────────────────────────────────────────────────
export {
  exportToPng,
  exportToPdf,
  dataUrlToBlob,
  downloadExportResult,
  type ExportFormat,
  type ExportRegion,
  type ExportOptions,
  type ExportResult,
} from '../export/index.js';

// ── Collaboration ──────────────────────────────────────────────────────────
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
} from '../collaboration/index.js';

// ── React overlay helpers ──────────────────────────────────────────────────
export { LiveReactOverlay, type LiveReactOverlayProps } from '../react/LiveReactOverlay.js';
export {
  CodeEditorOverlay,
  type CodeEditorOverlayProps,
  type EditorFactory,
  type EditorFactoryOptions,
} from '../react/CodeEditorOverlay.js';
export {
  useCanvas,
  Canvas,
  CanvasProps,
  CanvasFlow,
  CanvasFlowProps,
  CanvasFlowElementEvent,
  useCanvasElements as useCanvasRendererElements,
  useCanvasViewport as useCanvasRendererViewport,
} from '../react/index.js';

// ── Tools ──────────────────────────────────────────────────────────────────
export * from '../tools/index.js';
export {
  ConnectorTool,
  type ConnectorToolOptions,
} from '../tools/connector-tool.js';

// ── Chrome & panel components ──────────────────────────────────────────────
export * from '../chrome.js';
export { RoleSwitcher } from '../components/RoleSwitcher.js';
export { SmartContextBar } from '../components/SmartContextBar.js';
export {
  CollaborationCursors,
  useCollaborationCursor,
} from '../components/CollaborationCursors.js';
export { OutlinePanel } from '../components/panels/OutlinePanel.js';
export { LayersPanel } from '../components/panels/LayersPanel.js';
export { PalettePanel } from '../components/panels/PalettePanel.js';
export { TasksPanel } from '../components/panels/TasksPanel.js';
export { MinimapPanel } from '../components/panels/MinimapPanel.js';
export { CommandPalette } from '../components/CommandPalette.js';
export { EnhancedContextMenu } from '../components/EnhancedContextMenu.js';
export { IntegratedCanvasChrome } from '../components/IntegratedCanvasChrome.js';

// ── Theme ──────────────────────────────────────────────────────────────────
export * from '../theme/index.js';
export * from '../theme/theme.js';
export * from '../theme/ThemeProvider.js';

// ── Accessibility ──────────────────────────────────────────────────────────
export * from '../accessibility/accessibility.js';
export * from '../accessibility/AccessibilityProvider.js';

// ── Core systems ───────────────────────────────────────────────────────────
export * from '../core/viewport.js';
export * from '../core/layer-manager.js';
export * from '../core/canvas-renderer.js';
export * from '../core/canvas-advanced.js';
export * from '../core/quick-search.js';
export * from '../core/performance.js';
export * from '../core/accessibility.js';
export * from '../core/action-registry.js';
export {
  ShortcutConfig,
  ShortcutGroup,
  formatShortcut,
  KeyboardShortcutManager as CanvasKeyboardShortcutManager,
  getKeyboardShortcutManager,
  resetKeyboardShortcutManager,
  createKeyboardShortcutHook,
  COMMON_SHORTCUTS,
} from '../core/keyboard-shortcuts.js';
export * from '../core/element-registry.js';
export * from '../core/tool-registry.js';
export * from '../core/connection-manager.js';
export * from '../core/element-operations.js';
export { registerBuiltInElements } from '../core/element-registrations.js';
export * from '../core/canvas-config.js';
export {
  GenericLayerDetector,
  getLayerConfig as getGenericLayerConfig,
  isZoomInLayer,
  getRecommendedZoomForLayer,
} from '../core/generic-layer-system.js';
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
} from '../core/generic-chrome.js';
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
} from '../core/layer-detector.js';
export * from '../hooks/useLayerDetection.js';
export * from '../hooks/useAvailableActions.js';

// ── Drill-down navigation ──────────────────────────────────────────────────
export {
  DrillDownManager,
  type DrillDownEntry,
  type DrillDownEntryType,
  type DrillDownListener,
} from '../core/drill-down-manager.js';

// ── Performance optimizations ──────────────────────────────────────────────
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
} from '../performance/optimizations.js';

// ── Elements ───────────────────────────────────────────────────────────────
export { CanvasElement as CanvasElementBase } from '../elements/base.js';
export * from '../elements/ui-component.js';
export * from '../elements/shape.js';
export * from '../elements/text.js';
export * from '../elements/brush.js';
export * from '../elements/connector.js';
export * from '../elements/pipeline-node.js';
export * from '../elements/code.js';
export * from '../elements/diagram.js';
export * from '../elements/group.js';
export * from '../elements/frame.js';
export * from '../elements/mindmap.js';
export * from '../elements/highlighter.js';
export * from '../elements/image.js';
export * from '../elements/attachment.js';
export * from '../elements/embed.js';
export * from '../elements/rich-text.js';
export * from '../elements/note.js';
export * from '../elements/table.js';
export * from '../elements/callout.js';
export * from '../elements/list.js';
export * from '../elements/divider.js';
export * from '../elements/latex.js';
export * from '../elements/bookmark.js';
export * from '../elements/video.js';
export * from '../elements/audio.js';
export * from '../elements/live-react.js';
export * from '../elements/code-editor.js';
export * from '../elements/data-chart.js';
export * from '../elements/data-metric.js';
export * from '../elements/whiteboard.js';
export * from '../elements/portal.js';

// ── UI Builder integration ─────────────────────────────────────────────────
export * from '../ui-builder/index.js';

// ── Utilities ──────────────────────────────────────────────────────────────
export * from '../utils/export-import.js';
export * from '../utils/layer-detection.js';
