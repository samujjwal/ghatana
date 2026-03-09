/**
 * @ghatana/yappc-canvas - World-Class Canvas Library
 *
 * Production-ready, modular, and highly composable Canvas library for diagramming applications.
 * Built with React 18+, TypeScript, and Jotai state management.
 *
 * Features:
 * - 🎨 Flexible element system (nodes, edges, groups)
 * - ⚡ High-performance rendering with virtualization
 * - 🔄 Reactive state management with Jotai atoms
 * - ♿ Full WCAG 2.2 AA accessibility compliance
 * - 🧪 Comprehensive test coverage
 * - 📱 Touch and mobile optimized
 * - 🎭 Theme management with validation and plugins
 * - 🔗 Legacy migration adapters
 * - 📊 Performance monitoring integration
 * - 👥 Real-time collaboration support
 *
 * @version 1.0.0
 * @author YAPPC Team
 * @license MIT
 */

// Component API - composable CanvasFlow with controlled/uncontrolled modes (NEW)
export {
  CanvasProvider,
  CanvasFlow,
  useCanvas,
  type CanvasProviderProps,
  type CanvasFlowProps,
  type CanvasContextValue,
  type CanvasAPI,
  type CanvasChangeEvent,
  type CanvasChange,
  type CanvasElementEvent,
  type CanvasSelectionEvent,
  type CanvasViewportEvent,
  type CanvasInteractionEvent,
} from './api';

// Main components - production ready
export {
  Canvas,
  CanvasSurface,
  type CanvasProps,
  type CanvasSurfaceProps,
} from './components';

// State management - reactive atoms
export {
  // Core document atoms
  canvasDocumentAtom,
  updateDocumentAtom,

  // Element management
  addElementAtom,
  updateElementAtom,
  removeElementAtom,
  batchUpdateElementsAtom,

  // Selection state
  canvasSelectionAtom,
  updateSelectionAtom,
  clearSelectionAtom,

  // Viewport controls
  canvasViewportAtom,
  updateViewportAtom,
  panViewportAtom,
  zoomViewportAtom,

  // History and undo/redo
  canvasHistoryAtom,
  addHistoryEntryAtom,
  undoAtom,
  redoAtom,
  clearHistoryAtom,

  // UI state management
  canvasUIStateAtom,
  updateUIStateAtom,
  resetUIStateAtom,

  // Performance tracking
  canvasPerformanceAtom,
  updatePerformanceAtom,

  // Collaboration features
  canvasCollaborationAtom,

  // Computed derived atoms
  canvasElementsArrayAtom,
  selectedElementsAtom,
  canvasCapabilitiesAtom,
  hasUnsavedChangesAtom,
  canUndoAtom,
  canRedoAtom,
  boundingBoxAtom,
} from './state';

// Type system - comprehensive and extensible
export type {
  // Core document types
  CanvasDocument,
  CanvasElement,
  CanvasElementMetadata,
  CanvasNode,
  CanvasEdge,
  CanvasGroup,

  // State management types
  CanvasSelection,
  CanvasViewport,
  CanvasHistoryEntry,
  CanvasUIState,
  CanvasPerformanceMetrics,

  // Interaction and theming
  CanvasEvent,
  CanvasTheme,
  CanvasCapabilities,

  // Geometry and positioning
  Point,
  Bounds,
  Transform,
  CanvasLayer,
  PageBuilderTrait,

  // Utilities
  CanvasElementType,
  CanvasEventType,
} from './types/canvas-document';

// Migration adapters for legacy systems
export {
  convertBaseItemToCanvasElement,
  convertReactFlowNodeToCanvasNode,
  convertReactFlowEdgeToCanvasEdge,
  convertCanvasNodeToReactFlowNode,
  convertCanvasEdgeToReactFlowEdge,
  migrateLegacyPersistence,
  type LegacyBaseItem,
  type LegacyReactFlowNode,
  type LegacyReactFlowEdge,
} from './types/adapters';

// Type guards and utilities
export {
  isCanvasNode,
  isCanvasEdge,
  isCanvasGroup,
  createDefaultDocument,
  createDefaultViewport,
  createDefaultCapabilities,
} from './types/canvas-document';

// Migration utilities (Phase C: State Model Unification)
// Note: These are compatibility layers for gradual migration from legacy systems
export * as Migration from './migration';

// Element transformation utilities (Feature 1.2: Element Manipulation)
export {
  snapValue,
  snapRotation,
  calculateRotationDelta,
  applyRotation,
  updateLayerOrder,
  batchUpdatePositions,
  calculateSnapLines,
  getBoundingBox,
  type BaseElement,
  type SnapLine,
  type Point as TransformPoint,
} from './elements/transformations';

// Grid & snapping utilities (Feature 2.4: Grid, Snapping & Alignment - Enhanced)
export {
  snapToGrid,
  snapPointToGrid,
  getGridLines,
  distributeElements,
  alignElements,
  isNearGridLine,
  getSnapSuggestions,
  // Feature 2.4 Enhancements: Smart alignment guides
  getSmartAlignmentGuides,
  snapToAlignmentGuides,
  calculateSpacingDistribution,
  alignToSelectionBounds,
  getEnhancedGridLines,
  type GridConfig,
  type DistributionOptions,
  type AlignmentOptions,
  type Point as GridPoint,
  // Bounds already exported from canvas-document
  type AlignmentGuide,
  type SmartSnapResult,
} from './layout/snapEngine';

