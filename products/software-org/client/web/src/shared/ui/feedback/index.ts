/**
 * Shared Feedback Components
 *
 * Consolidated notification, page state, and error boundary components
 * for consistent user feedback across the application.
 */

export { NotificationCenter } from './NotificationCenter';
export type {
  NotificationCenterProps,
  NotificationMetrics,
  Notification,
  AlertNotification,
  CollaborationActivity,
  ApprovalRequest,
} from './NotificationCenter';

export { LoadingState, ErrorState, EmptyState } from './PageState';
export type {
  LoadingStateProps,
  ErrorStateProps,
  EmptyStateProps,
} from './PageState';

export { ErrorBoundary, useErrorHandler } from './ErrorBoundary';
export type { Props as ErrorBoundaryProps } from './ErrorBoundary';
