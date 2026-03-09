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
import { Handle, Position, type NodeProps } from '@xyflow/react';
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

export interface UserNodeData {
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

export interface UserNodeProps extends NodeProps<UserNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const USER_TYPE_CONFIG: Record<UserType, { label: string; icon: typeof User; color: string; bgColor: string }> = {
  admin: {
    label: 'Admin',
    icon: Shield,
    color: 'text-red-700',
    bgColor: 'bg-red-50 border-red-300',
  },
  user: {
    label: 'User',
    icon: User,
    color: 'text-blue-700',
    bgColor: 'bg-blue-50 border-blue-300',
  },
  manager: {
    label: 'Manager',
    icon: Briefcase,
    color: 'text-purple-700',
    bgColor: 'bg-purple-50 border-purple-300',
  },
  guest: {
    label: 'Guest',
    icon: User,
    color: 'text-gray-600',
    bgColor: 'bg-gray-50 border-gray-300',
  },
  developer: {
    label: 'Developer',
    icon: UserCog,
    color: 'text-green-700',
    bgColor: 'bg-green-50 border-green-300',
  },
  support: {
    label: 'Support',
    icon: HeadphonesIcon,
    color: 'text-orange-700',
    bgColor: 'bg-orange-50 border-orange-300',
  },
  customer: {
    label: 'Customer',
    icon: ShoppingCart,
    color: 'text-emerald-700',
    bgColor: 'bg-emerald-50 border-emerald-300',
  },
  vendor: {
    label: 'Vendor',
    icon: Briefcase,
    color: 'text-amber-700',
    bgColor: 'bg-amber-50 border-amber-300',
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
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-white"
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
            <span className="px-1.5 py-0.5 bg-white/50 text-[10px] text-gray-600 rounded flex items-center gap-1">
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
              <MoreHorizontal className="w-4 h-4 text-gray-500" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-full mt-1 bg-white rounded-lg shadow-lg border py-1 z-50 min-w-[130px]">
                <button
                  onClick={handleEdit}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Edit2 className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => data.onAddPermission?.(id)}
                  className="w-full px-3 py-1.5 text-left text-sm hover:bg-gray-50 flex items-center gap-2"
                >
                  <Shield className="w-3.5 h-3.5" /> Add Permission
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
        <h3 className="text-sm font-medium text-gray-900">{data.label}</h3>
        {data.description && (
          <p className={cn('text-xs text-gray-600 mt-1', expanded ? '' : 'line-clamp-2')}>
            {data.description}
          </p>
        )}
      </div>

      {/* Goals Preview */}
      {primaryGoals.length > 0 && (
        <div className="px-3 py-1.5">
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <Target className="w-3.5 h-3.5" />
            <span className="truncate">{primaryGoals[0].goal}</span>
            {primaryGoals.length > 1 && <span className="text-gray-400">+{primaryGoals.length - 1}</span>}
          </div>
        </div>
      )}

      {/* Quick Info */}
      <div className="px-3 py-2 border-t border-current/10 flex items-center justify-between text-xs text-gray-500">
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
              <span className="text-xs font-medium text-gray-700">Goals:</span>
              <div className="mt-1 space-y-1">
                {data.goals.map((goal, idx) => (
                  <div
                    key={idx}
                    className={cn(
                      'flex items-start gap-2 py-1 px-2 rounded text-xs',
                      goal.priority === 'primary' ? 'bg-emerald-50' : 'bg-gray-50'
                    )}
                  >
                    <Target
                      className={cn(
                        'w-3.5 h-3.5 mt-0.5 flex-shrink-0',
                        goal.priority === 'primary' ? 'text-emerald-600' : 'text-gray-400'
                      )}
                    />
                    <span className="text-gray-700">{goal.goal}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Permissions */}
          {data.permissions.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Permissions:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.permissions.map((perm) => (
                  <span
                    key={perm.name}
                    className="px-1.5 py-0.5 bg-white/50 rounded text-xs text-gray-600 flex items-center gap-1"
                    title={perm.description}
                  >
                    <CheckCircle2 className="w-3 h-3 text-green-500" />
                    {perm.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Touchpoints */}
          {data.touchpoints && data.touchpoints.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Touchpoints:</span>
              <div className="flex flex-wrap gap-1 mt-1">
                {data.touchpoints.map((tp) => (
                  <span key={tp} className="px-1.5 py-0.5 bg-blue-50 rounded text-xs text-blue-700">
                    {tp}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Pain Points */}
          {data.painPoints && data.painPoints.length > 0 && (
            <div>
              <span className="text-xs font-medium text-gray-700">Pain Points:</span>
              <ul className="mt-1 space-y-0.5">
                {data.painPoints.map((pp, idx) => (
                  <li key={idx} className="text-xs text-gray-600 flex items-start gap-1">
                    <span className="text-red-400">•</span>
                    {pp}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Notes */}
          {data.notes && (
            <div>
              <span className="text-xs font-medium text-gray-700">Notes:</span>
              <p className="text-xs text-gray-600 mt-0.5">{data.notes}</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

UserNode.displayName = 'UserNode';

export default UserNode;
