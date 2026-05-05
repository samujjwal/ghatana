// ============================================================================
// PolicyNode - Security Canvas Node
//
// Displays security policy information including rules, scope, enforcement
// settings, exceptions, and violation counts.
// ============================================================================

import { memo, useMemo } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
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

export interface PolicyNodeData extends Record<string, unknown> {
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

type PolicyCanvasNode = Node<PolicyNodeData>;

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
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  DATA_PROTECTION: {
    label: 'Data Protection',
    icon: Shield,
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    borderColor: 'border-success-border',
  },
  NETWORK_SECURITY: {
    label: 'Network Security',
    icon: Globe,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  ENCRYPTION: {
    label: 'Encryption',
    icon: FileKey,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  AUTHENTICATION: {
    label: 'Authentication',
    icon: User,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
    borderColor: 'border-info-border',
  },
  COMPLIANCE: {
    label: 'Compliance',
    icon: CheckCircle2,
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    borderColor: 'border-success-border',
  },
  INFRASTRUCTURE: {
    label: 'Infrastructure',
    icon: Server,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    borderColor: 'border-warning-border',
  },
  CUSTOM: {
    label: 'Custom',
    icon: Settings,
    color: 'text-fg-muted',
    bgColor: 'bg-surface-muted',
    borderColor: 'border-border',
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
    color: 'text-fg-muted',
    bgColor: 'bg-muted',
    icon: Edit,
  },
  PENDING_APPROVAL: {
    label: 'Pending Approval',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    icon: Clock,
  },
  ACTIVE: {
    label: 'Active',
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    icon: CheckCircle2,
  },
  INACTIVE: {
    label: 'Inactive',
    color: 'text-muted-foreground',
    bgColor: 'bg-muted',
    icon: XCircle,
  },
  ARCHIVED: {
    label: 'Archived',
    color: 'text-muted-foreground',
    bgColor: 'bg-muted',
    icon: Archive,
  },
};

// ============================================================================
// Severity Configuration
// ============================================================================

const SEVERITY_CONFIG = {
  CRITICAL: { color: 'bg-destructive' },
  HIGH: { color: 'bg-warning-color' },
  MEDIUM: { color: 'bg-warning-color' },
  LOW: { color: 'bg-info-color' },
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
        <span className="text-xs font-medium text-fg">Rules ({enabledRules.length}/{rules.length})</span>
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
            <span className="text-fg truncate flex-1">{rule.name}</span>
            <span className="text-fg-muted truncate max-w-[80px]">{rule.action}</span>
          </div>
        ))}
        {enabledRules.length > 3 && (
          <div className="flex items-center text-xs text-info-color cursor-pointer hover:opacity-80">
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
        <div className="flex items-center gap-1 rounded bg-info-bg px-2 py-1 text-xs">
          <Globe className="h-3 w-3 text-info-color" />
          <span className="text-info-color">{scope.environments.length} env</span>
        </div>
      )}
      {scope.teams && scope.teams.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-info-bg px-2 py-1 text-xs">
          <Users className="h-3 w-3 text-info-color" />
          <span className="text-info-color">{scope.teams.length} teams</span>
        </div>
      )}
      {scope.resources && scope.resources.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-surface-muted px-2 py-1 text-xs">
          <Server className="h-3 w-3 text-fg-muted" />
          <span className="text-fg">{scope.resources.length} resources</span>
        </div>
      )}
      {scope.excludedResources && scope.excludedResources.length > 0 && (
        <div className="flex items-center gap-1 rounded bg-destructive-bg px-2 py-1 text-xs">
          <XCircle className="h-3 w-3 text-destructive" />
          <span className="text-destructive">{scope.excludedResources.length} excluded</span>
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
            enforcement.mode === 'ENFORCE' ? 'bg-destructive-bg text-destructive border border-destructive-border' :
            enforcement.mode === 'MONITOR' ? 'bg-warning-bg text-warning-color border border-warning-border' :
        'bg-muted text-fg'
      )}>
        {enforcement.mode}
      </span>
      {enforcement.blockOnViolation && (
        <span className="flex items-center gap-1 rounded bg-destructive-bg px-1.5 py-0.5 text-xs text-destructive">
          <Lock className="h-3 w-3" />
          Block
        </span>
      )}
      {enforcement.notifyOnViolation && (
        <span className="flex items-center gap-1 rounded bg-info-bg px-1.5 py-0.5 text-xs text-info-color">
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

function PolicyNodeComponent({ data, selected }: NodeProps<PolicyCanvasNode>) {
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
          selected && 'ring-2 ring-primary ring-offset-2',
          'min-w-[200px]'
        )}
      >
        <Handle type="target" position={Position.Left} className="!bg-muted-foreground" />
        <Handle type="source" position={Position.Right} className="!bg-muted-foreground" />

        <div className="flex items-center gap-2">
          <TypeIcon className={cn('h-4 w-4', typeConfig.color)} />
          <span className="truncate text-sm font-medium text-fg">{data.name}</span>
        </div>
        <div className="mt-1 flex items-center justify-between">
          <span className={cn('text-xs', statusConfig.color)}>{statusConfig.label}</span>
          <span className="text-xs text-fg-muted">v{data.version}</span>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border-2 bg-white shadow-md transition-all duration-200',
        typeConfig.borderColor,
        selected && 'ring-2 ring-primary ring-offset-2',
        hasViolations && isActive && 'shadow-destructive/20',
        'min-w-[320px] max-w-[400px]'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-muted-foreground" />
      <Handle type="source" position={Position.Right} className="!bg-muted-foreground" />

      {/* Header */}
      <div className={cn('rounded-t-lg px-4 py-3', typeConfig.bgColor)}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            <TypeIcon className={cn('h-5 w-5 mt-0.5', typeConfig.color)} />
            <div>
              <h3 className="font-semibold text-fg leading-tight">{data.name}</h3>
              <div className="flex items-center gap-2 mt-1">
                <span className={cn('text-xs font-medium', typeConfig.color)}>
                  {typeConfig.label}
                </span>
                <span className="text-fg-muted">•</span>
                <span className="text-xs text-fg-muted">v{data.version}</span>
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
          <p className="text-sm text-fg-muted line-clamp-2">{data.description}</p>
        )}

        {/* Violations Alert */}
        {hasViolations && isActive && (
          <div className="flex items-center gap-2 rounded-md bg-destructive-bg px-3 py-2 border border-destructive-border">
            <AlertTriangle className="h-4 w-4 text-destructive" />
            <div className="flex-1">
              <span className="text-sm font-medium text-destructive">
                {data.openViolationCount} Open Violation{data.openViolationCount !== 1 ? 's' : ''}
              </span>
              {data.violationCount && data.violationCount > (data.openViolationCount || 0) && (
                <span className="text-xs text-destructive ml-2">
                  ({data.violationCount} total)
                </span>
              )}
            </div>
          </div>
        )}

        {/* Enforcement */}
        {data.enforcement && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-fg">Enforcement</span>
            <EnforcementBadge enforcement={data.enforcement} />
          </div>
        )}

        {/* Rules Summary */}
        {data.rules && data.rules.length > 0 && (
          <div className="rounded-md bg-surface-muted p-3">
            <RulesSummary rules={data.rules} />
          </div>
        )}

        {/* Scope */}
        {data.scope && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-fg">Scope</span>
            <ScopeSummary scope={data.scope} />
          </div>
        )}

        {/* Exceptions */}
        {data.exceptions && data.exceptions.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-fg">Exceptions</span>
              <span className="text-xs text-fg-muted">{data.exceptions.length} active</span>
            </div>
            <div className="space-y-1">
              {data.exceptions.slice(0, 2).map((exception) => (
                <div key={exception.id} className="flex items-center gap-2 text-xs rounded bg-warning-bg px-2 py-1">
                  <AlertTriangle className="h-3 w-3 text-warning-color flex-shrink-0" />
                  <span className="text-warning-color truncate flex-1">{exception.resource}</span>
                  {exception.validUntil && (
                    <span className="text-warning-color">until {formatDate(exception.validUntil)}</span>
                  )}
                </div>
              ))}
              {data.exceptions.length > 2 && (
                <span className="text-xs text-fg-muted">
                  +{data.exceptions.length - 2} more exceptions
                </span>
              )}
            </div>
          </div>
        )}

        {/* Effective Dates */}
        {(data.effectiveFrom || data.effectiveUntil) && (
          <div className="flex items-center gap-4 text-xs text-fg-muted">
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
      <div className="border-t border-border px-4 py-3 flex items-center justify-between">
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
                <User className="h-4 w-4 text-muted-foreground" />
              )}
              <span className="text-xs text-fg-muted">{data.createdBy.name}</span>
            </div>
          ) : (
            <span className="text-xs text-muted-foreground">System</span>
          )}

          {/* Approved By */}
          {data.approvedBy && (
            <div className="flex items-center gap-1 text-xs text-success-color">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Approved by {data.approvedBy.name}</span>
            </div>
          )}
        </div>

        {/* Last Updated */}
        <span className="text-xs text-muted-foreground">
          Updated {formatDate(data.updatedAt)}
        </span>
      </div>
    </div>
  );
}

export const PolicyNode = memo(PolicyNodeComponent);
export default PolicyNode;
