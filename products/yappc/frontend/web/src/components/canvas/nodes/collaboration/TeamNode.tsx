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
import type { Node, NodeProps } from '@xyflow/react';
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

type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'GUEST';
type TeamStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

interface TeamUser {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
}

interface TeamMemberRecord {
  id: string;
  role: TeamRole;
  user: TeamUser;
}

interface TeamChannel {
  id: string;
  name: string;
  type: string;
}

interface TeamSettings {
  allowGuestInvites: boolean;
  requireApprovalToJoin: boolean;
}

interface TeamRecord {
  id: string;
  name: string;
  slug: string;
  status: TeamStatus;
  memberCount: number;
  avatarUrl?: string;
  description?: string;
  owner: TeamUser;
  members?: TeamMemberRecord[];
  channels?: TeamChannel[];
  settings?: TeamSettings;
  createdAt: string;
}

export interface TeamNodeData extends Record<string, unknown> {
  team: TeamRecord;
  onInviteMember?: () => void;
  onOpenSettings?: () => void;
  onOpenChannel?: (channelId: string) => void;
}

type TeamCanvasNode = Node<TeamNodeData, 'team'>;

const statusConfig: Record<TeamStatus, { color: string; bgColor: string; label: string }> = {
  ACTIVE: { color: 'text-success-color', bgColor: 'bg-success-bg0/20', label: 'Active' },
  INACTIVE: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20', label: 'Inactive' },
  SUSPENDED: { color: 'text-destructive', bgColor: 'bg-destructive-bg0/20', label: 'Suspended' },
};

const roleIcons: Record<TeamRole, typeof Crown> = {
  OWNER: Crown,
  ADMIN: Shield,
  MEMBER: User,
  GUEST: UserPlus,
};

const roleColors: Record<TeamRole, string> = {
  OWNER: 'text-warning-color',
  ADMIN: 'text-info-color',
  MEMBER: 'text-info-color',
  GUEST: 'text-fg-muted',
};

function TeamNode({ data }: NodeProps<TeamCanvasNode>) {
  const { team, onInviteMember, onOpenSettings, onOpenChannel } = data;

  const statusInfo = statusConfig[team.status];

  // Count members by role
  const roleCounts = (team.members ?? []).reduce(
    (acc: Record<TeamRole, number>, member: TeamMemberRecord) => {
      acc[member.role] = (acc[member.role] || 0) + 1;
      return acc;
    },
    {} as Record<TeamRole, number>
  );

  // Get recent channels (up to 4)
  const recentChannels = (team.channels ?? []).slice(0, 4);

  return (
    <div className="bg-surface rounded-lg border border-border shadow-xl min-w-[320px] max-w-[380px]">
      {/* Input Handle */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-3 h-3 bg-info-bg border-2 border-border"
      />

      {/* Header */}
      <div className="p-4 border-b border-border">
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
            <p className="text-fg-muted text-xs mt-0.5">/{team.slug}</p>
            {team.description && (
              <p className="text-fg-muted text-xs mt-1 line-clamp-2">{team.description}</p>
            )}
          </div>

          {/* Settings Button */}
          {onOpenSettings && (
            <button
              onClick={onOpenSettings}
              className="p-1.5 rounded-lg hover:bg-surface-muted text-fg-muted hover:text-white transition-colors"
              title="Team Settings"
            >
              <Settings className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Member Stats */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center justify-between mb-3">
          <span className="text-fg-muted text-sm">Members</span>
          <span className="text-white font-semibold">{team.memberCount}</span>
        </div>

        <div className="grid grid-cols-4 gap-2">
          {(['OWNER', 'ADMIN', 'MEMBER', 'GUEST'] as TeamRole[]).map((role) => {
            const Icon = roleIcons[role];
            const count = roleCounts[role] || 0;

            return (
              <div
                key={role}
                className="flex flex-col items-center p-2 rounded-lg bg-surface-muted"
              >
                <Icon className={cn('w-4 h-4 mb-1', roleColors[role])} />
                <span className="text-white font-medium text-sm">{count}</span>
                <span className="text-fg-muted text-xs capitalize">
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
            className="mt-3 w-full flex items-center justify-center gap-2 px-3 py-2 bg-info-bg/20 text-info-color rounded-lg hover:bg-info-bg/30 transition-colors text-sm font-medium"
          >
            <UserPlus className="w-4 h-4" />
            Invite Member
          </button>
        )}
      </div>

      {/* Owner Info */}
      <div className="p-4 border-b border-border">
        <span className="text-fg-muted text-xs font-medium uppercase tracking-wide">Owner</span>
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
            <p className="text-fg-muted text-xs truncate">{team.owner.email}</p>
          </div>
          <Crown className="w-4 h-4 text-warning-color" />
        </div>
      </div>

      {/* Recent Channels */}
      {recentChannels.length > 0 && (
        <div className="p-4 border-b border-border">
          <span className="text-fg-muted text-xs font-medium uppercase tracking-wide">
            Channels
          </span>
          <div className="mt-2 space-y-1">
            {recentChannels.map((channel: TeamChannel) => (
              <button
                key={channel.id}
                onClick={() => onOpenChannel?.(channel.id)}
                className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface-muted text-left transition-colors"
              >
                {channel.type === 'PUBLIC' ? (
                  <Hash className="w-4 h-4 text-fg-muted" />
                ) : (
                  <MessageSquare className="w-4 h-4 text-fg-muted" />
                )}
                <span className="text-fg text-sm truncate flex-1">
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
          <span className="text-fg-muted text-xs font-medium uppercase tracking-wide">
            Settings
          </span>
          <div className="mt-2 space-y-2">
            <div className="flex items-center justify-between text-sm">
              <span className="text-fg-muted">Guest Invites</span>
              <span
                className={cn(
                  'font-medium',
                  team.settings.allowGuestInvites ? 'text-success-color' : 'text-fg-muted'
                )}
              >
                {team.settings.allowGuestInvites ? 'Allowed' : 'Disabled'}
              </span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-fg-muted">Join Approval</span>
              <span
                className={cn(
                  'font-medium',
                  team.settings.requireApprovalToJoin ? 'text-warning-color' : 'text-fg-muted'
                )}
              >
                {team.settings.requireApprovalToJoin ? 'Required' : 'Not Required'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-3 bg-surface-muted border-t border-border rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-fg-muted">
          <span>Created {new Date(team.createdAt).toLocaleDateString()}</span>
          <div className="flex items-center gap-1">
            <Circle
              className={cn(
                'w-2 h-2',
                team.status === 'ACTIVE' ? 'fill-green-400 text-success-color' : 'fill-slate-500 text-fg-muted'
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
        className="w-3 h-3 bg-success-bg0 border-2 border-border"
        style={{ top: '40%' }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="members"
        className="w-3 h-3 bg-info-bg border-2 border-border"
        style={{ top: '60%' }}
      />
    </div>
  );
}

export default memo(TeamNode);
