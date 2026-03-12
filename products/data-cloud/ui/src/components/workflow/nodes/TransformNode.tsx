import React from 'react';
import { Handle, Position, NodeProps } from '@ghatana/flow-canvas';
import type { Node } from '@ghatana/flow-canvas';

type TransformNodeData = Record<string, unknown> & {
  label?: string;
  mapping?: Record<string, string>;
};

type TransformNodeType = Node<TransformNodeData, 'transform'>;

export const TransformNode: React.FC<NodeProps<TransformNodeType>> = ({ data }) => {
  return (
    <div className="px-4 py-2 rounded-md bg-white border-2 border-purple-200">
      <div className="flex items-center">
        <div className="w-4 h-4 rounded-full bg-purple-500 mr-2"></div>
        <div className="font-bold text-purple-800">Transform</div>
      </div>
      <Handle type="target" position={Position.Left} />
      <div className="text-xs text-gray-500 mt-1">{data.label ?? 'Transform'}</div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
};

// NOTE: Module augmentation for ReactFlow node types should live in a
// centralized `types` file to avoid duplicate declaration errors across the
// monorepo. Removed local augmentation to prevent `Duplicate identifier 'NodeTypes'`.
