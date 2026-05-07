/**
 * StageNode — Custom ReactFlow node for a pipeline processing stage.
 *
 * Renders a card showing the stage name, kind badge, agent count,
 * and input/output handles. Supports selection highlight and
 * drag-and-drop resize.
 *
 * @doc.type component
 * @doc.purpose Pipeline stage node
 * @doc.layer frontend
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import clsx from 'clsx';
import type { StageNodeData, StageKind } from '@/types/pipeline.types';

const KIND_COLORS: Record<StageKind, string> = {
  ingestion: 'bg-blue-100 border-blue-400 text-blue-800',
  validation: 'bg-green-100 border-green-400 text-green-800',
  transformation: 'bg-purple-100 border-purple-400 text-purple-800',
  enrichment: 'bg-amber-100 border-amber-400 text-amber-800',
  analysis: 'bg-rose-100 border-rose-400 text-rose-800',
  persistence: 'bg-teal-100 border-teal-400 text-teal-800',
  custom: 'bg-gray-100 border-gray-400 text-gray-800',
};

const KIND_ICONS: Record<StageKind, string> = {
  ingestion: '📥',
  validation: '✅',
  transformation: '🔀',
  enrichment: '✨',
  analysis: '📊',
  persistence: '💾',
  custom: '🧩',
};

function StageNodeComponent({ data, selected }: NodeProps) {
  const stageData = data as unknown as StageNodeData;
  const kind = stageData.kind ?? 'custom';
  const colors = KIND_COLORS[kind];
  const icon = KIND_ICONS[kind];

  return (
    <div
      className={clsx(
        'rounded-lg border-2 shadow-md min-w-[200px] transition-all',
        colors,
        selected && 'ring-2 ring-offset-2 ring-blue-500',
      )}
    >
      {/* Input handle */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-gray-600 !border-white !border-2"
      />

      {/* Header */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-current/10">
        <span className="text-lg" role="img" aria-label={kind}>
          {icon}
        </span>
        <div className="flex-1 min-w-0">
          <p className="font-semibold text-sm truncate">{stageData.label}</p>
          <p className="text-xs opacity-70 capitalize">{kind} stage</p>
        </div>
        <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-white/60 text-xs font-bold">
          {stageData.agentCount}
        </span>
      </div>

      {/* Agent list (collapsed) */}
      {stageData.agents.length > 0 && (
        <ul className="px-3 py-1.5 space-y-0.5">
          {stageData.agents.slice(0, 3).map((agent) => (
            <li key={agent.id} className="text-xs truncate flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-current opacity-50" />
              <span className="font-mono">{agent.agent}</span>
              {agent.role && (
                <span className="ml-auto opacity-60 text-[10px]">{agent.role}</span>
              )}
            </li>
          ))}
          {stageData.agents.length > 3 && (
            <li className="text-[10px] opacity-50">
              +{stageData.agents.length - 3} more agents
            </li>
          )}
        </ul>
      )}

      {/* Connector badges */}
      {(stageData.connectorIds?.length ?? 0) > 0 && (
        <div className="px-3 py-1 border-t border-current/10">
          <p className="text-[10px] opacity-60">
            🔌 {stageData.connectorIds!.length} connector(s)
          </p>
        </div>
      )}

      {/* Output handle */}
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-gray-600 !border-white !border-2"
      />
    </div>
  );
}

export const StageNode = memo(StageNodeComponent);
