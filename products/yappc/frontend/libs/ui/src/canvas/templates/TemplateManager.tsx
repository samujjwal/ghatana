/**
 * Template Manager Component
 *
 * UI for browsing, previewing, and instantiating canvas templates.
 *
 * @module canvas/templates/TemplateManager
 */

import React, { useState, useMemo } from 'react';

import { predefinedTemplates, searchTemplates, getTemplatesByCategory } from './predefinedTemplates';
import {
  InstantiatedTemplate,
} from './TemplateDefinition';

import type {
  TemplateDefinition,
  TemplateCategory,
  TemplateInstantiationOptions} from './TemplateDefinition';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface TemplateManagerProps {
  /**
   * Callback when template is selected for instantiation
   */
  onInstantiate: (template: TemplateDefinition, options: TemplateInstantiationOptions) => void;

  /**
   * Callback when template preview is requested
   */
  onPreview?: (template: TemplateDefinition) => void;

  /**
   * Show category filter
   */
  showCategoryFilter?: boolean;

  /**
   * Show search
   */
  showSearch?: boolean;

  /**
   * Custom templates to include
   */
  customTemplates?: TemplateDefinition[];
}

// ============================================================================
// Template Manager Component
// ============================================================================

export const TemplateManager: React.FC<TemplateManagerProps> = ({
  onInstantiate,
  onPreview,
  showCategoryFilter = true,
  showSearch = true,
  customTemplates = [],
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<TemplateCategory | 'all'>('all');
  const [selectedTemplate, setSelectedTemplate] = useState<TemplateDefinition | null>(null);

  // Combine predefined and custom templates
  const allTemplates = useMemo(
    () => [...predefinedTemplates, ...customTemplates],
    [customTemplates]
  );

  // Filter templates
  const filteredTemplates = useMemo(() => {
    let templates = allTemplates;

    // Category filter
    if (selectedCategory !== 'all') {
      templates = getTemplatesByCategory(selectedCategory);
    }

    // Search filter
    if (searchQuery.trim()) {
      templates = searchTemplates(searchQuery);
    }

    return templates;
  }, [allTemplates, selectedCategory, searchQuery]);

  // Get category counts
  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { all: allTemplates.length };
    for (const template of allTemplates) {
      counts[template.category] = (counts[template.category] || 0) + 1;
    }
    return counts;
  }, [allTemplates]);

  const categories: Array<{ value: TemplateCategory | 'all'; label: string }> = [
    { value: 'all', label: 'All Templates' },
    { value: 'authentication', label: 'Authentication' },
    { value: 'dashboard', label: 'Dashboard' },
    { value: 'form', label: 'Forms' },
    { value: 'data-display', label: 'Data Display' },
    { value: 'navigation', label: 'Navigation' },
    { value: 'settings', label: 'Settings' },
    { value: 'custom', label: 'Custom' },
  ];

  const handleInstantiate = (template: TemplateDefinition) => {
    const options: TemplateInstantiationOptions = {
      position: { x: 100, y: 100 },
      theme: 'base',
      generateIds: true,
    };
    onInstantiate(template, options);
  };

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: '#fafafa',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: 16,
          backgroundColor: '#fff',
          borderBottom: '1px solid #e0e0e0',
        }}
      >
        <h2 style={{ margin: '0 0 8px', fontSize: 18, fontWeight: 600 }}>
          Template Library
        </h2>
        <p style={{ margin: 0, fontSize: 13, color: '#666' }}>
          Choose from {allTemplates.length} pre-built templates
        </p>
      </div>

      {/* Search */}
      {showSearch && (
        <div style={{ padding: 16, backgroundColor: '#fff', borderBottom: '1px solid #e0e0e0' }}>
          <input
            type="text"
            placeholder="Search templates..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 12px',
              border: '1px solid #ccc',
              borderRadius: 4,
              fontSize: 14,
            }}
          />
        </div>
      )}

      {/* Category Filter */}
      {showCategoryFilter && (
        <div
          style={{
            padding: '12px 16px',
            backgroundColor: '#fff',
            borderBottom: '1px solid #e0e0e0',
            overflowX: 'auto',
            whiteSpace: 'nowrap',
          }}
        >
          {categories.map((cat) => (
            <button
              key={cat.value}
              onClick={() => setSelectedCategory(cat.value)}
              style={{
                padding: '6px 12px',
                marginRight: 8,
                backgroundColor: selectedCategory === cat.value ? '#1976d2' : '#f5f5f5',
                color: selectedCategory === cat.value ? '#fff' : '#000',
                border: 'none',
                borderRadius: 16,
                cursor: 'pointer',
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              {cat.label} ({categoryCounts[cat.value] || 0})
            </button>
          ))}
        </div>
      )}

      {/* Template Grid */}
      <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
        {filteredTemplates.length === 0 ? (
          <div
            style={{
              padding: 40,
              textAlign: 'center',
              color: '#999',
            }}
          >
            <div style={{ fontSize: 48, marginBottom: 16 }}>📋</div>
            <div style={{ fontSize: 16, marginBottom: 8 }}>No templates found</div>
            <div style={{ fontSize: 13 }}>Try adjusting your search or filters</div>
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: 16,
            }}
          >
            {filteredTemplates.map((template) => (
              <TemplateCard
                key={template.id}
                template={template}
                isSelected={selectedTemplate?.id === template.id}
                onSelect={() => setSelectedTemplate(template)}
                onInstantiate={() => handleInstantiate(template)}
                onPreview={() => onPreview?.(template)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Selected Template Details */}
      {selectedTemplate && (
        <div
          style={{
            padding: 16,
            backgroundColor: '#fff',
            borderTop: '2px solid #1976d2',
            boxShadow: '0 -2px 8px rgba(0,0,0,0.1)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
            <div style={{ flex: 1 }}>
              <h3 style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600 }}>
                {selectedTemplate.name}
              </h3>
              <p style={{ margin: '0 0 8px', fontSize: 13, color: '#666' }}>
                {selectedTemplate.description}
              </p>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {selectedTemplate.tags.map((tag) => (
                  <span
                    key={tag}
                    style={{
                      padding: '2px 8px',
                      backgroundColor: '#e3f2fd',
                      color: '#1976d2',
                      borderRadius: 12,
                      fontSize: 11,
                    }}
                  >
                    {tag}
                  </span>
                ))}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {onPreview && (
                <button
                  onClick={() => onPreview(selectedTemplate)}
                  style={{
                    padding: '8px 16px',
                    backgroundColor: '#f5f5f5',
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: 13,
                    fontWeight: 500,
                  }}
                >
                  Preview
                </button>
              )}
              <button
                onClick={() => handleInstantiate(selectedTemplate)}
                style={{
                  padding: '8px 16px',
                  backgroundColor: '#1976d2',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 4,
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: 500,
                }}
              >
                Use Template
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Template Card Component
// ============================================================================

/**
 *
 */
interface TemplateCardProps {
  template: TemplateDefinition;
  isSelected: boolean;
  onSelect: () => void;
  onInstantiate: () => void;
  onPreview?: () => void;
}

const TemplateCard: React.FC<TemplateCardProps> = ({
  template,
  isSelected,
  onSelect,
  onInstantiate,
  onPreview,
}) => {
  return (
    <div
      onClick={onSelect}
      style={{
        backgroundColor: '#fff',
        border: `2px solid ${isSelected ? '#1976d2' : '#e0e0e0'}`,
        borderRadius: 8,
        overflow: 'hidden',
        cursor: 'pointer',
        transition: 'all 0.2s',
        boxShadow: isSelected ? '0 4px 12px rgba(25, 118, 210, 0.2)' : '0 2px 4px rgba(0,0,0,0.1)',
      }}
    >
      {/* Preview Image */}
      <div
        style={{
          height: 140,
          backgroundColor: '#f5f5f5',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderBottom: '1px solid #e0e0e0',
        }}
      >
        {template.preview ? (
          <img
            src={template.preview}
            alt={template.name}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <div style={{ fontSize: 48, opacity: 0.3 }}>📋</div>
        )}
      </div>

      {/* Content */}
      <div style={{ padding: 12 }}>
        <h4 style={{ margin: '0 0 4px', fontSize: 14, fontWeight: 600 }}>
          {template.name}
        </h4>
        <p
          style={{
            margin: '0 0 8px',
            fontSize: 12,
            color: '#666',
            lineHeight: 1.4,
            minHeight: 32,
          }}
        >
          {template.description}
        </p>

        {/* Stats */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 8, fontSize: 11, color: '#999' }}>
          <span>{template.nodes.length} components</span>
          {template.bindings && <span>{template.bindings.length} bindings</span>}
          {template.events && <span>{template.events.length} events</span>}
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 8 }}>
          {onPreview && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onPreview();
              }}
              style={{
                flex: 1,
                padding: '6px 12px',
                backgroundColor: '#f5f5f5',
                border: '1px solid #ccc',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 12,
              }}
            >
              Preview
            </button>
          )}
          <button
            onClick={(e) => {
              e.stopPropagation();
              onInstantiate();
            }}
            style={{
              flex: 1,
              padding: '6px 12px',
              backgroundColor: '#1976d2',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            Use
          </button>
        </div>
      </div>
    </div>
  );
};
