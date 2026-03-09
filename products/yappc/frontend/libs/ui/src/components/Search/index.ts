/**
 * Search Components Library
 *
 * Advanced search and filtering components with WebSocket integration
 * for real-time data updates and synchronized filter states.
 */

export { FilterPanel } from './FilterPanel';
export type {
  FilterCriteria,
  FilterOption,
  FilterPanelProps,
} from './FilterPanel';

export { SearchInput } from './SearchInput';
export type {
  SearchSuggestion,
  SearchOperator,
  SearchInputProps,
} from './SearchInput';

export { SearchProvider, SearchInterface, useSearch } from './SearchProvider';
export type {
  SearchResult,
  SearchContextValue,
  SearchProviderProps,
  SearchInterfaceProps,
} from './SearchProvider';

export { SearchResults } from './SearchResults';
export type { SearchResultsProps } from './SearchResults';
