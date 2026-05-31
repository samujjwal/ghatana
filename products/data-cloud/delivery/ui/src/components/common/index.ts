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

export {
  DegradedState,
  EmptyState,
  ErrorState,
  LoadingState,
  NotFoundState,
  PreviewState,
  UnavailableState,
} from "./AsyncStates";
export { GlobalSearch, useGlobalSearch } from "./GlobalSearch";
export { LabeledInput, LabeledSelect } from "./LabeledInput";
export { SearchFilterBar } from "./SearchFilterBar";

// DS-004: Re-export legacy local duplicates pending migration to @ghatana/ui
export { AIAssistSuggestion } from "./AIAssistSuggestion";
export type { AIAssistSuggestionProps } from "./AIAssistSuggestion";
export { AppErrorBoundary } from "./AppErrorBoundary";
export { BackendAuthErrorPanel } from "./BackendAuthErrorPanel";
export type {
  AuthDenialCode,
  BackendAuthErrorPanelProps,
} from "./BackendAuthErrorPanel";
export { Button } from "./Button";
export { Container } from "./Container";
export { GuardedAction } from "./GuardedAction";
export type {
  GuardedActionProps,
  GuardedActionTriggerProps,
} from "./GuardedAction";
export { KeyboardShortcuts } from "./KeyboardShortcuts";
export { OperationTimeline } from "./OperationTimeline";
export type {
  OperationOutcome,
  OperationRecord,
  OperationTimelineProps,
} from "./OperationTimeline";
export { QueryStateBoundary } from "./QueryStateBoundary";
export { ResourceDetailShell } from "./ResourceDetailShell";
export type {
  ResourceDetailMetaItem,
  ResourceDetailShellProps,
} from "./ResourceDetailShell";
export { RolePermissionNotice } from "./RolePermissionNotice";
export type { RolePermissionNoticeProps } from "./RolePermissionNotice";
export { StatusBadge } from "./StatusBadge";
export { TabWorkspace } from "./TabWorkspace";
export { Timeline } from "./Timeline";
export { ToastProvider } from "./Toast";
export { TrustSignalGroup } from "./TrustSignalGroup";
export type {
  TrustSignalDescriptor,
  TrustSignalGroupProps,
} from "./TrustSignalGroup";
