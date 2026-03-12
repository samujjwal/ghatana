/**
 * Decision node component.
 *
 * @doc.type component
 * @doc.purpose Decision/branching workflow node
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from 'react';
import { Handle, Position } from '@ghatana/flow-canvas';
import type { DecisionNodeData } from '../../types/workflow.types';

/**
 * DecisionNode component.
 *
 * @param data node data
 * @returns JSX element
 */
export const DecisionNode: React.FC<{ data: DecisionNodeData }> = ({ data }) => {
  return (
    <div className="px-4 py-2 bg-yellow-100 border-2 border-yellow-500 rounded-lg shadow-md min-w-[200px]">
      <div className="font-semibold text-yellow-900">{data.label || 'Decision'}</div>
      <div className="text-xs text-yellow-700 mt-1">
        {data.conditions?.length || 0} conditions
      </div>
      <Handle type="target" position={Position.Top} />
      <Handle type="source" position={Position.Bottom} id="default" />
      {data.conditions?.map((cond, idx) => (
        <Handle
          key={cond.id}
          type="source"
          position={Position.Right}
          id={cond.id}
          style={{ top: `${30 + idx * 20}px` }}
        />
      ))}
    </div>
  );
};
