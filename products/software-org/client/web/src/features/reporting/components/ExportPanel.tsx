import React from 'react';

/**
 * Export Panel Props interface.
 */
export interface ExportPanelProps {
    onExport?: (format: string, options: ExportOptions) => void;
    isExporting?: boolean;
    availableFormats?: string[];
    error?: string;
}

/**
 * Export options interface.
 */
export interface ExportOptions {
    format: 'pdf' | 'csv' | 'json' | 'html' | 'excel';
    includeCharts: boolean;
    includeMetadata: boolean;
    dateRange?: { start: Date; end: Date };
    fileName?: string;
}

/**
 * Export Panel - Controls for exporting reports and data.
 *
 * <p><b>Purpose</b><br>
 * Provides UI for selecting export format and options for downloading reports.
 *
 * <p><b>Features</b><br>
 * - Multiple export formats (PDF, CSV, JSON, HTML, Excel)
 * - Options for charts and metadata inclusion
 * - Date range selection
 * - File name customization
 * - Export progress tracking
 * - Error handling
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <ExportPanel 
 *   onExport={handleExport}
 *   isExporting={false}
 *   availableFormats={['pdf', 'csv', 'json']}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Report export controls
 * @doc.layer product
 * @doc.pattern Molecule
 */
export const ExportPanel = React.memo(
    ({ onExport, isExporting, availableFormats = ['pdf', 'csv', 'json', 'html', 'excel'], error }: ExportPanelProps) => {
        const [selectedFormat, setSelectedFormat] = React.useState<ExportOptions['format']>('pdf');
        const [includeCharts, setIncludeCharts] = React.useState(true);
        const [includeMetadata, setIncludeMetadata] = React.useState(true);
        const [fileName, setFileName] = React.useState('report');

        const handleExport = () => {
            onExport?.(selectedFormat, {
                format: selectedFormat,
                includeCharts,
                includeMetadata,
                fileName,
            });
        };

        const formatDescriptions = {
            pdf: 'Adobe PDF - Best for sharing and printing',
            csv: 'CSV Spreadsheet - Best for data analysis',
            json: 'JSON Format - Best for API integration',
            html: 'HTML Web Page - Best for viewing online',
            excel: 'Excel Workbook - Best for spreadsheets',
        };

        const formatIcons = {
            pdf: '📄',
            csv: '📊',
            json: '{ }',
            html: '🌐',
            excel: '📈',
        };

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                    Export Report
                </h3>

                {error && (
                    <div className="mb-4 p-3 rounded-lg bg-red-100 border border-red-300 dark:bg-rose-600/30 dark:border-red-800">
                        <p className="text-sm text-red-800 dark:text-red-300">
                            ✕ {error}
                        </p>
                    </div>
                )}

                {/* Format selection */}
                <div className="mb-6">
                    <label className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-3">
                        Export Format
                    </label>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                        {availableFormats.map((format) => (
                            <button
                                key={format}
                                onClick={() => setSelectedFormat(format as ExportOptions['format'])}
                                className={`p-3 rounded-lg border-2 text-left transition-all ${selectedFormat === format
                                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 dark:border-blue-500'
                                        : 'border-slate-200 bg-white dark:border-neutral-600 dark:bg-neutral-800 hover:border-slate-300 dark:hover:border-slate-600'
                                    }`}
                                aria-pressed={selectedFormat === format}
                            >
                                <div className="flex items-center gap-2">
                                    <span className="text-lg">{formatIcons[format as keyof typeof formatIcons]}</span>
                                    <div>
                                        <p className="font-medium text-slate-900 dark:text-neutral-100 uppercase text-sm">
                                            {format}
                                        </p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            {formatDescriptions[format as keyof typeof formatDescriptions]}
                                        </p>
                                    </div>
                                </div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Options */}
                <div className="mb-6 space-y-3 p-4 bg-slate-50 dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-600">
                    <label className="flex items-center gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={includeCharts}
                            onChange={(e) => setIncludeCharts(e.target.checked)}
                            className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-2 focus:ring-blue-500 dark:border-neutral-600 dark:bg-neutral-700"
                        />
                        <span className="text-sm text-slate-900 dark:text-neutral-100">
                            Include charts and visualizations
                        </span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={includeMetadata}
                            onChange={(e) => setIncludeMetadata(e.target.checked)}
                            className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-2 focus:ring-blue-500 dark:border-neutral-600 dark:bg-neutral-700"
                        />
                        <span className="text-sm text-slate-900 dark:text-neutral-100">
                            Include metadata and timestamps
                        </span>
                    </label>
                </div>

                {/* File name */}
                <div className="mb-6">
                    <label htmlFor="fileName" className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-2">
                        File Name
                    </label>
                    <div className="flex gap-2">
                        <input
                            id="fileName"
                            type="text"
                            value={fileName}
                            onChange={(e) => setFileName(e.target.value)}
                            placeholder="report"
                            className="flex-1 px-3 py-2 rounded-lg border border-slate-300 bg-white dark:border-neutral-600 dark:bg-neutral-800 text-slate-900 dark:text-neutral-100 placeholder-slate-500 dark:placeholder-slate-400 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                        />
                        <span className="flex items-center px-3 py-2 text-sm text-slate-600 dark:text-neutral-400">
                            .{selectedFormat}
                        </span>
                    </div>
                </div>

                {/* Export button */}
                <button
                    onClick={handleExport}
                    disabled={isExporting}
                    className="w-full py-2.5 px-4 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-700 dark:hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    aria-busy={isExporting}
                >
                    {isExporting ? (
                        <>
                            <span className="inline-block animate-spin mr-2">⟳</span>
                            Exporting...
                        </>
                    ) : (
                        <>
                            ↓ Export as {selectedFormat.toUpperCase()}
                        </>
                    )}
                </button>
            </div>
        );
    }
);

ExportPanel.displayName = 'ExportPanel';

export default ExportPanel;
