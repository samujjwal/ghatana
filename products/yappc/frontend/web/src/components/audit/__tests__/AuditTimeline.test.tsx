import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { AuditTimeline } from '../AuditTimeline';

describe('AuditTimeline', () => {
  it('renders timeline entries', () => {
    render(
      <AuditTimeline
        entries={[
          {
            id: 'evt-1',
            title: 'Requirement approved',
            description: 'Reviewer accepted generated version v2.',
            actor: 'reviewer:alice',
            level: 'INFO',
            createdAt: '2026-04-26T13:00:00.000Z',
          },
        ]}
      />
    );

    expect(screen.getByTestId('audit-timeline')).toBeTruthy();
    expect(screen.getByText('Requirement approved')).toBeTruthy();
    expect(screen.getByText(/Actor:\s+reviewer:alice/i)).toBeTruthy();
  });
});
