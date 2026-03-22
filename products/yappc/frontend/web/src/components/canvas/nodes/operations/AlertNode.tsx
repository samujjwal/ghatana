// ============================================================================
// AlertNode - Canvas node for alert visualization
// 
// Displays:
// - Alert severity (Critical/Warning/Info) with color coding
// - Alert status (Firing/Resolved/Acknowledged/Silenced)
// - Condition expression and threshold
// - Current value vs threshold visualization
// - Alert history sparkline
// - Notification channels
// ============================================================================

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { 
  Bell, 
  BellOff, 
  AlertTriangle, 
  AlertCircle, 
  Info, 
  Check,
  CheckCircle2,
  Clock,
  TrendingUp,
  TrendingDown,
  Minus,
  Mail,
  MessageSquare,
  Webhook,
  Volume2,
  VolumeX
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// TYPES
// ============================================================================

export type AlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO';
export type AlertStatus = 'FIRING' | 'RESOLVED' | 'ACKNOWLEDGED' | 'SILENCED';

export interface AlertChannel {
  type: 'email' | 'slack' | 'webhook' | 'pagerduty';
  name: string;
}

export interface AlertNodeData {
  id: string;
  name: string;
  description?: string;
  severity: AlertSeverity;
  status: AlertStatus;
  metricQuery: string;
  operator: string;
  threshold: number;
  currentValue?: number;
  duration: number;
  channels: AlertChannel[];
  firedAt?: string;
  resolvedAt?: string;
  acknowledgedBy?: string;
  silencedUntil?: string;
  historyValues?: number[];
  trend?: 'up' | 'down' | 'stable';
}

export type AlertNodeProps = NodeProps<AlertNodeData>;

// ============================================================================
// CONSTANTS
// ============================================================================

const SEVERITY_CONFIG: Record<AlertSeverity, { 
  label: string; 
  color: string; 
  bgColor: string; 
  borderColor: string;
  Icon: typeof AlertTriangle;
}> = {
  CRITICAL: { 
    label: 'Critical', 
    color: 'text-red-700', 
    bgColor: 'bg-red-100', 
    borderColor: 'border-red-500',
    Icon: AlertTriangle 
  },
  WARNING: { 
    label: 'Warning', 
    color: 'text-orange-700', 
    bgColor: 'bg-orange-100', 
    borderColor: 'border-orange-500',
    Icon: AlertCircle 
  },
  INFO: { 
    label: 'Info', 
    color: 'text-blue-700', 
    bgColor: 'bg-blue-100', 
    borderColor: 'border-blue-500',
    Icon: Info 
  },
};

const STATUS_CONFIG: Record<AlertStatus, { 
  label: string; 
  color: string;
  bgColor: string;
  Icon: typeof Bell;
}> = {
  FIRING: { label: 'Firing', color: 'text-red-600', bgColor: 'bg-red-100', Icon: Bell },
  RESOLVED: { label: 'Resolved', color: 'text-green-600', bgColor: 'bg-green-100', Icon: CheckCircle2 },
  ACKNOWLEDGED: { label: 'Acknowledged', color: 'text-blue-600', bgColor: 'bg-blue-100', Icon: Check },
  SILENCED: { label: 'Silenced', color: 'text-slate-600', bgColor: 'bg-slate-100', Icon: BellOff },
};

const CHANNEL_ICONS: Record<string, typeof Mail> = {
  email: Mail,
  slack: MessageSquare,
  webhook: Webhook,
  pagerduty: Bell,
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function ThresholdGauge({ currentValue, threshold, operator, severity }: {
  currentValue?: number;
  threshold: number;
  operator: string;
  severity: AlertSeverity;
}) {
  if (currentValue === undefined) return null;
  
  const percentage = Math.min((currentValue / threshold) * 100, 150);
  const isBreached = operator === '>' 
    ? currentValue > threshold 
    : operator === '<' 
      ? currentValue < threshold 
      : currentValue === threshold;
  
  return (
    <div className="mt-2">
      <div className="flex justify-between text-[10px] text-slate-500 mb-1">
        <span>Current: {currentValue.toFixed(2)}</span>
        <span>Threshold: {threshold}</span>
      </div>
      <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
        <div 
          className={cn(
            'h-full transition-all',
            isBreached ? 'bg-red-500' : 'bg-green-500'
          )}
          style={{ width: `${Math.min(percentage, 100)}%` }}
        />
      </div>
      <div 
        className="relative h-0"
        style={{ left: `${Math.min((threshold / (Math.max(currentValue, threshold) * 1.2)) * 100, 100)}%` }}
      >
        <div className="absolute -top-2 w-0.5 h-4 bg-slate-600" />
      </div>
    </div>
  );
}

function MiniSparkline({ values, threshold }: { values: number[]; threshold: number }) {
  if (!values || values.length === 0) return null;
  
  const max = Math.max(...values, threshold * 1.2);
  const min = Math.min(...values, 0);
  const range = max - min || 1;
  const width = 60;
  const height = 20;
  
  const points = values.map((v, i) => {
    const x = (i / (values.length - 1)) * width;
    const y = height - ((v - min) / range) * height;
    return `${x},${y}`;
  }).join(' ');
  
  const thresholdY = height - ((threshold - min) / range) * height;
  
  return (
    <svg width={width} height={height} className="inline-block">
      {/* Threshold line */}
      <line 
        x1="0" 
        y1={thresholdY} 
        x2={width} 
        y2={thresholdY} 
        stroke="#ef4444" 
        strokeWidth="1" 
        strokeDasharray="2,2"
      />
      {/* Value line */}
      <polyline 
        points={points} 
        fill="none" 
        stroke="#3b82f6" 
        strokeWidth="1.5"
      />
    </svg>
  );
}

function ChannelBadges({ channels }: { channels: AlertChannel[] }) {
  return (
    <div className="flex items-center gap-1">
      {channels.slice(0, 4).map((channel, index) => {
        const Icon = CHANNEL_ICONS[channel.type] || Bell;
        return (
          <div
            key={index}
            className="w-5 h-5 rounded bg-slate-100 flex items-center justify-center"
            title={`${channel.type}: ${channel.name}`}
          >
            <Icon className="w-3 h-3 text-slate-500" />
          </div>
        );
      })}
      {channels.length > 4 && (
        <span className="text-[10px] text-slate-400">+{channels.length - 4}</span>
      )}
    </div>
  );
}

function TrendIndicator({ trend }: { trend?: 'up' | 'down' | 'stable' }) {
  if (!trend) return null;
  
  const config = {
    up: { Icon: TrendingUp, color: 'text-red-500' },
    down: { Icon: TrendingDown, color: 'text-green-500' },
    stable: { Icon: Minus, color: 'text-slate-400' },
  };
  
  const { Icon, color } = config[trend];
  return <Icon className={cn('w-4 h-4', color)} />;
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

function AlertNodeComponent({ data, selected }: AlertNodeProps) {
  const severityConfig = SEVERITY_CONFIG[data.severity];
  const statusConfig = STATUS_CONFIG[data.status];
  const SeverityIcon = severityConfig.Icon;
  const StatusIcon = statusConfig.Icon;
  const isFiring = data.status === 'FIRING';
  
  return (
    <div
      className={cn(
        'min-w-[260px] max-w-[300px] rounded-lg border-2 bg-white shadow-md transition-all',
        isFiring ? severityConfig.borderColor : 'border-slate-300',
        isFiring && 'animate-pulse',
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
        id="incident"
        className="!w-3 !h-3 !bg-red-400 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="metric"
        className="!w-3 !h-3 !bg-blue-400 !border-2 !border-white"
      />
      
      {/* Header */}
      <div className={cn('px-3 py-2 rounded-t-lg', statusConfig.bgColor)}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <SeverityIcon className={cn('w-4 h-4', severityConfig.color)} />
            <span className={cn('text-xs font-semibold', severityConfig.color)}>
              {severityConfig.label}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <StatusIcon className={cn('w-4 h-4', statusConfig.color)} />
            <span className={cn('text-xs font-medium', statusConfig.color)}>
              {statusConfig.label}
            </span>
          </div>
        </div>
      </div>
      
      {/* Alert Name */}
      <div className="px-3 py-2 border-b border-slate-100">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-slate-900 line-clamp-1">{data.name}</h3>
          <TrendIndicator trend={data.trend} />
        </div>
        {data.description && (
          <p className="text-xs text-slate-500 mt-1 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Condition */}
      <div className="px-3 py-2 border-b border-slate-100 bg-slate-50">
        <div className="flex items-center gap-2">
          <code className="text-[10px] text-slate-600 font-mono bg-white px-1.5 py-0.5 rounded border border-slate-200 line-clamp-1 flex-1">
            {data.metricQuery} {data.operator} {data.threshold}
          </code>
        </div>
        <div className="flex items-center gap-2 mt-1 text-[10px] text-slate-500">
          <Clock className="w-3 h-3" />
          <span>for {data.duration}s</span>
        </div>
      </div>
      
      {/* Threshold Gauge */}
      {data.currentValue !== undefined && (
        <div className="px-3 py-2 border-b border-slate-100">
          <ThresholdGauge 
            currentValue={data.currentValue}
            threshold={data.threshold}
            operator={data.operator}
            severity={data.severity}
          />
        </div>
      )}
      
      {/* History Sparkline */}
      {data.historyValues && data.historyValues.length > 0 && (
        <div className="px-3 py-2 border-b border-slate-100 flex items-center justify-between">
          <span className="text-[10px] text-slate-500">Last 24h</span>
          <MiniSparkline values={data.historyValues} threshold={data.threshold} />
        </div>
      )}
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between">
        <ChannelBadges channels={data.channels} />
        
        <div className="flex items-center gap-2">
          {data.silencedUntil && (
            <div className="flex items-center gap-1 text-[10px] text-slate-400">
              <VolumeX className="w-3 h-3" />
              <span>Until {new Date(data.silencedUntil).toLocaleTimeString()}</span>
            </div>
          )}
          {data.acknowledgedBy && (
            <div className="flex items-center gap-1 text-[10px] text-blue-500">
              <Check className="w-3 h-3" />
              <span>{data.acknowledgedBy}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export const AlertNode = memo(AlertNodeComponent);
export default AlertNode;
