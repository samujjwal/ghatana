/**
 * PrivacyRequestPage — focused coverage for GDPR / CCPA intake and fulfilment.
 *
 * @doc.type test
 * @doc.purpose RTL coverage for privacy request routing, capability gating, and fulfilment results
 * @doc.layer frontend
 */
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PrivacyRequestPage } from '@/pages/PrivacyRequestPage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as aepApi from '@/api/aep.api';
import { useCapabilities } from '@/hooks/useCapabilities';

vi.mock('@/api/aep.api');
vi.mock('@/hooks/useCapabilities', () => ({
  useCapabilities: vi.fn(),
}));

function renderWithQuery(ui: React.ReactElement) {
  return render(ui, { wrapper: createAepTestWrapper() });
}

describe('PrivacyRequestPage', () => {
  beforeEach(() => {
    vi.mocked(useCapabilities).mockReturnValue({
      capabilities: {
        dataCloud: true,
        redis: false,
        analyticsStore: true,
        aiSuggestions: true,
        nlpParse: true,
        gdprCompliance: true,
        soc2Compliance: true,
        piiEnforcement: true,
        killSwitch: true,
        gracefulDegradation: true,
        policyEngine: true,
        episodeLearning: false,
        humanInTheLoop: true,
        serverSideConsent: true,
        durableSessions: true,
        sseStreaming: true,
      },
      isLoading: false,
      isDegraded: false,
      generatedAt: new Date().toISOString(),
    });
    vi.mocked(aepApi.requestGdprAccess).mockResolvedValue({
      operationType: 'gdpr_access',
      tenantId: 'default',
      subjectId: 'subject-123',
      success: true,
      message: 'Compiled subject access report.',
      total: 3,
      breakdown: { audit: 1, memory: 2 },
      warnings: [],
      start: new Date().toISOString(),
      end: new Date().toISOString(),
    });
    vi.mocked(aepApi.requestCcpaOptOut).mockResolvedValue({
      operationType: 'ccpa_opt_out',
      tenantId: 'default',
      subjectId: 'consumer-9',
      success: true,
      message: 'Registered consumer opt-out marker.',
      total: 1,
      breakdown: { consent: 1 },
      warnings: [],
      start: new Date().toISOString(),
      end: new Date().toISOString(),
    });
    vi.mocked(aepApi.requestGdprPortability).mockResolvedValue({
      tenantId: 'default',
      subjectId: 'subject-123',
      exportedAt: new Date().toISOString(),
      collections: [{ name: 'audit', count: 2 }],
    });
    vi.mocked(aepApi.requestGdprErasure).mockResolvedValue({
      operationType: 'gdpr_erasure',
      tenantId: 'default',
      subjectId: 'subject-123',
      success: true,
      message: 'Deleted matching subject records.',
      total: 2,
      breakdown: { episodes: 2 },
      warnings: ['One immutable audit record remains by policy.'],
      start: new Date().toISOString(),
      end: new Date().toISOString(),
    });
  });

  it('shows an unavailable state when GDPR capability is disabled', async () => {
    vi.mocked(useCapabilities).mockReturnValue({
      capabilities: {
        dataCloud: true,
        redis: false,
        analyticsStore: true,
        aiSuggestions: true,
        nlpParse: true,
        gdprCompliance: false,
        soc2Compliance: true,
        piiEnforcement: true,
        killSwitch: true,
        gracefulDegradation: true,
        policyEngine: true,
        episodeLearning: false,
        humanInTheLoop: true,
        serverSideConsent: true,
        durableSessions: true,
        sseStreaming: true,
      },
      isLoading: false,
      isDegraded: false,
      generatedAt: new Date().toISOString(),
    });

    renderWithQuery(<PrivacyRequestPage />);
    expect(screen.getByText(/privacy fulfilment not available/i)).toBeInTheDocument();
  });

  it('triages intake text and lets the operator apply the recommendation', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.type(
      screen.getByLabelText(/request summary/i),
      'Customer asks us to delete their personal data and remove them from records.',
    );

    expect(await screen.findByText(/suggested route: gdpr erasure request/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /use recommendation/i }));
    expect(screen.getByRole('button', { name: /submit erasure request/i })).toBeInTheDocument();
    expect(screen.getByText(/matched deletion or erasure language/i)).toBeInTheDocument();
  });

  it('submits a GDPR access request and renders the fulfilment result', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.type(screen.getByLabelText(/request summary/i), 'Please send me a copy of my data.');
    await user.type(screen.getByLabelText(/subject id/i), 'subject-123');
    await user.click(screen.getByRole('button', { name: /submit access request/i }));

    await waitFor(() => expect(aepApi.requestGdprAccess).toHaveBeenCalledWith('subject-123', 'default'));
    expect(await screen.findByText(/compiled subject access report/i)).toBeInTheDocument();
    expect(screen.getByText(/records affected/i)).toBeInTheDocument();
    expect(screen.getByText('audit: 1')).toBeInTheDocument();
  });

  it('submits a GDPR portability/export request and renders the export result', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    // Select the portability operation button
    await user.click(screen.getByRole('button', { name: /portability/i }));
    await user.type(screen.getByLabelText(/subject id/i), 'subject-123');
    await user.click(screen.getByRole('button', { name: /submit portability request/i }));

    await waitFor(() => expect(aepApi.requestGdprPortability).toHaveBeenCalledWith('subject-123', 'default'));
    expect(await screen.findByText(/portable export payload/i)).toBeInTheDocument();
  });

  it('submits a GDPR erasure/delete request and shows warnings', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.click(screen.getByRole('button', { name: /erasure/i }));
    await user.type(screen.getByLabelText(/subject id/i), 'subject-123');
    await user.click(screen.getByRole('button', { name: /submit erasure request/i }));

    await waitFor(() => expect(aepApi.requestGdprErasure).toHaveBeenCalledWith('subject-123', 'default'));
    expect(await screen.findByText(/deleted matching subject records/i)).toBeInTheDocument();
    expect(screen.getByText(/one immutable audit record remains by policy/i)).toBeInTheDocument();
  });

  it('shows backend error state when API rejects', async () => {
    vi.mocked(aepApi.requestGdprAccess).mockRejectedValue(new Error('Backend unavailable'));
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.type(screen.getByLabelText(/subject id/i), 'subject-123');
    await user.click(screen.getByRole('button', { name: /submit access request/i }));

    await waitFor(() =>
      expect(screen.getByText(/backend unavailable/i)).toBeInTheDocument(),
    );
  });

  it('scopes request to current tenant — calls API with tenant id from atom', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.type(screen.getByLabelText(/subject id/i), 'subject-abc');
    await user.click(screen.getByRole('button', { name: /submit access request/i }));

    await waitFor(() =>
      expect(aepApi.requestGdprAccess).toHaveBeenCalledWith('subject-abc', 'default'),
    );
  });

  it('shows success fulfilment status label after access request', async () => {
    const user = userEvent.setup();
    renderWithQuery(<PrivacyRequestPage />);

    await user.type(screen.getByLabelText(/subject id/i), 'subject-123');
    await user.click(screen.getByRole('button', { name: /submit access request/i }));

    await waitFor(() => expect(screen.getByText(/fulfilled/i)).toBeInTheDocument());
  });
});
