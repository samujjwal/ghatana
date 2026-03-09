/**
 * @fileoverview Data Export Tab
 * 
 * Export metrics data as CSV/JSON and import backup data
 */

import React, { useState } from 'react';
import { Download, Upload } from 'lucide-react';
import browser from 'webextension-polyfill';

interface PolicyEventExport {
    timestamp?: number;
    url?: string;
    domain?: string;
    category?: string;
    policyDecision?: string;
    duration?: number;
}

export const DataExport: React.FC = () => {
    const [exporting, setExporting] = useState(false);
    const [importing, setImporting] = useState(false);
    const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

    const exportAsJSON = async () => {
        try {
            setExporting(true);
            setMessage(null);

            // Get all data from storage
            const data = await browser.storage.local.get(null);
            const allData = await browser.storage.sync.get(null);

            const exportData = {
                exportDate: new Date().toISOString(),
                localData: data,
                syncData: allData,
            };

            // Create blob and download
            const json = JSON.stringify(exportData, null, 2);
            const blob = new Blob([json], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `guardian-export-${new Date().toISOString().split('T')[0]}.json`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);

            setMessage({ type: 'success', text: 'Data exported successfully' });
        } catch (error) {
            setMessage({
                type: 'error',
                text: `Export failed: ${error instanceof Error ? error.message : String(error)}`,
            });
        } finally {
            setExporting(false);
        }
    };

    const exportAsCSV = async () => {
        try {
            setExporting(true);
            setMessage(null);

            // Get pipeline policy-evaluated events from LocalStorageSink
            // LocalStorageSink stores these under 'guardian:events'
            const data = await browser.storage.local.get('guardian:events');
            const events = (data['guardian:events'] || []) as PolicyEventExport[];

            // Convert to CSV
            let csv = 'Timestamp,URL,Domain,Category,PolicyDecision,DurationMs\n';

            const esc = (v: string) => `"${v.replace(/"/g, '""')}"`;

            events.forEach((e) => {
                const timestamp = e.timestamp ? new Date(e.timestamp).toISOString() : '';
                const url = e.url ?? '';
                const domain = e.domain ?? '';
                const category = e.category ?? '';
                const decision = e.policyDecision ?? '';
                const duration = e.duration ?? 0;

                csv += [
                    esc(timestamp),
                    esc(url),
                    esc(domain),
                    esc(category),
                    esc(decision),
                    duration.toString(),
                ].join(',') + '\n';
            });

            // Create blob and download
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `guardian-events-${new Date().toISOString().split('T')[0]}.csv`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);

            setMessage({ type: 'success', text: 'CSV exported successfully' });
        } catch (error) {
            setMessage({
                type: 'error',
                text: `Export failed: ${error instanceof Error ? error.message : String(error)}`,
            });
        } finally {
            setExporting(false);
        }
    };
    const handleImportFile = async (file: File) => {
        try {
            setImporting(true);
            setMessage(null);

            const text = await file.text();
            const importData = JSON.parse(text);

            // Validate structure
            if (!importData.localData && !importData.syncData) {
                throw new Error('Invalid backup file format');
            }

            // Ask for confirmation
            if (!window.confirm('This will overwrite your current Guardian data. Continue?')) {
                return;
            }

            // Import data
            if (importData.localData) {
                await browser.storage.local.set(importData.localData);
            }
            if (importData.syncData) {
                await browser.storage.sync.set(importData.syncData);
            }

            setMessage({ type: 'success', text: 'Data imported successfully. Please reload Guardian.' });
        } catch (error) {
            setMessage({
                type: 'error',
                text: `Import failed: ${error instanceof Error ? error.message : String(error)}`,
            });
        } finally {
            setImporting(false);
        }
    };

    return (
        <div className="space-y-8">
            {/* Messages */}
            {message && (
                <div
                    className={`p-4 rounded-lg border ${message.type === 'success'
                        ? 'bg-green-50 border-green-200 text-green-900'
                        : 'bg-red-50 border-red-200 text-red-900'
                        }`}
                >
                    {message.text}
                </div>
            )}

            {/* Export Section */}
            <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Export Data</h3>
                <p className="text-gray-600 mb-6">Download your Guardian data for backup or analysis</p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <button
                        onClick={exportAsJSON}
                        disabled={exporting}
                        className="p-6 border-2 border-dashed border-gray-300 rounded-lg hover:border-blue-400 hover:bg-blue-50 transition-colors flex flex-col items-center gap-3 disabled:opacity-50"
                    >
                        <Download className="h-6 w-6 text-blue-600" />
                        <div className="text-left">
                            <p className="font-medium text-gray-900">Export as JSON</p>
                            <p className="text-sm text-gray-600 mt-1">Complete backup (settings + data)</p>
                        </div>
                    </button>

                    <button
                        onClick={exportAsCSV}
                        disabled={exporting}
                        className="p-6 border-2 border-dashed border-gray-300 rounded-lg hover:border-blue-400 hover:bg-blue-50 transition-colors flex flex-col items-center gap-3 disabled:opacity-50"
                    >
                        <Download className="h-6 w-6 text-blue-600" />
                        <div className="text-left">
                            <p className="font-medium text-gray-900">Export as CSV</p>
                            <p className="text-sm text-gray-600 mt-1">Metrics data only (for spreadsheets)</p>
                        </div>
                    </button>
                </div>
            </div>

            {/* Import Section */}
            <div className="border-t border-gray-200 pt-8">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Import Data</h3>
                <p className="text-gray-600 mb-6">Restore Guardian data from a backup file</p>

                <label className="p-6 border-2 border-dashed border-gray-300 rounded-lg hover:border-green-400 hover:bg-green-50 transition-colors cursor-pointer flex flex-col items-center gap-3">
                    <Upload className="h-6 w-6 text-green-600" />
                    <div className="text-center">
                        <p className="font-medium text-gray-900">Click to select backup file</p>
                        <p className="text-sm text-gray-600 mt-1">or drag and drop (.json)</p>
                    </div>
                    <input
                        type="file"
                        accept=".json"
                        onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) {
                                handleImportFile(file);
                            }
                        }}
                        disabled={importing}
                        className="hidden"
                    />
                </label>
            </div>

            {/* Info */}
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-2">
                <p className="text-sm text-blue-900">
                    <strong>📁 Backup:</strong> We recommend regularly backing up your Guardian data.
                </p>
                <p className="text-sm text-blue-900">
                    <strong>📊 Analysis:</strong> CSV export is perfect for analyzing trends in a spreadsheet.
                </p>
            </div>
        </div>
    );
};
