/* eslint-disable storybook/no-renderer-packages */
import React, { useState } from 'react';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * Advanced ComponentPalette Stories - Phase 3
 *
 * Stories testing advanced features:
 * - Advanced search and filtering
 * - Custom categorization
 * - Drag and drop interactions
 * - Performance with large component libraries
 * - Real-time collaboration
 */

// Placeholder for actual ComponentPalette component
const ComponentPalette: React.FC<unknown> = ({ _theme, _searchQuery, _categoryFilter, onComponentSelect, children, components }) => (
  <div
    style={{
      width: '300px',
      height: '600px',
      border: '1px solid #ddd',
      background: '#fafafa',
      display: 'flex',
      flexDirection: 'column',
      fontFamily: 'system-ui, -apple-system, sans-serif',
      overflow: 'hidden',
    }}
  >
    <div
      style={{
        padding: '12px',
        borderBottom: '1px solid #ddd',
        backgroundColor: '#f0f0f0',
        fontSize: '12px',
        fontWeight: 'bold',
      }}
    >
      {children || 'Component Palette'}
    </div>

    <div style={{ flex: 1, overflow: 'auto', padding: '8px' }}>
      {components && components.length > 0 ? (
        components.map((c: unknown) => (
          <div
            key={c.id}
            onClick={() => onComponentSelect?.(c)}
            style={{
              padding: '8px',
              marginBottom: '4px',
              background: '#fff',
              border: '1px solid #ddd',
              borderRadius: '4px',
              fontSize: '11px',
              cursor: 'pointer',
              userSelect: 'none',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = '#f0f7ff';
              e.currentTarget.style.borderColor = '#007bff';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = '#fff';
              e.currentTarget.style.borderColor = '#ddd';
            }}
          >
            {c.name}
          </div>
        ))
      ) : (
        <div style={{ padding: '12px', color: '#999', fontSize: '11px' }}>No components</div>
      )}
    </div>
  </div>
);

const meta: Meta<typeof ComponentPalette> = {
  title: 'UI/ComponentPalette/Advanced',
  component: ComponentPalette,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'Advanced ComponentPalette features: Advanced Search, Filtering, Drag-Drop, Performance, Collaboration.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    onComponentSelect: { action: 'component-selected' },
  },
};

export default meta;

/**
 *
 */
type Story = StoryObj<typeof ComponentPalette>;

// ============================================
// Story 1: Advanced Search with Fuzzy Matching
// ============================================
export const AdvancedSearchFuzzy: Story = {
  render: () => {
    const [searchTerm, setSearchTerm] = useState('');

    const allComponents = [
      { id: 'btn', name: 'Button' },
      { id: 'btn-primary', name: 'Button Primary' },
      { id: 'btn-secondary', name: 'Button Secondary' },
      { id: 'input-text', name: 'Input Text' },
      { id: 'input-number', name: 'Input Number' },
      { id: 'select', name: 'Select Dropdown' },
      { id: 'card', name: 'Card Container' },
      { id: 'modal', name: 'Modal Dialog' },
      { id: 'tooltip', name: 'Tooltip' },
      { id: 'badge', name: 'Badge' },
    ];

    // Simple fuzzy matching
    const filtered = allComponents.filter((c) => c.name.toLowerCase().includes(searchTerm.toLowerCase()));

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <input
          type="text"
          placeholder="Try typing 'btn' or 'inp'..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{
            padding: '8px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '14px',
          }}
        />
        <ComponentPalette components={filtered}>
          Advanced Search - {filtered.length} results
        </ComponentPalette>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Fuzzy search matching allows searching for components by partial name. Type "btn" to find all button variants.',
      },
    },
  },
};

