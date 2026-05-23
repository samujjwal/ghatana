/**
 * @fileoverview Component palette for visual builder.
 *
 * Displays available components from the design system registry in a searchable,
 * filterable palette. Components can be dragged onto the canvas or clicked to add.
 *
 * @doc.type component
 * @doc.purpose Component selection and discovery for visual builder
 * @doc.layer platform
 */

import type { KeyboardEvent, ReactElement } from 'react';
import { useState, useMemo } from 'react';
import { Typography, Input, Badge } from '@ghatana/design-system';

// Simplified component contract interface for the palette
export interface ComponentContract {
  name: string;
  displayName?: string;
  description?: string;
  category?: string;
  deprecated?: boolean;
  props?: Record<string, unknown>;
}

export interface ComponentPaletteProps {
  /** Available component contracts from design system registry */
  contracts: readonly ComponentContract[];
  /** Callback when a component is selected/dragged */
  onComponentSelect: (contract: ComponentContract) => void;
  /** Optional category filter */
  categoryFilter?: string;
  /** Optional search query */
  searchQuery?: string;
}

export function ComponentPalette({
  contracts,
  onComponentSelect,
  categoryFilter,
  searchQuery: externalSearchQuery,
}: ComponentPaletteProps): ReactElement {
  const [searchQuery, setSearchQuery] = useState(externalSearchQuery || '');
  const [selectedCategory, setSelectedCategory] = useState<string | undefined>(categoryFilter);

  // Extract unique categories from contracts
  const categories = useMemo(() => {
    const cats = new Set<string>();
    for (const contract of contracts) {
      if (contract.category) {
        cats.add(contract.category);
      }
    }
    return Array.from(cats).sort();
  }, [contracts]);

  // Filter contracts by category and search query
  const filteredContracts = useMemo(() => {
    return contracts.filter((contract) => {
      if (selectedCategory && contract.category !== selectedCategory) {
        return false;
      }

      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        return (
          contract.name.toLowerCase().includes(query) ||
          contract.displayName?.toLowerCase().includes(query) ||
          contract.description?.toLowerCase().includes(query)
        );
      }

      return true;
    });
  }, [contracts, selectedCategory, searchQuery]);

  const handleDragStart = (contract: ComponentContract): void => {
    onComponentSelect(contract);
  };

  const handlePaletteItemKeyDown = (
    event: KeyboardEvent<HTMLDivElement>,
    contract: ComponentContract,
  ): void => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onComponentSelect(contract);
  };

  return (
    <div className="flex flex-col h-full">
      {/* Search */}
      <div className="p-3 border-b">
        <Input
          data-testid="builder-palette-search"
          placeholder="Search components..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full"
        />
      </div>

      {/* Category Filter */}
      <div className="p-3 border-b flex gap-2 overflow-x-auto">
        <button
          type="button"
          data-testid="builder-palette-category-all"
          onClick={() => setSelectedCategory(undefined)}
          className={`px-3 py-1 rounded-full text-sm whitespace-nowrap ${
            selectedCategory === undefined
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          All
        </button>
        {categories.map((category) => (
          <button
            key={category}
            type="button"
            data-testid={`builder-palette-category-${category}`}
            onClick={() => setSelectedCategory(category)}
            className={`px-3 py-1 rounded-full text-sm whitespace-nowrap ${
              selectedCategory === category
                ? 'bg-blue-500 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {category}
          </button>
        ))}
      </div>

      {/* Component List */}
      <div className="flex-1 overflow-y-auto p-3">
        {filteredContracts.length === 0 ? (
          <div className="text-center py-8">
            <Typography variant="body2" className="text-gray-500">
              No components found
            </Typography>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredContracts.map((contract) => (
              <div
                key={contract.name}
                data-testid={`builder-palette-item-${contract.name}`}
                role="button"
                tabIndex={0}
                aria-label={`Add ${contract.displayName || contract.name}`}
                draggable
                onDragStart={() => handleDragStart(contract)}
                onClick={() => onComponentSelect(contract)}
                onKeyDown={(event) => handlePaletteItemKeyDown(event, contract)}
                className="p-3 border rounded-lg hover:border-blue-500 hover:bg-blue-50 cursor-pointer transition-colors"
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <Typography variant="body1" className="font-medium">
                        {contract.displayName || contract.name}
                      </Typography>
                      {contract.category && (
                        <Badge variant="soft" tone="neutral" className="text-xs">
                          {contract.category}
                        </Badge>
                      )}
                    </div>
                    {contract.description && (
                      <Typography variant="body2" className="text-gray-500 text-sm">
                        {contract.description}
                      </Typography>
                    )}
                  </div>
                </div>
                {contract.deprecated && (
                  <div className="mt-2">
                    <Badge variant="soft" tone="warning" className="text-xs">
                      Deprecated
                    </Badge>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
