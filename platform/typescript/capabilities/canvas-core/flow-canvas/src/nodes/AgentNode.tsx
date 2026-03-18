/**
 * AgentNode — AEP agent topology node for agent network visualization.
 *
 * Represents an AEP agent in the neural-map / agent-network canvas.
 * Supports status indicators, capability badges, and memory tier display.
 *
 * @doc.type component
 * @doc.purpose AEP agent network node for agent topology visualization
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { AgentNodeData } from '../types';

type AgentStatus = 'active' | 'idle' | 'error' | 'training';

const STATUS_STYLES: Record<AgentStatus, { border: string; dot: string; badge: string }> = {
  active: {
    border: 'border-green-500',
    dot: 'bg-green-500 animate-pulse',
    badge: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300',
  },
  idle: {
    border: 'border-gray-400',
    dot: 'bg-gray-400',
    badge: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
  },
  error: {
    border: 'border-red-500',
    dot: 'bg-red-500',
    badge: 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300',
  },
  training: {
    border: 'border-purple-500',
    dot: 'bg-purple-500 animate-pulse',
    badge: 'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300',
  },
};

const AgentNode = memo(({ data, selected }: NodeProps<AgentNodeData>) => {
  const { label, agentType, status = 'idle', capabilities = [], memoryCount } = data;
  const styles = STATUS_STYLES[status as AgentStatus] ?? STATUS_STYLES.idle;

  return (
    <div
      role="img"
      aria-label={`Agent node: ${label}, type ${agentType}, status ${status}`}
      className={[
        'rounded-xl border-2 px-4 py-3 min-w-[180px] max-w-[240px] shadow-md cursor-pointer',
        'bg-white dark:bg-gray-900',
        styles.border,
        selected ? 'ring-2 ring-indigo-400 ring-offset-2' : '',
        'transition-all duration-200 hover:shadow-lg hover:scale-105',
      ].join(' ')}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${styles.dot}`} aria-hidden="true" />
          <span className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
            {agentType}
          </span>
        </div>
        <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${styles.badge}`}>
          {status}
        </span>
      </div>

      {/* Label */}
      <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate mb-2">
        {label}
      </div>

      {/* Capabilities */}
      {capabilities.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-2">
          {capabilities.slice(0, 3).map((cap) => (
            <span
              key={cap}
              className="text-xs bg-indigo-50 dark:bg-indigo-950 text-indigo-600 dark:text-indigo-300 px-1.5 py-0.5 rounded"
            >
              {cap}
            </span>
          ))}
          {capabilities.length > 3 && (
            <span className="text-xs text-gray-400">+{capabilities.length - 3}</span>
          )}
        </div>
      )}

      {/* Memory count */}
      {memoryCount !== undefined && (
        <div className="text-xs text-gray-500 dark:text-gray-400">
          {memoryCount} memories
        </div>
      )}

      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
    </div>
  );
});

AgentNode.displayName = 'AgentNode';
export default AgentNode;
