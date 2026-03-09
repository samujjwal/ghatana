import React from 'react';
import { NodeProps, Node, Handle, Position } from '@xyflow/react';
import { useAtom } from 'jotai';
import { selectedNodeAtom } from '@/stores/workflow.store';

type ApprovalNodeType = Node<{
  label?: string;
  config?: {
    assignee?: string;
  };
}, 'approval'>;

/**
 * Approval node component for human approval gates.
 *
 * @doc.type component
 * @doc.purpose Human approval workflow node
 * @doc.layer frontend
 */
export function ApprovalNode(props: NodeProps<ApprovalNodeType>): JSX.Element {
  const { data, id } = props;
  const [selectedNodeId] = useAtom(selectedNodeAtom);
  const isSelected = selectedNodeId === id;

  return (
    <div
      className={`px-4 py-3 rounded-lg border-2 shadow-md transition-all border-purple-300 bg-purple-50 ${
        isSelected ? 'ring-2 ring-primary-500' : ''
      }`}
    >
      <Handle type="target" position={Position.Top} />
      <div className="flex items-center gap-2">
        <span className="text-lg">👤</span>
        <div>
          <div className="text-sm font-medium text-gray-900">{data.label || 'Approval'}</div>
          <div className="text-xs text-gray-600">{data.config?.assignee || 'Unassigned'}</div>
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
