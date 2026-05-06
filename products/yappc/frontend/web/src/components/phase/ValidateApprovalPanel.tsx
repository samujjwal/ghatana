/**
 * Validate Approval Panel
 *
 * Phase-native panel for the Validate phase. Provides a durable approval
 * workflow with reviewer assignment, decision recording, and audit trail.
 * Replaces generic route embedding with a purpose-built approval surface.
 *
 * @doc.type component
 * @doc.purpose Durable approval workflow panel for the Validate phase
 * @doc.layer product
 * @doc.pattern Phase Panel
 */

import React, { useState, useCallback } from 'react';
import { Button, Card, CardContent } from '@ghatana/design-system';

export type ApprovalDecision = 'approved' | 'changes-requested' | 'rejected' | 'pending';

export interface ApprovalReviewer {
  readonly id: string;
  readonly name: string;
  readonly role: string;
  readonly avatarUrl?: string;
  readonly decision?: ApprovalDecision;
  readonly decidedAt?: string;
  readonly comment?: string;
}

export interface ApprovalGate {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly required: boolean;
  readonly passed: boolean;
  readonly failureReason?: string;
}

export interface ValidateApprovalPanelProps {
  /** Unique artifact ID being approved */
  readonly artifactId: string;
  /** Human-readable artifact name */
  readonly artifactName: string;
  /** Current approval status */
  readonly currentDecision: ApprovalDecision;
  /** Reviewers assigned to this approval */
  readonly reviewers: readonly ApprovalReviewer[];
  /** Automated gates that must pass before manual approval */
  readonly gates: readonly ApprovalGate[];
  /** ISO timestamp when approval was last updated */
  readonly lastUpdatedAt?: string;
  /** Called when the current user approves */
  readonly onApprove: (comment: string) => void;
  /** Called when the current user requests changes */
  readonly onRequestChanges: (comment: string) => void;
  /** Called when the current user rejects */
  readonly onReject: (comment: string) => void;
  /** Whether the current user can make a decision */
  readonly canDecide: boolean;
  /** Reason the current user cannot decide (shown when canDecide=false) */
  readonly cannotDecideReason?: string;
  /** Custom className */
  readonly className?: string;
}

const DECISION_BADGE: Record<ApprovalDecision, { label: string; className: string }> = {
  approved: { label: 'Approved', className: 'bg-success-bg border-success-border text-success-color' },
  'changes-requested': { label: 'Changes Requested', className: 'bg-warning-bg border-warning-border text-warning-color' },
  rejected: { label: 'Rejected', className: 'bg-destructive-bg border-destructive-border text-destructive' },
  pending: { label: 'Pending Review', className: 'bg-info-bg border-info-border text-info-color' },
};

const REVIEWER_DECISION_LABEL: Record<ApprovalDecision, string> = {
  approved: 'Approved',
  'changes-requested': 'Requested changes',
  rejected: 'Rejected',
  pending: 'Awaiting decision',
};

/**
 * Validate Approval Panel
 *
 * Provides a durable approval record including:
 * - Automated gate status (all gates must pass before approval)
 * - Per-reviewer decisions and comments
 * - Decision form for the current reviewer
 * - Full decision history
 */
