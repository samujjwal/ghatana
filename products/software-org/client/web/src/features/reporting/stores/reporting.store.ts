import { atom } from 'jotai';

/**
 * Reporting state interface.
 */
export interface ReportingState {
    reportTimeRange: { start: Date; end: Date } | null;
    reportFilters: Record<string, unknown>;
    exportFormat: 'pdf' | 'csv' | 'json' | 'html' | 'excel';
    selectedReportId: string | null;
    expandedReportSection: string | null;
    isGeneratingReport: boolean;
}

/**
 * Reporting Jotai store - manages reporting UI state.
 *
 * <p><b>Purpose</b><br>
 * Centralizes reporting feature state (time range, filters, export format, report selection).
 *
 * <p><b>Atoms</b><br>
 * - reportTimeRangeAtom: Time range for report data
 * - reportFiltersAtom: Active report filters
 * - exportFormatAtom: Selected export format
 * - selectedReportIdAtom: Currently selected report
 * - expandedReportSectionAtom: Expanded report section for detail
 * - isGeneratingReportAtom: Report generation in progress
 * - reportSortByAtom: Sort order for report list
 * - reportStatusFilterAtom: Filter by report status
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useAtom } from 'jotai';
 * import { reportTimeRangeAtom } from '@/features/reporting/stores/reporting.store';
 *
 * function ReportingPage() {
 *   const [timeRange, setTimeRange] = useAtom(reportTimeRangeAtom);
 *   // Page logic
 * }
 * ```
 *
 * @doc.type configuration
 * @doc.purpose Reporting state management
 * @doc.layer product
 * @doc.pattern State Store
 */

/**
 * Report time range atom - time range for report data collection
 */
export const reportTimeRangeAtom = atom<{ start: Date; end: Date } | null>(null);

/**
 * Report filters atom - active filters for report generation
 */
export const reportFiltersAtom = atom<Record<string, unknown>>({});

/**
 * Export format atom - selected export format
 */
export const exportFormatAtom = atom<'pdf' | 'csv' | 'json' | 'html' | 'excel'>('pdf');

/**
 * Selected report ID atom - currently selected report
 */
export const selectedReportIdAtom = atom<string | null>(null);

/**
 * Expanded report section atom - report section expanded for detail view
 */
export const expandedReportSectionAtom = atom<string | null>(null);

/**
 * Is generating report atom - report generation in progress
 */
export const isGeneratingReportAtom = atom<boolean>(false);

/**
 * Report sort by atom - sort order for report list
 */
export const reportSortByAtom = atom<'date' | 'name' | 'status'>('date');

/**
 * Report status filter atom - filter by report status
 */
export const reportStatusFilterAtom = atom<'draft' | 'published' | 'archived' | null>(null);

export default {
    reportTimeRangeAtom,
    reportFiltersAtom,
    exportFormatAtom,
    selectedReportIdAtom,
    expandedReportSectionAtom,
    isGeneratingReportAtom,
    reportSortByAtom,
    reportStatusFilterAtom,
};
