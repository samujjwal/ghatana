/**
 * ViewModeSwitcher Component Types
 *
 * @module DevSecOps/ViewModeSwitcher/types
 */

import type { ViewMode } from '@ghatana/yappc-types/devsecops';

/**
 * Props for the ViewModeSwitcher component
 */
export interface ViewModeSwitcherProps {
  /** Current active view mode */
  value: ViewMode;

  /** Callback when view mode changes */
  onChange: (mode: ViewMode) => void;

  /** Available view modes to display */
  modes?: ViewMode[];

  /** Display mode - buttons with text or icon-only */
  variant?: 'full' | 'compact';

  /** Size of the switcher */
  size?: 'small' | 'medium' | 'large';

  /** Whether the switcher is disabled */
  disabled?: boolean;

  /** Orientation of the switcher */
  orientation?: 'horizontal' | 'vertical';

  /** Custom labels for view modes */
  labels?: Partial<Record<ViewMode, string>>;

  /** Additional CSS class name */
  className?: string;
}

/**
 * View mode metadata for rendering
 */
export interface ViewModeMetadata {
  id: ViewMode;
  label: string;
  icon: React.ComponentType;
  description: string;
}
