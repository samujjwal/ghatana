/**
 * PDF Export Utility
 * Provides functions to export data to PDF format using jsPDF
 */

import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import type { UsageEvent, BlockEvent } from '../services/websocket.service';
import type { Policy } from '../components/PolicyManagement';

export interface PDFExportOptions {
  title: string;
  filename: string;
  headers: string[];
  data: string[][];
  orientation?: 'portrait' | 'landscape';
}

/**
 * Export data to PDF file
 */
export function exportToPDF(options: PDFExportOptions): void {
  const { title, filename, headers, data, orientation = 'portrait' } = options;
  
  // Create PDF document
  const doc = new jsPDF({
    orientation,
    unit: 'mm',
    format: 'a4',
  });
  
  // Add title
  doc.setFontSize(16);
  doc.text(title, 14, 15);
  
  // Add timestamp
  doc.setFontSize(10);
  doc.text(`Generated: ${new Date().toLocaleString()}`, 14, 22);
  
  // Add table
  autoTable(doc, {
    head: [headers],
    body: data,
    startY: 30,
    theme: 'striped',
    headStyles: {
      fillColor: [59, 130, 246], // blue-500
      textColor: [255, 255, 255],
      fontStyle: 'bold',
    },
    styles: {
      fontSize: 9,
      cellPadding: 3,
    },
    alternateRowStyles: {
      fillColor: [249, 250, 251], // gray-50
    },
  });
  
  // Save PDF
  const filenameWithExtension = filename.endsWith('.pdf') ? filename : `${filename}.pdf`;
  doc.save(filenameWithExtension);
}

/**
 * Export usage events to PDF
 */
export function exportUsageEventsToPDF(events: UsageEvent[]): void {
  const headers = ['Timestamp', 'Device', 'Type', 'App/Website', 'Session Type', 'Duration (min)'];
  const data: string[][] = events.map(event => [
    new Date(event.usageSession.timestamp).toLocaleString(),
    event.device.name,
    event.device.type,
    event.usageSession.item_name,
    event.usageSession.session_type,
    (event.usageSession.duration_seconds / 60).toFixed(2),
  ]);
  
  exportToPDF({
    title: 'Usage Events Report',
    filename: `usage-events-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
    orientation: 'landscape',
  });
}

/**
 * Export block events to PDF
 */
export function exportBlockEventsToPDF(events: BlockEvent[]): void {
  const headers = ['Timestamp', 'Device', 'Type', 'Blocked Item', 'Event Type', 'Reason'];
  const data: string[][] = events.map(event => [
    new Date(event.blockEvent.timestamp).toLocaleString(),
    event.device.name,
    event.device.type,
    event.blockEvent.blocked_item,
    event.blockEvent.event_type,
    event.blockEvent.reason,
  ]);
  
  exportToPDF({
    title: 'Block Events Report',
    filename: `block-events-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
    orientation: 'landscape',
  });
}

/**
 * Export policies to PDF
 */
export function exportPoliciesToPDF(policies: Policy[]): void {
  const headers = ['Policy Name', 'Type', 'Devices', 'Created', 'Restrictions'];
  const data: string[][] = policies.map(policy => [
    policy.name,
    policy.type,
    policy.deviceIds.length.toString(),
    new Date(policy.createdAt).toLocaleDateString(),
    formatRestrictions(policy.restrictions),
  ]);
  
  exportToPDF({
    title: 'Policies Report',
    filename: `policies-${new Date().toISOString().split('T')[0]}`,
    headers,
    data,
    orientation: 'portrait',
  });
}

/**
 * Export analytics summary to PDF
 */
