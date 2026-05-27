import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchDocuments, downloadDocument } from '../api/phrApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import type { DocumentSummary } from '../types';

export function DocumentsPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [downloading, setDownloading] = useState<Set<string>>(new Set());
  const [previewing, setPreviewing] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    fetchDocuments(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setDocuments)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load documents'))
      .finally(() => setLoading(false));
  }, [session]);

  const handleDownload = async (documentId: string, title: string): Promise<void> => {
    if (!session) return;
    setDownloading(prev => new Set(prev).add(documentId));
    try {
      const blob = await downloadDocument(documentId, session.principalId, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = title;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to download document:', err);
    } finally {
      setDownloading(prev => {
        const next = new Set(prev);
        next.delete(documentId);
        return next;
      });
    }
  };

  const handlePreview = async (documentId: string, contentType: string): Promise<void> => {
    if (!session) return;
    setPreviewing(documentId);
    setPreviewError(null);
    try {
      const blob = await downloadDocument(documentId, session.principalId, {
        tenantId: session.tenantId,
        principalId: session.principalId,
        role: session.role,
      });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
    } catch (err) {
      setPreviewError(err instanceof Error ? err.message : 'Failed to preview document');
    } finally {
      setPreviewing(null);
    }
  };

  const canPreview = (contentType: string): boolean => {
    return contentType === 'application/pdf' || 
           contentType.startsWith('image/') ||
           contentType === 'text/plain';
  };

  if (loading) return <div className="loading">Loading documents...</div>;
  if (error) return <div className="error">Error: {error}</div>;

  // Filter documents by content type (as a proxy for category)
  const filteredDocuments = categoryFilter
    ? documents.filter(doc => doc.contentType === categoryFilter)
    : documents;

  const categories = Array.from(new Set(documents.map(doc => doc.contentType)));

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title="Documents" subheader="Your medical documents and records" />
        <CardContent>
          {/* Category filter */}
          <div className="filter-bar">
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All types</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
            {categoryFilter && (
              <button
                onClick={() => setCategoryFilter('')}
                className="filter-clear"
              >
                Clear filter
              </button>
            )}
          </div>

          <ul className="stack gap-sm" aria-label="Documents">
            {filteredDocuments.length === 0 ? (
              <p className="empty">No documents found</p>
            ) : (
              filteredDocuments.map((doc) => (
                <li key={doc.id} className="document-entry">
                  <span className="document-title">{doc.title}</span>
                  <span className="muted">{doc.contentType}</span>
                  {doc.sizeKb && <span className="muted">{doc.sizeKb.toFixed(1)} KB</span>}
                  <time dateTime={doc.uploadedAt}>{new Date(doc.uploadedAt).toLocaleDateString()}</time>
                  {doc.ocrStatus && (
                    <span className={`badge badge--ocr-${doc.ocrStatus}`}>
                      {doc.ocrStatus === 'ready' && 'OCR Complete'}
                      {doc.ocrStatus === 'pending' && 'OCR Pending'}
                      {doc.ocrStatus === 'processing' && 'OCR Processing'}
                      {doc.ocrStatus === 'failed' && 'OCR Failed'}
                    </span>
                  )}
                  <div className="row gap-sm">
                    {canPreview(doc.contentType || '') && (
                      <Button
                        size="small"
                        onClick={() => handlePreview(doc.id, doc.contentType || '')}
                        disabled={previewing === doc.id}
                      >
                        {previewing === doc.id ? 'Previewing...' : 'Preview'}
                      </Button>
                    )}
                    <Button
                      size="small"
                      onClick={() => handleDownload(doc.id, doc.title)}
                      disabled={downloading.has(doc.id)}
                    >
                      {downloading.has(doc.id) ? 'Downloading...' : 'Download'}
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
