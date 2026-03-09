/**
 * Export Utilities
 * 
 * Export charts and data in various formats (PNG, SVG, CSV, JSON, PDF).
 */

import type { TimeSeriesData, ChartConfig, ExportOptions } from '../types';

/**
 * Export chart to specified format
 */
export async function exportChart(
  data: TimeSeriesData[],
  config: ChartConfig,
  options: ExportOptions
): Promise<void> {
  const { format, filename = 'chart', quality = 1.0 } = options;

  switch (format) {
    case 'png':
      await exportToPNG(data, config, filename, quality);
      break;
    case 'svg':
      await exportToSVG(data, config, filename);
      break;
    case 'csv':
      await exportToCSV(data, filename);
      break;
    case 'json':
      await exportToJSON(data, config, filename, options.includeMetadata);
      break;
    case 'pdf':
      await exportToPDF(data, config, filename);
      break;
    default:
      throw new Error(`Unsupported export format: ${format}`);
  }
}

/**
 * Export chart as PNG image
 */
async function exportToPNG(
  data: TimeSeriesData[],
  config: ChartConfig,
  filename: string,
  quality: number
): Promise<void> {
  // Find the chart container
  const chartElement = document.querySelector('.recharts-wrapper');
  if (!chartElement) {
    throw new Error('Chart element not found');
  }

  // Use html2canvas for rendering
  const { default: html2canvas } = await import('html2canvas');
  const canvas = await html2canvas(chartElement as HTMLElement, {
    backgroundColor: null,
    scale: quality * 2,
  });

  // Download as PNG
  const link = document.createElement('a');
  link.download = `${filename}.png`;
  link.href = canvas.toDataURL('image/png');
  link.click();
}

/**
 * Export chart as SVG
 */
async function exportToSVG(
  data: TimeSeriesData[],
  config: ChartConfig,
  filename: string
): Promise<void> {
  // Find the SVG element
  const svgElement = document.querySelector('.recharts-wrapper svg');
  if (!svgElement) {
    throw new Error('SVG element not found');
  }

  // Clone and serialize SVG
  const svgClone = svgElement.cloneNode(true) as SVGElement;
  const serializer = new XMLSerializer();
  const svgString = serializer.serializeToString(svgClone);

  // Create blob and download
  const blob = new Blob([svgString], { type: 'image/svg+xml' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.download = `${filename}.svg`;
  link.href = url;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Export data as CSV
 */
async function exportToCSV(data: TimeSeriesData[], filename: string): Promise<void> {
  // Create CSV header
  const headers = ['Timestamp', ...data.map(series => series.name)];
  const csvRows = [headers.join(',')];

  // Find all unique timestamps
  const timestamps = new Set<number>();
  data.forEach(series => {
    series.data.forEach(point => timestamps.add(point.timestamp));
  });

  // Create CSV rows
  Array.from(timestamps)
    .sort((a, b) => a - b)
    .forEach(timestamp => {
      const row = [new Date(timestamp).toISOString()];
      data.forEach(series => {
        const point = series.data.find(p => p.timestamp === timestamp);
        row.push(point ? String(point.value) : '');
      });
      csvRows.push(row.join(','));
    });

  // Create blob and download
  const csv = csvRows.join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.download = `${filename}.csv`;
  link.href = url;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Export data as JSON
 */
async function exportToJSON(
  data: TimeSeriesData[],
  config: ChartConfig,
  filename: string,
  includeMetadata?: boolean
): Promise<void> {
  const exportData = {
    data,
    ...(includeMetadata && {
      config,
      exportedAt: new Date().toISOString(),
      version: '1.0.0',
    }),
  };

  const json = JSON.stringify(exportData, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.download = `${filename}.json`;
  link.href = url;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Export chart as PDF
 */
async function exportToPDF(
  data: TimeSeriesData[],
  config: ChartConfig,
  filename: string
): Promise<void> {
  // Use jsPDF for PDF generation
  const { default: jsPDF } = await import('jspdf');
  const { default: html2canvas } = await import('html2canvas');

  const chartElement = document.querySelector('.recharts-wrapper');
  if (!chartElement) {
    throw new Error('Chart element not found');
  }

  // Render chart to canvas
  const canvas = await html2canvas(chartElement as HTMLElement, {
    backgroundColor: '#ffffff',
    scale: 2,
  });

  // Create PDF
  const pdf = new jsPDF({
    orientation: canvas.width > canvas.height ? 'landscape' : 'portrait',
    unit: 'px',
    format: [canvas.width, canvas.height],
  });

  const imgData = canvas.toDataURL('image/png');
  pdf.addImage(imgData, 'PNG', 0, 0, canvas.width, canvas.height);

  // Add title if available
  if (config.title) {
    pdf.setFontSize(16);
    pdf.text(config.title, 20, 20);
  }

  pdf.save(`${filename}.pdf`);
}
