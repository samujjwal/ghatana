import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchCaregiverDependents } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { DependentEntry } from '../types';

export function CaregiverDependentsPage(): React.ReactElement {
  const [dependents, setDependents] = useState<DependentEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCaregiverDependents()
      .then(setDependents)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('caregiver.dependents.error')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('caregiver.dependents.loading')}</div>;
  if (error) return <div className="error">{t('caregiver.dependents.error')}: {error}</div>;
  if (!dependents.length) return <div className="empty">{t('caregiver.dependents.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('caregiver.dependents.title')} subheader={t('caregiver.dependents.subheader')} />
        <CardContent>
          <ul className="stack gap-sm">
            {dependents.map((dep) => (
              <li key={dep.id} className="dependent-entry">
                <strong>{dep.name}</strong>
                <span className="muted">{dep.relationship}</span>
                {dep.age != null && <span className="muted">Age {dep.age}</span>}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
