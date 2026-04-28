/**
 * CostOptimisationPanel tests (AI-Y8)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { CostOptimisationPanel } from '../CostOptimisationPanel';
import type { CostAnalysisData } from '../CostOptimisationPanel';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(data),
  } as Response);
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

const sampleData: CostAnalysisData = {
  runId: 'run-1',
  totalCostUsd: 0.042,
  breakdown: [
    {
      model: 'gpt-4o',
      calls: 3,
      costUsd: 0.036,
      cheaperAlternative: { model: 'gpt-4o-mini', estimatedSavingUsd: 0.024 },
    },
    {
      model: 'text-embedding-3-small',
      calls: 1,
      costUsd: 0.006,
    },
  ],
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('CostOptimisationPanel (AI-Y8)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(
      <Wrapper>
        <CostOptimisationPanel runId="run-1" />
      </Wrapper>
    );

    expect(screen.getByTestId('cost-loading')).toBeInTheDocument();
  });

  it('renders model breakdown with costs and call counts', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <CostOptimisationPanel runId="run-1" />
      </Wrapper>
    );

    const panel = await screen.findByTestId('cost-panel');
    expect(panel).toBeInTheDocument();

    expect(screen.getByTestId('model-row-gpt-4o')).toBeInTheDocument();
    expect(screen.getByText('gpt-4o')).toBeInTheDocument();
    expect(screen.getByTestId('model-row-text-embedding-3-small')).toBeInTheDocument();
  });

  it('shows cheaper alternative suggestion for eligible models', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <CostOptimisationPanel runId="run-1" />
      </Wrapper>
    );

    const altChip = await screen.findByTestId('cheaper-alt-gpt-4o');
    expect(altChip).toBeInTheDocument();
    expect(altChip).toHaveTextContent('gpt-4o-mini');

    // No cheaper alternative chip for the embedding model
    expect(screen.queryByTestId('cheaper-alt-text-embedding-3-small')).not.toBeInTheDocument();
  });

  it('shows savings summary when alternatives exist', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <CostOptimisationPanel runId="run-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('cost-savings-summary')).toBeInTheDocument();
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(
      <Wrapper>
        <CostOptimisationPanel runId="run-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('cost-error')).toBeInTheDocument();
  });

  it('renders nothing when runId is empty', () => {
    const { container } = render(
      <Wrapper>
        <CostOptimisationPanel runId="" />
      </Wrapper>
    );

    expect(container.firstChild).toBeNull();
    expect(mockFetch).not.toHaveBeenCalled();
  });
});
