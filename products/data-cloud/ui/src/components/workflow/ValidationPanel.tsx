import React, { useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import { validationErrorsAtom, workflowAtom, selectedNodeAtom } from '@/stores/workflow.store';
import type { ValidationError, ValidationWarning } from '@/types/workflow.types';

/**
 * Validation panel component for workflow validation errors and warnings.
 *
 * <p><b>Purpose</b><br>
 * Displays workflow validation errors and warnings with suggestions and auto-fix recommendations.
 * Provides error navigation and context for debugging workflow issues.
 *
 * <p><b>Features</b><br>
 * - Error and warning display
 * - Error filtering and sorting
 * - Auto-fix recommendations
 * - Error navigation to problematic nodes
 * - Suggestion display with reasoning
 * - Error count summary
 * - Severity-based highlighting
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { ValidationPanel } from '@/components/workflow/ValidationPanel';
 *
 * export function WorkflowEditor() {
 *   return (
 *     <div className="flex">
 *       <div className="flex-1">Canvas</div>
 *       <ValidationPanel />
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * React component - safe for concurrent rendering.
 *
 * @see ValidationError
 * @see ValidationWarning
 * @doc.type component
 * @doc.purpose Workflow validation error and warning display
 * @doc.layer frontend
 */

export interface ValidationPanelProps {
  /**
   * Optional callback when error is clicked
   */
  onErrorClick?: (error: ValidationError) => void;

  /**
   * Optional callback when auto-fix is applied
   */
  onAutoFix?: (error: ValidationError) => void;
}

/**
 * ValidationPanel component.
 *
 * @param props component props
 * @returns JSX element
 */
export function ValidationPanel({ onErrorClick, onAutoFix }: ValidationPanelProps): JSX.Element {
  const [validationErrors] = useAtom(validationErrorsAtom);
  const [workflow] = useAtom(workflowAtom);
  const [, setSelectedNodeId] = useAtom(selectedNodeAtom);

  // Parse validation errors (stored as JSON strings)
  const parsedErrors = useMemo(() => {
    return validationErrors
      .map((err) => {
        try {
          return JSON.parse(err) as ValidationError;
        } catch {
          return {
            code: 'PARSE_ERROR',
            message: err,
          } as ValidationError;
        };
      })
      .sort((a, b) => {
        // Sort by severity: errors first, then warnings
        const aSeverity = a.code.includes('ERROR') ? 0 : 1;
        const bSeverity = b.code.includes('ERROR') ? 0 : 1;
        return aSeverity - bSeverity;
      });
  }, [validationErrors]);

  // Count errors and warnings
  const errorCount = useMemo(
    () => parsedErrors.filter((e) => e.code.includes('ERROR')).length,
    [parsedErrors]
  );

  const warningCount = useMemo(
    () => parsedErrors.filter((e) => !e.code.includes('ERROR')).length,
    [parsedErrors]
  );

  // Handle error click - navigate to problematic node
  const handleErrorClick = useCallback(
    (error: ValidationError) => {
      if (error.nodeId) {
        setSelectedNodeId(error.nodeId);
      }
      onErrorClick?.(error);
    },
    [setSelectedNodeId, onErrorClick]
  );

  // Handle auto-fix
  const handleAutoFix = useCallback(
    (error: ValidationError) => {
      // Auto-fix logic would be implemented here
      // For now, just call the callback
      onAutoFix?.(error);
    },
    [onAutoFix]
  );

  return (
    <div className="w-full h-full bg-white border-l border-gray-200 flex flex-col">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
        <h3 className="text-sm font-semibold text-gray-900">Validation</h3>
        <div className="mt-2 flex gap-4 text-xs">
          <div className="flex items-center gap-1">
            <div className="w-2 h-2 bg-red-500 rounded-full" />
            <span className="text-gray-600">
              {errorCount} {errorCount === 1 ? 'error' : 'errors'}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <div className="w-2 h-2 bg-yellow-500 rounded-full" />
            <span className="text-gray-600">
              {warningCount} {warningCount === 1 ? 'warning' : 'warnings'}
            </span>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {parsedErrors.length === 0 ? (
          <div className="p-4 text-center text-sm text-gray-500">
            <div className="text-2xl mb-2">✓</div>
            <p>No validation errors</p>
            <p className="text-xs mt-1">Workflow is valid</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {parsedErrors.map((error, index) => (
              <ErrorItem
                key={`${error.code}-${index}`}
                error={error}
                onClick={handleErrorClick}
                onAutoFix={handleAutoFix}
              />
            ))}
          </div>
        )}
      </div>

      {/* Footer */}
      {parsedErrors.length > 0 && (
        <div className="px-4 py-3 border-t border-gray-200 bg-gray-50 text-xs text-gray-600">
          <p>Click on an error to navigate to the problematic node</p>
        </div>
      )}
    </div>
  );
}

/**
 * Error item component.
 *
 * @param props component props
 * @returns JSX element
 */
interface ErrorItemProps {
  error: ValidationError;
  onClick: (error: ValidationError) => void;
  onAutoFix: (error: ValidationError) => void;
}

function ErrorItem({ error, onClick, onAutoFix }: ErrorItemProps): JSX.Element {
  const isError = error.code.includes('ERROR');
  const bgColor = isError ? 'bg-red-50 hover:bg-red-100' : 'bg-yellow-50 hover:bg-yellow-100';
  const borderColor = isError ? 'border-l-red-500' : 'border-l-yellow-500';
  const textColor = isError ? 'text-red-900' : 'text-yellow-900';
  const codeColor = isError ? 'text-red-700' : 'text-yellow-700';

  return (
    <div
      className={`px-4 py-3 border-l-4 ${borderColor} ${bgColor} cursor-pointer transition-colors`}
      onClick={() => onClick(error)}
    >
      {/* Error code and message */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1">
          <p className={`text-xs font-semibold ${codeColor}`}>{error.code}</p>
          <p className={`text-sm ${textColor} mt-1`}>{error.message}</p>
        </div>
        <div className="flex-shrink-0">
          {isError ? (
            <div className="w-5 h-5 bg-red-500 rounded-full flex items-center justify-center">
              <span className="text-white text-xs font-bold">!</span>
            </div>
          ) : (
            <div className="w-5 h-5 bg-yellow-500 rounded-full flex items-center justify-center">
              <span className="text-white text-xs font-bold">⚠</span>
            </div>
          )}
        </div>
      </div>

      {/* Suggestion */}
      {error.suggestion && (
        <div className="mt-2 p-2 bg-white bg-opacity-50 rounded text-xs text-gray-700">
          <p className="font-semibold mb-1">💡 Suggestion:</p>
          <p>{error.suggestion}</p>
        </div>
      )}

      {/* Node reference */}
      {error.nodeId && (
        <div className="mt-2 text-xs text-gray-600">
          <p>Node: <span className="font-mono bg-gray-200 px-1 rounded">{error.nodeId}</span></p>
        </div>
      )}

      {/* Auto-fix button */}
      {error.suggestion && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            onAutoFix(error);
          }}
          className="mt-2 px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
        >
          Apply Fix
        </button>
      )}
    </div>
  );
}
