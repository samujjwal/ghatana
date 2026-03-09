/**
 * CSV Export Utility
 * Provides functions to export data to CSV format
 */

import type { UsageEvent, BlockEvent } from '../services/websocket.service';
import type { Policy } from '../components/PolicyManagement';

export interface CSVExportOptions {
  filename: string;
  headers: string[];
  data: Record<string, unknown>[];
  delimiter?: string;
}

/**
 * Convert array of objects to CSV string
 */
export function arrayToCSV(headers: string[], data: Record<string, unknown>[], delimiter: string = ','): string {
  const csvRows: string[] = [];
  
  // Add headers
  csvRows.push(headers.join(delimiter));
  
  // Add data rows
  data.forEach(row => {
    const values = headers.map(header => {
      const value = row[header] ?? '';
      // Handle values that contain delimiter, quotes, or newlines
      const stringValue = String(value);
      if (stringValue.includes(delimiter) || stringValue.includes('"') || stringValue.includes('\n')) {
        return `"${stringValue.replace(/"/g, '""')}"`;
      }
      return stringValue;
    });
    csvRows.push(values.join(delimiter));
  });
  
  return csvRows.join('\n');
}

/**
 * Download CSV file
 */
export function downloadCSV(csvContent: string, filename: string): void {
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  
  link.setAttribute('href', url);
  link.setAttribute('download', filename);
  link.style.visibility = 'hidden';
  
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  URL.revokeObjectURL(url);
}

/**
 * Export data to CSV file
 */
export function exportToCSV(options: CSVExportOptions): void {
  const { filename, headers, data, delimiter = ',' } = options;
  const csvContent = arrayToCSV(headers, data, delimiter);
  const filenameWithExtension = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  downloadCSV(csvContent, filenameWithExtension);
}

/**
 * Export usage events to CSV
 */
export function exportUsageEventsToCSV(events: UsageEvent[]): void {
  const headers = ['timestamp', 'device_name', 'device_type', 'item_name', 'session_type', 'duration_minutes'];
  const data = events.map(event => ({
    timestamp: new Date(event.usageSession.timestamp).toLocaleString(),
    device_name: event.device.name,
    device_type: event.device.type,
    item_name: event.usageSession.item_name,
    session_type: event.usageSession.session_type,
    duration_minutes: (event.usageSession.duration_seconds / 60).toFixed(2),
  }));
  
  exportToCSV({
    filename: `usage-events-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
  });
}

/**
 * Export block events to CSV
 */
export function exportBlockEventsToCSV(events: BlockEvent[]): void {
  const headers = ['timestamp', 'device_name', 'device_type', 'blocked_item', 'event_type', 'reason'];
  const data = events.map(event => ({
    timestamp: new Date(event.blockEvent.timestamp).toLocaleString(),
    device_name: event.device.name,
    device_type: event.device.type,
    blocked_item: event.blockEvent.blocked_item,
    event_type: event.blockEvent.event_type,
    reason: event.blockEvent.reason,
  }));
  
  exportToCSV({
    filename: `block-events-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
  });
}

/**
 * Export policies to CSV
 */
export function exportPoliciesToCSV(policies: Policy[]): void {
  const headers = ['name', 'type', 'device_count', 'created_at', 'restrictions'];
  const data = policies.map(policy => ({
    name: policy.name,
    type: policy.type,
    device_count: policy.deviceIds.length,
    created_at: new Date(policy.createdAt).toLocaleString(),
    restrictions: JSON.stringify(policy.restrictions),
  }));
  
  exportToCSV({
    filename: `policies-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
  });
}
