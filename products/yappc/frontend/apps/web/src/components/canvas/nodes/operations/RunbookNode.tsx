// ============================================================================
// RunbookNode - Canvas node for runbook automation visualization
// 
// Displays:
// - Runbook name and description
// - Status (draft/published/deprecated)
// - Step count and types
// - Triggers configuration
// - Execution stats
// - Recent execution status
// ============================================================================

import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { 
  BookOpen, 
  Play, 
  CheckCircle, 
  XCircle,
  Clock,
  Zap,
  Settings,
  Tag,
  User,
  BarChart2,
  AlertTriangle,
  Archive
} from 'lucide-react';
import { cn } from '../../utils/cn';

// ============================================================================
// TYPES
// ============================================================================

export type RunbookStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';

export type RunbookStepType = 
  | 'command'
  | 'script'
  | 'http'
  | 'approval'
  | 'condition'
  | 'notification'
  | 'wait';

export interface RunbookStepSummary {
  type: RunbookStepType;
  count: number;
}

export interface RunbookTrigger {
  type: 'manual' | 'alert' | 'schedule' | 'webhook';
  enabled: boolean;
}

export interface RunbookExecutionSummary {
  total: number;
  successful: number;
  failed: number;
  lastStatus?: 'COMPLETED' | 'FAILED' | 'RUNNING';
  lastExecutedAt?: string;
  averageDuration?: number;
}

export interface RunbookNodeData {
  id: string;
  name: string;
  description?: string;
  status: RunbookStatus;
  version: number;
  stepCount: number;
  stepSummary: RunbookStepSummary[];
  triggers: RunbookTrigger[];
  parameters: string[];
  tags: string[];
  author: {
    id: string;
    name: string;
    avatarUrl?: string;
  };
  executionStats: RunbookExecutionSummary;
}

export type RunbookNodeProps = NodeProps<RunbookNodeData>;

// ============================================================================
// CONSTANTS
// ============================================================================

const STATUS_CONFIG: Record<RunbookStatus, { 
  label: string; 
  color: string;
  bgColor: string;
  Icon: typeof CheckCircle;
}> = {
  DRAFT: { label: 'Draft', color: 'text-yellow-700', bgColor: 'bg-yellow-100', Icon: Settings },
  PUBLISHED: { label: 'Published', color: 'text-green-700', bgColor: 'bg-green-100', Icon: CheckCircle },
  DEPRECATED: { label: 'Deprecated', color: 'text-slate-500', bgColor: 'bg-slate-100', Icon: Archive },
};

const STEP_TYPE_ICONS: Record<RunbookStepType, typeof Play> = {
  command: Play,
  script: BookOpen,
  http: Zap,
  approval: CheckCircle,
  condition: Settings,
  notification: AlertTriangle,
  wait: Clock,
};

const TRIGGER_TYPE_ICONS: Record<string, typeof Zap> = {
  manual: Play,
  alert: AlertTriangle,
  schedule: Clock,
  webhook: Zap,
};

// ============================================================================
// HELPER COMPONENTS
// ============================================================================

