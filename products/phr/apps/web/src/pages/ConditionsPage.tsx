import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchConditions } from '../api/clinicalApi';
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
    fetchConditions(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setConditions)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('conditions.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('conditions.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

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
              {active.length === 0 ? (
                <p className="muted">{t('conditions.emptyActive')}</p>
              ) : (
                <ul className="stack gap-sm">
                  {active.map((c) => (
                    <li key={c.id} className="condition-item">
                      <Link to={`/conditions/${c.id}`} className="condition-link">
                        <strong>{c.display}</strong>
                      </Link>
                      {c.code && <code>{c.code}</code>}
                      {c.onsetDate != null && <span className="muted"> (since {c.onsetDate})</span>}
                    </li>
                  ))}
                </ul>
              )}
              {resolved.length > 0 && (
                <>
                  <h3>{t('conditions.resolved')}</h3>
                  <ul className="stack gap-sm">
                    {resolved.map((c) => (
                      <li key={c.id} className="condition-item">
                        <Link to={`/conditions/${c.id}`} className="condition-link muted">
                          {c.display}
                        </Link>
                        {c.code && <code className="muted">{c.code}</code>}
                        {c.resolvedDate != null && <span className="muted"> (resolved {c.resolvedDate})</span>}
                      </li>
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
