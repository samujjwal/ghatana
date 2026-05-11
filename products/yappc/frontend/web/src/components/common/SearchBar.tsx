import React from 'react';
import { useNavigate } from 'react-router';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { cn } from '@/lib/utils';
import { Search, X } from 'lucide-react';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { useTranslation } from '@ghatana/i18n';

/**
 * Global search bar with autocomplete.
 *
 * **Features**:
 * - Real-time search across dashboards, incidents, vulnerabilities
 * - Autocomplete suggestions
 * - Keyboard navigation (↑↓ + Enter)
 * - Recent searches
 * - Entity type filtering
 * - Quick navigation
 *
 * **Usage**:
 * ```tsx
 * <SearchBar tenantId={tenantId} />
 * ```
 */
export function SearchBar({ tenantId }: { tenantId: string }) {
  const navigate = useNavigate();
  const { t } = useTranslation('common');
  const [query, setQuery] = React.useState('');
  const [isOpen, setIsOpen] = React.useState(false);
  const [results, setResults] = React.useState<SearchResult[]>([]);
  const [selectedIndex, setSelectedIndex] = React.useState(0);

  // Debounced search
  React.useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      const searchResults = await performSearch(tenantId, query);
      setResults(searchResults);
      setIsOpen(true);
    }, 300);

    return () => clearTimeout(timer);
  }, [query, tenantId]);

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => Math.min(prev + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => Math.max(prev - 1, 0));
    } else if (e.key === 'Enter' && results[selectedIndex]) {
      e.preventDefault();
      navigate(results[selectedIndex].link);
      setQuery('');
      setIsOpen(false);
    } else if (e.key === 'Escape') {
      setIsOpen(false);
    }
  };

  return (
    <div className="relative w-full max-w-xl">
      {/* Search input */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-fg-muted" />
        <Input
          type="text"
          className="w-full pl-10 pr-10 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          fullWidth
          size="sm"
          placeholder={t('search.bar.placeholder')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => query.length >= 2 && setIsOpen(true)}
        />
        {query && (
          <Button
            variant="ghost"
            size="sm"
            className="absolute right-3 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg-muted"
            onClick={() => {
              setQuery('');
              setResults([]);
              setIsOpen(false);
            }}
            aria-label={t('search.bar.clear')}
          >
            <X className="w-4 h-4" />
          </Button>
        )}
      </div>

      {/* Search results dropdown */}
      {isOpen && results.length > 0 && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => setIsOpen(false)}
          />

          {/* Results */}
          <div className="absolute top-full left-0 right-0 mt-2 bg-white rounded-lg shadow-xl border border-border z-50 max-h-96 overflow-y-auto">
            {results.map((result, index) => (
              <SearchResultItem
                key={result.id}
                result={result}
                isSelected={index === selectedIndex}
                onClick={() => {
                  navigate(result.link);
                  setQuery('');
                  setIsOpen(false);
                }}
                onMouseEnter={() => setSelectedIndex(index)}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/**
 * Individual search result item.
 */
function SearchResultItem({
  result,
  isSelected,
  onClick,
  onMouseEnter,
}: {
  result: SearchResult;
  isSelected: boolean;
  onClick: () => void;
  onMouseEnter: () => void;
}) {
  const typeConfig = getEntityTypeConfig(result.type);

  return (
    <div
      className={cn(
        'px-4 py-3 cursor-pointer transition-colors',
        isSelected ? 'bg-info-bg' : 'hover:bg-surface-muted'
      )}
      onClick={onClick}
      onMouseEnter={onMouseEnter}
    >
      <div className="flex items-start gap-3">
        {/* Icon */}
        <div className={cn('flex-shrink-0 mt-0.5', typeConfig.color)}>
          {typeConfig.icon}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={cn('px-2 py-0.5 text-xs font-medium rounded', typeConfig.badgeColor)}>
              {result.type}
            </span>
            <span className="text-sm font-medium text-fg truncate">
              {result.title}
            </span>
          </div>
          <p className="text-sm text-fg-muted line-clamp-1">
            {result.description}
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * Search result type.
 */
interface SearchResult {
  id: string;
  type: 'DASHBOARD' | 'INCIDENT' | 'VULNERABILITY' | 'PIPELINE' | 'RESOURCE';
  title: string;
  description: string;
  link: string;
}

/**
 * Get entity type configuration.
 */
function getEntityTypeConfig(type: string) {
  const configs = {
    DASHBOARD: {
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      ),
      color: 'text-info-color',
      badgeColor: 'bg-info-bg text-info-color',
    },
    INCIDENT: {
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      ),
      color: 'text-destructive',
      badgeColor: 'bg-destructive-bg text-destructive',
    },
    VULNERABILITY: {
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
        </svg>
      ),
      color: 'text-warning-color',
      badgeColor: 'bg-warning-bg text-warning-color',
    },
    PIPELINE: {
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 10l-2 1m0 0l-2-1m2 1v2.5M20 7l-2 1m2-1l-2-1m2 1v2.5M14 4l-2-1-2 1M4 7l2-1M4 7l2 1M4 7v2.5M12 21l-2-1m2 1l2-1m-2 1v-2.5M6 18l-2-1v-2.5M18 18l2-1v-2.5" />
        </svg>
      ),
      color: 'text-success-color',
      badgeColor: 'bg-success-bg text-success-color',
    },
    RESOURCE: {
      icon: (
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z" />
        </svg>
      ),
      color: 'text-info-color',
      badgeColor: 'bg-info-bg text-info-color',
    },
  };

  return configs[type as keyof typeof configs] || configs.DASHBOARD;
}

/**
 * Search API call.
 */
async function performSearch(
  tenantId: string,
  query: string
): Promise<SearchResult[]> {
  const params = new URLSearchParams({
    q: query,
    tenantId,
    limit: '10',
  });
  const res = await fetch(`/api/search?${params.toString()}`);
  if (!res.ok) throw new Error(await readErrorResponse(res, `Search failed: ${res.status}`));
  return parseJsonResponse<SearchResult[]>(res, 'global search');
}
