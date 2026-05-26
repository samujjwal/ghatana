import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchConditions } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ConditionSummary } from '../types';

export function ConditionsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [conditions, setConditions] = useState<ConditionSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchConditions(session.principalId)
      .then(setConditions)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('conditions.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('conditions.loading')}</div>;
  if (error) return <div className="error">{t('conditions.error')}: {error}</div>;

  const active = conditions.filter((c) => c.status === 'active');
  const resolved = conditions.filter((c) => c.status !== 'active');

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('conditions.title')} subheader={t('conditions.subheader')} />
        <CardContent>
          {conditions.length === 0 ? (
            <p className="empty">{t('conditions.empty')}</p>
          ) : (
            <>
              <h3>{t('conditions.active')}</h3>
              <ul className="stack gap-sm">
                {active.map((c) => (
                  <li key={c.id}><strong>{c.display}</strong> <code>{c.code}</code>{c.onsetDate != null && <span className="muted"> ({c.onsetDate})</span>}</li>
                ))}
              </ul>
              {resolved.length > 0 && (
                <>
                  <h3>{t('conditions.resolved')}</h3>
                  <ul className="stack gap-sm">
                    {resolved.map((c) => (
                      <li key={c.id}><span className="muted">{c.display}</span></li>
                    ))}
                  </ul>
                </>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
