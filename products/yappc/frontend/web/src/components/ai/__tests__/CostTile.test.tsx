import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CostTile } from '../CostTile';
import * as projectAiCostApi from '@/services/ai/projectAiCostApi';

vi.mock('@/services/ai/projectAiCostApi');

function wrap(ui: React.ReactElement): React.ReactElement {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{ui}</QueryClientProvider>;
}

const stubData: projectAiCostApi.ProjectAiCostResponse = {
  projectId: 'proj-abc',
  totalRuns: 20,
  succeededRuns: 16,
  estimatedCostUSD: 0.08,
  currency: 'USD',
  costBasis: 'indicative',
  byAgent: {
    RequirementEnricher: { count: 10, estimatedCostUSD: 0.05 },
    CodeGenerator: { count: 6, estimatedCostUSD: 0.03 },
  },
};

describe('CostTile', () => {
  beforeEach(() => {
    vi.mocked(projectAiCostApi.fetchProjectAiCost).mockResolvedValue(stubData);
  });

  it('renders loading skeleton before data arrives', () => {
    vi.mocked(projectAiCostApi.fetchProjectAiCost).mockReturnValueOnce(
      new Promise(() => undefined)
    );
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(screen.getByRole('status', { name: /loading ai cost/i })).toBeInTheDocument();
  });

  it('displays estimated cost in USD format', async () => {
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(await screen.findByText('$0.08')).toBeInTheDocument();
  });

  it('displays total and succeeded run counts', async () => {
    render(wrap(<CostTile projectId="proj-abc" />));
    await screen.findByText('$0.08');
    expect(screen.getByText('20')).toBeInTheDocument();
    expect(screen.getByText('16')).toBeInTheDocument();
  });

  it('shows estimate badge for indicative cost basis', async () => {
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(await screen.findByText('Estimate')).toBeInTheDocument();
  });

  it('does not show estimate badge for actual cost basis', async () => {
    vi.mocked(projectAiCostApi.fetchProjectAiCost).mockResolvedValueOnce({
      ...stubData,
      costBasis: 'actual',
    });
    render(wrap(<CostTile projectId="proj-abc" />));
    await screen.findByText('$0.08');
    expect(screen.queryByText('Estimate')).not.toBeInTheDocument();
  });

  it('renders top agents breakdown', async () => {
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(await screen.findByText('RequirementEnricher')).toBeInTheDocument();
    expect(screen.getByText('CodeGenerator')).toBeInTheDocument();
  });

  it('renders error state when fetch fails', async () => {
    vi.mocked(projectAiCostApi.fetchProjectAiCost).mockRejectedValueOnce(
      new Error('Network error')
    );
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(
      await screen.findByText(/could not load ai cost data/i)
    ).toBeInTheDocument();
  });

  it('applies custom className to container', async () => {
    const { container } = render(
      wrap(<CostTile projectId="proj-abc" className="custom-tile" />)
    );
    await screen.findByText('$0.08');
    expect(container.firstChild).toHaveClass('custom-tile');
  });

  it('formats zero cost correctly', async () => {
    vi.mocked(projectAiCostApi.fetchProjectAiCost).mockResolvedValueOnce({
      ...stubData,
      estimatedCostUSD: 0,
      succeededRuns: 0,
      byAgent: {},
    });
    render(wrap(<CostTile projectId="proj-abc" />));
    expect(await screen.findByText('$0.00')).toBeInTheDocument();
  });
});
