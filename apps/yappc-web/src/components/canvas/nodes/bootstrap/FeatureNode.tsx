/**
 * Bootstrap Feature Node Component
 *
 * Canvas node representing a feature identified during bootstrapping.
 * Extends CanvasElement pattern for consistency with canvas framework.
 *
 * @doc.type component
 * @doc.purpose Bootstrap feature visualization in project graph
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Sparkles,
  Rocket,
  Target,
  Clock,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  CheckCircle2,
  AlertCircle,
  XCircle,
  MoreHorizontal,
  Edit2,
  Trash2,
  Link,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type FeaturePhase = 'mvp' | 'v2' | 'future';
export type FeaturePriority = 'high' | 'medium' | 'low';
export type FeatureStatus = 'identified' | 'confirmed' | 'rejected';

export interface FeatureNodeData {
  readonly label: string;
  readonly description?: string;
  readonly phase: FeaturePhase;
  readonly priority: FeaturePriority;
  readonly effort: number; // days
  readonly status: FeatureStatus;
  readonly dependencies: readonly string[];
  readonly techStack?: readonly string[];
  readonly notes?: string;
  readonly commentCount?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onStatusChange?: (nodeId: string, status: FeatureStatus) => void;
  readonly onAddDependency?: (nodeId: string) => void;
  readonly onOpenComments?: (nodeId: string) => void;
}

export interface FeatureNodeProps extends NodeProps<FeatureNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const PHASE_CONFIG: Record<FeaturePhase, { label: string; color: string; bgColor: string; icon: typeof Rocket }> = {
  mvp: {
    label: 'MVP',
    color: 'text-emerald-700',
    bgColor: 'bg-emerald-50 border-emerald-200',
    icon: Rocket,
  },
  v2: {
    label: 'v2.0',
    color: 'text-blue-700',
    bgColor: 'bg-blue-50 border-blue-200',
    icon: Target,
  },
  future: {
    label: 'Future',
    color: 'text-purple-700',
    bgColor: 'bg-purple-50 border-purple-200',
    icon: Sparkles,
  },
};

const PRIORITY_CONFIG: Record<FeaturePriority, { label: string; color: string; dotColor: string }> = {
  high: { label: 'High', color: 'text-red-600', dotColor: 'bg-red-500' },
  medium: { label: 'Medium', color: 'text-yellow-600', dotColor: 'bg-yellow-500' },
  low: { label: 'Low', color: 'text-gray-500', dotColor: 'bg-gray-400' },
};

const STATUS_CONFIG: Record<FeatureStatus, { label: string; icon: typeof CheckCircle2; color: string }> = {
  identified: { label: 'Identified', icon: AlertCircle, color: 'text-gray-500' },
  confirmed: { label: 'Confirmed', icon: CheckCircle2, color: 'text-emerald-600' },
  rejected: { label: 'Rejected', icon: XCircle, color: 'text-red-500' },
};

// =============================================================================
// Component
// =============================================================================

export const FeatureNode = memo<FeatureNodeProps>(({ id, data, selected }) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const phase = PHASE_CONFIG[data.phase];
  const priority = PRIORITY_CONFIG[data.priority];
  const status = STATUS_CONFIG[data.status];
  const PhaseIcon = phase.icon;
  const StatusIcon = status.icon;

  const handleToggleExpand = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setExpanded((prev) => !prev);
  }, []);

  const handleEdit = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onEdit?.(id);
    },
    [id, data]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onDelete?.(id);
    },
    [id, data]
  );

  const handleStatusChange = useCallback(
    (newStatus: FeatureStatus) => (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onStatusChange?.(id, newStatus);
    },
    [id, data]
  );

  return (
    <div
      className={cn(
        'min-w-[220px] max-w-[300px] rounded-lg border-2 transition-all duration-200',
        phase.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <PhaseIcon className={cn('w-4 h-4', phase.color)} />
          <span className={cn('text-xs font-semibold', phase.color)}>{phase.label}</span>
        </div>
        <div className="flex items-center gap-1">
          <div className={cn('w-2 h-2 rounded-full', priority.dotColor)} title={`${priority.label} priority`} />
          <StatusIcon className={cn('w-4 h-4', status.color)} />
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1 hover:bg-black/5 rounded"
            >
              <MoreHorizontal className="w-4 h-4 text-gray-500" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-white rounded-lg shadow-lg border py-1 z-50 min-w-[120px]">
                <button
                  onClick={handleEdit}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => data.onAddDependency?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Link className="w-3.5 h-3.5" /> Link
                </button>
                <hr className="my-1" />
                <button
                  onClick={handleDelete}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-red-50 text-red-600 flex items-center gap-2"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-3 py-2">
        <h3 className="text-sm font-medium text-gray-900 line-clamp-2">{data.label}</h3>
        {data.description && (
          <p className={cn('text-xs text-gray-600 mt-1', expanded ? '' : 'line-clamp-2')}>
            {data.description}
          </p>
        )}
      </div>

      {/* Metadata */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-gray-500">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <Clock className="w-3.5 h-3.5" />
            {data.effort}d
          </span>
          {data.dependencies.length > 0 && (
            <span className="flex items-center gap-1">
              <Link className="w-3.5 h-3.5" />
              {data.dependencies.length}
            </span>
          )}
          {data.commentCount && data.commentCount > 0 && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                data.onOpenComments?.(id);
              }}
              className="flex items-center gap-1 hover:text-primary"
            >
              <MessageSquare className="w-3.5 h-3.5" />
              {data.commentCount}
            </button>
          )}
        </div>
        {(data.description || data.techStack?.length || data.notes) && (
          <button onClick={handleToggleExpand} className="hover:text-primary">
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="px-3 py-2 border-t border-current/10 space-y-2">
          {data.techStack && data.techStack.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Tech Stack:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.techStack.map((tech) => (
                  <span
                    key={tech}
                    className="px-1.5 py-0.5 bg-white/50 rounded text-xs text-gray-600"
                  >
                    {tech}
                  </span>
                ))}
              </div>
            </div>
          )}
          {data.notes && (
            <div>
              <span className="text-xs font-medium text-gray-700">Notes:</span>
              <p className="text-xs text-gray-600 mt-0.5">{data.notes}</p>
            </div>
          )}
          {/* Status Actions */}
          <div className="flex gap-1 pt-1">
            {data.status !== 'confirmed' && (
              <button
                onClick={handleStatusChange('confirmed')}
                className="px-2 py-1 text-xs bg-emerald-100 text-emerald-700 rounded hover:bg-emerald-200"
              >
                Confirm
              </button>
            )}
            {data.status !== 'rejected' && (
              <button
                onClick={handleStatusChange('rejected')}
                className="px-2 py-1 text-xs bg-red-100 text-red-700 rounded hover:bg-red-200"
              >
                Reject
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
});

FeatureNode.displayName = 'FeatureNode';

export default FeatureNode;
