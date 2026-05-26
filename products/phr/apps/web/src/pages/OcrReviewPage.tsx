import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { confirmOcrDocument, fetchOcrDocument } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { OcrReviewDocument } from '../types';

export function OcrReviewPage(): React.ReactElement {
  const [params] = useSearchParams();
  const documentId = params.get('documentId') ?? '';

  const [doc, setDoc] = useState<OcrReviewDocument | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<boolean>(false);
  const [confirmed, setConfirmed] = useState<boolean>(false);

  useEffect(() => {
    if (!documentId) {
      setError(t('documents.ocr.error'));
      setLoading(false);
      return;
    }
    fetchOcrDocument(documentId)
      .then(setDoc)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('documents.ocr.error')))
      .finally(() => setLoading(false));
  }, [documentId]);

  async function handleConfirm(): Promise<void> {
    if (!documentId) return;
    setConfirming(true);
    try {
      await confirmOcrDocument(documentId);
      setConfirmed(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('documents.ocr.error'));
    } finally {
      setConfirming(false);
    }
  }

  if (loading) return <div className="loading">{t('documents.ocr.loading')}</div>;
  if (error) return <div className="error">{t('documents.ocr.error')}: {error}</div>;
  if (!doc) return <div className="error">{t('documents.ocr.error')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('documents.ocr.title')} subheader={t('documents.ocr.subheader')} />
        <CardContent>
          <h3>{doc.title}</h3>
          <p className="muted">Confidence: {Math.round(doc.confidence * 100)}%</p>
          <pre className="ocr-text">{doc.extractedText}</pre>
          {!confirmed ? (
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => void handleConfirm()}
              disabled={confirming}
              aria-busy={confirming}
            >
              {t('documents.ocr.confirm')}
            </button>
          ) : (
            <p role="status" className="success">{t('documents.upload.success')}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
