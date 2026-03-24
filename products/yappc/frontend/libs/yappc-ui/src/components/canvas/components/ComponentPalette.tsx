/**
 * Component Palette
 *
 * Searchable component library for drag-and-drop UI composition
 */

import React, { useState, useMemo } from 'react';

import { ComponentNodeAdapter } from '../adapters/ComponentNodeAdapter';
import { ComponentRegistry } from '../registry/ComponentRegistry';

import type { ComponentMetadata } from '../registry/ComponentRegistry';
import type { TransformContext } from '../types';

/**
 * Component Palette Props
 */
export interface ComponentPaletteProps {
  /**
   * Current theme layer
   */
  theme?: 'base' | 'brand' | 'workspace' | 'app';

  /**
   * Search query
   */
  searchQuery?: string;

  /**
   * Selected category filter
   */
  categoryFilter?: ComponentMetadata['category'] | 'all';

  /**
   * Callback when component is selected for adding to canvas
   */
  onComponentSelect?: (componentType: string) => void;

  /**
   * Custom class name
   */
  className?: string;
}

/**
 * Component Palette Component
 */
export function ComponentPalette({
  theme = 'base',
  searchQuery = '',
  categoryFilter = 'all',
  onComponentSelect,
  className = '',
}: ComponentPaletteProps) {
  const [activeCategory, setActiveCategory] = useState<ComponentMetadata['category'] | 'all'>(
    categoryFilter
  );
  const [search, setSearch] = useState(searchQuery);

  // Get filtered components
  const filteredComponents = useMemo(() => {
    let components = ComponentRegistry.getAll();

    // Apply category filter
    if (activeCategory !== 'all') {
      components = components.filter((comp) => comp.category === activeCategory);
    }

    // Apply search filter
    if (search.trim()) {
      components = ComponentRegistry.search(search);
      if (activeCategory !== 'all') {
        components = components.filter((comp) => comp.category === activeCategory);
      }
    }

    return components;
  }, [activeCategory, search]);

  // Category counts
  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = {
      all: ComponentRegistry.count(),
      atoms: ComponentRegistry.getByCategory('atoms').length,
      molecules: ComponentRegistry.getByCategory('molecules').length,
      organisms: ComponentRegistry.getByCategory('organisms').length,
      templates: ComponentRegistry.getByCategory('templates').length,
      custom: ComponentRegistry.getByCategory('custom').length,
    };
    return counts;
  }, []);

  // Handle component selection
  const handleComponentClick = (component: ComponentMetadata) => {
    if (onComponentSelect) {
      onComponentSelect(component.type);
    }
  };

  // Handle drag start
  const handleDragStart = (event: React.DragEvent, component: ComponentMetadata) => {
    // Create default schema
    const schema = ComponentRegistry.createDefaultSchema(component.type);
    if (schema) {
      event.dataTransfer.setData('application/json', JSON.stringify(schema));
      event.dataTransfer.effectAllowed = 'copy';
    }
  };

  return (
    <div className={`component-palette ${className}`}>
      {/* Search */}
      <div className="palette-search">
        <input
          type="text"
          placeholder="Search components..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="search-input"
        />
      </div>

      {/* Category Tabs */}
      <div className="palette-categories">
        {(['all', 'atoms', 'molecules', 'organisms', 'templates', 'custom'] as const).map((cat) => (
          <button
            key={cat}
            className={`category-tab ${activeCategory === cat ? 'active' : ''}`}
            onClick={() => setActiveCategory(cat)}
          >
            {cat.charAt(0).toUpperCase() + cat.slice(1)}
            <span className="count">({categoryCounts[cat]})</span>
          </button>
        ))}
      </div>

      {/* Component List */}
      <div className="palette-components">
        {filteredComponents.length === 0 ? (
          <div className="empty-state">
            <p>No components found</p>
            {search && <button onClick={() => setSearch('')}>Clear search</button>}
          </div>
        ) : (
          filteredComponents.map((component) => (
            <ComponentCard
              key={component.type}
              component={component}
              onClick={() => handleComponentClick(component)}
              onDragStart={(e) => handleDragStart(e, component)}
            />
          ))
        )}
      </div>
    </div>
  );
}

/**
 * Component Card Props
 */
