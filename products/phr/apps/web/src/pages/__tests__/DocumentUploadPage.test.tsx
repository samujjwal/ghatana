/**
 * Tests for DocumentUploadPage — verifies file selection, upload flow, success, and error states.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DocumentUploadPage } from '../DocumentUploadPage';

vi.mock('../../api/phrApi', () => ({
  uploadDocument: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

import { uploadDocument } from '../../api/phrApi';

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
    expect(screen.getByLabelText('documents.upload.cta')).toBeTruthy();
  });

  it('submit button is disabled when no file is selected', () => {
    render(<DocumentUploadPage />);
    const btn = screen.getByRole('button', { name: 'documents.upload.submit' });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('enables submit button after file is selected', async () => {
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.cta') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.pdf', 'application/pdf')] } });
    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'documents.upload.submit' });
      expect((btn as HTMLButtonElement).disabled).toBe(false);
    });
  });

  it('shows success message after upload completes', async () => {
    mockUpload.mockResolvedValue({ id: 'doc-001', status: 'uploaded' });
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.cta') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.jpg')] } });
    fireEvent.submit(screen.getByRole('button', { name: 'documents.upload.submit' }).closest('form')!);
    await waitFor(() => expect(screen.getByRole('status')).toBeTruthy());
    expect(screen.getByText(/documents\.upload\.success/)).toBeTruthy();
  });

  it('shows error message when upload fails', async () => {
    mockUpload.mockRejectedValue(new Error('upload failed'));
    render(<DocumentUploadPage />);
    const input = screen.getByLabelText('documents.upload.cta') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [makeFile('report.jpg')] } });
    fireEvent.submit(screen.getByRole('button', { name: 'documents.upload.submit' }).closest('form')!);
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy());
    expect(screen.getByText('upload failed')).toBeTruthy();
  });
});
