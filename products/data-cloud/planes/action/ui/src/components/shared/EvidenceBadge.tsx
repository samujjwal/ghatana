/**
 * EvidenceBadge — trust claim with optional evidence and audit linkage.
 *
 * @doc.type component
 * @doc.purpose Surface trust claims with direct links to evidence and audit entries
 * @doc.layer frontend
 * @doc.pattern Compliance / Evidence
 */
import React from 'react';
import { Shield, ExternalLink, FileCheck } from 'lucide-react';

export type EvidenceStatus = 'verified' | 'pending' | 'unverified' | 'deprecated';

interface EvidenceBadgeProps {
  label: string;
  status: EvidenceStatus;
  evidenceUrl?: string;
  auditEntryId?: string;
  generatedAt?: string;
  className?: string;
}

const STATUS_CONFIG: Record<EvidenceStatus, { icon: React.ReactNode; bg: string; text: string; border: string }> = {
  verified: {
    icon: <FileCheck className="h-3.5 w-3.5 text-green-600" aria-hidden />,
    bg: 'bg-green-50 dark:bg-green-950/30',
    text: 'text-green-700 dark:text-green-300',
    border: 'border-green-200 dark:border-green-900',
  },
  pending: {
    icon: <Shield className="h-3.5 w-3.5 text-amber-600" aria-hidden />,
    bg: 'bg-amber-50 dark:bg-amber-950/30',
    text: 'text-amber-700 dark:text-amber-300',
    border: 'border-amber-200 dark:border-amber-900',
  },
  unverified: {
    icon: <Shield className="h-3.5 w-3.5 text-red-600" aria-hidden />,
    bg: 'bg-red-50 dark:bg-red-950/30',
    text: 'text-red-700 dark:text-red-300',
    border: 'border-red-200 dark:border-red-900',
  },
  deprecated: {
    icon: <Shield className="h-3.5 w-3.5 text-gray-500" aria-hidden />,
    bg: 'bg-gray-50 dark:bg-gray-900/30',
    text: 'text-gray-600 dark:text-gray-400',
    border: 'border-gray-200 dark:border-gray-800',
  },
};

export function EvidenceBadge({
  label,
  status,
  evidenceUrl,
  auditEntryId,
  generatedAt,
  className = '',
}: EvidenceBadgeProps): React.ReactElement {
  const config = STATUS_CONFIG[status];
  const hasLinks = evidenceUrl || auditEntryId;

  return (
    <div
      className={[
        'inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-medium',
        config.bg,
        config.text,
        config.border,
        className,
      ].join(' ')}
      role="status"
      aria-label={`${label}: ${status}`}
    >
      {config.icon}
      <span>{label}</span>
      {generatedAt && !Number.isNaN(new Date(generatedAt).getTime()) && (
        <span className="text-[10px] opacity-75 ml-0.5">
          {new Date(generatedAt).toLocaleDateString()}
        </span>
      )}
      {hasLinks && (
        <div className="ml-1 flex items-center gap-1 border-l pl-1.5 opacity-80 dark:border-white/10">
          {evidenceUrl && (
            <a
              href={evidenceUrl}
              target="_blank"
              rel="noreferrer"
              className="hover:opacity-100"
              title="View evidence"
              aria-label="View evidence"
            >
              <ExternalLink className="h-3 w-3" />
            </a>
          )}
          {auditEntryId && (
            <a
              href={`/govern?tab=audit&entry=${auditEntryId}`}
              className="hover:opacity-100"
              title="View audit entry"
              aria-label="View audit entry"
            >
              <FileCheck className="h-3 w-3" />
            </a>
          )}
        </div>
      )}
    </div>
  );
}
