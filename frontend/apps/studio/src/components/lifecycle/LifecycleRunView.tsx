/**
 * Lifecycle Run View - displays lifecycle execution progress and results.
 *
 * @doc.type component
 * @doc.purpose Visualize lifecycle execution progress and results
 * @doc.layer studio
 */

import React from "react";

interface LifecyclePhaseExecution {
  phase: string;
  surface: string;
  status: "pending" | "running" | "succeeded" | "failed" | "skipped";
  durationMs?: number;
  error?: string;
  startedAt?: string;
  completedAt?: string;
}

interface LifecycleRun {
  runId: string;
  productId: string;
  phase: string;
  profile: string;
  status: "pending" | "running" | "succeeded" | "failed";
  startedAt: string;
  completedAt?: string;
  executions: LifecyclePhaseExecution[];
}

interface LifecycleRunViewProps {
  run: LifecycleRun;
  onRetry?: (surface: string) => void;
  onRecover?: (surface: string) => void;
}

export function LifecycleRunView({ run, onRetry, onRecover }: LifecycleRunViewProps) {
  const succeededCount = run.executions.filter((e) => e.status === "succeeded").length;
  const failedCount = run.executions.filter((e) => e.status === "failed").length;
  const skippedCount = run.executions.filter((e) => e.status === "skipped").length;

  return (
    <div className="lifecycle-run-view">
      <div className="run-header">
        <h2>Lifecycle Run: {run.runId}</h2>
        <div className="run-meta">
          <span className="product-id">{run.productId}</span>
          <span className="phase">{run.phase}</span>
          <span className="profile">{run.profile}</span>
          <span className={`status ${run.status}`}>{run.status}</span>
        </div>
      </div>

      <div className="run-summary">
        <div className="summary-item succeeded">
          <span className="count">{succeededCount}</span>
          <span className="label">Succeeded</span>
        </div>
        <div className="summary-item failed">
          <span className="count">{failedCount}</span>
          <span className="label">Failed</span>
        </div>
        <div className="summary-item skipped">
          <span className="count">{skippedCount}</span>
          <span className="label">Skipped</span>
        </div>
        <div className="summary-item duration">
          <span className="count">
            {run.completedAt
              ? `${Math.floor((new Date(run.completedAt).getTime() - new Date(run.startedAt).getTime()) / 1000)}s`
              : "Running..."}
          </span>
          <span className="label">Duration</span>
        </div>
      </div>

      <div className="executions-list">
        <h3>Surface Executions</h3>
        {run.executions.map((execution) => (
          <ExecutionItem
            key={`${execution.phase}-${execution.surface}`}
            execution={execution}
            onRetry={onRetry}
            onRecover={onRecover}
          />
        ))}
      </div>
    </div>
  );
}

interface ExecutionItemProps {
  execution: LifecyclePhaseExecution;
  onRetry?: (surface: string) => void;
  onRecover?: (surface: string) => void;
}

function ExecutionItem({ execution, onRetry, onRecover }: ExecutionItemProps) {
  const statusIcon = {
    pending: "⏳",
    running: "🔄",
    succeeded: "✅",
    failed: "❌",
    skipped: "⏭️",
  }[execution.status];

  return (
    <div className={`execution-item ${execution.status}`}>
      <div className="execution-header">
        <span className="status-icon">{statusIcon}</span>
        <span className="surface">{execution.surface}</span>
        <span className="phase">{execution.phase}</span>
        <span className={`status ${execution.status}`}>{execution.status}</span>
        {execution.durationMs && (
          <span className="duration">{execution.durationMs}ms</span>
        )}
      </div>

      {execution.error && (
        <div className="execution-error">
          <span className="error-label">Error:</span>
          <span className="error-message">{execution.error}</span>
          {onRetry && (
            <button onClick={() => onRetry(execution.surface)} className="retry-button">
              Retry
            </button>
          )}
          {onRecover && (
            <button onClick={() => onRecover(execution.surface)} className="recover-button">
              Recover
            </button>
          )}
        </div>
      )}

      <div className="execution-timeline">
        {execution.startedAt && (
          <span className="timeline-start">Started: {new Date(execution.startedAt).toLocaleString()}</span>
        )}
        {execution.completedAt && (
          <span className="timeline-end">Completed: {new Date(execution.completedAt).toLocaleString()}</span>
        )}
      </div>
    </div>
  );
}
