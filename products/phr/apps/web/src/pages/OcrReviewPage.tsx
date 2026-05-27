import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Button, TextArea, Badge } from '@ghatana/design-system';
import { confirmOcrDocument, fetchOcrDocument, rejectOcrDocument } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { OcrReviewDocument } from '../types';

export function OcrReviewPage(): React.ReactElement {
  const [params] = useSearchParams();
  const documentId = params.get('documentId') ?? '';
  const { session } = usePhrSession();

  const [doc, setDoc] = useState<OcrReviewDocument | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<boolean>(false);
  const [rejecting, setRejecting] = useState<boolean>(false);
  const [confirmed, setConfirmed] = useState<boolean>(false);
  const [rejected, setRejected] = useState<boolean>(false);
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
      setError(err instanceof Error ? err.message : 'Failed to confirm OCR');
      logError('Failed to confirm OCR', undefined, { error: err });
    } finally {
      setConfirming(false);
    }
  }

  async function handleReject(): Promise<void> {
    if (!documentId || !session) return;
    setRejecting(true);
    try {
      await rejectOcrDocument(
        documentId,
        {
          tenantId: session.tenantId,
          principalId: session.principalId,
          role: session.role,
        },
      );
      setRejected(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to reject OCR');
      logError('Failed to reject OCR', undefined, { error: err });
    } finally {
      setRejecting(false);
    }
  }

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('documents.ocr.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('documents.ocr.error')}: {error}</div>;
  if (!doc) return <div className="error" role="alert">{t('documents.ocr.error')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('documents.ocr.title')} subheader={t('documents.ocr.subheader')} />
        <CardContent>
          <h3>{doc.title}</h3>
          <div className="stack gap-sm">
            <Badge 
              variant={doc.confidence > 0.8 ? 'success' : doc.confidence > 0.5 ? 'secondary' : 'destructive'}
              aria-label={`OCR confidence: ${Math.round(doc.confidence * 100)}%`}
            >
              Confidence: {Math.round(doc.confidence * 100)}%
            </Badge>
          </div>
          <label htmlFor="ocr-text-edit" className="visually-hidden">
            {t('ocr.corrected')}
          </label>
          <TextArea
            id="ocr-text-edit"
            value={correctedText}
            onChange={(e) => setCorrectedText(e.target.value)}
            rows={10}
            aria-label={t('ocr.corrected')}
          />
          {confirmed ? (
            <p role="status" className="success">{t('documents.upload.success')}</p>
          ) : rejected ? (
            <p role="status" className="warning">{t('documents.ocr.error')}</p>
          ) : (
            <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
              <Button
                onClick={() => void handleConfirm()}
                disabled={confirming || rejecting}
                aria-busy={confirming}
              >
                {confirming ? 'Confirming...' : t('documents.ocr.confirm')}
              </Button>
              <Button
                onClick={() => void handleReject()}
                disabled={confirming || rejecting}
                variant="outlined"
                aria-busy={rejecting}
              >
                {rejecting ? 'Rejecting...' : 'Reject'}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
