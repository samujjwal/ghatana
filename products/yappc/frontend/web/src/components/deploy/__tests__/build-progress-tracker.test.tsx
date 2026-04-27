/**
 * Tests for BuildProgressTracker (deploy/ component)
 */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import {
  BuildProgressTracker,
  type BuildInfo,
  type BuildStep,
} from '../BuildProgressTracker';

// ─── Fixtures ────────────────────────────────────────────────────────────────

const makeStep = (overrides: Partial<BuildStep> = {}): BuildStep => ({
  id: 's1',
  name: 'Build',
  status: 'success',
  ...overrides,
});

const makeBuild = (overrides: Partial<BuildInfo> = {}): BuildInfo => ({
  id: 'build-123',
  version: '1.2.3',
  branch: 'main',
  commit: 'abc1234defgh',
  triggeredBy: 'ci-bot',
  triggeredAt: new Date().toISOString(),
  status: 'success',
  steps: [makeStep()],
  ...overrides,
});

const defaultProps = {
  build: makeBuild(),
  onRefresh: vi.fn().mockResolvedValue(undefined),
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BuildProgressTracker', () => {
  it('renders build id in header', () => {
    render(<BuildProgressTracker {...defaultProps} />);
    expect(screen.getByText('Build #build-123')).toBeTruthy();
  });

  it('renders version and branch info', () => {
    render(<BuildProgressTracker {...defaultProps} />);
    // "v1.2.3 • main • abc1234"
    expect(screen.getByText(/v1\.2\.3/)).toBeTruthy();
    expect(screen.getByText(/main/)).toBeTruthy();
    expect(screen.getByText(/abc1234/)).toBeTruthy();
  });

  it('renders status in uppercase', () => {
    render(<BuildProgressTracker {...defaultProps} />);
    expect(screen.getByText('SUCCESS')).toBeTruthy();
  });

  it('renders RUNNING status', () => {
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'running' })}
      />
    );
    expect(screen.getByText('RUNNING')).toBeTruthy();
  });

  it('renders FAILED status', () => {
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'failed' })}
      />
    );
    expect(screen.getByText('FAILED')).toBeTruthy();
  });

  it('shows step count progress', () => {
    const build = makeBuild({
      status: 'success',
      steps: [
        makeStep({ id: '1', status: 'success' }),
        makeStep({ id: '2', status: 'success' }),
      ],
    });
    render(<BuildProgressTracker {...defaultProps} build={build} />);
    expect(screen.getByText('2/2 steps')).toBeTruthy();
  });

  it('shows partial step count when some steps incomplete', () => {
    const build = makeBuild({
      status: 'running',
      steps: [
        makeStep({ id: '1', status: 'success' }),
        makeStep({ id: '2', status: 'running' }),
        makeStep({ id: '3', status: 'pending' }),
      ],
    });
    render(<BuildProgressTracker {...defaultProps} build={build} />);
    // success counts as completed
    expect(screen.getByText('1/3 steps')).toBeTruthy();
  });

  it('renders Cancel button when status is running and onCancel provided', () => {
    const onCancel = vi.fn().mockResolvedValue(undefined);
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'running' })}
        onCancel={onCancel}
      />
    );
    expect(screen.getByText('Cancel')).toBeTruthy();
  });

  it('calls onCancel when Cancel button clicked', () => {
    const onCancel = vi.fn().mockResolvedValue(undefined);
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'running' })}
        onCancel={onCancel}
      />
    );
    fireEvent.click(screen.getByText('Cancel'));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('does not render Cancel button when status is not running', () => {
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'success' })}
        onCancel={vi.fn().mockResolvedValue(undefined)}
      />
    );
    expect(screen.queryByText('Cancel')).toBeNull();
  });

  it('renders Retry button when status is failed and onRetry provided', () => {
    const onRetry = vi.fn().mockResolvedValue(undefined);
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'failed' })}
        onRetry={onRetry}
      />
    );
    expect(screen.getByText('Retry')).toBeTruthy();
  });

  it('calls onRetry when Retry button clicked', () => {
    const onRetry = vi.fn().mockResolvedValue(undefined);
    render(
      <BuildProgressTracker
        {...defaultProps}
        build={makeBuild({ status: 'failed' })}
        onRetry={onRetry}
      />
    );
    fireEvent.click(screen.getByText('Retry'));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('calls onRefresh when refresh button clicked', async () => {
    const onRefresh = vi.fn().mockResolvedValue(undefined);
    render(
      <BuildProgressTracker
        {...defaultProps}
        onRefresh={onRefresh}
      />
    );
    // Refresh button is first in the header (no cancel/retry for success status)
    const buttons = screen.getAllByRole('button');
    fireEvent.click(buttons[0]);
    await waitFor(() => {
      expect(onRefresh).toHaveBeenCalledOnce();
    });
  });

  it('renders step name in steps list', () => {
    const build = makeBuild({
      steps: [makeStep({ name: 'Run Tests' })],
    });
    render(<BuildProgressTracker {...defaultProps} build={build} />);
    expect(screen.getByText('Run Tests')).toBeTruthy();
  });

  it('handles empty steps array gracefully', () => {
    const build = makeBuild({ steps: [] });
    render(<BuildProgressTracker {...defaultProps} build={build} />);
    expect(screen.getByText('0/0 steps')).toBeTruthy();
  });

  it('renders commit message when provided', () => {
    const build = makeBuild({ commitMessage: 'Fix critical bug' });
    render(<BuildProgressTracker {...defaultProps} build={build} />);
    expect(screen.getByText('Fix critical bug')).toBeTruthy();
  });
});
