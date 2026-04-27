import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ApprovalInbox, type ApprovalRecord } from '../ApprovalInbox';

function makeApproval(overrides: Partial<ApprovalRecord> = {}): ApprovalRecord {
  return {
    id: 'approval-1',
    projectId: 'project-1',
    requirementId: 'req-1',
    requestedAction: 'REQUIREMENT_APPROVAL',
    status: 'PENDING',
    requesterId: 'user-requester',
    createdAt: '2026-04-26T10:00:00.000Z',
    ...overrides,
  };
}

describe('ApprovalInbox', () => {
  it('renders empty state', () => {
    render(<ApprovalInbox approvals={[]} />);
    expect(screen.getByText('No approval requests yet.')).toBeDefined();
  });

  it('renders approval records and pending counter', () => {
    render(
      <ApprovalInbox
        approvals={[
          makeApproval(),
          makeApproval({ id: 'approval-2', status: 'APPROVED' }),
        ]}
      />
    );

    expect(screen.getByText('Approval Inbox')).toBeDefined();
    expect(screen.getByText('1 pending')).toBeDefined();
    expect(screen.getAllByText('REQUIREMENT_APPROVAL').length).toBeGreaterThan(0);
  });

  it('triggers decision callbacks', () => {
    const onApprove = vi.fn();
    const onReject = vi.fn();
    const onRequestChanges = vi.fn();

    render(
      <ApprovalInbox
        approvals={[makeApproval()]}
        onApprove={onApprove}
        onReject={onReject}
        onRequestChanges={onRequestChanges}
      />
    );

    fireEvent.click(screen.getByText('Approve'));
    fireEvent.click(screen.getByText('Reject'));
    fireEvent.click(screen.getByText('Request changes'));

    expect(onApprove).toHaveBeenCalledWith('approval-1');
    expect(onReject).toHaveBeenCalledWith('approval-1');
    expect(onRequestChanges).toHaveBeenCalledWith('approval-1');
  });
});
