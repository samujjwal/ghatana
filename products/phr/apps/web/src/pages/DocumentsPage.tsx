import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDocuments } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { DocumentSummary } from '../types';

export function DocumentsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchDocuments(session.principalId)
      .then(setDocuments)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('documents.error')))
      .finally(() => setLoading(false));
  }, [session]);

  if (loading) return <div className="loading">{t('documents.loading')}</div>;
  if (error) return <div className="error">{t('documents.error')}: {error}</div>;
  if (!documents.length) return <div className="empty">{t('documents.empty')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('documents.title')} subheader={t('documents.subheader')} />
        <CardContent>
          <ul className="stack gap-sm" aria-label={t('documents.title')}>
            {documents.map((doc) => (
              <li key={doc.id} className="document-entry">
                <span className="document-title">{doc.title}</span>
                <span className="muted">{doc.contentType}</span>
                <time dateTime={doc.uploadedAt}>{new Date(doc.uploadedAt).toLocaleDateString()}</time>
                {doc.ocrStatus != null && (
                  <span className={`badge badge--ocr-${doc.ocrStatus}`}>{doc.ocrStatus}</span>
                )}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
