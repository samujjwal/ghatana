/**
 * TrustSignalGroup Component
 *
 * Renders a horizontal group of policy-derived trust indicators. Accepts an
 * array of `TrustSignal` descriptors — each with a status and optional label —
 * and delegates visual rendering to `TrustBadge` and `AccessLevelIndicator`
 * from the governance layer.
 *
 * Usage replaces ad-hoc inline badge placement in SqlWorkspacePage and
 * DataExplorer with a single data-driven component. The signals are derived
 * from caller-supplied query/policy data; this component is purely
 * presentational.
 *
 * @doc.type component
 * @doc.purpose Grouped policy-derived trust indicators for query and collection flows
 * @doc.layer shared
 * @doc.pattern Governance Component
 *
 * @example
 * ```tsx
 * <TrustSignalGroup
 *   accessLevel="tenant"
 *   signals={[
 *     { status: 'warning', label: 'Review required' },
 *     { status: 'compliant', label: 'GDPR' },
 *   ]}
 * />
 * ```
 */

import React from 'react';
import { Shield } from 'lucide-react';
import {
  TrustBadge,
  AccessLevelIndicator,
  type TrustStatus,
  type AccessLevel,
} from '../governance/TrustSignal';
import { cn } from '../../lib/theme';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** A single trust signal to display */
export interface TrustSignalDescriptor {
  /** Trust status — controls badge color and icon */
  status: TrustStatus;
  /** Human-readable label shown next to the status indicator */
  label: string;
  /** Optional test id suffix for targeted querying in tests */
  testId?: string;
}

export interface TrustSignalGroupProps {
  /**
   * The access scope governing this resource or query. When provided, renders
   * an `AccessLevelIndicator` as the first signal.
   */
  accessLevel?: AccessLevel;
  /**
   * Policy-derived trust signals. These are computed by the caller from real
   * query/policy/governance data — the group renders them without modification.
   */
  signals?: TrustSignalDescriptor[];
  /**
   * Label for the leading access-context prefix text (defaults to "Data access:").
   * Set to null to suppress the prefix entirely.
   */
  accessLabel?: string | null;
  /** Optional test id applied to the container */
  'data-testid'?: string;
  /** Optional className for the container */
  className?: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * TrustSignalGroup
 *
 * Renders access level + optional trust badges in a consistent horizontal
 * layout used across SqlWorkspacePage and DataExplorer. All signals are
 * policy-derived — callers are responsible for computing which signals to
 * show based on query plan, execution context, or governance data.
 */
export const TrustSignalGroup = React.memo(function TrustSignalGroup({
  accessLevel,
  signals = [],
  accessLabel = 'Data access:',
  'data-testid': testId,
  className,
}: TrustSignalGroupProps): React.ReactElement | null {
  const hasContent = accessLevel !== undefined || signals.length > 0;

  if (!hasContent) {
    return null;
  }

  return (
    <div
      className={cn(
        'rounded-lg border border-gray-200 dark:border-gray-700',
        'bg-gray-50 dark:bg-gray-900/30 p-3',
        'flex items-center gap-3 flex-wrap',
        className
      )}
      data-testid={testId}
      aria-label="Trust signals"
    >
      {accessLevel !== undefined && (
        <div className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-400">
          <Shield className="h-4 w-4 text-blue-500" aria-hidden="true" />
          {accessLabel !== null && (
            <span className="font-medium">{accessLabel}</span>
          )}
          <AccessLevelIndicator level={accessLevel} />
        </div>
      )}

      {signals.map((signal, index) => (
        <TrustBadge
          key={signal.testId ?? `${signal.status}-${index}`}
          status={signal.status}
          label={signal.label}
        />
      ))}
    </div>
  );
});

TrustSignalGroup.displayName = 'TrustSignalGroup';

export default TrustSignalGroup;
