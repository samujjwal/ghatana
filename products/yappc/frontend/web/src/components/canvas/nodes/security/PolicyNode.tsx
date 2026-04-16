// ============================================================================
// PolicyNode - Security Canvas Node
//
// Displays security policy information including rules, scope, enforcement
// settings, exceptions, and violation counts.
// ============================================================================

import { memo, useMemo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import {
  FileKey,
  Shield,
  Lock,
  Globe,
  Server,
  Users,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Clock,
  User,
  Settings,
  Bell,
  List,
  ChevronRight,
  Edit,
  Archive,
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// Types
// ============================================================================

export type PolicyType = 
  | 'ACCESS_CONTROL' 
  | 'DATA_PROTECTION' 
  | 'NETWORK_SECURITY' 
  | 'ENCRYPTION' 
  | 'AUTHENTICATION' 
  | 'COMPLIANCE' 
  | 'INFRASTRUCTURE' 
  | 'CUSTOM';

export type PolicyStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export interface PolicyRule {
  id: string;
  name: string;
  description?: string;
  condition: string;
  action: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  enabled: boolean;
}

export interface PolicyScope {
  resources?: string[];
  environments?: string[];
  teams?: string[];
  excludedResources?: string[];
}

export interface PolicyEnforcement {
  mode: string;
  blockOnViolation: boolean;
  notifyOnViolation: boolean;
  notificationChannels?: string[];
}

export interface PolicyException {
  id: string;
  reason: string;
  resource: string;
  validUntil?: string;
}

export interface PolicyNodeData {
  id: string;
  name: string;
  description?: string;
  type: PolicyType;
  status: PolicyStatus;
  version: number;
  rules?: PolicyRule[];
  scope?: PolicyScope;
  exceptions?: PolicyException[];
  enforcement?: PolicyEnforcement;
  createdBy?: {
    id: string;
    name: string;
    avatarUrl?: string;
  };
  approvedBy?: {
    id: string;
    name: string;
  };
  effectiveFrom?: string;
  effectiveUntil?: string;
  violationCount?: number;
  openViolationCount?: number;
  createdAt: string;
  updatedAt: string;
  selected?: boolean;
  compact?: boolean;
}

// ============================================================================
// Type Configuration
// ============================================================================

const TYPE_CONFIG: Record<PolicyType, {
  label: string;
  icon: typeof Shield;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  ACCESS_CONTROL: {
    label: 'Access Control',
    icon: Lock,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-500',
  },
  DATA_PROTECTION: {
    label: 'Data Protection',
    icon: Shield,
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    borderColor: 'border-green-500',
  },
  NETWORK_SECURITY: {
    label: 'Network Security',
    icon: Globe,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-500',
  },
  ENCRYPTION: {
    label: 'Encryption',
    icon: FileKey,
    color: 'text-cyan-600',
    bgColor: 'bg-cyan-50',
    borderColor: 'border-cyan-500',
  },
  AUTHENTICATION: {
    label: 'Authentication',
    icon: User,
    color: 'text-indigo-600',
    bgColor: 'bg-indigo-50',
    borderColor: 'border-indigo-500',
  },
  COMPLIANCE: {
    label: 'Compliance',
    icon: CheckCircle2,
    color: 'text-teal-600',
    bgColor: 'bg-teal-50',
    borderColor: 'border-teal-500',
  },
  INFRASTRUCTURE: {
    label: 'Infrastructure',
    icon: Server,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-500',
  },
  CUSTOM: {
    label: 'Custom',
    icon: Settings,
    color: 'text-gray-600',
    bgColor: 'bg-gray-50',
    borderColor: 'border-gray-500',
  },
};

// ============================================================================
// Status Configuration
// ============================================================================

const STATUS_CONFIG: Record<PolicyStatus, {
  label: string;
  color: string;
  bgColor: string;
  icon: typeof CheckCircle2;
}> = {
  DRAFT: {
    label: 'Draft',
    color: 'text-gray-600',
    bgColor: 'bg-gray-100',
    icon: Edit,
  },
  PENDING_APPROVAL: {
    label: 'Pending Approval',
    color: 'text-amber-600',
    bgColor: 'bg-amber-100',
    icon: Clock,
  },
  ACTIVE: {
    label: 'Active',
    color: 'text-green-600',
    bgColor: 'bg-green-100',
    icon: CheckCircle2,
  },
  INACTIVE: {
    label: 'Inactive',
    color: 'text-gray-500',
    bgColor: 'bg-gray-100',
    icon: XCircle,
  },
  ARCHIVED: {
    label: 'Archived',
    color: 'text-slate-500',
    bgColor: 'bg-slate-100',
    icon: Archive,
  },
};

// ============================================================================
// Severity Configuration
// ============================================================================

const SEVERITY_CONFIG = {
  CRITICAL: { color: 'bg-red-500' },
  HIGH: { color: 'bg-orange-500' },
  MEDIUM: { color: 'bg-yellow-500' },
  LOW: { color: 'bg-blue-500' },
};

// ============================================================================
// Helper Functions
// ============================================================================

function formatDate(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

// ============================================================================
// Helper Components
// ============================================================================

function RulesSummary({ rules }: { rules: PolicyRule[] }) {
  const enabledRules = rules.filter((r) => r.enabled);
  const bySeverity = useMemo(() => {
    const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    enabledRules.forEach((r) => {
      counts[r.severity]++;
    });
    return counts;
  }, [enabledRules]);

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-gray-700">Rules ({enabledRules.length}/{rules.length})</span>
        <div className="flex items-center gap-1">
          {Object.entries(bySeverity).map(([severity, count]) => (
            count > 0 && (
              <span
                key={severity}
                className={cn(
                  'px-1.5 py-0.5 rounded text-xs font-medium text-white',
                  SEVERITY_CONFIG[severity as keyof typeof SEVERITY_CONFIG].color
                )}
              >
                {count}
              </span>
            )
          ))}
        </div>
      </div>
      <div className="space-y-1">
        {enabledRules.slice(0, 3).map((rule) => (
          <div key={rule.id} className="flex items-center gap-2 text-xs">
            <span className={cn(
              'h-1.5 w-1.5 rounded-full',
              SEVERITY_CONFIG[rule.severity].color
            )} />
            <span className="text-gray-700 truncate flex-1">{rule.name}</span>
            <span className="text-gray-400 truncate max-w-[80px]">{rule.action}</span>
          </div>
        ))}
        {enabledRules.length > 3 && (
          <div className="flex items-center text-xs text-blue-600 cursor-pointer hover:text-blue-700">
            <span>View all {enabledRules.length} rules</span>
            <ChevronRight className="h-3 w-3" />
          </div>
        )}
      </div>
    </div>
  );
}

function ScopeSummary({ scope }: { scope: PolicyScope }) {
  const totalItems = 
    (scope.resources?.length || 0) + 
    (scope.environments?.length || 0) + 
    (scope.teams?.length || 0);

  return (
    <div className="flex flex-wrap gap-2">
      {scope.environments && scope.environments.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-purple-50 px-2 py-1 text-xs">
          <Globe className="h-3 w-3 text-purple-500" />
          <span className="text-purple-700">{scope.environments.length} env</span>
        </div>
      )}
      {scope.teams && scope.teams.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-blue-50 px-2 py-1 text-xs">
          <Users className="h-3 w-3 text-blue-500" />
          <span className="text-blue-700">{scope.teams.length} teams</span>
        </div>
      )}
      {scope.resources && scope.resources.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-gray-50 px-2 py-1 text-xs">
          <Server className="h-3 w-3 text-gray-500" />
          <span className="text-gray-700">{scope.resources.length} resources</span>
        </div>
      )}
      {scope.excludedResources && scope.excludedResources.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-red-50 px-2 py-1 text-xs">
          <XCircle className="h-3 w-3 text-red-500" />
          <span className="text-red-700">{scope.excludedResources.length} excluded</span>
        </div>
      )}
    </div>
  );
}

