/**
 * ValidationPanel Placeholder Component
 * 
 * Temporary placeholder until the actual component is implemented in @ghatana/yappc-ui
 */

import React from 'react';

interface ValidationPanelProps {
  issues?: unknown[];
  onResolve?: (issueId: string) => void;
  onIgnore?: (issueId: string) => void;
  className?: string;
}

export const ValidationPanel: React.FC<ValidationPanelProps> = ({
  issues = [],
  onResolve,
  onIgnore,
}) => {
  return (
    <div className="bg-zinc-900 rounded-lg border border-zinc-800 p-4">
      <h3 className="text-lg font-semibold text-white mb-4">Validation Issues</h3>
      
      {issues.length === 0 ? (
        <div className="text-center py-8 text-zinc-400">
          No validation issues found
        </div>
      ) : (
        <div className="space-y-3">
          {issues.map((issue: unknown, idx: number) => (
            <div key={idx} className="p-3 bg-zinc-800 rounded-lg">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      issue.severity === 'error' 
                        ? 'bg-red-500/20 text-red-400'
                        : 'bg-yellow-500/20 text-yellow-400'
                    }`}>
                      {issue.severity || 'warning'}
                    </span>
                    <span className="text-white font-medium">{issue.title || issue.message}</span>
                  </div>
                  {issue.description && (
                    <p className="text-sm text-zinc-400 mt-1">{issue.description}</p>
                  )}
                </div>
                <div className="flex gap-2 ml-4">
                  {onResolve && (
                    <button
                      onClick={() => onResolve(issue.id)}
                      className="px-3 py-1 text-xs bg-green-500/20 text-green-400 rounded hover:bg-green-500/30"
                    >
                      Resolve
                    </button>
                  )}
                  {onIgnore && (
                    <button
                      onClick={() => onIgnore(issue.id)}
                      className="px-3 py-1 text-xs bg-zinc-700 text-zinc-400 rounded hover:bg-zinc-600"
                    >
                      Ignore
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
