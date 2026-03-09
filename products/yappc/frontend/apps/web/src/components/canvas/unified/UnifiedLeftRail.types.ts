/**
 * Unified Left Rail Type Definitions
 *
 * Comprehensive type system for the extensible, context-aware left rail.
 * Supports multi-dimensional filtering (Mode × Role × Phase) and plugin architecture.
 *
 * @doc.type types
 * @doc.purpose Type definitions for canvas left rail system
 * @doc.layer components
 * @doc.pattern Configuration-Driven UI
 */

import { ReactNode, ComponentType } from 'react';
import { CanvasMode } from '../../../types/canvasMode';
import { LifecyclePhase } from '../../../types/lifecycle';

// ============================================================================
// Core Panel System
// ============================================================================

/**
 * Unique identifier for rail tabs/panels
 */
export type RailTabId =
  | 'assets' // Shape library, icons, templates
  | 'layers' // Canvas layer management
  | 'components' // Reusable component library
  | 'infrastructure' // Cloud resources (Architect/DevOps)
  | 'history' // Undo/redo visualization
  | 'files' // File explorer (Code mode)
  | 'data' // Data sources & APIs
  | 'ai' // AI suggestions & patterns
  | 'favorites' // User's saved items
  | 'team'; // Collaborative features

/**
 * Context dimensions for content filtering
 */
export interface RailContext {
  /** Current canvas mode */
  mode: CanvasMode;
  /** Current user role (from header) */
  role?: string;
  /** Current lifecycle phase */
  phase?: LifecyclePhase;
  /** Selected node types (for contextual filtering) */
  selectedTypes?: string[];
  /** Project metadata */
  projectType?: 'web' | 'mobile' | 'api' | 'infrastructure';
}

/**
 * Visibility rule for conditional panel display
 */
export interface VisibilityRule {
  /** Required modes (OR logic) */
  modes?: CanvasMode[];
  /** Required roles (OR logic) */
  roles?: string[];
  /** Required phases (OR logic) */
  phases?: LifecyclePhase[];
  /** Custom predicate for complex logic */
  condition?: (context: RailContext) => boolean;
}

/**
 * Panel lifecycle hooks for advanced behaviors
 */
export interface PanelLifecycle {
  /** Called when panel becomes active */
  onActivate?: (context: RailContext) => void;
  /** Called when panel becomes inactive */
  onDeactivate?: () => void;
  /** Called when context changes while active */
  onContextChange?: (context: RailContext) => void;
  /** Cleanup on unmount */
  onUnmount?: () => void;
}

/**
 * Complete panel definition
 */
export interface RailPanelDefinition {
  /** Unique identifier */
  id: RailTabId;
  /** Display label */
  label: string;
  /** Icon component or string */
  icon: ReactNode;
  /** Panel category for grouping */
  category?: 'content' | 'structure' | 'technical' | 'utility';
  /** Tooltip description */
  description?: string;
  /** Panel component */
  component: ComponentType<RailPanelProps>;
  /** Visibility rules */
  visibility?: VisibilityRule;
  /** Lifecycle hooks */
  lifecycle?: PanelLifecycle;
  /** Sort order (lower = left) */
  order?: number;
  /** Keyboard shortcut */
  shortcut?: string;
  /** Badge content (e.g., item count) */
  badge?: (context: RailContext) => string | number | null;
}

/**
 * Props passed to all panel components
 */
export interface RailPanelProps {
  /** Current context */
  context: RailContext;
  /** Canvas nodes (for layer panel) */
  nodes?: unknown[];
  /** Selected node IDs */
  selectedNodeIds?: string[];
  /** Canvas interaction handlers */
  onInsertNode?: (nodeData: unknown, position?: { x: number; y: number }) => void;
  onSelectNode?: (nodeId: string) => void;
  onUpdateNode?: (nodeId: string, updates: unknown) => void;
  onDeleteNode?: (nodeId: string) => void;
  onToggleVisibility?: (nodeId: string) => void;
  onToggleLock?: (nodeId: string) => void;
}

// ============================================================================
// Asset System
// ============================================================================

/**
 * Asset categories with hierarchical structure
 */