interface ComponentCardProps {
  component: ComponentMetadata;
  onClick: () => void;
  onDragStart: (event: React.DragEvent) => void;
}

/**
 * Component Card
 */
function ComponentCard({ component, onClick, onDragStart }: ComponentCardProps) {
  return (
    <div
      className="component-card"
      draggable
      onDragStart={onDragStart}
      onClick={onClick}
      role="button"
      tabIndex={0}
      data-component-type={component.type}
    >
      {/* Icon */}
      {component.icon && (
        <div className="component-icon">
          <span className="icon">{component.icon}</span>
        </div>
      )}

      {/* Info */}
      <div className="component-info">
        <h4 className="component-name">{component.displayName}</h4>
        {component.description && (
          <p className="component-description">{component.description}</p>
        )}
        {component.tags && component.tags.length > 0 && (
          <div className="component-tags">
            {component.tags.slice(0, 3).map((tag) => (
              <span key={tag} className="tag">
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Category Badge */}
      <div className="component-badge">
        <span className={`badge badge-${component.category}`}>
          {component.category}
        </span>
      </div>
    </div>
  );
}

/**
 * Component Palette Styles (to be extracted to CSS/styled-components)
 */
export const componentPaletteStyles = `
  .component-palette {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--color-background, #ffffff);
    border-right: 1px solid var(--color-border, #e0e0e0);
  }

  .palette-search {
    padding: 12px;
    border-bottom: 1px solid var(--color-border, #e0e0e0);
  }

  .search-input {
    width: 100%;
    padding: 8px 12px;
    border: 1px solid var(--color-border, #e0e0e0);
    border-radius: 4px;
    font-size: 14px;
  }

  .palette-categories {
    display: flex;
    overflow-x: auto;
    border-bottom: 1px solid var(--color-border, #e0e0e0);
    background: var(--color-background-secondary, #f5f5f5);
  }

  .category-tab {
    padding: 8px 16px;
    border: none;
    background: transparent;
    cursor: pointer;
    white-space: nowrap;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .category-tab.active {
    background: var(--color-background, #ffffff);
    border-bottom: 2px solid var(--color-primary, #0066cc);
  }

  .category-tab .count {
    opacity: 0.6;
    font-size: 12px;
  }

  .palette-components {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }

  .component-card {
    display: flex;
    align-items: start;
    gap: 12px;
    padding: 12px;
    margin-bottom: 8px;
    border: 1px solid var(--color-border, #e0e0e0);
    border-radius: 6px;
    cursor: grab;
    background: var(--color-background, #ffffff);
    transition: all 0.2s;
  }

  .component-card:hover {
    border-color: var(--color-primary, #0066cc);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  .component-card:active {
    cursor: grabbing;
  }

  .component-icon {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--color-background-secondary, #f5f5f5);
    border-radius: 4px;
  }

  .component-info {
    flex: 1;
    min-width: 0;
  }

  .component-name {
    margin: 0 0 4px;
    font-size: 14px;
    font-weight: 600;
  }

  .component-description {
    margin: 0 0 8px;
    font-size: 12px;
    color: var(--color-text-secondary, #666666);
  }

  .component-tags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  .component-tags .tag {
    padding: 2px 6px;
    background: var(--color-background-tertiary, #eeeeee);
    border-radius: 3px;
    font-size: 11px;
  }

  .component-badge {
    flex-shrink: 0;
  }

  .badge {
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 500;
  }

  .badge-atoms {
    background: #e3f2fd;
    color: #1976d2;
  }

  .badge-molecules {
    background: #f3e5f5;
    color: #7b1fa2;
  }

  .badge-organisms {
    background: #e8f5e9;
    color: #388e3c;
  }

  .badge-templates {
    background: #fff3e0;
    color: #f57c00;
  }

  .badge-custom {
    background: #fce4ec;
    color: #c2185b;
  }

  .empty-state {
    padding: 32px;
    text-align: center;
    color: var(--color-text-secondary, #666666);
  }

  .empty-state button {
    margin-top: 16px;
    padding: 8px 16px;
    border: 1px solid var(--color-border, #e0e0e0);
    border-radius: 4px;
    background: var(--color-background, #ffffff);
    cursor: pointer;
  }
`;
