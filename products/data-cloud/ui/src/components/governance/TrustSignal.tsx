/**
 * Trust Signal Components
 *
 * Visual indicators for trust, privacy, and compliance properties
 * across collection, pipeline, and query flows.
 *
 * @doc.type component
 * @doc.purpose Surface trust properties in data flows
 * @doc.layer shared
 * @doc.pattern Governance Component
 * @example
 * ```tsx
 * <SensitivityBadge level="pii" />
 * <TrustBadge status="compliant" label="GDPR" />
 * <AccessLevelIndicator level="restricted" />
 * ```
 */

import React from 'react';
import {
  Shield,
  ShieldCheck,
  ShieldAlert,
  Lock,
  Eye,
  Users,
  Globe,
  AlertTriangle,
  CheckCircle2,
} from 'lucide-react';
import { cn } from '../../lib/theme';

// ---------------------------------------------------------------------------
// Sensitivity Badge
// ---------------------------------------------------------------------------

export type SensitivityLevel = 'public' | 'internal' | 'confidential' | 'pii' | 'restricted';

interface SensitivityBadgeProps {
  /** Sensitivity level value. Accepts undefined gracefully (DC-UX-014). */
  level: SensitivityLevel | undefined | null | string;
  showLabel?: boolean;
  className?: string;
}

const sensitivityConfig: Record<SensitivityLevel, {
  label: string;
  icon: React.ReactNode;
  badgeClass: string;
}> = {
  public: {
    label: 'Public',
    icon: <Globe className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-300 border-green-200 dark:border-green-800',
  },
  internal: {
    label: 'Internal',
    icon: <Users className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 border-blue-200 dark:border-blue-800',
  },
  confidential: {
    label: 'Confidential',
    icon: <Lock className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 border-amber-200 dark:border-amber-800',
  },
  pii: {
    label: 'PII',
    icon: <Eye className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-300 border-red-200 dark:border-red-800',
  },
  restricted: {
    label: 'Restricted',
    icon: <Lock className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-purple-50 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300 border-purple-200 dark:border-purple-800',
  },
};

function isSensitivityLevel(level: string): level is SensitivityLevel {
  return Object.prototype.hasOwnProperty.call(sensitivityConfig, level);
}

export const SensitivityBadge = React.memo(function SensitivityBadge({
  level,
  showLabel = true,
  className,
}: SensitivityBadgeProps) {
  // DC-UX-014: Defensive fallback for undefined/invalid level values.
  // CollectionForm may pass the raw form value before it is set, which is undefined.
  const resolvedLevel = typeof level === 'string' && isSensitivityLevel(level)
    ? level
    : null;
  const config = resolvedLevel
    ? sensitivityConfig[resolvedLevel]
    : sensitivityConfig.internal;

  const displayLabel = config.label + (resolvedLevel ? '' : ' (unknown)');

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border',
        config.badgeClass,
        className
      )}
      title={`Sensitivity: ${displayLabel}`}
    >
      {config.icon}
      {showLabel && displayLabel}
    </span>
  );
});

SensitivityBadge.displayName = 'SensitivityBadge';

// ---------------------------------------------------------------------------
// Trust / Compliance Badge
// ---------------------------------------------------------------------------

export type TrustStatus = 'compliant' | 'warning' | 'non-compliant' | 'pending-review' | 'exempt';

interface TrustBadgeProps {
  status: TrustStatus;
  label: string;
  className?: string;
}

const trustConfig: Record<TrustStatus, {
  icon: React.ReactNode;
  badgeClass: string;
}> = {
  compliant: {
    icon: <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-300 border-green-200 dark:border-green-800',
  },
  warning: {
    icon: <ShieldAlert className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 border-amber-200 dark:border-amber-800',
  },
  'non-compliant': {
    icon: <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-300 border-red-200 dark:border-red-800',
  },
  'pending-review': {
    icon: <Shield className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300 border-blue-200 dark:border-blue-800',
  },
  exempt: {
    icon: <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />,
    badgeClass: 'bg-gray-50 text-gray-600 dark:bg-gray-800 dark:text-gray-400 border-gray-200 dark:border-gray-700',
  },
};

export const TrustBadge = React.memo(function TrustBadge({
  status,
  label,
  className,
}: TrustBadgeProps) {
  const config = trustConfig[status];

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium border',
        config.badgeClass,
        className
      )}
      title={`Trust status: ${label}`}
    >
      {config.icon}
      {label}
    </span>
  );
});

TrustBadge.displayName = 'TrustBadge';

// ---------------------------------------------------------------------------
// Access Level Indicator
// ---------------------------------------------------------------------------

export type AccessLevel = 'public' | 'tenant' | 'team' | 'user' | 'admin-only';

interface AccessLevelIndicatorProps {
  level: AccessLevel;
  showLabel?: boolean;
  className?: string;
}

const accessConfig: Record<AccessLevel, {
  label: string;
  dotClass: string;
  textClass: string;
}> = {
  public: {
    label: 'Public access',
    dotClass: 'bg-green-500',
    textClass: 'text-green-700 dark:text-green-300',
  },
  tenant: {
    label: 'Tenant-scoped',
    dotClass: 'bg-blue-500',
    textClass: 'text-blue-700 dark:text-blue-300',
  },
  team: {
    label: 'Team access',
    dotClass: 'bg-amber-500',
    textClass: 'text-amber-700 dark:text-amber-300',
  },
  user: {
    label: 'User-private',
    dotClass: 'bg-purple-500',
    textClass: 'text-purple-700 dark:text-purple-300',
  },
  'admin-only': {
    label: 'Admin only',
    dotClass: 'bg-red-500',
    textClass: 'text-red-700 dark:text-red-300',
  },
};

export const AccessLevelIndicator = React.memo(function AccessLevelIndicator({
  level,
  showLabel = true,
  className,
}: AccessLevelIndicatorProps) {
  const config = accessConfig[level];

  return (
    <span
      className={cn('inline-flex items-center gap-1.5', className)}
      title={config.label}
    >
      <span className={cn('h-2 w-2 rounded-full', config.dotClass)} aria-hidden="true" />
      {showLabel && (
        <span className={cn('text-xs font-medium', config.textClass)}>
          {config.label}
        </span>
      )}
    </span>
  );
});

AccessLevelIndicator.displayName = 'AccessLevelIndicator';

export default TrustBadge;
