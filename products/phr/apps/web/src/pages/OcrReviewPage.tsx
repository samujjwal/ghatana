import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { confirmOcrDocument, fetchOcrDocument } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { OcrReviewDocument } from '../types';

export function OcrReviewPage(): React.ReactElement {
  const [params] = useSearchParams();
  const documentId = params.get('documentId') ?? '';
  const { session } = usePhrSession();

  const [doc, setDoc] = useState<OcrReviewDocument | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<boolean>(false);
  const [confirmed, setConfirmed] = useState<boolean>(false);
  const [correctedText, setCorrectedText] = useState<string>('');

  useEffect(() => {
    if (!documentId || !session) {
      setError(t('documents.ocr.error'));
      setLoading(false);
      return;
    }
    fetchOcrDocument(documentId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then((fetchedDoc) => {
        setDoc(fetchedDoc);
        setCorrectedText(fetchedDoc.extractedText);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('documents.ocr.error')))
      .finally(() => setLoading(false));
  }, [documentId, session]);

  async function handleConfirm(): Promise<void> {
    if (!documentId || !session) return;
    setConfirming(true);
    try {
      await confirmOcrDocument(
        documentId,
        {
          tenantId: session.tenantId,
          principalId: session.principalId,
          role: session.role,
        },
        correctedText,
      );
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
          <p className="muted">{t('ocr.confidence', { percent: Math.round(doc.confidence * 100) })}</p>
          <label htmlFor="ocr-text-edit" className="visually-hidden">
            {t('ocr.corrected')}
          </label>
          <textarea
            id="ocr-text-edit"
            className="ocr-text-edit"
            value={correctedText}
            onChange={(e) => setCorrectedText(e.target.value)}
            rows={10}
            aria-label={t('ocr.corrected')}
          />
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
