import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { InsightPanel } from '../InsightPanel';

const baseInsight = {
  id: 'insight-1',
  projectId: 'project-1',
  title: 'Boundary leak detected',
  description: 'Transport concerns are leaking into a product service.',
  suggestion: 'Move mapping to an adapter boundary.',
  severity: 'warning' as const,
  category: 'architecture' as const,
  confidence: 0.88,
  sourceRef: 'products/yappc/core/services-platform',
  createdAt: '2026-04-06T12:00:00.000Z',
  read: false,
};

describe('InsightPanel', () => {
  it('renders active insights and actions when open', () => {
    render(
      <InsightPanel
        open
        insights={[baseInsight]}
        unreadCount={1}
        onClose={vi.fn()}
        onDismiss={vi.fn()}
        onMarkAllRead={vi.fn()}
      />
    );

    expect(screen.getByTestId('insight-panel')).toBeInTheDocument();
    expect(screen.getByText('Proactive insights')).toBeInTheDocument();
    expect(screen.getByText('Boundary leak detected')).toBeInTheDocument();
    expect(screen.getByText(/88% confidence/i)).toBeInTheDocument();
    expect(screen.getByText(/Move mapping to an adapter boundary/i)).toBeInTheDocument();
  });

  it('fires dismiss, mark all read, and close callbacks', () => {
    const onClose = vi.fn();
    const onDismiss = vi.fn();
    const onMarkAllRead = vi.fn();

    render(
      <InsightPanel
        open
        insights={[baseInsight]}
        unreadCount={1}
        onClose={onClose}
        onDismiss={onDismiss}
        onMarkAllRead={onMarkAllRead}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /mark all read/i }));
    fireEvent.click(screen.getByRole('button', { name: /dismiss boundary leak detected/i }));
    fireEvent.click(screen.getByRole('button', { name: /close insights panel/i }));

    expect(onMarkAllRead).toHaveBeenCalledTimes(1);
    expect(onDismiss).toHaveBeenCalledWith('insight-1');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('renders the empty state when there are no insights', () => {
    render(
      <InsightPanel
        open
        insights={[]}
        unreadCount={0}
        onClose={vi.fn()}
        onDismiss={vi.fn()}
        onMarkAllRead={vi.fn()}
      />
    );

    expect(screen.getByText('No active insights')).toBeInTheDocument();
  });
});