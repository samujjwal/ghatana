/**
 * AuditBoundary Module
 *
 * @doc.type component
 * @doc.purpose Display audit warnings and telemetry status
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { AlertTriangle, ShieldCheck } from 'lucide-react';
import { Typography } from '@ghatana/design-system';

export interface AuditBoundaryProps {
  readonly warning: string | null;
  readonly auditContext?: {
    readonly tenantId?: string;
    readonly workspaceId?: string;
    readonly projectId?: string;
  };
}

export function AuditBoundary({
  warning,
  auditContext,
}: AuditBoundaryProps): React.JSX.Element | null {
  if (!warning && !auditContext) {
    return null;
  }

  return (
    <div className="rounded-lg border border-orange-200 bg-orange-50 p-4 dark:border-orange-900/50 dark:bg-orange-950/20">
      <div className="mb-3 flex items-center gap-2">
        <ShieldCheck className="h-5 w-5 text-orange-600 dark:text-orange-400" />
        <Typography variant="h3" className="text-base font-semibold text-orange-800 dark:text-orange-200">
          Audit & Telemetry
        </Typography>
      </div>

      {auditContext && (
        <div className="mb-3 text-xs text-gray-600 dark:text-gray-400">
          <div>
            <strong>Scope:</strong> {auditContext.tenantId ?? 'N/A'} / {auditContext.workspaceId ?? 'N/A'} / {auditContext.projectId ?? 'N/A'}
          </div>
        </div>
      )}

      {warning && (
        <div className="flex items-start gap-2 rounded border border-orange-300 bg-orange-100 p-3 text-sm text-orange-800 dark:border-orange-900/50 dark:bg-orange-950/30 dark:text-orange-200">
          <AlertTriangle className="h-4 w-4 flex-shrink-0 mt-0.5" />
          <span>{warning}</span>
        </div>
      )}

      {!warning && auditContext && (
        <div className="text-sm text-green-700 dark:text-green-300">
          <ShieldCheck className="mr-1 inline h-4 w-4" />
          Command audit and telemetry are active and recording.
        </div>
      )}
    </div>
  );
}
