import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Checkbox } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { toSessionContext } from '../api/requestApi';
import { fetchRecordDetail } from '../api/recordsApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { t } from '../i18n/phrI18n';
import { usePhrSession } from '../auth/PhrSessionContext';
import type { PatientRecordDetail } from '../types';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const { session } = usePhrSession();
  const [recordData, setRecordData] = useState<PatientRecordDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [showRawFhir, setShowRawFhir] = useState<boolean>(false);
  const [fhirInvalid, setFhirInvalid] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !recordId) {
      setError({ message: t('recordDetail.error.context') });
      setLoading(false);
      return;
    }

    fetchRecordDetail(session.principalId, recordId, toSessionContext(session))
      .then(setRecordData)
      .catch((err: unknown) => {
        const errorMessage = err instanceof Error ? err.message : t('recordDetail.error.load');
        setError({ ...toSafeApiErrorState(err, t('recordDetail.error.load')), message: errorMessage });
        // Check if error indicates invalid FHIR payload
        if (errorMessage.toLowerCase().includes('invalid') || errorMessage.toLowerCase().includes('parse')) {
          setFhirInvalid(true);
        }
      })
      .finally(() => setLoading(false));
  }, [session, recordId]);

  const handleCopyFhir = () => {
    if (recordData) {
      navigator.clipboard.writeText(recordData.fhirJson);
    }
  };

  const handleDownloadFhir = () => {
    if (recordData) {
      const blob = new Blob([recordData.fhirJson], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `record-${recordData.record.id}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
  };

  // PHI redaction function for display
  const redactPhi = (json: string): string => {
    try {
      const parsed = JSON.parse(json);
      const redacted = JSON.stringify(parsed, (key, value) => {
        const phiFields = ['name', 'given', 'family', 'birthDate', 'address', 'telecom', 'email', 'phone', 'ssn', 'nationalId'];
        if (phiFields.some(field => key.toLowerCase().includes(field))) {
          return '***REDACTED***';
        }
        return value;
      }, 2);
      return redacted;
    } catch {
      return json;
    }
  };

  if (loading) {
    return <div className="loading">{t('recordDetail.loading')}</div>;
  }

  if (error) {
    return (
      <Card>
        <CardHeader title={t('recordDetail.unavailable.title')} subheader={t('recordDetail.error.load')} />
        <CardContent>
          <SafeError message={error.message} correlationId={error.correlationId} />
          {fhirInvalid && (
            <div className="warning-banner">
              <strong>{t('recordDetail.invalidFhir.title')}</strong>
              <p>{t('recordDetail.invalidFhir.body')}</p>
            </div>
          )}
        </CardContent>
      </Card>
    );
  }

  if (!recordData) {
    return (
      <Card>
        <CardHeader title={t('recordDetail.unavailable.title')} subheader={t('recordDetail.notFound')} />
        <CardContent>
          <p className="muted">{t('recordDetail.notFound.body')}</p>
        </CardContent>
      </Card>
    );
  }

  const displayFhirJson = showRawFhir ? recordData.fhirJson : redactPhi(recordData.fhirJson);

  return (
    <Card>
      <CardHeader title={recordData.record.title} subheader={t('recordDetail.fhirDetail')} />
      <CardContent>
        <div className="stack gap-md">
          <div className="row gap-sm">
            <span className="pill">{recordData.record.resourceType}</span>
            <span className="pill ghost">{recordData.record.category}</span>
          </div>

          {/* Access audit information */}
          <div className="audit-info">
            <small className="muted">
              {t('recordDetail.accessedAt', { date: new Date(recordData.accessAudit.accessedAt).toLocaleString(), principal: recordData.accessAudit.accessedBy })}
            </small>
          </div>

          {/* PHI safety controls */}
          <div className="phi-controls">
            <Checkbox
              checked={showRawFhir}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setShowRawFhir(e.target.checked)}
              label={t('recordDetail.showRawFhir')}
            />
            <Button type="button" onClick={handleCopyFhir} variant="secondary">
              {t('recordDetail.copyFhir')}
            </Button>
            <Button type="button" onClick={handleDownloadFhir} variant="secondary">
              {t('recordDetail.downloadFhir')}
            </Button>
          </div>

          {!showRawFhir && (
            <div className="phi-notice">
              <strong>{t('recordDetail.phiRedacted.title')}</strong>
              <p className="muted">{t('recordDetail.phiRedacted.body')}</p>
            </div>
          )}

          <pre className="code-block">{displayFhirJson}</pre>
        </div>
      </CardContent>
    </Card>
  );
}
