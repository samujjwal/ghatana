import React, { useState, useCallback } from 'react';

import { AccessibilityAuditor } from './AccessibilityAuditor';
import { AccessibilityReportViewer } from './AccessibilityReportViewer';

import type { AccessibilityReport, Finding } from './types';

/**
 *
 */
export interface AccessibilityAuditToolProps {
  /** Optional class name for the container */
  className?: string;

  /** Whether to show the audit button */
  showAuditButton?: boolean;

  /** Whether to show the report by default */
  defaultExpanded?: boolean;

  /** Callback when the audit completes */
  onAuditComplete?: (report: AccessibilityReport) => void;

  /** Callback when an error occurs */
  onError?: (error: Error) => void;
}

/**
 * A tool that allows users to run accessibility audits and view the results
 */
export const AccessibilityAuditTool: React.FC<AccessibilityAuditToolProps> = ({
  className = '',
  showAuditButton = true,
  defaultExpanded = false,
  onAuditComplete,
  onError,
}) => {
  const [isLoading, setIsLoading] = useState(false);
  const [report, setReport] = useState<AccessibilityReport | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);
  const auditorRef = React.useRef<AccessibilityAuditor | null>(null);

  // Initialize the auditor
  React.useEffect(() => {
    let isMounted = true;

    const init = async () => {
      try {
        const auditor = new AccessibilityAuditor();
        await auditor.initialize();
        if (isMounted) {
          auditorRef.current = auditor;
        }
      } catch (err) {
        console.error('Failed to initialize AccessibilityAuditor:', err);
        if (isMounted) {
          const error = err instanceof Error ? err : new Error(String(err));
          setError(error);
          onError?.(error);
        }
      }
    };

    init();

    return () => {
      isMounted = false;
    };
  }, [onError]);

  // Run the accessibility audit
  const runAudit = useCallback(async () => {
    if (!auditorRef.current) {
      const error = new Error('AccessibilityAuditor not initialized');
      setError(error);
      onError?.(error);
      return;
    }

    setIsLoading(true);
    setError(null);
    setReport(null);

    try {
      const result = await auditorRef.current.audit();
      setReport(result);
      onAuditComplete?.(result);
      setIsExpanded(true);
    } catch (err) {
      console.error('Accessibility audit failed:', err);
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      onError?.(error);
    } finally {
      setIsLoading(false);
    }
  }, [onAuditComplete, onError]);

  // Handle violation click
  /**
   * Handles click on a violation finding
   * Note: This is a simplified handler that receives Finding objects.
   * For full element highlighting, use the complete AccessibilityReportViewer
   * with access to the original AxeResults.
   */
  const handleViolationClick = useCallback((finding: Finding) => {
    // Future: Could implement element highlighting if we store
    // additional node information in Finding type
    console.log('Violation clicked:', finding.id, finding.description);
  }, []);

  // Toggle the expanded state
  const toggleExpanded = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  return (
    <div className={`accessibility-audit-tool ${className}`}>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-medium text-gray-900 dark:text-white">
          Accessibility Audit
        </h2>

        <div className="flex items-center space-x-2">
          {showAuditButton && (
            <button
              onClick={runAudit}
              disabled={isLoading}
              className={`px-4 py-2 text-sm font-medium rounded-md ${isLoading
                ? 'bg-gray-200 text-gray-500 cursor-not-allowed dark:bg-gray-700 dark:text-gray-400'
                : 'bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 dark:bg-blue-700 dark:hover:bg-blue-600'
                }`}
            >
              {isLoading ? 'Auditing...' : 'Run Audit'}
            </button>
          )}

          {report && (
            <button
              onClick={toggleExpanded}
              className="p-2 text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 focus:outline-none"
              aria-label={isExpanded ? 'Collapse report' : 'Expand report'}
            >
              {isExpanded ? '▼' : '▶'}
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 mb-4 text-sm text-red-700 bg-red-100 rounded-lg dark:bg-red-900 dark:text-red-200">
          <p className="font-medium">Error running accessibility audit:</p>
          <p>{error.message}</p>
          <button
            onClick={() => setError(null)}
            className="mt-2 text-sm text-red-600 hover:text-red-800 dark:text-red-300 dark:hover:text-red-100"
          >
            Dismiss
          </button>
        </div>
      )}

      {isExpanded && report && (
        <div className="mt-4">
          <AccessibilityReportViewer
            report={report}
            onFindingClick={handleViolationClick}
            showAllFindings={false}
          />
        </div>
      )}

      {isExpanded && !report && !error && !isLoading && (
        <div className="p-6 text-center text-gray-500 bg-gray-50 rounded-lg dark:bg-gray-800 dark:text-gray-400">
          <p>No audit has been run yet. Click "Run Audit" to check this page for accessibility issues.</p>
        </div>
      )}

      {isLoading && (
        <div className="p-6 text-center">
          <div className="inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent"></div>
          <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">Running accessibility audit...</p>
        </div>
      )}
    </div>
  );
};

export default AccessibilityAuditTool;
