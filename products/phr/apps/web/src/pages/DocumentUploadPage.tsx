import React, { useRef, useState } from 'react';
import { Card, CardContent, CardHeader, Input } from '@ghatana/design-system';
import { uploadDocument } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';

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
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [title, setTitle] = useState<string>('');
  const [category, setCategory] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [uploading, setUploading] = useState<boolean>(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  function handleFileChange(evt: React.ChangeEvent<HTMLInputElement>): void {
    const file = evt.target.files?.[0] ?? null;
    setSelectedFile(file);
    setResult(null);
    setError(null);
    setValidationError(null);

    // Client-side file validation
    if (file) {
      if (file.size > MAX_FILE_SIZE) {
        setValidationError(`File size exceeds maximum limit of ${MAX_FILE_SIZE / 1024 / 1024}MB`);
        setSelectedFile(null);
        return;
      }

      if (!ALLOWED_CONTENT_TYPES.includes(file.type)) {
        setValidationError(`File type ${file.type} is not allowed. Allowed types: ${ALLOWED_CONTENT_TYPES.join(', ')}`);
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
      setValidationError('Document title is required');
      return false;
    }
    if (title.trim().length > 200) {
      setValidationError('Document title must be less than 200 characters');
      return false;
    }
    if (category && category.length > 50) {
      setValidationError('Category must be less than 50 characters');
      return false;
    }
    if (description && description.length > 500) {
      setValidationError('Description must be less than 500 characters');
      return false;
    }
    return true;
  }

  async function handleUpload(evt: React.FormEvent<HTMLFormElement>): Promise<void> {
    evt.preventDefault();
    
    if (!selectedFile) {
      setValidationError('Please select a file to upload');
      return;
    }

    if (!session) {
      setError('Not authenticated');
      return;
    }

    if (!validateMetadata()) {
      return;
    }

    setUploading(true);
    setError(null);
    setValidationError(null);
    
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
      );
      setResult(`Document uploaded successfully (ID: ${res.id}, OCR Status: ${res.ocrStatus})`);
      setSelectedFile(null);
      setTitle('');
      setCategory('');
      setDescription('');
      if (inputRef.current) inputRef.current.value = '';
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to upload document');
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title="Upload Document" subheader="Upload medical documents and records" />
        <CardContent>
          <form onSubmit={(e) => void handleUpload(e)} className="stack gap-md">
            <div>
              <label htmlFor="doc-upload" className="form-label">Select File</label>
              <input
                ref={inputRef}
                id="doc-upload"
                type="file"
                accept={ALLOWED_CONTENT_TYPES.join(',')}
                onChange={handleFileChange}
                aria-describedby={validationError != null ? 'validation-error' : undefined}
              />
              <p className="muted">Allowed types: PDF, JPEG, PNG, TIFF, DOC, DOCX. Max size: 10MB</p>
            </div>

            <div>
              <label htmlFor="doc-title" className="form-label">Title *</label>
              <Input
                id="doc-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Document title"
                maxLength={200}
                required
              />
            </div>

            <div>
              <label htmlFor="doc-category" className="form-label">Category</label>
              <select
                id="doc-category"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="">Select category (optional)</option>
                <option value="lab-results">Lab Results</option>
                <option value="imaging">Imaging</option>
                <option value="discharge-summary">Discharge Summary</option>
                <option value="insurance">Insurance</option>
                <option value="other">Other</option>
              </select>
            </div>

            <div>
              <label htmlFor="doc-description" className="form-label">Description</label>
              <Input
                id="doc-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description"
                maxLength={500}
              />
            </div>

            {validationError != null && (
              <p id="validation-error" role="alert" className="error">{validationError}</p>
            )}
            {error != null && (
              <p role="alert" className="error">{error}</p>
            )}
            {result != null && (
              <p role="status" className="success">{result}</p>
            )}
            
            <button 
              type="submit" 
              disabled={selectedFile == null || uploading || !title.trim()} 
              className="btn btn--primary"
            >
              {uploading ? 'Uploading...' : 'Upload Document'}
            </button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
