/**
 * Data Flow Visualizer Component
 *
 * Visualizes data flow connections between data sources and components.
 * Shows binding paths, directions, and error states.
 *
 * @module canvas/visualization/DataFlowVisualizer
 */

import React, { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface DataBinding {
  id: string;
  sourceId: string;
  targetId: string;
  sourcePath: string;
  targetProp: string;
  mode: 'one-way' | 'two-way' | 'one-time' | 'expression';
  hasError?: boolean;
  errorMessage?: string;
}

/**
 *
 */
export interface NodePosition {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 *
 */
export interface DataFlowVisualizerProps {
  /**
   * Data bindings to visualize
   */
  bindings: DataBinding[];

  /**
   * Node positions on canvas
   */
  nodePositions: Record<string, NodePosition>;

  /**
   * Selected binding ID
   */
  selectedBindingId?: string;

  /**
   * Callback when binding is clicked
   */
  onBindingClick?: (bindingId: string) => void;

  /**
   * Show labels on connections
   */
  showLabels?: boolean;

  /**
   * Show flow direction arrows
   */
  showArrows?: boolean;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Calculate connection path between two nodes
 */
function calculatePath(
  source: NodePosition,
  target: NodePosition,
  mode: DataBinding['mode']
): string {
  const sourceX = source.x + source.width;
  const sourceY = source.y + source.height / 2;
  const targetX = target.x;
  const targetY = target.y + target.height / 2;

  const midX = (sourceX + targetX) / 2;

  // Curved path
  return `M ${sourceX} ${sourceY} C ${midX} ${sourceY}, ${midX} ${targetY}, ${targetX} ${targetY}`;
}

/**
 * Get color based on binding mode
 */
function getBindingColor(binding: DataBinding): string {
  if (binding.hasError) return '#f44336';

  switch (binding.mode) {
    case 'one-way':
      return '#1976d2';
    case 'two-way':
      return '#9c27b0';
    case 'one-time':
      return '#4caf50';
    case 'expression':
      return '#ff9800';
    default:
      return '#757575';
  }
}

// ============================================================================
// Data Flow Visualizer Component
// ============================================================================

export const DataFlowVisualizer: React.FC<DataFlowVisualizerProps> = ({
  bindings,
  nodePositions,
  selectedBindingId,
  onBindingClick,
  showLabels = true,
  showArrows = true,
}) => {
  // Calculate SVG bounds
  const bounds = useMemo(() => {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const pos of Object.values(nodePositions)) {
      minX = Math.min(minX, pos.x);
      minY = Math.min(minY, pos.y);
      maxX = Math.max(maxX, pos.x + pos.width);
      maxY = Math.max(maxY, pos.y + pos.height);
    }

    return {
      x: minX - 50,
      y: minY - 50,
      width: maxX - minX + 100,
      height: maxY - minY + 100,
    };
  }, [nodePositions]);

  // Filter valid bindings (both source and target exist)
  const validBindings = useMemo(() => {
    return bindings.filter(
      (binding) => nodePositions[binding.sourceId] && nodePositions[binding.targetId]
    );
  }, [bindings, nodePositions]);

  return (
    <svg
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        zIndex: 1,
      }}
      viewBox={`${bounds.x} ${bounds.y} ${bounds.width} ${bounds.height}`}
    >
      <defs>
        {/* Arrow markers for different modes */}
        {['one-way', 'two-way', 'one-time', 'expression', 'error'].map((mode) => (
          <marker
            key={mode}
            id={`arrow-${mode}`}
            markerWidth="10"
            markerHeight="10"
            refX="9"
            refY="3"
            orient="auto"
            markerUnits="strokeWidth"
          >
            <path
              d="M0,0 L0,6 L9,3 z"
              fill={
                mode === 'error'
                  ? '#f44336'
                  : mode === 'one-way'
                  ? '#1976d2'
                  : mode === 'two-way'
                  ? '#9c27b0'
                  : mode === 'one-time'
                  ? '#4caf50'
                  : '#ff9800'
              }
            />
          </marker>
        ))}

        {/* Glow filter for selected binding */}
        <filter id="glow">
          <feGaussianBlur stdDeviation="4" result="coloredBlur" />
          <feMerge>
            <feMergeNode in="coloredBlur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* Render bindings */}
      {validBindings.map((binding) => {
        const source = nodePositions[binding.sourceId];
        const target = nodePositions[binding.targetId];
        const isSelected = binding.id === selectedBindingId;
        const color = getBindingColor(binding);
        const path = calculatePath(source, target, binding.mode);

        // Calculate label position (middle of path)
        const sourceX = source.x + source.width;
        const sourceY = source.y + source.height / 2;
        const targetX = target.x;
        const targetY = target.y + target.height / 2;
        const labelX = (sourceX + targetX) / 2;
        const labelY = (sourceY + targetY) / 2;

        return (
          <g key={binding.id}>
            {/* Connection path */}
            <path
              d={path}
              stroke={color}
              strokeWidth={isSelected ? 3 : 2}
              fill="none"
              strokeDasharray={binding.mode === 'one-time' ? '5,5' : undefined}
              markerEnd={
                showArrows
                  ? `url(#arrow-${binding.hasError ? 'error' : binding.mode})`
                  : undefined
              }
              filter={isSelected ? 'url(#glow)' : undefined}
              style={{
                pointerEvents: 'stroke',
                cursor: 'pointer',
                transition: 'stroke-width 0.2s',
              }}
              onClick={() => onBindingClick?.(binding.id)}
            />

            {/* Bidirectional arrow for two-way binding */}
            {binding.mode === 'two-way' && showArrows && (
              <path
                d={path}
                stroke={color}
                strokeWidth={isSelected ? 3 : 2}
                fill="none"
                markerStart={`url(#arrow-${binding.hasError ? 'error' : binding.mode})`}
                style={{ pointerEvents: 'none' }}
              />
            )}

            {/* Label */}
            {showLabels && (
              <g>
                {/* Background */}
                <rect
                  x={labelX - 60}
                  y={labelY - 12}
                  width={120}
                  height={24}
                  fill="#fff"
                  stroke={color}
                  strokeWidth={1}
                  rx={4}
                  style={{ pointerEvents: 'none' }}
                />

                {/* Text */}
                <text
                  x={labelX}
                  y={labelY + 4}
                  textAnchor="middle"
                  fontSize="11"
                  fontWeight="500"
                  fill={color}
                  style={{ pointerEvents: 'none' }}
                >
                  {binding.sourcePath} → {binding.targetProp}
                </text>
              </g>
            )}

            {/* Error indicator */}
            {binding.hasError && (
              <g>
                <circle cx={labelX} cy={labelY - 20} r="10" fill="#f44336" />
                <text
                  x={labelX}
                  y={labelY - 16}
                  textAnchor="middle"
                  fontSize="12"
                  fontWeight="bold"
                  fill="#fff"
                >
                  !
                </text>
                {binding.errorMessage && (
                  <title>{binding.errorMessage}</title>
                )}
              </g>
            )}
          </g>
        );
      })}

      {/* No bindings message */}
      {validBindings.length === 0 && (
        <text
          x={bounds.x + bounds.width / 2}
          y={bounds.y + bounds.height / 2}
          textAnchor="middle"
          fontSize="14"
          fill="#999"
        >
          No data bindings configured
        </text>
      )}
    </svg>
  );
};

