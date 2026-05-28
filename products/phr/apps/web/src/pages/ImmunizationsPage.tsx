import React, { useEffect, useState } from 'react';
import { Badge, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchImmunizations } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ImmunizationSummary } from '../types';

type ImmunizationStatus = NonNullable<ImmunizationSummary['status']>;

function statusLabel(status: ImmunizationStatus): string {
  return t(`immunizations.status.${status}`);
}

function statusVariant(status: ImmunizationStatus): 'default' | 'secondary' | 'destructive' {
  if (status === 'entered-in-error' || status === 'not-done' || status === 'due') return 'destructive';
  return status === 'completed' ? 'default' : 'secondary';
}

function groupTitle(status: ImmunizationStatus): string {
  return t(`immunizations.group.${status}`);
}

export function ImmunizationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [immunizations, setImmunizations] = useState<ImmunizationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchImmunizations(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setImmunizations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('immunizations.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('immunizations.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('dashboard.errorPrefix')}: {error}</div>;
  if (!immunizations.length) return <div className="empty" role="status">{t('immunizations.empty')}</div>;

  const statuses: ImmunizationStatus[] = ['completed', 'not-done', 'entered-in-error', 'due'];

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('immunizations.title')} subheader={t('immunizations.subheader')} />
        <CardContent>
          <div className="stack gap-md">
            {statuses.map((status) => {
              const group = immunizations.filter((immunization) => immunization.status === status);
              if (group.length === 0) return null;

              return (
                <section key={status} className="stack gap-sm" aria-label={groupTitle(status)}>
                  <h3>{groupTitle(status)}</h3>
                  <ul className="stack gap-sm" role="list">
                    {group.map((immunization) => {
                      const isSelected = selectedId === immunization.id;
                      return (
                        <li key={immunization.id} className="immunization-entry" role="listitem">
                          <button
                            type="button"
                            className="data-card"
                            aria-expanded={isSelected}
                            onClick={() => setSelectedId(isSelected ? null : immunization.id)}
                          >
                            <div>
                              <strong>{immunization.vaccine}</strong>
                              <p className="muted">{t('immunizations.date', { date: formatPhrDate(immunization.occurrenceDate) })}</p>
                            </div>
                            <div className="row gap-sm align-center">
                              <Badge variant={statusVariant(status)} aria-label={statusLabel(status)}>{statusLabel(status)}</Badge>
                              <Badge variant="secondary" aria-label={t('immunizations.retention.permanent')}>{t('immunizations.retention.permanent')}</Badge>
                            </div>
                          </button>
                          {isSelected && (
                            <dl className="detail-list">
                              {immunization.cvxCode != null && (
                                <>
                                  <dt>{t('immunizations.cvx')}</dt>
                                  <dd>{immunization.cvxCode}</dd>
                                </>
                              )}
                              {immunization.lotNumber != null && (
                                <>
                                  <dt>{t('immunizations.lot')}</dt>
                                  <dd>{immunization.lotNumber}</dd>
                                </>
                              )}
                              {immunization.dose != null && (
                                <>
                                  <dt>{t('immunizations.doseLabel')}</dt>
                                  <dd>{immunization.dose}</dd>
                                </>
                              )}
                              {immunization.site != null && (
                                <>
                                  <dt>{t('immunizations.site')}</dt>
                                  <dd>{immunization.site}</dd>
                                </>
                              )}
                            </dl>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
