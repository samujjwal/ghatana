/**
 * @fileoverview Metric Table Component
 *
 * Sortable and filterable table for displaying metric data
 * with export capabilities and responsive design.
 *
 * @module ui/components/metrics
 * @since 2.0.0
 */

import React, { useState, useMemo } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { ProcessedMetrics } from '../../../analytics/AnalyticsPipeline';

export interface MetricTableProps {
  title: string;
  description?: string;
  data: ProcessedMetrics[];
  columns: string[];
  sortable?: boolean;
  filterable?: boolean;
  pagination?: boolean;
  pageSize?: number;
  exportable?: boolean;
  loading?: boolean;
  error?: string;
}

type SortDirection = 'asc' | 'desc';
type ColumnSort = { column: string; direction: SortDirection };

/**
 * Metric Table Component
 *
 * Displays metrics in a sortable, filterable table format.
 */
export const MetricTable: React.FC<MetricTableProps> = ({
  title,
  description,
  data,
  columns,
  sortable = true,
  filterable = true,
  pagination = true,
  pageSize = 10,
  exportable = true,
  loading = false,
  error,
}) => {
  const [sort, setSort] = useState<ColumnSort | null>(null);
  const [filter, setFilter] = useState('');
  const [currentPage, setCurrentPage] = useState(1);

  // Format value for display
  const formatValue = (value: any, column: string): string => {
    if (column === 'timestamp') {
      return new Date(value).toLocaleString();
    }
    
    if (typeof value === 'number') {
      if (column === 'cls') {
        return value.toFixed(3);
      }
      if (column.includes('Transfer') || column.includes('Size')) {
        return `${(value / 1024).toFixed(1)} KB`;
      }
      return value.toFixed(1);
    }
    
    return String(value ?? '—');
  };

  // Sort and filter data
  const processedData = useMemo(() => {
    let result = [...data];

    // Apply filter
    if (filter && filterable) {
      result = result.filter(row =>
        columns.some(column => {
          const value = row.summary[column];
          return String(value).toLowerCase().includes(filter.toLowerCase());
        })
      );
    }

    // Apply sort
    if (sort && sortable) {
      result.sort((a, b) => {
        const aValue = a.summary[sort.column];
        const bValue = b.summary[sort.column];
        
        if (aValue === undefined || aValue === null) return 1;
        if (bValue === undefined || bValue === null) return -1;
        
        let comparison = 0;
        if (typeof aValue === 'number' && typeof bValue === 'number') {
          comparison = aValue - bValue;
        } else {
          comparison = String(aValue).localeCompare(String(bValue));
        }
        
        return sort.direction === 'asc' ? comparison : -comparison;
      });
    }

    return result;
  }, [data, filter, sort, columns, filterable, sortable]);

  // Paginate data
  const paginatedData = useMemo(() => {
    if (!pagination) return processedData;
    
    const startIndex = (currentPage - 1) * pageSize;
    return processedData.slice(startIndex, startIndex + pageSize);
  }, [processedData, currentPage, pagination, pageSize]);

  const totalPages = Math.ceil(processedData.length / pageSize);

  // Handle column click for sorting
  const handleColumnClick = (column: string) => {
    if (!sortable) return;
    
    setSort(currentSort => {
      if (!currentSort || currentSort.column !== column) {
        return { column, direction: 'asc' };
      }
      if (currentSort.direction === 'asc') {
        return { column, direction: 'desc' };
      }
      return null;
    });
  };

  // Export data
  const handleExport = (format: 'csv' | 'json') => {
    const exportData = processedData.map(row => {
      const exportRow: Record<string, any> = {};
      columns.forEach(column => {
        exportRow[column] = row.summary[column];
      });
      return exportRow;
    });

    if (format === 'csv') {
      const headers = columns.join(',');
      const rows = exportData.map(row =>
        columns.map(col => {
          const value = formatValue(row[col], col);
          // Escape commas and quotes for CSV
          if (value.includes(',') || value.includes('"')) {
            return `"${value.replace(/"/g, '""')}"`;
          }
          return value;
        }).join(',')
      );
      
      const csv = [headers, ...rows].join('\n');
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `metrics-${Date.now()}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } else {
      const json = JSON.stringify(exportData, null, 2);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `metrics-${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(url);
    }
  };

  // Get sort indicator
  const getSortIndicator = (column: string) => {
    if (!sort || sort.column !== column) return null;
    return sort.direction === 'asc' ? '↑' : '↓';
  };

  return (
    <Card title={title} description={description}>
      {loading ? (
        <div className="flex items-center justify-center py-8">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-slate-400"></div>
        </div>
      ) : error ? (
        <div className="text-center py-8 text-rose-600">
          {error}
        </div>
      ) : (
        <div className="space-y-4">
          {/* Controls */}
          <div className="flex items-center justify-between gap-4">
            {filterable && (
              <input
                type="text"
                placeholder="Filter metrics..."
                value={filter}
                onChange={(e) => {
                  setFilter(e.target.value);
                  setCurrentPage(1);
                }}
                className="px-3 py-2 border border-slate-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            )}
            
            <div className="flex items-center gap-2">
              {exportable && (
                <>
                  <button
                    onClick={() => handleExport('csv')}
                    className="px-3 py-1 text-sm border border-slate-300 rounded hover:bg-slate-50"
                  >
                    Export CSV
                  </button>
                  <button
                    onClick={() => handleExport('json')}
                    className="px-3 py-1 text-sm border border-slate-300 rounded hover:bg-slate-50"
                  >
                    Export JSON
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Table */}
          <div className="overflow-x-auto border border-slate-200 rounded-lg">
            <table className="min-w-full divide-y divide-slate-200">
              <thead className="bg-slate-50">
                <tr>
                  {columns.map(column => (
                    <th
                      key={column}
                      onClick={() => handleColumnClick(column)}
                      className={`px-4 py-3 text-left text-xs font-medium text-slate-500 uppercase tracking-wider ${
                        sortable ? 'cursor-pointer hover:bg-slate-100' : ''
                      }`}
                    >
                      <div className="flex items-center gap-1">
                        <span>{column}</span>
                        {getSortIndicator(column)}
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-slate-200">
                {paginatedData.map((row, index) => (
                  <tr key={index} className="hover:bg-slate-50">
                    {columns.map(column => (
                      <td key={column} className="px-4 py-3 text-sm text-slate-900">
                        {formatValue(row.summary[column], column)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {pagination && totalPages > 1 && (
            <div className="flex items-center justify-between">
              <div className="text-sm text-slate-500">
                Showing {((currentPage - 1) * pageSize) + 1} to {Math.min(currentPage * pageSize, processedData.length)} of {processedData.length} results
              </div>
              
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                  className="px-3 py-1 text-sm border border-slate-300 rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
                >
                  Previous
                </button>
                
                <span className="text-sm text-slate-500">
                  Page {currentPage} of {totalPages}
                </span>
                
                <button
                  onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  disabled={currentPage === totalPages}
                  className="px-3 py-1 text-sm border border-slate-300 rounded disabled:opacity-50 disabled:cursor-not-allowed hover:bg-slate-50"
                >
                  Next
                </button>
              </div>
            </div>
          )}

          {/* No data message */}
          {processedData.length === 0 && !loading && (
            <div className="text-center py-8 text-slate-500">
              No data available
            </div>
          )}
        </div>
      )}
    </Card>
  );
};

export default MetricTable;
