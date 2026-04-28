import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RunLineage } from '../RunLineage';
import type { RunLineageData } from '../RunLineage';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function Wrapper({ children }: { children: React.ReactNode }): React.ReactElement {
  const qc = makeQueryClient();
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const LINEAGE_DATA: RunLineageData = {
  runId: 'run-abc',
  nodes: [
    { id: 'wf-1', label: 'Main Workflow', type: 'workflow' },
    { id: 'plan-1', label: 'Design Phase', type: 'plan' },
    { id: 'run-abc', label: 'Run #3', type: 'run' },
  ],
};

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('RunLineage', () => {
  it('shows loading skeletons while fetching', () => {
    const fetchLineage = vi.fn(() => new Promise<RunLineageData>(() => {}));
    render(<RunLineage runId="run-abc" fetchLineage={fetchLineage} />, { wrapper: Wrapper });
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
  });

  it('renders lineage nodes after successful fetch', async () => {
    const fetchLineage = vi.fn().mockResolvedValue(LINEAGE_DATA);
    render(<RunLineage runId="run-abc" fetchLineage={fetchLineage} />, { wrapper: Wrapper });
    expect(await screen.findByRole('navigation', { name: /lineage/i })).toBeInTheDocument();
    expect(screen.getByText('Main Workflow')).toBeInTheDocument();
    expect(screen.getByText('Design Phase')).toBeInTheDocument();
    expect(screen.getByText('Run #3')).toBeInTheDocument();
  });

  it('renders separator between nodes', async () => {
    const fetchLineage = vi.fn().mockResolvedValue(LINEAGE_DATA);
    render(<RunLineage runId="run-abc" fetchLineage={fetchLineage} />, { wrapper: Wrapper });
    await screen.findByRole('navigation', { name: /lineage/i });
    const separators = screen.getAllByText('/');
    expect(separators).toHaveLength(2); // 3 nodes → 2 separators
  });

  it('shows empty state when no nodes returned', async () => {
    const fetchLineage = vi.fn().mockResolvedValue({ runId: 'x', nodes: [] });
    render(<RunLineage runId="x" fetchLineage={fetchLineage} />, { wrapper: Wrapper });
    expect(await screen.findByText(/no lineage available/i)).toBeInTheDocument();
  });

  it('shows error state on fetch failure', async () => {
    const fetchLineage = vi.fn().mockRejectedValue(new Error('Network error'));
    render(<RunLineage runId="run-fail" fetchLineage={fetchLineage} />, { wrapper: Wrapper });
    expect(await screen.findByRole('alert')).toHaveTextContent('Network error');
  });

  it('calls onNodeClick when a node is clicked', async () => {
    const user = userEvent.setup();
    const fetchLineage = vi.fn().mockResolvedValue(LINEAGE_DATA);
    const onNodeClick = vi.fn();
    render(
      <RunLineage runId="run-abc" fetchLineage={fetchLineage} onNodeClick={onNodeClick} />,
      { wrapper: Wrapper },
    );
    await screen.findByRole('navigation', { name: /lineage/i });
    await user.click(screen.getByRole('button', { name: /workflow Main Workflow/i }));
    expect(onNodeClick).toHaveBeenCalledWith(LINEAGE_DATA.nodes[0]);
  });
});
