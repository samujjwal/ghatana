/**
 * API call node component.
 *
 * @doc.type component
 * @doc.purpose API call workflow node
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from 'react';
import { Handle, Position } from '@xyflow/react';
import type { ApiCallNodeData } from '../../types/workflow.types';

/**
 * ApiCallNode component.
 *
 * @param data node data
 * @returns JSX element
 */
export const ApiCallNode: React.FC<{ data: ApiCallNodeData }> = ({ data }) => {
  return (
    <div className="px-4 py-2 bg-blue-100 border-2 border-blue-500 rounded-lg shadow-md min-w-[200px]">
      <div className="font-semibold text-blue-900">{data.label || 'API Call'}</div>
      <div className="text-xs text-blue-700 mt-1">
        {data.method} {data.url}
      </div>
      <Handle type="target" position={Position.Top} />
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
};
