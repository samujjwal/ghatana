import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Badge, Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchConditions } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ConditionSummary } from '../types';

type ConditionFilter = ConditionSummary['status'] | 'all';

function conditionStatusLabel(status: ConditionSummary['status']): string {
  return t(`conditions.status.${status}`);
}

function isForbiddenError(message: string): boolean {
  return /forbidden|denied|unauthorized/i.test(message);
}

export function ConditionsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [conditions, setConditions] = useState<ConditionSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [filter, setFilter] = useState<ConditionFilter>('all');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchConditions(session.principalId, toSessionContext(session))
      .then(setConditions)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('conditions.error'))))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('conditions.loading')}</div>;
  if (error) {
    const message = isForbiddenError(error.message) ? t('conditions.denied') : error.message;
    return <SafeError title={t('dashboard.errorPrefix')} message={message} correlationId={error.correlationId} />;
  }

  const filteredConditions = filter === 'all' ? conditions : conditions.filter((condition) => condition.status === filter);

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('conditions.title')} subheader={t('conditions.subheader')} />
        <CardContent>
          <div className="row gap-sm wrap" role="tablist" aria-label={t('conditions.filters.label')}>
            {(['all', 'active', 'chronic', 'resolved'] as const).map((status) => (
              <Button
                key={status}
                type="button"
                role="tab"
                aria-selected={filter === status}
                variant={filter === status ? 'primary' : 'secondary'}
                onClick={() => {
                  setFilter(status);
                  setSelectedId(null);
                }}
              >
                {status === 'all' ? t('conditions.filters.all') : conditionStatusLabel(status)}
              </Button>
            ))}
          </div>

          {conditions.length === 0 ? (
            <p className="empty" role="status">{t('conditions.empty')}</p>
          ) : filteredConditions.length === 0 ? (
            <p className="muted" role="status">{t('conditions.filters.empty')}</p>
          ) : (
            <ul className="stack gap-sm" role="list">
              {filteredConditions.map((condition) => {
                const isSelected = selectedId === condition.id;
                return (
                  <li key={condition.id} className="condition-item" role="listitem">
                    <div className="data-card">
                      <div>
                        <Link to={`/conditions/${condition.id}`} className="condition-link">
                          <strong>{condition.display}</strong>
                        </Link>
                        <div className="row gap-sm align-center wrap">
                          <Badge variant={condition.status === 'active' || condition.status === 'chronic' ? 'default' : 'secondary'}>
                            {conditionStatusLabel(condition.status)}
                          </Badge>
                          {condition.code && <code>{condition.code}</code>}
                        </div>
                      </div>
                      <Button
                        type="button"
                        variant="secondary"
                        aria-expanded={isSelected}
                        onClick={() => setSelectedId(isSelected ? null : condition.id)}
                      >
                        {t('conditions.detail.toggle')}
                      </Button>
                    </div>
                    {isSelected && (
                      <dl className="detail-list" aria-label={t('conditions.detail.label', { condition: condition.display })}>
                        {condition.onsetDate != null && (
                          <>
                            <dt>{t('conditions.detail.onset')}</dt>
                            <dd>{formatPhrDate(condition.onsetDate)}</dd>
                          </>
                        )}
                        {condition.resolvedDate != null && (
                          <>
                            <dt>{t('conditions.detail.resolved')}</dt>
                            <dd>{formatPhrDate(condition.resolvedDate)}</dd>
                          </>
                        )}
                        {condition.icdCode != null && (
                          <>
                            <dt>{t('conditions.detail.icd')}</dt>
                            <dd>{condition.icdCode}</dd>
                          </>
                        )}
                      </dl>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
