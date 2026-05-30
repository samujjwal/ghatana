import React, { useRef, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Card, CardContent, CardHeader, Input, Button, Select, Progress, TextField } from '@ghatana/design-system';
import { uploadDocument } from '../api/documentsApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import { logError } from '../utils/safeLogger';

// File size limit: 10MB
const MAX_FILE_SIZE = 10 * 1024 * 1024;
// Allowed content types
const ALLOWED_CONTENT_TYPES = [
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/tiff',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];

export function DocumentUploadPage(): React.ReactElement {
  const { session } = usePhrSession();
  const inputRef = useRef<HTMLInputElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [title, setTitle] = useState<string>('');
  const [category, setCategory] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [uploading, setUploading] = useState<boolean>(false);
  const [uploadProgress, setUploadProgress] = useState<number>(0);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [canRetry, setCanRetry] = useState<boolean>(false);
  const validationErrorRef = useRef<HTMLParagraphElement>(null);
  const errorRef = useRef<HTMLParagraphElement>(null);
  const resultRef = useRef<HTMLParagraphElement>(null);

  // Focus validation error when set for accessibility
  React.useEffect(() => {
    if (validationError && validationErrorRef.current) {
      validationErrorRef.current.focus();
    }
  }, [validationError]);

  // Focus error when set for accessibility
  React.useEffect(() => {
    if (error && errorRef.current) {
      errorRef.current.focus();
    }
  }, [error]);

  // Focus result when set for accessibility
  React.useEffect(() => {
    if (result && resultRef.current) {
      resultRef.current.focus();
    }
  }, [result]);

  function handleFileChange(evt: React.ChangeEvent<HTMLInputElement>): void {
    const file = evt.target.files?.[0] ?? null;
    setSelectedFile(file);
    setResult(null);
    setError(null);
    setCanRetry(false);
    setValidationError(null);

    // Client-side file validation
    if (file) {
      if (file.size > MAX_FILE_SIZE) {
        setValidationError(t('documents.upload.error.size', { size: MAX_FILE_SIZE / 1024 / 1024 }));
        setSelectedFile(null);
        return;
      }

      if (!ALLOWED_CONTENT_TYPES.includes(file.type)) {
        setValidationError(t('documents.upload.error.type', { type: file.type, allowed: ALLOWED_CONTENT_TYPES.join(', ') }));
        setSelectedFile(null);
        return;
      }

      // Auto-populate title from filename if empty
      if (!title) {
        setTitle(file.name.replace(/\.[^/.]+$/, ''));
      }
    }
  }

  function validateMetadata(): boolean {
    if (!title.trim()) {
      setValidationError(t('documents.upload.error.titleRequired'));
      return false;
    }
    if (title.trim().length > 200) {
      setValidationError(t('documents.upload.error.titleLength'));
      return false;
    }
    if (category && category.length > 50) {
      setValidationError(t('documents.upload.error.categoryLength'));
      return false;
    }
    if (description && description.length > 500) {
      setValidationError(t('documents.upload.error.descriptionLength'));
      return false;
    }
    return true;
  }

  async function handleUpload(evt: React.FormEvent<HTMLFormElement>): Promise<void> {
    evt.preventDefault();
    
    if (!selectedFile) {
      setValidationError(t('documents.upload.error.fileRequired'));
      return;
    }

    if (!session) {
      setError(t('documents.upload.error.auth'));
      return;
    }

    if (!validateMetadata()) {
      return;
    }

    setUploading(true);
    setUploadProgress(0);
    setError(null);
    setCanRetry(false);
    setValidationError(null);
    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    
    try {
      const res = await uploadDocument(
        session.principalId,
        selectedFile,
        {
          title: title.trim(),
          category: category.trim() || undefined,
          description: description.trim() || undefined,
        },
        {
          tenantId: session.tenantId,
          principalId: session.principalId,
          role: session.role,
        },
        {
          signal: abortController.signal,
          onProgress: setUploadProgress,
        },
      );
      
      setUploadProgress(100);
      setResult(t('documents.upload.successWithOcr', { id: res.id, status: res.ocrStatus }));
      setSelectedFile(null);
      setTitle('');
      setCategory('');
      setDescription('');
      if (inputRef.current) inputRef.current.value = '';
    } catch (err: unknown) {
      const aborted = err instanceof DOMException && err.name === 'AbortError';
      setError(aborted ? t('documents.upload.cancelled') : err instanceof Error ? err.message : t('documents.upload.error'));
      setCanRetry(!aborted);
      if (!aborted) {
        logError('Failed to upload document', undefined, { error: err });
      }
    } finally {
      setUploading(false);
      setUploadProgress(0);
      abortControllerRef.current = null;
    }
  }

  function handleCancelUpload(): void {
    abortControllerRef.current?.abort();
  }

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('documents.upload.title')} subheader={t('documents.upload.subheader')} />
        <CardContent>
          <form onSubmit={(e) => void handleUpload(e)} className="stack gap-md">
            <div>
              <label htmlFor="doc-upload" className="form-label">{t('documents.upload.file.label')}</label>
              <input
                ref={inputRef}
                id="doc-upload"
                type="file"
                accept={ALLOWED_CONTENT_TYPES.join(',')}
                onChange={handleFileChange}
                aria-describedby={validationError != null ? 'validation-error' : undefined}
              />
              <p className="muted">{t('documents.upload.file.help')}</p>
            </div>

            <div>
              <label htmlFor="doc-title" className="form-label">{t('documents.upload.title.label')}</label>
              <TextField
                id="doc-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder={t('documents.upload.title.placeholder')}
                maxLength={200}
                required
                aria-label={t('documents.upload.title.placeholder')}
              />
            </div>

            <div>
              <label htmlFor="doc-category" className="form-label">{t('documents.upload.category.label')}</label>
              <Select
                id="doc-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                aria-label={t('documents.upload.category.label')}
              >
                <option value="">{t('documents.upload.category.placeholder')}</option>
                <option value="lab-results">{t('documents.category.lab')}</option>
                <option value="imaging">{t('documents.category.imaging')}</option>
                <option value="discharge-summary">{t('documents.category.discharge')}</option>
                <option value="insurance">{t('documents.category.insurance')}</option>
                <option value="other">{t('documents.category.other')}</option>
              </Select>
            </div>

            <div>
              <label htmlFor="doc-description" className="form-label">{t('documentDetail.description')}</label>
              <TextField
                id="doc-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t('documents.upload.description.placeholder')}
                maxLength={500}
                aria-label={t('documents.upload.description.placeholder')}
              />
            </div>

            {uploading && (
              <Progress value={uploadProgress} aria-label={t('documents.upload.progress')} />
            )}

            {validationError != null && (
              <p 
                ref={validationErrorRef}
                id="validation-error" 
                role="alert" 
                className="error"
                tabIndex={-1}
              >
                {validationError}
              </p>
            )}
            {error != null && (
              <p 
                ref={errorRef}
                role="alert" 
                className="error"
                tabIndex={-1}
              >
                {error}
              </p>
            )}
            {result != null && (
              <p 
                ref={resultRef}
                role="status" 
                className="success"
                tabIndex={-1}
              >
                {result}
              </p>
            )}
            
            <Button 
              type="submit" 
              disabled={selectedFile == null || uploading || !title.trim()} 
              aria-busy={uploading}
            >
              {canRetry ? t('documents.upload.retry') : uploading ? t('documents.upload.uploading') : t('documents.upload.submit')}
            </Button>
            {uploading ? (
              <Button type="button" variant="outline" tone="secondary" onClick={handleCancelUpload}>
                {t('documents.upload.cancel')}
              </Button>
            ) : null}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
