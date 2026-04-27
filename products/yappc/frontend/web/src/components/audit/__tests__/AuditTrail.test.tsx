import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuditTrail } from '../AuditTrail';

const makeEvent = (overrides: Record<string, unknown> = {}) => ({
  id: 'evt-1',
  timestamp: new Date('2026-01-15T10:30:00Z').toISOString(),
  category: 'workflow' as const,
  action: 'workflow.created',
  severity: 'info' as const,
  actor: { type: 'user' as const, id: 'u1', name: 'Alice' },
  target: { type: 'workflow', id: 'w1', name: 'My Workflow' },
  message: 'Workflow was created',
  metadata: {},
  ...overrides,
});

describe('AuditTrail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty state when no events', () => {
    render(<AuditTrail events={[]} />);
    expect(screen.getByText('No events found')).toBeInTheDocument();
  });

  it('shows event count in stats bar', () => {
    render(<AuditTrail events={[makeEvent()]} />);
    expect(screen.getByText('1 events')).toBeInTheDocument();
  });

  it('renders event actor name', () => {
    render(<AuditTrail events={[makeEvent()]} />);
    expect(screen.getByText(/Alice/)).toBeInTheDocument();
  });

  it('renders event message', () => {
    render(<AuditTrail events={[makeEvent()]} />);
    expect(screen.getByText('Workflow was created')).toBeInTheDocument();
  });

  it('renders error alert when error prop is provided', () => {
    render(<AuditTrail error="Failed to load events" />);
    expect(screen.getByText('Failed to load events')).toBeInTheDocument();
  });

  it('shows refresh button', () => {
    const onRefresh = vi.fn();
    render(<AuditTrail events={[]} onRefresh={onRefresh} />);
    // IconButton from design-system renders with aria-label="Icon button"
    const refreshBtn = screen.getByRole('button', { name: /Icon button/i });
    expect(refreshBtn).toBeInTheDocument();
  });

  it('calls onRefresh when refresh button clicked', () => {
    const onRefresh = vi.fn();
    render(<AuditTrail events={[]} onRefresh={onRefresh} />);
    fireEvent.click(screen.getByRole('button', { name: /Icon button/i }));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it('disables refresh button when isLoading', () => {
    render(<AuditTrail events={[]} onRefresh={vi.fn()} isLoading={true} />);
    expect(screen.getByRole('button', { name: /Icon button/i })).toBeDisabled();
  });

  it('renders search input', () => {
    render(<AuditTrail events={[]} />);
    expect(screen.getByPlaceholderText('Search events...')).toBeInTheDocument();
  });

  it('filters events by search query', () => {
    const events = [
      makeEvent({ id: 'e1', actor: { type: 'user' as const, id: 'u1', name: 'Alice' }, message: 'Created workflow' }),
      makeEvent({ id: 'e2', actor: { type: 'user' as const, id: 'u2', name: 'Bob' }, message: 'Deleted task' }),
    ];
    render(<AuditTrail events={events} />);
    expect(screen.getByText('2 events')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText('Search events...'), { target: { value: 'Created' } });
    expect(screen.getByText('1 events')).toBeInTheDocument();
  });

  it('calls onExport with json when JSON button clicked', () => {
    const onExport = vi.fn();
    render(<AuditTrail events={[]} onExport={onExport} />);
    fireEvent.click(screen.getByRole('button', { name: /JSON/i }));
    expect(onExport).toHaveBeenCalledWith('json');
  });

  it('calls onExport with csv when CSV button clicked', () => {
    const onExport = vi.fn();
    render(<AuditTrail events={[]} onExport={onExport} />);
    fireEvent.click(screen.getByRole('button', { name: /CSV/i }));
    expect(onExport).toHaveBeenCalledWith('csv');
  });

  it('calls onExport with pdf when PDF button clicked', () => {
    const onExport = vi.fn();
    render(<AuditTrail events={[]} onExport={onExport} />);
    fireEvent.click(screen.getByRole('button', { name: /PDF/i }));
    expect(onExport).toHaveBeenCalledWith('pdf');
  });

  it('shows multiple events with correct count', () => {
    const events = [makeEvent({ id: 'e1' }), makeEvent({ id: 'e2' }), makeEvent({ id: 'e3' })];
    render(<AuditTrail events={events} />);
    expect(screen.getByText('3 events')).toBeInTheDocument();
  });
});
