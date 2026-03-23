/**
 * ColdTierNode — COLD tier topology node for Data-Cloud EventCloud visualization.
 *
 * Represents the block-storage / long-term analytical tier. Styled in blue
 * to convey archived-but-queryable data at scale.
 *
 * @doc.type component
 * @doc.purpose COLD tier topology node for 4-tier EventCloud visualization
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { TierNodeData } from '../types';

const ColdTierNode = memo(({ data, selected }: NodeProps<TierNodeData>) => {
  const { label, metrics, status = 'healthy' } = data;

  return (
    <div
      role="img"
      aria-label={`COLD tier node: ${label}, status ${status}`}
      className={[
        'rounded-lg border-2 px-4 py-3 min-w-[160px] shadow-md cursor-pointer',
        'bg-blue-50 dark:bg-blue-950',
        'border-blue-500',
        selected ? 'ring-2 ring-blue-400 ring-offset-2' : '',
        'transition-all duration-200 hover:shadow-lg hover:scale-105',
      ].join(' ')}
    >
      <div className="flex items-center gap-2 mb-2">
        <span className="w-2.5 h-2.5 rounded-full bg-blue-500" aria-hidden="true" />
        <span className="text-xs font-bold text-blue-600 dark:text-blue-300 uppercase tracking-wider">
          COLD
        </span>
      </div>

      <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {label}
      </div>

      {metrics && (
        <div className="mt-2 space-y-0.5">
          {metrics.throughput !== undefined && (
            <div className="text-xs text-gray-500 dark:text-gray-400">
              {metrics.throughput.toLocaleString()} ev/s
            </div>
          )}
          {metrics.latencyMs !== undefined && (
            <div className="text-xs text-gray-500 dark:text-gray-400">
              {metrics.latencyMs}ms p99
            </div>
          )}
        </div>
      )}

      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
    </div>
  );
});

ColdTierNode.displayName = 'ColdTierNode';
export default ColdTierNode;