// Infinite canvas viewport utilities (Feature 2.5: Infinite Canvas)
export {
  shouldShiftOrigin,
  computeOriginShiftDelta,
  getViewportBounds,
  screenToWorld,
  worldToScreen,
  isPointVisible,
  isRectVisible,
  getTiledBackgroundOffset,
  clampZoom,
  fitElementsInView,
  zoomAtPoint,
  testCoordinateAccuracy,
  validateOriginShiftConfig,
  type Point as ViewportPoint,
  type Viewport,
  type ViewportTransform,
  type OriginShiftConfig,
} from './viewport/infiniteSpace';

// Minimap & Viewport Controls (Feature 2.9)
export {
  MinimapPanel,
  type MinimapPanelProps,
} from './components/MinimapPanel';

export {
  calculateCanvasBounds,
  worldToMinimapCoordinates,
  calculateMinimapViewport,
  handleMinimapClick,
  isPointInMinimapViewport,
  createMinimapConfig,
  zoomToSelection,
  applyKeyboardZoom,
  createZoomConfig,
  type MinimapNode,
  type MinimapConfig,
  type MinimapViewport as MinimapViewportRect,
  type ZoomConfig,
  type CanvasBounds,
} from './viewport/minimapState';

// Document management utilities (Feature 1.4: Document Management)
export {
  // History management
  createHistoryManager,
  addHistory,
  undo,
  redo,
  canUndo,
  canRedo,
  clearHistory,
  batchHistory,
  // Version management
  createVersion,
  diffVersions,
  // Template management
  createTemplate,
  updateTemplate,
  filterTemplates,
  // Autosave coordination
  createAutosaveState,
  shouldAutosave,
  markDirty,
  markSaved,
  markSavePending,
  // Types
  type HistoryEntry,
  type HistoryConfig,
  type HistoryState,
  type DocumentVersion,
  type VersionDiff,
  type DocumentTemplate,
  type AutosaveState,
} from './history/historyManager';

// Document management UI components (Feature 1.4: Document Management)
export {
  TemplateLibraryDialog,
  VersionComparisonModal,
  AutosaveIndicator,
  AutosaveStatus,
  type TemplateLibraryDialogProps,
  type VersionComparisonModalProps,
  type AutosaveIndicatorProps,
} from './components';

// Accessibility features (Features 1.12-1.13, 2.31: Keyboard Navigation & Assistive Tech & Screen Reader Enhancements)
export {
  // Keyboard shortcuts
  ShortcutRegistry,
  globalShortcutRegistry,
  useKeyboardShortcuts,
  CANVAS_SHORTCUTS,
  type KeyboardShortcut,
  type ModifierKey,
  type ShortcutConflict,
  // ARIA roles
  getNodeAriaProps,
  getEdgeAriaProps,
  getCanvasAriaProps,
  AriaAnnouncer,
  globalAnnouncer,
  useAriaAnnouncer,
  describeNodeRelationships,
  CANVAS_ANNOUNCEMENTS,
  useCanvasAnnouncements,
  type CanvasAriaRole,
  type AriaProperties,
  type AnnouncePolite,
  // Reduced motion
  prefersReducedMotion,
  useReducedMotion,
  getAnimationDuration,
  useAnimationConfig,
  getTransition,
  useTransition,
  getSpringConfig,
  CANVAS_ANIMATIONS,
  getCanvasAnimation,
  useCanvasAnimations,
  isZoomLevelSafe,
  clampZoomLevel,
  getResponsiveFontSize,
  isBrowserZoomHigh,
  useZoomResiliency,
  DEFAULT_ANIMATION_CONFIG,
  type AnimationConfig,
  type SpringConfig,
  // Screen Reader Enhancements (Feature 2.31)
  createScreenReaderEnhancements,
  announceNodeRelationships,
  announceCollaborativeEdit,
  getKeyboardShortcutHelp,
  announceKeyboardShortcuts,
  announceCustom,
  getNextAnnouncement,
  clearAnnouncementQueue,
  setScreenReaderEnabled,
  updateScreenReaderConfig,
  registerKeyboardShortcut,
  unregisterKeyboardShortcut,
  getAnnouncementStatistics,
  describeRelationships,
  type ScreenReaderConfig,
  type ScreenReaderEnhancementState,
  type NodeRelationships,
  type CollaborativeEditEvent,
  type PolitenessLevel,
  type ShortcutCategory,
  type SR_KeyboardShortcut,
} from './accessibility';

// Theming system (Feature 1.14: Visual Styling & Customization)
export {
  // Theme management
  ThemeManager,
  LIGHT_THEME,
  DARK_THEME,
  THEMES,
  useTheme,
  globalThemeManager,
  // Theme validation
  validateTheme,
  validateThemeJSON,
  checkContrast,
  CanvasThemeSchema,
  // Plugin system
  PluginManager,
  globalPluginManager,
  // Types
  type ThemeMode,
  type ThemeValidationResult,
  type Plugin,
  type PluginMetadata,
  type PluginPermissions,
  type PluginAPI,
  type PluginTool,
  type RenderHook,
} from './theming';

// Layout & Routing (Feature 2.1: Edge Routing & Connectors)
export {
  routeEdge,
  routeWithWaypoints,
  updateWaypoint,
  addWaypoint,
  removeWaypoint,
  getPointOnPath,
  type EdgeRouterConfig,
  type EdgeRoute,
  type Waypoint,
  type RoutingAlgorithm,
} from './layout/edgeRouter';

// Auto-layouts (Feature 2.2: Auto-layouts)
export {
  applyLayout,
  getLayoutPreset,
  getAllLayoutPresets,
  LAYOUT_PRESETS,
  type LayoutAlgorithm,
  type LayoutDirection,
  type LayoutConfig,
  type LayoutNode,
  type LayoutEdge,
  type LayoutResult,
  type LayoutPreset,
} from './layout/layoutEngine';

