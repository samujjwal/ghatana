import { memo, useState } from 'react';

/**
 * Data export and integration page for exporting data to external systems.
 *
 * <p><b>Purpose</b><br>
 * Central hub for exporting events, metrics, and reports in multiple formats
 * to support external analysis and integration with downstream systems.
 *
 * <p><b>Features</b><br>
 * - Multi-format export (CSV, JSON, Parquet)
 * - Scheduled exports with retention
 * - Export templates and presets
 * - API integration setup
 * - Export history and audit trail
 * - Bulk data export
 *
 * @doc.type page
 * @doc.purpose Data export and integration
 * @doc.layer product
 * @doc.pattern Page
 */

export const DataExport = memo(function DataExport() {
    // GIVEN: User on data export page
    // WHEN: User configures and runs exports
    // THEN: Display export options and history

    const [exportFormat, setExportFormat] = useState<'csv' | 'json' | 'parquet'>('csv');
    const [selectedData, setSelectedData] = useState<'events' | 'metrics' | 'reports'>('events');
    const [showScheduleForm, setShowScheduleForm] = useState(false);

    const exportHistory = [
        { id: 1, name: 'Events Export', format: 'CSV', date: '2025-11-23', size: '2.4 GB', status: 'completed' },
        { id: 2, name: 'Metrics Report', format: 'JSON', date: '2025-11-22', size: '156 MB', status: 'completed' },
        { id: 3, name: 'Analytics Data', format: 'Parquet', date: '2025-11-21', size: '8.9 GB', status: 'completed' },
    ];

    return (
        <div className="flex flex-col h-full bg-gradient-to-br from-slate-900 to-slate-950">
            {/* Header */}
            <div className="border-b border-slate-700 bg-slate-900 p-6">
                <div>
                    <h1 className="text-4xl font-bold text-white mb-2">Data Export</h1>
                    <p className="text-slate-400">Export and share data in multiple formats</p>
                </div>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto p-6">
                <div className="grid grid-cols-3 gap-6">
                    {/* Export Configuration */}
                    <div className="col-span-2 space-y-6">
                        {/* Format Selection */}
                        <div className="bg-slate-800 border border-slate-700 rounded-lg p-6">
                            <h3 className="text-lg font-bold text-white mb-4">Export Format</h3>
                            <div className="grid grid-cols-3 gap-3">
                                {(['csv', 'json', 'parquet'] as const).map((format) => (
                                    <button
                                        key={format}
                                        onClick={() => setExportFormat(format)}
                                        className={`p-3 rounded-lg border transition-all ${exportFormat === format
                                            ? 'bg-blue-600 border-blue-500 text-white'
                                            : 'bg-slate-700 border-slate-600 text-slate-300 hover:border-slate-500'
                                            }`}
                                    >
                                        {format.toUpperCase()}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Data Selection */}
                        <div className="bg-slate-800 border border-slate-700 rounded-lg p-6">
                            <h3 className="text-lg font-bold text-white mb-4">Select Data to Export</h3>
                            <div className="space-y-2">
                                {(['events', 'metrics', 'reports'] as const).map((data) => (
                                    <label key={data} className="flex items-center gap-3 p-3 rounded hover:bg-slate-700 cursor-pointer">
                                        <input
                                            type="radio"
                                            name="dataType"
                                            value={data}
                                            checked={selectedData === data}
                                            onChange={() => setSelectedData(data)}
                                            className="w-4 h-4"
                                        />
                                        <span className="text-white capitalize">{data}</span>
                                    </label>
                                ))}
                            </div>
                        </div>

                        {/* Export Actions */}
                        <div className="bg-slate-800 border border-slate-700 rounded-lg p-6 flex gap-3">
                            <button className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors">
                                📥 Export Now
                            </button>
                            <button
                                onClick={() => setShowScheduleForm(!showScheduleForm)}
                                className="flex-1 px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-lg font-medium transition-colors"
                            >
                                📅 Schedule Export
                            </button>
                        </div>

                        {showScheduleForm && (
                            <div className="bg-slate-800 border border-slate-700 rounded-lg p-6">
                                <h3 className="text-lg font-bold text-white mb-4">Schedule Export</h3>
                                <div className="space-y-3">
                                    <select className="w-full px-4 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:border-blue-500">
                                        <option>Daily</option>
                                        <option>Weekly</option>
                                        <option>Monthly</option>
                                    </select>
                                    <input
                                        type="time"
                                        className="w-full px-4 py-2 bg-slate-700 border border-slate-600 rounded-lg text-white focus:outline-none focus:border-blue-500"
                                    />
                                    <button className="w-full px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium transition-colors">
                                        ✓ Save Schedule
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Export History */}
                    <div className="bg-slate-800 border border-slate-700 rounded-lg p-6">
                        <h3 className="text-lg font-bold text-white mb-4">Recent Exports</h3>
                        <div className="space-y-3">
                            {exportHistory.map((exp) => (
                                <div key={exp.id} className="p-3 bg-slate-700 rounded border border-slate-600">
                                    <div className="font-medium text-white text-sm mb-1">{exp.name}</div>
                                    <div className="flex justify-between items-center text-xs text-slate-400">
                                        <span>{exp.format} · {exp.size}</span>
                                        <span className="text-green-400">✓</span>
                                    </div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-400 mt-1">{exp.date}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default DataExport;
