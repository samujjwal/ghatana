/**
 * Approval Detail
 *
 * @doc.type component
 * @doc.purpose Display rich approval context for policy-sensitive decisions
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Button, Card, CardContent, Chip, Typography } from '@ghatana/design-system';
import { AISourceChip } from '../ai/AISourceChip';

import type { ApprovalDecisionStatus, ApprovalRecord } from './ApprovalInbox';

/**
 * Structured output from the AI enrichment agent run, surfaced for explainability.
 * Mirrors `AiEnrichmentSuggestion` returned by the backend resolver.
 */
export interface EnrichmentSuggestion {
  normalizedTitle: string;
  acceptanceCriteria: string[];
  storyTrace: string;
  confidence: number;
  rationale: string;
  /** Which subsystem produced this enrichment: deterministic rule engine or probabilistic model. */
  source: 'RULE' | 'MODEL';
}

export interface PolicyDecisionSummary {
  id: string;
  status: 'ALLOWED' | 'REQUIRES_REVIEW' | 'BLOCKED';
  reason: string;
  evaluatedAt: string;
}

export interface ApprovalDetailProps {
  approval: ApprovalRecord;
  aiSummary?: string;
  confidence?: number;
  /** Full structured enrichment suggestion from the AI agent run */
  enrichmentSuggestion?: EnrichmentSuggestion;
  originalContent?: string;
  proposedContent?: string;
  policyDecisions?: PolicyDecisionSummary[];
  /** When false, action buttons are hidden regardless of approval status. Defaults to true for backwards compatibility. */
  isAuthorizedApprover?: boolean;
  onApprove?: (approvalId: string) => void;
  onReject?: (approvalId: string) => void;
  onRequestChanges?: (approvalId: string) => void;
}

const STATUS_STYLES: Record<ApprovalDecisionStatus, string> = {
  PENDING: 'bg-warning-bg text-warning-color',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-destructive-bg text-destructive',
  CHANGES_REQUESTED: 'bg-info-bg text-info-color',
  EXPIRED: 'bg-surface-muted text-fg',
};

const POLICY_STYLES: Record<PolicyDecisionSummary['status'], string> = {
  ALLOWED: 'bg-emerald-100 text-emerald-800',
  REQUIRES_REVIEW: 'bg-warning-bg text-warning-color',
  BLOCKED: 'bg-destructive-bg text-destructive',
};