// Rendering Optimizations (Feature 1.10: Virtual Viewport & LOD System)
export {
  // Virtual Viewport
  createVirtualViewport,
  createVisibilityChecker,
  VirtualViewportUtils,
  type ViewportBounds,
  type VirtualViewportConfig,
  type VisibilityResult,
  type ViewportStats,
  // LOD System
  createLODSystem,
  LODLevel,
  DEFAULT_LOD_CONFIG,
  PERFORMANCE_LOD_CONFIG,
  QUALITY_LOD_CONFIG,
  GlyphRenderers,
  ProgressiveRendering,
  LODTransitions,
  createLODPerformanceMonitor,
  type LODConfig as RenderingLODConfig,
  type LODRenderInstruction,
  type ElementTypeLODConfig,
  type LODSystemInstance,
  type LODPerformanceMetrics,
  type LODPerformanceMonitor,
  // WebGL Renderer
  createWebGLRenderer,
  DEFAULT_WEBGL_CONFIG,
  WebGLRendererUtils,
  type WebGLRendererConfig,
  type WebGLCapabilities,
  type WebGLRenderStats,
  type WebGLRendererInstance,
} from './rendering';

// Renderer Abstraction (Feature 2.27: Renderer Switcher with Production WebGL)
export {
  // Renderer Switcher
  RendererSwitcher,
  DOMRenderer,
  WebGLRenderer, // Legacy basic implementation
  createRendererSwitcher,
  detectBestRenderer,
  type IRenderer,
  type RendererType,
  type RendererCapabilities,
  type CanvasState as RendererCanvasState, // Renamed to avoid conflict
  type RendererPerformance,
  type RendererSwitcherConfig,
  type WebGLFallbackReason,
  type RendererSwitchEvent,
  type PluginAdapter,
  // Production WebGL Renderer (integrates Feature 1.10 WebGL)
  ProductionWebGLRenderer,
} from './renderer';

// Layer System with Tags (Feature 2.6: Layer System with Tags)
export {
  createLayerStore,
  createLayer,
  getLayer,
  getAllLayers,
  getLayersInRenderOrder,
  updateLayer,
  deleteLayer,
  reorderLayers,
  moveLayerUp,
  moveLayerDown,
  moveLayerToTop,
  moveLayerToBottom,
  assignElementToLayer,
  assignElementsToLayer,
  removeElementFromLayer,
  getLayerForElement,
  toggleLayerVisibility,
  toggleLayerLock,
  setActiveLayer,
  getActiveLayer,
  createTag,
  getTag,
  getAllTags,
  updateTag,
  deleteTag,
  addTagToElement,
  removeTagFromElement,
  getTagsForElement,
  getElementsByTag,
  searchTags,
  createViewpoint,
  getViewpoint,
  getAllViewpoints,
  updateViewpoint,
  deleteViewpoint,
  applyViewpoint,
  setLayerPermission,
  getLayerPermission,
  canViewLayer,
  canEditLayer,
  canAdminLayer,
  getVisibleLayersForUser,
  removeLayerPermissions,
  type Layer,
  type Tag,
  type Viewpoint,
  type LayerPermission,
  type Permission,
  type LayerStore,
} from './layers/layerStore';

// Semantic Zoom & Drill-down (Feature 2.7: Semantic Zoom & Drill-down)
export {
  createSemanticZoomConfig,
  createDrillDownState,
  registerLODConfig,
  getLODConfig,
  getAllLODConfigs,
  getActiveDetailLevel,
  shouldRenderElement,
  getVisibleElementTypes,
  updateZoomLevel,
  createNestedScene,
  drillDown,
  drillUp,
  navigateToScene,
  getCurrentScene,
  getParentScene,
  getScene,
  getBreadcrumbs,
  loadNestedScene,
  isSceneLoading,
  isSceneCached,
  clearCache,
  getSceneDepth,
  isAtRoot,
  resetToRoot,
  createStandardLODConfig,
  createPerformanceLODConfig,
  createLabelLODConfig,
  type ZoomThreshold,
  type LODConfig,
  type NestedScene,
  type BreadcrumbItem,
  type DrillDownState,
  type SemanticZoomConfig,
} from './navigation/semanticZoom';

// Multi-Page Management with Deep Linking (Feature 2.10: Multi-page & Deep Linking)
export {
  createPageManagerState,
  createPage,
  getPage,
  getAllPages,
  updatePage,
  deletePage,
  reorderPages,
  setActivePage,
  getActivePage,
  nextPage,
  previousPage,
  duplicatePage,
  createDeepLink,
  getDeepLink,
  navigateToDeepLink,
  deleteDeepLink,
  getDeepLinksForPage,
  createPortalLink,
  getPortalLink,
  getPortalLinkByElement,
  navigateToPortalLink,
  deletePortalLink,
  getPortalLinksFromPage,
  generateDeepLinkURL,
  parseDeepLinkFromURL,
  historyBack,
  historyForward,
  canGoBack,
  canGoForward,
  getPageCount,
  searchPages,
  type PageManagerState,
  type Page,
  type DeepLink,
  type PortalLink,
} from './navigation/pageManager';

// Undo/Redo with Timeline & Checkpoints (Feature 2.11: Undo/Redo UX Enhancements)
export {
  createCheckpointManagerState,
  setActor,
  recordAction,
  undo as undoWithConflictDetection,
  redo as redoTimeline,
  forceUndo,
  canUndo as canUndoTimeline,
  canRedo as canRedoTimeline,
  getTimelinePosition,
  createCheckpoint,
  getCheckpoint,
  getAllCheckpoints,
  restoreCheckpoint,
  deleteCheckpoint,
  updateCheckpoint,
  createBranch,
  getBranch,
  getAllBranches,
  switchBranch,
  getConflict,
  getUnresolvedConflicts,
  resolveConflict,
  clearResolvedConflicts,
  getActionsByActor,
  getActionsByType,
  getActionsInRange,
  getTimelineStats,
  searchCheckpoints,
  clearTimeline,
  exportTimeline,
  importTimeline,
  type CheckpointManagerState,
  type TimelineAction,
  type Checkpoint,
  type UndoConflict,
  type TimelineBranch,
  type RecordActionOptions,
  type CheckpointOptions,
  type ConflictResolutionOptions,
} from './history/checkpointManager';