// ============================================
// Story 2: Multi-Category Filtering
// ============================================
export const MultiCategoryFiltering: Story = {
  render: () => {
    const [selectedCategories, setSelectedCategories] = useState<Set<string>>(new Set(['form', 'layout']));

    const allComponents = [
      { id: 'btn', name: 'Button', category: 'form' },
      { id: 'input', name: 'Input', category: 'form' },
      { id: 'card', name: 'Card', category: 'layout' },
      { id: 'grid', name: 'Grid', category: 'layout' },
      { id: 'tooltip', name: 'Tooltip', category: 'feedback' },
      { id: 'badge', name: 'Badge', category: 'feedback' },
      { id: 'modal', name: 'Modal', category: 'overlay' },
    ];

    const categories = ['form', 'layout', 'feedback', 'overlay'];

    const filtered = allComponents.filter((c) => selectedCategories.has(c.category));

    const toggleCategory = (cat: string) => {
      setSelectedCategories((prev) => {
        const next = new Set(prev);
        if (next.has(cat)) {
          next.delete(cat);
        } else {
          next.add(cat);
        }
        return next;
      });
    };

    return (
      <div style={{ display: 'flex', gap: '16px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div style={{ fontWeight: 'bold', fontSize: '12px' }}>Categories:</div>
          {categories.map((cat) => (
            <label key={cat} style={{ display: 'flex', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={selectedCategories.has(cat)}
                onChange={() => toggleCategory(cat)}
              />
              {cat}
            </label>
          ))}
        </div>
        <ComponentPalette components={filtered}>
          Filtered - {filtered.length} components
        </ComponentPalette>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Filter components by multiple categories simultaneously. Select/deselect categories to refine the palette.',
      },
    },
  },
};

// ============================================
// Story 3: Custom Component Groups
// ============================================
export const CustomComponentGroups: Story = {
  render: () => {
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(['form', 'layout']));

    const groups = [
      {
        id: 'form',
        name: 'Form Components',
        components: [
          { id: 'btn', name: 'Button' },
          { id: 'input', name: 'Input' },
          { id: 'select', name: 'Select' },
        ],
      },
      {
        id: 'layout',
        name: 'Layout Components',
        components: [
          { id: 'card', name: 'Card' },
          { id: 'grid', name: 'Grid' },
          { id: 'flex', name: 'Flex' },
        ],
      },
      {
        id: 'feedback',
        name: 'Feedback Components',
        components: [
          { id: 'tooltip', name: 'Tooltip' },
          { id: 'badge', name: 'Badge' },
          { id: 'alert', name: 'Alert' },
        ],
      },
    ];

    const toggleGroup = (groupId: string) => {
      setExpandedGroups((prev) => {
        const next = new Set(prev);
        if (next.has(groupId)) {
          next.delete(groupId);
        } else {
          next.add(groupId);
        }
        return next;
      });
    };

    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', width: '300px' }}>
        <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
          Component Groups
        </div>
        <div style={{ maxHeight: '600px', overflow: 'auto' }}>
          {groups.map((group) => (
            <div key={group.id} style={{ borderBottom: '1px solid #eee' }}>
              <div
                onClick={() => toggleGroup(group.id)}
                style={{
                  padding: '8px 12px',
                  background: '#f9f9f9',
                  cursor: 'pointer',
                  display: 'flex',
                  justifyContent: 'space-between',
                  fontSize: '12px',
                  fontWeight: 'bold',
                  userSelect: 'none',
                }}
              >
                {group.name}
                <span style={{ fontSize: '10px' }}>{expandedGroups.has(group.id) ? '▼' : '▶'}</span>
              </div>
              {expandedGroups.has(group.id) && (
                <div style={{ padding: '4px' }}>
                  {group.components.map((c) => (
                    <div
                      key={c.id}
                      style={{
                        padding: '6px 12px',
                        fontSize: '11px',
                        cursor: 'pointer',
                        transition: 'all 0.2s',
                        userSelect: 'none',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.background = '#f0f7ff';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.background = 'transparent';
                      }}
                    >
                      {c.name}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Group components into collapsible categories. Click group headers to expand/collapse.',
      },
    },
  },
};

// ============================================
// Story 4: Drag and Drop Support
// ============================================
export const DragDropSupport: Story = {
  render: () => {
    const [draggedComponent, setDraggedComponent] = useState<unknown>(null);

    const components = [
      { id: 'btn', name: 'Button' },
      { id: 'input', name: 'Input' },
      { id: 'card', name: 'Card' },
    ];

    return (
      <div style={{ display: 'flex', gap: '16px' }}>
        <div
          style={{
            width: '300px',
            height: '600px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
            Drag Components
          </div>
          <div style={{ flex: 1, overflow: 'auto', padding: '8px' }}>
            {components.map((c) => (
              <div
                key={c.id}
                draggable
                onDragStart={() => setDraggedComponent(c)}
                onDragEnd={() => setDraggedComponent(null)}
                style={{
                  padding: '8px',
                  marginBottom: '4px',
                  background: draggedComponent?.id === c.id ? '#d1ecf1' : '#fff',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '11px',
                  cursor: 'grab',
                  opacity: draggedComponent?.id === c.id ? 0.7 : 1,
                }}
              >
                {c.name}
              </div>
            ))}
          </div>
        </div>
        <div
          style={{
            width: '400px',
            height: '600px',
            border: '2px dashed #ccc',
            borderRadius: '4px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: '#f9f9f9',
            color: '#999',
            fontSize: '12px',
          }}
          onDragOver={(e) => {
            e.preventDefault();
            e.currentTarget.style.borderColor = '#007bff';
            e.currentTarget.style.background = '#f0f7ff';
          }}
          onDragLeave={(e) => {
            e.currentTarget.style.borderColor = '#ccc';
            e.currentTarget.style.background = '#f9f9f9';
          }}
          onDrop={(e) => {
            e.preventDefault();
            e.currentTarget.style.borderColor = '#ccc';
            e.currentTarget.style.background = '#f9f9f9';
          }}
        >
          {draggedComponent
            ? `Drop ${draggedComponent.name} here`
            : 'Drag components here to add to canvas'}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Drag components from palette onto canvas. Visual feedback shows drop zones.',
      },
    },
  },
};

// ============================================
// Story 5: Large Component Library (1000+)
// ============================================
export const LargeComponentLibrary: Story = {
  render: () => {
    const [searchTerm, setSearchTerm] = useState('');

    // Generate 1000 components
    const allComponents = Array.from({ length: 1000 }, (_, i) => ({
      id: `comp-${i}`,
      name: `Component ${i + 1}`,
      category: ['form', 'layout', 'feedback', 'overlay'][i % 4],
    }));

    const filtered = allComponents.filter((c) =>
      c.name.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div style={{ fontSize: '12px', color: '#666' }}>
          Total: {allComponents.length} components | Showing: {filtered.length}
        </div>
        <input
          type="text"
          placeholder="Search 1000+ components..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{
            padding: '8px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '14px',
          }}
        />
        <div style={{ height: '400px', border: '1px solid #ddd', borderRadius: '4px', overflow: 'auto' }}>
          {filtered.slice(0, 50).map((c) => (
            <div
              key={c.id}
              style={{
                padding: '6px 12px',
                borderBottom: '1px solid #eee',
                fontSize: '11px',
                cursor: 'pointer',
              }}
            >
              {c.name}
            </div>
          ))}
          {filtered.length > 50 && (
            <div
              style={{
                padding: '8px 12px',
                textAlign: 'center',
                fontSize: '11px',
                color: '#999',
                background: '#f9f9f9',
              }}
            >
              ... and {filtered.length - 50} more
            </div>
          )}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Handle large component libraries with 1000+ components. Search enables efficient lookup.',
      },
    },
  },
};

// ============================================
// Story 6: Recently Used Components
// ============================================
export const RecentlyUsed: Story = {
  render: () => {
    const recent = [
      { id: 'btn', name: 'Button', lastUsed: '2 min ago' },
      { id: 'input', name: 'Input', lastUsed: '5 min ago' },
      { id: 'card', name: 'Card', lastUsed: '15 min ago' },
      { id: 'grid', name: 'Grid', lastUsed: '30 min ago' },
    ];

    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', width: '300px' }}>
        <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
          Recently Used (4)
        </div>
        <div style={{ maxHeight: '300px', overflow: 'auto' }}>
          {recent.map((c) => (
            <div
              key={c.id}
              style={{
                padding: '8px 12px',
                borderBottom: '1px solid #eee',
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '11px',
                cursor: 'pointer',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#f0f7ff';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
              }}
            >
              <span>{c.name}</span>
              <span style={{ color: '#999' }}>{c.lastUsed}</span>
            </div>
          ))}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Show frequently used components at the top for quick access. Speeds up common workflows.',
      },
    },
  },
};

// ============================================
// Story 7: Favorites / Starred Components
// ============================================
export const FavoritedComponents: Story = {
  render: () => {
    const [favorites, setFavorites] = useState<Set<string>>(new Set(['btn', 'card']));

    const allComponents = [
      { id: 'btn', name: 'Button', category: 'form' },
      { id: 'input', name: 'Input', category: 'form' },
      { id: 'card', name: 'Card', category: 'layout' },
      { id: 'grid', name: 'Grid', category: 'layout' },
      { id: 'tooltip', name: 'Tooltip', category: 'feedback' },
    ];

    const toggleFavorite = (id: string) => {
      setFavorites((prev) => {
        const next = new Set(prev);
        if (next.has(id)) {
          next.delete(id);
        } else {
          next.add(id);
        }
        return next;
      });
    };

    const favorited = allComponents.filter((c) => favorites.has(c.id));
    const others = allComponents.filter((c) => !favorites.has(c.id));

    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', width: '300px' }}>
        {favorited.length > 0 && (
          <>
            <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
              ⭐ Favorites ({favorited.length})
            </div>
            <div>
              {favorited.map((c) => (
                <div
                  key={c.id}
                  style={{
                    padding: '8px 12px',
                    borderBottom: '1px solid #eee',
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '11px',
                  }}
                >
                  <span>{c.name}</span>
                  <button
                    onClick={() => toggleFavorite(c.id)}
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '12px',
                    }}
                  >
                    ⭐
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
        <div style={{ padding: '12px', background: '#fafafa', fontWeight: 'bold', fontSize: '12px' }}>
          Other ({others.length})
        </div>
        <div>
          {others.map((c) => (
            <div
              key={c.id}
              style={{
                padding: '8px 12px',
                borderBottom: '1px solid #eee',
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: '11px',
              }}
            >
              <span>{c.name}</span>
              <button
                onClick={() => toggleFavorite(c.id)}
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '12px',
                  color: '#ccc',
                }}
              >
                ☆
              </button>
            </div>
          ))}
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Star/favorite frequently used components for quick access. Personalized component organization.',
      },
    },
  },
};

// ============================================
// Story 8: Component Preview on Hover
// ============================================
export const ComponentPreviewHover: Story = {
  render: () => {
    const [hoveredComponent, setHoveredComponent] = useState<unknown>(null);

    const components = [
      { id: 'btn', name: 'Button', preview: '→ Button' },
      { id: 'input', name: 'Input Field', preview: '→ [Input]' },
      { id: 'card', name: 'Card Container', preview: '→ ╔════╗\n  ║    ║\n  ╚════╝' },
    ];

    return (
      <div style={{ display: 'flex', gap: '16px' }}>
        <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', width: '200px' }}>
          <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
            Components
          </div>
          <div>
            {components.map((c) => (
              <div
                key={c.id}
                onMouseEnter={() => setHoveredComponent(c)}
                onMouseLeave={() => setHoveredComponent(null)}
                style={{
                  padding: '8px 12px',
                  borderBottom: '1px solid #eee',
                  fontSize: '11px',
                  cursor: 'pointer',
                  background: hoveredComponent?.id === c.id ? '#f0f7ff' : '#fff',
                }}
              >
                {c.name}
              </div>
            ))}
          </div>
        </div>
        {hoveredComponent && (
          <div style={{ border: '1px solid #007bff', borderRadius: '4px', padding: '12px', minWidth: '150px', background: '#f0f7ff' }}>
            <div style={{ fontWeight: 'bold', fontSize: '12px', marginBottom: '8px' }}>
              {hoveredComponent.name}
            </div>
            <pre style={{ fontSize: '10px', margin: 0, color: '#666', whiteSpace: 'pre-wrap' }}>
              {hoveredComponent.preview}
            </pre>
          </div>
        )}
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Hover over components to see live preview. Quick visual reference without needing to add to canvas.',
      },
    },
  },
};

// ============================================
// Story 9: Real-Time Sync with Team
// ============================================
export const RealtimeSyncTeam: Story = {
  render: () => {
    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', width: '300px' }}>
        <div style={{ padding: '12px', background: '#f0f0f0', fontWeight: 'bold', fontSize: '12px' }}>
          Shared Library - Real-time Sync
        </div>
        <div style={{ padding: '8px', background: '#e8f5e9', fontSize: '11px', color: '#2e7d32', borderBottom: '1px solid #ddd' }}>
          ✓ Syncing... (Last updated 2 seconds ago)
        </div>
        <div style={{ maxHeight: '300px', overflow: 'auto' }}>
          {['Button', 'Input', 'Card', 'Modal', 'Tooltip'].map((name) => (
            <div
              key={name}
              style={{
                padding: '8px 12px',
                borderBottom: '1px solid #eee',
                fontSize: '11px',
                display: 'flex',
                justifyContent: 'space-between',
              }}
            >
              <span>{name}</span>
              <span style={{ fontSize: '10px', color: '#999' }}>v1.2.0</span>
            </div>
          ))}
        </div>
        <div style={{ padding: '8px 12px', background: '#f9f9f9', fontSize: '10px', color: '#999' }}>
          5 components shared with team
        </div>
      </div>
    );
  },
  parameters: {
    docs: {
      description: {
        story: 'Component library syncs in real-time with team. All team members see updates instantly.',
      },
    },
  },
};
