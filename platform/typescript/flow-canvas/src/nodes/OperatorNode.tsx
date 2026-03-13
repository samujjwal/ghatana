/**
 * OperatorNode — AEP pipeline operator node for pipeline DAG visualization.
 *
 * Represents a single, fine-grained operator within a pipeline stage
 * (e.g., MapOperator, FilterOperator, JoinOperator, AggregateOperator).
 * Operators are composable units that form the building blocks of stages.
 *
 * @doc.type component
 * @doc.purpose AEP pipeline operator node for pipeline DAG visualization
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { OperatorNodeData, OperatorNode as OperatorNodeType } from '../types';

type OperatorType =
  | 'MAP'
  | 'FILTER'
  | 'JOIN'
  | 'AGGREGATE'
  | 'SPLIT'
  | 'MERGE'
  | 'VALIDATE'
  | 'ENRICH'
  | 'AI'
  | string;

/** Visual config per operator category */
const OPERATOR_STYLES: Record<string, { dot: string; badge: string; icon: string }> = {
  MAP: {
    dot: 'bg-violet-500',
    badge: 'bg-violet-100 text-violet-700 dark:bg-violet-900 dark:text-violet-300',
    icon: 'M',
  },
  FILTER: {
    dot: 'bg-amber-500',
    badge: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
    icon: 'F',
  },
  JOIN: {
    dot: 'bg-blue-500',
    badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
    icon: 'J',
  },
  AGGREGATE: {
    dot: 'bg-indigo-500',
    badge: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300',
    icon: 'A',
  },
  SPLIT: {
    dot: 'bg-orange-500',
    badge: 'bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300',
    icon: '↦',
  },
  MERGE: {
    dot: 'bg-teal-500',
    badge: 'bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-300',
    icon: '⇐',
  },
  VALIDATE: {
    dot: 'bg-green-500',
    badge: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300',
    icon: '✓',
  },
  ENRICH: {
    dot: 'bg-cyan-500',
    badge: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900 dark:text-cyan-300',
    icon: '+',
  },
  AI: {
    dot: 'bg-pink-500',
    badge: 'bg-pink-100 text-pink-700 dark:bg-pink-900 dark:text-pink-300',
    icon: '✦',
  },
};

const DEFAULT_OPERATOR_STYLE = {
  dot: 'bg-gray-400',
  badge: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
  icon: '○',
};

/**
 * OperatorNode: a compact, typed operator chip within a pipeline stage.
 *
 * - Source handle on RIGHT, target handle on LEFT.
 * - Configured operators show a filled circle; unconfigured show a hollow outline.
 * - AI operators pulse to indicate inference is active.
 */
const OperatorNode = memo(({ data, selected }: NodeProps<OperatorNodeType>) => {
  const {
    label,
    operatorType = 'MAP',
    configured = false,
    status = 'idle',
    inputSchema,
    outputSchema,
  } = data;

  const styles = OPERATOR_STYLES[operatorType.toUpperCase()] ?? DEFAULT_OPERATOR_STYLE;

  const isRunning = status === 'running';
  const isError = status === 'error';
  const borderClass = isError
    ? 'border-red-500'
    : selected
    ? 'border-indigo-400'
    : configured
    ? 'border-gray-300 dark:border-gray-600'
    : 'border-dashed border-gray-400 dark:border-gray-500';

  const dotSizeClass = isRunning ? `${styles.dot} animate-pulse` : isError ? 'bg-red-500' : styles.dot;

  return (
    <div
      role="img"
      aria-label={`Operator: ${label}, type ${operatorType}, ${configured ? 'configured' : 'unconfigured'}`}
      className={[
        'rounded-lg border-2 px-3 py-2 min-w-[140px] max-w-[220px] shadow-sm cursor-pointer',
        'bg-white dark:bg-gray-900',
        borderClass,
        selected ? 'ring-2 ring-indigo-400 ring-offset-1' : '',
        'transition-all duration-200 hover:shadow-md hover:scale-105',
      ].join(' ')}
    >
      {/* Header: operator type badge + config state */}
      <div className="flex items-center justify-between mb-1">
        <span
          className={`text-xs font-bold px-1.5 py-0.5 rounded uppercase tracking-wider ${styles.badge}`}
        >
          {styles.icon}&nbsp;{operatorType}
        </span>
        <span
          className={`w-1.5 h-1.5 rounded-full ${dotSizeClass}`}
          aria-hidden="true"
          title={status}
        />
      </div>

      {/* Operator label */}
      <div className="text-xs font-semibold text-gray-800 dark:text-gray-200 truncate">
        {label}
      </div>

      {/* Schema summary (optional) */}
      {(inputSchema || outputSchema) && (
        <div className="mt-1.5 space-y-0.5">
          {inputSchema && (
            <div className="text-xs text-gray-400 truncate" title={`Input: ${inputSchema}`}>
              in: <span className="font-mono">{inputSchema}</span>
            </div>
          )}
          {outputSchema && (
            <div className="text-xs text-gray-400 truncate" title={`Output: ${outputSchema}`}>
              out: <span className="font-mono">{outputSchema}</span>
            </div>
          )}
        </div>
      )}

      {/* Unconfigured indicator */}
      {!configured && (
        <div className="mt-1.5 text-xs text-amber-600 dark:text-amber-400 font-medium">
          ⚠ unconfigured
        </div>
      )}

      {/* Handles */}
      <Handle type="target" position={Position.Left} />
      <Handle type="source" position={Position.Right} />
    </div>
  );
});

OperatorNode.displayName = 'OperatorNode';
export default OperatorNode;
