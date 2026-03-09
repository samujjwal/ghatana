/**
 * Property panel component for node configuration.
 *
 * <p><b>Purpose</b><br>
 * Displays context-aware node configuration forms.
 * Provides dynamic form rendering based on node type.
 *
 * <p><b>Architecture</b><br>
 * - Dynamic form rendering
 * - Node type-specific forms
 * - Real-time validation
 * - State management integration
 *
 * @doc.type component
 * @doc.purpose Node property editor
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useCallback } from 'react';
import { useAtom } from 'jotai';
import { selectedNodeAtom, updateNodeAtom } from '../stores/workflow.store';
import { NodeType } from '../types/workflow.types';
import { ApiCallForm } from './forms/ApiCallForm';
import { DecisionForm } from './forms/DecisionForm';
import { ApprovalForm } from './forms/ApprovalForm';

/**
 * PropertyPanel component props.
 *
 * @doc.type interface
 */
export interface PropertyPanelProps {
  readOnly?: boolean;
}

/**
 * PropertyPanel component.
 *
 * Displays node configuration form based on selected node type.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const PropertyPanel: React.FC<PropertyPanelProps> = ({ readOnly = false }) => {
  const [selectedNode] = useAtom(selectedNodeAtom);
  const [, updateNode] = useAtom(updateNodeAtom);

  /**
   * Handles property change.
   */
  const handlePropertyChange = useCallback(
    (updates: Record<string, unknown>) => {
      if (!selectedNode) return;

      updateNode(selectedNode.id, {
        data: {
          ...selectedNode.data,
          ...updates,
        },
      } as any);
    },
    [selectedNode, updateNode]
  );

  const nodeData = selectedNode?.data || {};

  if (!selectedNode) {
    return (
      <div className="flex flex-col h-full bg-white border-l border-gray-200 p-4">
        <div className="text-center text-gray-500">
          <p className="text-sm">Select a node to edit properties</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-white border-l border-gray-200 overflow-y-auto">
      {/* Header */}
      <div className="p-4 border-b border-gray-200 sticky top-0 bg-white">
        <h3 className="font-semibold text-gray-900">{(selectedNode.data as any)?.label || 'Node'}</h3>
        <p className="text-xs text-gray-500 mt-1">Type: {selectedNode.type}</p>
      </div>

      {/* Form */}
      <div className="flex-1 p-4">
        {selectedNode.type === NodeType.API_CALL && (
          <ApiCallForm data={nodeData as any} onChange={handlePropertyChange} readOnly={readOnly} />
        )}
        {selectedNode.type === NodeType.DECISION && (
          <DecisionForm data={nodeData as any} onChange={handlePropertyChange} readOnly={readOnly} />
        )}
        {selectedNode.type === NodeType.APPROVAL && (
          <ApprovalForm data={nodeData as any} onChange={handlePropertyChange} readOnly={readOnly} />
        )}
        {![NodeType.API_CALL, NodeType.DECISION, NodeType.APPROVAL].includes(
          selectedNode.type as NodeType
        ) && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Label</label>
              <input
                type="text"
                value={(selectedNode.data as any)?.label || ''}
                onChange={(e) => handlePropertyChange({ label: e.target.value })}
                disabled={readOnly}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Description</label>
              <textarea
                value={(selectedNode.data as any)?.description || ''}
                onChange={(e) => handlePropertyChange({ description: e.target.value })}
                disabled={readOnly}
                className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
                rows={3}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PropertyPanel;
