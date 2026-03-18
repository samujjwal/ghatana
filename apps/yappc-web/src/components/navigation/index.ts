/**
 * Navigation Components Barrel Export
 *
 * @doc.type module
 * @doc.purpose Navigation components export
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

// Breadcrumbs component
export { Breadcrumbs } from './Breadcrumbs';
export type { BreadcrumbsProps } from './Breadcrumbs';

// Enhanced navigation components
export { EnhancedBreadcrumb } from './EnhancedBreadcrumb';
export type {
  EnhancedBreadcrumbProps,
  BreadcrumbSegment,
  BreadcrumbDropdownConfig,
} from './EnhancedBreadcrumb';

export { UnifiedHeaderBar } from './UnifiedHeaderBar';
export { NewButton, QuickActionsPanel } from './QuickActionsPanel';

// Phase rail
export { UnifiedPhaseRail } from './UnifiedPhaseRail';
export type { UnifiedPhaseRailProps } from './UnifiedPhaseRail';

// Unified navigation system
export { NavigationBreadcrumb } from './NavigationBreadcrumb';
export type {
  NavigationBreadcrumbProps,
  CanvasMode,
  WorkspaceInfo,
  ProjectInfo,
} from './NavigationBreadcrumb';

export {
  ActionsToolbar,
  canvasActions,
  projectActions,
} from './ActionsToolbar';
export type {
  ActionsToolbarProps,
  Action,
  ActionContext,
} from './ActionsToolbar';

export { UnifiedContextHeader } from './UnifiedContextHeader';
export type {
  UnifiedContextHeaderProps,
  UserInfo,
  PhaseInfo,
} from './UnifiedContextHeader';
