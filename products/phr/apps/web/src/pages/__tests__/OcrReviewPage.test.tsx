/**
 * Tests for OcrReviewPage: verifies loading, error, confirm, edit, and reject flows.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { OcrReviewPage } from '../OcrReviewPage';

vi.mock('../../api/documentsApi', () => ({
  fetchOcrDocument: vi.fn(),
  confirmOcrDocument: vi.fn(),
  rejectOcrDocument: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, params?: Record<string, string | number>) => {
    if (params && Object.keys(params).length > 0) return `${key}:${JSON.stringify(params)}`;
    return key;
  },
}));

vi.mock('react-router-dom', () => ({
  useParams: () => ({ docId: 'doc-001' }),
  useSearchParams: () => [new URLSearchParams('documentId=doc-001')],
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: {
      principalId: 'patient-42',
      tenantId: 't1',
      role: 'patient' as const,
      name: 'Test Patient',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

import { fetchOcrDocument, confirmOcrDocument, rejectOcrDocument } from '../../api/documentsApi';

const mockFetch = vi.mocked(fetchOcrDocument);
const mockConfirm = vi.mocked(confirmOcrDocument);
const mockReject = vi.mocked(rejectOcrDocument);

const ocrDoc = {
  id: 'doc-001',
  title: 'Lab Report 2025',
  confidence: 0.92,
  extractedText: 'Hemoglobin: 12.5 g/dL\nGlucose: 98 mg/dL',
  ocrText: 'Hemoglobin: 12.5 g/dL\nGlucose: 98 mg/dL',
  status: 'pending_review' as const,
};

describe('OcrReviewPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    mockConfirm.mockReset();
    mockReject.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<OcrReviewPage />);
    expect(screen.getByText('documents.ocr.loading')).toBeTruthy();
  });

  it('shows error when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('not found'));
    render(<OcrReviewPage />);
    await waitFor(() => expect(screen.getByText(/documents\.ocr\.error/)).toBeTruthy());
  });

  it('displays document title after fetch', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    render(<OcrReviewPage />);
    await waitFor(() => expect(screen.getByText('Lab Report 2025')).toBeTruthy());
  });

  it('populates textarea with extracted text', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    render(<OcrReviewPage />);
    await waitFor(() => {
      const textarea = screen.getByRole('textbox') as HTMLTextAreaElement;
      expect(textarea.value).toBe('Hemoglobin: 12.5 g/dL\nGlucose: 98 mg/dL');
    });
  });

  it('calls confirmOcrDocument on confirm button click', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    mockConfirm.mockResolvedValue({ ...ocrDoc, status: 'confirmed' });
    render(<OcrReviewPage />);
    await waitFor(() => screen.getByText('ocr.confirm'));
    fireEvent.click(screen.getByText('ocr.confirm'));
    await waitFor(() =>
      expect(mockConfirm).toHaveBeenCalledWith(
        'doc-001',
        { tenantId: 't1', principalId: 'patient-42', role: 'patient' },
        ocrDoc.extractedText,
      ),
    );
  });

  it('shows success status after confirm', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    mockConfirm.mockResolvedValue({ ...ocrDoc, status: 'confirmed' });
    render(<OcrReviewPage />);
    await waitFor(() => screen.getByText('ocr.confirm'));
    fireEvent.click(screen.getByText('ocr.confirm'));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('ocr.success'));
  });

  it('sends edited OCR text on confirm', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    mockConfirm.mockResolvedValue({ ...ocrDoc, status: 'confirmed' });
    render(<OcrReviewPage />);

    const textarea = await screen.findByRole('textbox') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'Corrected hemoglobin value' } });
    fireEvent.click(screen.getByText('ocr.confirm'));

    await waitFor(() =>
      expect(mockConfirm).toHaveBeenCalledWith(
        'doc-001',
        { tenantId: 't1', principalId: 'patient-42', role: 'patient' },
        'Corrected hemoglobin value',
      ),
    );
  });

  it('rejects OCR review', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    mockReject.mockResolvedValue({ documentId: 'doc-001', rejected: true });
    render(<OcrReviewPage />);

    fireEvent.click(await screen.findByText('ocr.reject'));

    await waitFor(() =>
      expect(mockReject).toHaveBeenCalledWith(
        'doc-001',
        { tenantId: 't1', principalId: 'patient-42', role: 'patient' },
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('ocr.rejected');
  });
});