// Template Gallery System (Feature 2.12: Template System with Gallery)
export {
  createGalleryState,
  addToGallery,
  removeFromGallery,
  setCategory,
  getTemplatesByCategory,
  getFeaturedTemplates,
  setFeatured,
  recordUsage,
  getRecentlyUsed,
  getPopularTemplates,
  getTopRatedTemplates,
  rateTemplate,
  applyParameters,
  getParameterDefaults,
  checkForUpdates,
  searchTemplates,
  exportGallery,
  importGallery,
  getGalleryStats,
  type TemplateGalleryState,
  type GalleryTemplate,
  type ParameterizedTemplate,
  type TemplateParameter,
  type TemplateCategory,
  type TemplateUpdate,
} from './templates/templateManager';

// Import/Export Format Adapters (Feature 2.13: Import/Export Format Coverage)
export {
  exportToMermaid,
  importFromPlantUML,
  exportToC4,
  importFromC4,
  validateC4RoundTrip,
  getFormatCapabilities,
  type MermaidExportOptions,
  type PlantUMLImportOptions,
  type AdapterResult,
} from './interop/formatAdapters';

// Stable IDs & Semantic Diffing (Feature 2.15: Stable IDs & Diffing)
export {
  // ID generation
  createIDGeneratorState,
  generateContentHash,
  generateID,
  checkCollision,
  validateID,
  normalizeID,
  batchGenerateIDs,
  createIDRemapping,
  applyIDRemapping,
  getIDStatistics,
  // Types
  type IDStrategy,
  type IDGeneratorState,
  type IDOptions,
  type IDValidation,
  type NormalizationOptions,
  type CollisionResult,
} from './persistence/idStrategy';

export {
  // Semantic diff
  diff,
  applyPatch,
  generateDiffSummary,
  exportPatchesJSON,
  importPatchesJSON,
  mergeDiffs,
  filterDiffByType,
  // Types
  type ChangeType,
  type OperationType,
  type JSONPatchOperation,
  type PropertyChange,
  type ElementChange,
  type DiffResult,
  type DiffStatistics,
  type DiffOptions,
} from './persistence/semanticDiff';

// Presentation Mode (Feature 2.18: Presentation Mode)
export {
  // State management
  createPresentationState,
  // Frame CRUD
  createFrame,
  getFrame,
  getAllFrames,
  updateFrame,
  deleteFrame,
  reorderFrames,
  // Presentation control
  startPresentation,
  endPresentation,
  nextFrame,
  previousFrame,
  jumpToFrame,
  applyNavigation,
  // Frame queries
  getCurrentFrame,
  canGoForward as canGoForwardInPresentation,
  canGoBackward,
  getFrameCount,
  getCurrentFrameNumber,
  // Presenter/audience mode
  togglePresenterMode,
  updateAudienceConfig,
  generateShareLink,
  sanitizeFrameForAudience,
  getAudienceFrames,
  // Utilities
  duplicateFrame,
  searchFrames,
  getPresentationProgress,
  getPresentationStats,
  // Import/export
  exportPresentation,
  importPresentation,
  // Types
  type Frame,
  type PresentationState,
  type AudienceConfig,
  type NavigationResult,
  type TransitionType,
} from './presentation/frameStore';

// Ready-for-Dev Workflow (Feature 2.19: Ready-for-Dev Workflow)
export {
  // Workflow management
  createWorkflowState,
  // Task CRUD
  createTask,
  getTask,
  getAllTasks,
  updateTask,
  completeTask,
  deleteTask,
  reorderTasks,
  // Workflow stages
  changeStage,
  canTransitionToStage,
  isReadyForDev,
  // Validation
  addValidationRule,
  removeValidationRule,
  validateWorkflow,
  // Notifications
  markNotificationRead,
  getUnreadNotifications,
  clearNotifications,
  // Locking
  lockWorkflow,
  unlockWorkflow,
  // Statistics
  getWorkflowStatistics,
  // Export/import
  exportSpecBundle,
  exportSpecBundleJSON,
  importSpecBundle,
  // Search and filtering
  searchTasks,
  filterTasksByStatus,
  filterTasksByPriority,
  getBlockedTasks,
  getReadyTasks,
  // Templates
  CHECKLIST_TEMPLATES,
  // Types
  type ChecklistTask,
  type ChecklistTemplate,
  type ValidationRule,
  type WorkflowNotification,
  type SpecBundle,
  type WorkflowState,
  type CreateWorkflowOptions,
  type TaskPriority,
  type TaskStatus,
  type WorkflowStage,
} from './workflow/checklistStore';

// Lifecycle State Machine (YAPPC Task Execution System)
export {
  LifecycleStateMachine,
  createLifecycleStateMachine,
  generateDefaultTransitions,
  STAGE_ORDER,
  STAGE_GROUPS,
  type TransitionType,
  type TransitionValidation,
  type TransitionError,
  type TransitionWarning,
  type TransitionContext,
  type TransitionHook,
  type StageTransitionConfig,
  type LifecycleState,
  type StageHistoryEntry,
  type LifecycleStateMachineConfig,
  type TransitionResult,
  type SerializedLifecycleState,
} from './workflow/lifecycleStateMachine';

