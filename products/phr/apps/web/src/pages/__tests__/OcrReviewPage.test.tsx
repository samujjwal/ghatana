/**
 * Tests for OcrReviewPage — verifies loading, error, confirmed states and edit flow.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { OcrReviewPage } from '../OcrReviewPage';

vi.mock('../../api/phrApi', () => ({
  fetchOcrDocument: vi.fn(),
  confirmOcrDocument: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, params?: Record<string, string | number>) => {
    if (params && Object.keys(params).length > 0) return `${key}:${JSON.stringify(params)}`;
    return key;
  },
}));

vi.mock('react-router-dom', () => ({
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

import { fetchOcrDocument, confirmOcrDocument } from '../../api/phrApi';

const mockFetch = fetchOcrDocument as ReturnType<typeof vi.fn>;
const mockConfirm = confirmOcrDocument as ReturnType<typeof vi.fn>;

const ocrDoc = {
  id: 'doc-001',
  title: 'Lab Report 2025',
  confidence: 0.92,
  extractedText: 'Hemoglobin: 12.5 g/dL\nGlucose: 98 mg/dL',
};

describe('OcrReviewPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    mockConfirm.mockReset();
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
    mockConfirm.mockResolvedValue(undefined);
    render(<OcrReviewPage />);
    await waitFor(() => screen.getByText('documents.ocr.confirm'));
    fireEvent.click(screen.getByText('documents.ocr.confirm'));
    await waitFor(() =>
      expect(mockConfirm).toHaveBeenCalledWith(
        'doc-001',
        { tenantId: 't1', principalId: 'patient-42', role: 'patient' },
        ocrDoc.extractedText,
      )
    );
  });

  it('shows success status after confirm', async () => {
    mockFetch.mockResolvedValue(ocrDoc);
    mockConfirm.mockResolvedValue(undefined);
    render(<OcrReviewPage />);
    await waitFor(() => screen.getByText('documents.ocr.confirm'));
    fireEvent.click(screen.getByText('documents.ocr.confirm'));
    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
  });
});
