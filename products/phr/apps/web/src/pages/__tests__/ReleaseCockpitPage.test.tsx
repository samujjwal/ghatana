/**
 * Tests for ReleaseCockpitPage — verifies environment switch, loading, error, and readiness display.
 */
import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ReleaseCockpitPage } from '../ReleaseCockpitPage';

vi.mock('../../api/phrApi', () => ({
  fetchReleaseReadiness: vi.fn(),
}));

vi.mock('../../i18n/phrI18n', () => ({
  t: (key: string) => key,
}));

vi.mock('../../auth/PhrAccessContext', () => ({
  usePhrAccess: () => ({ role: 'admin' as const }),
}));

import { fetchReleaseReadiness } from '../../api/phrApi';

const mockFetch = fetchReleaseReadiness as ReturnType<typeof vi.fn>;

const readinessPayload = {
  environment: 'staging',
  targetCommitSha: 'abc123def456',
  runtimeTruthBlocked: false,
  releaseReadiness: { overallScore: 87 },
  sections: {
    evidenceFreshness: { label: 'Evidence Freshness', message: 'OK', status: 'pass', runtimeProven: true },
    fhirRuntime: { label: 'FHIR Runtime', message: 'healthy', status: 'pass', runtimeProven: true },
  },
};

describe('ReleaseCockpitPage', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('shows loading indicator while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    render(<ReleaseCockpitPage />);
    expect(screen.getByText('release.loading')).toBeTruthy();
  });

  it('shows error when fetch fails', async () => {
    mockFetch.mockRejectedValue(new Error('unreachable'));
    render(<ReleaseCockpitPage />);
    await waitFor(() => expect(screen.getByText(/release\.errorPrefix/)).toBeTruthy());
  });

  it('displays overall score after successful fetch', async () => {
    mockFetch.mockResolvedValue(readinessPayload);
    render(<ReleaseCockpitPage />);
    await waitFor(() => expect(screen.getByText('87')).toBeTruthy());
  });

  it('displays section labels', async () => {
    mockFetch.mockResolvedValue(readinessPayload);
    render(<ReleaseCockpitPage />);
    await waitFor(() => expect(screen.getByText('Evidence Freshness')).toBeTruthy());
    expect(screen.getByText('FHIR Runtime')).toBeTruthy();
  });

  it('re-fetches when environment is switched', async () => {
    mockFetch.mockResolvedValue(readinessPayload);
    render(<ReleaseCockpitPage />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(1));

    const prodBtn = screen.getByText('release.environment.prod');
    fireEvent.click(prodBtn);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledTimes(2));
    expect(mockFetch).toHaveBeenLastCalledWith(expect.objectContaining({ environment: 'prod' }));
  });

  it('shows blocked indicator when runtime truth is blocked', async () => {
    mockFetch.mockResolvedValue({ ...readinessPayload, runtimeTruthBlocked: true });
    render(<ReleaseCockpitPage />);
    await waitFor(() => expect(screen.getByText('release.blocked.title')).toBeTruthy());
  });
});