function StepTypesSummary({ summary }: { summary: RunbookStepSummary[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {summary.slice(0, 4).map((step) => {
        const Icon = STEP_TYPE_ICONS[step.type] || Settings;
        return (
          <div 
            key={step.type}
            className="flex items-center gap-1 px-1.5 py-0.5 bg-slate-100 rounded"
            title={`${step.count} ${step.type} step${step.count > 1 ? 's' : ''}`}
          >
            <Icon className="w-3 h-3 text-slate-500" />
            <span className="text-[10px] text-slate-600">{step.count}</span>
          </div>
        );
      })}
    </div>
  );
}

function TriggerBadges({ triggers }: { triggers: RunbookTrigger[] }) {
  const enabledTriggers = triggers.filter(t => t.enabled);
  
  return (
    <div className="flex items-center gap-1">
      {enabledTriggers.map((trigger) => {
        const Icon = TRIGGER_TYPE_ICONS[trigger.type] || Zap;
        return (
          <div 
            key={trigger.type}
            className="w-5 h-5 rounded bg-blue-50 flex items-center justify-center"
            title={`${trigger.type} trigger enabled`}
          >
            <Icon className="w-3 h-3 text-blue-500" />
          </div>
        );
      })}
      {enabledTriggers.length === 0 && (
        <span className="text-[10px] text-slate-400">No triggers</span>
      )}
    </div>
  );
}

function ExecutionStats({ stats }: { stats: RunbookExecutionSummary }) {
  const successRate = stats.total > 0 
    ? Math.round((stats.successful / stats.total) * 100) 
    : 0;
  
  return (
    <div className="space-y-2">
      {/* Success rate bar */}
      <div>
        <div className="flex justify-between text-[10px] text-slate-500 mb-1">
          <span>{stats.total} executions</span>
          <span>{successRate}% success</span>
        </div>
        <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden">
          <div 
            className="h-full bg-green-500 rounded-full"
            style={{ width: `${successRate}%` }}
          />
        </div>
      </div>
      
      {/* Stats row */}
      <div className="flex items-center gap-3 text-[10px]">
        <div className="flex items-center gap-1 text-green-600">
          <CheckCircle className="w-3 h-3" />
          <span>{stats.successful}</span>
        </div>
        <div className="flex items-center gap-1 text-red-600">
          <XCircle className="w-3 h-3" />
          <span>{stats.failed}</span>
        </div>
        {stats.averageDuration && (
          <div className="flex items-center gap-1 text-slate-500">
            <Clock className="w-3 h-3" />
            <span>{formatDuration(stats.averageDuration)}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function LastExecutionBadge({ stats }: { stats: RunbookExecutionSummary }) {
  if (!stats.lastStatus) return null;
  
  const config = {
    COMPLETED: { color: 'text-green-600', bgColor: 'bg-green-50', Icon: CheckCircle },
    FAILED: { color: 'text-red-600', bgColor: 'bg-red-50', Icon: XCircle },
    RUNNING: { color: 'text-blue-600', bgColor: 'bg-blue-50', Icon: Play },
  };
  
  const { color, bgColor, Icon } = config[stats.lastStatus];
  
  return (
    <div className={cn('flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px]', bgColor, color)}>
      <Icon className="w-3 h-3" />
      <span>{stats.lastStatus.toLowerCase()}</span>
    </div>
  );
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

// ============================================================================
// MAIN COMPONENT
// ============================================================================

function RunbookNodeComponent({ data, selected }: RunbookNodeProps) {
  const statusConfig = STATUS_CONFIG[data.status];
  const StatusIcon = statusConfig.Icon;
  const isPublished = data.status === 'PUBLISHED';
  
  return (
    <div
      className={cn(
        'min-w-[250px] max-w-[300px] rounded-lg border bg-white shadow-md transition-all',
        isPublished ? 'border-green-200' : 'border-slate-200',
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
        id="trigger"
        className="!w-3 !h-3 !bg-orange-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        id="execution"
        className="!w-3 !h-3 !bg-blue-400 !border-2 !border-white"
      />
      
      {/* Header */}
      <div className={cn('px-3 py-2 rounded-t-lg', statusConfig.bgColor)}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <BookOpen className={cn('w-4 h-4', statusConfig.color)} />
            <span className={cn('text-xs font-semibold', statusConfig.color)}>
              Runbook v{data.version}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <StatusIcon className={cn('w-3 h-3', statusConfig.color)} />
            <span className={cn('text-xs font-medium', statusConfig.color)}>
              {statusConfig.label}
            </span>
          </div>
        </div>
      </div>
      
      {/* Runbook Name */}
      <div className="px-3 py-2 border-b border-slate-100">
        <h3 className="text-sm font-medium text-slate-900 line-clamp-1">{data.name}</h3>
        {data.description && (
          <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{data.description}</p>
        )}
      </div>
      
      {/* Steps Summary */}
      <div className="px-3 py-2 border-b border-slate-100">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs text-slate-500">{data.stepCount} steps</span>
          <LastExecutionBadge stats={data.executionStats} />
        </div>
        <StepTypesSummary summary={data.stepSummary} />
      </div>
      
      {/* Triggers */}
      <div className="px-3 py-2 border-b border-slate-100">
        <div className="flex items-center justify-between">
          <span className="text-[10px] text-slate-500">Triggers</span>
          <TriggerBadges triggers={data.triggers} />
        </div>
      </div>
      
      {/* Parameters */}
      {data.parameters.length > 0 && (
        <div className="px-3 py-2 border-b border-slate-100">
          <div className="flex items-center gap-2">
            <Settings className="w-3 h-3 text-slate-400" />
            <span className="text-[10px] text-slate-500">
              {data.parameters.length} parameter{data.parameters.length !== 1 ? 's' : ''}
            </span>
          </div>
          <div className="flex flex-wrap gap-1 mt-1">
            {data.parameters.slice(0, 3).map((param) => (
              <span 
                key={param}
                className="px-1.5 py-0.5 text-[9px] bg-purple-50 text-purple-600 rounded font-mono"
              >
                {param}
              </span>
            ))}
            {data.parameters.length > 3 && (
              <span className="text-[9px] text-slate-400">
                +{data.parameters.length - 3} more
              </span>
            )}
          </div>
        </div>
      )}
      
      {/* Execution Stats */}
      {data.executionStats.total > 0 && (
        <div className="px-3 py-2 border-b border-slate-100">
          <div className="flex items-center gap-1 mb-2">
            <BarChart2 className="w-3 h-3 text-slate-400" />
            <span className="text-[10px] text-slate-500">Execution Stats</span>
          </div>
          <ExecutionStats stats={data.executionStats} />
        </div>
      )}
      
      {/* Tags */}
      {data.tags.length > 0 && (
        <div className="px-3 py-2 border-b border-slate-100">
          <div className="flex flex-wrap gap-1">
            {data.tags.slice(0, 4).map((tag) => (
              <span 
                key={tag}
                className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] bg-slate-100 text-slate-600 rounded"
              >
                <Tag className="w-2.5 h-2.5" />
                {tag}
              </span>
            ))}
            {data.tags.length > 4 && (
              <span className="text-[10px] text-slate-400">+{data.tags.length - 4}</span>
            )}
          </div>
        </div>
      )}
      
      {/* Footer */}
      <div className="px-3 py-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div
            className="w-5 h-5 rounded-full bg-slate-100 flex items-center justify-center text-[10px] font-medium text-slate-600"
            title={data.author.name}
          >
            {data.author.avatarUrl ? (
              <img src={data.author.avatarUrl} alt={data.author.name} className="w-full h-full rounded-full" />
            ) : (
              data.author.name.substring(0, 2).toUpperCase()
            )}
          </div>
          <span className="text-[10px] text-slate-500">{data.author.name}</span>
        </div>
        
        {data.executionStats.lastExecutedAt && (
          <span className="text-[10px] text-slate-400">
            Last run: {new Date(data.executionStats.lastExecutedAt).toLocaleDateString()}
          </span>
        )}
      </div>
    </div>
  );
}

export const RunbookNode = memo(RunbookNodeComponent);
export default RunbookNode;
