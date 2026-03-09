/**
 * Approval node form component.
 *
 * @doc.type component
 * @doc.purpose Approval node configuration form
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useState } from 'react';
import type { ApprovalNodeData } from '../../types/workflow.types';

/**
 * ApprovalForm component props.
 *
 * @doc.type interface
 */
interface ApprovalFormProps {
  data: ApprovalNodeData;
  onChange: (updates: Record<string, unknown>) => void;
  readOnly?: boolean;
}

/**
 * ApprovalForm component.
 *
 * @param props component props
 * @returns JSX element
 */
export const ApprovalForm: React.FC<ApprovalFormProps> = ({ data, onChange, readOnly }) => {
  const [newApprover, setNewApprover] = useState('');

  const handleAddApprover = () => {
    if (!newApprover.trim()) return;

    onChange({
      approvers: [...(data.approvers || []), newApprover],
    });
    setNewApprover('');
  };

  const handleRemoveApprover = (approver: string) => {
    onChange({
      approvers: data.approvers?.filter((a) => a !== approver) || [],
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
        <label className="block text-sm font-medium text-gray-700 mb-3">Approvers</label>
        <div className="space-y-2">
          {data.approvers?.map((approver) => (
            <div
              key={approver}
              className="flex items-center justify-between p-2 bg-gray-50 rounded border border-gray-200"
            >
              <span className="text-sm">{approver}</span>
              {!readOnly && (
                <button
                  onClick={() => handleRemoveApprover(approver)}
                  className="text-xs text-red-600 hover:text-red-700"
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
              type="email"
              value={newApprover}
              onChange={(e) => setNewApprover(e.target.value)}
              placeholder="approver@example.com"
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm"
              onKeyPress={(e) => e.key === 'Enter' && handleAddApprover()}
            />
            <button
              onClick={handleAddApprover}
              className="px-3 py-2 bg-blue-500 text-white rounded-md text-sm hover:bg-blue-600"
            >
              Add
            </button>
          </div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-2">Timeout (minutes)</label>
        <input
          type="number"
          value={data.timeout || 24 * 60}
          onChange={(e) => onChange({ timeout: parseInt(e.target.value) })}
          disabled={readOnly}
          min="1"
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        />
      </div>

      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="autoApprove"
          checked={data.autoApprove || false}
          onChange={(e) => onChange({ autoApprove: e.target.checked })}
          disabled={readOnly}
          className="rounded border-gray-300"
        />
        <label htmlFor="autoApprove" className="text-sm text-gray-700">
          Auto-approve if no response
        </label>
      </div>
    </div>
  );
};
