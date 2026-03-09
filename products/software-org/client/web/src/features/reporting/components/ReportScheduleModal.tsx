import { useState } from 'react';

/**
 * Report scheduling modal for configuring automated report delivery.
 *
 * <p><b>Purpose</b><br>
 * Allows users to schedule recurring report generation and delivery via email.
 * Supports weekly/monthly schedules, email recipients, and multiple export formats.
 *
 * <p><b>Features</b><br>
 * - Frequency selection (Weekly, Monthly, Custom)
 * - Day/time selection for delivery
 * - Email recipient list management
 * - Export format selection (PDF, CSV, Excel, JSON)
 * - Preview of scheduled report
 * - Save/Cancel actions
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const [showSchedule, setShowSchedule] = useState(false);
 *
 * return (
 *   <>
 *     <button onClick={() => setShowSchedule(true)}>Schedule Report</button>
 *     {showSchedule && (
 *       <ReportScheduleModal
 *         reportId="weekly-kpis"
 *         reportName="Weekly KPIs"
 *         onSchedule={(schedule) => {
 *           console.log('Scheduled:', schedule);
 *           setShowSchedule(false);
 *         }}
 *         onCancel={() => setShowSchedule(false)}
 *       />
 *     )}
 *   </>
 * );
 * }</pre>
 *
 * @param reportId - ID of report to schedule
 * @param reportName - Display name of report
 * @param onSchedule - Callback when schedule is saved
 * @param onCancel - Callback when modal is closed
 *
 * @doc.type component
 * @doc.purpose Report scheduling modal
 * @doc.layer product
 * @doc.pattern Modal Component
 * @see ReportingDashboard
 */
export interface ReportScheduleModalProps {
    reportId: string;
    reportName: string;
    onSchedule: (schedule: ReportSchedule) => void;
    onCancel: () => void;
}

