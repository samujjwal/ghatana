/**
 * @ghatana/yappc-ide - Components Exports
 */

export * from './FileExplorer';
export * from './TabBar';
export * from './EditorPanel';
export * from './IDEShell';
export * from './StatusBar';
export * from './PresenceIndicators';
export * from './SearchBar';
export * from './ContextMenu';
export * from './CommandPalette';
export * from './CursorOverlay';
export * from './ConflictResolver';
export * from './CollaborationSettings';
export * from './CollaborationStatusBar';
export * from './CollaborationHistory';
export * from './BulkOperationsToolbar';
export * from './AdvancedSearchPanel';
export * from './RealTimeCursorTracking';
export * from './EnhancedConflictResolver';
export * from './KeyboardShortcutsManager';
export * from './ProfessionalIDELayout';
export * from './PerformanceOptimizedVirtualScroll';
export * from './MemoryManager';
export * from './AccessibilityManager';
export * from './KeyboardNavigation';

/**
 * @ghatana/yappc-ide - Component Exports
 * 
 * Centralized exports for all IDE components
 * 
 * @doc.type module
 * @doc.purpose Component exports for IDE
 * @doc.layer product
 * @doc.pattern Module Index
 */

// Collaboration Components
export { CursorOverlay, useEditorCursorOverlay } from './CursorOverlay';
export { ConflictResolver } from './ConflictResolver';
export { CollaborationSettings } from './CollaborationSettings';
export { CollaborationStatusBar } from './CollaborationStatusBar';
export { CollaborationHistory } from './CollaborationHistory';

// UX Enhancement Components
export {
  InteractiveButton,
  AnimatedCheckbox,
  ToggleSwitch,
  AnimatedInput,
  HoverCard,
  Skeleton,
  ProgressBar,
} from './MicroInteractions';

// Toast Notifications
export {
  ToastProvider,
  useToast,
  useToastNotifications,
  ToastContainer,
} from './Toast';
export type { Toast, ToastType } from './Toast';

// Loading States
export {
  Spinner,
  DotsLoader,
  PulseLoader,
  SkeletonText,
  SkeletonCard,
  SkeletonTable,
  SkeletonFileTree,
  LoadingOverlay,
  ProgressLoader,
  StaggeredLoader,
  IDELoadingStates,
} from './LoadingStates';
