/**
 * W-010: Document detail page.
 * Shows metadata, versions, provenance, audit/download policy.
 */

import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Button } from '@ghatana/design-system';
import { fetchDocumentDetail, downloadDocument } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { logError } from '../utils/safeLogger';
import type { DocumentDetail } from '../types';

export function DocumentDetailPage(): React.ReactElement {
  const { documentId } = useParams<{ documentId: string }>();
  const { session } = usePhrSession();
  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !documentId) return;
    fetchDocumentDetail(documentId, session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setDocument)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load document details'))
      .finally(() => setLoading(false));
  }, [session, documentId]);

  const handleDownload = async (): Promise<void> => {
    if (!session || !documentId) return;
    setDownloading(true);
    try {
      const result = await downloadDocument(documentId, session.principalId, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      window.open(result.downloadUrl, '_blank');
    } catch (err) {
      logError('Failed to download document', undefined, { error: err });
    } finally {
      setDownloading(false);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">Loading document details...</div>;
  if (error) return <div className="error" role="alert">Failed to load document details: {error}</div>;
  if (!document) return <div className="error" role="alert">Failed to load document details</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={document.title} subheader="Document metadata and history" />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>Document ID</dt><dd>{document.id}</dd></div>
            <div><dt>Content type</dt><dd>{document.contentType}</dd></div>
            <div><dt>Uploaded at</dt><dd>{new Date(document.uploadedAt).toLocaleString()}</dd></div>
            <div><dt>Uploaded by</dt><dd>{document.uploadedBy}</dd></div>
            <div><dt>Size</dt><dd>{document.sizeKb ? `${document.sizeKb.toFixed(1)} KB` : '—'}</dd></div>
            {document.ocrStatus && (
              <div><dt>OCR status</dt><dd>{document.ocrStatus}</dd></div>
            )}
            {document.description && (
              <div><dt>Description</dt><dd>{document.description}</dd></div>
            )}
          </dl>
          <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
            <Button onClick={handleDownload} disabled={downloading} aria-busy={downloading}>
              {downloading ? 'Downloading...' : 'Download'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {document.versions && document.versions.length > 0 && (
        <Card>
          <CardHeader title="Versions" />
          <CardContent>
            <ul className="stack gap-sm" role="list">
              {document.versions.map((version) => (
                <li key={version.versionId} role="listitem">
                  <div><strong>Version: {version.versionNumber}</strong></div>
                  <div className="muted">{new Date(version.createdAt).toLocaleString()}</div>
                  <div className="muted">Created by: {version.createdBy}</div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {document.auditLog && document.auditLog.length > 0 && (
        <Card>
          <CardHeader title="Audit log" />
          <CardContent>
            <ul className="stack gap-sm" role="list">
              {document.auditLog.map((entry) => (
                <li key={entry.id} role="listitem">
                  <div><strong>{entry.action}</strong></div>
                  <div className="muted">{new Date(entry.timestamp).toLocaleString()}</div>
                  <div className="muted">Performed by: {entry.performedBy}</div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
