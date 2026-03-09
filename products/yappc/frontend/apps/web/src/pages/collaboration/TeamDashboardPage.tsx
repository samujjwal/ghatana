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
import { useParams, useNavigate, Link } from 'react-router-dom';
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
} from '@ghatana/yappc-api';

// ============================================================================
// Types
// ============================================================================

interface TeamStats {
  totalMembers: number;
  activeNow: number;
  messagesThisWeek: number;
  channelsCount: number;
}

// ============================================================================
// Sub-components
// ============================================================================

const StatusIndicator: React.FC<{ status: string }> = ({ status }) => {
  const statusColors: Record<string, string> = {
    ONLINE: 'bg-green-500',
    AWAY: 'bg-yellow-500',
    BUSY: 'bg-red-500',
    OFFLINE: 'bg-gray-400',
  };

  return (
    <span
      className={cn(
        'absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-slate-800',
        statusColors[status] ?? 'bg-gray-400'
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
    OWNER: 'text-yellow-400',
    ADMIN: 'text-purple-400',
    MEMBER: 'text-blue-400',
    GUEST: 'text-gray-400',
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
      className="flex items-center gap-3 p-3 bg-slate-800 rounded-lg hover:bg-slate-700 transition-colors cursor-pointer"
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
          <div className="w-10 h-10 rounded-full bg-slate-600 flex items-center justify-center">
            <span className="text-sm font-medium text-white">
              {member.user.name.charAt(0).toUpperCase()}
            </span>
          </div>
        )}
        <StatusIndicator status={presence?.status ?? 'OFFLINE'} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-white truncate">
            {member.user.name}
          </span>
          <RoleIcon role={member.role} />
        </div>
        <p className="text-xs text-slate-400 truncate">
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
    <button
      onClick={onClick}
      className="w-full flex items-center gap-3 px-3 py-2 hover:bg-slate-700 rounded-lg transition-colors text-left"
    >
      <Icon className="w-4 h-4 text-slate-400" />
      <span className="flex-1 text-sm text-slate-300 truncate">{channel.name}</span>
      {channel.unreadCount > 0 && (
        <span className="px-2 py-0.5 bg-blue-600 rounded-full text-xs text-white">
          {channel.unreadCount}
        </span>
      )}
    </button>
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
    <div className="flex items-start gap-3 p-3 hover:bg-slate-700/50 rounded-lg transition-colors">
      <div className="p-2 bg-slate-700 rounded-lg">
        <Icon className="w-4 h-4 text-slate-400" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-slate-300">
          <span className="font-medium text-white">{activity.actor?.name}</span>{' '}
          {activity.description}
        </p>
        <span className="text-xs text-slate-500">{timeAgo}</span>
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
    <div className="bg-slate-800 rounded-lg p-4">
      <div className="flex items-center justify-between mb-2">
        <Icon className="w-5 h-5 text-slate-400" />
        {trend && (
          <span className="flex items-center gap-1 text-xs text-green-400">
            <TrendingUp className="w-3 h-3" />
            {trend}
          </span>
        )}
      </div>
      <p className="text-2xl font-bold text-white">{value}</p>
      <p className="text-sm text-slate-400">{label}</p>
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
      <div className="flex items-center justify-center min-h-screen bg-slate-900">
        <div className="animate-spin w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900">
      {/* Header */}
      <header className="border-b border-slate-700 bg-slate-800">
        <div className="max-w-7xl mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              {team.avatarUrl ? (
                <img src={team.avatarUrl} alt={team.name} className="w-12 h-12 rounded-lg" />
              ) : (
                <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
                  <span className="text-xl font-bold text-white">
                    {team.name.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <div>
                <h1 className="text-xl font-bold text-white">{team.name}</h1>
                <p className="text-sm text-slate-400">{team.description}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setShowInviteModal(true)}
                className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm font-medium text-white transition-colors"
              >
                <UserPlus className="w-4 h-4" />
                Invite
              </button>
              <button
                onClick={() => navigate(`/teams/${teamId}/settings`)}
                className="p-2 hover:bg-slate-700 rounded-lg transition-colors"
              >
                <Settings className="w-5 h-5 text-slate-400" />
              </button>
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
            <div className="flex items-center gap-4 border-b border-slate-700 pb-2">
              <button
                onClick={() => setSelectedTab('members')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'members'
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-400 hover:text-white'
                )}
              >
                <Users className="w-4 h-4 inline-block mr-2" />
                Members ({members.length})
              </button>
              <button
                onClick={() => setSelectedTab('channels')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'channels'
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-400 hover:text-white'
                )}
              >
                <Hash className="w-4 h-4 inline-block mr-2" />
                Channels ({channels.length})
              </button>
              <button
                onClick={() => setSelectedTab('activity')}
                className={cn(
                  'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
                  selectedTab === 'activity'
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-400 hover:text-white'
                )}
              >
                <Activity className="w-4 h-4 inline-block mr-2" />
                Activity
              </button>
            </div>

            {/* Members Tab */}
            {selectedTab === 'members' && (
              <div className="space-y-4">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <input
                    type="text"
                    placeholder="Search members..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
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
                  <h3 className="text-sm font-medium text-slate-400">All Channels</h3>
                  <button
                    onClick={() => navigate(`/teams/${teamId}/channels/new`)}
                    className="flex items-center gap-1 text-sm text-blue-400 hover:text-blue-300"
                  >
                    <PlusCircle className="w-4 h-4" />
                    Create Channel
                  </button>
                </div>
                <div className="bg-slate-800 rounded-lg divide-y divide-slate-700">
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
              <div className="bg-slate-800 rounded-lg divide-y divide-slate-700/50">
                {activities.map((activity) => (
                  <ActivityItem key={activity.id} activity={activity} />
                ))}
              </div>
            )}
          </div>

          {/* Right Column - Quick Actions & Info */}
          <div className="space-y-6">
            {/* Quick Actions */}
            <div className="bg-slate-800 rounded-lg p-4">
              <h3 className="text-sm font-medium text-white mb-3">Quick Actions</h3>
              <div className="space-y-2">
                <Link
                  to={`/teams/${teamId}/chat`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-slate-700 rounded-lg transition-colors"
                >
                  <MessageSquare className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-300">Open Team Chat</span>
                  <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                </Link>
                <Link
                  to={`/teams/${teamId}/docs`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-slate-700 rounded-lg transition-colors"
                >
                  <Activity className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-300">Team Documents</span>
                  <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                </Link>
                <Link
                  to={`/teams/${teamId}/calendar`}
                  className="flex items-center gap-3 px-3 py-2 hover:bg-slate-700 rounded-lg transition-colors"
                >
                  <Calendar className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-300">Team Calendar</span>
                  <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                </Link>
              </div>
            </div>

            {/* Online Members */}
            <div className="bg-slate-800 rounded-lg p-4">
              <h3 className="text-sm font-medium text-white mb-3">
                Online Now ({stats.activeNow})
              </h3>
              <div className="space-y-2">
                {members
                  .filter((m) => presenceMap.get(m.user.id)?.status === 'ONLINE')
                  .slice(0, 5)
                  .map((member) => (
                    <div key={member.id} className="flex items-center gap-2">
                      <div className="relative">
                        <div className="w-8 h-8 rounded-full bg-slate-600 flex items-center justify-center">
                          <span className="text-xs text-white">
                            {member.user.name.charAt(0)}
                          </span>
                        </div>
                        <StatusIndicator status="ONLINE" />
                      </div>
                      <span className="text-sm text-slate-300">{member.user.name}</span>
                    </div>
                  ))}
              </div>
            </div>

            {/* Team Info */}
            <div className="bg-slate-800 rounded-lg p-4">
              <h3 className="text-sm font-medium text-white mb-3">Team Info</h3>
              <div className="space-y-3 text-sm">
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Created</span>
                  <span className="text-slate-300">
                    {new Date(team.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Owner</span>
                  <span className="text-slate-300">{team.owner?.name}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Status</span>
                  <span
                    className={cn(
                      'px-2 py-0.5 rounded text-xs',
                      team.status === 'ACTIVE'
                        ? 'bg-green-500/20 text-green-400'
                        : 'bg-slate-700 text-slate-400'
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
          <div className="bg-slate-800 rounded-lg p-6 w-full max-w-md">
            <h2 className="text-lg font-semibold text-white mb-4">Invite Team Member</h2>
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
                  <label className="block text-sm text-slate-400 mb-1">Email</label>
                  <input
                    name="email"
                    type="email"
                    required
                    className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="colleague@company.com"
                  />
                </div>
                <div>
                  <label className="block text-sm text-slate-400 mb-1">Role</label>
                  <select
                    name="role"
                    className="w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="MEMBER">Member</option>
                    <option value="ADMIN">Admin</option>
                    <option value="GUEST">Guest</option>
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowInviteModal(false)}
                  className="px-4 py-2 text-sm text-slate-400 hover:text-white transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={inviting}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-sm font-medium text-white transition-colors disabled:opacity-50"
                >
                  {inviting ? 'Sending...' : 'Send Invite'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default TeamDashboardPage;
