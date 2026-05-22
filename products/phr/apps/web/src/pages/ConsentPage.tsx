import React, { useEffect, useState } from 'react';
import { Badge, Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDashboardData } from '../api/phrApi';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ConsentGrant } from '../types';

type ConsentBadgeTone = 'success' | 'warning' | 'danger';

const consentBadgeTone: Record<ConsentGrant['status'], ConsentBadgeTone> = {
  active: 'success',
  expiring: 'warning',
  revoked: 'danger',
};

export function ConsentPage(): React.ReactElement {
  const [consents, setConsents] = useState<ConsentGrant[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboardData()
      .then((data) => setConsents(data.consents))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.consentsLoad')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('consents.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  return (
    <Card>
      <CardHeader title={t('consents.title')} subheader={t('consents.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          {consents.map((consent) => (
            <section key={consent.id} className="data-card">
              <div>
                <strong>{consent.recipient}</strong>
                <p className="muted">
                  {consent.purpose} - {t('consents.expires', { date: formatPhrDate(consent.expiresAt) })}
                </p>
              </div>
              <div className="row gap-sm">
                <Badge tone={consentBadgeTone[consent.status]}>{consent.status}</Badge>
                <Button className="secondary-button">{t('consents.update')}</Button>
              </div>
            </section>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
