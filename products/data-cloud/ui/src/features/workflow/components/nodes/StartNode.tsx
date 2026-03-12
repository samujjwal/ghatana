/**
 * Start node component.
 *
 * @doc.type component
 * @doc.purpose Start workflow node
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from 'react';
import { Handle, Position } from '@ghatana/flow-canvas';
import type { BaseNodeData } from '../../types/workflow.types';

/**
 * StartNode component.
 *
 * @param data node data
 * @returns JSX element
 */
export const StartNode: React.FC<{ data: BaseNodeData }> = ({ data }) => {
  return (
    <div className="px-4 py-2 bg-green-100 border-2 border-green-500 rounded-lg shadow-md">
      <div className="font-semibold text-green-900">{data.label || 'Start'}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
};
