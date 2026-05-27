import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { useParams } from 'react-router-dom';
import { fetchRecordDetail } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import { usePhrSession } from '../auth/PhrSessionContext';

export function RecordDetailPage(): React.ReactElement {
  const { recordId } = useParams();
  const { session } = usePhrSession();
  const [recordData, setRecordData] = useState<{
    record: { id: string; title: string; resourceType: string; category: string; updatedAt: string };
    fhirJson: string;
    accessAudit: { accessedAt: string; accessedBy: string };
  } | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [showRawFhir, setShowRawFhir] = useState<boolean>(false);
  const [fhirInvalid, setFhirInvalid] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !recordId) {
      setError('Session or record ID missing');
      setLoading(false);
      return;
    }

    fetchRecordDetail(session.principalId, recordId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setRecordData)
      .catch((err: unknown) => {
        const errorMessage = err instanceof Error ? err.message : 'Failed to load record detail';
        setError(errorMessage);
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
    return <div className="loading">Loading record detail...</div>;
  }

  if (error) {
    return (
      <Card>
        <CardHeader title="Record Unavailable" subheader="Error loading record" />
        <CardContent>
          <p className="error">{error}</p>
          {fhirInvalid && (
            <div className="warning-banner">
              <strong>Invalid FHIR Payload</strong>
              <p>This record contains invalid or malformed FHIR data and cannot be displayed safely.</p>
            </div>
          )}
        </CardContent>
      </Card>
    );
  }

  if (!recordData) {
    return (
      <Card>
        <CardHeader title="Record Unavailable" subheader="Record not found" />
        <CardContent>
          <p className="muted">The requested record could not be found.</p>
        </CardContent>
      </Card>
    );
  }

  const displayFhirJson = showRawFhir ? recordData.fhirJson : redactPhi(recordData.fhirJson);

  return (
    <Card>
      <CardHeader title={recordData.record.title} subheader="FHIR Record Detail" />
      <CardContent>
        <div className="stack gap-md">
          <div className="row gap-sm">
            <span className="pill">{recordData.record.resourceType}</span>
            <span className="pill ghost">{recordData.record.category}</span>
          </div>

          {/* Access audit information */}
          <div className="audit-info">
            <small className="muted">
              Accessed at: {new Date(recordData.accessAudit.accessedAt).toLocaleString()} by {recordData.accessAudit.accessedBy}
            </small>
          </div>

          {/* PHI safety controls */}
          <div className="phi-controls">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={showRawFhir}
                onChange={(e) => setShowRawFhir(e.target.checked)}
              />
              Show raw FHIR (contains PHI)
            </label>
            <button onClick={handleCopyFhir} className="btn-secondary">
              Copy FHIR
            </button>
            <button onClick={handleDownloadFhir} className="btn-secondary">
              Download FHIR
            </button>
          </div>

          {!showRawFhir && (
            <div className="phi-notice">
              <strong>PHI Redacted</strong>
              <p className="muted">Personal health information has been redacted from this view. Check "Show raw FHIR" to see the complete data.</p>
            </div>
          )}

          <pre className="code-block">{displayFhirJson}</pre>
        </div>
      </CardContent>
    </Card>
  );
}
