/**
 * Properties Panel Component
 *
 * Dynamic property editor for canvas nodes.
 * Integrates with Phase 3 useForm hook and ComponentRegistry metadata.
 *
 * @module canvas/properties/PropertiesPanel
 */

import React, { useMemo } from 'react';

import { TokenPicker } from './TokenPicker';
import { useForm } from '../../hooks/useForm';
import { validators } from '../../utils/validation';

import type { ComponentMetadata, PropDefinition } from '../registry/ComponentRegistry';
import type { ThemeContext } from '../renderer/ThemeApplicator';
import type { ComponentNodeData } from '../types/CanvasNode';


// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface PropertiesPanelProps {
  /**
   * Component metadata
   */
  metadata: ComponentMetadata;

  /**
   * Current node data
   */
  nodeData: ComponentNodeData;

  /**
   * Theme context for token picker
   */
  themeContext: ThemeContext;

  /**
   * Callback when properties change
   */
  onChange: (nodeData: Partial<ComponentNodeData>) => void;

  /**
   * Show advanced properties
   */
  showAdvanced?: boolean;
}

// ============================================================================
// Property Field Components
// ============================================================================

/**
 *
 */
interface PropertyFieldProps {
  definition: PropDefinition;
  value: unknown;
  onChange: (value: unknown) => void;
  themeContext?: ThemeContext;
}

const PropertyField: React.FC<PropertyFieldProps> = ({
  definition,
  value,
  onChange,
  themeContext,
}) => {
  const { name, type, label, required, options, tokenCategory } = definition;

  const commonStyles: React.CSSProperties = {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #ccc',
    borderRadius: 4,
    fontSize: 14,
  };

  // String input
  if (type === 'string') {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#d32f2f' }}> *</span>}
        </label>
        <input
          type="text"
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          style={commonStyles}
          placeholder={`Enter ${label.toLowerCase()}`}
        />
      </div>
    );
  }

  // Number input
  if (type === 'number') {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#d32f2f' }}> *</span>}
        </label>
        <input
          type="number"
          value={value || ''}
          onChange={(e) => onChange(Number(e.target.value))}
          style={commonStyles}
          placeholder={`Enter ${label.toLowerCase()}`}
        />
      </div>
    );
  }

  // Boolean (checkbox)
  if (type === 'boolean') {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={value || false}
            onChange={(e) => onChange(e.target.checked)}
            style={{ marginRight: 8 }}
          />
          <span style={{ fontSize: 14, fontWeight: 500 }}>{label}</span>
        </label>
      </div>
    );
  }

  // Select dropdown
  if (type === 'select' && options) {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#d32f2f' }}> *</span>}
        </label>
        <select
          value={value || ''}
          onChange={(e) => onChange(e.target.value)}
          style={commonStyles}
        >
          <option value="">Select {label.toLowerCase()}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
    );
  }

  // Token picker
  if (type === 'token' && themeContext) {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#d32f2f' }}> *</span>}
        </label>
        <TokenPicker
          themeContext={themeContext}
          category={tokenCategory}
          value={value}
          onChange={(tokenPath) => onChange(`$${tokenPath}`)}
        />
      </div>
    );
  }

  // Color picker
  if (type === 'color') {
    return (
      <div style={{ marginBottom: 16 }}>
        <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
          {label}
          {required && <span style={{ color: '#d32f2f' }}> *</span>}
        </label>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            type="color"
            value={value || '#000000'}
            onChange={(e) => onChange(e.target.value)}
            style={{ width: 60, height: 40, border: '1px solid #ccc', borderRadius: 4 }}
          />
          <input
            type="text"
            value={value || ''}
            onChange={(e) => onChange(e.target.value)}
            style={{ ...commonStyles, flex: 1 }}
            placeholder="#000000"
          />
        </div>
      </div>
    );
  }

  // Fallback for unsupported types
  return (
    <div style={{ marginBottom: 16 }}>
      <label style={{ display: 'block', marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
        {label}
      </label>
      <input
        type="text"
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
        style={commonStyles}
        placeholder={`Enter ${label.toLowerCase()}`}
      />
    </div>
  );
};

// ============================================================================
// Properties Panel Component
// ============================================================================

export const PropertiesPanel: React.FC<PropertiesPanelProps> = ({
  metadata,
  nodeData,
  themeContext,
  onChange,
  showAdvanced = false,
}) => {
  // Build validation rules from prop definitions
  const validationRules = useMemo(() => {
    const rules: Record<string, unknown[]> = {};

    if (metadata.propDefinitions) {
      for (const propDef of metadata.propDefinitions) {
        if (propDef.required) {
          rules[propDef.name] = [validators.required(`${propDef.label} is required`)];
        }
      }
    }

    return rules;
  }, [metadata]);

  // Use Phase 3 form hook
  const form = useForm({
    initialValues: nodeData.props || {},
    validationRules,
    onSubmit: async (values) => {
      onChange({ props: values });
    },
    validateOnChange: true,
  });

  // Handle prop changes
  const handlePropChange = (propName: string, value: unknown) => {
    form.setFieldValue(propName, value);
    // Trigger onChange immediately
    onChange({
      props: {
        ...nodeData.props,
        [propName]: value,
      },
    });
  };

  // Render property fields
  const renderFields = () => {
    if (!metadata.propDefinitions || metadata.propDefinitions.length === 0) {
      return (
        <div style={{ padding: 16, textAlign: 'center', color: '#999' }}>
          No properties available
        </div>
      );
    }

    return metadata.propDefinitions.map((propDef) => (
      <PropertyField
        key={propDef.name}
        definition={propDef}
        value={form.values[propDef.name]}
        onChange={(value) => handlePropChange(propDef.name, value)}
        themeContext={themeContext}
      />
    ));
  };

  return (
    <div style={{ padding: 16, backgroundColor: '#fafafa', height: '100%', overflowY: 'auto' }}>
      {/* Header */}
      <div style={{ marginBottom: 16 }}>
        <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
          {metadata.displayName}
        </h3>
        {metadata.description && (
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#666' }}>
            {metadata.description}
          </p>
        )}
      </div>

      {/* Properties Section */}
      <div style={{ marginBottom: 24 }}>
        <h4 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600, color: '#666' }}>
          Properties
        </h4>
        {renderFields()}
      </div>

      {/* Advanced Section */}
      {showAdvanced && (
        <div style={{ marginBottom: 24 }}>
          <h4 style={{ margin: '0 0 12px', fontSize: 14, fontWeight: 600, color: '#666' }}>
            Advanced
          </h4>
          <div style={{ padding: 12, backgroundColor: '#fff', borderRadius: 4, fontSize: 12 }}>
            <div style={{ marginBottom: 8 }}>
              <strong>Component Type:</strong> {metadata.type}
            </div>
            <div style={{ marginBottom: 8 }}>
              <strong>Category:</strong> {metadata.category}
            </div>
            {metadata.acceptsChildren && (
              <div>
                <strong>Accepts Children:</strong> Yes
              </div>
            )}
          </div>
        </div>
      )}

      {/* Validation Errors */}
      {form.touched && Object.keys(form.errors).length > 0 && (
        <div
          style={{
            padding: 12,
            backgroundColor: '#ffebee',
            border: '1px solid #ef5350',
            borderRadius: 4,
            marginTop: 16,
          }}
        >
          <strong style={{ fontSize: 14, color: '#d32f2f' }}>Validation Errors:</strong>
          <ul style={{ margin: '8px 0 0', paddingLeft: 20, fontSize: 12, color: '#d32f2f' }}>
            {Object.entries(form.errors).map(([field, error]) => (
              <li key={field}>{error}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};
