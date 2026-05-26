import React, { useRef, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { uploadDocument } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import type { DocumentUploadResult } from '../types';

export function DocumentUploadPage(): React.ReactElement {
  const inputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState<boolean>(false);
  const [result, setResult] = useState<DocumentUploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleFileChange(evt: React.ChangeEvent<HTMLInputElement>): void {
    const file = evt.target.files?.[0] ?? null;
    setSelectedFile(file);
    setResult(null);
    setError(null);
  }

  async function handleUpload(evt: React.FormEvent<HTMLFormElement>): Promise<void> {
    evt.preventDefault();
    if (!selectedFile) return;
    setUploading(true);
    setError(null);
    try {
      const res = await uploadDocument(selectedFile);
      setResult(res);
      setSelectedFile(null);
      if (inputRef.current) inputRef.current.value = '';
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t('documents.upload.error'));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={t('documents.upload.title')} subheader={t('documents.upload.subheader')} />
        <CardContent>
          <form onSubmit={(e) => void handleUpload(e)} className="stack gap-md">
            <div>
              <label htmlFor="doc-upload" className="form-label">{t('documents.upload.cta')}</label>
              <input
                ref={inputRef}
                id="doc-upload"
                type="file"
                accept="image/*,application/pdf"
                onChange={handleFileChange}
                aria-describedby={error != null ? 'upload-error' : undefined}
              />
            </div>
            {error != null && <p id="upload-error" role="alert" className="error">{error}</p>}
            {result != null && (
              <p role="status" className="success">{t('documents.upload.success')} (ID: {result.id})</p>
            )}
            <button type="submit" disabled={selectedFile == null || uploading} className="btn btn--primary">
              {uploading ? t('documents.upload.uploading') : t('documents.upload.submit')}
            </button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
