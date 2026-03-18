/**
 * DataFlowEdge — animated data-flow edge for topology visualizations.
 *
 * Renders an animated SVG path with optional throughput label and
 * direction indicator. Used for data-flow connections between topology nodes.
 *
 * @doc.type component
 * @doc.purpose Animated data-flow edge for topology canvas
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React, { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type EdgeProps,
} from '@xyflow/react';
import type { DataFlowEdgeData } from '../types';

const DataFlowEdge = memo(
  ({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
    markerEnd,
  }: EdgeProps<DataFlowEdgeData>) => {
    const [edgePath, labelX, labelY] = getBezierPath({
      sourceX,
      sourceY,
      sourcePosition,
      targetX,
      targetY,
      targetPosition,
    });

    const { label, throughput, animated = true } = data ?? {};

    const strokeColor = selected ? '#6366f1' : '#64748b';
    const strokeWidth = selected ? 2.5 : 1.5;

    return (
      <>
        <BaseEdge
          id={id}
          path={edgePath}
          markerEnd={markerEnd}
          style={{
            stroke: strokeColor,
            strokeWidth,
            strokeDasharray: animated ? '6 3' : undefined,
            animation: animated ? 'flow-dash 1.5s linear infinite' : undefined,
          }}
        />

        {(label || throughput !== undefined) && (
          <EdgeLabelRenderer>
            <div
              style={{
                position: 'absolute',
                transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
                pointerEvents: 'all',
              }}
              className="nodrag nopan"
            >
              <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded px-1.5 py-0.5 text-xs text-gray-600 dark:text-gray-300 shadow-sm whitespace-nowrap">
                {label && <span>{label}</span>}
                {throughput !== undefined && (
                  <span className={label ? ' ml-1 text-gray-400' : ''}>
                    {throughput.toLocaleString()}/s
                  </span>
                )}
              </div>
            </div>
          </EdgeLabelRenderer>
        )}

        {/* CSS animation for dash flow */}
        <style>{`
          @keyframes flow-dash {
            to { stroke-dashoffset: -18; }
          }
        `}</style>
      </>
    );
  },
);

DataFlowEdge.displayName = 'DataFlowEdge';
export default DataFlowEdge;
