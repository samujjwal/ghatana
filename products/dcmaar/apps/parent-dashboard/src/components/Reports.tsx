/**
 * Reports Component
 * Main interface for generating and scheduling reports
 */

import { useState, useMemo, memo } from 'react';
import { useAtom } from 'jotai';
import { usageEventsAtom, blockEventsAtom } from '../stores/eventsStore';
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

export const Reports = memo(function Reports() {
  const [usageEvents] = useAtom(usageEventsAtom);
  const [blockEvents] = useAtom(blockEventsAtom);

  const [selectedPeriod, setSelectedPeriod] = useState<ReportPeriod>('weekly');
  const [selectedFormat, setSelectedFormat] = useState<ReportFormat>('pdf');
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');
  const [showPreview, setShowPreview] = useState(false);
  const [schedules, setSchedules] = useState<ReportSchedule[]>(getReportSchedules());

  // Generate report data for preview
  const reportData = useMemo<ReportData | null>(() => {
    if (!showPreview) return null;

    try {
      return generateReportData(usageEvents, blockEvents, {
        period: selectedPeriod,
        format: selectedFormat,
        startDate: customStartDate || undefined,
        endDate: customEndDate || undefined,
      });
    } catch (error) {
      console.error('Error generating report data:', error);
      return null;
    }
  }, [showPreview, usageEvents, blockEvents, selectedPeriod, selectedFormat, customStartDate, customEndDate]);

  const handleGeneratePreview = () => {
    if (selectedPeriod === 'custom' && (!customStartDate || !customEndDate)) {
      alert('Please select both start and end dates for custom period');
      return;
    }
    setShowPreview(true);
  };

  const handleGenerateReport = () => {
    if (!reportData) return;

    try {
      generateReport(reportData, selectedFormat);
      setShowPreview(false);
    } catch (error) {
      console.error('Error generating report:', error);
      alert('Error generating report. Please try again.');
    }
  };

  const handleCancelPreview = () => {
    setShowPreview(false);
  };

  const handleCreateSchedule = () => {
    const schedule = createReportSchedule({
      period: selectedPeriod,
      format: selectedFormat,
      enabled: true,
    });
    
    saveReportSchedule(schedule);
    setSchedules(getReportSchedules());
    alert(`Schedule created! Report will be generated ${selectedPeriod}.`);
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

  if (showPreview && reportData) {
    return (
      <ReportPreview
        reportData={reportData}
        onGenerate={handleGenerateReport}
        onCancel={handleCancelPreview}
      />
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="bg-white rounded-lg shadow-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Analytics Reports</h2>

        {/* Report Configuration */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          {/* Period Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Report Period
            </label>
            <select
              value={selectedPeriod}
              onChange={(e) => setSelectedPeriod(e.target.value as ReportPeriod)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="daily">Daily (Yesterday)</option>
              <option value="weekly">Weekly (Last 7 Days)</option>
              <option value="monthly">Monthly (Last 30 Days)</option>
              <option value="custom">Custom Period</option>
            </select>
          </div>

          {/* Format Selection */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Report Format
            </label>
            <select
              value={selectedFormat}
              onChange={(e) => setSelectedFormat(e.target.value as ReportFormat)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="pdf">PDF Report</option>
              <option value="csv">CSV Export</option>
            </select>
          </div>
        </div>

        {/* Custom Date Range */}
        {selectedPeriod === 'custom' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6 p-4 bg-gray-50 rounded-md">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Start Date
              </label>
              <input
                type="date"
                value={customStartDate}
                onChange={(e) => setCustomStartDate(e.target.value)}
                max={customEndDate || new Date().toISOString().split('T')[0]}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                End Date
              </label>
              <input
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                min={customStartDate}
                max={new Date().toISOString().split('T')[0]}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex space-x-4 mb-8">
          <button
            onClick={handleGeneratePreview}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors font-medium"
          >
            Preview Report
          </button>
          <button
            onClick={handleCreateSchedule}
            className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors font-medium"
          >
            Schedule Report
          </button>
        </div>

        {/* Scheduled Reports */}
        {schedules.length > 0 && (
          <div className="border-t pt-6">
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
