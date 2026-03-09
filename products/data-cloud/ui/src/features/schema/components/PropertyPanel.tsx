/**
 * Schema-aware property panel for workflow nodes.
 *
 * <p><b>Purpose</b><br>
 * Displays and edits node configuration based on collection schema fields.
 * Shows available fields from the schema and allows mapping to node properties.
 *
 * <p><b>Features</b><br>
 * - Schema-aware field selection
 * - Field type validation
 * - Real-time configuration updates
 * - Field mapping visualization
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { PropertyPanel } from '@/features/schema/components/PropertyPanel';
 *
 * <PropertyPanel
 *   schema={collectionSchema}
 *   nodeConfig={nodeConfiguration}
 *   onChange={handleConfigChange}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Schema-based property editing
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, { useMemo } from 'react';
import clsx from 'clsx';
import type { MetaCollection, MetaField } from '../../../types/schema.types';

export interface PropertyPanelProps {
  schema: MetaCollection;
  nodeConfig?: Record<string, unknown>;
  onChange?: (config: Record<string, unknown>) => void;
  disabled?: boolean;
}

/**
 * Renders a type badge for visual field type identification.
 *
 * @param type field type
 * @returns styled badge component
 */
function TypeBadge({ type }: { type: string }) {
  const colors: Record<string, string> = {
    text: 'bg-blue-100 text-blue-800',
    email: 'bg-purple-100 text-purple-800',
    number: 'bg-green-100 text-green-800',
    boolean: 'bg-yellow-100 text-yellow-800',
    date: 'bg-pink-100 text-pink-800',
    select: 'bg-indigo-100 text-indigo-800',
    textarea: 'bg-cyan-100 text-cyan-800',
  };

  return (
    <span
      className={clsx(
        'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        colors[type] || 'bg-gray-100 text-gray-800'
      )}
    >
      {type}
    </span>
  );
}

/**
 * Property panel for viewing and editing schema-based field properties.
 *
 * @param props panel configuration
 * @returns rendered property panel
 */
export function PropertyPanel({
  schema,
  nodeConfig = {},
  onChange,
  disabled = false,
}: PropertyPanelProps) {
  const fieldGroups = useMemo(() => {
    const required: MetaField[] = [];
    const optional: MetaField[] = [];

    schema.fields.forEach((field) => {
      if (field.required) {
        required.push(field);
      } else {
        optional.push(field);
      }
    });

    return { required, optional };
  }, [schema.fields]);

  const handleFieldSelect = (fieldId: string) => {
    if (!onChange) return;

    const newConfig = { ...nodeConfig };
    if (newConfig[fieldId]) {
      delete newConfig[fieldId];
    } else {
      newConfig[fieldId] = true;
    }
    onChange(newConfig);
  };

  const selectedFields = useMemo(
    () => Object.keys(nodeConfig).filter((key) => nodeConfig[key] === true),
    [nodeConfig]
  );

  return (
    <div className="space-y-6 p-4 bg-white rounded-lg border border-gray-200">
      {/* Header */}
      <div>
        <h3 className="text-lg font-semibold text-gray-900">Schema Properties</h3>
        <p className="mt-1 text-sm text-gray-500">
          Configure which collection fields this node accesses
        </p>
      </div>

      {/* Required Fields */}
      {fieldGroups.required.length > 0 && (
        <fieldset disabled={disabled}>
          <legend className="text-sm font-medium text-gray-900 mb-3">Required Fields</legend>
          <div className="space-y-2">
            {fieldGroups.required.map((field) => (
              <label
                key={field.id}
                className="flex items-start gap-3 p-2 rounded hover:bg-gray-50 cursor-pointer"
              >
                <input
                  type="checkbox"
                  checked={selectedFields.includes(field.id)}
                  onChange={() => handleFieldSelect(field.id)}
                  disabled={disabled}
                  className="mt-1 w-4 h-4 rounded border-gray-300 focus:ring-blue-500"
                  aria-label={`Select ${field.name}`}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-gray-900">{field.name}</p>
                    <TypeBadge type={field.type} />
                  </div>
                  {field.description && (
                    <p className="mt-0.5 text-xs text-gray-500">{field.description}</p>
                  )}
                </div>
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {/* Optional Fields */}
      {fieldGroups.optional.length > 0 && (
        <fieldset disabled={disabled}>
          <legend className="text-sm font-medium text-gray-900 mb-3">Optional Fields</legend>
          <div className="space-y-2">
            {fieldGroups.optional.map((field) => (
              <label
                key={field.id}
                className="flex items-start gap-3 p-2 rounded hover:bg-gray-50 cursor-pointer"
              >
                <input
                  type="checkbox"
                  checked={selectedFields.includes(field.id)}
                  onChange={() => handleFieldSelect(field.id)}
                  disabled={disabled}
                  className="mt-1 w-4 h-4 rounded border-gray-300 focus:ring-blue-500"
                  aria-label={`Select ${field.name}`}
                />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-gray-900">{field.name}</p>
                    <TypeBadge type={field.type} />
                  </div>
                  {field.description && (
                    <p className="mt-0.5 text-xs text-gray-500">{field.description}</p>
                  )}
                </div>
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {/* Summary */}
      <div className="pt-4 border-t border-gray-200">
        <p className="text-sm text-gray-600">
          <span className="font-medium">{selectedFields.length}</span>
          {' '}of{' '}
          <span className="font-medium">{schema.fields.length}</span>
          {' '}fields selected
        </p>
      </div>

      {/* Empty state */}
      {schema.fields.length === 0 && (
        <div className="text-center py-8">
          <p className="text-sm text-gray-500">No fields available in this collection</p>
        </div>
      )}
    </div>
  );
}

