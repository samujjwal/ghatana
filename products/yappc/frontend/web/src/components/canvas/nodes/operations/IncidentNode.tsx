// ============================================================================
// IncidentNode - Canvas node for incident management visualization
// 
// Displays:
// - Incident severity (SEV1-4) with color coding
// - Status timeline (detected → triaging → mitigating → resolved)
// - Commander and responders
// - Affected services
// - Customer impact
// - Duration and timestamps
// ============================================================================

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { 
  AlertTriangle, 
  AlertOctagon, 
  AlertCircle, 
  Info, 
  Clock, 
  Users, 
  Server, 
  UserCircle,
  CheckCircle2,
  XCircle,
  Timer,
  MessageSquare,
  FileText
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// TYPES
// ============================================================================

export type IncidentSeverity = 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';

export type IncidentStatus = 
  | 'DETECTED'
  | 'TRIAGING'
  | 'INVESTIGATING'
  | 'MITIGATING'
  | 'RESOLVED'
  | 'POST_MORTEM'
  | 'CLOSED';

export interface IncidentResponder {
  id: string;
  name: string;
  role: string;
  avatarUrl?: string;
}

export interface IncidentNodeData {
  id: string;
  number: number;
  title: string;
  description?: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  commander?: {
    id: string;
    name: string;
    avatarUrl?: string;
  };
  responders: IncidentResponder[];
  affectedServices: string[];
  customerImpacted: boolean;
  usersAffected?: number;
  timelineEventsCount: number;
  detectedAt: string;
  acknowledgedAt?: string;
  mitigatedAt?: string;
  resolvedAt?: string;
  hasPostMortem: boolean;
}

export type IncidentNodeProps = NodeProps<IncidentNodeData>;

// ============================================================================
// CONSTANTS
// ============================================================================

const SEVERITY_CONFIG: Record<IncidentSeverity, { 
  label: string; 
  color: string; 
  bgColor: string; 
  borderColor: string;
  Icon: typeof AlertOctagon;
}> = {
  SEV1: { 
    label: 'SEV1 - Critical', 
    color: 'text-red-700', 
    bgColor: 'bg-red-100', 
    borderColor: 'border-red-500',
    Icon: AlertOctagon 
  },
  SEV2: { 
    label: 'SEV2 - High', 
    color: 'text-orange-700', 
    bgColor: 'bg-orange-100', 
    borderColor: 'border-orange-500',
    Icon: AlertTriangle 
  },
  SEV3: { 
    label: 'SEV3 - Medium', 
    color: 'text-yellow-700', 
    bgColor: 'bg-yellow-100', 
    borderColor: 'border-yellow-500',
    Icon: AlertCircle 
  },
  SEV4: { 
    label: 'SEV4 - Low', 
    color: 'text-blue-700', 
    bgColor: 'bg-blue-100', 
    borderColor: 'border-blue-500',
    Icon: Info 
  },
};

const STATUS_CONFIG: Record<IncidentStatus, { 
  label: string; 
  color: string;
  progressIndex: number;
}> = {
  DETECTED: { label: 'Detected', color: 'text-red-600', progressIndex: 0 },
  TRIAGING: { label: 'Triaging', color: 'text-orange-600', progressIndex: 1 },
  INVESTIGATING: { label: 'Investigating', color: 'text-yellow-600', progressIndex: 2 },
  MITIGATING: { label: 'Mitigating', color: 'text-blue-600', progressIndex: 3 },
  RESOLVED: { label: 'Resolved', color: 'text-green-600', progressIndex: 4 },
  POST_MORTEM: { label: 'Post-Mortem', color: 'text-purple-600', progressIndex: 5 },
  CLOSED: { label: 'Closed', color: 'text-slate-600', progressIndex: 6 },
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function StatusTimeline({ status, detectedAt, acknowledgedAt, mitigatedAt, resolvedAt }: {
  status: IncidentStatus;
  detectedAt: string;
  acknowledgedAt?: string;
  mitigatedAt?: string;
  resolvedAt?: string;
}) {
  const progressIndex = STATUS_CONFIG[status].progressIndex;
  const stages = ['Detected', 'Triaging', 'Investigating', 'Mitigating', 'Resolved'];
  
  return (
    <div className="mt-2">
      <div className="flex items-center gap-1">
        {stages.map((stage, index) => (
          <div key={stage} className="flex items-center">
            <div 
              className={cn(
                'w-2 h-2 rounded-full',
                index <= progressIndex ? 'bg-blue-500' : 'bg-slate-300'
              )}
            />
            {index < stages.length - 1 && (
              <div 
                className={cn(
                  'w-4 h-0.5',
                  index < progressIndex ? 'bg-blue-500' : 'bg-slate-300'
                )}
              />
            )}
          </div>
        ))}
      </div>
      <div className="flex justify-between mt-1">
        <span className="text-[9px] text-slate-500">Detected</span>
        <span className="text-[9px] text-slate-500">Resolved</span>
      </div>
    </div>
  );
}

function ResponderAvatars({ commander, responders }: {
  commander?: IncidentNodeData['commander'];
  responders: IncidentResponder[];
}) {
  const allResponders = commander ? [{ ...commander, role: 'Commander' }, ...responders] : responders;
  const displayCount = 4;
  const overflow = allResponders.length - displayCount;
  
  return (
    <div className="flex items-center -space-x-2">
      {allResponders.slice(0, displayCount).map((responder, index) => (
        <div
          key={responder.id}
          className={cn(
            'w-6 h-6 rounded-full border-2 border-white flex items-center justify-center text-[10px] font-medium',
            responder.role === 'Commander' ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-700'
          )}
          title={`${responder.name} (${responder.role})`}
        >
          {responder.avatarUrl ? (
            <img src={responder.avatarUrl} alt={responder.name} className="w-full h-full rounded-full" />
          ) : (
            responder.name.substring(0, 2).toUpperCase()
          )}
        </div>
      ))}
      {overflow > 0 && (
        <div className="w-6 h-6 rounded-full border-2 border-white bg-slate-200 flex items-center justify-center text-[10px] font-medium text-slate-600">
          +{overflow}
        </div>
      )}
    </div>
  );
}

function DurationBadge({ detectedAt, resolvedAt }: { detectedAt: string; resolvedAt?: string }) {
  const start = new Date(detectedAt);
  const end = resolvedAt ? new Date(resolvedAt) : new Date();
  const diffMs = end.getTime() - start.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);
  
  let duration: string;
  if (diffDays > 0) {
    duration = `${diffDays}d ${diffHours % 24}h`;
  } else if (diffHours > 0) {
    duration = `${diffHours}h ${diffMins % 60}m`;
  } else {
    duration = `${diffMins}m`;
  }
  
  return (
    <span className="inline-flex items-center gap-1 text-xs text-slate-500">
      <Timer className="w-3 h-3" />
      {duration}
    </span>
  );
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

function IncidentNodeComponent({ data, selected }: IncidentNodeProps) {
  const severityConfig = SEVERITY_CONFIG[data.severity];
  const statusConfig = STATUS_CONFIG[data.status];
  const SeverityIcon = severityConfig.Icon;
  const isResolved = ['RESOLVED', 'POST_MORTEM', 'CLOSED'].includes(data.status);
  
  return (
    <div
      className={cn(
        'min-w-[280px] max-w-[320px] rounded-lg border-2 bg-white shadow-md transition-all',
        severityConfig.borderColor,
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
        type="target"
        position={Position.Left}
        id="alert"
        className="!w-3 !h-3 !bg-orange-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="postmortem"
        className="!w-3 !h-3 !bg-purple-400 !border-2 !border-white"
      />
      
      {/* Header */}
      <div className={cn('px-3 py-2 rounded-t-lg', severityConfig.bgColor)}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <SeverityIcon className={cn('w-5 h-5', severityConfig.color)} />
            <span className={cn('text-xs font-semibold', severityConfig.color)}>
              INC-{data.number}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className={cn('text-xs font-medium px-2 py-0.5 rounded', statusConfig.color, 'bg-white/80')}>
              {statusConfig.label}
            </span>
            {isResolved && <CheckCircle2 className="w-4 h-4 text-green-500" />}
          </div>
        </div>
      </div>
      
      {/* Title */}
      <div className="px-3 py-2 border-b border-slate-100">
        <h3 className="text-sm font-medium text-slate-900 line-clamp-2">{data.title}</h3>
        {data.description && (
          <p className="text-xs text-slate-500 mt-1 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Status Timeline */}
      <div className="px-3 py-2 border-b border-slate-100">
        <StatusTimeline 
          status={data.status}
          detectedAt={data.detectedAt}
          acknowledgedAt={data.acknowledgedAt}
          mitigatedAt={data.mitigatedAt}
          resolvedAt={data.resolvedAt}
        />
      </div>
      
      {/* Affected Services */}
      <div className="px-3 py-2 border-b border-slate-100">
        <div className="flex items-center gap-2 mb-1">
          <Server className="w-3 h-3 text-slate-400" />
          <span className="text-xs text-slate-500">Affected Services</span>
        </div>
        <div className="flex flex-wrap gap-1">
          {data.affectedServices.slice(0, 3).map((service) => (
            <span key={service} className="px-1.5 py-0.5 text-[10px] bg-slate-100 text-slate-700 rounded">
              {service}
            </span>
          ))}
          {data.affectedServices.length > 3 && (
            <span className="px-1.5 py-0.5 text-[10px] bg-slate-100 text-slate-500 rounded">
              +{data.affectedServices.length - 3} more
            </span>
          )}
        </div>
      </div>
      
      {/* Customer Impact */}
      {data.customerImpacted && (
        <div className="px-3 py-2 border-b border-slate-100 bg-red-50">
          <div className="flex items-center gap-2">
            <XCircle className="w-3 h-3 text-red-500" />
            <span className="text-xs text-red-700 font-medium">Customer Impact</span>
            {data.usersAffected && (
              <span className="text-xs text-red-600 ml-auto">
                {data.usersAffected.toLocaleString()} users affected
              </span>
            )}
          </div>
        </div>
      )}
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between">
        <ResponderAvatars commander={data.commander} responders={data.responders} />
        
        <div className="flex items-center gap-3">
          <DurationBadge detectedAt={data.detectedAt} resolvedAt={data.resolvedAt} />
          
          <div className="flex items-center gap-1 text-xs text-slate-400">
            <MessageSquare className="w-3 h-3" />
            <span>{data.timelineEventsCount}</span>
          </div>
          
          {data.hasPostMortem && (
            <div className="flex items-center gap-1 text-xs text-purple-500" title="Post-mortem available">
              <FileText className="w-3 h-3" />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export const IncidentNode = memo(IncidentNodeComponent);
export default IncidentNode;
