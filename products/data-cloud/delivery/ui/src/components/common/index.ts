/**
 * Common Components Index
 *
 * DS-004 Convergence Plan:
 * - Canonical local (keep here): AsyncStates, GlobalSearch, LabeledInput, LabeledSelect, SearchFilterBar,
 *   RouteErrorBoundary, UnsupportedSurfaceBoundary
 * - Migrate to @ghatana/ui: AppErrorBoundary, Button, Container, EmptyState, KeyboardShortcuts,
 *   LoadingState, StatusBadge, TabWorkspace, Timeline, Toast
 * - Import from @ghatana/ui directly: BaseCard, KPICard (in cards/)
 */

export { GlobalSearch, useGlobalSearch } from "./GlobalSearch";
export { LabeledInput, LabeledSelect } from "./LabeledInput";
export { SearchFilterBar } from "./SearchFilterBar";
export {
  LoadingState,
  EmptyState,
  ErrorState,
  UnavailableState,
  PreviewState,
  NotFoundState,
  DegradedState,
} from "./AsyncStates";

// DS-004: Re-export legacy local duplicates pending migration to @ghatana/ui
export { AppErrorBoundary } from "./AppErrorBoundary";
export { Button } from "./Button";
export { Container } from "./Container";
export { KeyboardShortcuts } from "./KeyboardShortcuts";
export { StatusBadge } from "./StatusBadge";
export { TabWorkspace } from "./TabWorkspace";
export { Timeline } from "./Timeline";
export { ToastProvider } from "./Toast";
export { QueryStateBoundary } from "./QueryStateBoundary";
export { OperationTimeline } from "./OperationTimeline";
export type { OperationRecord, OperationOutcome, OperationTimelineProps } from "./OperationTimeline";
export { TrustSignalGroup } from "./TrustSignalGroup";
export type { TrustSignalDescriptor, TrustSignalGroupProps } from "./TrustSignalGroup";
export { ResourceDetailShell } from "./ResourceDetailShell";
export type { ResourceDetailShellProps, ResourceDetailMetaItem } from "./ResourceDetailShell";
export { GuardedAction } from "./GuardedAction";
export type { GuardedActionProps, GuardedActionTriggerProps } from "./GuardedAction";
export { AIAssistSuggestion } from "./AIAssistSuggestion";
export type { AIAssistSuggestionProps } from "./AIAssistSuggestion";
export { RolePermissionNotice } from "./RolePermissionNotice";
export type { RolePermissionNoticeProps } from "./RolePermissionNotice";
export { BackendAuthErrorPanel } from "./BackendAuthErrorPanel";
export type { BackendAuthErrorPanelProps, AuthDenialCode } from "./BackendAuthErrorPanel";
