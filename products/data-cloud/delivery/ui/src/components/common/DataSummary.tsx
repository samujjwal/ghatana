/**
 * Data Summary Component
 * 
 * Displays structured data in a human-friendly format with expandable technical details.
 * Replaces raw JSON panels with readable summaries.
 * 
 * @doc.type component
 * @doc.purpose Human-friendly data display with expandable details
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React, { useState } from 'react';
import { cn, textStyles } from '../../lib/theme';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface DataSummaryProps {
  /** Title of the data section */
  title: string;
  /** Description of the data */
  description?: string;
  /** The data to display */
  data: Record<string, unknown> | null | undefined;
  /** Fields to highlight in the summary */
  highlightFields?: string[];
  /** Additional CSS classes */
  className?: string;
}

/**
 * Renders a key-value pair in a human-friendly format.
 */
function renderValue(key: string, value: unknown): React.ReactNode {
  if (value === null || value === undefined) {
    return <span className="text-gray-400 italic">Not set</span>;
  }
  
  if (typeof value === 'boolean') {
    return (
      <span className={cn('px-2 py-0.5 text-xs font-medium rounded', value ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200')}>
        {value ? 'Yes' : 'No'}
      </span>
    );
  }
  
  if (typeof value === 'number') {
    return <span className="font-mono">{value.toLocaleString()}</span>;
  }
  
  if (typeof value === 'string') {
    // Check if it's a date
    if (key.toLowerCase().includes('date') || key.toLowerCase().includes('time') || key.toLowerCase().includes('at')) {
      try {
        const date = new Date(value);
        if (!isNaN(date.getTime())) {
          return <span className="font-mono">{date.toLocaleString()}</span>;
        }
      } catch {
        // Not a valid date, fall through
      }
    }
    return <span className="break-words">{value}</span>;
  }
  
  if (Array.isArray(value)) {
    return (
      <div className="space-y-1">
        {value.length === 0 ? (
          <span className="text-gray-400 italic">Empty array</span>
        ) : (
          value.slice(0, 5).map((item, index) => (
            <div key={index} className="text-sm pl-2 border-l-2 border-gray-200 dark:border-gray-700">
              {renderValue(`${key}[${index}]`, item)}
            </div>
          ))
        )}
        {value.length > 5 && (
          <span className="text-xs text-gray-500">+{value.length - 5} more items</span>
        )}
      </div>
    );
  }
  
  if (typeof value === 'object') {
    return (
      <div className="pl-2 border-l-2 border-gray-200 dark:border-gray-700">
        {Object.entries(value as Record<string, unknown>).slice(0, 3).map(([k, v]) => (
          <div key={k} className="text-sm py-1">
            <span className="font-medium text-gray-600 dark:text-gray-400">{k}:</span>{' '}
            {renderValue(k, v)}
          </div>
        ))}
        {Object.keys(value as Record<string, unknown>).length > 3 && (
          <span className="text-xs text-gray-500">+{Object.keys(value as Record<string, unknown>).length - 3} more fields</span>
        )}
      </div>
    );
  }
  
  return <span className="font-mono text-xs">{String(value)}</span>;
}

/**
 * Data summary component with human-friendly display and expandable technical details.
 * 
 * @example
 * ```tsx
 * <DataSummary 
 *   title="Retention Policy" 
 *   data={retentionPolicy}
 *   highlightFields={['retentionPeriod', 'archiveAfter']}
 * />
 * ```
 */
export const DataSummary: React.FC<DataSummaryProps> = ({
  title,
  description,
  data,
  highlightFields = [],
  className = ''
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  if (!data) {
    return (
      <div className={cn('p-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30', className)}>
        <h3 className={cn(textStyles.h3, 'mb-2')}>{title}</h3>
        <p className={cn(textStyles.small, 'text-gray-500 italic')}>No data available</p>
      </div>
    );
  }

  const entries = Object.entries(data);
  const highlightedEntries = entries.filter(([key]) => highlightFields.includes(key));
  const otherEntries = entries.filter(([key]) => !highlightFields.includes(key));

  return (
    <div className={cn('rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30', className)}>
      <div className="p-4">
        <div className="flex items-center justify-between mb-2">
          <h3 className={cn(textStyles.h3)}>{title}</h3>
          <button
            type="button"
            onClick={() => setIsExpanded(!isExpanded)}
            className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
            aria-label={isExpanded ? 'Collapse' : 'Expand'}
          >
            {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </button>
        </div>
        {description && <p className={cn(textStyles.small, 'text-gray-600 dark:text-gray-400 mb-3')}>{description}</p>}
        
        {/* Highlighted fields */}
        {highlightedEntries.length > 0 && (
          <div className="space-y-2 mb-3">
            {highlightedEntries.map(([key, value]) => (
              <div key={key} className="flex items-start gap-2">
                <span className={cn(textStyles.small, 'font-medium text-gray-600 dark:text-gray-400 min-w-[120px]')}>
                  {formatKey(key)}:
                </span>
                <span className={cn(textStyles.small, 'flex-1')}>
                  {renderValue(key, value)}
                </span>
              </div>
            ))}
          </div>
        )}

        {/* Other fields (collapsed by default) */}
        {otherEntries.length > 0 && (
          <div className={cn('space-y-2', !isExpanded && 'hidden')}>
            {otherEntries.map(([key, value]) => (
              <div key={key} className="flex items-start gap-2">
                <span className={cn(textStyles.small, 'font-medium text-gray-600 dark:text-gray-400 min-w-[120px]')}>
                  {formatKey(key)}:
                </span>
                <span className={cn(textStyles.small, 'flex-1')}>
                  {renderValue(key, value)}
                </span>
              </div>
            ))}
          </div>
        )}

        {/* Expandable technical details */}
        {otherEntries.length > 0 && !isExpanded && (
          <button
            type="button"
            onClick={() => setIsExpanded(true)}
            className={cn(textStyles.small, 'text-blue-600 dark:text-blue-400 hover:underline mt-2')}
          >
            Show {otherEntries.length} more field{otherEntries.length !== 1 ? 's' : ''}
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Formats a key for display (camelCase to Title Case).
 */
function formatKey(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (str) => str.toUpperCase())
    .trim();
}

export default DataSummary;
