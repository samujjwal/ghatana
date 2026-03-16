/**
 * ValidationPanel Component
 *
 * Panel for displaying and managing validation errors and warnings
 * surfaced during the YAPPC bootstrapping session flow.
 *
 * @doc.type component
 * @doc.purpose Validation issue list with resolve/ignore actions
 * @doc.layer product
 * @doc.pattern UI Component
 */

import React from 'react';
import { AlertTriangle, XCircle, CheckCircle2, X, Wrench } from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export interface ValidationIssue {
  id: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  title?: string;
  description?: string;
}

interface ValidationPanelProps {
  issues?: ValidationIssue[];
  onResolve?: (issueId: string) => void;
  onIgnore?: (issueId: string) => void;
  className?: string;
}

// =============================================================================
// Severity Config
// =============================================================================

const SEVERITY_CONFIG = {
  error: {
    label: 'Error',
    icon: XCircle,
    badge: 'bg-red-500/15 text-red-400 border border-red-500/20',
    row: 'border-l-2 border-red-500/50',
  },
  warning: {
    label: 'Warning',
    icon: AlertTriangle,
    badge: 'bg-yellow-500/15 text-yellow-400 border border-yellow-500/20',
    row: 'border-l-2 border-yellow-500/50',
  },
  info: {
    label: 'Info',
    icon: CheckCircle2,
    badge: 'bg-blue-500/15 text-blue-400 border border-blue-500/20',
    row: 'border-l-2 border-blue-500/50',
  },
} as const;

// =============================================================================
// Issue Row Component
// =============================================================================

const IssueRow: React.FC<{
  issue: ValidationIssue;
  onResolve?: (id: string) => void;
  onIgnore?: (id: string) => void;
}> = ({ issue, onResolve, onIgnore }) => {
  const config = SEVERITY_CONFIG[issue.severity] ?? SEVERITY_CONFIG.warning;
  const Icon = config.icon;

  return (
    <div
      className={`flex items-start gap-3 p-3 bg-zinc-800/60 rounded-lg ${config.row}`}
    >
      {/* Icon */}
      <Icon className="w-4 h-4 mt-0.5 flex-shrink-0 text-zinc-400" />

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap mb-0.5">
          <span className={`px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide ${config.badge}`}>
            {config.label}
          </span>
          <span className="text-sm font-medium text-white truncate">
            {issue.title ?? issue.message}
          </span>
        </div>
        {issue.title && issue.message !== issue.title && (
          <p className="text-xs text-zinc-400 leading-relaxed">{issue.message}</p>
        )}
        {issue.description && (
          <p className="text-xs text-zinc-500 mt-1 leading-relaxed">{issue.description}</p>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1.5 flex-shrink-0">
        {onResolve && (
          <button
            onClick={() => onResolve(issue.id)}
            title="Mark as resolved"
            className="flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md bg-emerald-500/15 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/25 transition-colors"
          >
            <Wrench className="w-3 h-3" />
            Fix
          </button>
        )}
        {onIgnore && (
          <button
            onClick={() => onIgnore(issue.id)}
            title="Ignore this issue"
            className="flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-md bg-zinc-700/60 text-zinc-400 border border-zinc-600/40 hover:bg-zinc-700 transition-colors"
          >
            <X className="w-3 h-3" />
          </button>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// ValidationPanel Component
// =============================================================================

export const ValidationPanel: React.FC<ValidationPanelProps> = ({
  issues = [],
  onResolve,
  onIgnore,
  className = '',
}) => {
  const errors = issues.filter((i) => i.severity === 'error');
  const warnings = issues.filter((i) => i.severity === 'warning');
  const infos = issues.filter((i) => i.severity === 'info');

  return (
    <div className={`bg-zinc-900 rounded-lg border border-zinc-800 flex flex-col h-full ${className}`}>
      {/* Header */}
      <div className="px-4 py-3 border-b border-zinc-800 flex-shrink-0">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-white">Validation</h3>
          {issues.length > 0 && (
            <div className="flex items-center gap-2">
              {errors.length > 0 && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-red-500/15 text-red-400">
                  {errors.length} error{errors.length !== 1 ? 's' : ''}
                </span>
              )}
              {warnings.length > 0 && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-yellow-500/15 text-yellow-400">
                  {warnings.length} warning{warnings.length !== 1 ? 's' : ''}
                </span>
              )}
              {infos.length > 0 && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-500/15 text-blue-400">
                  {infos.length} info
                </span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Issue List */}
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {issues.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-2 text-center py-8">
            <CheckCircle2 className="w-8 h-8 text-emerald-400/60" />
            <p className="text-sm font-medium text-zinc-400">No issues found</p>
            <p className="text-xs text-zinc-600">All validations passed</p>
          </div>
        ) : (
          issues.map((issue) => (
            <IssueRow
              key={issue.id}
              issue={issue}
              onResolve={onResolve}
              onIgnore={onIgnore}
            />
          ))
        )}
      </div>
    </div>
  );
};
