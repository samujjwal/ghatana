// ============================================================================
// AuditNode - Security Canvas Node
//
// Displays audit log events with category, action details, user information,
// resource changes, and timestamp visualization.
// ============================================================================

import { memo, useMemo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import {
  FileText,
  User,
  Shield,
  Database,
  Settings,
  Lock,
  Server,
  Clock,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Globe,
  Eye,
  Edit,
  Trash,
  Plus,
  ArrowRight,
  ChevronRight,
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// Types
// ============================================================================

export type AuditEventCategory = 
  | 'AUTHENTICATION' 
  | 'AUTHORIZATION' 
  | 'DATA_ACCESS' 
  | 'DATA_MODIFICATION' 
  | 'CONFIGURATION' 
  | 'SECURITY' 
  | 'SYSTEM';

export interface AuditChanges {
  before?: Record<string, unknown>;
  after?: Record<string, unknown>;
  fields?: string[];
}

export interface AuditNodeData {
  id: string;
  projectId: string;
  userId: string;
  user?: {
    id: string;
    email: string;
    name: string;
    avatarUrl?: string;
  };
  category: AuditEventCategory;
  action: string;
  resource: string;
  resourceId?: string;
  status: 'SUCCESS' | 'FAILURE' | 'PENDING';
  ipAddress?: string;
  userAgent?: string;
  requestId?: string;
  details?: Record<string, unknown>;
  changes?: AuditChanges;
  timestamp: string;
  selected?: boolean;
  compact?: boolean;
}

// ============================================================================
// Category Configuration
// ============================================================================

const CATEGORY_CONFIG: Record<AuditEventCategory, {
  label: string;
  icon: typeof Shield;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  AUTHENTICATION: {
    label: 'Authentication',
    icon: Lock,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-500',
  },
  AUTHORIZATION: {
    label: 'Authorization',
    icon: Shield,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-500',
  },
  DATA_ACCESS: {
    label: 'Data Access',
    icon: Eye,
    color: 'text-cyan-600',
    bgColor: 'bg-cyan-50',
    borderColor: 'border-cyan-500',
  },
  DATA_MODIFICATION: {
    label: 'Data Modification',
    icon: Edit,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-500',
  },
  CONFIGURATION: {
    label: 'Configuration',
    icon: Settings,
    color: 'text-gray-600',
    bgColor: 'bg-gray-50',
    borderColor: 'border-gray-500',
  },
  SECURITY: {
    label: 'Security',
    icon: Shield,
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    borderColor: 'border-red-500',
  },
  SYSTEM: {
    label: 'System',
    icon: Server,
    color: 'text-indigo-600',
    bgColor: 'bg-indigo-50',
    borderColor: 'border-indigo-500',
  },
};

// ============================================================================
// Status Configuration
// ============================================================================

const STATUS_CONFIG: Record<string, {
  label: string;
  color: string;
  bgColor: string;
  icon: typeof CheckCircle2;
}> = {
  SUCCESS: {
    label: 'Success',
    color: 'text-green-600',
    bgColor: 'bg-green-100',
    icon: CheckCircle2,
  },
  FAILURE: {
    label: 'Failure',
    color: 'text-red-600',
    bgColor: 'bg-red-100',
    icon: XCircle,
  },
  PENDING: {
    label: 'Pending',
    color: 'text-amber-600',
    bgColor: 'bg-amber-100',
    icon: Clock,
  },
};

// ============================================================================
// Action Icons
// ============================================================================

function getActionIcon(action: string): typeof Edit {
  const actionLower = action.toLowerCase();
  if (actionLower.includes('create') || actionLower.includes('add')) return Plus;
  if (actionLower.includes('delete') || actionLower.includes('remove')) return Trash;
  if (actionLower.includes('update') || actionLower.includes('edit') || actionLower.includes('modify')) return Edit;
  if (actionLower.includes('read') || actionLower.includes('view') || actionLower.includes('get')) return Eye;
  if (actionLower.includes('login') || actionLower.includes('auth')) return Lock;
  return FileText;
}

// ============================================================================
// Helper Functions
// ============================================================================

function formatTimestamp(dateString: string): string {
  const date = new Date(dateString);
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSecs = Math.floor(diffMs / 1000);
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

  if (diffSecs < 60) return `${diffSecs}s ago`;
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

function parseUserAgent(userAgent: string): { browser: string; os: string } | null {
  if (!userAgent) return null;
  
  let browser = 'Unknown';
  let os = 'Unknown';

  if (userAgent.includes('Chrome')) browser = 'Chrome';
  else if (userAgent.includes('Firefox')) browser = 'Firefox';
  else if (userAgent.includes('Safari')) browser = 'Safari';
  else if (userAgent.includes('Edge')) browser = 'Edge';

  if (userAgent.includes('Windows')) os = 'Windows';
  else if (userAgent.includes('Mac')) os = 'macOS';
  else if (userAgent.includes('Linux')) os = 'Linux';
  else if (userAgent.includes('Android')) os = 'Android';
  else if (userAgent.includes('iOS')) os = 'iOS';

  return { browser, os };
}

// ============================================================================
// Helper Components
// ============================================================================

function ChangesDiff({ changes }: { changes: AuditChanges }) {
  const changedFields = changes.fields || [];
  
  if (changedFields.length === 0 && !changes.before && !changes.after) {
    return null;
  }

  return (
    <div className="rounded-md bg-gray-50 p-2 space-y-2">
      <span className="text-xs font-medium text-gray-700">Changes</span>
      {changedFields.length > 0 ? (
        <div className="space-y-1">
          {changedFields.slice(0, 4).map((field) => {
            const before = changes.before?.[field];
            const after = changes.after?.[field];
            
            return (
              <div key={field} className="flex items-center gap-2 text-xs">
                <span className="text-gray-600 font-medium min-w-[60px]">{field}:</span>
                {before !== undefined && (
                  <span className="text-red-600 line-through truncate max-w-[80px]">
                    {String(before)}
                  </span>
                )}
                <ArrowRight className="h-3 w-3 text-gray-400 flex-shrink-0" />
                {after !== undefined && (
                  <span className="text-green-600 truncate max-w-[80px]">
                    {String(after)}
                  </span>
                )}
              </div>
            );
          })}
          {changedFields.length > 4 && (
            <span className="text-xs text-gray-500">
              +{changedFields.length - 4} more fields
            </span>
          )}
        </div>
      ) : (
        <div className="flex items-center gap-2 text-xs">
          <span className="text-gray-500">
            {Object.keys(changes.after || {}).length} fields modified
          </span>
        </div>
      )}
    </div>
  );
}

function DetailsList({ details }: { details: Record<string, unknown> }) {
  const entries = Object.entries(details).filter(
    ([_, value]) => value !== null && value !== undefined
  );
  
  if (entries.length === 0) return null;

  return (
    <div className="space-y-1">
      {entries.slice(0, 3).map(([key, value]) => (
        <div key={key} className="flex items-center gap-2 text-xs">
          <span className="text-gray-500">{key}:</span>
          <span className="text-gray-700 truncate">
            {typeof value === 'object' ? JSON.stringify(value) : String(value)}
          </span>
        </div>
      ))}
      {entries.length > 3 && (
        <div className="flex items-center text-xs text-blue-600 cursor-pointer hover:text-blue-700">
          <span>View all details</span>
          <ChevronRight className="h-3 w-3" />
        </div>
      )}
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

function AuditNodeComponent({ data, selected }: NodeProps<AuditNodeData>) {
  const categoryConfig = CATEGORY_CONFIG[data.category];
  const statusConfig = STATUS_CONFIG[data.status];
  const CategoryIcon = categoryConfig.icon;
  const StatusIcon = statusConfig.icon;
  const ActionIcon = getActionIcon(data.action);

  const parsedUserAgent = useMemo(
    () => data.userAgent ? parseUserAgent(data.userAgent) : null,
    [data.userAgent]
  );

  if (data.compact) {
    return (
      <div
        className={cn(
          'rounded-lg border-2 bg-white p-3 shadow-sm transition-all duration-200',
          categoryConfig.borderColor,
          selected && 'ring-2 ring-blue-500 ring-offset-2',
          'min-w-[200px]'
        )}
      >
        <Handle type="target" position={Position.Left} className="!bg-gray-400" />
        <Handle type="source" position={Position.Right} className="!bg-gray-400" />

        <div className="flex items-center gap-2">
          <CategoryIcon className={cn('h-4 w-4', categoryConfig.color)} />
          <span className="truncate text-sm font-medium text-gray-900">{data.action}</span>
        </div>
        <div className="mt-1 flex items-center justify-between">
          <span className={cn('text-xs', statusConfig.color)}>{statusConfig.label}</span>
          <span className="text-xs text-gray-400">{formatRelativeTime(data.timestamp)}</span>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'rounded-lg border-2 bg-white shadow-md transition-all duration-200',
        categoryConfig.borderColor,
        selected && 'ring-2 ring-blue-500 ring-offset-2',
        data.status === 'FAILURE' && 'shadow-red-100',
        'min-w-[320px] max-w-[400px]'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-gray-400" />
      <Handle type="source" position={Position.Right} className="!bg-gray-400" />

      {/* Header */}
      <div className={cn('rounded-t-lg px-4 py-3', categoryConfig.bgColor)}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2">
            <CategoryIcon className={cn('h-5 w-5 mt-0.5', categoryConfig.color)} />
            <div>
              <div className="flex items-center gap-2">
                <ActionIcon className="h-4 w-4 text-gray-500" />
                <h3 className="font-semibold text-gray-900">{data.action}</h3>
              </div>
              <span className={cn('text-xs font-medium', categoryConfig.color)}>
                {categoryConfig.label}
              </span>
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
        {/* User Info */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {data.user?.avatarUrl ? (
              <img
                src={data.user.avatarUrl}
                alt={data.user.name}
                className="h-8 w-8 rounded-full"
              />
            ) : (
              <div className="h-8 w-8 rounded-full bg-gray-200 flex items-center justify-center">
                <User className="h-4 w-4 text-gray-500" />
              </div>
            )}
            <div>
              <p className="text-sm font-medium text-gray-900">
                {data.user?.name || 'Unknown User'}
              </p>
              <p className="text-xs text-gray-500">{data.user?.email}</p>
            </div>
          </div>
        </div>

        {/* Resource */}
        <div className="flex items-start gap-2">
          <Database className="h-4 w-4 text-gray-400 mt-0.5" />
          <div className="flex-1 min-w-0">
            <span className="text-xs text-gray-500">Resource</span>
            <p className="text-sm font-medium text-gray-900">{data.resource}</p>
            {data.resourceId && (
              <p className="text-xs font-mono text-gray-500 truncate">{data.resourceId}</p>
            )}
          </div>
        </div>

        {/* Changes */}
        {data.changes && <ChangesDiff changes={data.changes} />}

        {/* Additional Details */}
        {data.details && Object.keys(data.details).length > 0 && (
          <div className="space-y-2">
            <span className="text-xs font-medium text-gray-700">Details</span>
            <DetailsList details={data.details} />
          </div>
        )}

        {/* Request Info */}
        <div className="grid grid-cols-2 gap-3">
          {/* IP Address */}
          {data.ipAddress && (
            <div className="flex items-start gap-2">
              <Globe className="h-4 w-4 text-gray-400 mt-0.5" />
              <div>
                <span className="text-xs text-gray-500">IP Address</span>
                <p className="text-sm font-mono text-gray-700">{data.ipAddress}</p>
              </div>
            </div>
          )}
          
          {/* User Agent */}
          {parsedUserAgent && (
            <div className="flex items-start gap-2">
              <Server className="h-4 w-4 text-gray-400 mt-0.5" />
              <div>
                <span className="text-xs text-gray-500">Client</span>
                <p className="text-sm text-gray-700">
                  {parsedUserAgent.browser} / {parsedUserAgent.os}
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Request ID */}
        {data.requestId && (
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-gray-400" />
            <span className="text-xs text-gray-500">Request ID:</span>
            <span className="text-xs font-mono text-gray-600 truncate">{data.requestId}</span>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="border-t border-gray-100 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-gray-500">
          <Clock className="h-4 w-4" />
          <span className="text-xs">{formatTimestamp(data.timestamp)}</span>
        </div>
        <span className="text-xs text-gray-400">
          {formatRelativeTime(data.timestamp)}
        </span>
      </div>
    </div>
  );
}

export const AuditNode = memo(AuditNodeComponent);
export default AuditNode;
