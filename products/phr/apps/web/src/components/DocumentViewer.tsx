/**
 * W-007: Secure document viewer component.
 * Replaces unmanaged window.open with secure internal viewer.
 */

import React, { useEffect, useRef, useState } from 'react';
import { Button } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';

interface DocumentViewerProps {
  documentId: string;
  title: string;
  downloadUrl: string;
  contentType: string;
  expiresAt: string;
  onClose: () => void;
}

export function DocumentViewer({
  documentId,
  title,
  downloadUrl,
  contentType,
  expiresAt,
  onClose,
}: DocumentViewerProps): React.ReactElement {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);

    const iframe = iframeRef.current;
    if (!iframe) return;

    const handleLoad = () => {
      setLoading(false);
    };

    const handleError = () => {
      setLoading(false);
      setError(t('documents.error.preview'));
      logError('Document viewer failed to load', undefined, { documentId, title });
    };

    iframe.addEventListener('load', handleLoad);
    iframe.addEventListener('error', handleError);

    // Load the document
    iframe.src = downloadUrl;

    return () => {
      iframe.removeEventListener('load', handleLoad);
      iframe.removeEventListener('error', handleError);
    };
  }, [downloadUrl, documentId, title]);

  return (
    <div className="document-viewer-overlay" role="dialog" aria-modal="true" aria-label={t('documents.preview.title', { title })}>
      <div className="document-viewer-content">
        <div className="document-viewer-header">
          <h2>{title}</h2>
          <Button onClick={onClose} aria-label={t('documents.preview.close')}>{t('documents.preview.close')}</Button>
        </div>
        
        {loading && (
          <div className="document-viewer-loading" role="status" aria-live="polite">
            {t('documents.preview.loading')}
          </div>
        )}
        
        {error && (
          <div className="document-viewer-error" role="alert">
            {error}
          </div>
        )}
        
        <iframe
          ref={iframeRef}
          title={title}
          className="document-viewer-iframe"
          sandbox="allow-same-origin allow-scripts"
          aria-label={`Document preview: ${title}`}
        />
        
        <div className="document-viewer-footer">
          <p className="muted">
            {t('documents.preview.expires', { date: new Date(expiresAt).toLocaleString() })}
          </p>
          <Button
            onClick={() => window.open(downloadUrl, '_blank')}
            aria-label={t('documents.download.named', { title })}
          >
            {t('documents.download')}
          </Button>
        </div>
      </div>
    </div>
  );
}