export const ValidateApprovalPanel: React.FC<ValidateApprovalPanelProps> = ({
  artifactId,
  artifactName,
  currentDecision,
  reviewers,
  gates,
  lastUpdatedAt,
  onApprove,
  onRequestChanges,
  onReject,
  canDecide,
  cannotDecideReason,
  className = '',
}) => {
  const [comment, setComment] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const allGatesPassed = gates.every((g) => !g.required || g.passed);
  const failedGates = gates.filter((g) => g.required && !g.passed);

  const handleDecision = useCallback(
    async (action: 'approve' | 'request-changes' | 'reject') => {
      setIsSubmitting(true);
      try {
        if (action === 'approve') {
          onApprove(comment);
        } else if (action === 'request-changes') {
          onRequestChanges(comment);
        } else {
          onReject(comment);
        }
        setComment('');
      } finally {
        setIsSubmitting(false);
      }
    },
    [comment, onApprove, onRequestChanges, onReject],
  );

  const badge = DECISION_BADGE[currentDecision];

  return (
    <section
      className={`validate-approval-panel space-y-6 ${className}`}
      aria-label="Approval workflow"
      data-testid="validate-approval-panel"
      data-artifact-id={artifactId}
    >
      {/* Status Header */}
      <Card variant="outlined">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h3 className="text-base font-semibold text-fg">
                {artifactName}
              </h3>
              {lastUpdatedAt && (
                <p className="text-xs text-fg-muted mt-1">
                  Last updated:{' '}
                  <time dateTime={lastUpdatedAt}>
                    {new Date(lastUpdatedAt).toLocaleString()}
                  </time>
                </p>
              )}
            </div>
            <span
              className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${badge.className}`}
              aria-label={`Approval status: ${badge.label}`}
            >
              {badge.label}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Automated Gates */}
      {gates.length > 0 && (
        <section aria-label="Automated gates">
          <h4 className="text-sm font-medium text-fg mb-3">
            Automated gates ({gates.filter((g) => g.passed).length}/{gates.length} passed)
          </h4>
          <div className="space-y-2">
            {gates.map((gate) => (
              <div
                key={gate.id}
                className={`flex items-start gap-3 rounded-lg border p-3 ${
                  gate.passed
                    ? 'bg-success-bg border-success-border'
                    : gate.required
                    ? 'bg-destructive-bg border-destructive-border'
                    : 'bg-warning-bg border-warning-border'
                }`}
              >
                <span
                  className="mt-0.5 text-base"
                  aria-hidden="true"
                >
                  {gate.passed ? '✓' : gate.required ? '✗' : '⚠'}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-fg">
                    {gate.name}
                    {gate.required && !gate.passed && (
                      <span className="ml-2 text-xs font-normal text-destructive">
                        (required)
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-fg-muted mt-0.5">
                    {gate.passed ? gate.description : (gate.failureReason ?? gate.description)}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Reviewer Decisions */}
      {reviewers.length > 0 && (
        <section aria-label="Reviewer decisions">
          <h4 className="text-sm font-medium text-fg mb-3">
            Reviewers ({reviewers.filter((r) => r.decision && r.decision !== 'pending').length}/{reviewers.length} responded)
          </h4>
          <div className="space-y-3">
            {reviewers.map((reviewer) => (
              <Card key={reviewer.id} variant="outlined">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-fg">
                        {reviewer.name}
                        <span className="ml-2 text-xs text-fg-muted font-normal">
                          {reviewer.role}
                        </span>
                      </p>
                      {reviewer.comment && (
                        <p className="text-sm text-fg-muted mt-1 italic">
                          &ldquo;{reviewer.comment}&rdquo;
                        </p>
                      )}
                      {reviewer.decidedAt && (
                        <p className="text-xs text-fg-muted mt-1">
                          <time dateTime={reviewer.decidedAt}>
                            {new Date(reviewer.decidedAt).toLocaleString()}
                          </time>
                        </p>
                      )}
                    </div>
                    <span className="text-xs font-medium text-fg-muted whitespace-nowrap">
                      {REVIEWER_DECISION_LABEL[reviewer.decision ?? 'pending']}
                    </span>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* Decision Form */}
      {canDecide ? (
        <section aria-label="Submit your decision">
          <h4 className="text-sm font-medium text-fg mb-3">Your decision</h4>
          {!allGatesPassed && (
            <div className="mb-4 rounded-lg bg-warning-bg border border-warning-border p-3">
              <p className="text-sm text-warning-color">
                {failedGates.length} required gate{failedGates.length !== 1 ? 's' : ''} must pass before approving.
              </p>
            </div>
          )}
          <Card variant="outlined">
            <CardContent className="p-4 space-y-4">
              <div>
                <label
                  htmlFor="approval-comment"
                  className="block text-sm font-medium text-fg mb-1"
                >
                  Comment (optional)
                </label>
                <textarea
                  id="approval-comment"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={3}
                  placeholder="Add context for your decision…"
                  className="w-full rounded-md border border-border bg-surface text-fg text-sm p-2 resize-none focus:outline-none focus:ring-2 focus:ring-ring"
                  aria-label="Approval comment"
                />
              </div>
              <div className="flex flex-wrap gap-2" role="group" aria-label="Decision buttons">
                <Button
                  variant="solid"
                  size="sm"
                  onClick={() => handleDecision('approve')}
                  disabled={isSubmitting || !allGatesPassed}
                  aria-label="Approve this artifact"
                >
                  Approve
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleDecision('request-changes')}
                  disabled={isSubmitting}
                  aria-label="Request changes to this artifact"
                >
                  Request changes
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleDecision('reject')}
                  disabled={isSubmitting}
                  aria-label="Reject this artifact"
                >
                  Reject
                </Button>
              </div>
            </CardContent>
          </Card>
        </section>
      ) : (
        cannotDecideReason && (
          <div
            className="rounded-lg border border-border bg-surface-muted p-4"
            role="status"
            aria-label="Decision not available"
          >
            <p className="text-sm text-fg-muted">{cannotDecideReason}</p>
          </div>
        )
      )}
    </section>
  );
};

export default ValidateApprovalPanel;
