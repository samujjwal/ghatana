// ============================================================================
// TeamNode - Canvas node for visualizing team information
//
// Features:
// - Team avatar and status
// - Member count with role breakdown
// - Settings summary
// - Owner information
// - Quick actions
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Users,
  Crown,
  Shield,
  User,
  UserPlus,
  Settings,
  Archive,
  Hash,
  MessageSquare,
  Circle,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import type { Team, TeamMember, TeamRole, TeamStatus } from '@ghatana/yappc-api';

export interface TeamNodeData {
  team: Team & {
    members?: TeamMember[];
    channels?: { id: string; name: string; type: string }[];
  };
  onInviteMember?: () => void;
  onOpenSettings?: () => void;
  onOpenChannel?: (channelId: string) => void;
}

const statusConfig: Record<TeamStatus, { color: string; bgColor: string; label: string }> = {
  ACTIVE: { color: 'text-green-400', bgColor: 'bg-green-500/20', label: 'Active' },
  INACTIVE: { color: 'text-gray-400', bgColor: 'bg-gray-500/20', label: 'Inactive' },
  SUSPENDED: { color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Suspended' },
};

const roleIcons: Record<TeamRole, typeof Crown> = {
  OWNER: Crown,
  ADMIN: Shield,
  MEMBER: User,
  GUEST: UserPlus,
};

const roleColors: Record<TeamRole, string> = {
  OWNER: 'text-yellow-400',
  ADMIN: 'text-purple-400',
  MEMBER: 'text-blue-400',
  GUEST: 'text-gray-400',
};

function TeamNode({ data }: NodeProps<TeamNodeData>) {
  const { team, onInviteMember, onOpenSettings, onOpenChannel } = data;

  const statusInfo = statusConfig[team.status];

  // Count members by role
  const roleCounts = (team.members ?? []).reduce(
    (acc, member) => {
      acc[member.role] = (acc[member.role] || 0) + 1;
      return acc;
    },
    {} as Record<TeamRole, number>
  );

  // Get recent channels (up to 4)
  const recentChannels = (team.channels ?? []).slice(0, 4);

  return (
    <div className="bg-slate-800 rounded-lg border border-slate-600 shadow-xl min-w-[320px] max-w-[380px]">
      {/* Input Handle */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-3 h-3 bg-blue-500 border-2 border-slate-800"
      />

      {/* Header */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-start gap-3">
          {/* Team Avatar */}
          {team.avatarUrl ? (
            <img
              src={team.avatarUrl}
              alt={team.name}
              className="w-12 h-12 rounded-lg object-cover"
            />
          ) : (
            <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
              <Users className="w-6 h-6 text-white" />
            </div>
          )}

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-white font-semibold truncate">{team.name}</h3>
              <span
                className={cn(
                  'px-2 py-0.5 text-xs font-medium rounded-full',
                  statusInfo.bgColor,
                  statusInfo.color
                )}
              >
                {statusInfo.label}
              </span>
            </div>
            <p className="text-slate-400 text-xs mt-0.5">/{team.slug}</p>
            {team.description && (
              <p className="text-slate-500 text-xs mt-1 line-clamp-2">{team.description}</p>
            )}
          </div>

          {/* Settings Button */}
          {onOpenSettings && (
            <button
              onClick={onOpenSettings}
              className="p-1.5 rounded-lg hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
              title="Team Settings"
            >
              <Settings className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Member Stats */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-center justify-between mb-3">
          <span className="text-slate-400 text-sm">Members</span>
          <span className="text-white font-semibold">{team.memberCount}</span>
        </div>

        <div className="grid grid-cols-4 gap-2">
          {(['OWNER', 'ADMIN', 'MEMBER', 'GUEST'] as TeamRole[]).map((role) => {
            const Icon = roleIcons[role];
            const count = roleCounts[role] || 0;

            return (
              <div
                key={role}
                className="flex flex-col items-center p-2 rounded-lg bg-slate-700/50"
              >
                <Icon className={cn('w-4 h-4 mb-1', roleColors[role])} />
                <span className="text-white font-medium text-sm">{count}</span>
                <span className="text-slate-500 text-xs capitalize">
                  {role.toLowerCase()}
                </span>
              </div>
            );
          })}
        </div>

        {/* Invite Button */}
        {onInviteMember && (
          <button
            onClick={onInviteMember}
            className="mt-3 w-full flex items-center justify-center gap-2 px-3 py-2 bg-blue-500/20 text-blue-400 rounded-lg hover:bg-blue-500/30 transition-colors text-sm font-medium"
          >
            <UserPlus className="w-4 h-4" />
            Invite Member
          </button>
        )}
      </div>

      {/* Owner Info */}
      <div className="p-4 border-b border-slate-700">
        <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">Owner</span>
        <div className="flex items-center gap-2 mt-2">
          {team.owner.avatarUrl ? (
            <img
              src={team.owner.avatarUrl}
              alt={team.owner.name}
              className="w-8 h-8 rounded-full object-cover"
            />
          ) : (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-yellow-500 to-orange-600 flex items-center justify-center">
              <span className="text-white text-xs font-semibold">
                {team.owner.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm font-medium truncate">{team.owner.name}</p>
            <p className="text-slate-500 text-xs truncate">{team.owner.email}</p>
          </div>
          <Crown className="w-4 h-4 text-yellow-400" />
        </div>
      </div>

      {/* Recent Channels */}
      {recentChannels.length > 0 && (
        <div className="p-4 border-b border-slate-700">
          <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">
            Channels
          </span>
          <div className="mt-2 space-y-1">
            {recentChannels.map((channel) => (
              <button
                key={channel.id}
                onClick={() => onOpenChannel?.(channel.id)}
                className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-slate-700 text-left transition-colors"
              >
                {channel.type === 'PUBLIC' ? (
                  <Hash className="w-4 h-4 text-slate-400" />
                ) : (
                  <MessageSquare className="w-4 h-4 text-slate-400" />
                )}
                <span className="text-slate-300 text-sm truncate flex-1">
                  {channel.name}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Settings Summary */}
      {team.settings && (
        <div className="p-4">
          <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">
            Settings
          </span>
          <div className="mt-2 space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">Guest Invites</span>
              <span
                className={cn(
                  'font-medium',
                  team.settings.allowGuestInvites ? 'text-green-400' : 'text-slate-500'
                )}
              >
                {team.settings.allowGuestInvites ? 'Allowed' : 'Disabled'}
              </span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">Join Approval</span>
              <span
                className={cn(
                  'font-medium',
                  team.settings.requireApprovalToJoin ? 'text-yellow-400' : 'text-slate-500'
                )}
              >
                {team.settings.requireApprovalToJoin ? 'Required' : 'Not Required'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-3 bg-slate-900/50 border-t border-slate-700 rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>Created {new Date(team.createdAt).toLocaleDateString()}</span>
          <div className="flex items-center gap-1">
            <Circle
              className={cn(
                'w-2 h-2',
                team.status === 'ACTIVE' ? 'fill-green-400 text-green-400' : 'fill-slate-500 text-slate-500'
              )}
            />
            <span>{team.status === 'ACTIVE' ? 'Online' : 'Offline'}</span>
          </div>
        </div>
      </div>

      {/* Output Handles */}
      <Handle
        type="source"
        position={Position.Right}
        id="channels"
        className="w-3 h-3 bg-green-500 border-2 border-slate-800"
        style={{ top: '40%' }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="members"
        className="w-3 h-3 bg-purple-500 border-2 border-slate-800"
        style={{ top: '60%' }}
      />
    </div>
  );
}

export default memo(TeamNode);
