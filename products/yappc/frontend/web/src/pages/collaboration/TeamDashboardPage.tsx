// @ts-nocheck
/**
 * TeamDashboardPage
 *
 * @description Main team management and overview page.
 * Displays team members, activity, channels, and collaboration tools.
 *
 * @route /teams/:teamId
 * @doc.phase 5
 * @doc.type page
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { useAtom } from 'jotai';
import {
  Users,
  Hash,
  MessageSquare,
  Activity,
  Settings,
  UserPlus,
  Bell,
  Search,
  MoreHorizontal,
  Crown,
  Shield,
  User,
  Mail,
  Calendar,
  Clock,
  TrendingUp,
  Circle,
  PlusCircle,
  ChevronRight,
  Filter,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import {
  useTeam,
  useTeamMembers,
  useChannels,
  useTeamPresence,
  useActivityFeed,
  useInviteTeamMember,
  type Team,
  type TeamMember,
  type Channel,
  type UserPresence,
  type Activity as ActivityType,
  type TeamRole,
} from '@ghatana/yappc-api-app';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

interface TeamStats {
  totalMembers: number;
  activeNow: number;
  messagesThisWeek: number;
  channelsCount: number;
}

const NativeButton = React.forwardRef<HTMLButtonElement, React.ButtonHTMLAttributes<HTMLButtonElement>>((props, ref) =>
  React.createElement('button', { ...props, ref }),
);
NativeButton.displayName = 'NativeButton';

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>((props, ref) =>
  React.createElement('input', { ...props, ref }),
);
NativeInput.displayName = 'NativeInput';

const NativeSelect = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>((props, ref) =>
  React.createElement('select', { ...props, ref }),
);
NativeSelect.displayName = 'NativeSelect';

// ============================================================================
// Sub-components
// ============================================================================

const StatusIndicator: React.FC<{ status: string }> = ({ status }) => {
  const statusColors: Record<string, string> = {
    ONLINE: 'bg-success-color',
    AWAY: 'bg-warning-color',
    BUSY: 'bg-destructive',
    OFFLINE: 'bg-muted-foreground',
  };

  return (
    <span
      className={cn(
        'absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-surface',
        statusColors[status] ?? 'bg-muted-foreground'
      )}
    />
  );
};

const RoleIcon: React.FC<{ role: TeamRole }> = ({ role }) => {
  const icons: Record<TeamRole, typeof Crown> = {
    OWNER: Crown,
    ADMIN: Shield,
    MEMBER: User,
    GUEST: User,
  };
  const Icon = icons[role];
  const colors: Record<TeamRole, string> = {
    OWNER: 'text-warning-color',
    ADMIN: 'text-info-color',
    MEMBER: 'text-info-color',
    GUEST: 'text-fg-muted',
  };

  return <Icon className={cn('w-4 h-4', colors[role])} />;
};

const MemberCard: React.FC<{
  member: TeamMember;
  presence?: UserPresence;
  onViewProfile: () => void;
}> = ({ member, presence, onViewProfile }) => {
  return (
    <div
      className="flex items-center gap-3 p-3 bg-surface rounded-lg hover:bg-surface-muted transition-colors cursor-pointer"
      onClick={onViewProfile}
    >
      <div className="relative flex-shrink-0">
        {member.user.avatarUrl ? (
          <img
            src={member.user.avatarUrl}
            alt={member.user.name}
            className="w-10 h-10 rounded-full"
          />
        ) : (
          <div className="w-10 h-10 rounded-full bg-muted flex items-center justify-center">
            <span className="text-sm font-medium text-fg">
              {member.user.name.charAt(0).toUpperCase()}
            </span>
          </div>
        )}
        <StatusIndicator status={presence?.status ?? 'OFFLINE'} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-fg truncate">
            {member.user.name}
          </span>
          <RoleIcon role={member.role} />
        </div>
        <p className="text-xs text-fg-muted truncate">
          {presence?.statusMessage ?? member.user.email}
        </p>
      </div>
    </div>
  );
};

const ChannelItem: React.FC<{
  channel: Channel;
  onClick: () => void;
}> = ({ channel, onClick }) => {
  const typeIcons: Record<string, typeof Hash> = {
    PUBLIC: Hash,
    PRIVATE: Shield,
    DIRECT_MESSAGE: MessageSquare,
    GROUP_DM: Users,
  };
  const Icon = typeIcons[channel.type] ?? Hash;

  return (
    <NativeButton
      onClick={onClick}
      className="w-full flex items-center gap-3 px-3 py-2 hover:bg-surface-muted rounded-lg transition-colors text-left"
    >
      <Icon className="w-4 h-4 text-fg-muted" />
      <span className="flex-1 text-sm text-fg truncate">{channel.name}</span>
      {channel.unreadCount > 0 && (
        <span className="px-2 py-0.5 bg-primary rounded-full text-xs text-primary-foreground">
          {channel.unreadCount}
        </span>
      )}
    </NativeButton>
  );
};

const ActivityItem: React.FC<{ activity: ActivityType }> = ({ activity }) => {
  const getActivityIcon = (type: string) => {
    const icons: Record<string, typeof Activity> = {
      MESSAGE: MessageSquare,
      MEMBER_JOINED: UserPlus,
      CHANNEL_CREATED: PlusCircle,
      MENTION: Bell,
    };
    return icons[type] ?? Activity;
  };

  const Icon = getActivityIcon(activity.type);
  const timeAgo = useMemo(() => {
    const diff = Date.now() - new Date(activity.timestamp).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }, [activity.timestamp]);

  return (
    <div className="flex items-start gap-3 p-3 hover:bg-surface-muted rounded-lg transition-colors">
      <div className="p-2 bg-muted rounded-lg">
        <Icon className="w-4 h-4 text-fg-muted" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-fg">
          <span className="font-medium text-fg">{activity.actor?.name}</span>{' '}
          {activity.description}
        </p>
        <span className="text-xs text-fg-muted">{timeAgo}</span>
      </div>
    </div>
  );
};

const StatCard: React.FC<{
  icon: typeof Users;
  label: string;
  value: number | string;
  trend?: string;
}> = ({ icon: Icon, label, value, trend }) => {
  return (
    <div className="bg-surface rounded-lg p-4 border border-border">
      <div className="flex items-center justify-between mb-2">
        <Icon className="w-5 h-5 text-fg-muted" />
        {trend && (
          <span className="flex items-center gap-1 text-xs text-success-color">
            <TrendingUp className="w-3 h-3" />
            {trend}
          </span>
        )}
      </div>
      <p className="text-2xl font-bold text-fg">{value}</p>
      <p className="text-sm text-fg-muted">{label}</p>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

const TeamDashboardPage: React.FC = () => {
  const { teamId } = useParams<{ teamId: string }>();
  const navigate = useNavigate();

  // State
  const [searchQuery, setSearchQuery] = useState('');
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [selectedTab, setSelectedTab] = useState<'members' | 'channels' | 'activity'>('members');
  const { t } = useTranslation('common');

  // Data queries
  const { data: teamData, loading: teamLoading } = useTeam(teamId ?? '');
  const { data: membersData } = useTeamMembers(teamId ?? '');
  const { data: channelsData } = useChannels(teamId ?? '');
  const { data: presenceData } = useTeamPresence(teamId ?? '');
  const { data: activityData } = useActivityFeed(teamId ?? '', { limit: 20 });

  // Mutations
  const [inviteMember, { loading: inviting }] = useInviteTeamMember();

  const team = teamData?.team;
  const members = membersData?.teamMembers ?? [];
  const channels = channelsData?.channels ?? [];
  const presenceMap = useMemo(() => {
    const map = new Map<string, UserPresence>();
    presenceData?.teamPresence?.forEach((p) => map.set(p.userId, p));
    return map;
  }, [presenceData]);
  const activities = activityData?.activityFeed ?? [];

  // Computed
  const stats: TeamStats = useMemo(() => ({
    totalMembers: members.length,
    activeNow: Array.from(presenceMap.values()).filter((p) => p.status === 'ONLINE').length,
    messagesThisWeek: activities.filter((a) => a.type === 'MESSAGE').length,
    channelsCount: channels.length,
  }), [members, presenceMap, activities, channels]);

  const filteredMembers = useMemo(() => {
    if (!searchQuery) return members;
    const query = searchQuery.toLowerCase();
    return members.filter(
      (m) =>
        m.user.name.toLowerCase().includes(query) ||
        m.user.email.toLowerCase().includes(query)
    );
  }, [members, searchQuery]);

  // Handlers
  const handleInvite = useCallback(async (email: string, role: TeamRole) => {
    if (!teamId) return;
    await inviteMember({ teamId, email, role });
    setShowInviteModal(false);
  }, [teamId, inviteMember]);

  const handleViewProfile = useCallback((userId: string) => {
    navigate(`/teams/${teamId}/members/${userId}`);
  }, [teamId, navigate]);

  const handleOpenChannel = useCallback((channelId: string) => {
    navigate(`/teams/${teamId}/channels/${channelId}`);
  }, [teamId, navigate]);

  if (teamLoading || !team) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-background">
        <div className="animate-spin w-8 h-8 border-2 border-primary border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-surface">
        <div className="max-w-7xl mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              {team.avatarUrl ? (
                <img src={team.avatarUrl} alt={team.name} className="w-12 h-12 rounded-lg" />
              ) : (
                <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-primary to-info-color flex items-center justify-center">
                  <span className="text-xl font-bold text-primary-foreground">
                    {team.name.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <div>
                <h1 className="text-xl font-bold text-fg">{team.name}</h1>
                <p className="text-sm text-fg-muted">{team.description}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <NativeButton
                onClick={() => setShowInviteModal(true)}
                className="flex items-center gap-2 px-4 py-2 bg-primary hover:opacity-90 rounded-lg text-sm font-medium text-primary-foreground transition-colors"
              >
                <UserPlus className="w-4 h-4" />
                Invite
              </NativeButton>
              <NativeButton
                onClick={() => navigate(`/teams/${teamId}/settings`)}
                className="p-2 hover:bg-surface-muted rounded-lg transition-colors"
                aria-label={t('teamDashboard.settings')}
              >
                <Settings className="w-5 h-5 text-fg-muted" />
              </NativeButton>
            </div>
          </div>
        </div>
      </header>

      {/* Stats */}
      <section className="max-w-7xl mx-auto px-6 py-6">
        <div className="grid grid-cols-4 gap-4">
          <StatCard icon={Users} label="Total Members" value={stats.totalMembers} />
          <StatCard icon={Circle} label="Active Now" value={stats.activeNow} trend="+2" />
          <StatCard icon={MessageSquare} label="Messages This Week" value={stats.messagesThisWeek} trend="+12%" />
          <StatCard icon={Hash} label="Channels" value={stats.channelsCount} />
        </div>
      </section>

      {/* Main Content */}
      <section className="max-w-7xl mx-auto px-6 pb-12">
        <div className="grid grid-cols-3 gap-6">
          {/* Left Column - Members/Channels */}
          <div className="col-span-2 space-y-6">
            {/* Tabs */}
            <div className="flex items-center gap-4 border-b border-border pb-2">
              <NativeButton
                onClick={() => setSelectedTab('members')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'members'
                    ? 'bg-primary text-primary-foreground'
                    : 'text-fg-muted hover:text-fg'
                )}
              >
                <Users className="w-4 h-4 inline-block mr-2" />
                Members ({members.length})
              </NativeButton>
              <NativeButton
                onClick={() => setSelectedTab('channels')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'channels'
                    ? 'bg-primary text-primary-foreground'
                    : 'text-fg-muted hover:text-fg'
                )}
              >
                <Hash className="w-4 h-4 inline-block mr-2" />
                Channels ({channels.length})
              </NativeButton>
              <NativeButton
                onClick={() => setSelectedTab('activity')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'activity'
                    ? 'bg-primary text-primary-foreground'
                    : 'text-fg-muted hover:text-fg'
                )}
              >
                <Activity className="w-4 h-4 inline-block mr-2" />
                Activity
              </NativeButton>
            </div>

            {/* Members Tab */}
            {selectedTab === 'members' && (
              <div className="space-y-4">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
                  <NativeInput
                    type="text"
                    placeholder={t('teamDashboard.searchMembers')}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 bg-surface border border-border rounded-lg text-sm text-fg placeholder:text-fg-muted focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {filteredMembers.map((member) => (
                    <MemberCard
                      key={member.id}
                      member={member}
                      presence={presenceMap.get(member.user.id)}
                      onViewProfile={() => handleViewProfile(member.user.id)}
                    />
                  ))}
                </div>
              </div>
            )}

            {/* Channels Tab */}
            {selectedTab === 'channels' && (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-medium text-fg-muted">All Channels</h3>
                  <NativeButton
                    onClick={() => navigate(`/teams/${teamId}/channels/new`)}
                    className="flex items-center gap-1 text-sm text-info-color hover:opacity-80"
                  >
                    <PlusCircle className="w-4 h-4" />
                    Create Channel
                  </NativeButton>
                </div>
                <div className="bg-surface rounded-lg divide-y divide-border border border-border">
                  {channels.map((channel) => (
                    <ChannelItem
                      key={channel.id}
                      channel={channel}
                      onClick={() => handleOpenChannel(channel.id)}
                    />
                  ))}
                </div>
              </div>
            )}

            {/* Activity Tab */}
            {selectedTab === 'activity' && (
              <div className="bg-surface rounded-lg divide-y divide-border border border-border">
                {activities.map((activity) => (
                  <ActivityItem key={activity.id} activity={activity} />
                ))}
              </div>
            )}
          </div>

          {/* Right Column - Quick Actions & Info */}
          <div className="space-y-6">
            {/* Quick Actions */}
            <div className="bg-surface rounded-lg p-4 border border-border">
              <h3 className="text-sm font-medium text-fg mb-3">Quick Actions</h3>
              <div className="space-y-2">
                <Link
                  to={`/teams/${teamId}/chat`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-surface-muted rounded-lg transition-colors"
                >
                  <MessageSquare className="w-4 h-4 text-fg-muted" />
                  <span className="text-sm text-fg">Open Team Chat</span>
                  <ChevronRight className="w-4 h-4 text-fg-muted ml-auto" />
                </Link>
                <Link
                  to={`/teams/${teamId}/docs`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-surface-muted rounded-lg transition-colors"
                >
                  <Activity className="w-4 h-4 text-fg-muted" />
                  <span className="text-sm text-fg">Team Documents</span>
                  <ChevronRight className="w-4 h-4 text-fg-muted ml-auto" />
                </Link>
                <Link
                  to={`/teams/${teamId}/calendar`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-surface-muted rounded-lg transition-colors"
                >
                  <Calendar className="w-4 h-4 text-fg-muted" />
                  <span className="text-sm text-fg">Team Calendar</span>
                  <ChevronRight className="w-4 h-4 text-fg-muted ml-auto" />
                </Link>
              </div>
            </div>

            {/* Online Members */}
            <div className="bg-surface rounded-lg p-4 border border-border">
              <h3 className="text-sm font-medium text-fg mb-3">
                Online Now ({stats.activeNow})
              </h3>
              <div className="space-y-2">
                {members
                  .filter((m) => presenceMap.get(m.user.id)?.status === 'ONLINE')
                  .slice(0, 5)
                  .map((member) => (
                    <div key={member.id} className="flex items-center gap-2">
                      <div className="relative">
                        <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
                          <span className="text-xs text-fg">
                            {member.user.name.charAt(0)}
                          </span>
                        </div>
                        <StatusIndicator status="ONLINE" />
                      </div>
                      <span className="text-sm text-fg">{member.user.name}</span>
                    </div>
                  ))}
              </div>
            </div>

            {/* Team Info */}
            <div className="bg-surface rounded-lg p-4 border border-border">
              <h3 className="text-sm font-medium text-fg mb-3">Team Info</h3>
              <div className="space-y-3 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Created</span>
                  <span className="text-fg">
                    {new Date(team.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Owner</span>
                  <span className="text-fg">{team.owner?.name}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-fg-muted">Status</span>
                  <span
                    className={cn(
                      'px-2 py-0.5 rounded text-xs',
                      team.status === 'ACTIVE'
                        ? 'bg-success-bg text-success-color border border-success-border'
                        : 'bg-muted text-fg-muted'
                    )}
                  >
                    {team.status}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Invite Modal - Simplified inline implementation */}
      {showInviteModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-surface rounded-lg border border-border p-6 w-full max-w-md">
            <h2 className="text-lg font-semibold text-fg mb-4">Invite Team Member</h2>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const formData = new FormData(e.currentTarget);
                handleInvite(
                  formData.get('email') as string,
                  formData.get('role') as TeamRole
                );
              }}
            >
              <div className="space-y-4">
                <div>
                  <label className="block text-sm text-fg-muted mb-1">Email</label>
                  <NativeInput
                    name="email"
                    type="email"
                    required
                    className="w-full px-3 py-2 bg-surface-muted border border-border rounded-lg text-fg focus:outline-none focus:ring-2 focus:ring-primary"
                    placeholder={t('teamDashboard.invitePlaceholder')}
                  />
                </div>
                <div>
                  <label className="block text-sm text-fg-muted mb-1">Role</label>
                  <NativeSelect
                    name="role"
                    className="w-full px-3 py-2 bg-surface-muted border border-border rounded-lg text-fg focus:outline-none focus:ring-2 focus:ring-primary"
                  >
                    <option value="MEMBER">{t('teamDashboard.role.member')}</option>
                    <option value="ADMIN">{t('teamDashboard.role.admin')}</option>
                    <option value="GUEST">{t('teamDashboard.role.guest')}</option>
                  </NativeSelect>
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6">
                <NativeButton
                  type="button"
                  onClick={() => setShowInviteModal(false)}
                  className="px-4 py-2 text-sm text-fg-muted hover:text-fg transition-colors"
                >
                  Cancel
                </NativeButton>
                <NativeButton
                  type="submit"
                  disabled={inviting}
                  className="px-4 py-2 bg-primary hover:opacity-90 rounded-lg text-sm font-medium text-primary-foreground transition-colors disabled:opacity-50"
                >
                  {inviting ? 'Sending...' : 'Send Invite'}
                </NativeButton>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default TeamDashboardPage;
