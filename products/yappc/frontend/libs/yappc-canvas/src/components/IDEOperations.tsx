/**
 * IDE Operations - AdvancedSearchPanel, BulkOperationsToolbar Bridge
 * 
 * @deprecated Use SearchPanel, OperationsToolbar from @ghatana/yappc-canvas
 * @see /docs/LIBRARY_CONSOLIDATION_PLAN.md
 */

import React, { useEffect, useState } from 'react';

// ============================================================================
// AdvancedSearchPanel
// ============================================================================

export interface AdvancedSearchPanelProps {
  /** Initial search query */
  query?: string;
  /** Search handler */
  onSearch?: (query: string, filters: SearchFilters) => void;
  /** Available filter options */
  filterOptions?: FilterOption[];
  /** Current filters */
  filters?: SearchFilters;
  /** Results display handler */
  onResultSelect?: (result: SearchResult) => void;
  /** Additional CSS classes */
  className?: string;
  /** Placeholder text */
  placeholder?: string;
}

export interface SearchFilters {
  fileTypes?: string[];
  locations?: string[];
  modifiedAfter?: Date;
  modifiedBefore?: Date;
  caseSensitive?: boolean;
  regex?: boolean;
}

export interface FilterOption {
  id: string;
  label: string;
  type: 'checkbox' | 'select' | 'date';
  options?: string[];
}

export interface SearchResult {
  id: string;
  path: string;
  line?: number;
  column?: number;
  preview: string;
  matches: number;
}

/**
 * AdvancedSearchPanel - Bridge to Canvas Search System
 */
export const AdvancedSearchPanel: React.FC<AdvancedSearchPanelProps> = ({
  query = '',
  onSearch,
  filterOptions = [],
  filters = {},
  onResultSelect,
  className,
  placeholder = 'Search...',
}) => {
  const [searchQuery, setSearchQuery] = useState(query);
  const [activeFilters, setActiveFilters] = useState<SearchFilters>(filters);
  const [results, setResults] = useState<SearchResult[]>([]);

  useEffect(() => {
    console.warn(
      '[MIGRATION] AdvancedSearchPanel from @ghatana/yappc-ide is deprecated. ' +
      'Use SearchPanel from @ghatana/yappc-canvas.'
    );
  }, []);

  const handleSearch = () => {
    onSearch?.(searchQuery, activeFilters);
    // Mock results for demo
    setResults([
      { id: '1', path: '/src/file1.ts', preview: 'example code', matches: 2 },
      { id: '2', path: '/src/file2.ts', preview: 'another example', matches: 1 },
    ]);
  };

  return (
    <div className={`advanced-search-panel ${className || ''}`}>
      <div className="search-input-container">
        <input
          type="text"
          className="search-input"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={placeholder}
          onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
        />
        <button className="search-button" onClick={handleSearch}>
          🔍
        </button>
      </div>
      
      {filterOptions.length > 0 && (
        <div className="filter-section">
          {filterOptions.map(option => (
            <div key={option.id} className="filter-option">
              <label>{option.label}</label>
              {option.type === 'checkbox' && (
                <input type="checkbox" />
              )}
              {option.type === 'select' && option.options && (
                <select>
                  {option.options.map(opt => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              )}
            </div>
          ))}
        </div>
      )}

      {results.length > 0 && (
        <div className="results-list">
          {results.map(result => (
            <div 
              key={result.id}
              className="search-result"
              onClick={() => onResultSelect?.(result)}
            >
              <div className="result-path">{result.path}</div>
              <div className="result-preview">{result.preview}</div>
              <div className="result-matches">{result.matches} matches</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// ============================================================================
// BulkOperationsToolbar
// ============================================================================

export interface BulkOperationsToolbarProps {
  /** Selected items count */
  selectionCount: number;
  /** Available operations */
  operations?: BulkOperation[];
  /** Operation handler */
  onOperation?: (operationId: string, selectedItems: string[]) => void;
  /** Selected item IDs */
  selectedItems?: string[];
  /** Additional CSS classes */
  className?: string;
}

export interface BulkOperation {
  id: string;
  label: string;
  icon?: string;
  disabled?: boolean;
  danger?: boolean;
}

/**
 * BulkOperationsToolbar - Bridge Component
 */
export const BulkOperationsToolbar: React.FC<BulkOperationsToolbarProps> = ({
  selectionCount,
  operations = [],
  onOperation,
  selectedItems = [],
  className,
}) => {
  useEffect(() => {
    console.warn(
      '[MIGRATION] BulkOperationsToolbar from @ghatana/yappc-ide is deprecated. ' +
      'Use OperationsToolbar from @ghatana/yappc-canvas.'
    );
  }, []);

  return (
    <div className={`bulk-operations-toolbar ${className || ''}`}>
      <span className="selection-count">{selectionCount} selected</span>
      
      {operations.map(op => (
        <button
          key={op.id}
          className={`operation-button ${op.danger ? 'danger' : ''} ${op.disabled ? 'disabled' : ''}`}
          onClick={() => onOperation?.(op.id, selectedItems)}
          disabled={op.disabled}
        >
          {op.icon && <span className="op-icon">{op.icon}</span>}
          {op.label}
        </button>
      ))}
    </div>
  );
};

// Re-export with Canvas prefix
export { AdvancedSearchPanel as SearchPanel };
export { BulkOperationsToolbar as OperationsToolbar };