export const ApprovalDetail: React.FC<ApprovalDetailProps> = ({
  approval,
  aiSummary,
  confidence,
  enrichmentSuggestion,
  originalContent,
  proposedContent,
  policyDecisions = [],
  isAuthorizedApprover = true,
  onApprove,
  onReject,
  onRequestChanges,
}) => {
  const isPending = approval.status === 'PENDING';
  const showActions = isPending && isAuthorizedApprover;

  return (
    <Card data-testid="approval-detail">
      <CardContent className="space-y-4 p-4">
        <Box className="flex items-center justify-between">
          <Typography className="text-lg font-semibold">Approval Detail</Typography>
          <Chip label={approval.status} size="sm" className={STATUS_STYLES[approval.status]} />
        </Box>

        <Box className="space-y-1">
          <Typography className="text-sm font-medium">Requested action</Typography>
          <Typography className="text-sm text-fg">{approval.requestedAction}</Typography>
        </Box>

        {aiSummary && (
          <Box className="space-y-1">
            <Typography className="text-sm font-medium">AI rationale</Typography>
            <Typography className="text-sm text-fg">{aiSummary}</Typography>
            {typeof confidence === 'number' && (
              <Typography className="text-xs text-fg-muted">Confidence: {Math.round(confidence * 100)}%</Typography>
            )}
          </Box>
        )}

        {enrichmentSuggestion && (
          <Box
            className="space-y-3 rounded-md border border-info-border bg-info-bg p-4"
            aria-label="AI enrichment suggestion"
            data-testid="enrichment-suggestion"
          >
            <Typography className="text-sm font-semibold text-info-color">AI Enrichment Details</Typography>

            {enrichmentSuggestion.normalizedTitle && (
              <Box className="space-y-0.5">
                <Typography className="text-xs font-medium text-info-color uppercase tracking-wide">Normalized Title</Typography>
                <Typography className="text-sm text-info-color">{enrichmentSuggestion.normalizedTitle}</Typography>
              </Box>
            )}

            {enrichmentSuggestion.acceptanceCriteria.length > 0 && (
              <Box className="space-y-1">
                <Typography className="text-xs font-medium text-info-color uppercase tracking-wide">Acceptance Criteria</Typography>
                <ul className="list-inside list-disc space-y-0.5">
                  {enrichmentSuggestion.acceptanceCriteria.map((criterion, idx) => (
                    <li key={idx} className="text-sm text-info-color">{criterion}</li>
                  ))}
                </ul>
              </Box>
            )}

            {enrichmentSuggestion.storyTrace && (
              <Box className="space-y-0.5">
                <Typography className="text-xs font-medium text-info-color uppercase tracking-wide">Story Trace</Typography>
                <Typography className="text-sm text-info-color">{enrichmentSuggestion.storyTrace}</Typography>
              </Box>
            )}

            <Box className="flex items-center gap-2 flex-wrap">
              <AISourceChip
                source={enrichmentSuggestion.source}
                confidence={enrichmentSuggestion.source === 'MODEL' ? enrichmentSuggestion.confidence : undefined}
                rationale={enrichmentSuggestion.rationale}
              />
              <Typography className="text-xs font-medium text-info-color uppercase tracking-wide">Confidence</Typography>
              <Box
                className={[
                  'rounded-full px-2 py-0.5 text-xs font-medium',
                  enrichmentSuggestion.confidence >= 0.8
                    ? 'bg-emerald-100 text-emerald-800'
                    : enrichmentSuggestion.confidence >= 0.6
                    ? 'bg-warning-bg text-warning-color'
                    : 'bg-destructive-bg text-destructive',
                ].join(' ')}
                aria-label={`Confidence: ${Math.round(enrichmentSuggestion.confidence * 100)}%`}
              >
                {Math.round(enrichmentSuggestion.confidence * 100)}%
              </Box>
            </Box>

            {enrichmentSuggestion.rationale && (
              <Box className="space-y-0.5">
                <Typography className="text-xs font-medium text-info-color uppercase tracking-wide">Rationale</Typography>
                <Typography className="text-sm text-info-color">{enrichmentSuggestion.rationale}</Typography>
              </Box>
            )}
          </Box>
        )}

        {(originalContent || proposedContent) && (
          <Box className="grid gap-3 md:grid-cols-2">
            <Box className="rounded border border-border p-3">
              <Typography className="mb-1 text-sm font-medium">Current</Typography>
              <Typography className="whitespace-pre-wrap text-sm text-fg">
                {originalContent ?? 'No existing content'}
              </Typography>
            </Box>
            <Box className="rounded border border-border p-3">
              <Typography className="mb-1 text-sm font-medium">Proposed</Typography>
              <Typography className="whitespace-pre-wrap text-sm text-fg">
                {proposedContent ?? 'No proposal'}
              </Typography>
            </Box>
          </Box>
        )}

        {policyDecisions.length > 0 && (
          <Box className="space-y-2">
            <Typography className="text-sm font-medium">Policy decisions</Typography>
            {policyDecisions.map((decision) => (
              <Box
                key={decision.id}
                className="flex items-center justify-between rounded border border-border px-3 py-2"
              >
                <Box>
                  <Typography className="text-sm text-fg">{decision.reason}</Typography>
                  <Typography className="text-xs text-fg-muted">{new Date(decision.evaluatedAt).toLocaleString()}</Typography>
                </Box>
                <Chip label={decision.status} size="sm" className={POLICY_STYLES[decision.status]} />
              </Box>
            ))}
          </Box>
        )}

        {showActions && (
          <Box className="flex flex-wrap gap-2">
            {onApprove && (
              <Button size="sm" variant="contained" onClick={() => onApprove(approval.id)}>
                Approve
              </Button>
            )}
            {onReject && (
              <Button size="sm" variant="outlined" color="error" onClick={() => onReject(approval.id)}>
                Reject
              </Button>
            )}
            {onRequestChanges && (
              <Button size="sm" variant="text" onClick={() => onRequestChanges(approval.id)}>
                Request changes
              </Button>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

export default ApprovalDetail;