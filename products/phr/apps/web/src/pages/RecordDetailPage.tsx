import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader, Checkbox } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { toSessionContext } from '../api/requestApi';
import { fetchRecordDetail } from '../api/recordsApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { t } from '../i18n/phrI18n';
import { usePhrSession } from '../auth/PhrSessionContext';
import { PhrDetailPage } from '../components/PhrDetailPage';
import { formatDateTime } from '../utils/formatters';
import type { PatientRecordDetail } from '../types';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const { session, identity } = usePhrSession();
  const [recordData, setRecordData] = useState<PatientRecordDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [showRawFhir, setShowRawFhir] = useState<boolean>(false);
  const [fhirInvalid, setFhirInvalid] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !identity || !recordId) {
      setError({ message: t('recordDetail.error.context') });
      setLoading(false);
      return;
    }

    fetchRecordDetail(identity.principalId, recordId, toSessionContext(session))
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
      <PhrDetailPage
        title={t('recordDetail.unavailable.title')}
        subtitle={t('recordDetail.error.load')}
        parentPath="/records"
        parentLabel={t('phr.routes.records.label')}
        loading={false}
        error={error}
        data={null}
      >
        {fhirInvalid && (
          <div className="warning-banner">
            <strong>{t('recordDetail.invalidFhir.title')}</strong>
            <p>{t('recordDetail.invalidFhir.body')}</p>
          </div>
        )}
      </PhrDetailPage>
    );
  }

  if (!recordData) {
    return (
      <PhrDetailPage
        title={t('recordDetail.unavailable.title')}
        subtitle={t('recordDetail.notFound')}
        parentPath="/records"
        parentLabel={t('phr.routes.records.label')}
        loading={false}
        error={null}
        data={null}
      >
        <p className="muted">{t('recordDetail.notFound.body')}</p>
      </PhrDetailPage>
    );
  }

  const displayFhirJson = showRawFhir ? recordData.fhirJson : redactPhi(recordData.fhirJson);

  const actions = [
    {
      id: 'copy-fhir',
      label: t('recordDetail.copyFhir'),
      onClick: handleCopyFhir,
      variant: 'secondary' as const,
    },
    {
      id: 'download-fhir',
      label: t('recordDetail.downloadFhir'),
      onClick: handleDownloadFhir,
      variant: 'secondary' as const,
    },
  ];

  return (
    <PhrDetailPage
      title={recordData.record.title}
      subtitle={t('recordDetail.fhirDetail')}
      parentPath="/records"
      parentLabel={t('phr.routes.records.label')}
      loading={loading}
      error={error}
      data={recordData}
      actions={actions}
    >
      <div className="stack gap-md">
        <div className="row gap-sm">
          <span className="pill">{recordData.record.resourceType}</span>
          <span className="pill ghost">{recordData.record.category}</span>
        </div>

        {/* Access audit information */}
        <div className="audit-info">
          <small className="muted">
            {t('recordDetail.accessedAt', { date: formatDateTime(recordData.accessAudit.accessedAt), principal: recordData.accessAudit.accessedBy })}
          </small>
        </div>

        {/* PHI safety controls */}
        <div className="phi-controls">
          <Checkbox
            checked={showRawFhir}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setShowRawFhir(e.target.checked)}
            label={t('recordDetail.showRawFhir')}
          />
        </div>

        {!showRawFhir && (
          <div className="phi-notice">
            <strong>{t('recordDetail.phiRedacted.title')}</strong>
            <p className="muted">{t('recordDetail.phiRedacted.body')}</p>
          </div>
        )}

        <pre className="code-block">{displayFhirJson}</pre>
      </div>
    </PhrDetailPage>
  );
}