function EnforcementBadge({ enforcement }: { enforcement: PolicyEnforcement }) {
  return (
    <div className="flex items-center gap-2">
      <span className={cn(
        'rounded-full px-2 py-0.5 text-xs font-medium',
        enforcement.mode === 'ENFORCE' ? 'bg-red-100 text-red-700' :
        enforcement.mode === 'MONITOR' ? 'bg-yellow-100 text-yellow-700' :
        'bg-gray-100 text-gray-700'
      )}>
        {enforcement.mode}
      </span>
      {enforcement.blockOnViolation && (
        <span className="flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-xs text-red-600">
          <Lock className="h-3 w-3" />
          Block
        </span>
      )}
      {enforcement.notifyOnViolation && (
        <span className="flex items-center gap-1 rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-600">
          <Bell className="h-3 w-3" />
          Notify
        </span>
      )}
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

function PolicyNodeComponent({ data, selected }: NodeProps<PolicyNodeData>) {
  const typeConfig = TYPE_CONFIG[data.type];
  const statusConfig = STATUS_CONFIG[data.status];
  const TypeIcon = typeConfig.icon;
  const StatusIcon = statusConfig.icon;

  const isActive = data.status === 'ACTIVE';
  const hasViolations = (data.openViolationCount || 0) > 0;

  if (data.compact) {
    return (
      <div
        className={cn(
          'rounded-lg border-2 bg-white p-3 shadow-sm transition-all duration-200',
          typeConfig.borderColor,
          selected && 'ring-2 ring-blue-500 ring-offset-2',
          'min-w-[200px]'
        )}
      >
        <Handle type="target" position={Position.Left} className="!bg-gray-400" />
        <Handle type="source" position={Position.Right} className="!bg-gray-400" />

        <div className="flex items-center gap-2">
          <TypeIcon className={cn('h-4 w-4', typeConfig.color)} />
          <span className="truncate text-sm font-medium text-gray-900">{data.name}</span>
        </div>
        <div className="mt-1 flex items-center justify-between">
          <span className={cn('text-xs', statusConfig.color)}>{statusConfig.label}</span>
          <span className="text-xs text-gray-400">v{data.version}</span>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border-2 bg-white shadow-md transition-all duration-200',
        typeConfig.borderColor,
        selected && 'ring-2 ring-blue-500 ring-offset-2',
        hasViolations && isActive && 'shadow-red-100',
        'min-w-[320px] max-w-[400px]'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-gray-400" />
      <Handle type="source" position={Position.Right} className="!bg-gray-400" />

      {/* Header */}
      <div className={cn('rounded-t-lg px-4 py-3', typeConfig.bgColor)}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            <TypeIcon className={cn('h-5 w-5 mt-0.5', typeConfig.color)} />
            <div>
              <h3 className="font-semibold text-gray-900 leading-tight">{data.name}</h3>
              <div className="flex items-center gap-2 mt-1">
                <span className={cn('text-xs font-medium', typeConfig.color)}>
                  {typeConfig.label}
                </span>
                <span className="text-gray-300">•</span>
                <span className="text-xs text-gray-500">v{data.version}</span>
              </div>
            </div>
          </div>
          <div className={cn(
            'flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium',
            statusConfig.bgColor,
            statusConfig.color
          )}>
            <StatusIcon className="h-3 w-3" />
            <span>{statusConfig.label}</span>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-4 space-y-4">
        {/* Description */}
        {data.description && (
          <p className="text-sm text-gray-600 line-clamp-2">{data.description}</p>
        )}

        {/* Violations Alert */}
        {hasViolations && isActive && (
          <div className="flex items-center gap-2 rounded-md bg-red-50 px-3 py-2 border border-red-200">
            <AlertTriangle className="h-4 w-4 text-red-600" />
            <div className="flex-1">
              <span className="text-sm font-medium text-red-700">
                {data.openViolationCount} Open Violation{data.openViolationCount !== 1 ? 's' : ''}
              </span>
              {data.violationCount && data.violationCount > (data.openViolationCount || 0) && (
                <span className="text-xs text-red-600 ml-2">
                  ({data.violationCount} total)
                </span>
              )}
            </div>
          </div>
        )}

        {/* Enforcement */}
        {data.enforcement && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Enforcement</span>
            <EnforcementBadge enforcement={data.enforcement} />
          </div>
        )}

        {/* Rules Summary */}
        {data.rules && data.rules.length > 0 && (
          <div className="rounded-md bg-gray-50 p-3">
            <RulesSummary rules={data.rules} />
          </div>
        )}

        {/* Scope */}
        {data.scope && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Scope</span>
            <ScopeSummary scope={data.scope} />
          </div>
        )}

        {/* Exceptions */}
        {data.exceptions && data.exceptions.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-gray-700">Exceptions</span>
              <span className="text-xs text-gray-500">{data.exceptions.length} active</span>
            </div>
            <div className="space-y-1">
              {data.exceptions.slice(0, 2).map((exception) => (
                <div key={exception.id} className="flex items-center gap-2 text-xs rounded bg-amber-50 px-2 py-1">
                  <AlertTriangle className="h-3 w-3 text-amber-500 flex-shrink-0" />
                  <span className="text-amber-700 truncate flex-1">{exception.resource}</span>
                  {exception.validUntil && (
                    <span className="text-amber-500">until {formatDate(exception.validUntil)}</span>
                  )}
                </div>
              ))}
              {data.exceptions.length > 2 && (
                <span className="text-xs text-gray-500">
                  +{data.exceptions.length - 2} more exceptions
                </span>
              )}
            </div>
          </div>
        )}

        {/* Effective Dates */}
        {(data.effectiveFrom || data.effectiveUntil) && (
          <div className="flex items-center gap-4 text-xs text-gray-500">
            {data.effectiveFrom && (
              <div className="flex items-center gap-1">
                <Clock className="h-3.5 w-3.5" />
                <span>From: {formatDate(data.effectiveFrom)}</span>
              </div>
            )}
            {data.effectiveUntil && (
              <div className="flex items-center gap-1">
                <Clock className="h-3.5 w-3.5" />
                <span>Until: {formatDate(data.effectiveUntil)}</span>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="border-t border-gray-100 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          {/* Created By */}
          {data.createdBy ? (
            <div className="flex items-center gap-1.5">
              {data.createdBy.avatarUrl ? (
                <img
                  src={data.createdBy.avatarUrl}
                  alt={data.createdBy.name}
                  className="h-5 w-5 rounded-full"
                />
              ) : (
                <User className="h-4 w-4 text-gray-400" />
              )}
              <span className="text-xs text-gray-600">{data.createdBy.name}</span>
            </div>
          ) : (
            <span className="text-xs text-gray-400">System</span>
          )}

          {/* Approved By */}
          {data.approvedBy && (
            <div className="flex items-center gap-1 text-xs text-green-600">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Approved by {data.approvedBy.name}</span>
            </div>
          )}
        </div>

        {/* Last Updated */}
        <span className="text-xs text-gray-400">
          Updated {formatDate(data.updatedAt)}
        </span>
      </div>
    </div>
  );
}

export const PolicyNode = memo(PolicyNodeComponent);
export default PolicyNode;
