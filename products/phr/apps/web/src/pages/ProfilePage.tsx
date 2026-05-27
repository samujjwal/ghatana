import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, Button, TextField, Select, FormControl, InputLabel } from '@ghatana/design-system';
import { fetchPatientProfile, updatePatientProfile } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { PatientProfileExtended, PatientProfileUpdateRequest } from '../types';

export function ProfilePage(): React.ReactElement {
  const { session } = usePhrSession();
  const [data, setData] = useState<PatientProfileExtended | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [draft, setDraft] = useState<PatientProfileUpdateRequest>({});

  useEffect(() => {
    if (!session) return;
    fetchPatientProfile({ tenantId: session.tenantId, principalId: session.principalId })
      .then((profile) => {
        setData(profile);
        setDraft({
          emergencyContact: profile.emergencyContact,
          preferredLanguage: profile.preferredLanguage,
          facilityId: profile.facilityId,
        });
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('profile.error')))
      .finally(() => setLoading(false));
  }, [session]);

  const handleEdit = (): void => {
    setSaveError(null);
    setEditing(true);
  };

  const handleCancel = (): void => {
    if (data) {
      setDraft({
        emergencyContact: data.emergencyContact,
        preferredLanguage: data.preferredLanguage,
        facilityId: data.facilityId,
      });
    }
    setEditing(false);
    setSaveError(null);
  };

  const handleSave = async (): Promise<void> => {
    if (!session) return;
    setSaving(true);
    setSaveError(null);
    try {
      const updated = await updatePatientProfile(draft, {
        tenantId: session.tenantId,
        principalId: session.principalId,
      });
      setData(updated);
      setEditing(false);
    } catch (err: unknown) {
      setSaveError(err instanceof Error ? err.message : t('profile.error.save'));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('profile.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('profile.error')}: {error}</div>;
  if (!data) return <div className="error" role="alert">{t('profile.error')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('profile.title')} subheader={t('profile.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('profile.name')}</dt><dd>{data.name}</dd></div>
            <div><dt>{t('profile.dob')}</dt><dd>{data.birthDate ?? '—'}</dd></div>
            <div><dt>{t('profile.bloodType')}</dt><dd>{data.bloodType ?? '—'}</dd></div>
            <div><dt>{t('profile.gender')}</dt><dd>{data.gender ?? '—'}</dd></div>
            <div><dt>{t('profile.mrn')}</dt><dd>{data.mrn ?? '—'}</dd></div>
            <div><dt>{t('profile.location')}</dt><dd>{data.location ?? '—'}</dd></div>
            {editing ? (
              <>
                <div>
                  <dt><label htmlFor="emergencyContact">{t('profile.emergencyContact')}</label></dt>
                  <dd>
                    <TextField
                      id="emergencyContact"
                      value={draft.emergencyContact ?? ''}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        setDraft((prev) => ({ ...prev, emergencyContact: e.target.value }))
                      }
                      aria-label={t('profile.emergencyContact')}
                    />
                  </dd>
                </div>
                <div>
                  <dt><label htmlFor="preferredLanguage">{t('profile.language')}</label></dt>
                  <dd>
                    <FormControl fullWidth>
                      <Select
                        id="preferredLanguage"
                        value={draft.preferredLanguage ?? ''}
                        onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
                          setDraft((prev) => ({ ...prev, preferredLanguage: e.target.value }))
                        }
                        aria-label={t('profile.language')}
                      >
                        <option value="en">English</option>
                        <option value="ne">नेपाली</option>
                      </Select>
                    </FormControl>
                  </dd>
                </div>
                <div>
                  <dt><label htmlFor="facilityId">{t('profile.facilityId')}</label></dt>
                  <dd>
                    <TextField
                      id="facilityId"
                      value={draft.facilityId ?? ''}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        setDraft((prev) => ({ ...prev, facilityId: e.target.value }))
                      }
                      aria-label={t('profile.facilityId')}
                    />
                  </dd>
                </div>
              </>
            ) : (
              <>
                <div><dt>{t('profile.emergencyContact')}</dt><dd>{data.emergencyContact ?? '—'}</dd></div>
                <div><dt>{t('profile.language')}</dt><dd>{data.preferredLanguage ?? '—'}</dd></div>
                <div><dt>{t('profile.facilityId')}</dt><dd>{data.facilityId ?? '—'}</dd></div>
              </>
            )}
          </dl>
          {saveError && <div className="error" role="alert">{saveError}</div>}
          <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
            {editing ? (
              <>
                <Button onClick={() => void handleSave()} disabled={saving} aria-busy={saving}>
                  {saving ? t('profile.saving') : t('profile.save')}
                </Button>
                <Button onClick={handleCancel} disabled={saving} variant="outlined">
                  {t('profile.cancel')}
                </Button>
              </>
            ) : (
              <Button onClick={handleEdit}>{t('profile.edit')}</Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
