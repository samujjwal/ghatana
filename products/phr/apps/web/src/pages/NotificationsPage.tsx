import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, Badge } from '@ghatana/design-system';
import { fetchNotifications } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { PhrMessageKey } from '../i18n/phrI18n';
import type { NotificationSummary } from '../types';

function notificationTypeLabel(type: NotificationSummary['type']): string {
  const keyMap: Record<NotificationSummary['type'], PhrMessageKey> = {
    consent_expiry: 'notifications.type.consent_expiry',
    appointment_reminder: 'notifications.type.appointment_reminder',
    lab_result: 'notifications.type.lab_result',
    emergency_access: 'notifications.type.emergency_access',
    system: 'notifications.type.system',
  };
  return t(keyMap[type]);
}

export function NotificationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [notifications, setNotifications] = useState<NotificationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchNotifications(session.principalId)
      .then(setNotifications)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('notifications.error')))
      .finally(() => setLoading(false));
  }, [session]);

  // PHI safety: Backend notification service must ensure that notification
  // title and body fields never contain PHI (patient names, record IDs, etc.).
  // Frontend displays notifications as-is; PHI redaction is enforced server-side.

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('notifications.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('notifications.error')}: {error}</div>;

  return (
    <Card>
      <CardHeader title={t('notifications.title')} subheader={t('notifications.subheader')} />
      <CardContent>
        {notifications.length === 0 ? (
          <p>{t('notifications.empty')}</p>
        ) : (
          <div className="stack gap-md">
            {notifications.map((n) => (
              <div
                key={n.id}
                className="data-card"
                aria-label={`${notificationTypeLabel(n.type)}: ${n.title}`}
              >
                <div className="stack gap-xs">
                  <Badge variant="default">{notificationTypeLabel(n.type)}</Badge>
                  {!n.readAt && (
                    <Badge variant="destructive" aria-label={t('notifications.unread')}>
                      {t('notifications.unread')}
                    </Badge>
                  )}
                </div>
                <strong>{n.title}</strong>
                <p>{n.body}</p>
                <small>{new Date(n.createdAt).toLocaleString()}</small>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
