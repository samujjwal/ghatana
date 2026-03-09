/**
 * SearchBar Component Types
 *
 * @module DevSecOps/SearchBar/types
 */

/**
 * Props for the SearchBar component
 */
export interface SearchBarProps {
  /** Current search value */
  value?: string;

  /** Callback when search value changes */
  onChange?: (value: string) => void;

  /** Placeholder text */
  placeholder?: string;

  /** Show search results count */
  resultsCount?: number;

  /** Whether to show recent searches */
  showRecent?: boolean;

  /** Recent search terms */
  recentSearches?: string[];

  /** Callback when recent search is clicked */
  onRecentSearchClick?: (search: string) => void;

  /** Callback when search is cleared */
  onClear?: () => void;

  /** Debounce delay in milliseconds */
  debounceMs?: number;

  /** Loading state */
  loading?: boolean;
}