export type AssetCategory =
  | 'basic' // Rectangle, Circle, Line
  | 'flowchart' // Decision, Process, Data
  | 'uml' // Class, Sequence, State
  | 'wireframe' // Button, Input, Card
  | 'cloud-aws' // EC2, S3, Lambda
  | 'cloud-azure' // VM, Blob, Function
  | 'cloud-gcp' // Compute, Storage, Cloud Run
  | 'icons' // General purpose icons
  | 'stickers' // Brainstorm annotations
  | 'charts' // Graph, Pie, Bar
  | 'mindmap' // Thought bubbles
  | 'code' // Code blocks
  | 'data'; // Database, API shapes

/**
 * Individual asset/template definition
 */
export interface AssetTemplate {
  id: string;
  name: string;
  icon: string;
  type: string;
  category: AssetCategory;
  defaultSize: { width: number; height: number };
  defaultData: Record<string, unknown>;
  /** Tags for search */
  tags?: string[];
  /** Visibility rules */
  visibility?: VisibilityRule;
  /** Preview image URL */
  preview?: string;
  /** Usage count (for popularity sorting) */
  usageCount?: number;
  /** AI-generated suggestion score */
  aiScore?: number;
}

/**
 * Asset category metadata
 */
export interface AssetCategoryMeta {
  id: AssetCategory;
  label: string;
  icon: ReactNode;
  description?: string;
  visibility?: VisibilityRule;
  defaultExpanded?: boolean;
}

// ============================================================================
// Layer System
// ============================================================================

/**
 * Layer representation in the tree
 */
export interface LayerNode {
  id: string;
  name: string;
  type: string;
  visible: boolean;
  locked: boolean;
  children?: LayerNode[];
  /** Original canvas node data */
  nodeData?: unknown;
}

/**
 * Layer panel state
 */
export interface LayerPanelState {
  expandedGroups: Set<string>;
  selectedLayers: Set<string>;
  searchQuery: string;
}

// ============================================================================
// Configuration Registry
// ============================================================================

/**
 * Global registry of all available panels
 */
export interface RailPanelRegistry {
  /** Get panel definition by ID */
  get(id: RailTabId): RailPanelDefinition | undefined;
  /** Register a new panel (for plugins) */
  register(panel: RailPanelDefinition): void;
  /** Unregister a panel */
  unregister(id: RailTabId): void;
  /** Get all panels matching visibility rules */
  getVisiblePanels(context: RailContext): RailPanelDefinition[];
  /** Get panels for specific mode */
  getPanelsForMode(mode: CanvasMode): RailPanelDefinition[];
}

/**
 * Mode-specific configuration
 */
export interface ModeRailConfig {
  /** Panel IDs to show (in order) */
  panels: RailTabId[];
  /** Default active panel */
  defaultPanel?: RailTabId;
  /** Asset categories to prioritize */
  featuredCategories?: AssetCategory[];
  /** Custom panel settings */
  panelSettings?: Record<RailTabId, unknown>;
}

// ============================================================================
// Plugin System
// ============================================================================

/**
 * Plugin descriptor for extending the rail
 */
export interface RailPlugin {
  id: string;
  name: string;
  version: string;
  /** Panels to register */
  panels?: RailPanelDefinition[];
  /** Asset templates to add */
  assets?: AssetTemplate[];
  /** Initialization hook */
  initialize?: (registry: RailPanelRegistry) => void;
  /** Cleanup hook */
  cleanup?: () => void;
}

// ============================================================================
// Search & Favorites
// ============================================================================

/**
 * Search result item
 */
export interface SearchResult {
  type: 'asset' | 'layer' | 'component' | 'action';
  id: string;
  name: string;
  description?: string;
  icon?: ReactNode;
  category?: string;
  relevance: number; // 0-1 score
  onSelect: () => void;
}

/**
 * Favorites storage
 */
export interface FavoritesState {
  assets: Set<string>;
  components: Set<string>;
  recentlyUsed: Array<{ id: string; timestamp: number }>;
}

// ============================================================================
// Analytics & Telemetry
// ============================================================================

/**
 * Usage analytics event
 */
export interface RailAnalyticsEvent {
  type: 'panel_open' | 'asset_insert' | 'search' | 'favorite_add';
  panelId?: RailTabId;
  assetId?: string;
  searchQuery?: string;
  context: RailContext;
  timestamp: number;
}
