/**
 * Tests for DocumentUploadPage — verifies file selection, upload flow, success, and error states.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DocumentUploadPage } from '../DocumentUploadPage';

vi.mock('../../api/documentsApi', () => ({
  uploadDocument: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string, params?: Record<string, string>) => {
    if (params == null) return key;
    return `${key}:${Object.values(params).join(':')}`;
  },
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({
    session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() },
    isAuthenticated: true,
    setSession: vi.fn(),
    clearSession: vi.fn(),
  }),
}));

import { uploadDocument } from '../../api/documentsApi';

const mockUpload = uploadDocument as ReturnType<typeof vi.fn>;

function makeFile(name: string, type = 'image/jpeg'): File {
  return new File(['content'], name, { type });
}

describe('DocumentUploadPage', () => {
  beforeEach(() => {
    mockUpload.mockReset();
  });

  it('renders the upload form', () => {
    render(<DocumentUploadPage />);
    expect(screen.getByText('documents.upload.title')).toBeTruthy();
    expect(screen.getByLabelText('documents.upload.file.label')).toBeTruthy();
  });

  it('submit button is disabled when no file is selected', () => {
    render(<DocumentUploadPage />);
    const btn = screen.getByRole('button', { name: 'documents.upload.submit' });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('enables submit button after file is selected', async () => {
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.file.label') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.pdf', 'application/pdf')] } });
    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'documents.upload.submit' });
      expect((btn as HTMLButtonElement).disabled).toBe(false);
    });
  });

  it('shows success message after upload completes', async () => {
    mockUpload.mockResolvedValue({ id: 'doc-001', ocrStatus: 'pending' });
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.file.label') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.jpg')] } });
    fireEvent.submit(screen.getByRole('button', { name: 'documents.upload.submit' }).closest('form')!);
    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
    expect(screen.getByText(/documents\.upload\.success/)).toBeTruthy();
  });

  it('shows error message when upload fails', async () => {
    mockUpload.mockRejectedValue(new Error('upload failed'));
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.file.label') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.jpg')] } });
    fireEvent.submit(screen.getByRole('button', { name: 'documents.upload.submit' }).closest('form')!);
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByText('upload failed')).toBeTruthy();
  });
});
