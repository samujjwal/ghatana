import React from 'react';
import { cn } from '@/lib/utils';

/**
 * Validation Panel component.
 *
 * Shows test results, linting errors, and AI suggestions.
 * Part of Studio Mode layout.
 *
 * @doc.type component
 * @doc.purpose Validation and testing results for Studio Mode
 * @doc.layer ui
 */

export type ValidationSeverity = 'error' | 'warning' | 'info' | 'success';

export interface ValidationIssue {
  id: string;
  type: 'typescript' | 'eslint' | 'test' | 'ai';
  severity: ValidationSeverity;
  message: string;
  file?: string;
  line?: number;
  column?: number;
  suggestion?: string;
}

export interface ValidationPanelProps {
  issues: ValidationIssue[];
  onIssueClick?: (issue: ValidationIssue) => void;
  onRunTests?: () => void;
  onFixAll?: () => void;
  className?: string;
}

function SeverityIcon({ severity }: { severity: ValidationSeverity }) {
  const config = {
    error: { icon: '❌', color: 'text-red-500' },
    warning: { icon: '⚠️', color: 'text-yellow-500' },
    info: { icon: 'ℹ️', color: 'text-blue-500' },
    success: { icon: '✅', color: 'text-green-500' },
  };

  const { icon, color } = config[severity];
  return <span className={cn('text-sm', color)}>{icon}</span>;
}

function ValidationIssueItem({
  issue,
  onClick,
}: {
  issue: ValidationIssue;
  onClick?: (issue: ValidationIssue) => void;
}) {
  return (
    <div
      className={cn(
        'p-3 border-b border-gray-200 dark:border-gray-800 cursor-pointer',
        'hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors'
      )}
      onClick={() => onClick?.(issue)}
    >
      <div className="flex items-start gap-2">
        <SeverityIcon severity={issue.severity} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
              {issue.type}
            </span>
            {issue.file && (
              <span className="text-xs text-gray-400 dark:text-gray-600 truncate">
                {issue.file}
                {issue.line && `:${issue.line}`}
                {issue.column && `:${issue.column}`}
              </span>
            )}
          </div>
          <p className="text-sm text-gray-900 dark:text-gray-100 mb-1">
            {issue.message}
          </p>
          {issue.suggestion && (
            <p className="text-xs text-blue-600 dark:text-blue-400 italic">
              💡 {issue.suggestion}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export function ValidationPanel({
  issues,
  onIssueClick,
  onRunTests,
  onFixAll,
  className,
}: ValidationPanelProps) {
  const stats = React.useMemo(() => {
    return {
      errors: issues.filter((i) => i.severity === 'error').length,
      warnings: issues.filter((i) => i.severity === 'warning').length,
      info: issues.filter((i) => i.severity === 'info').length,
      success: issues.filter((i) => i.severity === 'success').length,
    };
  }, [issues]);

  const groupedIssues = React.useMemo(() => {
    const groups: Record<string, ValidationIssue[]> = {
      typescript: [],
      eslint: [],
      test: [],
      ai: [],
    };
    issues.forEach((issue) => {
      groups[issue.type].push(issue);
    });
    return groups;
  }, [issues]);

  return (
    <div
      className={cn(
        'flex flex-col h-full bg-white dark:bg-gray-900',
        className
      )}
    >
      {/* Header */}
      <div className="h-10 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between px-3">
        <div className="flex items-center gap-3">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
            Validation
          </h3>
          <div className="flex items-center gap-2 text-xs">
            {stats.errors > 0 && (
              <span className="text-red-500">{stats.errors} errors</span>
            )}
            {stats.warnings > 0 && (
              <span className="text-yellow-500">{stats.warnings} warnings</span>
            )}
            {stats.success > 0 && (
              <span className="text-green-500">{stats.success} passed</span>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={onRunTests}
            className="px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            Run Tests
          </button>
          {(stats.errors > 0 || stats.warnings > 0) && (
            <button
              onClick={onFixAll}
              className="px-2 py-1 text-xs bg-green-500 text-white rounded hover:bg-green-600"
            >
              Fix All
            </button>
          )}
        </div>
      </div>

      {/* Issues List */}
      <div className="flex-1 overflow-auto">
        {issues.length === 0 ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-gray-500 dark:text-gray-400">
              <span className="text-4xl mb-2">✅</span>
              <p className="text-sm">No issues found</p>
            </div>
          </div>
        ) : (
          <>
            {/* TypeScript Issues */}
            {groupedIssues.typescript.length > 0 && (
              <div>
                <div className="px-3 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-semibold text-gray-700 dark:text-gray-300">
                  TypeScript ({groupedIssues.typescript.length})
                </div>
                {groupedIssues.typescript.map((issue) => (
                  <ValidationIssueItem
                    key={issue.id}
                    issue={issue}
                    onClick={onIssueClick}
                  />
                ))}
              </div>
            )}

            {/* ESLint Issues */}
            {groupedIssues.eslint.length > 0 && (
              <div>
                <div className="px-3 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-semibold text-gray-700 dark:text-gray-300">
                  ESLint ({groupedIssues.eslint.length})
                </div>
                {groupedIssues.eslint.map((issue) => (
                  <ValidationIssueItem
                    key={issue.id}
                    issue={issue}
                    onClick={onIssueClick}
                  />
                ))}
              </div>
            )}

            {/* Test Results */}
            {groupedIssues.test.length > 0 && (
              <div>
                <div className="px-3 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-semibold text-gray-700 dark:text-gray-300">
                  Tests ({groupedIssues.test.length})
                </div>
                {groupedIssues.test.map((issue) => (
                  <ValidationIssueItem
                    key={issue.id}
                    issue={issue}
                    onClick={onIssueClick}
                  />
                ))}
              </div>
            )}

            {/* AI Suggestions */}
            {groupedIssues.ai.length > 0 && (
              <div>
                <div className="px-3 py-2 bg-gray-100 dark:bg-gray-800 text-xs font-semibold text-gray-700 dark:text-gray-300">
                  AI Suggestions ({groupedIssues.ai.length})
                </div>
                {groupedIssues.ai.map((issue) => (
                  <ValidationIssueItem
                    key={issue.id}
                    issue={issue}
                    onClick={onIssueClick}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/**
 * Hook for managing validation state.
 *
 * @doc.type hook
 * @doc.purpose Validation state management
 */
export function useValidation() {
  const [issues, setIssues] = React.useState<ValidationIssue[]>([]);
  const [isRunning, setIsRunning] = React.useState(false);

  const runValidation = async () => {
    setIsRunning(true);
    // Simulate validation
    await new Promise((resolve) => setTimeout(resolve, 1000));
    setIsRunning(false);
  };

  const handleIssueClick = (issue: ValidationIssue) => {
    console.log('Navigate to issue:', issue);
  };

  const handleRunTests = () => {
    runValidation();
  };

  const handleFixAll = () => {
    console.log('Fix all issues');
  };

  return {
    issues,
    setIssues,
    isRunning,
    handleIssueClick,
    handleRunTests,
    handleFixAll,
  };
}
