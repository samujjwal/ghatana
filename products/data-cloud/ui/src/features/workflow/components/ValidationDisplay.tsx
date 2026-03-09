/**
 * Workflow validation display component.
 *
 * <p><b>Purpose</b><br>
 * Displays real-time validation feedback for workflows.
 * Shows errors, warnings, and auto-fix suggestions with visual indicators.
 *
 * <p><b>Features</b><br>
 * - Real-time validation status
 * - Error and warning categorization
 * - Visual severity indicators
 * - Auto-fix suggestions
 * - One-click fixes for common issues
 * - Expandable error details
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ValidationDisplay } from '@/features/workflow/components/ValidationDisplay';
 *
 * <ValidationDisplay
 *   validationResult={result}
 *   onFixClick={handleAutoFix}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Workflow validation feedback display
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React, { useState, useMemo } from 'react';
import clsx from 'clsx';
import type { ValidationResult, ValidationError, ValidationWarning } from '@/types/workflow.types';

export interface ValidationDisplayProps {
  validationResult: ValidationResult;
  onFixClick?: (errorCode: string, fix: () => void) => void;
  compact?: boolean;
}

/**
 * Error icon component.
 */
function ErrorIcon() {
  return (
    <svg className="w-5 h-5 text-red-500" fill="currentColor" viewBox="0 0 20 20">
      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
    </svg>
  );
}

/**
 * Warning icon component.
 */
function WarningIcon() {
  return (
    <svg className="w-5 h-5 text-yellow-500" fill="currentColor" viewBox="0 0 20 20">
      <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
    </svg>
  );
}

/**
 * Success icon component.
 */
function SuccessIcon() {
  return (
    <svg className="w-5 h-5 text-green-500" fill="currentColor" viewBox="0 0 20 20">
      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
    </svg>
  );
}

/**
 * Individual error item component.
 */
