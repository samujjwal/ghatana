import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Button, Card, CardContent, CardHeader, Select } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchDocuments, downloadDocument } from '../api/documentsApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import { DocumentViewer } from '../components/DocumentViewer';
import type { DocumentSummary } from '../types';

export function DocumentsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [downloading, setDownloading] = useState<Set<string>>(new Set());
  const [previewing, setPreviewing] = useState<string | null>(null);
  const [actionError, setActionError] = useState<SafeApiErrorState | null>(null);
  const [previewDocument, setPreviewDocument] = useState<{
    documentId: string;
    title: string;
    downloadUrl: string;
    contentType: string;
    expiresAt: string;
  } | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchDocuments(session.principalId, toSessionContext(session))
      .then(setDocuments)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('documents.error.load'))))
      .finally(() => setLoading(false));
  }, [session]);

  const handleDownload = async (documentId: string): Promise<void> => {
    if (!session) return;
    setDownloading(prev => new Set(prev).add(documentId));
    setActionError(null);
    try {
      const result = await downloadDocument(documentId, session.principalId, toSessionContext(session));
      window.open(result.downloadUrl, '_blank');
    } catch (err: unknown) {
      setActionError(toSafeApiErrorState(err, t('documents.error.download')));
      logError(t('documents.error.download'), undefined, { error: err });
    } finally {
      setDownloading(prev => {
        const next = new Set(prev);
        next.delete(documentId);
        return next;
      });
    }
  };

  const handlePreview = async (documentId: string, title: string, contentType: string): Promise<void> => {
    if (!session) return;
    setPreviewing(documentId);
    setActionError(null);
    try {
      const result = await downloadDocument(documentId, session.principalId, toSessionContext(session));
      setPreviewDocument({
        documentId,
        title,
        downloadUrl: result.downloadUrl,
        contentType,
        expiresAt: result.expiresAt,
      });
    } catch (err: unknown) {
      setActionError(toSafeApiErrorState(err, t('documents.error.preview')));
      logError(t('documents.error.preview'), undefined, { error: err });
    } finally {
      setPreviewing(null);
    }
  };

  const canPreview = (contentType: string): boolean => {
    return contentType === 'application/pdf' || 
           contentType.startsWith('image/') ||
           contentType === 'text/plain';
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('documents.loading')}</div>;
  if (error) return <SafeError title={t('documents.error')} message={error.message} correlationId={error.correlationId} />;

  // Filter documents by content type (as a proxy for category)
  const filteredDocuments = categoryFilter
    ? documents.filter(doc => doc.contentType === categoryFilter)
    : documents;

  const categories = Array.from(new Set(documents.map(doc => doc.contentType)));

  return (
    <div className="stack gap-lg">
      {previewDocument && (
        <DocumentViewer
          documentId={previewDocument.documentId}
          title={previewDocument.title}
          downloadUrl={previewDocument.downloadUrl}
          contentType={previewDocument.contentType}
          expiresAt={previewDocument.expiresAt}
          onClose={() => setPreviewDocument(null)}
        />
      )}
      <Card>
        <CardHeader 
          title={t('documents.title')} 
          subheader={t('documents.subheader')}
          action={
            <Link to="/documents/upload">
              <Button variant="solid">{t('documents.upload.submit')}</Button>
            </Link>
          }
        />
        <CardContent>
          {actionError ? (
            <SafeError
              title={t('documents.error')}
              message={actionError.message}
              correlationId={actionError.correlationId}
              onDismiss={() => setActionError(null)}
            />
          ) : null}
          {/* Category filter */}
          <div className="filter-bar">
            <Select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            >
              <option value="">{t('documents.filter.all')}</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </Select>
            {categoryFilter && (
              <Button
                type="button"
                onClick={() => setCategoryFilter('')}
                variant="secondary"
              >
                {t('documents.filter.clear')}
              </Button>
            )}
          </div>

          <ul className="stack gap-sm" aria-label={t('documents.list.label')}>
            {filteredDocuments.length === 0 ? (
              <p className="empty" role="status">{t('documents.empty')}</p>
            ) : (
              filteredDocuments.map((doc) => (
                <li key={doc.id} className="document-entry" role="listitem">
                  <span className="document-title" aria-label={t('documents.title.named', { title: doc.title })}>{doc.title}</span>
                  <span className="muted" aria-label={t('documents.contentType.named', { contentType: doc.contentType ?? t('common.unknown') })}>{doc.contentType}</span>
                  {doc.sizeKb && <span className="muted" aria-label={t('documents.fileSize.named', { size: doc.sizeKb.toFixed(1) })}>{doc.sizeKb.toFixed(1)} KB</span>}
                  <time dateTime={doc.uploadedAt} aria-label={t('documents.uploaded.named', { date: new Date(doc.uploadedAt).toLocaleDateString() })}>{new Date(doc.uploadedAt).toLocaleDateString()}</time>
                  {doc.ocrStatus && (
                    <span className={`badge badge--ocr-${doc.ocrStatus}`} role="status" aria-label={`OCR status: ${doc.ocrStatus}`}>
                      {doc.ocrStatus === 'ready' && t('documents.ocr.ready')}
                      {doc.ocrStatus === 'pending' && t('documents.ocr.pending')}
                      {doc.ocrStatus === 'processing' && t('documents.ocr.processing')}
                      {doc.ocrStatus === 'failed' && t('documents.ocr.failed')}
                    </span>
                  )}
                  <div className="row gap-sm" role="group" aria-label={t('documents.actions.named', { title: doc.title })}>
                    {canPreview(doc.contentType || '') && (
                      <Button
                        size="small"
                        onClick={() => handlePreview(doc.id, doc.title, doc.contentType || '')}
                        disabled={previewing === doc.id}
                        aria-label={previewing === doc.id ? t('documents.previewing') : `${t('documents.preview')} ${doc.title}`}
                        aria-busy={previewing === doc.id}
                      >
                        {previewing === doc.id ? t('documents.previewing') : t('documents.preview')}
                      </Button>
                    )}
                    <Button
                      size="small"
                      onClick={() => handleDownload(doc.id)}
                      disabled={downloading.has(doc.id)}
                      aria-label={downloading.has(doc.id) ? t('documents.downloading') : `${t('documents.download')} ${doc.title}`}
                      aria-busy={downloading.has(doc.id)}
                    >
                      {downloading.has(doc.id) ? t('documents.downloading') : t('documents.download')}
                    </Button>
                  </div>
                </li>
              ))
            )}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
