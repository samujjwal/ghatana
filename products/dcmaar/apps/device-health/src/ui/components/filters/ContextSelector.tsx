/**
 * @fileoverview Context Selector Component
 *
 * Allows users to switch between global, domain, and page
 * contexts for analytics viewing.
 *
 * @module ui/components/filters
 * @since 2.0.0
 */

import React, { useState, useEffect } from 'react';

type Scope = 'global' | 'domain' | 'page';

interface ContextSelectorProps {
  scope: Scope;
  entity?: string;
  onScopeChange: (scope: Scope) => void;
  onEntityChange: (entity?: string) => void;
  disabled?: boolean;
}

const SCOPE_OPTIONS: Record<Scope, { label: string; description: string; icon: string }> = {
  global: { label: 'Global', description: 'All domains and pages', icon: '🌐' },
  domain: { label: 'Domain', description: 'Specific domain analytics', icon: '🏢' },
  page: { label: 'Page', description: 'Specific page analytics', icon: '📄' },
};

// Mock data - in production, this would come from your analytics data
const MOCK_DOMAINS = [
  'example.com',
  'test.com',
  'staging.example.com',
  'api.example.com',
];

const MOCK_PAGES = [
  '/home',
  '/about',
  '/products',
  '/contact',
  '/dashboard',
];

/**
 * Context Selector Component
 *
 * Provides scope selection and entity filtering for analytics.
 */
export const ContextSelector: React.FC<ContextSelectorProps> = ({
  scope,
  entity,
  onScopeChange,
  onEntityChange,
  disabled = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [availableEntities, setAvailableEntities] = useState<string[]>([]);

  // Update available entities based on scope
  useEffect(() => {
    if (scope === 'domain') {
      setAvailableEntities(MOCK_DOMAINS);
    } else if (scope === 'page') {
      setAvailableEntities(MOCK_PAGES);
    } else {
      setAvailableEntities([]);
    }
    setSearchTerm('');
    onEntityChange?.(undefined);
  }, [scope, onEntityChange]);

  // Filter entities based on search
  const filteredEntities = availableEntities.filter(entity =>
    entity.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleScopeSelect = (newScope: Scope) => {
    onScopeChange(newScope);
    setIsOpen(false);
  };

  const handleEntitySelect = (selectedEntity: string) => {
    onEntityChange(selectedEntity);
    setIsOpen(false);
  };

  const getCurrentLabel = (): string => {
    const scopeInfo = SCOPE_OPTIONS[scope];
    if (scope === 'global') return scopeInfo.label;
    if (entity) return `${scopeInfo.label}: ${entity}`;
    return scopeInfo.label;
  };

  return (
    <div className="relative">
      {/* Main selector button */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        disabled={disabled}
        className="flex items-center gap-2 px-3 py-2 text-sm border border-slate-300 rounded-md hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <span>{SCOPE_OPTIONS[scope].icon}</span>
        <span className="font-medium">{getCurrentLabel()}</span>
        <span className="text-slate-500">▼</span>
      </button>

      {/* Dropdown */}
      {isOpen && (
        <div className="absolute top-full left-0 mt-1 w-80 bg-white border border-slate-200 rounded-lg shadow-lg z-50">
          <div className="p-4">
            {/* Scope selection */}
            <div className="mb-4">
              <h3 className="text-sm font-medium text-slate-700 mb-2">Scope</h3>
              <div className="space-y-1">
                {Object.entries(SCOPE_OPTIONS).map(([key, info]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => handleScopeSelect(key as Scope)}
                    className={`w-full flex items-center gap-3 px-3 py-2 text-sm rounded-md text-left transition-colors ${
                      scope === key
                        ? 'bg-blue-100 text-blue-700 border border-blue-200'
                        : 'hover:bg-slate-50 border border-transparent'
                    }`}
                  >
                    <span className="text-lg">{info.icon}</span>
                    <div>
                      <div className="font-medium">{info.label}</div>
                      <div className="text-xs text-slate-500">{info.description}</div>
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Entity selection */}
            {scope !== 'global' && (
              <div>
                <h3 className="text-sm font-medium text-slate-700 mb-2">
                  Select {scope === 'domain' ? 'Domain' : 'Page'}
                </h3>
                
                {/* Search */}
                <input
                  type="text"
                  placeholder={`Search ${scope}s...`}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full px-3 py-2 text-sm border border-slate-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 mb-2"
                />

                {/* Entity list */}
                <div className="max-h-40 overflow-y-auto border border-slate-200 rounded-md">
                  {filteredEntities.length === 0 ? (
                    <div className="px-3 py-2 text-sm text-slate-500 text-center">
                      No {scope}s found
                    </div>
                  ) : (
                    filteredEntities.map(entity => (
                      <button
                        key={entity}
                        type="button"
                        onClick={() => handleEntitySelect(entity)}
                        className={`w-full px-3 py-2 text-sm text-left hover:bg-slate-50 border-b border-slate-100 last:border-b-0 ${
                          entity === selectedEntity ? 'bg-blue-50 text-blue-700' : ''
                        }`}
                      >
                        {entity}
                      </button>
                    ))
                  )}
                </div>

                {/* Clear selection */}
                {entity && (
                  <button
                    type="button"
                    onClick={() => onEntityChange(undefined)}
                    className="mt-2 w-full px-3 py-2 text-sm text-slate-600 hover:text-slate-800 hover:bg-slate-50 rounded-md"
                  >
                    Clear selection
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default ContextSelector;