/**
 * Report schedule configuration.
 *
 * @doc.type type
 * @doc.purpose Schedule definition for automated reports
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface ReportSchedule {
    reportId: string;
    frequency: 'weekly' | 'monthly' | 'custom';
    dayOfWeek?: string; // 'Monday', 'Tuesday', etc.
    dayOfMonth?: number; // 1-31
    time: string; // HH:MM format
    recipients: string[];
    formats: Array<'pdf' | 'csv' | 'excel' | 'json'>;
    includeCharts: boolean;
    enabled: boolean;
    createdAt?: string;
    nextRun?: string;
}

export function ReportScheduleModal({
    reportId,
    reportName,
    onSchedule,
    onCancel,
}: ReportScheduleModalProps) {
    // GIVEN: User wants to schedule a report
    // WHEN: Schedule modal is opened
    // THEN: Show configuration options and save schedule

    const [frequency, setFrequency] = useState<'weekly' | 'monthly' | 'custom'>('weekly');
    const [dayOfWeek, setDayOfWeek] = useState('Monday');
    const [dayOfMonth, setDayOfMonth] = useState(1);
    const [time, setTime] = useState('09:00');
    const [recipients, setRecipients] = useState<string[]>([]);
    const [newRecipient, setNewRecipient] = useState('');
    const [formats, setFormats] = useState<Array<'pdf' | 'csv' | 'excel' | 'json'>>(['pdf']);
    const [includeCharts, setIncludeCharts] = useState(true);
    const [error, setError] = useState('');

    const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

    const handleAddRecipient = () => {
        if (!newRecipient.trim()) {
            setError('Please enter an email address');
            return;
        }
        if (!newRecipient.includes('@')) {
            setError('Please enter a valid email address');
            return;
        }
        if (recipients.includes(newRecipient)) {
            setError('This email is already added');
            return;
        }
        setRecipients([...recipients, newRecipient]);
        setNewRecipient('');
        setError('');
    };

    const handleRemoveRecipient = (email: string) => {
        setRecipients(recipients.filter((r) => r !== email));
    };

    const handleToggleFormat = (format: 'pdf' | 'csv' | 'excel' | 'json') => {
        if (formats.includes(format)) {
            setFormats(formats.filter((f) => f !== format));
        } else {
            setFormats([...formats, format]);
        }
    };

    const handleSave = () => {
        if (recipients.length === 0) {
            setError('Please add at least one recipient');
            return;
        }
        if (formats.length === 0) {
            setError('Please select at least one format');
            return;
        }

        const schedule: ReportSchedule = {
            reportId,
            frequency,
            dayOfWeek: frequency === 'weekly' ? dayOfWeek : undefined,
            dayOfMonth: frequency === 'monthly' ? dayOfMonth : undefined,
            time,
            recipients,
            formats,
            includeCharts,
            enabled: true,
            createdAt: new Date().toISOString(),
            nextRun: new Date().toISOString(), // Mock: calculate actual next run
        };

        // Mock: Store in localStorage
        const schedules = JSON.parse(localStorage.getItem('reportSchedules') || '[]');
        schedules.push(schedule);
        localStorage.setItem('reportSchedules', JSON.stringify(schedules));

        console.log('[Reporting] Schedule saved:', schedule);
        onSchedule(schedule);
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-slate-900 rounded-lg shadow-lg w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                {/* Header */}
                <div className="border-b border-slate-200 dark:border-neutral-600 px-6 py-4 flex justify-between items-center sticky top-0 bg-white dark:bg-neutral-800">
                    <h2 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">Schedule Report</h2>
                    <button
                        onClick={onCancel}
                        className="text-slate-500 hover:text-slate-700 dark:text-neutral-400 dark:hover:text-slate-200 text-2xl leading-none focus:outline-none focus:ring-2 focus:ring-blue-500 rounded"
                        aria-label="Close modal"
                    >
                        ×
                    </button>
                </div>

                {/* Content */}
                <div className="p-6 space-y-6">
                    {/* Report Info */}
                    <div>
                        <h3 className="text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">Report</h3>
                        <p className="text-lg font-semibold text-slate-900 dark:text-neutral-100">{reportName}</p>
                    </div>

                    {/* Frequency */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                            Frequency
                        </label>
                        <div className="space-y-2">
                            {(['weekly', 'monthly', 'custom'] as const).map((freq) => (
                                <label key={freq} className="flex items-center">
                                    <input
                                        type="radio"
                                        name="frequency"
                                        value={freq}
                                        checked={frequency === freq}
                                        onChange={(e) =>
                                            setFrequency(e.target.value as typeof frequency)
                                        }
                                        className="w-4 h-4 text-blue-600"
                                    />
                                    <span className="ml-2 text-slate-700 dark:text-neutral-300 capitalize">{freq}</span>
                                </label>
                            ))}
                        </div>
                    </div>

                    {/* Day/Time Selection */}
                    <div className="grid grid-cols-2 gap-4">
                        {frequency === 'weekly' && (
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Day of Week
                                </label>
                                <select
                                    value={dayOfWeek}
                                    onChange={(e) => setDayOfWeek(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-md text-slate-900 dark:text-neutral-100 bg-white dark:bg-neutral-700"
                                >
                                    {days.map((day) => (
                                        <option key={day} value={day}>
                                            {day}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}

                        {frequency === 'monthly' && (
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Day of Month
                                </label>
                                <select
                                    value={dayOfMonth}
                                    onChange={(e) => setDayOfMonth(Number(e.target.value))}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-md text-slate-900 dark:text-neutral-100 bg-white dark:bg-neutral-700"
                                >
                                    {Array.from({ length: 31 }, (_, i) => i + 1).map((day) => (
                                        <option key={day} value={day}>
                                            {day}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}

                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                Time (24h)
                            </label>
                            <input
                                type="time"
                                value={time}
                                onChange={(e) => setTime(e.target.value)}
                                className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-md text-slate-900 dark:text-neutral-100 bg-white dark:bg-neutral-700"
                            />
                        </div>
                    </div>

                    {/* Recipients */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                            Email Recipients
                        </label>
                        <div className="flex gap-2 mb-2">
                            <input
                                type="email"
                                value={newRecipient}
                                onChange={(e) => {
                                    setNewRecipient(e.target.value);
                                    setError('');
                                }}
                                placeholder="Enter email address"
                                className="flex-1 px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-md text-slate-900 dark:text-neutral-100 bg-white dark:bg-neutral-700 placeholder-slate-400"
                            />
                            <button
                                onClick={handleAddRecipient}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                            >
                                Add
                            </button>
                        </div>
                        {error && <p className="text-sm text-red-600 mb-2">{error}</p>}
                        <div className="space-y-1">
                            {recipients.map((email) => (
                                <div
                                    key={email}
                                    className="flex justify-between items-center bg-slate-50 dark:bg-neutral-700 px-3 py-2 rounded"
                                >
                                    <span className="text-sm text-slate-900 dark:text-neutral-100">{email}</span>
                                    <button
                                        onClick={() => handleRemoveRecipient(email)}
                                        className="text-red-600 hover:text-red-700 text-sm"
                                    >
                                        Remove
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Formats */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                            Export Formats
                        </label>
                        <div className="grid grid-cols-4 gap-3">
                            {(['pdf', 'csv', 'excel', 'json'] as const).map((fmt) => (
                                <button
                                    key={fmt}
                                    onClick={() => handleToggleFormat(fmt)}
                                    className={`px-3 py-2 rounded border-2 font-medium capitalize transition focus:outline-none focus:ring-2 focus:ring-blue-500 ${formats.includes(fmt)
                                        ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-indigo-400'
                                        : 'border-slate-300 dark:border-neutral-600 text-slate-700 dark:text-neutral-300 hover:border-slate-400 dark:hover:border-slate-500'
                                        }`}
                                >
                                    {fmt}
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Include Charts */}
                    <label className="flex items-center">
                        <input
                            type="checkbox"
                            checked={includeCharts}
                            onChange={(e) => setIncludeCharts(e.target.checked)}
                            className="w-4 h-4 text-blue-600 rounded"
                        />
                        <span className="ml-2 text-sm text-slate-700 dark:text-neutral-300">
                            Include charts and visualizations
                        </span>
                    </label>
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 dark:border-neutral-600 px-6 py-4 flex justify-end gap-3 bg-slate-50 dark:bg-neutral-800">
                    <button
                        onClick={onCancel}
                        className="px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-md text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSave}
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                    >
                        Save Schedule
                    </button>
                </div>
            </div>
        </div>
    );
}
