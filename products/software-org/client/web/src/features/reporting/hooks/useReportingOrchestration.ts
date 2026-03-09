/**
 * Reporting Orchestration Hook
 *
 * <p><b>Purpose</b><br>
 * Custom hook providing reporting feature orchestration, combining store state,
 * API queries, and business logic for report management, filtering,
 * and export operations.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const {
 *   reports,
 *   selectedReport,
 *   isLoading,
 *   selectReport,
 *   setStatusFilter,
 *   generateReport,
 *   exportReport,
 * } = useReportingOrchestration(tenantId);
 * ```
 *
 * @doc.type hook
 * @doc.purpose Reporting feature orchestration and state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import { useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { reportingApi } from '@/services/api/reportingApi';
import { selectedReportIdAtom, reportStatusFilterAtom } from '../stores/reporting.store';

/**
 * Reporting orchestration hook interface
 */
export interface UseReportingOrchestrationReturn {
    reports: Array<any> | undefined;
    selectedReport: any | undefined;
    statusFilter: 'draft' | 'archived' | 'published' | null;
    isLoading: boolean;
    isExporting: boolean;
    selectReport: (reportId: string) => void;
    setStatusFilter: (status: 'draft' | 'archived' | 'published' | null) => void;
    generateReport: (params: { title: string; dateRange: { start: Date; end: Date }; filters: Record<string, unknown> }) => Promise<any>;
    exportReport: (reportId: string, format: 'pdf' | 'csv' | 'json' | 'html' | 'excel') => Promise<Blob>;
    deleteReport: (reportId: string) => Promise<void>;
}

/**
 * Custom hook for reporting feature orchestration.
 *
 * @param tenantId - Tenant identifier
 * @returns Reporting orchestration state and methods
 */
export function useReportingOrchestration(tenantId: string): UseReportingOrchestrationReturn {
    const [selectedReportId, setSelectedReportId] = useAtom(selectedReportIdAtom);
    const [statusFilter, setStatusFilter] = useAtom(reportStatusFilterAtom);
    const queryClient = useQueryClient();

    // Fetch reports
    const { data: reports, isLoading: reportsLoading } = useQuery({
        queryKey: ['reports', tenantId, statusFilter],
        queryFn: () =>
            reportingApi.getReports(tenantId || 'default', {
                status: statusFilter || undefined,
            }),
        staleTime: 30 * 1000,
        enabled: !!tenantId,
    });

    // Generate report mutation
    const generateReportMutation = useMutation({
        mutationFn: (params: { title: string; dateRange: { start: Date; end: Date }; filters: Record<string, unknown> }) =>
            reportingApi.generateReport(params.title, params.dateRange, params.filters, tenantId || 'default'),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['reports', tenantId, statusFilter] });
        },
    });

    // Export report mutation
    const exportReportMutation = useMutation({
        mutationFn: ({ reportId, format }: { reportId: string; format: 'pdf' | 'csv' | 'json' | 'html' | 'excel' }) =>
            reportingApi.exportReport(reportId, format, tenantId || 'default'),
    });

    // Delete report mutation
    const deleteReportMutation = useMutation({
        mutationFn: (reportId: string) => reportingApi.deleteReport(reportId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['reports', tenantId, statusFilter] });
            setSelectedReportId(null);
        },
    });

    // Selected report
    const selectedReport = useMemo(
        () => reports?.find((r: any) => r.id === selectedReportId),
        [reports, selectedReportId]
    );

    // Combined loading state
    const isLoading = reportsLoading;
    const isExporting =
        exportReportMutation.isPending || generateReportMutation.isPending || deleteReportMutation.isPending;

    // Handler callbacks
    const selectReport = useCallback(
        (reportId: string) => {
            setSelectedReportId(reportId);
        },
        [setSelectedReportId]
    );

    const handleSetStatusFilter = useCallback(
        (status: 'draft' | 'archived' | 'published' | null) => {
            setStatusFilter(status);
        },
        [setStatusFilter]
    );

    const generateReport = useCallback(
        (params: { title: string; dateRange: { start: Date; end: Date }; filters: Record<string, unknown> }) =>
            generateReportMutation.mutateAsync(params),
        [generateReportMutation]
    );

    const exportReport = useCallback(
        async (reportId: string, format: 'pdf' | 'csv' | 'json' | 'html' | 'excel') => {
            const blob = await exportReportMutation.mutateAsync({ reportId, format });
            if (!blob) throw new Error('Export failed');
            return blob;
        },
        [exportReportMutation]
    );

    const deleteReport = useCallback(
        (reportId: string) => deleteReportMutation.mutateAsync(reportId),
        [deleteReportMutation]
    );

    return {
        reports,
        selectedReport,
        statusFilter,
        isLoading,
        isExporting,
        selectReport,
        setStatusFilter: handleSetStatusFilter,
        generateReport,
        exportReport,
        deleteReport,
    };
}
