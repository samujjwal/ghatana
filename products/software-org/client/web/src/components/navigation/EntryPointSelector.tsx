/**
 * Entry Point Selector Component
 *
 * Header dropdown component showing all available entry points organized by
 * organization layer and persona. Provides root_user with access to all
 * entry points across the entire system.
 *
 * <p><b>Purpose</b><br>
 * Allows root_user and privileged users to quickly navigate to any entry
 * point in the system, organized by:
 * - Organization Layer (org, department, team, individual)
 * - Persona Type (owner, executive, manager, ic, admin)
 *
 * <p><b>Features</b><br>
 * - Shows all entry points for root_user
 * - Filters entry points based on user's permissions
 * - Grouped by organization layer with collapsible sections
 * - Search/filter within entry points
 * - Badge counts for pending items
 *
 * @doc.type component
 * @doc.purpose Entry point navigation for root_user and privileged users
 * @doc.layer product
 * @doc.pattern Dropdown Menu Component
 */

import { useState, useMemo, useCallback } from 'react';
import { Link, useLocation } from 'react-router';
import { usePersona } from '@/hooks/usePersona';
import {
  getEntryPointRegistry,
  getEntryPointsByCategory,
  ENTRY_POINT_CATEGORIES,
} from '@/config/entrypoints.config';
import type { EntryPoint, EntryPointCategory, EntryPointAccessContext } from '@/types/entrypoints';

// =============================================================================
// Types
// =============================================================================

interface EntryPointSelectorProps {
  /** Optional className for styling */
  className?: string;
}

interface CategorySectionProps {
  category: EntryPointCategory;
  entryPoints: EntryPoint[];
  isExpanded: boolean;
  onToggle: () => void;
  currentRoute: string;
  onSelect: () => void;
}

// =============================================================================
// Sub-Components
// =============================================================================

/**
 * Individual entry point item in the dropdown
 */
function EntryPointItem({
  entryPoint,
  isActive,
  onSelect,
}: {
  entryPoint: EntryPoint;
  isActive: boolean;
  onSelect: () => void;
}) {
  return (
    <Link
      to={entryPoint.route}
      onClick={onSelect}
      className={`
        flex items-center gap-3 px-4 py-2 text-sm
        transition-colors duration-150
        ${isActive 
          ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' 
          : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700'
        }
      `}
      title={entryPoint.description}
    >
      <span className="text-base">{entryPoint.icon}</span>
      <div className="flex-1 min-w-0">
        <div className="font-medium truncate">{entryPoint.name}</div>
        <div className="text-xs text-slate-500 dark:text-slate-400 truncate">
          {entryPoint.description}
        </div>
      </div>
      {entryPoint.isPrimary && (
        <span className="px-1.5 py-0.5 text-xs bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded">
          Primary
        </span>
      )}
    </Link>
  );
}

/**
 * Category section with collapsible entry points
 */