// ============================================================================
// Legend Component
// ============================================================================

/**
 *
 */
export interface DataFlowLegendProps {
  /**
   * Show legend
   */
  visible?: boolean;
}

export const DataFlowLegend: React.FC<DataFlowLegendProps> = ({ visible = true }) => {
  if (!visible) return null;

  const items = [
    { label: 'One-way', color: '#1976d2', dash: false },
    { label: 'Two-way', color: '#9c27b0', dash: false },
    { label: 'One-time', color: '#4caf50', dash: true },
    { label: 'Expression', color: '#ff9800', dash: false },
    { label: 'Error', color: '#f44336', dash: false },
  ];

  return (
    <div
      style={{
        position: 'absolute',
        bottom: 16,
        right: 16,
        backgroundColor: '#fff',
        border: '1px solid #e0e0e0',
        borderRadius: 8,
        padding: 12,
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
        zIndex: 10,
      }}
    >
      <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>
        Data Flow Legend
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {items.map((item) => (
          <div key={item.label} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="24" height="2">
              <line
                x1="0"
                y1="1"
                x2="24"
                y2="1"
                stroke={item.color}
                strokeWidth="2"
                strokeDasharray={item.dash ? '3,3' : undefined}
              />
            </svg>
            <span style={{ fontSize: 11 }}>{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};
