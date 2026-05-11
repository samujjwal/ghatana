import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { yappcApi } from '@/lib/api/client';
import { useTranslation } from '@ghatana/i18n';

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
  return yappcApi.collaboration.getTeamHub<TeamHubData>();
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
  away: 'bg-warning-bg',
  offline: 'bg-surface-muted',
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
  const { t } = useTranslation('common');

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
          {t('teamHub.loadError', { message: error instanceof Error ? error.message : 'Unknown error' })}
        </div>
      </div>
    );
  }

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  const onlineCount = data.members.filter((m) => m.status === 'online').length;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-fg-muted">{t('teamHub.title')}</h1>
        <p className="text-sm text-fg-muted mt-1">
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
            className="bg-surface border border-border rounded-xl p-4 hover:border-border transition-colors group"
          >
            <span className="text-2xl">{link.icon}</span>
            <h3 className="text-sm font-semibold text-fg-muted mt-2 group-hover:text-info-color transition-colors">
              {link.label}
            </h3>
            <p className="text-xs text-fg-muted mt-0.5">{link.description}</p>
          </Link>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Members */}
        <div className="lg:col-span-1 bg-surface border border-border rounded-xl">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="text-sm font-semibold text-fg-muted">{t('teamHub.members')}</h2>
          </div>
          <div className="divide-y divide-zinc-800 max-h-[420px] overflow-y-auto">
            {data.members.map((member) => (
              <div key={member.id} className="flex items-center gap-3 px-5 py-3 hover:bg-surface/40 transition-colors">
                <div className="relative">
                  <div className="w-8 h-8 rounded-full bg-surface-muted flex items-center justify-center text-xs font-medium text-fg-muted">
                    {member.name.split(' ').map((n) => n[0]).join('')}
                  </div>
                  <span
                    className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-border ${STATUS_COLORS[member.status]}`}
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-fg-muted truncate">{member.name}</p>
                  <p className="text-xs text-fg-muted truncate">{member.role}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Activity Feed */}
        <div className="lg:col-span-2 bg-surface border border-border rounded-xl">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="text-sm font-semibold text-fg-muted">{t('teamHub.recentActivity')}</h2>
          </div>
          <div className="divide-y divide-zinc-800 max-h-[420px] overflow-y-auto">
            {data.activity.length === 0 ? (
              <div className="px-5 py-12 text-center text-fg-muted">{t('teamHub.noActivity')}</div>
            ) : (
              data.activity.map((item) => (
                <div key={item.id} className="px-5 py-3 hover:bg-surface/40 transition-colors">
                  <div className="flex items-start gap-3">
                    <div className="w-7 h-7 rounded-full bg-surface flex items-center justify-center text-xs text-fg-muted shrink-0 mt-0.5">
                      {item.user.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-fg-muted">
                        <span className="font-medium text-fg-muted">{item.user}</span>{' '}
                        {item.action}{' '}
                        <span className="font-medium text-fg-muted">{item.target}</span>
                      </p>
                      <p className="text-xs text-fg-muted mt-0.5">{item.timestamp}</p>
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
