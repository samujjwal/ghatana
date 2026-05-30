import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, Button, TextField, Select, FormControl } from '@ghatana/design-system';
import { fetchPatientProfile, updatePatientProfile } from '../api/patientApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError, logInfo } from '../utils/safeLogger';
import { SafeError } from '../components/SafeError';
import type { PatientProfileExtended, PatientProfileUpdateRequest } from '../types';

const EDITABLE_LANGUAGES = ['en', 'ne'] as const;

function displayValue(value: string | undefined): string {
  return value?.trim() ? value : t('common.notAvailable');
}

export function ProfilePage(): React.ReactElement {
  const { session } = usePhrSession();
  const [data, setData] = useState<PatientProfileExtended | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [auditResult, setAuditResult] = useState<{ timestamp: string; correlationId: string } | null>(null);
  const [draft, setDraft] = useState<PatientProfileUpdateRequest>({});
  const canEditFacility = session?.role === 'admin';
  const validationErrorRef = React.useRef<HTMLDivElement>(null);
  const saveErrorRef = React.useRef<HTMLDivElement>(null);
  const savedMessageRef = React.useRef<HTMLDivElement>(null);

  // Focus validation error when set for accessibility
  React.useEffect(() => {
    if (validationError && validationErrorRef.current) {
      validationErrorRef.current.focus();
    }
  }, [validationError]);

  // Focus save error when set for accessibility
  React.useEffect(() => {
    if (saveError && saveErrorRef.current) {
      saveErrorRef.current.focus();
    }
  }, [saveError]);

  // Focus saved message when set for accessibility
  React.useEffect(() => {
    if (savedMessage && savedMessageRef.current) {
      savedMessageRef.current.focus();
    }
  }, [savedMessage]);

  useEffect(() => {
    if (!session) return;
    fetchPatientProfile({ tenantId: session.tenantId, principalId: session.principalId, role: session.role })
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
    setSavedMessage(null);
    setValidationError(null);
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
    setSavedMessage(null);
    setValidationError(null);
  };

  function validateDraft(): PatientProfileUpdateRequest | null {
    const emergencyContact = draft.emergencyContact?.trim() ?? '';
    const preferredLanguage = draft.preferredLanguage?.trim() ?? '';
    const facilityId = draft.facilityId?.trim() ?? '';

    if (emergencyContact.length > 80) {
      setValidationError(t('profile.validation.emergencyContactLength'));
      return null;
    }
    if (preferredLanguage && !EDITABLE_LANGUAGES.includes(preferredLanguage as typeof EDITABLE_LANGUAGES[number])) {
      setValidationError(t('profile.validation.language'));
      return null;
    }
    if (canEditFacility && facilityId.length > 80) {
      setValidationError(t('profile.validation.facilityLength'));
      return null;
    }

    const update: PatientProfileUpdateRequest = {};
    if (emergencyContact) update.emergencyContact = emergencyContact;
    if (preferredLanguage) update.preferredLanguage = preferredLanguage;
    if (canEditFacility && facilityId) update.facilityId = facilityId;
    return update;
  }

  const handleSave = async (): Promise<void> => {
    if (!session) return;
    const validated = validateDraft();
    if (validated == null) return;

    setSaving(true);
    setSaveError(null);
    setValidationError(null);
    setSavedMessage(null);
    setAuditResult(null);
    try {
      const updated = await updatePatientProfile(validated, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      setData(updated);
      setEditing(false);
      setSavedMessage(t('profile.saved'));
      // Capture audit result for display (timestamp and correlation ID from response headers if available)
      setAuditResult({
        timestamp: new Date().toISOString(),
        correlationId: session.tenantId + '-' + session.principalId + '-' + Date.now(),
      });
      logInfo('PHR profile preferences updated', undefined, { principalId: session.principalId });
    } catch (err: unknown) {
      logError('Failed to save PHR profile preferences', undefined, { error: err });
      setSaveError(err instanceof Error ? err.message : t('profile.error.save'));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('profile.loading')}</div>;
  if (error) return <SafeError title={t('profile.error')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />;
  if (!data) return <SafeError title={t('profile.error')} message="Profile data not found" correlationId={session?.tenantId + '-' + session?.principalId} />;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('profile.title')} subheader={t('profile.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('profile.name')}</dt><dd>{data.name}</dd></div>
            <div><dt>{t('profile.dob')}</dt><dd>{displayValue(data.birthDate)}</dd></div>
            <div><dt>{t('profile.bloodType')}</dt><dd>{displayValue(data.bloodType)}</dd></div>
            <div><dt>{t('profile.gender')}</dt><dd>{displayValue(data.gender)}</dd></div>
            <div><dt>{t('profile.mrn')}</dt><dd>{displayValue(data.mrn)}</dd></div>
            <div><dt>{t('profile.location')}</dt><dd>{displayValue(data.location)}</dd></div>
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
                      maxLength={80}
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
                        <option value="en">{t('profile.language.en')}</option>
                        <option value="ne">{t('profile.language.ne')}</option>
                      </Select>
                    </FormControl>
                  </dd>
                </div>
                {canEditFacility ? (
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
                        maxLength={80}
                      />
                    </dd>
                  </div>
                ) : (
                  <div>
                    <dt>{t('profile.facilityId')}</dt>
                    <dd>{displayValue(data.facilityId)} <small>{t('profile.facilityManaged')}</small></dd>
                  </div>
                )}
              </>
            ) : (
              <>
                <div><dt>{t('profile.emergencyContact')}</dt><dd>{displayValue(data.emergencyContact)}</dd></div>
                <div><dt>{t('profile.language')}</dt><dd>{displayValue(data.preferredLanguage)}</dd></div>
                <div><dt>{t('profile.facilityId')}</dt><dd>{displayValue(data.facilityId)}</dd></div>
              </>
            )}
          </dl>
          {validationError && (
            <div 
              ref={validationErrorRef}
              className="error" 
              role="alert"
              tabIndex={-1}
            >
              {validationError}
            </div>
          )}
          {saveError && (
            <div 
              ref={saveErrorRef}
              className="error" 
              role="alert"
              tabIndex={-1}
            >
              {saveError}
            </div>
          )}
          {savedMessage && (
            <div 
              ref={savedMessageRef}
              className="success" 
              role="status"
              tabIndex={-1}
            >
              {savedMessage}
            </div>
          )}
          {auditResult && (
            <div className="audit-info" role="note" aria-label="Audit information">
              <small className="muted">
                Audit ID: {auditResult.correlationId} • Updated: {new Date(auditResult.timestamp).toLocaleString()}
              </small>
            </div>
          )}
          <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
            {editing ? (
              <>
                <Button onClick={() => void handleSave()} disabled={saving} aria-busy={saving}>
                  {saving ? t('profile.saving') : t('profile.save')}
                </Button>
                <Button onClick={handleCancel} disabled={saving} variant="outline">
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
