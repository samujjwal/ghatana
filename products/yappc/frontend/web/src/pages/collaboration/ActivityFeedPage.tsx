import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { yappcApi } from '@/lib/api/client';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

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
  commit: { icon: '⬤', color: 'text-success-color', label: 'Commits' },
  pr: { icon: '⬤', color: 'text-info-color', label: 'Pull Requests' },
  deployment: { icon: '⬤', color: 'text-info-color', label: 'Deployments' },
  comment: { icon: '⬤', color: 'text-warning-color', label: 'Comments' },
};

const ACTIVITY_ICONS: Record<ActivityType, React.ReactNode> = {
  commit: (
    <svg className="h-5 w-5 text-success-color" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v6m0 8v6" />
    </svg>
  ),
  pr: (
    <svg className="h-5 w-5 text-info-color" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M6 3v12m0 0a3 3 0 103 3m-3-3a3 3 0 10-3 3m12-12a3 3 0 10-3-3m3 3v9a3 3 0 01-3 3H9" />
    </svg>
  ),
  deployment: (
    <svg className="h-5 w-5 text-info-color" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path d="M5 12h14M12 5l7 7-7 7" />
    </svg>
  ),
  comment: (
    <svg className="h-5 w-5 text-warning-color" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
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
  return yappcApi.collaboration.getActivityFeed<ActivityFeedResponse>();
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
  const { t } = useI18n();

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
        <h1 className="text-2xl font-bold text-fg-muted">{t('activityFeed.title')}</h1>
        <p className="mt-1 text-sm text-fg-muted">{t('activityFeed.subtitle')}</p>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="ghost"
          size="sm"
          aria-pressed={filter === 'all'}
          onClick={() => setFilter('all')}
          className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
            filter === 'all'
              ? 'bg-surface-muted text-fg'
              : 'bg-surface text-fg-muted hover:text-fg-muted'
          }`}
        >
          {t('activityFeed.filterAll')}
        </Button>
        {(Object.keys(ACTIVITY_CONFIG) as ActivityType[]).map((type) => (
          <Button
            key={type}
            variant="ghost"
            size="sm"
            aria-pressed={filter === type}
            onClick={() => setFilter(type)}
            className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              filter === type
                ? 'bg-surface-muted text-fg'
                : 'bg-surface text-fg-muted hover:text-fg-muted'
            }`}
          >
            <span className={`inline-block h-2 w-2 rounded-full ${ACTIVITY_CONFIG[type].color.replace('text-', 'bg-')}`} />
            {ACTIVITY_CONFIG[type].label}
          </Button>
        ))}
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-destructive-border bg-destructive-bg/20 p-4">
          <p className="text-sm text-destructive">{t('activityFeed.loadError')}</p>
        </div>
      ) : activities.length === 0 ? (
        <div className="bg-surface border border-border rounded-lg p-12 text-center">
          <p className="text-sm text-fg-muted">No activities found{filter !== 'all' ? ` for "${ACTIVITY_CONFIG[filter].label}"` : ''}.</p>
        </div>
      ) : (
        <div className="relative">
          {/* Timeline line */}
          <div className="absolute left-5 top-0 bottom-0 w-px bg-surface" />

          <div className="space-y-1">
            {activities.map((activity) => (
              <div key={activity.id} className="relative flex items-start gap-4 rounded-lg p-3 hover:bg-surface/60 transition-colors">
                {/* Icon */}
                <div className="relative z-10 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-surface border border-border">
                  {ACTIVITY_ICONS[activity.type]}
                </div>

                {/* Content */}
                <div className="min-w-0 flex-1 pt-0.5">
                  <p className="text-sm text-fg-muted">
                    <span className="font-medium text-fg-muted">{activity.user.name}</span>{' '}
                    {activity.action}
                    {activity.target && (
                      <span className="font-medium text-info-color"> {activity.target}</span>
                    )}
                  </p>
                  <p className="mt-0.5 text-xs text-fg-muted">{formatRelativeTime(activity.timestamp)}</p>
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
