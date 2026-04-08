/**
 * AgentMonitor Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AgentMonitor, type OrchestrationState, type AgentInfo } from '../AgentMonitor';

function createAgent(overrides: Partial<AgentInfo> = {}): AgentInfo {
  return {
    id: 'agent-1',
    name: 'Code Agent',
    type: 'code-gen',
    status: 'running',
    confidence: 0.92,
    currentTask: 'Generate auth module',
    completedTasks: 3,
    totalTasks: 5,
    lastActivity: new Date().toISOString(),
    metrics: { avgResponseTime: 120, successRate: 0.95, tokensUsed: 4200 },
    ...overrides,
  };
}

function createOrchestration(overrides: Partial<OrchestrationState> = {}): OrchestrationState {
  return {
    agents: [createAgent()],
    conflicts: [],
    overallProgress: 60,
    startedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('AgentMonitor', () => {
  it('should render header with orchestration label', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText('Agent Orchestration')).toBeDefined();
  });

  it('should render overall progress percentage', () => {
    render(<AgentMonitor orchestration={createOrchestration({ overallProgress: 75 })} />);
    expect(screen.getByText(/75%/)).toBeDefined();
  });

  it('should render agent cards', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText('Code Agent')).toBeDefined();
    expect(screen.getByText('Generate auth module')).toBeDefined();
  });

  it('should show agent task progress', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText(/3\/5 tasks/)).toBeDefined();
  });

  it('should show agent confidence', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText(/92% conf/)).toBeDefined();
  });

  it('should show agent metrics', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText('120ms avg')).toBeDefined();
    expect(screen.getByText('95% success')).toBeDefined();
    expect(screen.getByText('4200 tokens')).toBeDefined();
  });

  it('should show running agent count', () => {
    render(<AgentMonitor orchestration={createOrchestration()} />);
    expect(screen.getByText(/1 active/)).toBeDefined();
  });

  it('should show failed agent count when present', () => {
    const orch = createOrchestration({
      agents: [createAgent({ status: 'failed' })],
    });
    render(<AgentMonitor orchestration={orch} />);
    expect(screen.getByText(/1 failed/)).toBeDefined();
  });

  it('should render conflicts section when conflicts exist', () => {
    const orch = createOrchestration({
      agents: [
        createAgent({ id: 'a1', name: 'Agent A' }),
        createAgent({ id: 'a2', name: 'Agent B' }),
      ],
      conflicts: [
        {
          id: 'c1',
          agentIds: ['a1', 'a2'],
          description: 'Both agents modifying same file',
          severity: 'high',
        },
      ],
    });
    render(<AgentMonitor orchestration={orch} />);
    expect(screen.getByText(/Conflicts \(1\)/)).toBeDefined();
    expect(screen.getByText('Both agents modifying same file')).toBeDefined();
  });

  it('should call onRetryAgent when retry clicked on failed agent', () => {
    const onRetry = vi.fn();
    const orch = createOrchestration({
      agents: [createAgent({ id: 'a1', status: 'failed' })],
    });
    render(<AgentMonitor orchestration={orch} onRetryAgent={onRetry} />);
    const retryBtn = screen.getByRole('button');
    fireEvent.click(retryBtn);
    expect(onRetry).toHaveBeenCalledWith('a1');
  });

  it('should call onResolveConflict when resolve clicked', () => {
    const onResolve = vi.fn();
    const orch = createOrchestration({
      agents: [
        createAgent({ id: 'a1', name: 'Agent A' }),
        createAgent({ id: 'a2', name: 'Agent B' }),
      ],
      conflicts: [
        {
          id: 'c1',
          agentIds: ['a1', 'a2'],
          description: 'Conflict',
          severity: 'medium',
        },
      ],
    });
    render(<AgentMonitor orchestration={orch} onResolveConflict={onResolve} />);
    fireEvent.click(screen.getByText('Resolve'));
    expect(onResolve).toHaveBeenCalledWith('c1');
  });

  it('should not show resolved conflicts', () => {
    const orch = createOrchestration({
      conflicts: [
        {
          id: 'c1',
          agentIds: ['a1', 'a2'],
          description: 'Old conflict',
          severity: 'low',
          resolvedAt: new Date().toISOString(),
        },
      ],
    });
    render(<AgentMonitor orchestration={orch} />);
    expect(screen.queryByText('Old conflict')).toBeNull();
  });

  it('should render multiple agents', () => {
    const orch = createOrchestration({
      agents: [
        createAgent({ id: 'a1', name: 'Code Agent' }),
        createAgent({ id: 'a2', name: 'Test Agent', status: 'completed' }),
        createAgent({ id: 'a3', name: 'Review Agent', status: 'idle' }),
      ],
    });
    render(<AgentMonitor orchestration={orch} />);
    expect(screen.getByText('Code Agent')).toBeDefined();
    expect(screen.getByText('Test Agent')).toBeDefined();
    expect(screen.getByText('Review Agent')).toBeDefined();
    expect(screen.getByText(/Agents \(3\)/)).toBeDefined();
  });

  it('should accept className prop', () => {
    const { container } = render(
      <AgentMonitor orchestration={createOrchestration()} className="test-class" />,
    );
    expect(container.firstElementChild?.classList.contains('test-class')).toBe(true);
  });
});
