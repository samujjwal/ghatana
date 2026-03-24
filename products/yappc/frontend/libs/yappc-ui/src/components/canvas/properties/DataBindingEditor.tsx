/**
 * Data Binding Editor Component
 *
 * UI for configuring data bindings between components and data sources.
 *
 * @module canvas/properties/DataBindingEditor
 */

import React, { useState } from 'react';

import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface DataBindingEditorProps {
  /**
   * Current data binding configuration
   */
  binding?: ComponentNodeData['dataBinding'];

  /**
   * Available data sources
   */
  dataSources: Array<{
    id: string;
    name: string;
    type: string;
    fields: string[];
  }>;

  /**
   * Callback when binding changes
   */
  onChange: (binding: ComponentNodeData['dataBinding']) => void;
}

/**
 *
 */
type BindingMode = 'one-way' | 'two-way' | 'one-time' | 'expression';

// ============================================================================
// Data Binding Editor Component
// ============================================================================

export const DataBindingEditor: React.FC<DataBindingEditorProps> = ({
  binding,
  dataSources,
  onChange,
}) => {
  const [isEnabled, setIsEnabled] = useState(!!binding);
  const [source, setSource] = useState(binding?.source || '');
  const [mode, setMode] = useState<BindingMode>(binding?.mode || 'one-way');
  const [path, setPath] = useState(binding?.path || '');

  const handleEnableChange = (enabled: boolean) => {
    setIsEnabled(enabled);
    if (!enabled) {
      onChange(undefined);
    } else {
      onChange({
        source: source || dataSources[0]?.id || '',
        mode,
        path,
      });
    }
  };

  const handleSourceChange = (newSource: string) => {
    setSource(newSource);
    if (isEnabled) {
      onChange({
        source: newSource,
        mode,
        path,
      });
    }
  };

  const handleModeChange = (newMode: BindingMode) => {
    setMode(newMode);
    if (isEnabled) {
      onChange({
        source,
        mode: newMode,
        path,
      });
    }
  };

  const handlePathChange = (newPath: string) => {
    setPath(newPath);
    if (isEnabled) {
      onChange({
        source,
        mode,
        path: newPath,
      });
    }
  };

  const selectedSource = dataSources.find((ds) => ds.id === source);

  return (
    <div
      style={{
        padding: 16,
        backgroundColor: '#fff',
        border: '1px solid #e0e0e0',
        borderRadius: 8,
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h4 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Data Binding</h4>
        <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={isEnabled}
            onChange={(e) => handleEnableChange(e.target.checked)}
            style={{ marginRight: 8 }}
          />
          <span style={{ fontSize: 12 }}>Enable</span>
        </label>
      </div>

      {isEnabled ? (
        <>
          {/* Data Source Selection */}
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 4, fontSize: 12, fontWeight: 500 }}>
              Data Source
            </label>
            {dataSources.length === 0 ? (
              <div style={{ padding: 8, backgroundColor: '#fff3cd', borderRadius: 4, fontSize: 12 }}>
                No data sources available. Add a data source node first.
              </div>
            ) : (
              <select
                value={source}
                onChange={(e) => handleSourceChange(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  border: '1px solid #ccc',
                  borderRadius: 4,
                  fontSize: 14,
                }}
              >
                <option value="">Select data source...</option>
                {dataSources.map((ds) => (
                  <option key={ds.id} value={ds.id}>
                    {ds.name} ({ds.type})
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Binding Mode */}
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 4, fontSize: 12, fontWeight: 500 }}>
              Binding Mode
            </label>
            <select
              value={mode}
              onChange={(e) => handleModeChange(e.target.value as BindingMode)}
              style={{
                width: '100%',
                padding: '8px 12px',
                border: '1px solid #ccc',
                borderRadius: 4,
                fontSize: 14,
              }}
            >
              <option value="one-way">One-way (read-only)</option>
              <option value="two-way">Two-way (read-write)</option>
              <option value="one-time">One-time (initialize only)</option>
              <option value="expression">Expression (computed)</option>
            </select>
            <div style={{ marginTop: 4, fontSize: 11, color: '#666' }}>
              {mode === 'one-way' && 'Updates when data source changes'}
              {mode === 'two-way' && 'Updates data source when component value changes'}
              {mode === 'one-time' && 'Sets value once on initialization'}
              {mode === 'expression' && 'Computed value using custom expression'}
            </div>
          </div>

          {/* Field Path */}
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', marginBottom: 4, fontSize: 12, fontWeight: 500 }}>
              Field Path
            </label>
            <input
              type="text"
              value={path}
              onChange={(e) => handlePathChange(e.target.value)}
              placeholder="e.g., user.email"
              style={{
                width: '100%',
                padding: '8px 12px',
                border: '1px solid #ccc',
                borderRadius: 4,
                fontSize: 14,
              }}
            />
            <div style={{ marginTop: 4, fontSize: 11, color: '#666' }}>
              Use dot notation for nested fields (e.g., user.profile.name)
            </div>
          </div>

          {/* Available Fields (if source selected) */}
          {selectedSource && selectedSource.fields.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 4 }}>
                Available Fields:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {selectedSource.fields.map((field) => (
                  <button
                    key={field}
                    onClick={() => handlePathChange(field)}
                    style={{
                      padding: '4px 8px',
                      fontSize: 11,
                      backgroundColor: path === field ? '#1976d2' : '#e0e0e0',
                      color: path === field ? '#fff' : '#000',
                      border: 'none',
                      borderRadius: 4,
                      cursor: 'pointer',
                    }}
                  >
                    {field}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Preview */}
          {source && path && (
            <div
              style={{
                marginTop: 16,
                padding: 12,
                backgroundColor: '#e3f2fd',
                borderRadius: 4,
                fontSize: 12,
              }}
            >
              <strong>Binding:</strong>{' '}
              <code style={{ backgroundColor: '#fff', padding: '2px 6px', borderRadius: 2 }}>
                {source}.{path}
              </code>{' '}
              ({mode})
            </div>
          )}
        </>
      ) : (
        <div style={{ padding: 16, textAlign: 'center', color: '#999', fontSize: 12 }}>
          Data binding is disabled. Enable it to configure.
        </div>
      )}
    </div>
  );
};
