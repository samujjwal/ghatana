// ============================================================================
// ThreatNode - Security Canvas Node
//
// Displays threat detection information with severity, category, timeline,
// indicators of compromise, affected assets, and response actions.
// ============================================================================

import { memo, useMemo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import {
  ShieldAlert,
  AlertTriangle,
  Activity,
  Globe,
  Server,
  User,
  Clock,
  Target,
  Crosshair,
  Shield,
  Zap,
  CheckCircle2,
  XCircle,
  Eye,
  Lock,
  ExternalLink,
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// Types
// ============================================================================

export type ThreatSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
export type ThreatStatus = 'DETECTED' | 'INVESTIGATING' | 'CONTAINED' | 'REMEDIATED' | 'FALSE_POSITIVE';
export type ThreatCategory = 
  | 'MALWARE' 
  | 'INTRUSION' 
  | 'DATA_EXFILTRATION' 
  | 'DDOS' 
  | 'PHISHING' 
  | 'INSIDER_THREAT' 
  | 'CREDENTIAL_COMPROMISE' 
  | 'SUSPICIOUS_ACTIVITY' 
  | 'OTHER';

export interface ThreatIndicator {
  type: string;
  value: string;
  confidence: number;
  source?: string;
}

export interface ThreatTimelineEvent {
  id: string;
  timestamp: string;
  eventType: string;
  description: string;
  actor?: string;
}

export interface ThreatNodeData {
  id: string;
  title: string;
  description: string;
  severity: ThreatSeverity;
  status: ThreatStatus;
  category: ThreatCategory;
  source: string;
  sourceIp?: string;
  targetResource?: string;
  indicators?: ThreatIndicator[];
  affectedAssets?: string[];
  timeline?: ThreatTimelineEvent[];
  assignee?: {
    id: string;
    name: string;
    avatarUrl?: string;
  };
  containmentActions?: string[];
  remediationSteps?: string[];
  detectedAt: string;
  containedAt?: string;
  remediatedAt?: string;
  selected?: boolean;
  compact?: boolean;
}

// ============================================================================
// Severity Configuration
// ============================================================================

const SEVERITY_CONFIG: Record<ThreatSeverity, {
  label: string;
  color: string;
  bgColor: string;
  borderColor: string;
  pulseColor: string;
}> = {
  CRITICAL: {
    label: 'Critical',
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    borderColor: 'border-red-500',
    pulseColor: 'bg-red-400',
  },
  HIGH: {
    label: 'High',
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-500',
    pulseColor: 'bg-orange-400',
  },
  MEDIUM: {
    label: 'Medium',
    color: 'text-yellow-600',
    bgColor: 'bg-yellow-50',
    borderColor: 'border-yellow-500',
    pulseColor: 'bg-yellow-400',
  },
  LOW: {
    label: 'Low',
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-500',
    pulseColor: 'bg-blue-400',
  },
};

// ============================================================================
// Status Configuration
// ============================================================================

const STATUS_CONFIG: Record<ThreatStatus, {
  label: string;
  color: string;
  bgColor: string;
  icon: typeof Activity;
}> = {
  DETECTED: {
    label: 'Detected',
    color: 'text-red-600',
    bgColor: 'bg-red-100',
    icon: AlertTriangle,
  },
  INVESTIGATING: {
    label: 'Investigating',
    color: 'text-blue-600',
    bgColor: 'bg-blue-100',
    icon: Eye,
  },
  CONTAINED: {
    label: 'Contained',
    color: 'text-amber-600',
    bgColor: 'bg-amber-100',
    icon: Lock,
  },
  REMEDIATED: {
    label: 'Remediated',
    color: 'text-green-600',
    bgColor: 'bg-green-100',
    icon: CheckCircle2,
  },
  FALSE_POSITIVE: {
    label: 'False Positive',
    color: 'text-gray-600',
    bgColor: 'bg-gray-100',
    icon: XCircle,
  },
};

// ============================================================================
// Category Configuration
// ============================================================================

const CATEGORY_CONFIG: Record<ThreatCategory, {
  label: string;
  icon: typeof ShieldAlert;
  color: string;
}> = {
  MALWARE: { label: 'Malware', icon: Zap, color: 'text-red-500' },
  INTRUSION: { label: 'Intrusion', icon: Target, color: 'text-orange-500' },
  DATA_EXFILTRATION: { label: 'Data Exfiltration', icon: ExternalLink, color: 'text-purple-500' },
  DDOS: { label: 'DDoS Attack', icon: Globe, color: 'text-amber-500' },
  PHISHING: { label: 'Phishing', icon: ShieldAlert, color: 'text-pink-500' },
  INSIDER_THREAT: { label: 'Insider Threat', icon: User, color: 'text-indigo-500' },
  CREDENTIAL_COMPROMISE: { label: 'Credential Compromise', icon: Lock, color: 'text-cyan-500' },
  SUSPICIOUS_ACTIVITY: { label: 'Suspicious Activity', icon: Eye, color: 'text-yellow-500' },
  OTHER: { label: 'Other', icon: AlertTriangle, color: 'text-gray-500' },
};

// ============================================================================
// Helper Functions
// ============================================================================

function formatDate(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

function formatTimestamp(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getDuration(start: string, end?: string): string {
  const startDate = new Date(start).getTime();
  const endDate = end ? new Date(end).getTime() : Date.now();
  const diffMs = endDate - startDate;
  
  const hours = Math.floor(diffMs / (1000 * 60 * 60));
  const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
  
  if (hours > 24) {
    const days = Math.floor(hours / 24);
    return `${days}d ${hours % 24}h`;
  }
  if (hours > 0) return `${hours}h ${mins}m`;
  return `${mins}m`;
}

// ============================================================================
// Helper Components
// ============================================================================

function ThreatTimeline({ events, maxItems = 3 }: { events: ThreatTimelineEvent[]; maxItems?: number }) {
  const sortedEvents = useMemo(
    () => [...events].sort((a, b) => 
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    ).slice(0, maxItems),
    [events, maxItems]
  );

  return (
    <div className="space-y-2">
      {sortedEvents.map((event, index) => (
        <div key={event.id} className="flex gap-2">
          <div className="flex flex-col items-center">
            <div className="h-2 w-2 rounded-full bg-blue-500" />
            {index < sortedEvents.length - 1 && (
              <div className="w-0.5 flex-1 bg-gray-200 mt-1" />
            )}
          </div>
          <div className="flex-1 pb-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-700">{event.eventType}</span>
              <span className="text-xs text-gray-400">{formatTimestamp(event.timestamp)}</span>
            </div>
            <p className="text-xs text-gray-600 mt-0.5 line-clamp-1">{event.description}</p>
          </div>
        </div>
      ))}
    </div>
  );
}

function IndicatorBadge({ indicator }: { indicator: ThreatIndicator }) {
  const confidenceColor = indicator.confidence >= 0.8 
    ? 'text-red-600' 
    : indicator.confidence >= 0.5 
    ? 'text-amber-600' 
    : 'text-gray-600';

  return (
    <div className="flex items-center gap-2 rounded-md bg-gray-50 px-2 py-1 text-xs">
      <span className="font-medium text-gray-700">{indicator.type}:</span>
      <span className="font-mono text-gray-600 truncate max-w-[120px]">{indicator.value}</span>
      <span className={cn('font-medium', confidenceColor)}>
        {Math.round(indicator.confidence * 100)}%
      </span>
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

function ThreatNodeComponent({ data, selected }: NodeProps<ThreatNodeData>) {
  const severityConfig = SEVERITY_CONFIG[data.severity];
  const statusConfig = STATUS_CONFIG[data.status];
  const categoryConfig = CATEGORY_CONFIG[data.category];
  const StatusIcon = statusConfig.icon;
  const CategoryIcon = categoryConfig.icon;

  const isActive = data.status === 'DETECTED' || data.status === 'INVESTIGATING';
  const duration = getDuration(data.detectedAt, data.remediatedAt || data.containedAt);

  if (data.compact) {
    return (
      <div
        className={cn(
          'rounded-lg border-2 bg-white p-3 shadow-sm transition-all duration-200',
          severityConfig.borderColor,
          selected && 'ring-2 ring-blue-500 ring-offset-2',
          'min-w-[200px]'
        )}
      >
        <Handle type="target" position={Position.Left} className="!bg-gray-400" />
        <Handle type="source" position={Position.Right} className="!bg-gray-400" />

        <div className="flex items-center gap-2">
          {isActive && (
            <span className="relative flex h-2 w-2">
              <span className={cn(
                'animate-ping absolute inline-flex h-full w-full rounded-full opacity-75',
                severityConfig.pulseColor
              )} />
              <span className={cn(
                'relative inline-flex rounded-full h-2 w-2',
                severityConfig.pulseColor
              )} />
            </span>
          )}
          <CategoryIcon className={cn('h-4 w-4', categoryConfig.color)} />
          <span className="truncate text-sm font-medium text-gray-900">{data.title}</span>
        </div>
        <div className="mt-1 flex items-center justify-between">
          <span className={cn('text-xs', severityConfig.color)}>{severityConfig.label}</span>
          <span className={cn('text-xs', statusConfig.color)}>{statusConfig.label}</span>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border-2 bg-white shadow-md transition-all duration-200',
        severityConfig.borderColor,
        selected && 'ring-2 ring-blue-500 ring-offset-2',
        isActive && 'shadow-lg',
        'min-w-[340px] max-w-[420px]'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-gray-400" />
      <Handle type="source" position={Position.Right} className="!bg-gray-400" />

      {/* Header */}
      <div className={cn('rounded-t-lg px-4 py-3', severityConfig.bgColor)}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            {isActive && (
              <span className="relative flex h-3 w-3 mt-1">
                <span className={cn(
                  'animate-ping absolute inline-flex h-full w-full rounded-full opacity-75',
                  severityConfig.pulseColor
                )} />
                <span className={cn(
                  'relative inline-flex rounded-full h-3 w-3',
                  severityConfig.pulseColor
                )} />
              </span>
            )}
            <div>
              <div className="flex items-center gap-2">
                <CategoryIcon className={cn('h-5 w-5', categoryConfig.color)} />
                <span className="text-xs font-medium text-gray-500">{categoryConfig.label}</span>
              </div>
              <h3 className="font-semibold text-gray-900 leading-tight mt-1">{data.title}</h3>
            </div>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className={cn(
              'rounded-full px-2 py-0.5 text-xs font-bold',
              severityConfig.bgColor,
              severityConfig.color,
              'border',
              severityConfig.borderColor
            )}>
              {severityConfig.label}
            </span>
            <div className={cn(
              'flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium',
              statusConfig.bgColor,
              statusConfig.color
            )}>
              <StatusIcon className="h-3 w-3" />
              <span>{statusConfig.label}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-4 space-y-4">
        {/* Description */}
        <p className="text-sm text-gray-600 line-clamp-2">{data.description}</p>

        {/* Source & Target */}
        <div className="grid grid-cols-2 gap-3">
          {data.sourceIp && (
            <div className="flex items-start gap-2">
              <Globe className="h-4 w-4 text-red-400 mt-0.5" />
              <div>
                <span className="text-xs text-gray-500">Source IP</span>
                <p className="text-sm font-mono text-gray-900">{data.sourceIp}</p>
              </div>
            </div>
          )}
          {data.targetResource && (
            <div className="flex items-start gap-2">
              <Target className="h-4 w-4 text-blue-400 mt-0.5" />
              <div>
                <span className="text-xs text-gray-500">Target</span>
                <p className="text-sm text-gray-900 truncate">{data.targetResource}</p>
              </div>
            </div>
          )}
        </div>

        {/* Indicators of Compromise */}
        {data.indicators && data.indicators.length > 0 && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Indicators of Compromise</span>
            <div className="flex flex-wrap gap-2">
              {data.indicators.slice(0, 3).map((indicator, index) => (
                <IndicatorBadge key={index} indicator={indicator} />
              ))}
              {data.indicators.length > 3 && (
                <span className="text-xs text-gray-500 self-center">
                  +{data.indicators.length - 3} more
                </span>
              )}
            </div>
          </div>
        )}

        {/* Affected Assets */}
        {data.affectedAssets && data.affectedAssets.length > 0 && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Affected Assets</span>
            <div className="flex flex-wrap gap-1.5">
              {data.affectedAssets.slice(0, 4).map((asset, index) => (
                <span
                  key={index}
                  className="inline-flex items-center gap-1 rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700"
                >
                  <Server className="h-3 w-3" />
                  {asset}
                </span>
              ))}
              {data.affectedAssets.length > 4 && (
                <span className="text-xs text-gray-500">
                  +{data.affectedAssets.length - 4} more
                </span>
              )}
            </div>
          </div>
        )}

        {/* Timeline */}
        {data.timeline && data.timeline.length > 0 && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Timeline</span>
            <div className="rounded-md bg-gray-50 p-2">
              <ThreatTimeline events={data.timeline} />
            </div>
          </div>
        )}

        {/* Containment Actions */}
        {data.containmentActions && data.containmentActions.length > 0 && (
          <div className="rounded-md bg-amber-50 border border-amber-200 px-3 py-2">
            <div className="flex items-center gap-1.5 mb-1">
              <Lock className="h-3.5 w-3.5 text-amber-600" />
              <span className="text-xs font-medium text-amber-700">Containment Actions</span>
            </div>
            <ul className="text-xs text-amber-800 space-y-0.5">
              {data.containmentActions.slice(0, 2).map((action, index) => (
                <li key={index} className="flex items-start gap-1">
                  <span className="mt-1">•</span>
                  <span>{action}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Remediation Steps */}
        {data.status === 'REMEDIATED' && data.remediationSteps && data.remediationSteps.length > 0 && (
          <div className="rounded-md bg-green-50 border border-green-200 px-3 py-2">
            <div className="flex items-center gap-1.5 mb-1">
              <Shield className="h-3.5 w-3.5 text-green-600" />
              <span className="text-xs font-medium text-green-700">Remediation Complete</span>
            </div>
            <ul className="text-xs text-green-800 space-y-0.5">
              {data.remediationSteps.slice(0, 2).map((step, index) => (
                <li key={index} className="flex items-start gap-1">
                  <CheckCircle2 className="h-3 w-3 text-green-600 mt-0.5 flex-shrink-0" />
                  <span>{step}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="border-t border-gray-100 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          {/* Assignee */}
          {data.assignee ? (
            <div className="flex items-center gap-1.5">
              {data.assignee.avatarUrl ? (
                <img
                  src={data.assignee.avatarUrl}
                  alt={data.assignee.name}
                  className="h-5 w-5 rounded-full"
                />
              ) : (
                <User className="h-4 w-4 text-gray-400" />
              )}
              <span className="text-xs text-gray-600">{data.assignee.name}</span>
            </div>
          ) : (
            <span className="text-xs text-gray-400">Unassigned</span>
          )}

          {/* Duration */}
          <div className="flex items-center gap-1 text-gray-500">
            <Clock className="h-3.5 w-3.5" />
            <span className="text-xs">{duration}</span>
          </div>
        </div>

        {/* Detection Source */}
        <span className="text-xs text-gray-400">
          via {data.source}
        </span>
      </div>
    </div>
  );
}

export const ThreatNode = memo(ThreatNodeComponent);
export default ThreatNode;
