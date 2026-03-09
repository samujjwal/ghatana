/**
 * Report interface.
 */
import * as React from 'react';

export interface Report {
    id: string;
    title: string;
    description: string;
    generatedAt: Date;
    generatedBy: string;
    format: 'pdf' | 'csv' | 'json' | 'html';
    status: 'draft' | 'published' | 'archived';
    sections: string[];
    metrics: {
        pageCount?: number;
        dataPoints?: number;
        timeSpan?: string;
    };
}

/**
 * Report List Props interface.
 */
export interface ReportListProps {
    reports: Report[];
    onReportClick?: (report: Report) => void;
    onDownload?: (report: Report) => void;
    onDelete?: (report: Report) => void;
    isLoading?: boolean;
    filterStatus?: Report['status'];
}

/**
 * Report List - Displays list of generated reports.
 *
 * <p><b>Purpose</b><br>
 * Shows all generated reports with metadata, status, and quick actions.
 *
 * <p><b>Features</b><br>
 * - Report listing with metadata
 * - Status badges (draft/published/archived)
 * - Format indicators
 * - Download button
 * - Delete confirmation
 * - Sorting and filtering
 * - Empty state
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * Render reports with filtering and actions.
 *
 * @doc.type component
 * @doc.purpose Report list display
 * @doc.layer product
 * @doc.pattern Organism
 */
export const ReportList = React.memo(
    ({
        reports,
        onReportClick,
        onDownload,
        onDelete,
        isLoading,
        filterStatus,
    }: ReportListProps) => {
        const [deleteConfirm, setDeleteConfirm] = React.useState<string | null>(null);

        const filteredReports = filterStatus
            ? reports.filter((r) => r.status === filterStatus)
            : reports;

        const statusColors = {
            draft: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
            published: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
            archived: 'bg-slate-100 text-slate-800 dark:bg-neutral-800 dark:text-neutral-300',
        };

        const statusIcons = {
            draft: '✏',
            published: '✓',
            archived: '📦',
        };

        const formatIcons = {
            pdf: '📄',
            csv: '📊',
            json: '{ }',
            html: '🌐',
        };

        if (isLoading) {
            return (
                <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                    <div className="animate-pulse space-y-4">
                        {Array.from({ length: 4 }).map((_, i) => (
                            <div key={i} className="h-16 bg-slate-200 dark:bg-neutral-700 rounded" />
                        ))}
                    </div>
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Reports
                </h3>

                {filteredReports.length === 0 ? (
                    <div className="py-8 text-center">
                        <p className="text-slate-600 dark:text-neutral-400 mb-2">No reports found</p>
                        <p className="text-sm text-slate-500 dark:text-slate-500">
                            {filterStatus ? `Try changing the status filter` : 'Generate a new report to get started'}
                        </p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {filteredReports.map((report) => (
                            <div
                                key={report.id}
                                onClick={() => onReportClick?.(report)}
                                role={onReportClick ? 'button' : undefined}
                                tabIndex={onReportClick ? 0 : undefined}
                                onKeyDown={(e) => {
                                    if ((e.key === 'Enter' || e.key === ' ') && onReportClick) {
                                        onReportClick(report);
                                    }
                                }}
                                className={`rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-neutral-600 dark:bg-neutral-800 transition-all ${onReportClick ? 'cursor-pointer hover:shadow-md' : ''
                                    }`}
                            >
                                <div className="flex items-start justify-between mb-2">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-2 mb-1">
                                            <h4 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {report.title}
                                            </h4>
                                            <span
                                                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${statusColors[report.status]
                                                    }`}
                                            >
                                                {statusIcons[report.status]} {report.status}
                                            </span>
                                        </div>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                                            {report.description}
                                        </p>
                                    </div>
                                </div>

                                {/* Metadata */}
                                <div className="flex items-center gap-4 mb-3 text-xs text-slate-600 dark:text-neutral-400">
                                    <span>{formatIcons[report.format]} {report.format.toUpperCase()}</span>
                                    <time>{report.generatedAt.toLocaleString()}</time>
                                    <span>•</span>
                                    <span>by {report.generatedBy}</span>
                                    {report.metrics.pageCount && (
                                        <>
                                            <span>•</span>
                                            <span>{report.metrics.pageCount} pages</span>
                                        </>
                                    )}
                                </div>

                                {/* Sections */}
                                {report.sections.length > 0 && (
                                    <div className="mb-3 flex flex-wrap gap-1">
                                        {report.sections.map((section) => (
                                            <span
                                                key={section}
                                                className="inline-block px-2 py-1 rounded-full text-xs bg-slate-200 text-slate-700 dark:bg-neutral-700 dark:text-neutral-300"
                                            >
                                                {section}
                                            </span>
                                        ))}
                                    </div>
                                )}

                                {/* Actions */}
                                <div className="flex gap-2 pt-3 border-t border-slate-200 dark:border-neutral-600">
                                    {onDownload && (
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onDownload(report);
                                            }}
                                            className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:hover:bg-blue-500 transition-colors"
                                            aria-label={`Download report ${report.id}`}
                                        >
                                            ↓ Download
                                        </button>
                                    )}
                                    {onDelete && (
                                        <>
                                            {deleteConfirm === report.id ? (
                                                <>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            onDelete(report);
                                                            setDeleteConfirm(null);
                                                        }}
                                                        className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-red-600 text-white hover:bg-red-700 dark:hover:bg-red-500 transition-colors"
                                                    >
                                                        Confirm Delete
                                                    </button>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            setDeleteConfirm(null);
                                                        }}
                                                        className="flex-1 py-1.5 px-3 rounded-md text-sm font-medium bg-slate-300 text-slate-800 hover:bg-slate-400 dark:bg-neutral-700 dark:text-slate-200 dark:hover:bg-slate-600 transition-colors"
                                                    >
                                                        Cancel
                                                    </button>
                                                </>
                                            ) : (
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setDeleteConfirm(report.id);
                                                    }}
                                                    className="px-3 py-1.5 rounded-md text-sm font-medium text-red-600 hover:text-red-700 dark:text-rose-400 dark:hover:text-red-300 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                                                    aria-label={`Delete report ${report.id}`}
                                                >
                                                    Delete
                                                </button>
                                            )}
                                        </>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        );
    }
);

ReportList.displayName = 'ReportList';

export default ReportList;
