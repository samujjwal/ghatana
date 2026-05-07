/**
 * Phase Governance Trace Component
 *
 * Displays governance and provenance information for lifecycle items.
 * Shows source, timestamp, actor/system, confidence, and review state.
 *
 * @doc.type component
 * @doc.purpose Governance/provenance display for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export interface GovernanceRecord {
  id: string;
  artifactId: string;
  action: string;
  actor: string;
  system?: string;
  timestamp: string;
  confidence?: number;
  reviewState?: 'pending' | 'approved' | 'rejected' | 'auto_applied';
  source: 'backed' | 'derived' | 'suggested' | 'preview' | 'unavailable';
  metadata?: Record<string, string | number | boolean | null | undefined>;
}

export interface PhaseGovernanceTraceProps {
  /** Governance records to display */
  records: GovernanceRecord[];
  /** Custom className */
  className?: string;
}

/**
 * Source badge colors
 */
const SOURCE_COLORS: Record<GovernanceRecord['source'], string> = {
  backed: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
  derived: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
  suggested: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
  preview: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
  unavailable: 'bg-surface-muted text-fg dark:bg-surface/30 dark:text-fg-muted',
};

/**
 * Review state colors
 */
type GovernanceReviewState = NonNullable<GovernanceRecord['reviewState']>;

const REVIEW_STATE_COLORS: Record<GovernanceReviewState, string> = {
  pending: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
  approved: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
  rejected: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
  auto_applied: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
};

/**
 * Phase Governance Trace Component
 *
 * Displays governance records with:
 * - Source and review state badges
 * - Actor, system, and action information
 * - Timestamp and optional confidence
 * - Collapsible metadata
 * - Empty state when no records
 */
export const PhaseGovernanceTrace: React.FC<PhaseGovernanceTraceProps> = ({
  records,
  className = '',
}) => {
  if (records.length === 0) {
    return (
      <div className={`phase-governance-trace ${className}`}>
        <p className="text-sm text-fg-muted dark:text-fg-muted text-center py-4">
          No governance records available
        </p>
      </div>
    );
  }

  return (
    <div className={`phase-governance-trace ${className}`}>
      <div className="space-y-3">
        {records.map((record) => (
          <div
            key={record.id}
            className="bg-white dark:bg-surface border border-border dark:border-border rounded-lg p-4"
          >
            <div className="flex items-start justify-between gap-3 mb-2">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${SOURCE_COLORS[record.source]}`}>
                    {record.source}
                  </span>
                  {record.reviewState && (
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${REVIEW_STATE_COLORS[record.reviewState]}`}>
                      {record.reviewState.replace('_', ' ')}
                    </span>
                  )}
                </div>
                <h4 className="font-medium text-fg dark:text-fg-muted mb-1">
                  {record.action}
                </h4>
                <div className="flex items-center gap-3 text-xs text-fg-muted dark:text-fg-muted">
                  <span>Actor: {record.actor}</span>
                  {record.system && <span>System: {record.system}</span>}
                  <span>{new Date(record.timestamp).toLocaleString()}</span>
                </div>
              </div>
              {record.confidence !== undefined && (
                <div className="flex-shrink-0 text-right">
                  <div className="text-xs text-fg-muted dark:text-fg-muted mb-1">
                    Confidence
                  </div>
                  <div className="text-sm font-medium text-fg dark:text-fg-muted">
                    {Math.round(record.confidence * 100)}%
                  </div>
                </div>
              )}
            </div>
            {record.metadata && Object.keys(record.metadata).length > 0 && (
              <details className="mt-3 pt-3 border-t border-border dark:border-border">
                <summary className="text-xs text-fg-muted dark:text-fg-muted cursor-pointer list-none">
                  Metadata
                </summary>
                <div className="mt-2 text-xs font-mono text-fg-muted dark:text-fg-muted bg-surface-muted dark:bg-surface/50 p-2 rounded">
                  <pre>{JSON.stringify(record.metadata, null, 2)}</pre>
                </div>
              </details>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default PhaseGovernanceTrace;
