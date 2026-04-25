/**
 * GovernanceEvidenceDrilldown — evidence detail panel for compliance claims.
 *
 * @doc.type component
 * @doc.purpose Drill into governance evidence (audit, policy, SOC2 control) details
 * @doc.layer frontend
 * @doc.pattern Compliance / Evidence
 */
import React, { useState } from 'react';
import { Shield, FileText, ExternalLink, ChevronRight, ChevronDown } from 'lucide-react';

export type EvidenceCategory = 'audit' | 'policy' | 'control' | 'review';

export interface EvidenceItem {
  id: string;
  category: EvidenceCategory;
  title: string;
  description: string;
  status: 'pass' | 'fail' | 'partial' | 'pending';
  generatedAt: string;
  evidenceUrl?: string;
  auditEntryId?: string;
  relatedPolicyId?: string;
  relatedPipelineId?: string;
  details?: string;
}

interface GovernanceEvidenceDrilldownProps {
  items: EvidenceItem[];
  className?: string;
}

const STATUS_ICON: Record<EvidenceItem['status'], { color: string; label: string; bg: string }> = {
  pass: { color: 'text-green-700 dark:text-green-300', label: 'Pass', bg: 'bg-green-50 dark:bg-green-950/30' },
  fail: { color: 'text-red-700 dark:text-red-300', label: 'Fail', bg: 'bg-red-50 dark:bg-red-950/30' },
  partial: { color: 'text-amber-700 dark:text-amber-300', label: 'Partial', bg: 'bg-amber-50 dark:bg-amber-950/30' },
  pending: { color: 'text-gray-700 dark:text-gray-300', label: 'Pending', bg: 'bg-gray-50 dark:bg-gray-900/30' },
};

const CATEGORY_LABEL: Record<EvidenceCategory, string> = {
  audit: 'Audit',
  policy: 'Policy',
  control: 'Control',
  review: 'Review',
};

export function GovernanceEvidenceDrilldown({
  items,
  className = '',
}: GovernanceEvidenceDrilldownProps): React.ReactElement {
  const [expanded, setExpanded] = useState<string | null>(null);

  if (items.length === 0) {
    return (
      <div className={`text-center text-xs text-gray-500 dark:text-gray-400 py-6 ${className}`}>
        No evidence items to display.
      </div>
    );
  }

  return (
    <div className={['space-y-2', className].join(' ')}>
      {items.map((item) => {
        const isExpanded = expanded === item.id;
        const status = STATUS_ICON[item.status];
        return (
          <div
            key={item.id}
            className="rounded-lg border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950 overflow-hidden"
          >
            <button
              type="button"
              onClick={() => setExpanded(isExpanded ? null : item.id)}
              className="w-full px-4 py-3 flex items-start gap-3 text-left hover:bg-gray-50 dark:hover:bg-gray-900/50 transition-colors"
              aria-expanded={isExpanded}
            >
              <Shield className="h-4 w-4 mt-0.5 flex-shrink-0 text-gray-500" aria-hidden />
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-gray-900 dark:text-white truncate">
                    {item.title}
                  </span>
                  <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${status.bg} ${status.color}`}>
                    {status.label}
                  </span>
                </div>
                <div className="mt-0.5 flex items-center gap-2 text-[11px] text-gray-500 dark:text-gray-400">
                  <span>{CATEGORY_LABEL[item.category]}</span>
                  <span>&middot;</span>
                  <span>{new Date(item.generatedAt).toLocaleDateString()}</span>
                </div>
              </div>
              {isExpanded ? (
                <ChevronDown className="h-4 w-4 flex-shrink-0 text-gray-400" aria-hidden />
              ) : (
                <ChevronRight className="h-4 w-4 flex-shrink-0 text-gray-400" aria-hidden />
              )}
            </button>

            {isExpanded && (
              <div className="px-4 pb-4 pt-1 space-y-2">
                <p className="text-xs text-gray-600 dark:text-gray-300 leading-relaxed">
                  {item.description}
                </p>
                {item.details && (
                  <div className="rounded bg-gray-50 dark:bg-gray-900/50 p-2.5 text-[11px] font-mono text-gray-600 dark:text-gray-400 whitespace-pre-wrap">
                    {item.details}
                  </div>
                )}
                <div className="flex flex-wrap gap-2 pt-1">
                  {item.evidenceUrl && (
                    <a
                      href={item.evidenceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
                    >
                      <ExternalLink className="h-3 w-3" />
                      View evidence
                    </a>
                  )}
                  {item.auditEntryId && (
                    <a
                      href={`/govern?tab=audit&entry=${item.auditEntryId}`}
                      className="inline-flex items-center gap-1 text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
                    >
                      <FileText className="h-3 w-3" />
                      Audit entry
                    </a>
                  )}
                  {item.relatedPolicyId && (
                    <span className="text-[11px] text-gray-500 dark:text-gray-400">
                      Policy: {item.relatedPolicyId}
                    </span>
                  )}
                  {item.relatedPipelineId && (
                    <span className="text-[11px] text-gray-500 dark:text-gray-400">
                      Pipeline: {item.relatedPipelineId}
                    </span>
                  )}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
