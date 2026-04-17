// Re-export platform UI components from @ghatana/ui
export * from '@ghatana/design-system';

// Re-export YAPPC-specific components from @ghatana/yappc-ui
// (These have no platform equivalent and are product-specific)
export {
  // YAPPC theme
  ThemeProvider,
  lightTheme,
  darkTheme,
  // YAPPC-specific types/utilities
  resolveMuiColor,
  getPaletteMain,
} from '@yappc/ui';
export { usePersonas } from '@yappc/state/config-hooks';
export {
  CommandPalette,
  ShortcutHelper,
  useKeyboardShortcuts,
  type Command,
} from '@yappc/ui/shortcuts';
export {
  Breadcrumb,
  TabNavigation,
  StageNavigation,
  type TabNavigationItem,
  type StageNavigationProps,
} from '@yappc/ui/navigation-ui';

/**
 * UI Components Index
 * 
 * Centralized exports for all UI components.
 * Provides a single import point for consistent component usage.
 * 
 * @doc.type module
 * @doc.purpose UI components public API
 * @doc.layer ui
 */

// Form Components
export { Button } from './Button';
export { Input } from './Input';
export { Textarea } from './Textarea';
export { Select } from './Select';

// Layout Components
export { Card } from './Card';
export { StatsCard, FeatureCard, ActionCard, MediaCard } from './CardVariants';

// Overlay Components
export { Modal } from './Modal';
export { ConfirmationDialog, AlertDialog, FormDialog } from './Dialog';

// Feedback Components
export {
    Spinner,
    Skeleton,
    LoadingCard,
    LoadingTable,
    LoadingState,
    Progress
} from './Loading';

// Legacy Components (for backward compatibility)
export { StatusCard } from './StatusCard';
export { LoadingFallback } from './LoadingFallback';
export { ProgressBar } from './ProgressBar';
