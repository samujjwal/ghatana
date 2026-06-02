import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { downloadDocument, fetchDocumentDetail } from '../api/documentsApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { PhrDetailPage } from '../components/PhrDetailPage';
import { formatDateTime, formatFileSize } from '../utils/formatters';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { DocumentDetail } from '../types';

export function DocumentDetailPage(): React.ReactElement {
  const { documentId } = useParams<{ documentId: string }>();
  const { session, identity } = usePhrSession();
  const [document, setDocument] = useState<DocumentDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [downloading, setDownloading] = useState<boolean>(false);

  useEffect(() => {
    if (!session || !identity || !documentId) return;
    fetchDocumentDetail(documentId, identity.principalId, toSessionContext(session))
      .then(setDocument)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('documentDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, identity, documentId]);

  const handleDownload = async (): Promise<void> => {
    if (!session || !identity || !documentId) return;
    setDownloading(true);
    try {
      const result = await downloadDocument(documentId, identity.principalId, toSessionContext(session));
      window.open(result.downloadUrl, '_blank');
    } catch (err) {
      logError('Failed to download document', undefined, { error: err });
    } finally {
      setDownloading(false);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('documentDetail.loading')}</div>;
  if (error) return (
    <PhrDetailPage
      title={t('documentDetail.error.load')}
      parentPath="/documents"
      parentLabel={t('phr.routes.documents.label')}
      loading={false}
      error={error}
      data={null}
    />
  );
  if (!document) return (
    <PhrDetailPage
      title={t('documentDetail.error.load')}
      parentPath="/documents"
      parentLabel={t('phr.routes.documents.label')}
      loading={false}
      error={null}
      data={null}
    >
      <p className="muted">{t('common.notAvailable')}</p>
    </PhrDetailPage>
  );

  const actions = [
    {
      id: 'download',
      label: downloading ? t('documentDetail.downloading') : t('documentDetail.download'),
      onClick: () => void handleDownload(),
      variant: 'primary' as const,
      disabled: downloading,
    },
  ];

  return (
    <PhrDetailPage
      title={document.title}
      subtitle={t('documentDetail.subheader')}
      parentPath="/documents"
      parentLabel={t('phr.routes.documents.label')}
      loading={loading}
      error={error}
      data={document}
      actions={actions}
    >
      <div className="stack gap-lg">
        <Card>
          <CardContent>
            <dl className="detail-list stack gap-sm">
              <div><dt>{t('documentDetail.documentId')}</dt><dd>{document.id}</dd></div>
              <div><dt>{t('documentDetail.contentType')}</dt><dd>{document.contentType}</dd></div>
              <div><dt>{t('documentDetail.uploadedAt')}</dt><dd>{formatDateTime(document.uploadedAt)}</dd></div>
              <div><dt>{t('documentDetail.uploadedBy')}</dt><dd>{document.uploadedBy}</dd></div>
              <div><dt>{t('documentDetail.size')}</dt><dd>{document.sizeKb ? formatFileSize(document.sizeKb * 1024) : '-'}</dd></div>
              {document.ocrStatus && (
                <div><dt>{t('documentDetail.ocrStatus')}</dt><dd>{document.ocrStatus}</dd></div>
              )}
              {document.description && (
                <div><dt>{t('documentDetail.description')}</dt><dd>{document.description}</dd></div>
              )}
            </dl>
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
                    <div className="muted">{formatDateTime(version.createdAt)}</div>
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
                    <div className="muted">{formatDateTime(entry.timestamp)}</div>
                    <div className="muted">{t('documentDetail.performedBy')}: {entry.performedBy}</div>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}
      </div>
    </PhrDetailPage>
  );
}
