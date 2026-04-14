/**
 * @group unit
 * @tier U, I-br
 *
 * Tests for @ghatana/patterns — AIReviewWorkflow component.
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import type { AIVisibilityContract } from '@ghatana/platform-events';

import { AIReviewWorkflow } from '../workflows/ai-review-workflow';

// ─── Fixture factory ─────────────────────────────────────────────────────────

function makeContract(
  overrides: Partial<AIVisibilityContract> = {},
): AIVisibilityContract {
  return {
    operationState: 'running',
    operationLabel: 'Applying layout suggestion',
    suggestedChanges: [],
    appliedChanges: [],
    pendingChanges: [],
    confidenceBand: { low: 0.72, high: 0.91 },
    rationale: 'Token spacing inconsistency detected',
    evidence: ['ds-token-audit-2026'],
    approvalState: 'PENDING',
    reviewRequired: true,
    rollbackAvailable: false,
    overrideAvailable: true,
    autonomyLevel: 'SUPERVISED',
    correlationId: 'corr-abc-123' as import('@ghatana/platform-events').CorrelationId,
    triggeredBy: 'user',
    ...overrides,
  } as AIVisibilityContract;
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('AIReviewWorkflow', () => {
  const noop = () => undefined;

  describe('rendering', () => {
    it('renders without crashing', () => {
      expect(() =>
        render(
          <AIReviewWorkflow
            visibilityContract={makeContract()}
            onApprove={noop}
            onReject={noop}
            onModify={noop}
          >
            <span>child</span>
          </AIReviewWorkflow>,
        ),
      ).not.toThrow();
    });

    it('displays the operationLabel from the contract', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract({ operationLabel: 'Refactoring imports' })}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      expect(screen.getByText('Refactoring imports')).toBeInTheDocument();
    });

    it('displays the confidence band values', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract({ confidenceBand: { low: 0.6, high: 0.85 } })}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      // Both the low (0.6) and high (0.85) values should appear somewhere in the header
      const text = document.body.textContent ?? '';
      expect(text).toContain('0.6');
      expect(text).toContain('0.85');
    });

    it('renders children inside the workflow', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          <div data-testid="workflow-content">Review items here</div>
        </AIReviewWorkflow>,
      );
      expect(screen.getByTestId('workflow-content')).toBeInTheDocument();
    });

    it('renders Approve, Reject, and Modify action buttons', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      expect(
        screen.getByRole('button', { name: /approve/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /reject/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /modify/i }),
      ).toBeInTheDocument();
    });
  });

  describe('action callbacks', () => {
    it('calls onApprove when Approve is clicked', async () => {
      const user = userEvent.setup();
      const onApprove = vi.fn();
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={onApprove}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      await user.click(screen.getByRole('button', { name: /approve/i }));
      expect(onApprove).toHaveBeenCalledOnce();
    });

    it('calls onReject when Reject is clicked', async () => {
      const user = userEvent.setup();
      const onReject = vi.fn();
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={noop}
          onReject={onReject}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      await user.click(screen.getByRole('button', { name: /reject/i }));
      expect(onReject).toHaveBeenCalledOnce();
    });

    it('calls onModify when Modify is clicked', async () => {
      const user = userEvent.setup();
      const onModify = vi.fn();
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={noop}
          onReject={noop}
          onModify={onModify}
        >
          content
        </AIReviewWorkflow>,
      );
      await user.click(screen.getByRole('button', { name: /modify/i }));
      expect(onModify).toHaveBeenCalledOnce();
    });

    it('does not call onReject when Approve is clicked', async () => {
      const user = userEvent.setup();
      const onReject = vi.fn();
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract()}
          onApprove={noop}
          onReject={onReject}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      await user.click(screen.getByRole('button', { name: /approve/i }));
      expect(onReject).not.toHaveBeenCalled();
    });
  });

  describe('different contract states', () => {
    it('renders correctly in completed state', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract({ operationState: 'completed' })}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      // Should still render without error
      expect(screen.getByRole('button', { name: /approve/i })).toBeInTheDocument();
    });

    it('renders correctly when reviewRequired is false', () => {
      render(
        <AIReviewWorkflow
          visibilityContract={makeContract({
            reviewRequired: false,
            approvalState: 'APPROVED',
          })}
          onApprove={noop}
          onReject={noop}
          onModify={noop}
        >
          content
        </AIReviewWorkflow>,
      );
      // Component should render without crashing even in auto-approved state
      expect(document.body).not.toBeEmptyDOMElement();
    });
  });
});
