/**
 * Validation Rule Editor Component
 *
 * UI for configuring validation rules for form components.
 *
 * @module canvas/properties/ValidationRuleEditor
 */

import React, { useState } from 'react';

import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface ValidationRuleEditorProps {
  /**
   * Current validation configuration
   */
  validation?: ComponentNodeData['validation'];

  /**
   * Callback when validation changes
   */
  onChange: (validation: ComponentNodeData['validation']) => void;
}

/**
 *
 */
interface ValidationRule {
  type: string;
  params?: unknown[];
  message?: string;
}

/**
 *
 */
type RuleType =
  | 'required'
  | 'email'
  | 'minLength'
  | 'maxLength'
  | 'min'
  | 'max'
  | 'pattern'
  | 'url'
  | 'phone'
  | 'custom';

/**
 *
 */
interface RuleDefinition {
  type: RuleType;
  label: string;
  description: string;
  params?: Array<{
    name: string;
    type: 'string' | 'number';
    placeholder?: string;
  }>;
  defaultMessage: string;
}

const RULE_DEFINITIONS: RuleDefinition[] = [
  {
    type: 'required',
    label: 'Required',
    description: 'Field must have a value',
    defaultMessage: 'This field is required',
  },
  {
    type: 'email',
    label: 'Email',
    description: 'Must be a valid email address',
    defaultMessage: 'Please enter a valid email address',
  },
  {
    type: 'minLength',
    label: 'Minimum Length',
    description: 'Minimum number of characters',
    params: [{ name: 'min', type: 'number', placeholder: 'Enter minimum length' }],
    defaultMessage: 'Must be at least {0} characters',
  },
  {
    type: 'maxLength',
    label: 'Maximum Length',
    description: 'Maximum number of characters',
    params: [{ name: 'max', type: 'number', placeholder: 'Enter maximum length' }],
    defaultMessage: 'Must be at most {0} characters',
  },
  {
    type: 'min',
    label: 'Minimum Value',
    description: 'Minimum numeric value',
    params: [{ name: 'min', type: 'number', placeholder: 'Enter minimum value' }],
    defaultMessage: 'Must be at least {0}',
  },
  {
    type: 'max',
    label: 'Maximum Value',
    description: 'Maximum numeric value',
    params: [{ name: 'max', type: 'number', placeholder: 'Enter maximum value' }],
    defaultMessage: 'Must be at most {0}',
  },
  {
    type: 'pattern',
    label: 'Pattern (Regex)',
    description: 'Must match regular expression',
    params: [{ name: 'pattern', type: 'string', placeholder: 'Enter regex pattern' }],
    defaultMessage: 'Invalid format',
  },
  {
    type: 'url',
    label: 'URL',
    description: 'Must be a valid URL',
    defaultMessage: 'Please enter a valid URL',
  },
  {
    type: 'phone',
    label: 'Phone',
    description: 'Must be a valid phone number',
    defaultMessage: 'Please enter a valid phone number',
  },
];

// ============================================================================
// Validation Rule Editor Component
// ============================================================================

