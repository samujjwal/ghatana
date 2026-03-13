/**
 * PipelineStageNode — AEP pipeline stage node for pipeline DAG visualization.
 *
 * Represents a named stage in an AEP pipeline (e.g., INGEST, TRANSFORM,
 * ENRICH, FILTER, ROUTE, SINK). Used in the Pipeline Builder and
 * WorkflowCanvas UIs.
 *
 * @doc.type component
 * @doc.purpose AEP pipeline stage node for pipeline DAG visualization
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { PipelineStageNodeData, PipelineStageNode as PipelineStageNodeType } from '../types';

type StageType = 'INGEST' | 'TRANSFORM' | 'ENRICH' | 'FILTER' | 'ROUTE' | 'SINK' | string;

const STAGE_STYLES: Record<string, { bg: string; border: string; badge: string; icon: string }> = {
  INGEST: {
    bg: 'bg-blue-50 dark:bg-blue-950',
    border: 'border-blue-500',
    badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
    icon: '⬇',
  },
  TRANSFORM: {
    bg: 'bg-violet-50 dark:bg-violet-950',
    border: 'border-violet-500',
    badge: 'bg-violet-100 text-violet-700 dark:bg-violet-900 dark:text-violet-300',
    icon: '⟳',
  },
  ENRICH: {
    bg: 'bg-teal-50 dark:bg-teal-950',
    border: 'border-teal-500',
    badge: 'bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-300',
    icon: '✦',
  },
  FILTER: {
    bg: 'bg-amber-50 dark:bg-amber-950',
    border: 'border-amber-500',
    badge: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
    icon: '▽',
  },
  ROUTE: {
    bg: 'bg-orange-50 dark:bg-orange-950',
    border: 'border-orange-500',
    badge: 'bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300',
    icon: '⇌',
  },
  SINK: {
    bg: 'bg-slate-50 dark:bg-slate-900',
    border: 'border-slate-500',
    badge: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
    icon: '⬆',
  },
};

const DEFAULT_STAGE_STYLE = {
  bg: 'bg-gray-50 dark:bg-gray-900',
  border: 'border-gray-400',
  badge: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
  icon: '◆',
};

function getStatusDot(status: string): string {
  switch (status) {
    case 'running': return 'bg-green-500 animate-pulse';
    case 'error': return 'bg-red-500';
    case 'warning': return 'bg-yellow-500';
    case 'idle': return 'bg-gray-400';
    default: return 'bg-gray-400';
  }
}

/**
 * Pipeline stage node: a labelled, typed stage in a pipeline DAG.
 *
 * Accepts `LEFT` target handle and `RIGHT` source handle for linear pipelines.
 * Set `data.hasMultipleOutputs = true` to also render a `BOTTOM` source handle
 * for fan-out / routing stages.
 */
const PipelineStageNode = memo(({ data, selected }: NodeProps<PipelineStageNodeType>) => {
  const {
    label,
    stageType = 'TRANSFORM',
    status = 'idle',
    operatorCount,
    hasMultipleOutputs = false,
    description,
  } = data;

  const styles = STAGE_STYLES[stageType.toUpperCase()] ?? DEFAULT_STAGE_STYLE;
  const dotClass = getStatusDot(typeof status === 'string' ? status : 'idle');

  return (
    <div
      role="img"
      aria-label={`Pipeline stage: ${label}, type ${stageType}, status ${status}`}
      className={[
        'rounded-xl border-2 px-4 py-3 min-w-[180px] max-w-[260px] shadow-md cursor-pointer',
        styles.bg,
        styles.border,
        selected ? 'ring-2 ring-indigo-400 ring-offset-2' : '',
        'transition-all duration-200 hover:shadow-lg hover:scale-105',
      ].join(' ')}
    >
      {/* Header: stage type + status dot */}
      <div className="flex items-center justify-between mb-1.5">
        <div className="flex items-center gap-1.5">
          <span className="text-sm" aria-hidden="true">{styles.icon}</span>
          <span className={`text-xs font-bold uppercase tracking-wider px-1.5 py-0.5 rounded ${styles.badge}`}>
            {stageType}
          </span>
        </div>
        <span className={`w-2 h-2 rounded-full ${dotClass}`} aria-hidden="true" />
      </div>

      {/* Stage label */}
      <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {label}
      </div>

      {/* Description (optional) */}
      {description && (
        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">
          {description}
        </div>
      )}

      {/* Operator count badge */}
      {operatorCount !== undefined && operatorCount > 0 && (
        <div className="mt-2 flex items-center gap-1">
          <span className="text-xs text-gray-500 dark:text-gray-400">
            {operatorCount} operator{operatorCount !== 1 ? 's' : ''}
          </span>
        </div>
      )}

      {/* Handles */}
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
      {hasMultipleOutputs && (
        <Handle
          id="bottom-out"
          type="source"
          position={Position.Bottom}
          style={{ left: '50%' }}
        />
      )}
    </div>
  );
});

PipelineStageNode.displayName = 'PipelineStageNode';
export default PipelineStageNode;
