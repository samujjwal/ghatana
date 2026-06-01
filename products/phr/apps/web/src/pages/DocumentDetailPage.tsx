import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { useParams } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { downloadDocument, fetchDocumentDetail } from '../api/documentsApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { DocumentDetail } from '../types';

export function DocumentDetailPage(): React.ReactElement {
  const { documentId } = useParams<{ documentId: string }>();
  const { session } = usePhrSession();
  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [downloading, setDownloading] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !documentId) return;
    fetchDocumentDetail(documentId, session.principalId, toSessionContext(session))
      .then(setDocument)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('documentDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, documentId]);

  const handleDownload = async (): Promise<void> => {
    if (!session || !documentId) return;
    setDownloading(true);
    try {
      const result = await downloadDocument(documentId, session.principalId, toSessionContext(session));
      window.open(result.downloadUrl, '_blank');
    } catch (err) {
      logError('Failed to download document', undefined, { error: err });
    } finally {
      setDownloading(false);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('documentDetail.loading')}</div>;
  if (error) return <SafeError title={t('documentDetail.error.load')} message={error.message} correlationId={error.correlationId} />;
  if (!document) return <SafeError title={t('documentDetail.error.load')} message={t('documentDetail.error.load')} />;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={document.title} subheader={t('documentDetail.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('documentDetail.documentId')}</dt><dd>{document.id}</dd></div>
            <div><dt>{t('documentDetail.contentType')}</dt><dd>{document.contentType}</dd></div>
            <div><dt>{t('documentDetail.uploadedAt')}</dt><dd>{new Date(document.uploadedAt).toLocaleString()}</dd></div>
            <div><dt>{t('documentDetail.uploadedBy')}</dt><dd>{document.uploadedBy}</dd></div>
            <div><dt>{t('documentDetail.size')}</dt><dd>{document.sizeKb ? `${document.sizeKb.toFixed(1)} KB` : '-'}</dd></div>
            {document.ocrStatus && (
              <div><dt>{t('documentDetail.ocrStatus')}</dt><dd>{document.ocrStatus}</dd></div>
            )}
            {document.description && (
              <div><dt>{t('documentDetail.description')}</dt><dd>{document.description}</dd></div>
            )}
          </dl>
          <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
            <Button onClick={() => void handleDownload()} disabled={downloading} aria-busy={downloading}>
              {downloading ? t('documentDetail.downloading') : t('documentDetail.download')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {document.versions && document.versions.length > 0 && (
        <Card>
          <CardHeader title={t('documentDetail.versions')} />
          <CardContent>
            <ul className="stack gap-sm" role="list">
              {document.versions.map((version) => (
                <li key={version.versionId} role="listitem">
                  <div><strong>{t('documentDetail.version')}: {version.versionNumber}</strong></div>
                  <div className="muted">{new Date(version.createdAt).toLocaleString()}</div>
                  <div className="muted">{t('documentDetail.createdBy')}: {version.createdBy}</div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {document.auditLog && document.auditLog.length > 0 && (
        <Card>
          <CardHeader title={t('documentDetail.auditLog')} />
          <CardContent>
            <ul className="stack gap-sm" role="list">
              {document.auditLog.map((entry) => (
                <li key={entry.id} role="listitem">
                  <div><strong>{entry.action}</strong></div>
                  <div className="muted">{new Date(entry.timestamp).toLocaleString()}</div>
                  <div className="muted">{t('documentDetail.performedBy')}: {entry.performedBy}</div>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
