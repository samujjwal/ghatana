/**
 * End node component.
 *
 * @doc.type component
 * @doc.purpose End workflow node
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from 'react';
import { Handle, Position } from '@ghatana/flow-canvas';
import type { BaseNodeData } from '../../types/workflow.types';

/**
 * EndNode component.
 *
 * @param data node data
 * @returns JSX element
 */
export const EndNode: React.FC<{ data: BaseNodeData }> = ({ data }) => {
  return (
    <div className="px-4 py-2 bg-red-100 border-2 border-red-500 rounded-lg shadow-md">
      <div className="font-semibold text-red-900">{data.label || 'End'}</div>
      <Handle type="target" position={Position.Top} />
    </div>
  );
};
