/**
 * React hooks for compliance features
 * 
 * Provides hooks for:
 * - Generate and manage compliance reports
 * - Run compliance assessments
 * - Track compliance issues
 * - Manage approval workflows
 * - Export reports
 * - Get dashboard data and statistics
 */

import { useState, useEffect, useCallback } from 'react';
import {
    ComplianceStandard,
    ComplianceReport,
    ComplianceAssessment,
    ComplianceDashboard,
    ComplianceIssue,
    ApprovalRequest,
    ApprovalStatus,
    ComplianceFilter,
    ExportOptions,
    ComplianceStats,
} from '../types/compliance';
import { ComplianceService } from '../services/complianceService';

/**
 * Hook for generating compliance reports
 */
export function useComplianceReport() {
    const [report, setReport] = useState<ComplianceReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const generate = useCallback(
        async (
            standard: ComplianceStandard,
            options?: {
                reportType?: 'summary' | 'detailed' | 'executive' | 'technical';
                startDate?: string;
                endDate?: string;
            }
        ) => {
            setLoading(true);
            setError(null);
            try {
                const result = await ComplianceService.generateReport(standard, options);
                setReport(result);
                return result;
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to generate report');
                setError(error);
                return null;
            } finally {
                setLoading(false);
            }
        },
        []
    );

    const clear = useCallback(() => {
        setReport(null);
        setError(null);
    }, []);

    return {
        report,
        loading,
        error,
        generate,
        clear,
    };
}

/**
 * Hook for running compliance assessments
 */
export function useComplianceAssessment() {
    const [assessment, setAssessment] = useState<ComplianceAssessment | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const runAssessment = useCallback(async (standard: ComplianceStandard) => {
        setLoading(true);
        setError(null);
        try {
            const result = await ComplianceService.runAssessment(standard);
            setAssessment(result);
            return result;
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to run assessment');
            setError(error);
            return null;
        } finally {
            setLoading(false);
        }
    }, []);

    const clear = useCallback(() => {
        setAssessment(null);
        setError(null);
    }, []);

    return {
        assessment,
        loading,
        error,
        runAssessment,
        clear,
    };
}

/**
 * Hook for managing compliance issues
 */
export function useComplianceIssues(initialFilter?: ComplianceFilter) {
    const [issues, setIssues] = useState<ComplianceIssue[]>([]);
    const [total, setTotal] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const [filter, setFilter] = useState<ComplianceFilter>(initialFilter || {});

    const loadIssues = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            // In a real implementation, this would call ComplianceService
            // For now, return empty result
            setIssues([]);
            setTotal(0);
            setHasMore(false);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load issues');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    const loadMore = useCallback(async () => {
        if (!hasMore || loading) return;

        setLoading(true);
        try {
            const newFilter = {
                ...filter,
                offset: (filter.offset || 0) + (filter.limit || 50),
            };
            setFilter(newFilter);
            // Load more issues...
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load more issues');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [filter, hasMore, loading]);

    const refresh = useCallback(() => {
        setFilter({ ...filter, offset: 0 });
        loadIssues();
    }, [filter, loadIssues]);

    useEffect(() => {
        loadIssues();
    }, [loadIssues]);

    return {
        issues,
        total,
        hasMore,
        loading,
        error,
        filter,
        setFilter,
        loadMore,
        refresh,
    };
}

/**
 * Hook for managing approval requests
 */
export function useApprovalRequest() {
    const [request, setRequest] = useState<ApprovalRequest | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const createRequest = useCallback(
        async (params: {
            requestType: 'permission_change' | 'role_change' | 'bulk_operation';
            requestedBy: string;
            title: string;
            description: string;
            changes: ApprovalRequest['changes'];
            justification?: string;
            approvers: string[];
            priority?: any;
            expiresAt?: string;
        }) => {
            setLoading(true);
            setError(null);
            try {
                const result = await ComplianceService.createApprovalRequest(params);
                setRequest(result);
                return result;
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to create approval request');
                setError(error);
                return null;
            } finally {
                setLoading(false);
            }
        },
        []
    );

    const respond = useCallback(
        async (requestId: string, userId: string, decision: 'approve' | 'reject', comment?: string) => {
            setLoading(true);
            setError(null);
            try {
                const result = await ComplianceService.respondToApprovalRequest(
                    requestId,
                    userId,
                    decision,
                    comment
                );
                setRequest(result);
                return result;
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to respond to approval request');
                setError(error);
                return null;
            } finally {
                setLoading(false);
            }
        },
        []
    );

    const clear = useCallback(() => {
        setRequest(null);
        setError(null);
    }, []);

    return {
        request,
        loading,
        error,
        createRequest,
        respond,
        clear,
    };
}

/**
 * Hook for listing approval requests
 */
export function useApprovalRequests(status?: ApprovalStatus) {
    const [requests, setRequests] = useState<ApprovalRequest[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadRequests = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            // In a real implementation, this would call ComplianceService
            setRequests([]);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load approval requests');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [status]);

    const refresh = useCallback(() => {
        loadRequests();
    }, [loadRequests]);

    useEffect(() => {
        loadRequests();
    }, [loadRequests]);

    return {
        requests,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for compliance dashboard
 */
export function useComplianceDashboard() {
    const [dashboard, setDashboard] = useState<ComplianceDashboard | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadDashboard = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await ComplianceService.getDashboard();
            setDashboard(result);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load compliance dashboard');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, []);

    const refresh = useCallback(() => {
        loadDashboard();
    }, [loadDashboard]);

    useEffect(() => {
        loadDashboard();
    }, [loadDashboard]);

    return {
        dashboard,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for exporting compliance reports
 */
export function useComplianceExport() {
    const [exporting, setExporting] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const exportReport = useCallback(
        async (reportId: string, options: ExportOptions) => {
            setExporting(true);
            setError(null);
            try {
                const blob = await ComplianceService.exportReport(reportId, options);

                // Trigger download
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `compliance-report-${reportId}.${options.format.toLowerCase()}`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);

                return true;
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to export report');
                setError(error);
                return false;
            } finally {
                setExporting(false);
            }
        },
        []
    );

    return {
        exporting,
        error,
        exportReport,
    };
}

/**
 * Hook for compliance statistics
 */
export function useComplianceStats() {
    const [stats, setStats] = useState<ComplianceStats | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadStats = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await ComplianceService.getStats();
            setStats(result);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load compliance statistics');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, []);

    const refresh = useCallback(() => {
        loadStats();
    }, [loadStats]);

    useEffect(() => {
        loadStats();
    }, [loadStats]);

    return {
        stats,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for generating quick reports
 */
export function useQuickComplianceReport(standard: ComplianceStandard | null) {
    const [report, setReport] = useState<ComplianceReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (!standard) return;

        const generateReport = async () => {
            setLoading(true);
            setError(null);
            try {
                const result = await ComplianceService.generateReport(standard, {
                    reportType: 'summary',
                });
                setReport(result);
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to generate report');
                setError(error);
            } finally {
                setLoading(false);
            }
        };

        generateReport();
    }, [standard]);

    return {
        report,
        loading,
        error,
    };
}
