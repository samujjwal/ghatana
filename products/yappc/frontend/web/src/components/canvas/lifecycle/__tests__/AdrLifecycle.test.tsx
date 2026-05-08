import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AdrLifecycle } from '../AdrLifecycle';
import type { AdrLifecycleRecord } from '../AdrLifecycle';

function makeAdr(overrides: Partial<AdrLifecycleRecord> = {}): AdrLifecycleRecord {
  return {
    id: 'adr-1',
    title: 'Use PostgreSQL over MySQL',
    status: 'DRAFT',
    auditTrail: [],
    ...overrides,
  };
}

describe('AdrLifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the ADR title and current status badge', () => {
    render(
      <AdrLifecycle
        adr={makeAdr()}
        currentUser="alice"
        onTransition={vi.fn()}
      />
    );
    expect(screen.getByText('Use PostgreSQL over MySQL')).toBeInTheDocument();
    expect(screen.getAllByText('Draft').length).toBeGreaterThanOrEqual(1);
  });

  it('shows Submit for Review button when status is DRAFT', () => {
    render(
      <AdrLifecycle
        adr={makeAdr({ status: 'DRAFT' })}
        currentUser="alice"
        onTransition={vi.fn()}
      />
    );
    expect(screen.getByTestId('adr-transition-IN_REVIEW')).toHaveClass('inline-flex');
  });

  it('shows Accept Decision and Send Back to Draft when status is IN_REVIEW', () => {
    render(
      <AdrLifecycle
        adr={makeAdr({ status: 'IN_REVIEW' })}
        currentUser="alice"
        onTransition={vi.fn()}
      />
    );
    expect(screen.getByTestId('adr-transition-ACCEPTED')).toBeInTheDocument();
    expect(screen.getByTestId('adr-transition-DRAFT')).toBeInTheDocument();
  });

  it('shows no transition buttons for SUPERSEDED status', () => {
    render(
      <AdrLifecycle
        adr={makeAdr({ status: 'SUPERSEDED' })}
        currentUser="alice"
        onTransition={vi.fn()}
      />
    );
    expect(screen.queryByTestId(/adr-transition-/)).toBeNull();
  });

  it('calls onTransition with correct arguments on confirm', async () => {
    const updated: AdrLifecycleRecord = makeAdr({
      status: 'IN_REVIEW',
      auditTrail: [
        {
          id: 'audit-1',
          fromStatus: 'DRAFT',
          toStatus: 'IN_REVIEW',
          performedBy: 'alice',
          timestamp: new Date().toISOString(),
          note: 'ready',
        },
      ],
    });
    const onTransition = vi.fn().mockResolvedValue(updated);

    render(
      <AdrLifecycle
        adr={makeAdr({ status: 'DRAFT' })}
        currentUser="alice"
        onTransition={onTransition}
      />
    );

    fireEvent.click(screen.getByTestId('adr-transition-IN_REVIEW'));
    // Transition form should appear
    expect(screen.getByTestId('adr-transition-form')).toBeInTheDocument();
    expect(screen.getByLabelText('Transition note')).toHaveClass('text-sm');

    // Type a note
    fireEvent.change(screen.getByLabelText('Transition note'), {
      target: { value: 'ready' },
    });

    const confirmButton = screen.getByText('Confirm').closest('button');
    expect(confirmButton).toHaveClass('inline-flex');
    fireEvent.click(confirmButton!);
    await waitFor(() =>
      expect(onTransition).toHaveBeenCalledWith('adr-1', 'IN_REVIEW', 'ready')
    );
  });

  it('shows audit trail when toggle clicked', () => {
    const adr = makeAdr({
      status: 'ACCEPTED',
      auditTrail: [
        {
          id: 'a1',
          fromStatus: 'DRAFT',
          toStatus: 'IN_REVIEW',
          performedBy: 'alice',
          timestamp: '2026-04-27T10:00:00.000Z',
        },
        {
          id: 'a2',
          fromStatus: 'IN_REVIEW',
          toStatus: 'ACCEPTED',
          performedBy: 'bob',
          timestamp: '2026-04-27T11:00:00.000Z',
          note: 'Approved after review',
        },
      ],
    });

    render(
      <AdrLifecycle adr={adr} currentUser="alice" onTransition={vi.fn()} />
    );

    fireEvent.click(screen.getByLabelText('Toggle audit trail'));
    expect(screen.getByTestId('adr-audit-trail')).toBeInTheDocument();
    expect(screen.getByText('"Approved after review"')).toBeInTheDocument();
  });

  it('renders the lifecycle stepper with correct phases', () => {
    render(
      <AdrLifecycle
        adr={makeAdr({ status: 'IN_REVIEW' })}
        currentUser="alice"
        onTransition={vi.fn()}
      />
    );
    expect(screen.getAllByText('Draft').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('In Review').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Accepted').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Superseded').length).toBeGreaterThanOrEqual(1);
  });
});
