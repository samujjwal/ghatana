/**
 * HotTierNode — HOT tier topology node for Data-Cloud EventCloud visualization.
 *
 * Represents the in-memory / real-time ingestion tier. Styled in red/orange
 * to convey high-velocity, high-temperature data processing.
 *
 * @doc.type component
 * @doc.purpose HOT tier topology node for 4-tier EventCloud visualization
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { TierNodeData } from '../types';

/**
 * HOT-tier node: real-time in-memory ingestion.
 * Pulsing animation indicates live data flow.
 */
const HotTierNode = memo(({ data, selected }: NodeProps<TierNodeData>) => {
  const { label, metrics, status = 'healthy' } = data;

  const isActive = status === 'healthy' || status === 'processing';

  return (
    <div
      role="img"
      aria-label={`HOT tier node: ${label}, status ${status}`}
      className={[
        'rounded-lg border-2 px-4 py-3 min-w-[160px] shadow-md cursor-pointer',
        'bg-red-50 dark:bg-red-950',
        'border-red-500',
        selected ? 'ring-2 ring-red-400 ring-offset-2' : '',
        'transition-all duration-200 hover:shadow-lg hover:scale-105',
      ].join(' ')}
    >
      {/* Tier badge */}
      <div className="flex items-center gap-2 mb-2">
        <span
          className={[
            'w-2.5 h-2.5 rounded-full bg-red-500',
            isActive ? 'animate-pulse' : '',
          ].join(' ')}
          aria-hidden="true"
        />
        <span className="text-xs font-bold text-red-600 dark:text-red-300 uppercase tracking-wider">
          HOT
        </span>
      </div>

      {/* Label */}
      <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {label}
      </div>

      {/* Metrics */}
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

HotTierNode.displayName = 'HotTierNode';
export default HotTierNode;
