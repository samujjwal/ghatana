/**
 * @fileoverview Validation panel for visual builder.
 *
 * Displays validation errors, warnings, and info messages for the builder document.
 * Supports filtering by severity and quick navigation to problematic components.
 *
 * @doc.type component
 * @doc.purpose Document validation and error reporting
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useMemo } from 'react';
import { Typography, Badge, Button } from '@ghatana/design-system';
import type { ValidationResult } from '@ghatana/ui-builder';

export interface ValidationPanelProps {
  /** Validation result from document validation */
  validationResult: ValidationResult | null;
  /** Callback when a validation item is clicked to navigate */
  onValidationErrorClick?: (nodeId: string) => void;
  /** Callback to re-run validation */
  onRevalidate?: () => void;
}

export function ValidationPanel({
  validationResult,
  onValidationErrorClick,
  onRevalidate,
}: ValidationPanelProps): ReactElement {
  const [severityFilter, setSeverityFilter] = useState<'all' | 'error' | 'warning' | 'info'>('all');

  const filteredIssues = useMemo(() => {
    if (!validationResult) return [];

    const issues: Array<{ severity: 'error' | 'warning' | 'info'; message: string; nodeId?: string }> = [];

    for (const error of validationResult.errors) {
      issues.push({ severity: 'error', message: error.message, nodeId: error.nodeId });
    }

    for (const warning of validationResult.warnings) {
      issues.push({ severity: 'warning', message: warning.message, nodeId: warning.nodeId });
    }

    if (severityFilter !== 'all') {
      return issues.filter((issue) => issue.severity === severityFilter);
    }

    return issues;
  }, [validationResult, severityFilter]);

  const errorCount = filteredIssues.filter((i) => i.severity === 'error').length;
  const warningCount = filteredIssues.filter((i) => i.severity === 'warning').length;
  const infoCount = filteredIssues.filter((i) => i.severity === 'info').length;

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-3 border-b flex items-center justify-between">
        <div>
          <Typography variant="h3" className="font-semibold">
            Validation
          </Typography>
          <div className="flex gap-2 mt-1">
            <Badge variant={errorCount > 0 ? 'solid' : 'soft'} tone={errorCount > 0 ? 'danger' : 'neutral'}>
              {errorCount} Errors
            </Badge>
            <Badge variant="soft" tone="warning">
              {warningCount} Warnings
            </Badge>
            <Badge variant="soft" tone="neutral">
              {infoCount} Info
            </Badge>
          </div>
        </div>
        {onRevalidate && (
          <Button variant="secondary" size="sm" onClick={onRevalidate}>
            Revalidate
          </Button>
        )}
      </div>

      {/* Severity Filter */}
      <div className="p-3 border-b flex gap-2">
        <button
          onClick={() => setSeverityFilter('all')}
          className={`px-3 py-1 rounded-full text-sm ${
            severityFilter === 'all'
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          All
        </button>
        <button
          onClick={() => setSeverityFilter('error')}
          className={`px-3 py-1 rounded-full text-sm ${
            severityFilter === 'error'
              ? 'bg-red-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Errors
        </button>
        <button
          onClick={() => setSeverityFilter('warning')}
          className={`px-3 py-1 rounded-full text-sm ${
            severityFilter === 'warning'
              ? 'bg-yellow-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Warnings
        </button>
        <button
          onClick={() => setSeverityFilter('info')}
          className={`px-3 py-1 rounded-full text-sm ${
            severityFilter === 'info'
              ? 'bg-blue-400 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Info
        </button>
      </div>

      {/* Issues List */}
      <div className="flex-1 overflow-y-auto p-3">
        {!validationResult ? (
          <div className="text-center py-8">
            <Typography variant="body2" className="text-gray-500">
              No validation results
            </Typography>
          </div>
        ) : validationResult.valid && filteredIssues.length === 0 ? (
          <div className="text-center py-8">
            <Typography variant="body1" className="text-green-600 font-medium mb-2">
              ✓ Document is valid
            </Typography>
            <Typography variant="body2" className="text-gray-500">
              No issues found
            </Typography>
          </div>
        ) : filteredIssues.length === 0 ? (
          <div className="text-center py-8">
            <Typography variant="body2" className="text-gray-500">
              No issues match the current filter
            </Typography>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredIssues.map((issue, index) => (
              <div
                key={index}
                className={`p-3 border rounded-lg cursor-pointer hover:bg-gray-50 ${
                  issue.severity === 'error'
                    ? 'border-red-200 bg-red-50'
                    : issue.severity === 'warning'
                      ? 'border-yellow-200 bg-yellow-50'
                      : 'border-blue-200 bg-blue-50'
                }`}
                onClick={() => issue.nodeId && onValidationErrorClick?.(issue.nodeId)}
              >
                <div className="flex items-start gap-2">
                  <Badge
                    variant="soft"
                    tone={issue.severity === 'error' ? 'danger' : issue.severity === 'warning' ? 'warning' : 'neutral'}
                    className="text-xs uppercase"
                  >
                    {issue.severity}
                  </Badge>
                  <Typography variant="body2" className="text-sm flex-1">
                    {issue.message}
                  </Typography>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
