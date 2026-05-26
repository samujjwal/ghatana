import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchPatientProfile } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { PatientProfileExtended } from '../types';

export function ProfilePage(): React.ReactElement {
  const [data, setData] = useState<PatientProfileExtended | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchPatientProfile()
      .then(setData)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('profile.error')))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="loading">{t('profile.loading')}</div>;
  if (error) return <div className="error">{t('profile.error')}: {error}</div>;
  if (!data) return <div className="error">{t('profile.error')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('profile.title')} subheader={t('profile.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('profile.name')}</dt><dd>{data.name}</dd></div>
            <div><dt>{t('profile.dob')}</dt><dd>{data.birthDate ?? '—'}</dd></div>
            <div><dt>{t('profile.bloodType')}</dt><dd>{data.bloodType ?? '—'}</dd></div>
            <div><dt>{t('profile.location')}</dt><dd>{data.location ?? '—'}</dd></div>
            <div><dt>{t('profile.emergencyContact')}</dt><dd>{data.emergencyContact ?? '—'}</dd></div>
            <div><dt>{t('profile.language')}</dt><dd>{data.preferredLanguage ?? '—'}</dd></div>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
