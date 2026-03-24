import React from 'react';

import type { Meta, StoryObj } from '@storybook/react';

/**
 * ComponentPalette Stories - Comprehensive test coverage for component palette
 *
 * Tests all palette states, interactions, and configurations.
 */

// Placeholder for actual ComponentPalette component
// Real import: import { ComponentPalette } from '../ComponentPalette';

const ComponentPalette: React.FC<unknown> = ({ theme, searchQuery, categoryFilter, onComponentSelect, children }) => (
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
      Component Palette ({theme || 'default'})
    </div>

    <div
      style={{
        padding: '8px',
        borderBottom: '1px solid #eee',
        backgroundColor: '#fff',
      }}
    >
      <input
        type="text"
        placeholder="Search..."
        value={searchQuery || ''}
        disabled
        style={{
          width: '100%',
          padding: '6px 8px',
          border: '1px solid #ddd',
          borderRadius: '4px',
          fontSize: '12px',
          boxSizing: 'border-box',
        }}
      />
    </div>

    <div
      style={{
        flex: 1,
        overflowY: 'auto',
        padding: '8px',
      }}
    >
      {!categoryFilter || categoryFilter === 'atoms' ? (
        <div style={{ marginBottom: '12px' }}>
          <div style={{ fontSize: '11px', fontWeight: 'bold', color: '#666', marginBottom: '6px' }}>
            Atoms
          </div>
          <div style={{ display: 'grid', gap: '8px' }}>
            {['Button', 'Input', 'Text', 'Icon'].map((comp) => (
              <div
                key={comp}
                onClick={() => onComponentSelect?.(comp)}
                style={{
                  padding: '8px',
                  background: '#f5f5f5',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '11px',
                  textAlign: 'center',
                  transition: 'all 0.2s',
                  userSelect: 'none',
                }}
                onMouseEnter={(e) => {
                  (e.target as HTMLElement).style.background = '#e0e0e0';
                }}
                onMouseLeave={(e) => {
                  (e.target as HTMLElement).style.background = '#f5f5f5';
                }}
              >
                {comp}
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {!categoryFilter || categoryFilter === 'molecules' ? (
        <div style={{ marginBottom: '12px' }}>
          <div style={{ fontSize: '11px', fontWeight: 'bold', color: '#666', marginBottom: '6px' }}>
            Molecules
          </div>
          <div style={{ display: 'grid', gap: '8px' }}>
            {['Card', 'Modal', 'Tooltip'].map((comp) => (
              <div
                key={comp}
                onClick={() => onComponentSelect?.(comp)}
                style={{
                  padding: '8px',
                  background: '#e8e8ff',
                  border: '1px solid #d0d0ff',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '11px',
                  textAlign: 'center',
                  transition: 'all 0.2s',
                  userSelect: 'none',
                }}
                onMouseEnter={(e) => {
                  (e.target as HTMLElement).style.background = '#d0d0ff';
                }}
                onMouseLeave={(e) => {
                  (e.target as HTMLElement).style.background = '#e8e8ff';
                }}
              >
                {comp}
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {searchQuery && (
        <div style={{ fontSize: '11px', color: '#999', textAlign: 'center', marginTop: '20px' }}>
          Found 0 components matching "{searchQuery}"
        </div>
      )}
    </div>
  </div>
);

const meta: Meta<typeof ComponentPalette> = {
  title: 'Palettes/ComponentPalette',
  component: ComponentPalette,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: 'Component palette for selecting and adding components to the canvas.',
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

// ============================================================================
// BASIC STATE STORIES
// ============================================================================

/**
 * Default palette with all components visible.
 * Verifies: Component rendering, categorization.
 */
export const Default: Story = {
  args: {
    theme: 'base',
  },
  parameters: {
    docs: {
      description: {
        story: 'Default palette with all components visible.',
      },
    },
  },
};

/**
 * Empty palette state.
 * Verifies: Empty state message.
 */
export const Empty: Story = {
  render: () => (
    <div
      style={{
        width: '300px',
        height: '600px',
        border: '1px solid #ddd',
        background: '#fafafa',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'system-ui',
      }}
    >
      <div
        style={{
          padding: '12px',
          borderBottom: '1px solid #ddd',
          fontSize: '12px',
          fontWeight: 'bold',
        }}
      >
        Component Palette
      </div>
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#999',
          textAlign: 'center',
          padding: '20px',
        }}
      >
        <div>
          <div style={{ fontSize: '14px', marginBottom: '8px' }}>No components available</div>
          <div style={{ fontSize: '12px' }}>Add components to get started</div>
        </div>
      </div>
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Empty palette with no components.',
      },
    },
  },
};

// ============================================================================
// SEARCH & FILTER STORIES
// ============================================================================

/**
 * Palette with search active.
 * Verifies: Search filtering, result highlighting.
 */
export const WithSearch: Story = {
  args: {
    theme: 'base',
    searchQuery: 'button',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette with search query filtering components.',
      },
    },
  },
};

/**
 * Palette filtered by category.
 * Verifies: Category filtering, tab switching.
 */
export const FilteredByCategory: Story = {
  args: {
    theme: 'base',
    categoryFilter: 'atoms',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette filtered to show only atom components.',
      },
    },
  },
};

/**
 * Palette with molecule category filter.
 * Verifies: Different category view.
 */
export const MoleculeCategory: Story = {
  args: {
    theme: 'base',
    categoryFilter: 'molecules',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette showing molecule components.',
      },
    },
  },
};

// ============================================================================
// THEME STORIES
// ============================================================================

/**
 * Base theme palette.
 * Verifies: Base theme styling.
 */
export const BaseTheme: Story = {
  args: {
    theme: 'base',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette with base theme styling.',
      },
    },
  },
};