// Workflow Template Engine (YAPPC Task Execution System)
export {
  WorkflowTemplateEngine,
  type WorkflowStatus,
  type PhaseStatus,
  type WorkflowInstance,
  type WorkflowPhaseInstance,
  type WorkflowStepInstance,
  type InstantiationParams,
  type TaskRegistry,
  type SerializedWorkflowInstance,
} from './workflow/workflowTemplateEngine';

// Task Execution Coordinator (YAPPC Task Execution System)
export {
  TaskExecutionCoordinator,
  type TaskExecutionRequest,
  type ExecutionPriority,
  type ExecutionMode,
  type AgentMapping,
  type DomainAgentConfig,
  type TaskAgentConfig,
  type AgentConfig,
  type ExecutionResult,
  type ExecutionArtifact,
  type ExecutionError,
  type ExecutionMetrics,
  type ValidationResult,
  type ValidationError,
  type AgentOrchestrator,
  type AgentExecutionResult,
  type AgentStatus,
  type ExecutionEvent,
  type ExecutionEventType,
} from './workflow/taskExecutionCoordinator';

// Developer Tools (Feature 1.17: Developer Tooling) - Development only
export {
  DevInspector,
  EventLog,
  type DevInspectorProps,
  type EventLogProps,
} from './devtools';

// Compliance Mapping (Feature 2.26: Compliance Mapping)
export {
  // Store creation
  createComplianceStore,
  // Control tagging
  tagControl,
  updateControlTag,
  removeControlTag,
  // Queries
  getElementTags,
  getControlTags,
  getFrameworkTags,
  // Coverage and reporting
  getCoverageReport,
  getComplianceStatistics,
  // Control definitions
  registerControlDefinition,
  getControlDefinition,
  // Audit bundle export
  exportAuditBundle,
  // Search
  searchControlTags,
  // Types
  type ComplianceFramework,
  type ControlStatus,
  type GapSeverity,
  type ControlDefinition,
  type ControlTag,
  type ControlGap,
  type CoverageReport,
  type AuditBundleOptions,
  type AuditBundle,
  type AuditLogEntry,
  type ComplianceStoreConfig,
  type ComplianceStore,
} from './compliance/complianceStore';

// Persona Node Types (Journey Implementation)
export {
  personaNodeTypes,
  AIPromptNode,
  ServiceNode,
  DatabaseNode,
  APIEndpointNode,
  UIScreenNode,
  TestSuiteNode,
  type AIPromptNodeData,
  type ServiceNodeData,
  type DatabaseNodeData,
  type APIEndpointNodeData,
  type UIScreenNodeData,
  type TestSuiteNodeData,
} from './components/PersonaNodes';

// Journey Templates (Persona Workflows)
export {
  journeyTemplates,
  type JourneyTemplate,
  type JourneyNode,
  type JourneyEdge,
} from './templates/journeyTemplates';

// Code Generation Integration
export {
  generateCodeFromNode,
  generateCodeFromFlow,
  type CodeGenerationRequest,
  type CodeGenerationOptions,
  type GeneratedFile,
  type CodeGenerationResult,
} from './integration/codeGeneration';

// AI-Powered Code Generation
export {
  AICodeGenerationService,
  createAICodeGenerationService,
  type AICodeGenerationOptions,
} from './integration/aiCodeGeneration';

// Code Scaffolding Dialog (Journey 3.1: Developer Workflow)
export {
  CodeScaffoldDialog,
  type CodeScaffoldDialogProps,
  type ScaffoldOptions,
} from './components/CodeScaffoldDialog';

// Security Alerts Component (Cross-cutting: Security Monitoring)
export {
  SecurityAlerts,
  type SecurityAlertsProps,
} from './components/SecurityAlerts';

// OpenAPI Generator Dialog (Journey 6.1: Backend Engineer - OpenAPI Generation)
export {
  OpenAPIGeneratorDialog,
  type OpenAPIGeneratorDialogProps,
} from './components/OpenAPIGeneratorDialog';

// Full-Stack Mode Toggle (Journey 8.1: Full-Stack Developer - Split-Screen Mode)
export {
  FullStackModeToggle,
  type FullStackModeToggleProps,
} from './components/FullStackModeToggle';

// Service Health Canvas (Journey 13.1: SRE - Real-Time Incident Response)
export {
  ServiceHealthCanvas,
  type ServiceHealthCanvasProps,
} from './components/ServiceHealthCanvas';

// Component Generator Dialog (Journey 7.1: Frontend Engineer - Component Development)
export {
  ComponentGeneratorDialog,
  type ComponentGeneratorDialogProps,
} from './components/ComponentGeneratorDialog';

// Storybook Service (Journey 7.1: Frontend Engineer - Component Development)
export {
  StorybookService,
  type ComponentProp,
  type StoryVariant,
  type StorybookGenerationOptions,
  type GeneratedStory,
} from './services/StorybookService';

// Design Mode Editor (Journey 5.1: UX Designer - High-Fidelity Mockups)
export {
  DesignModeEditor,
  type DesignModeEditorProps,
  type DesignComponent,
  type DesignComponentType,
  type PrototypeLink,
} from './components/DesignModeEditor';

// Mobile Canvas (Journey 9.1: Mobile Engineer - Mobile Screen Design)
export {
  MobileCanvas,
  type MobilePlatform,
  type MobileComponentType,
  type MobileComponent,
} from './components/MobileCanvas';

export {
  DataPipelineCanvas,
  type PipelineNodeType,
  type DataSourceType,
  type TransformationType,
  type SinkType,
  type PipelineNode,
} from './components/DataPipelineCanvas';

