/**
 * ConnectorNode — Custom ReactFlow node for a source/sink connector.
 *
 * Renders as a compact pill showing connector type, direction badge,
 * and a single handle (source for INGRESS, target for EGRESS,
 * both for BIDIRECTIONAL).
 *
 * @doc.type component
 * @doc.purpose Connector source/sink node
 * @doc.layer frontend
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import clsx from 'clsx';
import type { ConnectorNodeData, ConnectorDirection } from '@/types/pipeline.types';

const DIRECTION_STYLES: Record<ConnectorDirection, string> = {
  INGRESS: 'bg-sky-50 border-sky-400 text-sky-800',
  EGRESS: 'bg-orange-50 border-orange-400 text-orange-800',
  BIDIRECTIONAL: 'bg-violet-50 border-violet-400 text-violet-800',
};

const DIRECTION_LABELS: Record<ConnectorDirection, string> = {
  INGRESS: 'Source',
  EGRESS: 'Sink',
  BIDIRECTIONAL: 'Bi-Dir',
};

function ConnectorNodeComponent({ data, selected }: NodeProps) {
  const connData = data as unknown as ConnectorNodeData;
  const dir = connData.direction ?? 'INGRESS';
  const colors = DIRECTION_STYLES[dir];
  const dirLabel = DIRECTION_LABELS[dir];

  return (
    <div
      className={clsx(
        'rounded-full border-2 shadow-sm px-4 py-2 flex items-center gap-2 min-w-[140px]',
        colors,
        selected && 'ring-2 ring-offset-1 ring-blue-500',
      )}
    >
      {/* Target handle for EGRESS / BIDIRECTIONAL */}
      {(dir === 'EGRESS' || dir === 'BIDIRECTIONAL') && (
        <Handle
          type="target"
          position={Position.Left}
          className="!w-2.5 !h-2.5 !bg-gray-600 !border-white !border-2"
        />
      )}

      <span className="text-lg">🔌</span>
      <div className="flex-1 min-w-0">
        <p className="font-semibold text-xs truncate">{connData.label}</p>
        <p className="text-[10px] opacity-70">
          {connData.type} · {dirLabel}
        </p>
      </div>

      {/* Source handle for INGRESS / BIDIRECTIONAL */}
      {(dir === 'INGRESS' || dir === 'BIDIRECTIONAL') && (
        <Handle
          type="source"
          position={Position.Right}
          className="!w-2.5 !h-2.5 !bg-gray-600 !border-white !border-2"
        />
      )}
    </div>
  );
}

export const ConnectorNode = memo(ConnectorNodeComponent);
