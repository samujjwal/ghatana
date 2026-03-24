/**
 * Template Preview Component
 *
 * Displays a preview of template layout and structure.
 *
 * @module canvas/templates/TemplatePreview
 */

import React from 'react';

import type { TemplateDefinition } from './TemplateDefinition';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface TemplatePreviewProps {
  /**
   * Template to preview
   */
  template: TemplateDefinition;

  /**
   * Show structure details
   */
  showDetails?: boolean;

  /**
   * Callback when close is clicked
   */
  onClose?: () => void;
}

// ============================================================================
// Template Preview Component
// ============================================================================

export const TemplatePreview: React.FC<TemplatePreviewProps> = ({
  template,
  showDetails = true,
  onClose,
}) => {
  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1000,
      }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '90%',
          maxWidth: 1000,
          maxHeight: '90%',
          backgroundColor: '#fff',
          borderRadius: 8,
          boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: 16,
            borderBottom: '1px solid #e0e0e0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <h2 style={{ margin: '0 0 4px', fontSize: 18, fontWeight: 600 }}>
              {template.name}
            </h2>
            <p style={{ margin: 0, fontSize: 13, color: '#666' }}>
              {template.description}
            </p>
          </div>
          {onClose && (
            <button
              onClick={onClose}
              style={{
                padding: '6px 12px',
                backgroundColor: '#f5f5f5',
                border: '1px solid #ccc',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 20,
              }}
            >
              ×
            </button>
          )}
        </div>

        {/* Content */}
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          {/* Visual Preview */}
          <div
            style={{
              flex: 1,
              padding: 24,
              backgroundColor: '#f5f5f5',
              overflow: 'auto',
            }}
          >
            <div
              style={{
                backgroundColor: '#fff',
                borderRadius: 8,
                padding: 24,
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                minHeight: 400,
              }}
            >
              {/* Simplified visual representation */}
              <div style={{ fontSize: 14, color: '#666', textAlign: 'center', padding: 40 }}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>📋</div>
                <div>Template structure with {template.nodes.length} components</div>
              </div>

              {/* Component hierarchy preview */}
              <div style={{ marginTop: 24 }}>
                {template.nodes.slice(0, 5).map((node) => (
                  <div
                    key={node.id}
                    style={{
                      padding: 12,
                      marginBottom: 8,
                      backgroundColor: '#f9f9f9',
                      border: '1px solid #e0e0e0',
                      borderRadius: 4,
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 500 }}>
                      {node.componentType}
                    </div>
                    <div style={{ fontSize: 11, color: '#666', marginTop: 2 }}>
                      {node.id}
                    </div>
                  </div>
                ))}
                {template.nodes.length > 5 && (
                  <div style={{ fontSize: 12, color: '#999', textAlign: 'center', padding: 8 }}>
                    +{template.nodes.length - 5} more components
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Details Panel */}
          {showDetails && (
            <div
              style={{
                width: 320,
                borderLeft: '1px solid #e0e0e0',
                backgroundColor: '#fafafa',
                overflow: 'auto',
              }}
            >
              <div style={{ padding: 16 }}>
                {/* Metadata */}
                <div style={{ marginBottom: 24 }}>
                  <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600 }}>
                    Template Info
                  </h3>
                  <div style={{ fontSize: 12, color: '#666' }}>
                    <div style={{ marginBottom: 8 }}>
                      <strong>Category:</strong>{' '}
                      <span style={{ textTransform: 'capitalize' }}>{template.category}</span>
                    </div>
                    <div style={{ marginBottom: 8 }}>
                      <strong>Version:</strong> {template.metadata.version}
                    </div>
                    {template.metadata.author && (
                      <div style={{ marginBottom: 8 }}>
                        <strong>Author:</strong> {template.metadata.author}
                      </div>
                    )}
                    {template.metadata.usageCount !== undefined && (
                      <div style={{ marginBottom: 8 }}>
                        <strong>Used:</strong> {template.metadata.usageCount} times
                      </div>
                    )}
                  </div>
                </div>

                {/* Tags */}
                <div style={{ marginBottom: 24 }}>
                  <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600 }}>Tags</h3>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                    {template.tags.map((tag) => (
                      <span
                        key={tag}
                        style={{
                          padding: '4px 10px',
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

                {/* Components */}
                <div style={{ marginBottom: 24 }}>
                  <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600 }}>
                    Components ({template.nodes.length})
                  </h3>
                  <div style={{ fontSize: 12 }}>
                    {Array.from(new Set(template.nodes.map((n) => n.componentType))).map(
                      (type) => (
                        <div
                          key={type}
                          style={{
                            padding: '6px 8px',
                            marginBottom: 4,
                            backgroundColor: '#fff',
                            border: '1px solid #e0e0e0',
                            borderRadius: 4,
                          }}
                        >
                          {type}
                        </div>
                      )
                    )}
                  </div>
                </div>

                {/* Data Bindings */}
                {template.bindings && template.bindings.length > 0 && (
                  <div style={{ marginBottom: 24 }}>
                    <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600 }}>
                      Data Bindings ({template.bindings.length})
                    </h3>
                    <div style={{ fontSize: 11, color: '#666' }}>
                      {template.bindings.map((binding, idx) => (
                        <div
                          key={idx}
                          style={{
                            padding: 8,
                            marginBottom: 6,
                            backgroundColor: '#fff',
                            border: '1px solid #e0e0e0',
                            borderRadius: 4,
                          }}
                        >
                          <div style={{ fontWeight: 500, marginBottom: 4 }}>
                            {binding.mode}
                          </div>
                          <div>
                            {binding.sourcePath} → {binding.targetProp}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Events */}
                {template.events && template.events.length > 0 && (
                  <div>
                    <h3 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600 }}>
                      Events ({template.events.length})
                    </h3>
                    <div style={{ fontSize: 11, color: '#666' }}>
                      {template.events.map((event, idx) => (
                        <div
                          key={idx}
                          style={{
                            padding: 8,
                            marginBottom: 6,
                            backgroundColor: '#fff',
                            border: '1px solid #e0e0e0',
                            borderRadius: 4,
                          }}
                        >
                          <div style={{ fontWeight: 500, marginBottom: 4 }}>
                            {event.sourceEvent}
                          </div>
                          <div>→ {event.targetEvent}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
