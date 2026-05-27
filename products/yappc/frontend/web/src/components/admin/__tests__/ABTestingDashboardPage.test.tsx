/**
 * ABTestingDashboardPage unit tests (F-Y024)
 *
 * Verify A/B experiment list, view dialog, promote dialog, create dialog,
 * pause mutation, loading, error, and empty states.
 *
 * @doc.type test
 * @doc.purpose Verify admin A/B testing management UI lifecycle
 * @doc.layer product
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ABTestingDashboardPage } from '../ABTestingDashboardPage';
import type { Experiment } from '../../../services/admin/abTestingApi';

// ─────────────────────────────────────────────────────────────────────────────
// Mocks
// ─────────────────────────────────────────────────────────────────────────────

const mockListExperiments = vi.hoisted(
  () => vi.fn<() => Promise<{ items: Experiment[]; total: number }>>()
);
const mockCreateExperiment = vi.hoisted(() => vi.fn<() => Promise<Experiment>>());
const mockPromoteWinner = vi.hoisted(() => vi.fn<() => Promise<void>>());
const mockPauseExperiment = vi.hoisted(() => vi.fn<() => Promise<void>>());

vi.mock('../../../services/admin/abTestingApi', () => ({
  listExperiments: mockListExperiments,
  createExperiment: mockCreateExperiment,
  promoteWinner: mockPromoteWinner,
  pauseExperiment: mockPauseExperiment,
}));

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function makeVariant(overrides: Partial<Experiment['variants'][number]> = {}): Experiment['variants'][number] {
  return {
    variantId: 'var-a',
    variantName: 'Variant A',
    impressions: 1000,
    conversions: 120,
    conversionRate: 0.12,
    avgResponseTimeMs: 300,
    avgCostUsd: 0.005,
    avgQualityScore: 4.2,
    statisticalSignificance: false,
    ...overrides,
  };
}

function makeExperiment(overrides: Partial<Experiment> = {}): Experiment {
  return {
    id: 'exp-001',
    name: 'Prompt Template Test',
    description: 'Testing A vs B templates',
    status: 'running',
    promptName: 'requirement-gen-v1',
    createdAt: '2026-04-01T00:00:00.000Z',
    variants: [
      makeVariant({ variantId: 'var-a', variantName: 'Variant A' }),
      makeVariant({ variantId: 'var-b', variantName: 'Variant B', conversionRate: 0.15 }),
    ],
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ABTestingDashboardPage />
    </QueryClientProvider>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('ABTestingDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading spinner while fetching', async () => {
    mockListExperiments.mockReturnValue(new Promise(() => undefined)); // never resolves
    renderPage();
    expect(await screen.findByTestId('loading-spinner')).toBeInTheDocument();
  });

  it('shows error message when fetch fails', async () => {
    mockListExperiments.mockRejectedValue(new Error('network failure'));
    renderPage();
    expect(await screen.findByTestId('error-message')).toBeInTheDocument();
    expect(screen.getByTestId('error-message')).toHaveTextContent('network failure');
  });

  it('shows empty state when no experiments exist', async () => {
    mockListExperiments.mockResolvedValue({ items: [], total: 0 });
    renderPage();
    expect(await screen.findByTestId('empty-state')).toBeInTheDocument();
    expect(screen.getByTestId('empty-state')).toHaveTextContent('No experiments found');
  });

  it('renders experiment cards with name and status badge', async () => {
    mockListExperiments.mockResolvedValue({
      items: [makeExperiment({ id: 'exp-001', name: 'Prompt Template Test', status: 'running' })],
      total: 1,
    });
    renderPage();
    expect(await screen.findByTestId('experiment-row-exp-001')).toBeInTheDocument();
    expect(screen.getByText('Prompt Template Test')).toBeInTheDocument();
    expect(screen.getByText('running')).toBeInTheDocument();
  });

  it('renders Pause button only for running experiments', async () => {
    mockListExperiments.mockResolvedValue({
      items: [
        makeExperiment({ id: 'exp-001', status: 'running' }),
        makeExperiment({ id: 'exp-002', status: 'completed' }),
      ],
      total: 2,
    });
    renderPage();
    await screen.findByTestId('experiment-row-exp-001');
    expect(screen.getByTestId('btn-pause-exp-001')).toBeInTheDocument();
    expect(screen.queryByTestId('btn-pause-exp-002')).not.toBeInTheDocument();
  });

  it('renders Promote Winner button only for completed experiments without a winner', async () => {
    mockListExperiments.mockResolvedValue({
      items: [
        makeExperiment({ id: 'exp-001', status: 'running' }),
        makeExperiment({ id: 'exp-002', status: 'completed' }),
        makeExperiment({ id: 'exp-003', status: 'completed', winnerId: 'var-a' }),
      ],
      total: 3,
    });
    renderPage();
    await screen.findByTestId('experiment-row-exp-001');
    expect(screen.queryByTestId('btn-promote-exp-001')).not.toBeInTheDocument();
    expect(screen.getByTestId('btn-promote-exp-002')).toBeInTheDocument();
    expect(screen.queryByTestId('btn-promote-exp-003')).not.toBeInTheDocument();
  });

  it('opens view dialog when View Details is clicked', async () => {
    mockListExperiments.mockResolvedValue({
      items: [makeExperiment({ id: 'exp-001' })],
      total: 1,
    });
    renderPage();
    await screen.findByTestId('btn-view-exp-001');
    fireEvent.click(screen.getByTestId('btn-view-exp-001'));
    expect(await screen.findByTestId('view-dialog')).toBeInTheDocument();
    expect(screen.getByText('Experiment Details')).toBeInTheDocument();
  });

  it('opens promote dialog and confirm calls promoteWinner', async () => {
    mockListExperiments.mockResolvedValue({
      items: [makeExperiment({ id: 'exp-002', status: 'completed' })],
      total: 1,
    });
    mockPromoteWinner.mockResolvedValue(undefined);
    mockListExperiments.mockResolvedValueOnce({
      items: [makeExperiment({ id: 'exp-002', status: 'completed' })],
      total: 1,
    });
    renderPage();
    await screen.findByTestId('btn-promote-exp-002');
    fireEvent.click(screen.getByTestId('btn-promote-exp-002'));
    expect(await screen.findByTestId('promote-dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('btn-confirm-promote'));
    await waitFor(() => {
      expect(mockPromoteWinner).toHaveBeenCalledWith('exp-002', 'var-a', '');
    });
  });

  it('opens create dialog and submitting calls createExperiment', async () => {
    mockListExperiments.mockResolvedValue({ items: [], total: 0 });
    mockCreateExperiment.mockResolvedValue(
      makeExperiment({ id: 'exp-new', name: 'New Exp', status: 'running' })
    );
    renderPage();
    await screen.findByTestId('empty-state');
    fireEvent.click(screen.getByTestId('btn-create-experiment'));
    expect(await screen.findByTestId('create-dialog')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Experiment Name/i), {
      target: { value: 'New Exp' },
    });
    fireEvent.change(screen.getByLabelText(/Prompt Name/i), {
      target: { value: 'req-gen-v1' },
    });

    // submit button should now be enabled
    const createBtn = screen.getByTestId('btn-confirm-create');
    expect(createBtn).not.toBeDisabled();
    fireEvent.click(createBtn);

    await waitFor(() => {
      expect(mockCreateExperiment).toHaveBeenCalledWith(
        expect.objectContaining({ experimentName: 'New Exp', promptName: 'req-gen-v1' })
      );
    });
  });

  it('create submit button is disabled when required fields are empty', async () => {
    mockListExperiments.mockResolvedValue({ items: [], total: 0 });
    renderPage();
    await screen.findByTestId('empty-state');
    fireEvent.click(screen.getByTestId('btn-create-experiment'));
    await screen.findByTestId('create-dialog');
    expect(screen.getByTestId('btn-confirm-create')).toBeDisabled();
  });

  it('shows winner badge when winnerId is set', async () => {
    mockListExperiments.mockResolvedValue({
      items: [
        makeExperiment({
          id: 'exp-003',
          status: 'completed',
          winnerId: 'var-a',
          rollbackTargetWinnerId: 'var-b',
          reversible: true,
          variants: [makeVariant({ variantId: 'var-a', variantName: 'Variant A' })],
        }),
      ],
      total: 1,
    });
    renderPage();
    await screen.findByTestId('experiment-row-exp-003');
    expect(screen.getByText(/Winner.*Variant A/)).toBeInTheDocument();
    expect(screen.getByTestId('rollback-target-exp-003')).toHaveTextContent('Rollback: var-b');
  });
});
