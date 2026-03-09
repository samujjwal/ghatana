/**
 * Decision node form component.
 *
 * @doc.type component
 * @doc.purpose Decision node configuration form
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useState } from 'react';
import type { DecisionNodeData } from '../../types/workflow.types';

/**
 * DecisionForm component props.
 *
 * @doc.type interface
 */
interface DecisionFormProps {
  data: DecisionNodeData;
  onChange: (updates: Record<string, unknown>) => void;
  readOnly?: boolean;
}

/**
 * DecisionForm component.
 *
 * @param props component props
 * @returns JSX element
 */
export const DecisionForm: React.FC<DecisionFormProps> = ({ data, onChange, readOnly }) => {
  const [newCondLabel, setNewCondLabel] = useState('');

  const handleAddCondition = () => {
    if (!newCondLabel.trim()) return;

    const newCondition = {
      id: `cond-${Date.now()}`,
      label: newCondLabel,
      expression: '',
    };

    onChange({
      conditions: [...(data.conditions || []), newCondition],
    });
    setNewCondLabel('');
  };

  const handleRemoveCondition = (id: string) => {
    onChange({
      conditions: data.conditions?.filter((c) => c.id !== id) || [],
    });
  };

  const handleUpdateCondition = (id: string, field: string, value: string) => {
    onChange({
      conditions: data.conditions?.map((c) =>
        c.id === id ? { ...c, [field]: value } : c
      ) || [],
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Label</label>
        <input
          type="text"
          value={data.label || ''}
          onChange={(e) => onChange({ label: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Description</label>
        <textarea
          value={data.description || ''}
          onChange={(e) => onChange({ description: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
          rows={2}
        />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-3">Conditions</label>
        <div className="space-y-2">
          {data.conditions?.map((condition) => (
            <div key={condition.id} className="p-3 bg-gray-50 rounded-md border border-gray-200">
              <input
                type="text"
                value={condition.label}
                onChange={(e) => handleUpdateCondition(condition.id, 'label', e.target.value)}
                disabled={readOnly}
                placeholder="Condition label"
                className="w-full px-2 py-1 border border-gray-300 rounded text-sm mb-2 disabled:bg-gray-100"
              />
              <textarea
                value={condition.expression}
                onChange={(e) => handleUpdateCondition(condition.id, 'expression', e.target.value)}
                disabled={readOnly}
                placeholder="Expression (e.g., status == 'approved')"
                className="w-full px-2 py-1 border border-gray-300 rounded text-sm disabled:bg-gray-100"
                rows={2}
              />
              {!readOnly && (
                <button
                  onClick={() => handleRemoveCondition(condition.id)}
                  className="mt-2 text-xs text-red-600 hover:text-red-700"
                >
                  Remove
                </button>
              )}
            </div>
          ))}
        </div>

        {!readOnly && (
          <div className="mt-3 flex gap-2">
            <input
              type="text"
              value={newCondLabel}
              onChange={(e) => setNewCondLabel(e.target.value)}
              placeholder="New condition label"
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm"
              onKeyPress={(e) => e.key === 'Enter' && handleAddCondition()}
            />
            <button
              onClick={handleAddCondition}
              className="px-3 py-2 bg-blue-500 text-white rounded-md text-sm hover:bg-blue-600"
            >
              Add
            </button>
          </div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Default Branch</label>
        <select
          value={data.defaultBranch || ''}
          onChange={(e) => onChange({ defaultBranch: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        >
          <option value="">None</option>
          {data.conditions?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
};