// User Journey Canvas (Journey 19.1: UX Researcher - User Journey Mapping)
export {
  UserJourneyCanvas,
  type JourneyStage,
  type JourneyTouchpoint,
  type JourneyPainPoint,
  type EmotionType,
} from './components/UserJourneyCanvas';

// Service Blueprint Canvas (Journey 20.1: Service Designer - Service Blueprint)
export {
  ServiceBlueprintCanvas,
  type LaneType,
  type ProcessNode,
  type NodeConnection,
  type Touchpoint,
  type BlueprintLane,
} from './components/ServiceBlueprintCanvas';

// Threat Modeling Canvas (Journey 11.1: Security Engineer - Threat Modeling)
export { ThreatModelingCanvas } from './components/ThreatModelingCanvas';

export type {
  ThreatCategory,
  RiskLevel,
  Threat,
  Asset,
  Mitigation,
} from './hooks/useThreatModeling';

// CI/CD Pipeline Canvas (Journey 12.1: DevOps Engineer - CI/CD Pipeline)
export { CICDPipelineCanvas } from './components/CICDPipelineCanvas';

export type {
  StageType,
  StageStatus,
  PipelineStage,
  PipelineStep,
} from './hooks/useCICDPipeline';

// Microservices Extractor Canvas (Journey 14.1: Solution Architect - Microservices Extraction)
export {
  MicroservicesExtractorCanvas,
  type MonolithEntity,
  type BoundedContext,
  type ServiceBoundary,
  type ExtractionStrategy,
  type CouplingLevel,
} from './components/MicroservicesExtractorCanvas';

// Zero-Trust Architecture Canvas (Journey 15.1: Security Architect - Zero-Trust Design)
export {
  ZeroTrustArchitectureCanvas,
  type SecurityZone,
  type IdentityProvider,
  type PolicyRule,
  type TrustLevel,
  type SecurityZoneType,
} from './components/ZeroTrustArchitectureCanvas';

// OpenAPI Service (Journey 6.1: Backend Engineer - OpenAPI Generation)
export {
  OpenAPIService,
  type OpenAPISpec,
  type OpenAPIEndpoint,
  type OpenAPISchema,
  type OpenAPIParameter,
  type OpenAPIRequestBody,
  type OpenAPIResponse,
  type OpenAPIGenerationOptions,
  type APINodeData,
  type HTTPMethod,
  type AuthenticationType,
  type ParameterLocation,
} from './services/OpenAPIService';

// DevSecOps Workflow Integration
export {
  DevSecOpsCanvasIntegration,
  createDevSecOpsCanvasIntegration,
  type DeploymentConfig,
  type DeploymentResult,
  type InfrastructureProvisionResult,
} from './integration/devSecOpsIntegration';

// Policy-driven Export (Feature 2.33: Policy-driven Export)
export {
  // Store management
  createExportPolicyStore,
  registerPolicy,
  setActivePolicy,
  getActivePolicy,
  removePolicy,
  updatePolicy,
  // Export operations
  secureExport,
  applyRedaction,
  applyWatermark,
  signData,
  verifySignature,
  // Queries and utilities
  getExportStatistics,
  getExportAuditTrail,
  getPoliciesForSensitivity,
  pruneAuditLog,
  // Types
  type SensitivityLevel,
  type RedactionStrategy,
  type ExportFormat,
  type WatermarkPosition,
  type RedactionRule,
  type WatermarkConfig,
  type SigningConfig,
  type ExportPolicy,
  type ExportContext,
  type RedactionResult,
  type RedactedField,
  type WatermarkResult,
  type SignatureResult,
  type SecureExportBundle,
  type AuditEntry,
  type ExportPolicyStore,
} from './security/exportPolicy';

// Audit Trail Hardening (Feature 2.34: Audit Trail Hardening)
export {
  // Ledger management
  createAuditLedger,
  appendAuditEntry,
  // Integrity verification
  verifyChainIntegrity,
  // Retention policies
  applyRetentionPolicies,
  addRetentionPolicy,
  removeRetentionPolicy,
  // Export operations
  exportAuditLedger,
  // Query operations
  getEntriesByActor,
  getEntriesByResource,
  getEntriesByTier,
  getAuditStatistics,
  searchAuditEntries,
  // Types
  type AuditEventType,
  type AuditSeverity,
  type StorageTier,
  type AuditMetadata,
  type AuditLedgerEntry,
  type RetentionPolicy,
  type AuditExportOptions,
  type AuditExportBundle,
  type ChainVerificationResult,
  type ChainIssue,
  type AuditLedger,
} from './security/auditLedger';

// Feature catalog derived from documentation (stories, acceptance criteria, tests)
export * as FeatureStories from './features/stories';

// Journey template hooks and actions
export * from './hooks';

// Example journey flows
export * from './examples';

// Persona Canvas Components (Task 8: Specialized Persona Canvases)
export {
  PersonaCanvas,
  PersonaSwitcher,
  CompactPersonaSwitcher,
  usePersonaConfig,
  usePersonaFeature,
  usePersonaToolbar,
  usePersonaPanels,
  type PersonaCanvasProps,
  type PersonaSwitcherProps,
} from './components';

// Persona Types and Configurations
export type {
  PersonaType,
  PersonaViewMode,
  PersonaCanvasLayout,
  PersonaToolbarConfig,
  PersonaNodeStyle,
  PersonaPanelConfig,
  PersonaCanvasConfig,
  PersonaNodeFilter,
  PersonaEdgeFilter,
} from './types/persona';

export {
  PM_CANVAS_CONFIG,
  ARCHITECT_CANVAS_CONFIG,
  DEVELOPER_CANVAS_CONFIG,
  QA_CANVAS_CONFIG,
  PERSONA_CONFIGS,
  getPersonaConfig,
  getAvailablePersonas,
} from './config/personaConfigs';

