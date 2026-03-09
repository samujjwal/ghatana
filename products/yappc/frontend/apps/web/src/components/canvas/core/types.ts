/**
 * Base interfaces for all canvas items and configurations
 * This provides the foundation for generic, composable canvas components
 */

// Core item interface that all canvas items must extend
/**
 *
 */
export interface BaseItem {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  metadata?: {
    createdAt: string;
    updatedAt: string;
    tags?: string[];
    version?: string;
  };
}

// Generic canvas configuration
/**
 *
 */
export interface CanvasCapabilities {
  dragDrop?: boolean;
  selection?: boolean;
  keyboard?: boolean;
  zoom?: boolean;
  persistence?: boolean;
  collaboration?: boolean;
  undo?: boolean;
  export?: boolean;
}

// View mode configuration for different canvas visualizations
/**
 *
 */
export interface ViewModeDefinition<TItem = unknown> {
  id: string;
  label: string;
  icon?: React.ReactNode;
  component: React.ComponentType<ViewModeProps<TItem>>;
  capabilities?: Partial<CanvasCapabilities>;
}

// Props for view mode components
/**
 *
 */
export interface ViewModeProps<TItem = unknown> {
  items: TItem[];
  selectedItems: string[];
  onItemSelect: (itemId: string, multi?: boolean) => void;
  onItemUpdate: (itemId: string, updates: Partial<TItem>) => void;
  onItemDelete: (itemId: string) => void;
  onItemCreate?: (item: Omit<TItem, 'id'>) => void;
  readonly?: boolean;
}

// Item renderer props
/**
 *
 */
export interface ItemRendererProps<TItem = unknown> {
  item: TItem;
  selected: boolean;
  onSelect: (itemId: string, multi?: boolean) => void;
  onUpdate: (updates: Partial<TItem>) => void;
  onDelete: () => void;
  readonly?: boolean;
  style?: React.CSSProperties;
}

// Toolbar renderer props
/**
 *
 */
export interface ToolbarRendererProps<TItem = unknown> {
  selectedItems: string[];
  viewMode: string;
  onViewModeChange: (mode: string) => void;
  // Some toolbar implementations create a new item without passing the
  // created payload, while others accept an item payload. Allow both
  // signatures for compatibility: either a creator function that takes an
  // item, or a simple no-arg trigger.
  onItemCreate?: ((item: Omit<TItem, 'id'>) => void) | (() => void);
  onBulkAction?: (action: string, itemIds: string[]) => void;
  customActions?: ToolbarAction<TItem>[];
}

// Sidebar renderer props
/**
 *
 */
export interface SidebarRendererProps<TItem = unknown> {
  selectedItem?: TItem;
  onItemUpdate: (itemId: string, updates: Partial<TItem>) => void;
  onClose: () => void;
}

// Toolbar action definition
/**
 *
 */
export interface ToolbarAction<TItem = unknown> {
  id: string;
  label: string;
  icon?: React.ReactNode;
  onClick: (context: CanvasActionContext<TItem>) => void;
  disabled?: (context: CanvasActionContext<TItem>) => boolean;
  visible?: (context: CanvasActionContext<TItem>) => boolean;
}

// Context provided to toolbar actions
/**
 *
 */
export interface CanvasActionContext<TItem = unknown> {
  items: TItem[];
  selectedItems: string[];
  viewMode: string;
  canvasAPI: CanvasAPI<TItem>;
}

// Canvas API for programmatic control
/**
 *
 */
export interface CanvasAPI<TItem = unknown> {
  // Item management
  getItems: () => TItem[];
  getItem: (id: string) => TItem | undefined;
  createItem: (item: Omit<TItem, 'id'>) => void;
  updateItem: (id: string, updates: Partial<TItem>) => void;
  deleteItem: (id: string) => void;

  // Selection
  getSelectedItems: () => string[];
  selectItem: (id: string, multi?: boolean) => void;
  clearSelection: () => void;

  // View management
  getViewMode: () => string;
  setViewMode: (mode: string) => void;

  // State management
  exportState: () => CanvasState<TItem>;
  importState: (state: CanvasState<TItem>) => void;

  // Event subscription
  subscribe: (
    event: CanvasEvent,
    handler: CanvasEventHandler<TItem>
  ) => () => void;
}

// Canvas state structure
/**
 *
 */
export interface CanvasState<TItem = unknown> {
  items: TItem[];
  selectedItems: string[];
  viewMode: string;
  viewport?: {
    x: number;
    y: number;
    zoom: number;
  };
  metadata: {
    version: string;
    createdAt: string;
    updatedAt: string;
  };
}

// Canvas events for subscription
/**
 *
 */
export type CanvasEvent =
  | 'item-created'
  | 'item-updated'
  | 'item-deleted'
  | 'selection-changed'
  | 'view-mode-changed'
  | 'state-loaded'
  | 'state-saved'
  | 'template-loaded';

/**
 *
 */
export type CanvasEventHandler<TItem = unknown> = (
  payload: unknown,
  context: CanvasActionContext<TItem>
) => void;

// Template system
/**
 *
 */
export interface TemplateDefinition<TItem = unknown> {
  id: string;
  name: string;
  description: string;
  category: string;
  thumbnail?: string;
  items: TItem[];
  metadata?: {
    author?: string;
    version?: string;
    tags?: string[];
  };
}

// Filter and sort definitions
/**
 *
 */
export interface FilterDefinition<TItem = unknown> {
  id: string;
  label: string;
  type: 'text' | 'select' | 'date' | 'boolean';
  options?: { value: string; label: string }[];
  predicate: (item: TItem, value: unknown) => boolean;
}

/**
 *
 */
export interface SortDefinition<TItem = unknown> {
  id: string;
  label: string;
  compareFn: (a: TItem, b: TItem) => number;
}

// Plugin system interfaces
/**
 *
 */
export interface CanvasPlugin<TItem = unknown> {
  id: string;
  name: string;
  version: string;

  // Lifecycle hooks
  onInit?: (api: CanvasAPI<TItem>) => void;
  onDestroy?: () => void;

  // UI contributions
  toolbarActions?: ToolbarAction<TItem>[];
  contextMenuItems?: ContextMenuItem<TItem>[];
  panels?: PanelDefinition<TItem>[];

  // Item extensions
  itemDecorators?: ItemDecorator<TItem>[];

  // View mode contributions
  viewModes?: ViewModeDefinition<TItem>[];
}

/**
 *
 */
export interface ContextMenuItem<TItem = unknown> {
  id: string;
  label: string;
  icon?: React.ReactNode;
  onClick: (item: TItem, context: CanvasActionContext<TItem>) => void;
  visible?: (item: TItem) => boolean;
  separator?: boolean;
}

/**
 *
 */
export interface PanelDefinition<TItem = unknown> {
  id: string;
  title: string;
  icon?: React.ReactNode;
  component: React.ComponentType<PanelProps<TItem>>;
  position: 'left' | 'right' | 'bottom';
  defaultOpen?: boolean;
}

/**
 *
 */
export interface PanelProps<TItem = unknown> {
  items: TItem[];
  selectedItems: string[];
  canvasAPI: CanvasAPI<TItem>;
}

/**
 *
 */
export interface ItemDecorator<TItem = unknown> {
  id: string;
  priority: number;
  render: (
    item: TItem,
    context: ItemDecoratorContext<TItem>
  ) => React.ReactNode;
  shouldRender?: (item: TItem) => boolean;
}

/**
 *
 */
export interface ItemDecoratorContext<TItem = unknown> {
  item: TItem;
  selected: boolean;
  canvasAPI: CanvasAPI<TItem>;
}
