/**
 * Recovery Guidance View - provides actionable recovery guidance for failed lifecycle operations.
 *
 * @doc.type component
 * @doc.purpose Show recovery guidance view with clear recovery actions for failed operations
 * @doc.layer studio
 */

import React, { useState } from "react";

interface RecoveryIssue {
  id: string;
  type: "provider-missing" | "adapter-failed" | "gate-failed" | "interaction-error" | "deployment-failed";
  severity: "critical" | "high" | "medium" | "low";
  description: string;
  affectedComponent: string;
  suggestedActions: RecoveryAction[];
  relatedEvidence: string[];
}

interface RecoveryAction {
  id: string;
  description: string;
  type: "manual" | "automated" | "retry";
  command?: string;
  estimatedDuration?: string;
}

interface RecoveryGuidanceViewProps {
  issues: RecoveryIssue[];
  onExecuteAction: (actionId: string) => void;
}

const SEVERITY_COLORS = {
  critical: "bg-red-100 text-red-800 border-red-300",
  high: "bg-orange-100 text-orange-800 border-orange-300",
  medium: "bg-yellow-100 text-yellow-800 border-yellow-300",
  low: "bg-blue-100 text-blue-800 border-blue-300",
} as const;

const TYPE_ICONS = {
  "provider-missing": "🔌",
  "adapter-failed": "⚙️",
  "gate-failed": "🚦",
  "interaction-error": "🔗",
  "deployment-failed": "🚀",
} as const;

export function RecoveryGuidanceView({ issues, onExecuteAction }: RecoveryGuidanceViewProps) {
  const [selectedIssue, setSelectedIssue] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "critical" | "high" | "medium" | "low">("all");

  const filteredIssues = issues.filter((issue) => filter === "all" || issue.severity === filter);
  const selectedIssueData = issues.find((i) => i.id === selectedIssue);

  const criticalIssues = issues.filter((i) => i.severity === "critical").length;
  const highIssues = issues.filter((i) => i.severity === "high").length;

  return (
    <div className="recovery-guidance-view">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Recovery Guidance</h2>
        <div className="flex gap-2">
          <button
            onClick={() => setFilter("all")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "all" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            All ({issues.length})
          </button>
          <button
            onClick={() => setFilter("critical")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "critical" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            Critical ({criticalIssues})
          </button>
          <button
            onClick={() => setFilter("high")}
            className={`px-3 py-1 rounded-md text-sm ${
              filter === "high" ? "bg-blue-600 text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            High ({highIssues})
          </button>
        </div>
      </div>

      {(criticalIssues > 0 || highIssues > 0) && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
          <div className="flex items-center">
            <svg className="w-6 h-6 text-red-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <div>
              <p className="font-semibold text-red-900">Attention Required</p>
              <p className="text-sm text-red-700">
                {criticalIssues} critical and {highIssues} high severity issues need immediate attention.
              </p>
            </div>
          </div>
        </div>
      )}

      <div className="flex gap-6">
        <div className="flex-1 space-y-4">
          {filteredIssues.map((issue) => (
            <div
              key={issue.id}
              className={`bg-white border-2 rounded-lg p-4 cursor-pointer transition-colors ${
                selectedIssue === issue.id
                  ? "border-blue-500 bg-blue-50"
                  : SEVERITY_COLORS[issue.severity]
              }`}
              onClick={() => setSelectedIssue(issue.id)}
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex items-center">
                  <span className="text-2xl mr-3">{TYPE_ICONS[issue.type]}</span>
                  <div>
                    <h3 className="font-semibold text-gray-900">{issue.type}</h3>
                    <p className="text-sm text-gray-600">{issue.affectedComponent}</p>
                  </div>
                </div>
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${SEVERITY_COLORS[issue.severity]}`}>
                  {issue.severity}
                </span>
              </div>
              <p className="text-sm text-gray-700 mb-2">{issue.description}</p>
              <div className="flex items-center text-xs text-gray-500">
                <span className="mr-4">{issue.suggestedActions.length} recovery actions</span>
                <span>{issue.relatedEvidence.length} evidence refs</span>
              </div>
            </div>
          ))}
        </div>

        {selectedIssueData && (
          <div className="w-96 bg-white border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold">Recovery Actions</h3>
              <button
                onClick={() => setSelectedIssue(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <p className="text-sm text-gray-600">Issue</p>
                <p className="font-medium">{selectedIssueData.description}</p>
              </div>

              <div>
                <p className="text-sm text-gray-600 mb-2">Affected Component</p>
                <p className="font-mono text-sm bg-gray-100 p-2 rounded">{selectedIssueData.affectedComponent}</p>
              </div>

              <div>
                <p className="text-sm text-gray-600 mb-2">Suggested Actions</p>
                <div className="space-y-2">
                  {selectedIssueData.suggestedActions.map((action) => (
                    <div key={action.id} className="border border-gray-200 rounded-lg p-3">
                      <div className="flex items-start justify-between mb-2">
                        <p className="font-medium text-sm">{action.description}</p>
                        <span className="px-2 py-1 rounded text-xs bg-gray-100 text-gray-700">
                          {action.type}
                        </span>
                      </div>
                      {action.command && (
                        <div className="mb-2">
                          <p className="text-xs text-gray-600 mb-1">Command</p>
                          <code className="text-xs bg-gray-100 p-1 rounded block">{action.command}</code>
                        </div>
                      )}
                      {action.estimatedDuration && (
                        <p className="text-xs text-gray-600">Estimated: {action.estimatedDuration}</p>
                      )}
                      <button
                        onClick={() => onExecuteAction(action.id)}
                        className="mt-2 w-full px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                      >
                        Execute Action
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <p className="text-sm text-gray-600 mb-2">Related Evidence</p>
                <div className="space-y-1">
                  {selectedIssueData.relatedEvidence.map((ref, idx) => (
                    <p key={idx} className="text-xs font-mono bg-gray-100 p-1 rounded">
                      {ref}
                    </p>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