// Canvas Collaboration Backend Integration (Week 1 Implementation)
export {
  useCanvasCollaborationBackend,
  type UseCanvasCollaborationBackendConfig,
  type CanvasCollaborationState,
  type RemoteUser,
  type CanvasJoinPayload,
  type CanvasLeavePayload,
  type CanvasUpdatePayload,
  type CanvasCursorPayload,
  type CanvasSelectionPayload,
} from './hooks/useCanvasCollaborationBackend';

export {
  CanvasCollaborationProvider,
  useCanvasCollaboration,
  type CanvasCollaborationProviderProps,
} from './integration/CanvasCollaborationProvider';

export {
  RemoteCursor,
  type RemoteCursorProps,
} from './components/RemoteCursor';

export {
  CollaborationBar,
  type CollaborationBarProps,
} from './components/CollaborationBar';

// Version information
export const CANVAS_VERSION = '1.0.0';
export const CANVAS_DOCUMENT_VERSION = '1.0.0';

// ============================================================================
// Universal Canvas Framework (New Architecture)
// ============================================================================

// Universal Model & Contracts
export * from './model/contracts';
export * from './model/validation';

// Command System
export * from './commands/CommandTypes';
export {
  CommandDispatcher,
  getCommandDispatcher,
} from './commands/CommandDispatcher';
export * from './commands/core/NodeCommands';

// Artifact Registry & Extensions
export {
  ArtifactRegistry,
  getArtifactRegistry,
} from './registry/ArtifactRegistry';
export {
  ExtensionRegistry,
  getExtensionRegistry,
} from './registry/ExtensionPoints';
export type {
  RenderExtension,
  ValidationExtension,
  TransformExtension,
  BindingExtension,
  ExportExtension,
  ImportExtension,
  ToolExtension,
} from './registry/ExtensionPoints';

// Builtin Artifact Contracts
export * from './registry/builtins/ui-components';
export * from './registry/builtins/diagram-nodes';
export * from './registry/builtins/portal-node';

// Interaction System
export { DragDropManager } from './interaction/DragDropManager';
export type {
  DragSourceType,
  DragSource,
  DropTarget,
  DragContext,
} from './interaction/DragDropManager';
export { DropTargetResolver } from './interaction/DropTargetResolver';
export type {
  HitTestResult,
  ConstraintValidator,
} from './interaction/DropTargetResolver';
export { ToolManager, BuiltInTools } from './interaction/ToolManager';
export type {
  ToolDefinition,
  ToolState,
  ToolEventHandler,
  ToolOptions,
} from './interaction/ToolManager';

// Universal Renderer Components
export {
  InfiniteViewport,
  useViewport as useUniversalViewport,
} from './renderer/InfiniteViewport';
export type {
  ViewportState as UniversalViewportState,
  ViewportConfig as UniversalViewportConfig,
  ViewportTransform as UniversalViewportTransform,
} from './renderer/InfiniteViewport';

export { CanvasSurface as UniversalCanvasSurface } from './renderer/CanvasSurface';
export type {
  CanvasSurfaceProps as UniversalCanvasSurfaceProps,
  NodeRenderState,
} from './renderer/CanvasSurface';

export { NodeWrapper, calculateResizeDelta } from './renderer/NodeWrapper';
export type { NodeWrapperProps, ResizeHandle } from './renderer/NodeWrapper';

export {
  useSemanticZoom as useUniversalSemanticZoom,
  calculateFitZoom,
  getZoomDescription,
  DEFAULT_ZOOM_RANGES,
  DetailLevel,
} from './renderer/useSemanticZoom';
export type {
  SemanticZoomResult,
  SemanticZoomConfig as UniversalSemanticZoomConfig,
  VisibilityState,
  ZoomRange,
  NodeRepresentation,
} from './renderer/useSemanticZoom';

// UI Shell Components
export { PalettePanel } from './shell/PalettePanel';
export type { PalettePanelProps, PaletteViewMode } from './shell/PalettePanel';

export { InspectorPanel } from './shell/InspectorPanel';
export type { InspectorPanelProps, InspectorTab } from './shell/InspectorPanel';

// Developer tooling / playground
export { CanvasPlayground } from './dev/CanvasPlayground';
export type { } from './dev/CanvasPlayground';

// AI Integration
export { ShadowCodegen, getShadowCodegen } from './ai/ShadowCodegen';
export type {
  CodegenTarget,
  CodegenOptions,
  CodegenResult,
  CodeGenerator,
} from './ai/ShadowCodegen';

export { PredictiveLayout, getPredictiveLayout } from './ai/PredictiveLayout';
export type {
  LayoutSuggestion,
  LayoutAnalysis,
  LayoutPattern,
  LayoutIssue,
  LayoutPreview,
  LayoutGuide,
  LayoutHighlight,
  SnapTarget,
  SuggestionType,
  SuggestionConfidence,
} from './ai/PredictiveLayout';
// ============================================================
// UNIFIED CANVAS ENHANCEMENTS (v2.0)
// ============================================================

// Core Chrome Components
export { CanvasChromeLayout } from './components/CanvasChromeLayout';
export type { CanvasChromeLayoutProps } from './components/CanvasChromeLayout';

export { Frame } from './components/Frame';
export type { FrameProps } from './components/Frame';

export { OutlinePanel } from './components/OutlinePanel';
export type { OutlinePanelProps } from './components/OutlinePanel';

export { ContextBar } from './components/ContextBar';
export type { ContextBarProps } from './components/ContextBar';

export { ZoomHUD } from './components/ZoomHUD';
export type { ZoomHUDProps } from './components/ZoomHUD';

