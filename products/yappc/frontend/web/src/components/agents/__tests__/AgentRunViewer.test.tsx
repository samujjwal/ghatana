import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AgentRunViewer, type AgentRunRecord } from '../AgentRunViewer';

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
});
