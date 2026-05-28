import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchTimeline } from '../api/patientApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { TimelineEvent } from '../types';

type TimelineFilter = TimelineEvent['type'] | 'all';

const pageSize = 5;

function timelineTypeLabel(type: TimelineEvent['type']): string {
  return t(`timeline.type.${type}`);
}

function eventDate(event: TimelineEvent): string {
  return event.occurredAt || event.date;
}

function sortEvents(events: TimelineEvent[]): TimelineEvent[] {
  return [...events].sort((a, b) => new Date(eventDate(b)).getTime() - new Date(eventDate(a)).getTime());
}

function groupEvents(events: TimelineEvent[]): Record<TimelineEvent['type'], TimelineEvent[]> {
  return events.reduce<Record<TimelineEvent['type'], TimelineEvent[]>>((acc, event) => {
    acc[event.type] = [...(acc[event.type] ?? []), event];
    return acc;
  }, {} as Record<TimelineEvent['type'], TimelineEvent[]>);
}

export function TimelinePage(): React.ReactElement {
  const { session } = usePhrSession();
  const [events, setEvents] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<TimelineFilter>('all');
  const [page, setPage] = useState<number>(1);
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchTimeline(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    }, {})
      .then(setEvents)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('timeline.error.load')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('timeline.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('timeline.error')}: {error}</div>;
  if (!events.length) return <div className="empty" role="status">{t('timeline.empty')}</div>;

  const categories = Array.from(new Set(events.map((event) => event.type))).sort();
  const filteredEvents = sortEvents(categoryFilter === 'all' ? events : events.filter((event) => event.type === categoryFilter));
  const totalPages = Math.max(1, Math.ceil(filteredEvents.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const visibleEvents = filteredEvents.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const groupedEvents = groupEvents(visibleEvents);

  const handleFilterChange = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    setCategoryFilter(event.target.value as TimelineFilter);
    setPage(1);
    setSelectedEventId(null);
  };

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('timeline.title')} subheader={t('timeline.subheader')} />
        <CardContent>
          <div className="filter-bar" role="search">
            <label htmlFor="category-filter" className="visually-hidden">{t('timeline.filter.label')}</label>
            <select
              id="category-filter"
              value={categoryFilter}
              onChange={handleFilterChange}
              className="filter-select"
              aria-label={t('timeline.filter.label')}
            >
              <option value="all">{t('timeline.filter.all')}</option>
              {categories.map((category) => (
                <option key={category} value={category}>{timelineTypeLabel(category)}</option>
              ))}
            </select>
            {categoryFilter !== 'all' && (
              <button
                type="button"
                onClick={() => {
                  setCategoryFilter('all');
                  setPage(1);
                  setSelectedEventId(null);
                }}
                className="filter-clear"
                aria-label={t('timeline.filter.clear')}
              >
                {t('timeline.filter.clear')}
              </button>
            )}
          </div>

          <div className="stack gap-md">
            {Object.entries(groupedEvents).map(([category, categoryEvents]) => (
              <section key={category} className="timeline-category" aria-label={timelineTypeLabel(category as TimelineEvent['type'])}>
                <h3 className="category-header">{timelineTypeLabel(category as TimelineEvent['type'])}</h3>
                <ol className="timeline-list stack gap-sm" role="list">
                  {categoryEvents.map((event) => {
                    const isSelected = selectedEventId === event.id;
                    return (
                      <li key={event.id} className="timeline-event" role="listitem">
                        <button
                          type="button"
                          className="timeline-event-button"
                          onClick={() => setSelectedEventId(isSelected ? null : event.id)}
                          aria-expanded={isSelected}
                          aria-label={t('timeline.detail.open', { title: event.title })}
                        >
                          <time dateTime={eventDate(event)}>{formatPhrDate(eventDate(event))}</time>
                          <strong>{event.title}</strong>
                          <p className="muted">{event.summary}</p>
                        </button>
                        {isSelected && (
                          <div className="timeline-event-detail" role="region" aria-label={t('timeline.detail.label', { title: event.title })}>
                            <dl className="detail-list">
                              <dt>{t('timeline.detail.type')}</dt>
                              <dd>{timelineTypeLabel(event.type)}</dd>
                              <dt>{t('timeline.detail.date')}</dt>
                              <dd>{formatPhrDate(eventDate(event))}</dd>
                              {event.description != null && (
                                <>
                                  <dt>{t('timeline.detail.description')}</dt>
                                  <dd>{event.description}</dd>
                                </>
                              )}
                              {event.resourceId != null && (
                                <>
                                  <dt>{t('timeline.detail.resource')}</dt>
                                  <dd>{event.resourceId}</dd>
                                </>
                              )}
                            </dl>
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ol>
              </section>
            ))}
          </div>

          <nav className="pagination row gap-sm align-center" aria-label={t('timeline.pagination.label')}>
            <button
              type="button"
              className="secondary"
              disabled={currentPage === 1}
              onClick={() => setPage((value) => Math.max(1, value - 1))}
            >
              {t('timeline.pagination.previous')}
            </button>
            <span>{t('timeline.pagination.status', { page: currentPage, pages: totalPages })}</span>
            <button
              type="button"
              className="secondary"
              disabled={currentPage === totalPages}
              onClick={() => setPage((value) => Math.min(totalPages, value + 1))}
            >
              {t('timeline.pagination.next')}
            </button>
          </nav>
        </CardContent>
      </Card>
    </div>
  );
}
