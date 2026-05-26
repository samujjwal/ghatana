import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchImmunizations } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ImmunizationSummary } from '../types';

export function ImmunizationsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [immunizations, setImmunizations] = useState<ImmunizationSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchImmunizations(session.principalId)
      .then(setImmunizations)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('immunizations.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('immunizations.loading')}</div>;
  if (error) return <div className="error">{t('immunizations.error')}: {error}</div>;
  if (!immunizations.length) return <div className="empty">{t('immunizations.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('immunizations.title')} subheader={t('immunizations.subheader')} />
        <CardContent>
          <ul className="stack gap-sm">
            {immunizations.map((imm) => (
              <li key={imm.id} className="immunization-entry">
                <strong>{imm.vaccine}</strong>
                <span className={`badge badge--${imm.status}`}>{imm.status}</span>
                <time dateTime={imm.occurrenceDate}>{new Date(imm.occurrenceDate).toLocaleDateString()}</time>
                {imm.lotNumber != null && <span className="muted">Lot: {imm.lotNumber}</span>}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
