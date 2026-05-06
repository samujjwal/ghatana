/**
 * useGovernanceExport Hook
 *
 * React hook that wraps GovernanceExportService and exposes:
 *   - exportAudit(format) — triggers a browser download
 *   - isExporting         — true while the request is in flight
 *   - exportError         — last error message, or null
 *   - lastResult          — metadata about the most recent successful export
 *
 * @doc.type hook
 * @doc.purpose Page artifact governance export in React components
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import {
  downloadGovernanceExport,
  GovernanceExportError,
} from '../services/canvas/commands/GovernanceExportService';
import type { GovernanceExportFormat, GovernanceExportResult } from '../services/canvas/commands/GovernanceExportService';

// ============================================================================
// Types
// ============================================================================

export interface UseGovernanceExportResult {
  /** Triggers the audit export download for the given format */
  exportAudit: (format: GovernanceExportFormat) => Promise<void>;
  /** True while the request is in flight */
  isExporting: boolean;
  /** Last error message, or null */
  exportError: string | null;
  /** Metadata about the most recent successful export */
  lastResult: GovernanceExportResult | null;
  /** Clear any current error */
  clearError: () => void;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * @param artifactId - The page artifact ID to export audit records for.
 *                     Pass null/undefined to disable exports (isExporting stays false,
 *                     exportAudit is a no-op that sets an error).
 */
export function useGovernanceExport(
  artifactId: string | null | undefined
): UseGovernanceExportResult {
  const [isExporting, setIsExporting] = useState<boolean>(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<GovernanceExportResult | null>(null);

  const exportAudit = useCallback(
    async (format: GovernanceExportFormat): Promise<void> => {
      if (!artifactId) {
        setExportError('No artifact selected for export.');
        return;
      }

      setIsExporting(true);
      setExportError(null);

      try {
        const result = await downloadGovernanceExport(artifactId, format);
        setLastResult(result);
      } catch (err) {
        const message =
          err instanceof GovernanceExportError
            ? err.message
            : 'Governance export failed. Please try again.';
        setExportError(message);
      } finally {
        setIsExporting(false);
      }
    },
    [artifactId]
  );

  const clearError = useCallback(() => {
    setExportError(null);
  }, []);

  return { exportAudit, isExporting, exportError, lastResult, clearError };
}
