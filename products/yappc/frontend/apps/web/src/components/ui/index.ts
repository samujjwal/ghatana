// Re-export platform UI components from @ghatana/ui
export * from '@ghatana/ui';

// Re-export YAPPC-specific components from @ghatana/yappc-ui
// (These have no platform equivalent and are product-specific)
export {
  // YAPPC theme
  ThemeProvider,
  lightTheme,
  darkTheme,
  // YAPPC-specific hooks
  usePersonas,
  useKeyboardShortcuts,
  // YAPPC-specific types/utilities
  resolveMuiColor,
  getPaletteMain,
} from '@ghatana/yappc-ui';

// REMOVED: deprecated @ghatana/yappc-charts
// REMOVED: deprecated @ghatana/yappc-charts
// // export * from '@ghatana/yappc-charts';

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
