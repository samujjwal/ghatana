/**
 * Tests for DocumentsPage — verifies loading, error, empty, and document listing states.
 */
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DocumentsPage } from '../DocumentsPage';

vi.mock('../../api/phrApi', () => ({
  fetchDocuments: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrSessionContext', () => ({
  usePhrSession: () => ({ session: { principalId: 'patient-42', tenantId: 't1', role: 'patient' as const, name: 'Test Patient', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }, isAuthenticated: true, setSession: vi.fn(), clearSession: vi.fn() }),
}));

import { fetchDocuments } from '../../api/phrApi';

const mockFetch = fetchDocuments as ReturnType<typeof vi.fn>;

const documents = [
  { id: 'd1', title: 'Discharge Summary 2026', category: 'discharge_summary' as const, uploadedAt: '2026-04-01T12:00:00Z', mimeType: 'application/pdf', sizeKb: 120 },
  { id: 'd2', title: 'Lab Report March', category: 'lab_report' as const, uploadedAt: '2026-03-15T08:00:00Z', mimeType: 'application/pdf', sizeKb: 48 },
];

describe('DocumentsPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<DocumentsPage />);
    expect(screen.getByText('documents.loading')).toBeTruthy();
  });

  it('shows error message when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('server error'));
    render(<DocumentsPage />);
    await waitFor(() =>
      expect(screen.getByText(/documents\.error/)).toBeTruthy()
    );
  });

  it('shows empty message when no documents', async () => {
    mockFetch.mockResolvedValue([]);
    render(<DocumentsPage />);
    await waitFor(() =>
      expect(screen.getByText('documents.empty')).toBeTruthy()
    );
  });

  it('displays first document title', async () => {
    mockFetch.mockResolvedValue(documents);
    render(<DocumentsPage />);
    await waitFor(() =>
      expect(screen.getByText('Discharge Summary 2026')).toBeTruthy()
    );
  });

  it('displays second document title', async () => {
    mockFetch.mockResolvedValue(documents);
    render(<DocumentsPage />);
    await waitFor(() =>
      expect(screen.getByText('Lab Report March')).toBeTruthy()
    );
  });

  it('calls fetchDocuments with the session principalId', async () => {
    mockFetch.mockResolvedValue([]);
    render(<DocumentsPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith('patient-42'));
  });
});
