import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type ActivityType = 'commit' | 'pr' | 'deployment' | 'comment';

interface Activity {
  id: string;
  type: ActivityType;
  user: { name: string; avatarUrl?: string };
  action: string;
  target?: string;
  timestamp: string;
}

interface ActivityFeedResponse {
  activities: Activity[];
  total: number;
}

// ============================================================================
// Constants
// ============================================================================

const ACTIVITY_CONFIG: Record<ActivityType, { icon: string; color: string; label: string }> = {
  commit: { icon: '⬤', color: 'text-green-400', label: 'Commits' },
  pr: { icon: '⬤', color: 'text-purple-400', label: 'Pull Requests' },
  deployment: { icon: '⬤', color: 'text-blue-400', label: 'Deployments' },
  comment: { icon: '⬤', color: 'text-yellow-400', label: 'Comments' },
};

const ACTIVITY_ICONS: Record<ActivityType, React.ReactNode> = {
  commit: (
    <svg className="h-5 w-5 text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v6m0 8v6" />
    </svg>
  ),
  pr: (
    <svg className="h-5 w-5 text-purple-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M6 3v12m0 0a3 3 0 103 3m-3-3a3 3 0 10-3 3m12-12a3 3 0 10-3-3m3 3v9a3 3 0 01-3 3H9" />
    </svg>
  ),
  deployment: (
    <svg className="h-5 w-5 text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M5 12h14M12 5l7 7-7 7" />
    </svg>
  ),
  comment: (
    <svg className="h-5 w-5 text-yellow-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M8 10h.01M12 10h.01M16 10h.01M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
    </svg>
  ),
};

// ============================================================================
// Helpers
// ============================================================================

function formatRelativeTime(dateStr: string): string {
  const now = Date.now();
  const then = new Date(dateStr).getTime();
  const diffMs = now - then;
  const diffMin = Math.floor(diffMs / 60_000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 30) return `${diffDay}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

// ============================================================================
// API
// ============================================================================

async function fetchActivities(): Promise<ActivityFeedResponse> {
  const res = await fetch('/api/activity', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load activities');
  return res.json();
}

// ============================================================================
// Component
// ============================================================================

/**
 * ActivityFeedPage — Activity Feed.
 *
 * @doc.type component
 * @doc.purpose Team activity feed with filtering and timeline
 * @doc.layer product
 */
const ActivityFeedPage: React.FC = () => {
  const [filter, setFilter] = useState<ActivityType | 'all'>('all');

  const { data, isLoading, error } = useQuery<ActivityFeedResponse>({
    queryKey: ['activityfeedpage'],
    queryFn: fetchActivities,
  });

  const activities = useMemo(() => {
    const items = data?.activities ?? [];
    if (filter === 'all') return items;
    return items.filter((a) => a.type === filter);
  }, [data, filter]);

  return (
    <div className="mx-auto max-w-4xl px-6 py-8 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Activity Feed</h1>
        <p className="mt-1 text-sm text-zinc-400">Recent team activity across the workspace</p>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => setFilter('all')}
          className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
            filter === 'all'
              ? 'bg-zinc-100 text-zinc-900'
              : 'bg-zinc-800 text-zinc-400 hover:text-zinc-200'
          }`}
        >
          All
        </button>
        {(Object.keys(ACTIVITY_CONFIG) as ActivityType[]).map((type) => (
          <button
            key={type}
            onClick={() => setFilter(type)}
            className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              filter === type
                ? 'bg-zinc-100 text-zinc-900'
                : 'bg-zinc-800 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <span className={`inline-block h-2 w-2 rounded-full ${ACTIVITY_CONFIG[type].color.replace('text-', 'bg-')}`} />
            {ACTIVITY_CONFIG[type].label}
          </button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-800 bg-red-900/20 p-4">
          <p className="text-sm text-red-400">Failed to load activity feed. Please try again later.</p>
        </div>
      ) : activities.length === 0 ? (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-12 text-center">
          <p className="text-sm text-zinc-500">No activities found{filter !== 'all' ? ` for "${ACTIVITY_CONFIG[filter].label}"` : ''}.</p>
        </div>
      ) : (
        <div className="relative">
          {/* Timeline line */}
          <div className="absolute left-5 top-0 bottom-0 w-px bg-zinc-800" />

          <div className="space-y-1">
            {activities.map((activity) => (
              <div key={activity.id} className="relative flex items-start gap-4 rounded-lg p-3 hover:bg-zinc-900/60 transition-colors">
                {/* Icon */}
                <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-zinc-800 border border-zinc-700">
                  {ACTIVITY_ICONS[activity.type]}
                </div>

                {/* Content */}
                <div className="min-w-0 flex-1 pt-0.5">
                  <p className="text-sm text-zinc-200">
                    <span className="font-medium text-zinc-100">{activity.user.name}</span>{' '}
                    {activity.action}
                    {activity.target && (
                      <span className="font-medium text-blue-400"> {activity.target}</span>
                    )}
                  </p>
                  <p className="mt-0.5 text-xs text-zinc-500">{formatRelativeTime(activity.timestamp)}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default ActivityFeedPage;
