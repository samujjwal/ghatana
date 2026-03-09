import React, { useState } from 'react';


import { ComponentPalette } from '../ComponentPalette';

import type { ComponentMetadata } from '../../registry/ComponentRegistry';
import type { Meta, StoryObj } from '@storybook/react';

/**
 * ComponentPalette Interaction Stories - Phase 2
 *
 * Tests interactive features:
 * - Component search and filtering
 * - Category navigation
 * - Drag and drop
 * - Selection and callbacks
 * - Theme variations
 * - Performance with large sets
 */

const meta: Meta<typeof ComponentPalette> = {
  title: 'Canvas/ComponentPalette/Interactions',
  component: ComponentPalette,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'ComponentPalette interaction stories testing search, filtering, drag-drop, and selection.',
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;

/**
 *
 */
type Story = StoryObj<typeof ComponentPalette>;

/**
 * Helper to log selected components
 */
const handleComponentSelect = (componentType: string) => {
  console.log(`Component selected: ${componentType}`);
};

/**
 * ============================================
 * Story 1: Search Interaction
 * ============================================
 *
 * User types in search box and palette filters in real-time.
 * Tests search functionality with partial matches.
 */
export const SearchInteraction: Story = {
  render: () => {
    const [searchQuery, setSearchQuery] = useState('');

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '16px' }}>
          <input
            type="text"
            placeholder="Type 'button' to see search in action..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '8px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '14px',
            }}
          />
        </div>
        <ComponentPalette
          searchQuery={searchQuery}
          onComponentSelect={handleComponentSelect}
        />
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Type in the search box above. The palette filters components in real-time based on your query.',
      },
    },
  },
};

/**
 * ============================================
 * Story 2: Category Filtering
 * ============================================
 *
 * User clicks category tabs to filter components.
 * Tests category navigation and filtering.
 */
export const CategoryFiltering: Story = {
  render: () => {
    const [activeCategory, setActiveCategory] = useState<
      ComponentMetadata['category'] | 'all'
    >('all');

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '16px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {['all', 'atoms', 'molecules', 'organisms', 'templates', 'custom'].map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat as unknown)}
              style={{
                padding: '6px 12px',
                border: activeCategory === cat ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '4px',
                background: activeCategory === cat ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
                fontSize: '12px',
                fontWeight: activeCategory === cat ? 'bold' : 'normal',
              }}
            >
              {cat.charAt(0).toUpperCase() + cat.slice(1)}
            </button>
          ))}
        </div>
        <ComponentPalette
          categoryFilter={activeCategory}
          onComponentSelect={handleComponentSelect}
        />
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Click category buttons above to filter the palette. Only components in the selected category are shown.',
      },
    },
  },
};

/**
 * ============================================
 * Story 3: Combined Search and Category
 * ============================================
 *
 * Both search and category filtering active simultaneously.
 * Tests filtering logic with multiple constraints.
 */
export const CombinedSearchAndCategory: Story = {
  render: () => {
    const [searchQuery, setSearchQuery] = useState('');
    const [activeCategory, setActiveCategory] = useState<
      ComponentMetadata['category'] | 'all'
    >('all');

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '12px' }}>
          <label style={{ display: 'block', marginBottom: '4px', fontSize: '12px', fontWeight: 'bold' }}>
            Search:
          </label>
          <input
            type="text"
            placeholder="Search components..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '8px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '14px',
            }}
          />
        </div>
        <div style={{ marginBottom: '12px', display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {['all', 'atoms', 'molecules', 'organisms', 'templates', 'custom'].map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat as unknown)}
              style={{
                padding: '4px 8px',
                border: activeCategory === cat ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '4px',
                background: activeCategory === cat ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
                fontSize: '11px',
              }}
            >
              {cat.charAt(0).toUpperCase() + cat.slice(1)}
            </button>
          ))}
        </div>
        <ComponentPalette
          searchQuery={searchQuery}
          categoryFilter={activeCategory}
          onComponentSelect={handleComponentSelect}
        />
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Use both search and category filtering together. The palette applies both constraints to narrow results.',
      },
    },
  },
};

