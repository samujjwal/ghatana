import { useEffect, useState, useCallback, useRef } from 'react';

import { AccessibilityAuditor } from './AccessibilityAuditor';

import type { AccessibilityReport } from './types';

/**
 *
 */
export interface UseAccessibilityAuditOptions {
  /** Whether to run the audit automatically when the component mounts */
  autoRun?: boolean;
  
  /** Element to audit (defaults to document) */
  context?: Element | Document | string;
  
  /** axe-core run options */
  axeOptions?: unknown;
  
  /** Callback when audit completes */
  onComplete?: (report: AccessibilityReport) => void;
  
  /** Callback when an error occurs */
  onError?: (error: Error) => void;
}

/**
 *
 */
export function useAccessibilityAudit({
  autoRun = false,
  context,
  axeOptions,
  onComplete,
  onError,
}: UseAccessibilityAuditOptions = {}) {
  const [isLoading, setIsLoading] = useState(false);
  const [report, setReport] = useState<AccessibilityReport | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const auditorRef = useRef<AccessibilityAuditor | null>(null);

  // Run the accessibility audit
  const runAudit = useCallback(async (customContext?: Element | Document | string) => {
    if (!auditorRef.current) {
      const error = new Error('AccessibilityAuditor not initialized');
      setError(error);
      onError?.(error);
      return null;
    }

    setIsLoading(true);
    setError(null);

    try {
      const auditContext = customContext !== undefined ? customContext : context;
      const result = await auditorRef.current.audit(auditContext, axeOptions);
      
      setReport(result);
      onComplete?.(result);
      return result;
    } catch (err) {
      console.error('Accessibility audit failed:', err);
      const error = err instanceof Error ? err : new Error(String(err));
      setError(error);
      onError?.(error);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, [context, axeOptions, onComplete, onError]);

  // Initialize the auditor
  useEffect(() => {
    let isMounted = true;
    
    const init = async () => {
      try {
        const auditor = new AccessibilityAuditor();
        await auditor.initialize();
        if (isMounted) {
          auditorRef.current = auditor;
          
          // Run audit immediately if autoRun is true
          if (autoRun) {
            runAudit();
          }
        }
      } catch (err) {
        console.error('Failed to initialize AccessibilityAuditor:', err);
        if (isMounted) {
          setError(err instanceof Error ? err : new Error(String(err)));
          onError?.(err instanceof Error ? err : new Error(String(err)));
        }
      }
    };

    init();

    return () => {
      isMounted = false;
    };
  }, [autoRun, onError, /* include runAudit so effect re-runs if our callback changes */ runAudit]);


  // Generate a report string
  const generateReport = useCallback(() => {
    if (!report) return '';
    return auditorRef.current?.generateReport(report) || '';
  }, [report]);

  // Get issues grouped by category
  const getIssuesByCategory = useCallback(() => {
    if (!report) return {};
    
    // Group findings by their first tag (category)
    const categories: Record<string, typeof report.findings> = {};
    
    report.findings.forEach(finding => {
      const category = finding.tags.length > 0 ? finding.tags[0] : 'other';
      
      if (!categories[category]) {
        categories[category] = [];
      }
      
      categories[category].push(finding);
    });
    
    return categories;
  }, [report]);

  return {
    /** Whether an audit is currently in progress */
    isLoading,
    
    /** The last audit report */
    report,
    
    /** Any error that occurred during the last audit */
    error,
    
    /** Run the accessibility audit */
    runAudit,
    
    /** Generate a human-readable report */
    generateReport,
    
    /** Get issues grouped by category */
    getIssuesByCategory,
    
    /** The auditor instance */
    auditor: auditorRef.current,
  };
}

export default useAccessibilityAudit;
