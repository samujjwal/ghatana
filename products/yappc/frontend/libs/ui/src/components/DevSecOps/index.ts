/**
 * DevSecOps Components - Public Exports
 *
 * Collection of components for the DevSecOps Canvas feature.
 *
 * @module DevSecOps
 */

// Navigation Components
export { TopNav } from './TopNav';
export type {
  TopNavProps,
  TopNavUser,
  UserRole,
  NavigationPage,
} from './TopNav';

export { Breadcrumbs } from './Breadcrumbs';
export type { BreadcrumbsProps, BreadcrumbItem } from './Breadcrumbs';

export { Breadcrumb } from './Breadcrumb';
export type { BreadcrumbProps, BreadcrumbItem as BreadcrumbItemNew } from './Breadcrumb';

export { PhaseNav } from './PhaseNav';
export type { PhaseNavProps, Phase, PhaseKey } from './PhaseNav';

// Card Components
export { KPICard } from './KPICard';
export type { KPICardProps, KPITrend, TrendDirection } from './KPICard';

export { ItemCard } from './ItemCard';
export type { ItemCardProps, Item, Priority, ItemStatus } from './ItemCard';

// Layout Components
export { SidePanel } from './SidePanel';
export type { SidePanelProps } from './SidePanel';

// Enhanced View Components
export { KanbanBoard } from './KanbanBoard';
export type { KanbanBoardProps, KanbanColumn } from './KanbanBoard';

export { SearchBar } from './SearchBar';
export type { SearchBarProps } from './SearchBar';

export { FilterPanel } from './FilterPanel';
export type { FilterPanelProps, FilterSection } from './FilterPanel';

export { ViewModeSwitcher } from './ViewModeSwitcher';
export type { ViewModeSwitcherProps, ViewModeMetadata } from './ViewModeSwitcher';

export { Timeline } from './Timeline';
export type {
  TimelineProps,
  TimelineViewMode,
  TimelineScale,
  TimelineGrid,
  TimelineTick,
  TimelineItemPosition,
  TimelineMilestonePosition,
} from './Timeline';

export { DataTable, DataTableUtils, DataTableExport, ColumnVisibility, ExportToolbar } from './DataTable';
export type {
  DataTableProps,
  DataTableColumn,
  SortConfig,
  SortDirection,
  FilterConfig,
  FilterValue,
  PaginationConfig,
  ColumnVisibilityProps,
  ExportToolbarProps,
} from './DataTable';

// Workflow Automation Components
export { AgentPanel } from './WorkflowAutomation/AgentPanel';
export type { AgentPanelProps } from './WorkflowAutomation/AgentPanel';
