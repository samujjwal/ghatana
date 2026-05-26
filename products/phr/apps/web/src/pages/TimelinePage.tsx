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

  useEffect(() => {
    if (!session) return;
    fetchTimeline(session.principalId)
      .then(setEvents)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('timeline.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('timeline.loading')}</div>;
  if (error) return <div className="error">{t('timeline.error')}: {error}</div>;
  if (!events.length) return <div className="empty">{t('timeline.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('timeline.title')} subheader={t('timeline.subheader')} />
        <CardContent>
          <ol className="timeline-list stack gap-sm">
            {events.map((ev) => (
              <li key={ev.id} className="timeline-event">
                <time dateTime={ev.occurredAt}>{new Date(ev.occurredAt).toLocaleDateString()}</time>
                <strong>{ev.title}</strong>
                {ev.description != null && <p className="muted">{ev.description}</p>}
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
    </div>
  );
}