export const ValidationRuleEditor: React.FC<ValidationRuleEditorProps> = ({
  validation,
  onChange,
}) => {
  const [rules, setRules] = useState<ValidationRule[]>(validation?.rules || []);
  const [selectedType, setSelectedType] = useState<RuleType>('required');

  const handleAddRule = () => {
    const ruleDef = RULE_DEFINITIONS.find((r) => r.type === selectedType);
    if (!ruleDef) return;

    const newRule: ValidationRule = {
      type: selectedType,
      message: ruleDef.defaultMessage,
      params: ruleDef.params ? [] : undefined,
    };

    const updatedRules = [...rules, newRule];
    setRules(updatedRules);
    onChange({ rules: updatedRules });
  };

  const handleRemoveRule = (index: number) => {
    const updatedRules = rules.filter((_, i) => i !== index);
    setRules(updatedRules);
    onChange(updatedRules.length > 0 ? { rules: updatedRules } : undefined);
  };

  const handleUpdateRule = (index: number, updates: Partial<ValidationRule>) => {
    const updatedRules = rules.map((rule, i) => (i === index ? { ...rule, ...updates } : rule));
    setRules(updatedRules);
    onChange({ rules: updatedRules });
  };

  const getRuleDefinition = (type: string): RuleDefinition | undefined => {
    return RULE_DEFINITIONS.find((r) => r.type === type);
  };

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
      <div style={{ marginBottom: 16 }}>
        <h4 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Validation Rules</h4>
        <p style={{ margin: '4px 0 0', fontSize: 11, color: '#666' }}>
          Add rules to validate user input
        </p>
      </div>

      {/* Add Rule Section */}
      <div
        style={{
          marginBottom: 16,
          padding: 12,
          backgroundColor: '#f5f5f5',
          borderRadius: 4,
        }}
      >
        <label style={{ display: 'block', marginBottom: 8, fontSize: 12, fontWeight: 500 }}>
          Add Validation Rule
        </label>
        <div style={{ display: 'flex', gap: 8 }}>
          <select
            value={selectedType}
            onChange={(e) => setSelectedType(e.target.value as RuleType)}
            style={{
              flex: 1,
              padding: '6px 8px',
              border: '1px solid #ccc',
              borderRadius: 4,
              fontSize: 13,
            }}
          >
            {RULE_DEFINITIONS.map((ruleDef) => (
              <option key={ruleDef.type} value={ruleDef.type}>
                {ruleDef.label}
              </option>
            ))}
          </select>
          <button
            onClick={handleAddRule}
            style={{
              padding: '6px 16px',
              backgroundColor: '#1976d2',
              color: '#fff',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            Add
          </button>
        </div>
      </div>

      {/* Rules List */}
      {rules.length === 0 ? (
        <div
          style={{
            padding: 16,
            textAlign: 'center',
            color: '#999',
            fontSize: 12,
            backgroundColor: '#fafafa',
            borderRadius: 4,
          }}
        >
          No validation rules. Add one to get started.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {rules.map((rule, index) => {
            const ruleDef = getRuleDefinition(rule.type);
            if (!ruleDef) return null;

            return (
              <div
                key={index}
                style={{
                  padding: 12,
                  backgroundColor: '#f9f9f9',
                  border: '1px solid #e0e0e0',
                  borderRadius: 4,
                }}
              >
                {/* Rule Header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{ruleDef.label}</div>
                    <div style={{ fontSize: 11, color: '#666' }}>{ruleDef.description}</div>
                  </div>
                  <button
                    onClick={() => handleRemoveRule(index)}
                    style={{
                      padding: '4px 8px',
                      backgroundColor: '#ef5350',
                      color: '#fff',
                      border: 'none',
                      borderRadius: 4,
                      cursor: 'pointer',
                      fontSize: 11,
                    }}
                  >
                    Remove
                  </button>
                </div>

                {/* Rule Parameters */}
                {ruleDef.params && ruleDef.params.length > 0 && (
                  <div style={{ marginBottom: 8 }}>
                    {ruleDef.params.map((param, paramIndex) => (
                      <div key={param.name} style={{ marginBottom: 8 }}>
                        <label style={{ display: 'block', marginBottom: 4, fontSize: 11 }}>
                          {param.name}
                        </label>
                        <input
                          type={param.type === 'number' ? 'number' : 'text'}
                          value={rule.params?.[paramIndex] || ''}
                          onChange={(e) => {
                            const newParams = [...(rule.params || [])];
                            newParams[paramIndex] =
                              param.type === 'number' ? Number(e.target.value) : e.target.value;
                            handleUpdateRule(index, { params: newParams });
                          }}
                          placeholder={param.placeholder}
                          style={{
                            width: '100%',
                            padding: '6px 8px',
                            border: '1px solid #ccc',
                            borderRadius: 4,
                            fontSize: 12,
                          }}
                        />
                      </div>
                    ))}
                  </div>
                )}

                {/* Error Message */}
                <div>
                  <label style={{ display: 'block', marginBottom: 4, fontSize: 11 }}>
                    Error Message
                  </label>
                  <input
                    type="text"
                    value={rule.message || ruleDef.defaultMessage}
                    onChange={(e) => handleUpdateRule(index, { message: e.target.value })}
                    placeholder="Enter error message"
                    style={{
                      width: '100%',
                      padding: '6px 8px',
                      border: '1px solid #ccc',
                      borderRadius: 4,
                      fontSize: 12,
                    }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Summary */}
      {rules.length > 0 && (
        <div
          style={{
            marginTop: 16,
            padding: 8,
            backgroundColor: '#e8f5e9',
            borderRadius: 4,
            fontSize: 11,
            color: '#2e7d32',
          }}
        >
          ✓ {rules.length} validation rule{rules.length !== 1 ? 's' : ''} configured
        </div>
      )}
    </div>
  );
};
