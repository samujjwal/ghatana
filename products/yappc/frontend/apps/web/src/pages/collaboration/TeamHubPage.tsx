import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';

// ============================================================================
// Types
// ============================================================================

interface TeamMember {
  id: string;
  name: string;
  role: string;
  status: 'online' | 'away' | 'offline';
  avatarUrl?: string;
}

interface ActivityItem {
  id: string;
  user: string;
  action: string;
  target: string;
  timestamp: string;
}

interface QuickLink {
  label: string;
  href: string;
  icon: string;
  description: string;
}

interface TeamHubData {
  name: string;
  description: string;
  members: TeamMember[];
  activity: ActivityItem[];
}

// ============================================================================
// API
// ============================================================================

async function fetchTeamHub(): Promise<TeamHubData> {
  const res = await fetch('/api/teams/hub', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load team hub');
  return res.json();
}

// ============================================================================
// Constants
// ============================================================================

const QUICK_LINKS: QuickLink[] = [
  { label: 'Channels', href: '/collaboration/messages', icon: '💬', description: 'Team channels & DMs' },
  { label: 'Standups', href: '/collaboration/standups', icon: '📋', description: 'Daily standup notes' },
  { label: 'Calendar', href: '/collaboration/calendar', icon: '📅', description: 'Team schedule & events' },
  { label: 'Documents', href: '/collaboration/articles', icon: '📄', description: 'Shared knowledge base' },
];

const STATUS_COLORS: Record<TeamMember['status'], string> = {
  online: 'bg-emerald-400',
  away: 'bg-amber-400',
  offline: 'bg-zinc-600',
};

// ============================================================================
// Component
// ============================================================================

/**
 * TeamHubPage — Team overview hub.
 *
 * @doc.type component
 * @doc.purpose Team members, recent activity, and quick links
 * @doc.layer product
 */
const TeamHubPage: React.FC = () => {
  const { data, isLoading, error } = useQuery<TeamHubData>({
    queryKey: ['team-hub'],
    queryFn: fetchTeamHub,
  });

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load team hub: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  const onlineCount = data.members.filter((m) => m.status === 'online').length;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Team Hub</h1>
        <p className="text-sm text-zinc-400 mt-1">
          {data.name} &middot; {data.members.length} members &middot;{' '}
          <span className="text-emerald-400">{onlineCount} online</span>
        </p>
      </div>

      {/* Quick Links */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {QUICK_LINKS.map((link) => (
          <Link
            key={link.label}
            to={link.href}
            className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 hover:border-zinc-700 transition-colors group"
          >
            <span className="text-2xl">{link.icon}</span>
            <h3 className="text-sm font-semibold text-zinc-200 mt-2 group-hover:text-blue-400 transition-colors">
              {link.label}
            </h3>
            <p className="text-xs text-zinc-500 mt-0.5">{link.description}</p>
          </Link>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Members */}
        <div className="lg:col-span-1 bg-zinc-900 border border-zinc-800 rounded-xl">
          <div className="px-5 py-4 border-b border-zinc-800">
            <h2 className="text-sm font-semibold text-zinc-200">Members</h2>
          </div>
          <div className="divide-y divide-zinc-800 max-h-[420px] overflow-y-auto">
            {data.members.map((member) => (
              <div key={member.id} className="flex items-center gap-3 px-5 py-3 hover:bg-zinc-800/40 transition-colors">
                <div className="relative">
                  <div className="w-8 h-8 rounded-full bg-zinc-700 flex items-center justify-center text-xs font-medium text-zinc-300">
                    {member.name.split(' ').map((n) => n[0]).join('')}
                  </div>
                  <span
                    className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-zinc-900 ${STATUS_COLORS[member.status]}`}
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-zinc-200 truncate">{member.name}</p>
                  <p className="text-xs text-zinc-500 truncate">{member.role}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Activity Feed */}
        <div className="lg:col-span-2 bg-zinc-900 border border-zinc-800 rounded-xl">
          <div className="px-5 py-4 border-b border-zinc-800">
            <h2 className="text-sm font-semibold text-zinc-200">Recent Activity</h2>
          </div>
          <div className="divide-y divide-zinc-800 max-h-[420px] overflow-y-auto">
            {data.activity.length === 0 ? (
              <div className="px-5 py-12 text-center text-zinc-500">No recent activity</div>
            ) : (
              data.activity.map((item) => (
                <div key={item.id} className="px-5 py-3 hover:bg-zinc-800/40 transition-colors">
                  <div className="flex items-start gap-3">
                    <div className="w-7 h-7 rounded-full bg-zinc-800 flex items-center justify-center text-xs text-zinc-400 shrink-0 mt-0.5">
                      {item.user.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-zinc-300">
                        <span className="font-medium text-zinc-100">{item.user}</span>{' '}
                        {item.action}{' '}
                        <span className="font-medium text-zinc-200">{item.target}</span>
                      </p>
                      <p className="text-xs text-zinc-500 mt-0.5">{item.timestamp}</p>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default TeamHubPage;