/**
 * ============================================
 * Story 4: Theme Variations
 * ============================================
 *
 * Switch between theme layers to see palette styling changes.
 * Tests theme prop and styling variations.
 */
export const ThemeVariations: Story = {
  render: () => {
    const [theme, setTheme] = useState<'base' | 'brand' | 'workspace' | 'app'>('base');

    return (
      <div style={{ width: '400px' }}>
        <div style={{ marginBottom: '16px', display: 'flex', gap: '8px' }}>
          {(['base', 'brand', 'workspace', 'app'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTheme(t)}
              style={{
                padding: '6px 12px',
                border: theme === t ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '4px',
                background: theme === t ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              {t.charAt(0).toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
        <div style={{ width: '400px', height: '600px', border: '1px solid #ddd' }}>
          <ComponentPalette theme={theme} onComponentSelect={handleComponentSelect} />
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Switch between different theme layers (base, brand, workspace, app) to see styling variations.',
      },
    },
  },
};

/**
 * ============================================
 * Story 5: Drag and Drop Interaction
 * ============================================
 *
 * User drags components from palette.
 * Tests drag start handler and data transfer.
 */
export const DragAndDropInteraction: Story = {
  render: () => {
    const [draggedItem, setDraggedItem] = useState<string | null>(null);
    const [droppedItems, setDroppedItems] = useState<string[]>([]);

    const handleDragStart = (componentType: string) => {
      setDraggedItem(componentType);
    };

    const handleDrop = () => {
      if (draggedItem) {
        setDroppedItems([...droppedItems, draggedItem]);
        setDraggedItem(null);
      }
    };

    return (
      <div style={{ display: 'flex', gap: '20px', height: '600px' }}>
        <div style={{ width: '400px' }}>
          <h4 style={{ marginTop: 0 }}>Component Palette</h4>
          <ComponentPalette onComponentSelect={handleDragStart} />
        </div>
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={handleDrop}
          style={{
            flex: 1,
            border: '2px dashed #ddd',
            borderRadius: '8px',
            padding: '16px',
            background: draggedItem ? '#f0f7ff' : '#fafafa',
            overflow: 'auto',
          }}
        >
          <h4 style={{ marginTop: 0 }}>Drop Zone</h4>
          {droppedItems.length === 0 ? (
            <p style={{ color: '#999' }}>Drag components here</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {droppedItems.map((item, idx) => (
                <div
                  key={idx}
                  style={{
                    padding: '8px',
                    background: '#fff',
                    border: '1px solid #ddd',
                    borderRadius: '4px',
                  }}
                >
                  {item}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag components from the palette (left) and drop them in the drop zone (right).',
      },
    },
  },
};

/**
 * ============================================
 * Story 6: Component Selection Callback
 * ============================================
 *
 * Click components and see selection callback in action.
 * Tests onComponentSelect handler.
 */
export const ComponentSelectionCallback: Story = {
  render: () => {
    const [selectedComponents, setSelectedComponents] = useState<string[]>([]);

    const handleComponentSelect = (componentType: string) => {
      setSelectedComponents([...selectedComponents, componentType]);
      console.log(`Selected: ${componentType}`);
    };

    return (
      <div style={{ display: 'flex', gap: '20px', height: '600px' }}>
        <div style={{ width: '400px' }}>
          <h4 style={{ marginTop: 0 }}>Click Components</h4>
          <ComponentPalette onComponentSelect={handleComponentSelect} />
        </div>
        <div style={{ flex: 1, overflow: 'auto', background: '#f9f9f9', padding: '16px' }}>
          <h4 style={{ marginTop: 0 }}>Selection History</h4>
          {selectedComponents.length === 0 ? (
            <p style={{ color: '#999' }}>Click a component to add to history</p>
          ) : (
            <ol style={{ margin: 0 }}>
              {selectedComponents.map((comp, idx) => (
                <li key={idx} style={{ marginBottom: '8px' }}>
                  {comp}
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Click components in the palette. Selection history appears on the right. Check console for log messages.',
      },
    },
  },
};

/**
 * ============================================
 * Story 7: Responsive Layout
 * ============================================
 *
 * Palette adapts to different container sizes.
 * Tests responsive behavior.
 */
export const ResponsiveLayout: Story = {
  render: () => {
    const [size, setSize] = useState<'small' | 'medium' | 'large'>('medium');

    const sizeMap = {
      small: { width: '250px', height: '400px' },
      medium: { width: '350px', height: '600px' },
      large: { width: '500px', height: '800px' },
    };

    return (
      <div>
        <div style={{ marginBottom: '16px', display: 'flex', gap: '8px' }}>
          {(['small', 'medium', 'large'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setSize(s)}
              style={{
                padding: '6px 12px',
                border: size === s ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '4px',
                background: size === s ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
              }}
            >
              {s.charAt(0).toUpperCase() + s.slice(1)}
            </button>
          ))}
        </div>
        <div style={{ border: '1px solid #ddd', ...sizeMap[size] }}>
          <ComponentPalette onComponentSelect={handleComponentSelect} />
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Switch between different container sizes (small, medium, large) to see responsive behavior.',
      },
    },
  },
};

/**
 * ============================================
 * Story 8: Empty State
 * ============================================
 *
 * Shows empty state when no components match filters.
 * Tests empty state UI.
 */
export const EmptyState: Story = {
  render: () => {
    const [searchQuery, setSearchQuery] = useState('zzzzzzzzz-no-match');

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '16px' }}>
          <input
            type="text"
            placeholder="Search..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '8px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '14px',
            }}
          />
        </div>
        <ComponentPalette
          searchQuery={searchQuery}
          onComponentSelect={handleComponentSelect}
        />
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Empty state shown when search returns no results. User can clear search to reset.',
      },
    },
  },
};

/**
 * ============================================
 * Story 9: Multi-Category with Large Set
 * ============================================
 *
 * Tests palette performance with many components.
 * Validates filtering and scrolling with large datasets.
 */
export const LargeComponentSet: Story = {
  render: () => {
    const [activeCategory, setActiveCategory] = useState<
      ComponentMetadata['category'] | 'all'
    >('all');
    const [searchQuery, setSearchQuery] = useState('');

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '8px' }}>
          <input
            type="text"
            placeholder="Search in large set..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '6px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '12px',
              marginBottom: '8px',
            }}
          />
        </div>
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginBottom: '8px' }}>
          {['all', 'atoms', 'molecules', 'organisms', 'templates', 'custom'].map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat as unknown)}
              style={{
                padding: '4px 8px',
                border: activeCategory === cat ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '3px',
                background: activeCategory === cat ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
                fontSize: '10px',
              }}
            >
              {cat}
            </button>
          ))}
        </div>
        <ComponentPalette
          searchQuery={searchQuery}
          categoryFilter={activeCategory}
          onComponentSelect={handleComponentSelect}
        />
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Test palette performance with large component library. Search and filter remain responsive.',
      },
    },
  },
};

