/**
 * Bootstrap User/Persona Node Component
 *
 * Canvas node representing a user type or persona identified during bootstrapping.
 * Shows user role, permissions, and user journey details.
 *
 * @doc.type component
 * @doc.purpose Bootstrap user/persona visualization in project graph
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useState, useCallback } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  User,
  Users,
  UserCog,
  Shield,
  Briefcase,
  GraduationCap,
  ShoppingCart,
  HeadphonesIcon,
  MoreHorizontal,
  Edit2,
  Trash2,
  ChevronDown,
  ChevronUp,
  Link,
  MessageSquare,
  CheckCircle2,
  Target,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type UserType = 'admin' | 'user' | 'manager' | 'guest' | 'developer' | 'support' | 'customer' | 'vendor';

export interface UserPermission {
  readonly name: string;
  readonly description?: string;
}

export interface UserGoal {
  readonly goal: string;
  readonly priority: 'primary' | 'secondary';
}

export interface UserNodeData extends Record<string, unknown> {
  readonly label: string;
  readonly description?: string;
  readonly userType: UserType;
  readonly permissions: readonly UserPermission[];
  readonly goals: readonly UserGoal[];
  readonly touchpoints?: readonly string[];
  readonly painPoints?: readonly string[];
  readonly estimatedCount?: string; // e.g., "1,000-5,000"
  readonly notes?: string;
  readonly commentCount?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onAddPermission?: (nodeId: string) => void;
  readonly onOpenComments?: (nodeId: string) => void;
}

type UserCanvasNode = Node<UserNodeData>;

export interface UserNodeProps extends NodeProps<UserCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const USER_TYPE_CONFIG: Record<UserType, { label: string; icon: typeof User; color: string; bgColor: string }> = {
  admin: {
    label: 'Admin',
    icon: Shield,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
  user: {
    label: 'User',
    icon: User,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  manager: {
    label: 'Manager',
    icon: Briefcase,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  guest: {
    label: 'Guest',
    icon: User,
    color: 'text-muted-foreground',
    bgColor: 'bg-surface-muted border-border',
  },
  developer: {
    label: 'Developer',
    icon: UserCog,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  support: {
    label: 'Support',
    icon: HeadphonesIcon,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  customer: {
    label: 'Customer',
    icon: ShoppingCart,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
  vendor: {
    label: 'Vendor',
    icon: Briefcase,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
};

// =============================================================================
// Component
// =============================================================================

export const UserNode = memo<UserNodeProps>(({ id, data, selected }) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const config = USER_TYPE_CONFIG[data.userType];
  const UserIcon = config.icon;

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

  const primaryGoals = data.goals.filter((g) => g.priority === 'primary');
  const secondaryGoals = data.goals.filter((g) => g.priority === 'secondary');

  return (
    <div
      className={cn(
        'min-w-[200px] max-w-[280px] rounded-lg border-2 transition-all duration-200',
        config.bgColor,
        selected && 'ring-2 ring-primary ring-offset-2 shadow-lg',
        !selected && 'shadow-sm hover:shadow-md'
      )}
    >
      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-primary !border-2 !border-background"
      />

      {/* Header */}
      <div className="px-3 py-2 flex items-center justify-between border-b border-current/10">
        <div className="flex items-center gap-2">
          <div className={cn('p-1.5 rounded-full', config.bgColor)}>
            <UserIcon className={cn('w-4 h-4', config.color)} />
          </div>
          <div>
            <span className={cn('text-xs font-semibold', config.color)}>{config.label}</span>
          </div>
        </div>
        <div className="flex items-center gap-1">
          {data.estimatedCount && (
            <span className="px-1.5 py-0.5 bg-surface text-[10px] text-muted-foreground rounded flex items-center gap-1">
              <Users className="w-3 h-3" />
              {data.estimatedCount}
            </span>
          )}
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1 hover:bg-black/5 rounded"
            >
              <MoreHorizontal className="w-4 h-4 text-muted-foreground" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-surface rounded-lg shadow-lg border border-border py-1 z-50 min-w-[130px]">
                <button
                  onClick={handleEdit}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => data.onAddPermission?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted/40 flex items-center gap-2"
                >
                  <Shield className="w-3.5 h-3.5" /> Add Permission
                </button>
                <hr className="my-1" />
                <button
                  onClick={handleDelete}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-destructive-bg text-destructive flex items-center gap-2"
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
        <h3 className="text-sm font-medium text-fg">{data.label}</h3>
        {data.description && (
          <p className={cn('text-xs text-fg-muted mt-1', expanded ? '' : 'line-clamp-2')}>
            {data.description}
          </p>
        )}
      </div>

      {/* Goals Preview */}
      {primaryGoals.length > 0 && (
        <div className="px-3 py-1.5">
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <Target className="w-3.5 h-3.5" />
            <span className="truncate">{primaryGoals[0].goal}</span>
            {primaryGoals.length > 1 && <span className="text-muted-foreground">+{primaryGoals.length - 1}</span>}
          </div>
        </div>
      )}

      {/* Quick Info */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1">
            <Shield className="w-3.5 h-3.5" />
            {data.permissions.length}
          </span>
          <span className="flex items-center gap-1">
            <Target className="w-3.5 h-3.5" />
            {data.goals.length}
          </span>
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
        <button onClick={handleToggleExpand} className="hover:text-primary">
          {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
      </div>

      {/* Expanded Details */}
      {expanded && (
        <div className="px-3 py-2 border-t border-current/10 space-y-3">
          {/* Goals */}
          {data.goals.length > 0 && (
            <div>
              <span className="text-xs font-medium text-fg">Goals:</span>
              <div className="mt-1 space-y-1">
                {data.goals.map((goal, idx) => (
                  <div
                    key={idx}
                    className={cn(
                      'flex items-start gap-2 py-1 px-2 rounded text-xs',
                      goal.priority === 'primary' ? 'bg-success-bg' : 'bg-muted'
                    )}
                  >
                    <Target
                      className={cn(
                        'w-3.5 h-3.5 mt-0.5 flex-shrink-0',
                        goal.priority === 'primary' ? 'text-success-color' : 'text-muted-foreground'
                      )}
                    />
                    <span className="text-fg">{goal.goal}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Permissions */}
          {data.permissions.length > 0 && (
            <div>
              <span className="text-xs font-medium text-fg">Permissions:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.permissions.map((perm) => (
                  <span
                    key={perm.name}
                    className="px-1.5 py-0.5 bg-surface rounded text-xs text-muted-foreground flex items-center gap-1"
                    title={perm.description}
                  >
                    <CheckCircle2 className="w-3 h-3 text-success-color" />
                    {perm.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Touchpoints */}
          {data.touchpoints && data.touchpoints.length > 0 && (
            <div>
              <span className="text-xs font-medium text-fg">Touchpoints:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.touchpoints.map((tp) => (
                  <span key={tp} className="px-1.5 py-0.5 bg-info-bg rounded text-xs text-info-color">
                    {tp}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Pain Points */}
          {data.painPoints && data.painPoints.length > 0 && (
            <div>
              <span className="text-xs font-medium text-fg">Pain Points:</span>
              <ul className="mt-1 space-y-0.5">
                {data.painPoints.map((pp, idx) => (
                  <li key={idx} className="text-xs text-fg-muted flex items-start gap-1">
                    <span className="text-destructive">•</span>
                    {pp}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Notes */}
          {data.notes && (
            <div>
              <span className="text-xs font-medium text-fg">Notes:</span>
              <p className="text-xs text-fg-muted mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

UserNode.displayName = 'UserNode';

export default UserNode;
