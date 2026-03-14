import { useState, useMemo, memo } from 'react';
import { useAtom } from 'jotai';
import { usageEventsAtom, blockEventsAtom } from '../stores/eventsStore';
import { DynamicForm, type FieldConfig } from '@ghatana/design-system';
import { ReportPreview } from './ReportPreview';
import {
  type ReportPeriod,
  type ReportFormat,
  type ReportData,
  generateReportData,
  generateReport,
  getReportSchedules,
  saveReportSchedule,
  deleteReportSchedule,
  createReportSchedule,
  type ReportSchedule,
} from '../utils/reportGenerator';

/**
 * ReportsNew - Report generation interface using DynamicForm
 * 
 * Provides report configuration and generation using the generic DynamicForm component.
 * 
 * Features:
 * - Report period selection (daily, weekly, monthly, custom)
 * - Format selection (PDF, CSV)
 * - Custom date range picker
 * - Preview functionality
 * - Report scheduling
 * 
 * @example
 * ```tsx
 * <ReportsNew />
 * ```
 */

interface ReportFormData {
  period: ReportPeriod;
  format: ReportFormat;
  customStartDate?: string;
  customEndDate?: string;
}

export const ReportsNew = memo(function ReportsNew() {
  const [usageEvents] = useAtom(usageEventsAtom);
  const [blockEvents] = useAtom(blockEventsAtom);
  const [showPreview, setShowPreview] = useState(false);
  const [schedules, setSchedules] = useState<ReportSchedule[]>(getReportSchedules());
  const [formData, setFormData] = useState<ReportFormData>({
    period: 'weekly',
    format: 'pdf',
    customStartDate: '',
    customEndDate: '',
  });

  // Generate report data for preview
  const reportData = useMemo<ReportData | null>(() => {
    if (!showPreview) return null;

    try {
      return generateReportData(usageEvents, blockEvents, {
        period: formData.period,
        format: formData.format,
        startDate: formData.customStartDate || undefined,
        endDate: formData.customEndDate || undefined,
      });
    } catch (error) {
      console.error('Error generating report data:', error);
      return null;
    }
  }, [showPreview, usageEvents, blockEvents, formData]);

  // Form fields configuration
  const formFields: FieldConfig<ReportFormData>[] = [
    {
      name: 'period',
      label: 'Report Period',
      type: 'select',
      required: true,
      options: [
        { label: 'Daily (Yesterday)', value: 'daily' },
        { label: 'Weekly (Last 7 Days)', value: 'weekly' },
        { label: 'Monthly (Last 30 Days)', value: 'monthly' },
        { label: 'Custom Period', value: 'custom' },
      ],
      helpText: 'Select the time period for the report',
    },
    {
      name: 'format',
      label: 'Report Format',
      type: 'select',
      required: true,
      options: [
        { label: 'PDF Report', value: 'pdf' },
        { label: 'CSV Export', value: 'csv' },
      ],
      helpText: 'Choose the export format',
    },
    ...(formData.period === 'custom'
      ? [
          {
            name: 'customStartDate' as keyof ReportFormData,
            label: 'Start Date',
            type: 'date' as const,
            required: true,
          },
          {
            name: 'customEndDate' as keyof ReportFormData,
            label: 'End Date',
            type: 'date' as const,
            required: true,
          },
        ]
      : []),
  ];

  const handlePreview = (data: ReportFormData) => {
    if (data.period === 'custom' && (!data.customStartDate || !data.customEndDate)) {
      alert('Please select both start and end dates for custom period');
      return;
    }
    setFormData(data);
    setShowPreview(true);
  };

  const handleGenerate = () => {
    if (!reportData) return;

    try {
      generateReport(reportData, formData.format);
      setShowPreview(false);
    } catch (error) {
      console.error('Error generating report:', error);
      alert('Error generating report. Please try again.');
    }
  };

  const handleSchedule = (data: ReportFormData) => {
    const schedule = createReportSchedule({
      period: data.period,
      format: data.format,
      enabled: true,
    });
    
    saveReportSchedule(schedule);
    setSchedules(getReportSchedules());
    alert(`Schedule created! Report will be generated ${data.period}.`);
  };

  const handleToggleSchedule = (scheduleId: string) => {
    const schedule = schedules.find(s => s.id === scheduleId);
    if (!schedule) return;

    schedule.enabled = !schedule.enabled;
    saveReportSchedule(schedule);
    setSchedules(getReportSchedules());
  };

  const handleDeleteSchedule = (scheduleId: string) => {
    if (!confirm('Are you sure you want to delete this schedule?')) return;
    
    deleteReportSchedule(scheduleId);
    setSchedules(getReportSchedules());
  };

  const getPeriodLabel = (period: ReportPeriod) => {
    switch (period) {
      case 'daily': return 'Daily';
      case 'weekly': return 'Weekly';
      case 'monthly': return 'Monthly';
      case 'custom': return 'Custom';
      default: return period;
    }
  };

  // Show preview if requested
  if (showPreview && reportData) {
    return (
      <ReportPreview
        reportData={reportData}
        onGenerate={handleGenerate}
        onCancel={() => setShowPreview(false)}
      />
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Analytics Reports</h2>

        {/* Report Configuration Form */}
        <DynamicForm
          fields={formFields}
          onSubmit={handlePreview}
          submitText="Preview Report"
          initialData={formData}
        />

        {/* Schedule Button */}
        <div className="mt-4">
          <button
            onClick={() => handleSchedule(formData)}
            className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors font-medium"
          >
            Schedule Report
          </button>
        </div>

        {/* Scheduled Reports Section */}
        {schedules.length > 0 && (
          <div className="mt-8 border-t pt-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Scheduled Reports</h3>
            <div className="space-y-3">
              {schedules.map((schedule) => (
                <div
                  key={schedule.id}
                  className="flex items-center justify-between p-4 border rounded-md"
                >
                  <div className="flex items-center space-x-4">
                    <input
                      type="checkbox"
                      checked={schedule.enabled}
                      onChange={() => handleToggleSchedule(schedule.id)}
                      className="h-4 w-4 text-blue-600 rounded focus:ring-blue-500"
                    />
                    <div>
                      <div className="font-medium text-gray-900">
                        {getPeriodLabel(schedule.period)} {schedule.format.toUpperCase()} Report
                      </div>
                      {schedule.nextRun && (
                        <div className="text-sm text-gray-500">
                          Next run: {new Date(schedule.nextRun).toLocaleDateString()}
                        </div>
                      )}
                    </div>
                  </div>
                  <button
                    onClick={() => handleDeleteSchedule(schedule.id)}
                    className="px-3 py-1 text-red-600 hover:bg-red-50 rounded-md transition-colors"
                  >
                    Delete
                  </button>
                </div>
              ))}
            </div>
            <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-md">
              <p className="text-sm text-yellow-800">
                <strong>Note:</strong> Scheduled reports are currently stored locally. 
                Backend integration for automatic email delivery is coming soon.
              </p>
            </div>
          </div>
        )}

        {/* Help Text */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-md">
          <h4 className="text-sm font-semibold text-blue-900 mb-2">Report Types</h4>
          <ul className="text-sm text-blue-800 space-y-1">
            <li><strong>Daily:</strong> Previous day's activity (midnight to midnight)</li>
            <li><strong>Weekly:</strong> Last 7 days of activity</li>
            <li><strong>Monthly:</strong> Last 30 days of activity</li>
            <li><strong>Custom:</strong> Specify your own date range</li>
          </ul>
        </div>
      </div>
    </div>
  );
});