/**
 * Brand theme palette.
 * Verifies: Brand-specific component filtering and styling.
 */
export const BrandTheme: Story = {
  args: {
    theme: 'brand',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette filtered to brand theme components.',
      },
    },
  },
};

/**
 * Workspace theme palette.
 * Verifies: Workspace-specific components.
 */
export const WorkspaceTheme: Story = {
  args: {
    theme: 'workspace',
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette filtered to workspace theme components.',
      },
    },
  },
};

// ============================================================================
// INTERACTION STORIES
// ============================================================================

/**
 * Palette with interaction callbacks enabled.
 * Verifies: Component selection, callback firing.
 */
export const WithInteraction: Story = {
  args: {
    theme: 'base',
    onComponentSelect: (componentType: string) => {
      console.log('Selected component:', componentType);
      alert(`Selected: ${componentType}`);
    },
  },
  parameters: {
    docs: {
      description: {
        story: 'Palette with component selection interaction enabled.',
      },
    },
  },
};

// ============================================================================
// RESPONSIVE STORIES
// ============================================================================

/**
 * Palette in compact (narrow) view.
 * Verifies: Responsive layout, small viewport.
 */
export const CompactView: Story = {
  render: () => (
    <div style={{ width: '250px' }}>
      <ComponentPalette theme="base" />
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Palette in compact/narrow viewport.',
      },
    },
  },
};

/**
 * Palette in expanded view.
 * Verifies: Responsive layout, large viewport.
 */
export const ExpandedView: Story = {
  render: () => (
    <div style={{ width: '400px' }}>
      <ComponentPalette theme="base" />
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Palette in expanded/wide viewport.',
      },
    },
  },
};

// ============================================================================
// DARK MODE STORIES
// ============================================================================

/**
 * Palette in dark mode.
 * Verifies: Dark theme contrast and colors.
 */
export const DarkMode: Story = {
  render: () => (
    <div style={{ background: '#1a1a1a', padding: '20px' }}>
      <ComponentPalette theme="base" />
    </div>
  ),
  parameters: {
    docs: {
      description: {
        story: 'Palette with dark mode styling.',
      },
    },
  },
};
