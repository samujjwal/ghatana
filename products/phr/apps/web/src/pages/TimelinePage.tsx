import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchTimeline } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { TimelineEvent } from '../types';

export function TimelinePage(): React.ReactElement {
  const { session } = usePhrSession();
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<string>('');

  useEffect(() => {
    if (!session) return;
    fetchTimeline(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    }, {
      category: categoryFilter || undefined,
    })
      .then(setEvents)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('timeline.error.load')))
      .finally(() => setLoading(false));
  }, [session, categoryFilter]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('timeline.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('timeline.error')}: {error}</div>;
  if (!events.length) return <div className="empty" role="status">{t('timeline.empty')}</div>;

  // Group events by category
  const groupedEvents = events.reduce((acc, event) => {
    const category = event.type || 'other';
    if (!acc[category]) acc[category] = [];
    acc[category].push(event);
    return acc;
  }, {} as Record<string, TimelineEvent[]>);

  // Extract unique categories for filter
  const categories = Array.from(new Set(events.map(e => e.type)));

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('timeline.title')} subheader={t('timeline.subheader')} />
        <CardContent>
          {/* Category filter */}
          <div className="filter-bar" role="search">
            <label htmlFor="category-filter" className="visually-hidden">Filter by category</label>
            <select
              id="category-filter"
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
              aria-label="Filter by category"
            >
              <option value="">{t('timeline.filter.all')}</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
            {categoryFilter && (
              <button
                onClick={() => setCategoryFilter('')}
                className="filter-clear"
                aria-label={t('timeline.filter.clear')}
              >
                {t('timeline.filter.clear')}
              </button>
            )}
          </div>

          {/* Grouped timeline */}
          <div className="stack gap-md">
            {Object.entries(groupedEvents).map(([category, categoryEvents]) => (
              <div key={category} className="timeline-category">
                <h3 className="category-header">{category}</h3>
                <ol className="timeline-list stack gap-sm" role="list">
                  {categoryEvents.map((ev) => (
                    <li key={ev.id} className="timeline-event" role="listitem">
                      <button
                        className="timeline-event-button"
                        onClick={() => {
                          if (ev.resourceId) {
                            // Navigate to detail page based on resource type
                            // This would typically use React Router navigation
                            console.log('Navigate to detail:', ev.resourceId, ev.type);
                          }
                        }}
                        aria-label={`View details for ${ev.title}`}
                      >
                        <time dateTime={ev.occurredAt} aria-label={`Date: ${new Date(ev.occurredAt).toLocaleDateString()}`}>{new Date(ev.occurredAt).toLocaleDateString()}</time>
                        <strong aria-label={`Title: ${ev.title}`}>{ev.title}</strong>
                        {ev.description != null && <p className="muted" aria-label={`Description: ${ev.description}`}>{ev.description}</p>}
                      </button>
                    </li>
                  ))}
                </ol>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
