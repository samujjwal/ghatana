import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DecisionQueue } from '../DecisionQueue';
import type { DecisionQueueItem } from '../../../clients/dashboard';

const makeItem = (
  id: string,
  overrides: Partial<DecisionQueueItem> = {}
): DecisionQueueItem => ({
  id,
  title: `Decision ${id}`,
  description: `Description for ${id}`,
  type: 'approval',
  priority: 'high',
  projectId: 'proj-1',
  requestedBy: 'user@test.com',
  requestedAt: '2026-04-01T10:00:00Z',
  status: 'pending',
  ...overrides,
});

describe('DecisionQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders heading and empty state when no pending items', () => {
    render(<DecisionQueue items={[]} />);
    expect(screen.getByText('Decision Queue')).toBeInTheDocument();
    expect(screen.getByText(/No pending decisions/i)).toBeInTheDocument();
  });

  it('renders pending items', () => {
    const items = [makeItem('d1'), makeItem('d2')];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText('Decision d1')).toBeInTheDocument();
    expect(screen.getByText('Decision d2')).toBeInTheDocument();
  });

  it('shows item count chip', () => {
    const items = [makeItem('d1'), makeItem('d2')];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('filters out non-pending items', () => {
    const items = [
      makeItem('d1', { status: 'approved' }),
      makeItem('d2', { status: 'pending' }),
      makeItem('d3', { status: 'rejected' }),
    ];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText('Decision d2')).toBeInTheDocument();
    expect(screen.queryByText('Decision d1')).not.toBeInTheDocument();
    expect(screen.queryByText('Decision d3')).not.toBeInTheDocument();
  });

  it('filters by projectId when provided', () => {
    const items = [
      makeItem('d1', { projectId: 'proj-1' }),
      makeItem('d2', { projectId: 'proj-2' }),
    ];
    render(<DecisionQueue items={items} projectId="proj-1" />);
    expect(screen.getByText('Decision d1')).toBeInTheDocument();
    expect(screen.queryByText('Decision d2')).not.toBeInTheDocument();
  });

  it('calls onViewAll when View all button clicked', () => {
    const onViewAll = vi.fn();
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onViewAll={onViewAll} />);
    // There are two "View all" buttons: header and footer
    const buttons = screen.getAllByText(/View all/i);
    fireEvent.click(buttons[0]);
    expect(onViewAll).toHaveBeenCalledTimes(1);
  });

  it('shows approve/reject/defer buttons when handlers provided', () => {
    const items = [makeItem('d1')];
    render(
      <DecisionQueue
        items={items}
        onApprove={vi.fn()}
        onReject={vi.fn()}
        onDefer={vi.fn()}
      />
    );
    expect(screen.getByTitle('Approve')).toBeInTheDocument();
    expect(screen.getByTitle('Reject')).toBeInTheDocument();
    expect(screen.getByTitle('Defer')).toBeInTheDocument();
  });

  it('does not show action buttons when no handlers provided', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} />);
    expect(screen.queryByTitle('Approve')).not.toBeInTheDocument();
    expect(screen.queryByTitle('Reject')).not.toBeInTheDocument();
    expect(screen.queryByTitle('Defer')).not.toBeInTheDocument();
  });

  it('calls onApprove with item id when approve clicked', async () => {
    const onApprove = vi.fn().mockResolvedValue(undefined);
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onApprove={onApprove} />);
    fireEvent.click(screen.getByTitle('Approve'));
    await waitFor(() => {
      expect(onApprove).toHaveBeenCalledWith('d1');
    });
  });

  it('calls onReject with item id when reject clicked', async () => {
    const onReject = vi.fn().mockResolvedValue(undefined);
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onReject={onReject} />);
    fireEvent.click(screen.getByTitle('Reject'));
    await waitFor(() => {
      expect(onReject).toHaveBeenCalledWith('d1');
    });
  });

  it('calls onDefer with item id when defer clicked', async () => {
    const onDefer = vi.fn().mockResolvedValue(undefined);
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onDefer={onDefer} />);
    fireEvent.click(screen.getByTitle('Defer'));
    await waitFor(() => {
      expect(onDefer).toHaveBeenCalledWith('d1');
    });
  });

  it('shows checkbox for selection when actions provided', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onApprove={vi.fn()} />);
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });

  it('toggles item selection on checkbox click', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onApprove={vi.fn()} />);
    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });

  it('renders item description', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText('Description for d1')).toBeInTheDocument();
  });

  it('renders item priority', () => {
    const items = [makeItem('d1', { priority: 'critical' })];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText('critical')).toBeInTheDocument();
  });

  it('renders requestedBy', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText(/Requested by: user@test.com/i)).toBeInTheDocument();
  });

  it('renders dueDate when provided', () => {
    const items = [makeItem('d1', { dueDate: '2026-05-15T00:00:00Z' })];
    render(<DecisionQueue items={items} />);
    expect(screen.getByText(/Due:/i)).toBeInTheDocument();
  });

  it('renders "Go to Decision Queue" footer button with items', () => {
    const items = [makeItem('d1')];
    render(<DecisionQueue items={items} onViewAll={vi.fn()} />);
    expect(screen.getByText('Go to Decision Queue')).toBeInTheDocument();
  });
});
