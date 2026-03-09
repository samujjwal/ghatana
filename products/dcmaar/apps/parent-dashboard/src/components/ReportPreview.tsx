/**
 * Report Preview Component
 * Displays a preview of analytics reports before generation
 */

import { memo } from 'react';
import type { ReportData } from '../utils/reportGenerator';

interface ReportPreviewProps {
  reportData: ReportData;
  onGenerate: () => void;
  onCancel: () => void;
}

export const ReportPreview = memo(function ReportPreview({
  reportData,
  onGenerate,
  onCancel,
}: ReportPreviewProps) {
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const formatMinutes = (minutes: number) => {
    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  };

  const getPeriodLabel = (period: string) => {
    switch (period) {
      case 'daily':
        return 'Daily Report';
      case 'weekly':
        return 'Weekly Report';
      case 'monthly':
        return 'Monthly Report';
      case 'custom':
        return 'Custom Period Report';
      default:
        return 'Report';
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-lg p-6 max-w-4xl mx-auto">
      {/* Header */}
      <div className="border-b pb-4 mb-6">
        <h2 className="text-2xl font-bold text-gray-900">{getPeriodLabel(reportData.period)}</h2>
        <p className="text-sm text-gray-600 mt-1">
          {formatDate(reportData.startDate)} - {formatDate(reportData.endDate)}
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        {/* Usage Statistics */}
        <div className="border rounded-lg p-4">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Usage Statistics</h3>
          
          <div className="space-y-3">
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Total Usage Time:</span>
              <span className="font-medium text-gray-900">
                {formatMinutes(reportData.usageStats.totalMinutes)}
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Active Devices:</span>
              <span className="font-medium text-gray-900">
                {reportData.usageStats.uniqueDevices}
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Avg per Device:</span>
              <span className="font-medium text-gray-900">
                {reportData.usageStats.uniqueDevices > 0
                  ? formatMinutes(reportData.usageStats.totalMinutes / reportData.usageStats.uniqueDevices)
                  : '0m'}
              </span>
            </div>
          </div>

          {/* Top Apps */}
          {reportData.usageStats.topApps.length > 0 && (
            <div className="mt-4">
              <h4 className="text-sm font-semibold text-gray-700 mb-2">Top Applications</h4>
              <div className="space-y-2">
                {reportData.usageStats.topApps.slice(0, 5).map((app, index) => (
                  <div key={index} className="flex justify-between items-center text-sm">
                    <span className="text-gray-600 truncate flex-1">{app.app}</span>
                    <span className="font-medium text-blue-600 ml-2">
                      {formatMinutes(app.minutes)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Block Statistics */}
        <div className="border rounded-lg p-4">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Block Statistics</h3>
          
          <div className="space-y-3">
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Total Blocks:</span>
              <span className="font-medium text-red-600">
                {reportData.blockStats.totalBlocks}
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-gray-600">Affected Devices:</span>
              <span className="font-medium text-gray-900">
                {reportData.blockStats.uniqueDevices}
              </span>
            </div>
          </div>

          {/* Top Blocked Items */}
          {reportData.blockStats.topBlockedItems.length > 0 && (
            <div className="mt-4">
              <h4 className="text-sm font-semibold text-gray-700 mb-2">Most Blocked</h4>
              <div className="space-y-2">
                {reportData.blockStats.topBlockedItems.slice(0, 5).map((item, index) => (
                  <div key={index} className="flex justify-between items-center text-sm">
                    <span className="text-gray-600 truncate flex-1">{item.item}</span>
                    <span className="font-medium text-red-600 ml-2">
                      {item.count}x
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Policy Summary (if included) */}
      {reportData.policySummary && (
        <div className="border rounded-lg p-4 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Policy Summary</h3>
          <div className="grid grid-cols-3 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">
                {reportData.policySummary.totalPolicies}
              </div>
              <div className="text-sm text-gray-600">Total Policies</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">
                {reportData.policySummary.activePolicies}
              </div>
              <div className="text-sm text-gray-600">Active</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-gray-900">
                {reportData.policySummary.affectedDevices}
              </div>
              <div className="text-sm text-gray-600">Devices</div>
            </div>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="flex justify-end space-x-4 pt-4 border-t">
        <button
          onClick={onCancel}
          className="px-4 py-2 text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200 transition-colors"
        >
          Cancel
        </button>
        <button
          onClick={onGenerate}
          className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors font-medium"
        >
          Generate Report
        </button>
      </div>

      {/* Print Hint */}
      <div className="mt-4 text-center">
        <p className="text-xs text-gray-500">
          Tip: You can also print this preview using Ctrl+P (Cmd+P on Mac)
        </p>
      </div>
    </div>
  );
});