export function exportAnalyticsSummaryToPDF(
  usageStats: Record<string, unknown>,
  blockStats: Record<string, unknown>,
  timeRange: string
): void {
  const doc = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4',
  });
  
  // Title
  doc.setFontSize(18);
  doc.text('Analytics Summary Report', 14, 15);
  
  // Metadata
  doc.setFontSize(10);
  doc.text(`Time Range: ${timeRange}`, 14, 22);
  doc.text(`Generated: ${new Date().toLocaleString()}`, 14, 27);
  
  // Usage Statistics Section
  doc.setFontSize(14);
  doc.text('Usage Statistics', 14, 40);
  
  doc.setFontSize(10);
  let yPos = 48;
  const totalMinutes = typeof usageStats.totalMinutes === 'number' ? usageStats.totalMinutes : 0;
  const uniqueDevices = typeof usageStats.uniqueDevices === 'number' ? usageStats.uniqueDevices : 0;
  doc.text(`Total Usage: ${totalMinutes} minutes`, 20, yPos);
  yPos += 6;
  doc.text(`Unique Devices: ${uniqueDevices}`, 20, yPos);
  yPos += 6;
  doc.text(`Average per Device: ${uniqueDevices > 0 ? Math.round(totalMinutes / uniqueDevices) : 0} min`, 20, yPos);
  
  // Top Apps Table
  const topApps = usageStats.topApps as Array<Record<string, unknown>> | undefined;
  if (Array.isArray(topApps) && topApps.length > 0) {
    yPos += 10;
    doc.setFontSize(12);
    doc.text('Top Apps/Websites', 14, yPos);
    
    autoTable(doc, {
      head: [['App/Website', 'Usage (minutes)']],
      body: topApps.map(app => [String(app.app ?? ''), String((app.minutes as number)?.toFixed(2) ?? '')]),
      startY: yPos + 5,
      theme: 'striped',
      headStyles: {
        fillColor: [59, 130, 246],
        textColor: [255, 255, 255],
      },
    });
    
    const docWithTable = doc as unknown as Record<string, unknown>;
    yPos = (docWithTable.lastAutoTable as Record<string, unknown>)?.finalY as number + 10 || yPos + 10;
  }
  
  // Block Statistics Section
  doc.setFontSize(14);
  doc.text('Block Statistics', 14, yPos);
  
  doc.setFontSize(10);
  yPos += 8;
  const totalBlocks = typeof blockStats.totalBlocks === 'number' ? blockStats.totalBlocks : 0;
  const blockUniqueDevices = typeof blockStats.uniqueDevices === 'number' ? blockStats.uniqueDevices : 0;
  doc.text(`Total Blocks: ${totalBlocks}`, 20, yPos);
  yPos += 6;
  doc.text(`Affected Devices: ${blockUniqueDevices}`, 20, yPos);
  
  // Top Blocked Items
  const topBlockedItems = blockStats.topBlockedItems as Array<Record<string, unknown>> | undefined;
  if (Array.isArray(topBlockedItems) && topBlockedItems.length > 0) {
    yPos += 10;
    doc.setFontSize(12);
    doc.text('Most Blocked Items', 14, yPos);
    
    autoTable(doc, {
      head: [['Item', 'Block Count']],
      body: topBlockedItems.slice(0, 10).map(item => [String(item.item ?? ''), String(item.count ?? '')]),
      startY: yPos + 5,
      theme: 'striped',
      headStyles: {
        fillColor: [220, 38, 38], // red-600
        textColor: [255, 255, 255],
      },
    });
  }
  
  // Save
  doc.save(`analytics-summary-${new Date().toISOString().split('T')[0]}.pdf`);
}

/**
 * Format restrictions object for display
 */
function formatRestrictions(restrictions: Policy['restrictions']): string {
  const parts: string[] = [];
  
  if (restrictions.maxUsageMinutes) {
    parts.push(`Max: ${restrictions.maxUsageMinutes}min`);
  }
  if (restrictions.blockedCategories && restrictions.blockedCategories.length > 0) {
    parts.push(`Categories: ${restrictions.blockedCategories.length}`);
  }
  if (restrictions.blockedApps && restrictions.blockedApps.length > 0) {
    parts.push(`Apps: ${restrictions.blockedApps.length}`);
  }
  if (restrictions.allowedHours) {
    parts.push(`Hours: ${restrictions.allowedHours.start}-${restrictions.allowedHours.end}`);
  }
  
  return parts.join(', ') || 'None';
}
