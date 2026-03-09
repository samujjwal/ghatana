import React from 'react';
import { NodeProps, Node, Handle, Position } from '@xyflow/react';
import { useAtom } from 'jotai';
import { selectedNodeAtom } from '@/stores/workflow.store';

type DecisionNodeType = Node<{
  label?: string;
}, 'decision'>;

/**
 * Decision node component for conditional branching.
 *
 * @doc.type component
 * @doc.purpose Decision/conditional workflow node
 * @doc.layer frontend
 */
export function DecisionNode(props: NodeProps<DecisionNodeType>): JSX.Element {
  const { data, id } = props;
  const [selectedNodeId] = useAtom(selectedNodeAtom);
  const isSelected = selectedNodeId === id;

  return (
    <div
      className={`px-4 py-3 rounded-lg border-2 shadow-md transition-all border-yellow-300 bg-yellow-50 ${
        isSelected ? 'ring-2 ring-primary-500' : ''
      }`}
      style={{ clipPath: 'polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%)' }}
    >
      <Handle type="target" position={Position.Top} />
      <div className="text-sm font-medium text-gray-900 text-center">{data.label || 'Decision'}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
