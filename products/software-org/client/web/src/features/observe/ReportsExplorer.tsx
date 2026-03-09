import { useState } from "react";
import { useNavigate } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useReports, useGenerateReport, type ReportGenerateBody, type ReportResponse } from '@/hooks/useObserveApi';
import { FileText, Calendar, Plus, TrendingUp, AlertCircle, Package, Clock, Mail, Send, FileDown } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * Reports Explorer
 *
 * <p><b>Purpose</b><br>
 * View, generate, and export reliability and compliance reports.
 * Provides leadership and compliance teams with structured insights.
 *
 * <p><b>Features</b><br>
 * - Report type selection (reliability, change-management, incident-summary)
 * - Scope filtering (tenant, service, team)
 * - Date range selection
 * - Report generation with real-time progress
 * - Report list with export capabilities
 *
 * @doc.type component
 * @doc.purpose Report generation and management
 * @doc.layer product
 * @doc.pattern Page
 */
export function ReportsExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const [showGenerateModal, setShowGenerateModal] = useState(false);
    const [showScheduleModal, setShowScheduleModal] = useState(false);
    const [activeTab, setActiveTab] = useState<'reports' | 'scheduled'>('reports');
    
    const tenantId = selectedTenant || 'acme-payments-id';
    
    const { data: reportsData, isLoading, error } = useReports(tenantId);
    const generateMutation = useGenerateReport();

    const reports = reportsData?.data || [];

    // Form state for report generation
    const [reportType, setReportType] = useState<'reliability' | 'change-management' | 'incident-summary'>('reliability');
    const [scope, setScope] = useState('tenant');
    const [startDate, setStartDate] = useState(
        new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
    );
    const [endDate, setEndDate] = useState(new Date().toISOString().split('T')[0]);

    // Scheduling state
    const [scheduleName, setScheduleName] = useState('');
    const [scheduleFrequency, setScheduleFrequency] = useState<'daily' | 'weekly' | 'monthly'>('weekly');
    const [scheduleRecipients, setScheduleRecipients] = useState('');
    const [deliveryMethod, setDeliveryMethod] = useState<'email' | 'slack'>('email');

    // Mock scheduled reports (TODO: Replace with actual API)
    const scheduledReports = [
        {
            id: 'sched-1',
            name: 'Weekly Reliability Report',
            type: 'reliability',
            frequency: 'weekly',
            recipients: 'team@acme.com',
            deliveryMethod: 'email',
            nextRun: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000),
            lastRun: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
        },
        {
            id: 'sched-2',
            name: 'Monthly Incident Summary',
            type: 'incident-summary',
            frequency: 'monthly',
            recipients: '#incidents',
            deliveryMethod: 'slack',
            nextRun: new Date(Date.now() + 25 * 24 * 60 * 60 * 1000),
            lastRun: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
        },
    ];

    const reportTypes = [
        {
            value: 'reliability' as const,
            label: 'Reliability Report',
            description: 'Availability, MTTR, incident metrics',
            icon: <TrendingUp className="h-5 w-5" />,
        },
        {
            value: 'change-management' as const,
            label: 'Change Management',
            description: 'Deployment frequency, lead time, failure rate',
            icon: <Package className="h-5 w-5" />,
        },
        {
            value: 'incident-summary' as const,
            label: 'Incident Summary',
            description: 'Incident count, severity, resolution time',
            icon: <AlertCircle className="h-5 w-5" />,
        },
    ];

    const handleGenerateReport = async () => {
        const body: ReportGenerateBody = {
            tenantId,
            type: reportType,
            scope,
            startDate,
            endDate,
        };

        try {
            await generateMutation.mutateAsync(body);
            setShowGenerateModal(false);
        } catch (error) {
            console.error('Failed to generate report:', error);
        }
    };

    const handleExportPDF = (report: ReportResponse) => {
        // TODO: Implement actual PDF generation with react-pdf or similar
        console.log('Exporting report as PDF:', report.id);
        
        // Mock PDF download
        const content = `Report: ${report.name}\nType: ${report.type}\nPeriod: ${report.period.start} - ${report.period.end}\n\nGenerated on: ${new Date(report.createdAt).toLocaleString()}`;
        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${report.name.replace(/\s+/g, '_')}_${new Date().toISOString().split('T')[0]}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    const handleScheduleReport = () => {
        // TODO: Implement actual scheduling API
        console.log('Scheduling report:', {
            name: scheduleName,
            type: reportType,
            frequency: scheduleFrequency,
            recipients: scheduleRecipients,
            deliveryMethod,
            scope,
        });
        setShowScheduleModal(false);
        // Reset form
        setScheduleName('');
        setScheduleRecipients('');
    };

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load reports: {error.message}
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Reports</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Generate and export reliability and compliance reports
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => setShowScheduleModal(true)}
                        className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        <Clock className="h-4 w-4" />
                        Schedule Report
                    </button>
                    <button
                        onClick={() => setShowGenerateModal(true)}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        Generate Report
                    </button>
                </div>
            </div>

            {/* Tabs */}
            <div className="border-b border-slate-200 dark:border-slate-700">
                <div className="flex gap-6">
                    <button
                        onClick={() => setActiveTab('reports')}
                        className={`pb-3 border-b-2 font-medium text-sm transition-colors ${
                            activeTab === 'reports'
                                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                                : 'border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100'
                        }`}
                    >
                        <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4" />
                            Report History
                            <Badge variant="neutral">{reports.length}</Badge>
                        </div>
                    </button>
                    <button
                        onClick={() => setActiveTab('scheduled')}
                        className={`pb-3 border-b-2 font-medium text-sm transition-colors ${
                            activeTab === 'scheduled'
                                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                                : 'border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100'
                        }`}
                    >
                        <div className="flex items-center gap-2">
                            <Clock className="h-4 w-4" />
                            Scheduled Reports
                            <Badge variant="neutral">{scheduledReports.length}</Badge>
                        </div>
                    </button>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Reports" value={reports.length} icon={<FileText className="h-5 w-5" />} />
                <StatCard label="This Month" value={reports.filter(r => {
                    const date = new Date(r.createdAt);
                    const now = new Date();
                    return date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
                }).length} icon={<Calendar className="h-5 w-5" />} />
                <StatCard label="Scheduled" value={scheduledReports.length} icon={<Clock className="h-5 w-5 text-blue-500" />} />
                <StatCard label="Incidents" value={reports.filter(r => r.type === 'incident-summary').length} icon={<AlertCircle className="h-5 w-5 text-amber-500" />} />
            </div>

            {/* Content based on active tab */}
            {activeTab === 'reports' ? (
                // Reports List
                isLoading ? (
                    <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                        Loading reports...
                    </div>
                ) : reports.length === 0 ? (
                    <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                        <FileText className="h-12 w-12 text-slate-400 mx-auto mb-4" />
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                            No Reports Yet
                        </h3>
                        <p className="text-sm text-slate-500 dark:text-neutral-500 mb-4">
                            Generate your first report to get started
                        </p>
                        <button
                            onClick={() => setShowGenerateModal(true)}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                        >
                            <Plus className="h-4 w-4" />
                            Generate Report
                        </button>
                    </div>
                ) : (
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 overflow-hidden">
                    <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-700">
                        <thead className="bg-slate-50 dark:bg-slate-800">
                            <tr>
                                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-600 dark:text-neutral-400 uppercase tracking-wider">
                                    Report
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-600 dark:text-neutral-400 uppercase tracking-wider">
                                    Type
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-600 dark:text-neutral-400 uppercase tracking-wider">
                                    Period
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-semibold text-slate-600 dark:text-neutral-400 uppercase tracking-wider">
                                    Generated
                                </th>
                                <th className="px-6 py-3 text-right text-xs font-semibold text-slate-600 dark:text-neutral-400 uppercase tracking-wider">
                                    Actions
                                </th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
                            {reports.map((report) => (
                                <tr 
                                    key={report.id} 
                                    onClick={() => navigate(`/observe/report-detail/${report.id}`)}
                                    className="hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer"
                                >
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-3">
                                            <FileText className="h-5 w-5 text-slate-400" />
                                            <div>
                                                <div className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {report.name}
                                                </div>
                                                <div className="text-xs text-slate-500 dark:text-neutral-500">
                                                    {report.scope}
                                                </div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4">
                                        <Badge variant="neutral">
                                            {report.type.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                                        </Badge>
                                    </td>
                                    <td className="px-6 py-4 text-sm text-slate-600 dark:text-neutral-400">
                                        {new Date(report.period.start).toLocaleDateString()} - {new Date(report.period.end).toLocaleDateString()}
                                    </td>
                                    <td className="px-6 py-4 text-sm text-slate-600 dark:text-neutral-400">
                                        {new Date(report.createdAt).toLocaleDateString()}
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="flex items-center justify-end gap-2">
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleExportPDF(report);
                                                }}
                                                className="inline-flex items-center gap-2 px-3 py-1.5 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-md transition-colors"
                                                title="Export as PDF"
                                            >
                                                <FileDown className="h-4 w-4" />
                                                PDF
                                            </button>
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    // TODO: Email functionality
                                                }}
                                                className="inline-flex items-center gap-2 px-3 py-1.5 text-sm text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md transition-colors"
                                                title="Send via email"
                                            >
                                                <Mail className="h-4 w-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )
            ) : (
                // Scheduled Reports Tab
                scheduledReports.length === 0 ? (
                    <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                        <Clock className="h-12 w-12 text-slate-400 mx-auto mb-4" />
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                            No Scheduled Reports
                        </h3>
                        <p className="text-sm text-slate-500 dark:text-neutral-500 mb-4">
                            Set up automatic report generation and delivery
                        </p>
                        <button
                            onClick={() => setShowScheduleModal(true)}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                        >
                            <Clock className="h-4 w-4" />
                            Schedule Report
                        </button>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {scheduledReports.map((schedule) => (
                            <div 
                                key={schedule.id}
                                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6 hover:border-slate-300 dark:hover:border-slate-600 transition-colors"
                            >
                                <div className="flex items-start justify-between">
                                    <div className="flex-1">
                                        <div className="flex items-center gap-3 mb-2">
                                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                                {schedule.name}
                                            </h3>
                                            <Badge variant="neutral">
                                                {schedule.type.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}
                                            </Badge>
                                        </div>
                                        
                                        <div className="grid grid-cols-2 gap-4 mt-4">
                                            <div>
                                                <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Frequency</div>
                                                <div className="text-sm font-medium text-slate-900 dark:text-neutral-100 capitalize">
                                                    {schedule.frequency}
                                                </div>
                                            </div>
                                            <div>
                                                <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Delivery</div>
                                                <div className="flex items-center gap-2 text-sm font-medium text-slate-900 dark:text-neutral-100">
                                                    {schedule.deliveryMethod === 'email' ? (
                                                        <>
                                                            <Mail className="h-4 w-4" />
                                                            {schedule.recipients}
                                                        </>
                                                    ) : (
                                                        <>
                                                            <Send className="h-4 w-4" />
                                                            {schedule.recipients}
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                            <div>
                                                <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Next Run</div>
                                                <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                                    {schedule.nextRun.toLocaleDateString()} at {schedule.nextRun.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                </div>
                                            </div>
                                            <div>
                                                <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Last Run</div>
                                                <div className="text-sm text-slate-600 dark:text-neutral-400">
                                                    {schedule.lastRun.toLocaleDateString()}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <div className="flex items-center gap-2">
                                        <button
                                            onClick={() => {
                                                // TODO: Pause/Resume schedule
                                            }}
                                            className="px-3 py-1.5 text-sm text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md transition-colors"
                                        >
                                            Pause
                                        </button>
                                        <button
                                            onClick={() => {
                                                // TODO: Delete schedule
                                            }}
                                            className="px-3 py-1.5 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors"
                                        >
                                            Delete
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )
            )}

            {/* Generate Report Modal */}
            {showGenerateModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6">
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
                            Generate Report
                        </h2>

                        <div className="space-y-6">
                            {/* Report Type */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-3">
                                    Report Type
                                </label>
                                <div className="grid grid-cols-1 gap-3">
                                    {reportTypes.map((type) => (
                                        <button
                                            key={type.value}
                                            onClick={() => setReportType(type.value)}
                                            className={`flex items-start gap-3 p-4 border rounded-lg text-left transition-all ${
                                                reportType === type.value
                                                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                                    : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600'
                                            }`}
                                        >
                                            <div className={`mt-0.5 ${reportType === type.value ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400'}`}>
                                                {type.icon}
                                            </div>
                                            <div className="flex-1">
                                                <div className={`font-semibold ${reportType === type.value ? 'text-blue-900 dark:text-blue-100' : 'text-slate-900 dark:text-neutral-100'}`}>
                                                    {type.label}
                                                </div>
                                                <div className="text-sm text-slate-500 dark:text-neutral-500 mt-1">
                                                    {type.description}
                                                </div>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Scope */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Scope
                                </label>
                                <select
                                    value={scope}
                                    onChange={(e) => setScope(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="tenant">Entire Tenant</option>
                                    <option value="department">Department</option>
                                    <option value="team">Team</option>
                                    <option value="service">Service</option>
                                </select>
                            </div>

                            {/* Date Range */}
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                        Start Date
                                    </label>
                                    <input
                                        type="date"
                                        value={startDate}
                                        onChange={(e) => setStartDate(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                        End Date
                                    </label>
                                    <input
                                        type="date"
                                        value={endDate}
                                        onChange={(e) => setEndDate(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Actions */}
                        <div className="flex items-center justify-end gap-3 mt-6 pt-6 border-t border-slate-200 dark:border-slate-700">
                            <button
                                onClick={() => setShowGenerateModal(false)}
                                className="px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleGenerateReport}
                                disabled={generateMutation.isPending}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                            >
                                {generateMutation.isPending ? 'Generating...' : 'Generate Report'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Schedule Report Modal */}
            {showScheduleModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6 max-h-[90vh] overflow-y-auto">
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
                            Schedule Report
                        </h2>

                        <div className="space-y-6">
                            {/* Schedule Name */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Schedule Name
                                </label>
                                <input
                                    type="text"
                                    value={scheduleName}
                                    onChange={(e) => setScheduleName(e.target.value)}
                                    placeholder="e.g., Weekly Reliability Report"
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                />
                            </div>

                            {/* Report Type */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-3">
                                    Report Type
                                </label>
                                <div className="grid grid-cols-1 gap-3">
                                    {reportTypes.map((type) => (
                                        <button
                                            key={type.value}
                                            onClick={() => setReportType(type.value)}
                                            className={`flex items-start gap-3 p-4 border rounded-lg text-left transition-all ${
                                                reportType === type.value
                                                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                                    : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600'
                                            }`}
                                        >
                                            <div className={`mt-0.5 ${reportType === type.value ? 'text-blue-600 dark:text-blue-400' : 'text-slate-400'}`}>
                                                {type.icon}
                                            </div>
                                            <div className="flex-1">
                                                <div className={`font-semibold ${reportType === type.value ? 'text-blue-900 dark:text-blue-100' : 'text-slate-900 dark:text-neutral-100'}`}>
                                                    {type.label}
                                                </div>
                                                <div className="text-sm text-slate-500 dark:text-neutral-500 mt-1">
                                                    {type.description}
                                                </div>
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Frequency */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Frequency
                                </label>
                                <div className="grid grid-cols-3 gap-3">
                                    {(['daily', 'weekly', 'monthly'] as const).map((freq) => (
                                        <button
                                            key={freq}
                                            onClick={() => setScheduleFrequency(freq)}
                                            className={`px-4 py-2 border rounded-md text-sm font-medium capitalize transition-all ${
                                                scheduleFrequency === freq
                                                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-900 dark:text-blue-100'
                                                    : 'border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:border-slate-400 dark:hover:border-slate-500'
                                            }`}
                                        >
                                            {freq}
                                        </button>
                                    ))}
                                </div>
                            </div>

                            {/* Delivery Method */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Delivery Method
                                </label>
                                <div className="grid grid-cols-2 gap-3">
                                    <button
                                        onClick={() => setDeliveryMethod('email')}
                                        className={`flex items-center justify-center gap-2 px-4 py-3 border rounded-lg transition-all ${
                                            deliveryMethod === 'email'
                                                ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-900 dark:text-blue-100'
                                                : 'border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:border-slate-400 dark:hover:border-slate-500'
                                        }`}
                                    >
                                        <Mail className="h-5 w-5" />
                                        Email
                                    </button>
                                    <button
                                        onClick={() => setDeliveryMethod('slack')}
                                        className={`flex items-center justify-center gap-2 px-4 py-3 border rounded-lg transition-all ${
                                            deliveryMethod === 'slack'
                                                ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-900 dark:text-blue-100'
                                                : 'border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:border-slate-400 dark:hover:border-slate-500'
                                        }`}
                                    >
                                        <Send className="h-5 w-5" />
                                        Slack
                                    </button>
                                </div>
                            </div>

                            {/* Recipients */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    {deliveryMethod === 'email' ? 'Email Recipients' : 'Slack Channel'}
                                </label>
                                <input
                                    type="text"
                                    value={scheduleRecipients}
                                    onChange={(e) => setScheduleRecipients(e.target.value)}
                                    placeholder={deliveryMethod === 'email' ? 'team@acme.com, manager@acme.com' : '#incidents'}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                />
                                <p className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                    {deliveryMethod === 'email' ? 'Comma-separated email addresses' : 'Slack channel name (e.g., #incidents)'}
                                </p>
                            </div>

                            {/* Scope */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Scope
                                </label>
                                <select
                                    value={scope}
                                    onChange={(e) => setScope(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100"
                                >
                                    <option value="tenant">Entire Tenant</option>
                                    <option value="department">Department</option>
                                    <option value="team">Team</option>
                                    <option value="service">Service</option>
                                </select>
                            </div>
                        </div>

                        {/* Actions */}
                        <div className="flex items-center justify-end gap-3 mt-6 pt-6 border-t border-slate-200 dark:border-slate-700">
                            <button
                                onClick={() => {
                                    setShowScheduleModal(false);
                                    setScheduleName('');
                                    setScheduleRecipients('');
                                }}
                                className="px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleScheduleReport}
                                disabled={!scheduleName || !scheduleRecipients}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
                            >
                                Create Schedule
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// Helper component
function StatCard({ label, value, icon }: { label: string; value: number; icon: React.ReactNode }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{label}</div>
                </div>
                <div className="text-slate-400 dark:text-neutral-500">
                    {icon}
                </div>
            </div>
        </div>
    );
}
