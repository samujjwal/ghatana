/**
 * FilterPanel Component Types
 *
 * @module DevSecOps/FilterPanel/types
 */

import type { ItemFilter } from '@ghatana/yappc-types/devsecops';

/**
 * Props for the FilterPanel component
 */
export interface FilterPanelProps {
  /** Current filter configuration */
  filters?: ItemFilter;

  /** Callback when filters change */
  onChange?: (filters: ItemFilter) => void;

  /** Available phases for filtering */
  phaseIds?: string[];

  /** Phase labels map */
  phaseLabels?: Record<string, string>;

  /** Available tags for filtering */
  availableTags?: string[];

  /** Available owner IDs */
  availableOwners?: Array<{ id: string; name: string }>;

  /** Whether the panel is open */
  open?: boolean;

  /** Callback when panel is closed */
  onClose?: () => void;

  /** Show as drawer (mobile) or inline panel */
  variant?: 'drawer' | 'inline';
}

/**
 * Filter section definition
 */
export interface FilterSection {
  id: string;
  label: string;
  expanded: boolean;
}

/**
 * Filter configuration (alias for ItemFilter for convenience)
 */
export type FilterConfig = ItemFilter;
