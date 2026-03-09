/**
 * Approval node component.
 *
 * @doc.type component
 * @doc.purpose Approval/human review workflow node
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from 'react';
import { Handle, Position } from '@xyflow/react';
import type { ApprovalNodeData } from '../../types/workflow.types';

/**
 * ApprovalNode component.
 *
 * @param data node data
 * @returns JSX element
 */
export const ApprovalNode: React.FC<{ data: ApprovalNodeData }> = ({ data }) => {
  return (
    <div className="px-4 py-2 bg-purple-100 border-2 border-purple-500 rounded-lg shadow-md min-w-[200px]">
      <div className="font-semibold text-purple-900">{data.label || 'Approval'}</div>
      <div className="text-xs text-purple-700 mt-1">
        {data.approvers?.length || 0} approvers
      </div>
      <Handle type="target" position={Position.Top} />
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
};
