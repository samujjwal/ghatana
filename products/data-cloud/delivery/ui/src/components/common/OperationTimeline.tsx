/**
 * OperationTimeline Component
 *
 * Domain-specific timeline for workflow executions, alert history, and audit
 * logs. Adapts raw `Timeline`/`CompactTimeline` from common into a typed
 * surface that accepts the standardized `OperationRecord` shape used across
 * WorkflowsPage, AlertsPage, and TrustCenter.
 *
 * @doc.type component
 * @doc.purpose Shared operation/audit-event timeline for data-cloud pages
 * @doc.layer shared
 * @doc.pattern Adapter Component
 *
 * @example
 * ```tsx
 * <OperationTimeline
 *   records={auditEvents.map(mapAuditEventToRecord)}
 *   compact
 *   emptyMessage="No events recorded"
 * />
 * ```
 */

import React from 'react';
import { Timeline, CompactTimeline, type TimelineEvent, type TimelineEventType } from './Timeline';
import { cn } from '../../lib/theme';

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/** Severity / outcome that maps to a timeline event type */
export type OperationOutcome =
  | 'success'
  | 'failure'
  | 'warning'
  | 'pending'
  | 'info'
  | 'skipped';

/** Canonical record shape consumed by OperationTimeline */
export interface OperationRecord {
  /** Stable unique identifier */
  id: string;
  /** Short human-readable action label (e.g. "Policy evaluated", "Alert acknowledged") */
  action: string;
  /** Optional longer description or context */
  detail?: string;
  /** ISO timestamp or Date */
  timestamp: string | Date;
  /** Operation outcome */
  outcome: OperationOutcome;
  /** User or system actor that triggered the operation */
  actor?: string;
  /** Optional key/value metadata displayed below the title */
  metadata?: Record<string, string | number>;
}

// ---------------------------------------------------------------------------
// Internal mapping
// ---------------------------------------------------------------------------

const OUTCOME_TO_EVENT_TYPE: Record<OperationOutcome, TimelineEventType> = {
  success: 'success',
  failure: 'error',
  warning: 'warning',
  pending: 'pending',
  info: 'info',
  skipped: 'info',
};

function mapRecordToEvent(record: OperationRecord): TimelineEvent {
  return {
    id: record.id,
    type: OUTCOME_TO_EVENT_TYPE[record.outcome],
    title: record.action,
    description: record.detail,
    timestamp: record.timestamp,
    user: record.actor,
    metadata: record.metadata,
  };
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

export interface OperationTimelineProps {
  /** Operation records to display */
  records: OperationRecord[];
  /** Render compact variant (single-line per event) */
  compact?: boolean;
  /** Message shown when records list is empty */
  emptyMessage?: string;
  /** Optional aria-label for the timeline region */
  ariaLabel?: string;
  /** Optional className applied to the outermost element */
  className?: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * OperationTimeline
 *
 * Converts `OperationRecord[]` → `TimelineEvent[]` and delegates rendering to
 * `Timeline` (full) or `CompactTimeline` (compact). Both variants are
 * accessible: events are rendered as an ordered list with descriptive labels.
 */
export const OperationTimeline = React.memo(function OperationTimeline({
  records,
  compact = false,
  emptyMessage = 'No operations recorded.',
  ariaLabel = 'Operation timeline',
  className,
}: OperationTimelineProps): React.ReactElement {
  const events: TimelineEvent[] = records.map(mapRecordToEvent);

  if (events.length === 0) {
    return (
      <div
        role="status"
        aria-label={ariaLabel}
        className={cn('py-6 text-center text-sm text-gray-500 dark:text-gray-400', className)}
      >
        {emptyMessage}
      </div>
    );
  }

  return (
    <div role="region" aria-label={ariaLabel} className={cn(className)}>
      {compact ? (
        <CompactTimeline events={events} />
      ) : (
        <Timeline events={events} />
      )}
    </div>
  );
});

OperationTimeline.displayName = 'OperationTimeline';

export default OperationTimeline;
