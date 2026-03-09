/**
 * @ghatana/yappc-ide - Hook Exports
 * 
 * Centralized exports for all IDE hooks
 * 
 * @doc.type module
 * @doc.purpose Hook exports for IDE
 * @doc.layer product
 * @doc.pattern Module Index
 */

// Core Hooks
export { useCollaborativeEditing } from './useCollaborativeEditing';

// UX Enhancement Hooks
export { useKeyboardNavigation, useRovingTabIndex } from './useKeyboardNavigation';
export { useResponsiveDesign, useContainerQuery } from './useResponsiveDesign';

// Re-export types
export type {
  KeyboardShortcut as NavigationShortcut,
  FocusOptions,
} from './useKeyboardNavigation';

export type {
  Breakpoint,
  DeviceType,
  Orientation,
} from './useResponsiveDesign';