/**
 * ============================================
 * Story 10: Keyboard Navigation
 * ============================================
 *
 * Tests keyboard interactions for accessibility.
 * Arrow keys, Enter key support.
 */
export const KeyboardNavigation: Story = {
  render: () => {
    const [focusedComponent, setFocusedComponent] = useState<string | null>(null);

    const handleKeyDown = (e: React.KeyboardEvent, componentType: string) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setFocusedComponent(componentType);
        handleComponentSelect(componentType);
      }
    };

    return (
      <div style={{ width: '400px', height: '600px' }}>
        <div style={{ marginBottom: '12px', fontSize: '12px', color: '#666' }}>
          <p>Keyboard Navigation:</p>
          <ul style={{ margin: '4px 0', paddingLeft: '20px' }}>
            <li>Tab: Focus component</li>
            <li>Enter/Space: Select component</li>
            <li>Arrow keys: Navigate (when implemented)</li>
          </ul>
        </div>
        <ComponentPalette
          onComponentSelect={(comp) => {
            setFocusedComponent(comp);
            handleComponentSelect(comp);
          }}
        />
        {focusedComponent && (
          <div
            style={{
              marginTop: '12px',
              padding: '8px',
              background: '#f0f7ff',
              border: '1px solid #0066cc',
              borderRadius: '4px',
              fontSize: '12px',
            }}
          >
            Focused: <strong>{focusedComponent}</strong>
          </div>
        )}
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story:
          'Test keyboard navigation. Tab through components, use Enter/Space to select. Check console for selections.',
      },
    },
  },
};

