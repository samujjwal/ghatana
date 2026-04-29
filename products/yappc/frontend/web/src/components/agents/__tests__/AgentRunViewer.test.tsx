import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AgentRunViewer, type AgentRunRecord } from '../AgentRunViewer';

// ─── Mock RunLineage so tests stay unit-level ─────────────────────────────────
vi.mock('@/components/ai/RunLineage', () => ({
  RunLineage: ({ runId }: { runId: string }) => (
    <div data-testid="run-lineage" data-run-id={runId}>
      lineage:{runId}
    </div>
  ),
}));

vi.mock('@/services/ai/aepRunLineageApi', () => ({
  fetchAepRunLineage: vi.fn(),
}));

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeRun(overrides: Partial<AgentRunRecord> = {}): AgentRunRecord {
  return {
    id: 'run-1',
    agentName: 'RequirementAgent',
    status: 'RUNNING',
    stage: 'Normalize',
    retryCount: 0,
    createdAt: '2026-04-26T10:00:00.000Z',
    ...overrides,
  };
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('AgentRunViewer', () => {
  it('renders empty state', () => {
    render(<AgentRunViewer runs={[]} />);
    expect(screen.getByText('No agent runs available.')).toBeDefined();
  });

  it('renders run details and status counters', () => {
    render(
      <AgentRunViewer
        runs={[
          makeRun({ status: 'RUNNING' }),
          makeRun({ id: 'run-2', status: 'FAILED', errorMessage: 'Timeout' }),
        ]}
      />
    );

    expect(screen.getByText('Agent Run Viewer')).toBeDefined();
    expect(screen.getByText('1 running · 1 failed')).toBeDefined();
    expect(screen.getAllByText('RequirementAgent').length).toBeGreaterThan(0);
    expect(screen.getByText('Error: Timeout')).toBeDefined();
  });

  it('triggers retry callback for failed runs', () => {
    const onRetry = vi.fn();
    render(
      <AgentRunViewer
        runs={[makeRun({ status: 'FAILED', errorMessage: 'Error' })]}
        onRetryRun={onRetry}
      />
    );

    fireEvent.click(screen.getByText('Retry'));
    expect(onRetry).toHaveBeenCalledWith('run-1');
  });

  describe('AEP run lineage panel (F-Y009)', () => {
    it('shows lineage panel for SUCCEEDED runs', () => {
      render(<AgentRunViewer runs={[makeRun({ id: 'run-ok', status: 'SUCCEEDED' })]} />);
      const panel = screen.getByTestId('run-lineage');
      expect(panel).toBeDefined();
      expect(panel.getAttribute('data-run-id')).toBe('run-ok');
    });

    it('shows lineage panel for FAILED runs', () => {
      render(
        <AgentRunViewer
          runs={[makeRun({ id: 'run-fail', status: 'FAILED', errorMessage: 'Crash' })]}
        />
      );
      const panel = screen.getByTestId('run-lineage');
      expect(panel).toBeDefined();
      expect(panel.getAttribute('data-run-id')).toBe('run-fail');
    });

    it('does not show lineage panel for RUNNING runs', () => {
      render(<AgentRunViewer runs={[makeRun({ status: 'RUNNING' })]} />);
      expect(screen.queryByTestId('run-lineage')).toBeNull();
    });

    it('does not show lineage panel for QUEUED runs', () => {
      render(<AgentRunViewer runs={[makeRun({ status: 'QUEUED' })]} />);
      expect(screen.queryByTestId('run-lineage')).toBeNull();
    });

    it('does not show lineage panel for CANCELLED runs', () => {
      render(<AgentRunViewer runs={[makeRun({ status: 'CANCELLED' })]} />);
      expect(screen.queryByTestId('run-lineage')).toBeNull();
    });

    it('shows lineage for each completed run independently', () => {
      render(
        <AgentRunViewer
          runs={[
            makeRun({ id: 'run-a', status: 'SUCCEEDED' }),
            makeRun({ id: 'run-b', status: 'FAILED' }),
            makeRun({ id: 'run-c', status: 'RUNNING' }),
          ]}
        />
      );
      const panels = screen.getAllByTestId('run-lineage');
      expect(panels).toHaveLength(2);
      const ids = panels.map((p) => p.getAttribute('data-run-id'));
      expect(ids).toContain('run-a');
      expect(ids).toContain('run-b');
    });
  });
});
