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
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
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

export interface AlertNodeData extends Record<string, unknown> {
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

type AlertCanvasNode = Node<AlertNodeData>;

export type AlertNodeProps = NodeProps<AlertCanvasNode>;

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
    color: 'text-destructive', 
    bgColor: 'bg-destructive-bg', 
    borderColor: 'border-destructive-border',
    Icon: AlertTriangle 
  },
  WARNING: { 
    label: 'Warning', 
    color: 'text-warning-color', 
    bgColor: 'bg-warning-bg', 
    borderColor: 'border-warning-border',
    Icon: AlertCircle 
  },
  INFO: { 
    label: 'Info', 
    color: 'text-info-color', 
    bgColor: 'bg-info-bg', 
    borderColor: 'border-info-border',
    Icon: Info 
  },
};

const STATUS_CONFIG: Record<AlertStatus, { 
  label: string; 
  color: string;
  bgColor: string;
  Icon: typeof Bell;
}> = {
  FIRING: { label: 'Firing', color: 'text-destructive', bgColor: 'bg-destructive-bg', Icon: Bell },
  RESOLVED: { label: 'Resolved', color: 'text-success-color', bgColor: 'bg-success-bg', Icon: CheckCircle2 },
  ACKNOWLEDGED: { label: 'Acknowledged', color: 'text-info-color', bgColor: 'bg-info-bg', Icon: Check },
  SILENCED: { label: 'Silenced', color: 'text-fg-muted', bgColor: 'bg-muted', Icon: BellOff },
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
      <div className="flex justify-between text-[10px] text-fg-muted mb-1">
        <span>Current: {currentValue.toFixed(2)}</span>
        <span>Threshold: {threshold}</span>
      </div>
      <div className="h-2 bg-muted rounded-full overflow-hidden">
        <div 
          className={cn(
            'h-full transition-all',
            isBreached ? 'bg-destructive-bg0' : 'bg-success-bg0'
          )}
          style={{ width: `${Math.min(percentage, 100)}%` }}
        />
      </div>
      <div 
        className="relative h-0"
        style={{ left: `${Math.min((threshold / (Math.max(currentValue, threshold) * 1.2)) * 100, 100)}%` }}
      >
        <div className="absolute -top-2 w-0.5 h-4 bg-muted" />
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
            className="w-5 h-5 rounded bg-muted flex items-center justify-center"
            title={`${channel.type}: ${channel.name}`}
          >
            <Icon className="w-3 h-3 text-fg-muted" />
          </div>
        );
      })}
      {channels.length > 4 && (
        <span className="text-[10px] text-fg-muted">+{channels.length - 4}</span>
      )}
    </div>
  );
}

function TrendIndicator({ trend }: { trend?: 'up' | 'down' | 'stable' }) {
  if (!trend) return null;
  
  const config = {
    up: { Icon: TrendingUp, color: 'text-destructive' },
    down: { Icon: TrendingDown, color: 'text-success-color' },
    stable: { Icon: Minus, color: 'text-fg-muted' },
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
        isFiring ? severityConfig.borderColor : 'border-border',
        isFiring && 'animate-pulse',
        selected && 'ring-2 ring-info-border ring-offset-2'
      )}
    >
      {/* Connection Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-muted-foreground !border-2 !border-surface"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="incident"
        className="!w-3 !h-3 !bg-destructive !border-2 !border-surface"
      />
      <Handle
        type="target"
        position={Position.Left}
        id="metric"
        className="!w-3 !h-3 !bg-info-color !border-2 !border-surface"
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
      <div className="px-3 py-2 border-b border-border">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-fg line-clamp-1">{data.name}</h3>
          <TrendIndicator trend={data.trend} />
        </div>
        {data.description && (
          <p className="text-xs text-fg-muted mt-1 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Condition */}
      <div className="px-3 py-2 border-b border-border bg-surface-muted">
        <div className="flex items-center gap-2">
          <code className="text-[10px] text-fg-muted font-mono bg-white px-1.5 py-0.5 rounded border border-border line-clamp-1 flex-1">
            {data.metricQuery} {data.operator} {data.threshold}
          </code>
        </div>
        <div className="flex items-center gap-2 mt-1 text-[10px] text-fg-muted">
          <Clock className="w-3 h-3" />
          <span>for {data.duration}s</span>
        </div>
      </div>
      
      {/* Threshold Gauge */}
      {data.currentValue !== undefined && (
        <div className="px-3 py-2 border-b border-border">
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
        <div className="px-3 py-2 border-b border-border flex items-center justify-between">
          <span className="text-[10px] text-fg-muted">Last 24h</span>
          <MiniSparkline values={data.historyValues} threshold={data.threshold} />
        </div>
      )}
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between">
        <ChannelBadges channels={data.channels} />
        
        <div className="flex items-center gap-2">
          {data.silencedUntil && (
            <div className="flex items-center gap-1 text-[10px] text-fg-muted">
              <VolumeX className="w-3 h-3" />
              <span>Until {new Date(data.silencedUntil).toLocaleTimeString()}</span>
            </div>
          )}
          {data.acknowledgedBy && (
            <div className="flex items-center gap-1 text-[10px] text-info-color">
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