/**
 * ============================================
 * Story 11: Custom Styling Integration
 * ============================================
 *
 * Tests className prop for custom styling.
 * Validates that custom styles integrate properly.
 */
export const CustomStyling: Story = {
  render: () => {
    return (
      <div>
        <style>{`
          .custom-palette {
            --palette-bg: #f5f5f5;
            --palette-border: #999;
            --palette-primary: #6c63ff;
          }
          .custom-palette .component-card {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border-color: var(--palette-border);
          }
          .custom-palette .component-card:hover {
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
          }
        `}</style>
        <div style={{ width: '400px', height: '600px' }}>
          <ComponentPalette
            className="custom-palette"
            onComponentSelect={handleComponentSelect}
          />
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Custom styling applied via className prop. Shows gradient and custom hover effects.',
      },
    },
  },
};

/**
 * ============================================
 * Story 12: Mixed Interactions
 * ============================================
 *
 * Complex story combining multiple interaction features.
 * Real-world usage scenario.
 */
export const MixedInteractions: Story = {
  render: () => {
    const [searchQuery, setSearchQuery] = useState('');
    const [activeCategory, setActiveCategory] = useState<
      ComponentMetadata['category'] | 'all'
    >('all');
    const [theme, setTheme] = useState<'base' | 'brand' | 'workspace' | 'app'>('base');
    const [selectedCount, setSelectedCount] = useState(0);

    const handleSelect = (componentType: string) => {
      setSelectedCount(selectedCount + 1);
      console.log(`${selectedCount + 1}. Selected: ${componentType}`);
    };

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', height: '100%' }}>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '200px' }}>
            <input
              type="text"
              placeholder="Search components..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                width: '100%',
                padding: '6px 8px',
                border: '1px solid #ddd',
                borderRadius: '4px',
                fontSize: '12px',
              }}
            />
          </div>
          <select
            value={theme}
            onChange={(e) => setTheme(e.target.value as unknown)}
            style={{
              padding: '6px 8px',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '12px',
            }}
          >
            <option value="base">Base Theme</option>
            <option value="brand">Brand Theme</option>
            <option value="workspace">Workspace Theme</option>
            <option value="app">App Theme</option>
          </select>
        </div>
        <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
          {['all', 'atoms', 'molecules', 'organisms', 'templates', 'custom'].map((cat) => (
            <button
              key={cat}
              onClick={() => setActiveCategory(cat as unknown)}
              style={{
                padding: '4px 8px',
                border: activeCategory === cat ? '2px solid #0066cc' : '1px solid #ddd',
                borderRadius: '3px',
                background: activeCategory === cat ? '#f0f7ff' : '#fff',
                cursor: 'pointer',
                fontSize: '11px',
              }}
            >
              {cat}
            </button>
          ))}
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '8px',
            background: '#f0f7ff',
            borderRadius: '4px',
            fontSize: '12px',
          }}
        >
          <span>Components selected: {selectedCount}</span>
          {selectedCount > 0 && (
            <button
              onClick={() => setSelectedCount(0)}
              style={{
                padding: '4px 8px',
                border: '1px solid #0066cc',
                borderRadius: '3px',
                background: '#fff',
                cursor: 'pointer',
                fontSize: '11px',
              }}
            >
              Reset
            </button>
          )}
        </div>
        <div style={{ flex: 1, minHeight: 0, border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden' }}>
          <ComponentPalette
            searchQuery={searchQuery}
            categoryFilter={activeCategory}
            theme={theme}
            onComponentSelect={handleSelect}
          />
        </div>
      </div>
    );
  },
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        story:
          'Real-world scenario combining search, category filtering, theme switching, and selection tracking.',
      },
    },
  },
};
