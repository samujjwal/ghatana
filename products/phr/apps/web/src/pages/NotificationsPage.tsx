import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader, Badge, Button } from '@ghatana/design-system';
import { fetchNotifications, markNotificationRead } from '../api/notificationsApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
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
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [markingId, setMarkingId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchNotifications(session.principalId, toSessionContext(session))
      .then(setNotifications)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('notifications.error'))))
      .finally(() => setLoading(false));
  }, [session]);

  async function handleMarkRead(notificationId: string): Promise<void> {
    if (!session) return;
    setMarkingId(notificationId);
    setError(null);
    try {
      await markNotificationRead(notificationId, toSessionContext(session));
      const readAt = new Date().toISOString();
      setNotifications((current) => current.map((notification) => (
        notification.id === notificationId ? { ...notification, readAt } : notification
      )));
    } catch (err: unknown) {
      logError('Failed to mark notification as read', undefined, { notificationId, error: err });
      setError(toSafeApiErrorState(err, t('notifications.markReadError')));
    } finally {
      setMarkingId(null);
    }
  }

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('notifications.loading')}</div>;
  if (error) return <SafeError title={t('notifications.error')} message={error.message} correlationId={error.correlationId} />;

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
                {!n.readAt ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={markingId === n.id}
                    onClick={() => void handleMarkRead(n.id)}
                  >
                    {markingId === n.id ? t('notifications.markingRead') : t('notifications.markRead')}
                  </Button>
                ) : (
                  <small>{t('notifications.read')}</small>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