function ErrorItem({
  error,
  onFixClick,
}: {
  error: ValidationError;
  onFixClick?: (code: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="p-3 border-l-4 border-red-500 bg-red-50 rounded">
      <div className="flex items-start gap-3">
        <ErrorIcon />
        <div className="flex-1 min-w-0">
          <p className="font-medium text-red-900">{error.message}</p>
          {error.nodeId && (
            <p className="text-sm text-red-700 mt-1">Node: {error.nodeId}</p>
          )}
          {error.edgeId && (
            <p className="text-sm text-red-700 mt-1">Edge: {error.edgeId}</p>
          )}
          {error.suggestion && (
            <p className="text-sm text-red-700 mt-2 italic">
              💡 {error.suggestion}
            </p>
          )}
          {((error as any).details) && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-sm text-red-600 hover:text-red-700 mt-2 underline"
            >
              {expanded ? 'Hide' : 'Show'} details
            </button>
          )}
          {expanded && ((error as any).details) && (
            <pre className="text-xs bg-white p-2 rounded mt-2 overflow-auto max-h-32">
              {JSON.stringify((error as any).details, null, 2)}
            </pre>
          )}
        </div>
        {onFixClick && (
          <button
            onClick={() => onFixClick(error.code)}
            className="px-2 py-1 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded whitespace-nowrap"
          >
            Fix
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Individual warning item component.
 */
function WarningItem({
  warning,
  onFixClick,
}: {
  warning: ValidationWarning;
  onFixClick?: (code: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="p-3 border-l-4 border-yellow-500 bg-yellow-50 rounded">
      <div className="flex items-start gap-3">
        <WarningIcon />
        <div className="flex-1 min-w-0">
          <p className="font-medium text-yellow-900">{warning.message}</p>
          {warning.nodeId && (
            <p className="text-sm text-yellow-700 mt-1">Node: {warning.nodeId}</p>
          )}
          {warning.edgeId && (
            <p className="text-sm text-yellow-700 mt-1">Edge: {warning.edgeId}</p>
          )}
          {warning.suggestion && (
            <p className="text-sm text-yellow-700 mt-2 italic">
              💡 {warning.suggestion}
            </p>
          )}
          {((warning as any).details) && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-sm text-yellow-600 hover:text-yellow-700 mt-2 underline"
            >
              {expanded ? 'Hide' : 'Show'} details
            </button>
          )}
          {expanded && ((warning as any).details) && (
            <pre className="text-xs bg-white p-2 rounded mt-2 overflow-auto max-h-32">
              {JSON.stringify((warning as any).details, null, 2)}
            </pre>
          )}
        </div>
        {onFixClick && (
          <button
            onClick={() => onFixClick(warning.code)}
            className="px-2 py-1 text-sm font-medium text-white bg-yellow-600 hover:bg-yellow-700 rounded whitespace-nowrap"
          >
            Fix
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Validation display component showing errors, warnings, and status.
 *
 * @param props component props
 * @returns rendered validation display
 */
export function ValidationDisplay({
  validationResult,
  onFixClick,
  compact = false,
}: ValidationDisplayProps) {
  const [showWarnings, setShowWarnings] = useState(!compact);

  const stats = useMemo(
    () => ({
      errors: validationResult.errors.length,
      warnings: validationResult.warnings.length,
      total: validationResult.errors.length + validationResult.warnings.length,
    }),
    [validationResult]
  );

  if (stats.total === 0) {
    return (
      <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
        <div className="flex items-center gap-3">
          <SuccessIcon />
          <div>
            <p className="font-medium text-green-900">Workflow is valid ✓</p>
            <p className="text-sm text-green-700">No errors or warnings found</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Status header */}
      <div
        className={clsx(
          'p-4 rounded-lg border',
          stats.errors > 0
            ? 'bg-red-50 border-red-200'
            : 'bg-yellow-50 border-yellow-200'
        )}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {stats.errors > 0 ? <ErrorIcon /> : <WarningIcon />}
            <div>
              <p
                className={clsx(
                  'font-medium',
                  stats.errors > 0 ? 'text-red-900' : 'text-yellow-900'
                )}
              >
                {stats.errors > 0
                  ? `${stats.errors} error${stats.errors !== 1 ? 's' : ''} found`
                  : 'No errors, but there are warnings'}
              </p>
              {stats.warnings > 0 && (
                <p className="text-sm text-gray-700">
                  {stats.warnings} warning{stats.warnings !== 1 ? 's' : ''}
                </p>
              )}
            </div>
          </div>

          {/* Summary stats */}
          <div className="flex gap-4 text-sm">
            <div className="text-center">
              <p className="font-semibold text-red-600">{stats.errors}</p>
              <p className="text-gray-600">Errors</p>
            </div>
            <div className="text-center">
              <p className="font-semibold text-yellow-600">{stats.warnings}</p>
              <p className="text-gray-600">Warnings</p>
            </div>
          </div>
        </div>
      </div>

      {/* Errors section */}
      {stats.errors > 0 && (
        <div className="space-y-3">
          <h3 className="font-semibold text-red-900">Errors</h3>
          <div className="space-y-2">
            {validationResult.errors.map((error) => (
              <ErrorItem
                key={`${error.code}-${error.nodeId || error.edgeId || ''}`}
                error={error}
                onFixClick={onFixClick ? () => onFixClick(error.code, () => {}) : undefined}
              />
            ))}
          </div>
        </div>
      )}

      {/* Warnings section */}
      {stats.warnings > 0 && (
        <div className="space-y-3">
          <button
            onClick={() => setShowWarnings(!showWarnings)}
            className="font-semibold text-yellow-900 hover:text-yellow-700 flex items-center gap-2"
          >
            <span>{showWarnings ? '▼' : '▶'}</span>
            Warnings ({stats.warnings})
          </button>

          {showWarnings && (
            <div className="space-y-2">
              {validationResult.warnings.map((warning) => (
                <WarningItem
                  key={`${warning.code}-${warning.nodeId || warning.edgeId || ''}`}
                  warning={warning}
                  onFixClick={
                    onFixClick ? () => onFixClick(warning.code, () => {}) : undefined
                  }
                />
              ))}
            </div>
          )}
        </div>
      )}

      {/* Help text */}
      <p className="text-xs text-gray-500">
        💡 Tip: Click "Fix" buttons to automatically resolve common issues
      </p>
    </div>
  );
}

export default ValidationDisplay;
