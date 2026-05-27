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
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load timeline'))
      .finally(() => setLoading(false));
  }, [session, categoryFilter]);

  if (loading) return <div className="loading">Loading timeline...</div>;
  if (error) return <div className="error">Error: {error}</div>;
  if (!events.length) return <div className="empty">No timeline events found</div>;

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
        <CardHeader title="Health Timeline" subheader="Your health events over time" />
        <CardContent>
          {/* Category filter */}
          <div className="filter-bar">
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All categories</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
            {categoryFilter && (
              <button
                onClick={() => setCategoryFilter('')}
                className="filter-clear"
              >
                Clear filter
              </button>
            )}
          </div>

          {/* Grouped timeline */}
          <div className="stack gap-md">
            {Object.entries(groupedEvents).map(([category, categoryEvents]) => (
              <div key={category} className="timeline-category">
                <h3 className="category-header">{category}</h3>
                <ol className="timeline-list stack gap-sm">
                  {categoryEvents.map((ev) => (
                    <li key={ev.id} className="timeline-event">
                      <time dateTime={ev.occurredAt}>{new Date(ev.occurredAt).toLocaleDateString()}</time>
                      <strong>{ev.title}</strong>
                      {ev.description != null && <p className="muted">{ev.description}</p>}
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