export {
  ContrastDebugOverlay,
  useContrastDebug,
  logContrastIssues,
} from './components/ContrastDebugOverlay';

export { UnifiedCanvasApp } from './components/UnifiedCanvasApp';

// Design Token System
export {
  CANVAS_Z_INDEX,
  validateZIndexHierarchy,
  getZIndex,
  isOverlayLayer,
  isCanvasContentLayer,
} from './config/z-index';

export {
  PHASE_COLORS,
  PHASE_METADATA,
  getPhaseDefinition,
  getAllPhases,
  getNextPhase,
  getPreviousPhase,
  getPhaseCustomProperties,
} from './config/phase-colors';

// Chrome State Management
export {
  chromeCalmModeAtom,
  chromeLeftRailVisibleAtom,
  chromeContextBarVisibleAtom,
  chromeInspectorVisibleAtom,
  chromeOutlineVisibleAtom,
  chromeMinimapVisibleAtom,
  chromePaletteVisibleAtom,
  chromeConnectorsVisibleAtom,
  chromeLayersVisibleAtom,
  chromeAnyPanelVisibleAtom,
  chromeContentInsetAtom,
  CHROME_DIMENSIONS,
  CHROME_ANIMATION_DURATION,
  CHROME_HOVER_DELAY,
  CHROME_AUTO_HIDE_DELAY,
} from './state/chrome-atoms';

// Accessibility Utilities
export {
  calculateContrastRatio,
  validateContrast,
  validatePhaseColors,
  validateSemanticTokens,
  checkAllTokenPairs,
  getFailingChecks,
  generateContrastReport,
  suggestAccessibleAlternatives,
  formatRatio,
  getLevelEmoji,
  WCAG_LEVELS,
} from './utils/contrast-checker';

export type { ContrastCheck } from './utils/contrast-checker';

// ============================================================
// EPIC 9: COMMAND PALETTE INTEGRATION
// ============================================================

// Command System (Epic 9: Story 9.1-9.5)
export {
  // Command definitions
  ALL_CANVAS_COMMANDS,
  FRAME_COMMANDS,
  NAVIGATION_COMMANDS,
  PANEL_COMMANDS,
  executeCanvasCommand,
  searchCanvasCommands,
  getCommandsByCategory,
  getCanvasCommand,
  // React integration
  useCanvasCommands,
  useCanvasCommandsForPalette,
  CanvasCommandProvider,
  type CanvasCommand,
  type CanvasCommandContext,
  type CanvasCommandCategory,
} from './commands';

// ============================================================
// EPIC 10: ONBOARDING & TELEMETRY
// ============================================================

// Onboarding System (Epic 10: Story 10.1-10.2)
export {
  // Guided tour
  OnboardingTour,
  useOnboardingTour,
  CANVAS_TOUR_STEPS,
  // Feature hints
  FeatureHintsManager,
  useFeatureHints,
  CANVAS_FEATURE_HINTS,
  type TourStep,
  type FeatureHint,
  type HintTrigger,
  type TooltipPosition,
} from './onboarding';

// Telemetry & Analytics (Epic 10: Story 10.3)
export {
  CanvasTelemetry,
  getCanvasTelemetry,
  useCanvasTelemetry,
  usePerformanceTracking,
  CanvasTelemetryEvent,
  type TelemetryConfig,
  type TelemetryEventData,
  type TelemetryPerformanceMetric,
} from './telemetry';

// A/B Testing Framework (Epic 10: Story 10.4)
export {
  ABTestManager,
  getABTestManager,
  useABTest,
  useFeatureFlag,
  withABTest,
  CANVAS_AB_TESTS,
  type ABTest,
  type ABVariant,
  type ABTestConfig,
  type ABTestResult,
} from './telemetry';

// Canvas Chrome (consolidated from @yappc/canvas)
export * from './chrome';

// IDE Components (migrating from @ghatana/ide - consolidation in progress)
// These exports provide backward compatibility during the library consolidation
// @deprecated Use canvas-specific components instead
export {
  // IDE Shell Components
  IDEShell,
  ProfessionalIDELayout,
  type IDEShellProps,
  type ProfessionalIDELayoutProps,
} from './components/IDEShell';

export {
  // Editor Components
  EditorPanel,
  CodeEditor,
  type EditorPanelProps,
  type CodeEditorProps,
} from './components/EditorPanel';

export {
  // File Explorer
  FileExplorer,
  FileTree,
  type FileExplorerProps,
  type FileTreeProps,
} from './components/FileExplorer';

export {
  // UI Components
  ContextMenu,
  TabBar,
  type ContextMenuProps,
  type TabBarProps,
} from './components/IDEUI';

export {
  // Search and Operations
  AdvancedSearchPanel,
  BulkOperationsToolbar,
  type AdvancedSearchPanelProps,
  type BulkOperationsToolbarProps,
} from './components/IDEOperations';

export {
  // Collaboration
  CursorOverlay,
  RealTimeCursorTracking,
  type CursorOverlayProps,
  type RealTimeCursorTrackingProps,
} from './collaboration/CursorTracking';

export {
  // Utilities
  KeyboardShortcutsManager,
  LoadingStates,
  type KeyboardShortcutsManagerProps,
} from './components/IDEUtils';

export {
  // Code Features
  CodeGeneration,
  CodeCompletion,
  type CodeGenerationProps,
  type CodeCompletionProps,
} from './ai/IDECodeFeatures';

// REMOVED: deprecated @ghatana/yappc-charts
// REMOVED: deprecated @ghatana/yappc-charts
// // // Charts (consolidated from @ghatana/yappc-charts)
export * from '../../charts/src';