function CategorySection({
  category,
  entryPoints,
  isExpanded,
  onToggle,
  currentRoute,
  onSelect,
}: CategorySectionProps) {
  if (entryPoints.length === 0) return null;

  return (
    <div className="border-b border-slate-200 dark:border-slate-600 last:border-b-0">
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-slate-900 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
        aria-expanded={isExpanded}
      >
        <div className="flex items-center gap-2">
          <span>{category.icon}</span>
          <span>{category.name}</span>
          <span className="px-1.5 py-0.5 text-xs bg-slate-200 dark:bg-slate-600 rounded-full">
            {entryPoints.length}
          </span>
        </div>
        <span className={`transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}>
          ▼
        </span>
      </button>
      
      {isExpanded && (
        <div className="pb-2">
          {entryPoints.map((ep) => (
            <EntryPointItem
              key={ep.id}
              entryPoint={ep}
              isActive={currentRoute === ep.route}
              onSelect={onSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Search input for filtering entry points
 */
function SearchInput({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="p-3 border-b border-slate-200 dark:border-slate-600">
      <input
        type="text"
        placeholder="Search entry points..."
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-500 rounded-md bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </div>
  );
}

// =============================================================================
// Main Component
// =============================================================================

/**
 * Entry Point Selector
 *
 * Dropdown menu for navigating to different entry points based on
 * user persona and permissions.
 */
export function EntryPointSelector({ className = '' }: EntryPointSelectorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(['organization', 'department'])
  );
  
  const location = useLocation();
  const { persona, isRootUser } = usePersona();

  // Create access context for entry point filtering
  const accessContext: EntryPointAccessContext = useMemo(() => ({
    persona: persona ? {
      id: persona.id,
      type: persona.type,
      layer: persona.layer || 'contributor',
      permissions: persona.permissions,
      departmentId: persona.departmentId,
      teamId: persona.teamId,
    } : null,
    isRootUser,
  }), [persona, isRootUser]);

  // Get all entry points grouped by category
  const entryPointsByCategory = useMemo(() => {
    const registry = getEntryPointRegistry();
    const allByCategory = getEntryPointsByCategory();
    
    // Filter by access if not root user
    if (isRootUser) {
      return allByCategory;
    }

    const filtered: Record<string, EntryPoint[]> = {};
    for (const [categoryId, eps] of Object.entries(allByCategory)) {
      filtered[categoryId] = eps.filter(ep => 
        registry.canAccess(ep.id, accessContext)
      );
    }
    return filtered;
  }, [isRootUser, accessContext]);

  // Filter by search query
  const filteredEntryPoints = useMemo(() => {
    if (!searchQuery.trim()) return entryPointsByCategory;

    const query = searchQuery.toLowerCase();
    const filtered: Record<string, EntryPoint[]> = {};
    
    for (const [categoryId, eps] of Object.entries(entryPointsByCategory)) {
      filtered[categoryId] = eps.filter(ep =>
        ep.name.toLowerCase().includes(query) ||
        ep.description.toLowerCase().includes(query) ||
        ep.route.toLowerCase().includes(query)
      );
    }
    return filtered;
  }, [entryPointsByCategory, searchQuery]);

  // Total accessible entry points count
  const totalEntryPoints = useMemo(() => {
    return Object.values(filteredEntryPoints).reduce((sum, eps) => sum + eps.length, 0);
  }, [filteredEntryPoints]);

  // Toggle category expansion
  const toggleCategory = useCallback((categoryId: string) => {
    setExpandedCategories(prev => {
      const next = new Set(prev);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
      return next;
    });
  }, []);

  // Close dropdown and reset search
  const handleClose = useCallback(() => {
    setIsOpen(false);
    setSearchQuery('');
  }, []);

  // Don't render if no persona or no accessible entry points
  if (!persona || totalEntryPoints === 0) {
    return null;
  }

  // Sorted categories
  const sortedCategories = Object.values(ENTRY_POINT_CATEGORIES)
    .sort((a, b) => a.order - b.order);

  return (
    <div className={`relative ${className}`}>
      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`
          flex items-center gap-2 px-3 py-2 text-sm font-medium rounded-md
          transition-colors duration-150
          ${isOpen 
            ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' 
            : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700'
          }
          focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-800
        `}
        aria-expanded={isOpen}
        aria-haspopup="true"
        title="Navigate to entry points"
      >
        <span>🚀</span>
        <span className="hidden sm:inline">Entry Points</span>
        {isRootUser && (
          <span className="px-1.5 py-0.5 text-xs bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded font-bold">
            ROOT
          </span>
        )}
        <span className="px-1.5 py-0.5 text-xs bg-slate-200 dark:bg-slate-600 rounded-full">
          {totalEntryPoints}
        </span>
        <span className={`transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}>
          ▼
        </span>
      </button>

      {/* Dropdown Panel */}
      {isOpen && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40"
            onClick={handleClose}
            aria-hidden="true"
          />

          {/* Dropdown Content */}
          <div
            className="absolute right-0 mt-2 w-96 max-h-[70vh] bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-600 rounded-lg shadow-lg z-50 overflow-hidden flex flex-col"
            role="menu"
          >
            {/* Header */}
            <div className="px-4 py-3 bg-slate-50 dark:bg-slate-700/50 border-b border-slate-200 dark:border-slate-600">
              <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                Entry Points
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                {isRootUser 
                  ? 'Root access: All entry points available' 
                  : `Access based on ${persona.type} persona`}
              </p>
            </div>

            {/* Search */}
            <SearchInput value={searchQuery} onChange={setSearchQuery} />

            {/* Categories */}
            <div className="flex-1 overflow-y-auto">
              {sortedCategories.map((category) => (
                <CategorySection
                  key={category.id}
                  category={category}
                  entryPoints={filteredEntryPoints[category.id] || []}
                  isExpanded={expandedCategories.has(category.id)}
                  onToggle={() => toggleCategory(category.id)}
                  currentRoute={location.pathname}
                  onSelect={handleClose}
                />
              ))}
            </div>

            {/* Footer */}
            {isRootUser && (
              <div className="px-4 py-2 bg-red-50 dark:bg-red-900/20 border-t border-red-200 dark:border-red-800">
                <div className="flex items-center gap-2 text-xs text-red-700 dark:text-red-300">
                  <span>⚠️</span>
                  <span>Root user mode - unrestricted access</span>
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default EntryPointSelector;
