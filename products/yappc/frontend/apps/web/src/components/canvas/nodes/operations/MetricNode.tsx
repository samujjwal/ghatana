// ============================================================================
// MetricNode - Canvas node for metric visualization
// 
// Displays:
// - Metric name and description
// - Current value with trend
// - Mini chart (line/gauge)
// - Threshold indicators
// - Labels/tags
// - Data source info
// ============================================================================

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { 
  Activity, 
  BarChart2, 
  TrendingUp, 
  TrendingDown, 
  Minus,
  Clock,
  Database,
  Tag,
  RefreshCw
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// TYPES
// ============================================================================

export type MetricType = 'COUNTER' | 'GAUGE' | 'HISTOGRAM' | 'SUMMARY';

export interface MetricThreshold {
  value: number;
  color: string;
  label?: string;
}

export interface MetricNodeData {
  id: string;
  name: string;
  description?: string;
  type: MetricType;
  unit?: string;
  currentValue: number;
  previousValue?: number;
  trend?: 'up' | 'down' | 'stable';
  changePercent?: number;
  thresholds?: MetricThreshold[];
  labels?: Record<string, string>;
  historyValues?: number[];
  minValue?: number;
  maxValue?: number;
  aggregation?: string;
  refreshInterval?: number;
  lastUpdated?: string;
  dataSource?: string;
}

export type MetricNodeProps = NodeProps<MetricNodeData>;

// ============================================================================
// CONSTANTS
// ============================================================================

const METRIC_TYPE_CONFIG: Record<MetricType, { 
  label: string; 
  color: string;
  Icon: typeof Activity;
}> = {
  COUNTER: { label: 'Counter', color: 'text-blue-600', Icon: Activity },
  GAUGE: { label: 'Gauge', color: 'text-green-600', Icon: BarChart2 },
  HISTOGRAM: { label: 'Histogram', color: 'text-purple-600', Icon: BarChart2 },
  SUMMARY: { label: 'Summary', color: 'text-orange-600', Icon: Activity },
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function MiniLineChart({ values, thresholds, width = 100, height = 40 }: { 
  values: number[]; 
  thresholds?: MetricThreshold[];
  width?: number;
  height?: number;
}) {
  if (!values || values.length === 0) return null;
  
  const allValues = [...values, ...(thresholds?.map(t => t.value) || [])];
  const max = Math.max(...allValues);
  const min = Math.min(...allValues, 0);
  const range = max - min || 1;
  const padding = 4;
  const chartWidth = width - padding * 2;
  const chartHeight = height - padding * 2;
  
  const points = values.map((v, i) => {
    const x = padding + (i / (values.length - 1)) * chartWidth;
    const y = padding + chartHeight - ((v - min) / range) * chartHeight;
    return `${x},${y}`;
  }).join(' ');
  
  // Area fill points
  const areaPoints = [
    `${padding},${padding + chartHeight}`,
    ...values.map((v, i) => {
      const x = padding + (i / (values.length - 1)) * chartWidth;
      const y = padding + chartHeight - ((v - min) / range) * chartHeight;
      return `${x},${y}`;
    }),
    `${padding + chartWidth},${padding + chartHeight}`,
  ].join(' ');
  
  return (
    <svg width={width} height={height} className="inline-block">
      {/* Area fill */}
      <polygon points={areaPoints} fill="url(#gradient)" opacity="0.2" />
      
      {/* Threshold lines */}
      {thresholds?.map((threshold, index) => {
        const y = padding + chartHeight - ((threshold.value - min) / range) * chartHeight;
        return (
          <line 
            key={index}
            x1={padding} 
            y1={y} 
            x2={width - padding} 
            y2={y} 
            stroke={threshold.color} 
            strokeWidth="1" 
            strokeDasharray="3,3"
          />
        );
      })}
      
      {/* Value line */}
      <polyline 
        points={points} 
        fill="none" 
        stroke="#3b82f6" 
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      
      {/* Current value dot */}
      {values.length > 0 && (
        <circle
          cx={width - padding}
          cy={padding + chartHeight - ((values[values.length - 1] - min) / range) * chartHeight}
          r="3"
          fill="#3b82f6"
        />
      )}
      
      {/* Gradient definition */}
      <defs>
        <linearGradient id="gradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#3b82f6" />
          <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
        </linearGradient>
      </defs>
    </svg>
  );
}

function GaugeChart({ value, min = 0, max = 100, thresholds, size = 60 }: {
  value: number;
  min?: number;
  max?: number;
  thresholds?: MetricThreshold[];
  size?: number;
}) {
  const range = max - min;
  const percentage = Math.min(Math.max((value - min) / range, 0), 1);
  const angle = -135 + percentage * 270; // -135 to 135 degrees
  
  const radius = size / 2 - 6;
  const centerX = size / 2;
  const centerY = size / 2;
  
  // Determine color based on thresholds
  let color = '#22c55e'; // green by default
  if (thresholds) {
    for (const threshold of thresholds.sort((a, b) => a.value - b.value)) {
      if (value >= threshold.value) {
        color = threshold.color;
      }
    }
  }
  
  // Arc path calculation
  const startAngle = -135 * (Math.PI / 180);
  const endAngle = angle * (Math.PI / 180);
  const largeArcFlag = percentage > 0.5 ? 1 : 0;
  
  const x1 = centerX + radius * Math.cos(startAngle);
  const y1 = centerY + radius * Math.sin(startAngle);
  const x2 = centerX + radius * Math.cos(endAngle);
  const y2 = centerY + radius * Math.sin(endAngle);
  
  return (
    <svg width={size} height={size * 0.7} viewBox={`0 0 ${size} ${size * 0.7}`}>
      {/* Background arc */}
      <path
        d={`M ${centerX + radius * Math.cos(-135 * Math.PI / 180)} ${centerY + radius * Math.sin(-135 * Math.PI / 180)} 
            A ${radius} ${radius} 0 1 1 ${centerX + radius * Math.cos(135 * Math.PI / 180)} ${centerY + radius * Math.sin(135 * Math.PI / 180)}`}
        fill="none"
        stroke="#e5e7eb"
        strokeWidth="6"
        strokeLinecap="round"
      />
      {/* Value arc */}
      <path
        d={`M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2}`}
        fill="none"
        stroke={color}
        strokeWidth="6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function TrendIndicator({ trend, changePercent }: { trend?: 'up' | 'down' | 'stable'; changePercent?: number }) {
  if (!trend) return null;
  
  const config = {
    up: { Icon: TrendingUp, color: 'text-green-500', bgColor: 'bg-green-50' },
    down: { Icon: TrendingDown, color: 'text-red-500', bgColor: 'bg-red-50' },
    stable: { Icon: Minus, color: 'text-slate-400', bgColor: 'bg-slate-50' },
  };
  
  const { Icon, color, bgColor } = config[trend];
  
  return (
    <div className={cn('flex items-center gap-1 px-1.5 py-0.5 rounded text-xs', bgColor)}>
      <Icon className={cn('w-3 h-3', color)} />
      {changePercent !== undefined && (
        <span className={color}>
          {changePercent > 0 ? '+' : ''}{changePercent.toFixed(1)}%
        </span>
      )}
    </div>
  );
}

function LabelBadges({ labels }: { labels?: Record<string, string> }) {
  if (!labels || Object.keys(labels).length === 0) return null;
  
  const entries = Object.entries(labels).slice(0, 3);
  const overflow = Object.keys(labels).length - 3;
  
  return (
    <div className="flex flex-wrap gap-1">
      {entries.map(([key, value]) => (
        <span 
          key={key} 
          className="px-1.5 py-0.5 text-[9px] bg-slate-100 text-slate-600 rounded font-mono"
          title={`${key}=${value}`}
        >
          {key}={value}
        </span>
      ))}
      {overflow > 0 && (
        <span className="px-1.5 py-0.5 text-[9px] bg-slate-100 text-slate-400 rounded">
          +{overflow}
        </span>
      )}
    </div>
  );
}

function formatValue(value: number, unit?: string): string {
  if (value >= 1_000_000_000) {
    return `${(value / 1_000_000_000).toFixed(1)}B${unit ? ` ${unit}` : ''}`;
  }
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M${unit ? ` ${unit}` : ''}`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}K${unit ? ` ${unit}` : ''}`;
  }
  return `${value.toFixed(value % 1 === 0 ? 0 : 2)}${unit ? ` ${unit}` : ''}`;
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

function MetricNodeComponent({ data, selected }: MetricNodeProps) {
  const typeConfig = METRIC_TYPE_CONFIG[data.type];
  const TypeIcon = typeConfig.Icon;
  const isGauge = data.type === 'GAUGE';
  
  return (
    <div
      className={cn(
        'min-w-[220px] max-w-[280px] rounded-lg border bg-white shadow-md transition-all',
        'border-slate-200',
        selected && 'ring-2 ring-blue-400 ring-offset-2'
      )}
    >
      {/* Connection Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="alert"
        className="!w-3 !h-3 !bg-orange-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Left}
        id="dashboard"
        className="!w-3 !h-3 !bg-purple-400 !border-2 !border-white"
      />
      
      {/* Header */}
      <div className="px-3 py-2 border-b border-slate-100 bg-slate-50 rounded-t-lg">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TypeIcon className={cn('w-4 h-4', typeConfig.color)} />
            <span className={cn('text-xs font-medium', typeConfig.color)}>
              {typeConfig.label}
            </span>
          </div>
          {data.refreshInterval && (
            <div className="flex items-center gap-1 text-[10px] text-slate-400">
              <RefreshCw className="w-3 h-3" />
              <span>{data.refreshInterval}s</span>
            </div>
          )}
        </div>
      </div>
      
      {/* Metric Name */}
      <div className="px-3 py-2 border-b border-slate-100">
        <h3 className="text-sm font-medium text-slate-900 line-clamp-1">{data.name}</h3>
        {data.description && (
          <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Current Value */}
      <div className="px-3 py-3 border-b border-slate-100">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-2xl font-bold text-slate-900">
              {formatValue(data.currentValue, data.unit)}
            </div>
            {data.aggregation && (
              <div className="text-[10px] text-slate-400 mt-0.5">
                {data.aggregation}
              </div>
            )}
          </div>
          <div className="flex flex-col items-end gap-1">
            <TrendIndicator trend={data.trend} changePercent={data.changePercent} />
            {isGauge && data.thresholds && (
              <GaugeChart 
                value={data.currentValue} 
                min={data.minValue || 0}
                max={data.maxValue || 100}
                thresholds={data.thresholds}
              />
            )}
          </div>
        </div>
      </div>
      
      {/* Chart */}
      {!isGauge && data.historyValues && data.historyValues.length > 0 && (
        <div className="px-3 py-2 border-b border-slate-100 flex justify-center">
          <MiniLineChart 
            values={data.historyValues} 
            thresholds={data.thresholds}
            width={180}
            height={50}
          />
        </div>
      )}
      
      {/* Labels */}
      {data.labels && Object.keys(data.labels).length > 0 && (
        <div className="px-3 py-2 border-b border-slate-100">
          <div className="flex items-center gap-1 mb-1">
            <Tag className="w-3 h-3 text-slate-400" />
            <span className="text-[10px] text-slate-500">Labels</span>
          </div>
          <LabelBadges labels={data.labels} />
        </div>
      )}
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between text-[10px] text-slate-400">
        <div className="flex items-center gap-1">
          <Database className="w-3 h-3" />
          <span>{data.dataSource || 'prometheus'}</span>
        </div>
        {data.lastUpdated && (
          <div className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            <span>{new Date(data.lastUpdated).toLocaleTimeString()}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export const MetricNode = memo(MetricNodeComponent);
export default MetricNode;
