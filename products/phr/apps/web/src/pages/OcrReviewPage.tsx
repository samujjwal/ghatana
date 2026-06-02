import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { useParams, useSearchParams } from 'react-router-dom';
import { Card, CardContent, CardHeader, Button, TextArea, Badge } from '@ghatana/design-system';
import { confirmOcrDocument, fetchOcrDocument, rejectOcrDocument } from '../api/documentsApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { PhrPage } from '../components/PhrPage';
import { PhrDataState } from '../components/PhrDataState';
import { formatDateTime } from '../utils/formatters';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';
import type { OcrReviewDocument } from '../types';

function provenanceText(provenance: Record<string, unknown>): string {
  const source = typeof provenance.source === 'string' && provenance.source.trim()
    ? provenance.source
    : t('ocr.provenance.unknown');
  const processedAt = typeof provenance.processedAt === 'string' && provenance.processedAt.trim()
    ? formatDateTime(provenance.processedAt)
    : undefined;
  return processedAt
    ? t('ocr.provenance.withProcessedAt', { source, processedAt })
    : t('ocr.provenance.source', { source });
}

export function OcrReviewPage(): React.ReactElement {
  const [params] = useSearchParams();
  const routeParams = useParams<{ docId?: string }>();
  const documentId = routeParams.docId ?? params.get('documentId') ?? '';
  const { session } = usePhrSession();

  const [doc, setDoc] = useState<OcrReviewDocument | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [confirming, setConfirming] = useState<boolean>(false);
  const [rejecting, setRejecting] = useState<boolean>(false);
  const [confirmed, setConfirmed] = useState<boolean>(false);
  const [rejected, setRejected] = useState<boolean>(false);
  const [correctedText, setCorrectedText] = useState<string>('');

  useEffect(() => {
    if (!documentId || !session) {
      setError({ message: t('documents.ocr.error') });
      setLoading(false);
      return;
    }
    fetchOcrDocument(documentId, toSessionContext(session))
      .then((fetchedDoc) => {
        setDoc(fetchedDoc);
        setCorrectedText(fetchedDoc.extractedText);
      })
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('documents.ocr.error'))))
      .finally(() => setLoading(false));
  }, [documentId, session]);

  async function handleConfirm(): Promise<void> {
    if (!documentId || !session) return;
    setConfirming(true);
    try {
      await confirmOcrDocument(
        documentId,
        toSessionContext(session),
        correctedText,
      );
      setConfirmed(true);
      setDoc((current) => current ? { ...current, correctedText, status: 'confirmed' } : current);
    } catch (err: unknown) {
      setError(toSafeApiErrorState(err, t('ocr.error.confirm')));
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
        toSessionContext(session),
      );
      setRejected(true);
      setDoc((current) => current ? { ...current, status: 'rejected' } : current);
    } catch (err: unknown) {
      setError(toSafeApiErrorState(err, t('ocr.error.reject')));
      logError('Failed to reject OCR', undefined, { error: err });
    } finally {
      setRejecting(false);
    }
  }

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('documents.ocr.loading')}</div>;
  if (error) return <SafeError title={t('documents.ocr.error')} message={error.message} correlationId={error.correlationId} />;
  if (!doc) return <SafeError title={t('documents.ocr.error')} message={t('documents.ocr.error')} />;

  return (
    <PhrPage
      title={t('documents.ocr.title')}
      subtitle={t('documents.ocr.subheader')}
    >
      <PhrDataState
        loading={loading}
        error={error}
        data={doc}
      >
        <Card>
          <CardContent>
            <h3>{doc.title}</h3>
            <div className="stack gap-sm">
              <Badge 
                variant={doc.confidence > 0.8 ? 'success' : doc.confidence > 0.5 ? 'secondary' : 'destructive'}
                aria-label={t('ocr.confidence', { percent: Math.round(doc.confidence * 100) })}
              >
                {t('ocr.confidence', { percent: Math.round(doc.confidence * 100) })}
              </Badge>
              {doc.provenance && (
                <p className="muted" aria-label={t('ocr.provenance.label')}>
                  {provenanceText(doc.provenance)}
                </p>
              )}
            </div>
            <section aria-label={t('ocr.extracted')}>
              <h4>{t('ocr.extracted')}</h4>
              <pre className="data-card">{doc.extractedText}</pre>
            </section>
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
              <p role="status" className="success">{t('ocr.success')}</p>
            ) : rejected ? (
              <p role="status" className="warning">{t('ocr.rejected')}</p>
            ) : (
              <div className="stack gap-sm" style={{ marginTop: '1rem' }}>
                <Button
                  onClick={() => void handleConfirm()}
                  disabled={confirming || rejecting}
                  aria-busy={confirming}
                >
                  {confirming ? t('ocr.confirming') : t('ocr.confirm')}
                </Button>
                <Button
                  onClick={() => void handleReject()}
                  disabled={confirming || rejecting}
                  variant="outlined"
                  aria-busy={rejecting}
                >
                  {rejecting ? t('ocr.rejecting') : t('ocr.reject')}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </PhrDataState>
    </PhrPage>
  );
}
