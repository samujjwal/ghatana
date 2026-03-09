/**
 * Common Components
 * 
 * @doc.type module
 * @doc.purpose Shared UI components
 * @doc.layer product
 */

export {
  Skeleton,
  TextSkeleton,
  AvatarSkeleton,
  CardSkeleton,
  ProjectCardSkeleton,
  ProjectListSkeleton,
  CanvasSkeleton,
  DashboardSkeleton,
  TableSkeleton,
  SidebarSkeleton,
  TaskBoardSkeleton,
} from './SkeletonLoaders';

export { ToastProvider, useToast, type ToastData as Toast, type ToastSeverity as ToastType } from '@ghatana/ui';

export {
  EmptyState,
  NoProjectsEmptyState,
  NoTasksEmptyState,
  NoSearchResultsEmptyState,
  NoNotificationsEmptyState,
  NoCanvasElementsEmptyState,
  ErrorEmptyState,
} from './EmptyState';
