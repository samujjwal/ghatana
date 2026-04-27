import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ActivityFeed, type ActivityItem } from '../ActivityFeed';

function makeItem(overrides: Partial<ActivityItem> = {}): ActivityItem {
  return {
    id: 'activity-1',
    action: 'requirement.approved',
    actor: 'reviewer-1',
    details: 'Requirement req-1 approved',
    occurredAt: '2026-04-26T10:00:00.000Z',
    severity: 'info',
    ...overrides,
  };
}

describe('ActivityFeed', () => {
  it('renders empty state', () => {
    render(<ActivityFeed items={[]} />);
    expect(screen.getByText('No activity yet.')).toBeDefined();
  });

  it('renders activity items', () => {
    render(
      <ActivityFeed
        items={[
          makeItem(),
          makeItem({ id: 'activity-2', action: 'approval.rejected', severity: 'warn' }),
        ]}
      />
    );

    expect(screen.getByText('Activity Feed')).toBeDefined();
    expect(screen.getByText('requirement.approved')).toBeDefined();
    expect(screen.getByText('approval.rejected')).toBeDefined();
    expect(screen.getAllByText('Requirement req-1 approved').length).toBeGreaterThan(0);
  });
});
