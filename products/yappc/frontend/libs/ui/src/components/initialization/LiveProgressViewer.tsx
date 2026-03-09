/**
 * LiveProgressViewer Component
 *
 * @description Real-time progress display for initialization and deployment
 * operations with step-by-step status updates, logs, and timing information.
 *
 * @doc.type component
 * @doc.purpose progress-display
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <LiveProgressViewer
 *   steps={[
 *     { id: 'provision', name: 'Provisioning', status: 'completed', duration: 5000 },
 *     { id: 'configure', name: 'Configuring', status: 'in-progress', progress: 65 },
 *     { id: 'deploy', name: 'Deploying', status: 'pending' },
 *   ]}
 *   logs={logs}
 *   onCancel={() => handleCancel()}
 *   onRetry={(stepId) => retryStep(stepId)}
 * />
 * ```
 */

import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Step execution status
 */
export type ProgressStepStatus =
  | 'pending'
  | 'in-progress'
  | 'completed'
  | 'failed'
  | 'skipped'
  | 'warning';

/**
 * Log entry level
 */
export type LogLevel = 'info' | 'success' | 'warning' | 'error' | 'debug';

/**
 * Log entry
 */
export interface LogEntry {
  /** Unique ID */
  id: string;
  /** Timestamp */
  timestamp: Date;
  /** Log level */
  level: LogLevel;
  /** Log message */
  message: string;
  /** Associated step ID */
  stepId?: string;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Progress step
 */
export interface ProgressStep {
  /** Unique identifier */
  id: string;
  /** Step name */
  name: string;
  /** Step description */
  description?: string;
  /** Current status */
  status: ProgressStepStatus;
  /** Progress percentage (0-100) for in-progress steps */
  progress?: number;
  /** Duration in milliseconds (for completed steps) */
  duration?: number;
  /** Start time */
  startedAt?: Date;
  /** Completion time */
  completedAt?: Date;
  /** Error message if failed */
  error?: string;
  /** Warning message */
  warning?: string;
  /** Sub-steps */
  subSteps?: ProgressStep[];
  /** Whether this step can be retried */
  retryable?: boolean;
  /** Whether this step can be skipped */
  skippable?: boolean;
}

/**
 * Overall progress summary
 */
export interface ProgressSummary {
  /** Total steps */
  totalSteps: number;
  /** Completed steps */
  completedSteps: number;
  /** Failed steps */
  failedSteps: number;
  /** Overall percentage */
  percentage: number;
  /** Elapsed time in ms */
  elapsedTime: number;
  /** Estimated remaining time in ms */
  estimatedRemaining?: number;
}

/**
 * Props for the LiveProgressViewer component
 */
export interface LiveProgressViewerProps {
  /** Progress steps */
  steps: ProgressStep[];
  /** Log entries */
  logs?: LogEntry[];
  /** Overall operation title */
  title?: string;
  /** Whether operation is in progress */
  isRunning?: boolean;
  /** Start time of the operation */
  startTime?: Date;
  /** Callback to cancel the operation */
  onCancel?: () => void;
  /** Callback to retry a failed step */
  onRetry?: (stepId: string) => void;
  /** Callback to skip a step */
  onSkip?: (stepId: string) => void;
  /** Whether to auto-scroll logs */
  autoScrollLogs?: boolean;
  /** Whether to show logs panel */
  showLogs?: boolean;
  /** Whether to show time estimates */
  showTimeEstimates?: boolean;
  /** Filter logs by step */
  filterByStep?: string;
  /** Compact mode */
  compact?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const formatTime = (date: Date): string => {
  return date.toLocaleTimeString('en-US', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
};

const getStatusIcon = (status: ProgressStepStatus): React.ReactNode => {
  const icons: Record<ProgressStepStatus, React.ReactNode> = {
    pending: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <circle cx="12" cy="12" r="10" opacity="0.3" />
      </svg>
    ),
    'in-progress': (
      <svg className="spinning" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" opacity="0.25" />
        <path d="M12 2a10 10 0 0 1 10 10" strokeLinecap="round" />
      </svg>
    ),
    completed: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
      </svg>
    ),
    failed: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
      </svg>
    ),
    skipped: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4 11H8v-2h8v2z" />
      </svg>
    ),
    warning: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" />
      </svg>
    ),
  };
  return icons[status];
};

const getLogLevelIcon = (level: LogLevel): React.ReactNode => {
  const icons: Record<LogLevel, React.ReactNode> = {
    info: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
      </svg>
    ),
    success: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
      </svg>
    ),
    warning: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" />
      </svg>
    ),
    error: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
      </svg>
    ),
    debug: (
      <svg viewBox="0 0 24 24" fill="currentColor">
        <path d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5c-.49 0-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z" />
      </svg>
    ),
  };
  return icons[level];
};

// ============================================================================
// Sub-Components
// ============================================================================

interface StepItemProps {
  step: ProgressStep;
  onRetry?: () => void;
  onSkip?: () => void;
  compact?: boolean;
}

const StepItem: React.FC<StepItemProps> = ({ step, onRetry, onSkip, compact }) => {
  const [expanded, setExpanded] = useState(
    step.status === 'in-progress' || step.status === 'failed'
  );

  const hasSubSteps = step.subSteps && step.subSteps.length > 0;

  return (
    <div className={`progress-step progress-step--${step.status}`}>
      <div
        className="progress-step-header"
        onClick={hasSubSteps ? () => setExpanded(!expanded) : undefined}
        role={hasSubSteps ? 'button' : undefined}
        tabIndex={hasSubSteps ? 0 : undefined}
        aria-expanded={hasSubSteps ? expanded : undefined}
        onKeyDown={
          hasSubSteps
            ? (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  setExpanded(!expanded);
                }
              }
            : undefined
        }
      >
        <div className="progress-step-icon">{getStatusIcon(step.status)}</div>

        <div className="progress-step-content">
          <div className="progress-step-name">
            {step.name}
            {hasSubSteps && (
              <span className="progress-step-expand-icon">
                {expanded ? '▼' : '▶'}
              </span>
            )}
          </div>

          {!compact && step.description && (
            <div className="progress-step-description">{step.description}</div>
          )}

          {step.status === 'in-progress' && step.progress !== undefined && (
            <div className="progress-step-bar-container">
              <div
                className="progress-step-bar"
                style={{ width: `${step.progress}%` }}
              />
            </div>
          )}

          {step.error && (
            <div className="progress-step-error">{step.error}</div>
          )}

          {step.warning && (
            <div className="progress-step-warning">{step.warning}</div>
          )}
        </div>

        <div className="progress-step-meta">
          {step.status === 'in-progress' && step.progress !== undefined && (
            <span className="progress-step-percentage">{step.progress}%</span>
          )}

          {step.duration !== undefined && (
            <span className="progress-step-duration">
              {formatDuration(step.duration)}
            </span>
          )}

          {step.status === 'failed' && step.retryable && onRetry && (
            <button
              type="button"
              className="progress-step-action"
              onClick={(e) => {
                e.stopPropagation();
                onRetry();
              }}
            >
              Retry
            </button>
          )}

          {step.status === 'pending' && step.skippable && onSkip && (
            <button
              type="button"
              className="progress-step-action progress-step-action--secondary"
              onClick={(e) => {
                e.stopPropagation();
                onSkip();
              }}
            >
              Skip
            </button>
          )}
        </div>
      </div>

      {hasSubSteps && expanded && (
        <div className="progress-step-substeps">
          {step.subSteps!.map((subStep) => (
            <StepItem key={subStep.id} step={subStep} compact />
          ))}
        </div>
      )}
    </div>
  );
};

interface LogViewerProps {
  logs: LogEntry[];
  filterByStep?: string;
  autoScroll: boolean;
}

const LogViewer: React.FC<LogViewerProps> = ({
  logs,
  filterByStep,
  autoScroll,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [showDebug, setShowDebug] = useState(false);

  const filteredLogs = useMemo(() => {
    let filtered = logs;

    if (filterByStep) {
      filtered = filtered.filter((log) => log.stepId === filterByStep);
    }

    if (!showDebug) {
      filtered = filtered.filter((log) => log.level !== 'debug');
    }

    return filtered;
  }, [logs, filterByStep, showDebug]);

  useEffect(() => {
    if (autoScroll && containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [filteredLogs, autoScroll]);

  return (
    <div className="log-viewer">
      <div className="log-viewer-header">
        <span className="log-viewer-title">Logs</span>
        <div className="log-viewer-controls">
          <label className="log-viewer-toggle">
            <input
              type="checkbox"
              checked={showDebug}
              onChange={(e) => setShowDebug(e.target.checked)}
            />
            <span>Show debug</span>
          </label>
        </div>
      </div>

      <div className="log-viewer-content" ref={containerRef}>
        {filteredLogs.length === 0 ? (
          <div className="log-viewer-empty">No logs to display</div>
        ) : (
          filteredLogs.map((log) => (
            <div key={log.id} className={`log-entry log-entry--${log.level}`}>
              <span className="log-entry-time">
                {formatTime(log.timestamp)}
              </span>
              <span className="log-entry-icon">{getLogLevelIcon(log.level)}</span>
              <span className="log-entry-message">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const LiveProgressViewer: React.FC<LiveProgressViewerProps> = ({
  steps,
  logs = [],
  title = 'Progress',
  isRunning = false,
  startTime,
  onCancel,
  onRetry,
  onSkip,
  autoScrollLogs = true,
  showLogs = true,
  showTimeEstimates = true,
  filterByStep,
  compact = false,
  className = '',
}) => {
  const [elapsedTime, setElapsedTime] = useState(0);

  // Calculate progress summary
  const summary = useMemo((): ProgressSummary => {
    const totalSteps = steps.length;
    const completedSteps = steps.filter(
      (s) => s.status === 'completed' || s.status === 'skipped'
    ).length;
    const failedSteps = steps.filter((s) => s.status === 'failed').length;

    // Calculate weighted percentage
    let percentage = 0;
    if (totalSteps > 0) {
      const completedWeight = completedSteps * 100;
      const inProgressStep = steps.find((s) => s.status === 'in-progress');
      const inProgressWeight = inProgressStep?.progress || 0;
      percentage = Math.round(
        (completedWeight + inProgressWeight) / totalSteps
      );
    }

    // Calculate time estimates
    const completedDurations = steps
      .filter((s) => s.status === 'completed' && s.duration !== undefined)
      .map((s) => s.duration!);

    let estimatedRemaining: number | undefined;
    if (completedDurations.length > 0 && completedSteps < totalSteps) {
      const avgDuration =
        completedDurations.reduce((a, b) => a + b, 0) / completedDurations.length;
      estimatedRemaining = Math.round(avgDuration * (totalSteps - completedSteps));
    }

    return {
      totalSteps,
      completedSteps,
      failedSteps,
      percentage,
      elapsedTime,
      estimatedRemaining,
    };
  }, [steps, elapsedTime]);

  // Elapsed time timer
  useEffect(() => {
    if (!isRunning || !startTime) return;

    const interval = setInterval(() => {
      setElapsedTime(Date.now() - startTime.getTime());
    }, 1000);

    return () => clearInterval(interval);
  }, [isRunning, startTime]);

  // Reset elapsed time when startTime changes
  useEffect(() => {
    if (startTime) {
      setElapsedTime(Date.now() - startTime.getTime());
    }
  }, [startTime]);

  const handleRetry = useCallback(
    (stepId: string) => {
      onRetry?.(stepId);
    },
    [onRetry]
  );

  const handleSkip = useCallback(
    (stepId: string) => {
      onSkip?.(stepId);
    },
    [onSkip]
  );

  const containerClasses = [
    'live-progress-viewer',
    compact && 'live-progress-viewer--compact',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClasses}>
      {/* Header */}
      <div className="progress-header">
        <h3 className="progress-title">{title}</h3>

        {isRunning && onCancel && (
          <button
            type="button"
            className="progress-cancel-btn"
            onClick={onCancel}
          >
            Cancel
          </button>
        )}
      </div>

      {/* Overall Progress Bar */}
      <div className="progress-overall">
        <div className="progress-overall-bar-container">
          <div
            className={`progress-overall-bar ${
              summary.failedSteps > 0 ? 'progress-overall-bar--error' : ''
            }`}
            style={{ width: `${summary.percentage}%` }}
          />
        </div>
        <div className="progress-overall-meta">
          <span className="progress-overall-percentage">
            {summary.percentage}%
          </span>
          <span className="progress-overall-steps">
            {summary.completedSteps} of {summary.totalSteps} steps
          </span>
        </div>
      </div>

      {/* Time Estimates */}
      {showTimeEstimates && (
        <div className="progress-times">
          <div className="progress-time-item">
            <span className="progress-time-label">Elapsed</span>
            <span className="progress-time-value">
              {formatDuration(summary.elapsedTime)}
            </span>
          </div>

          {summary.estimatedRemaining !== undefined && isRunning && (
            <div className="progress-time-item">
              <span className="progress-time-label">Remaining</span>
              <span className="progress-time-value">
                ~{formatDuration(summary.estimatedRemaining)}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Steps List */}
      <div className="progress-steps">
        {steps.map((step) => (
          <StepItem
            key={step.id}
            step={step}
            onRetry={onRetry ? () => handleRetry(step.id) : undefined}
            onSkip={onSkip ? () => handleSkip(step.id) : undefined}
            compact={compact}
          />
        ))}
      </div>

      {/* Logs Panel */}
      {showLogs && (
        <LogViewer
          logs={logs}
          filterByStep={filterByStep}
          autoScroll={autoScrollLogs}
        />
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .live-progress-viewer {
          display: flex;
          flex-direction: column;
          gap: 1rem;
          background: #fff;
          border-radius: 12px;
          padding: 1.25rem;
        }

        .live-progress-viewer--compact {
          padding: 1rem;
          gap: 0.75rem;
        }

        .progress-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .progress-title {
          margin: 0;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .progress-cancel-btn {
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #EF4444;
          background: transparent;
          border: 1px solid #EF4444;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .progress-cancel-btn:hover {
          background: #FEF2F2;
        }

        .progress-overall {
          display: flex;
          flex-direction: column;
          gap: 0.375rem;
        }

        .progress-overall-bar-container {
          height: 8px;
          background: #E5E7EB;
          border-radius: 4px;
          overflow: hidden;
        }

        .progress-overall-bar {
          height: 100%;
          background: linear-gradient(90deg, #3B82F6 0%, #2563EB 100%);
          border-radius: 4px;
          transition: width 0.3s ease;
        }

        .progress-overall-bar--error {
          background: linear-gradient(90deg, #EF4444 0%, #DC2626 100%);
        }

        .progress-overall-meta {
          display: flex;
          justify-content: space-between;
          font-size: 0.75rem;
        }

        .progress-overall-percentage {
          font-weight: 600;
          color: #3B82F6;
        }

        .progress-overall-steps {
          color: #6B7280;
        }

        .progress-times {
          display: flex;
          gap: 1.5rem;
        }

        .progress-time-item {
          display: flex;
          flex-direction: column;
        }

        .progress-time-label {
          font-size: 0.625rem;
          text-transform: uppercase;
          color: #9CA3AF;
        }

        .progress-time-value {
          font-size: 0.875rem;
          font-weight: 500;
          color: #374151;
        }

        .progress-steps {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .progress-step {
          background: #F9FAFB;
          border-radius: 8px;
          overflow: hidden;
          transition: all 0.2s ease;
        }

        .progress-step--pending {
          opacity: 0.6;
        }

        .progress-step--in-progress {
          background: #EFF6FF;
          border: 1px solid #BFDBFE;
        }

        .progress-step--completed {
          background: #F0FDF4;
        }

        .progress-step--failed {
          background: #FEF2F2;
          border: 1px solid #FECACA;
        }

        .progress-step--warning {
          background: #FFFBEB;
          border: 1px solid #FED7AA;
        }

        .progress-step--skipped {
          opacity: 0.5;
        }

        .progress-step-header {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
          padding: 0.75rem;
          cursor: default;
        }

        .progress-step-header[role="button"] {
          cursor: pointer;
        }

        .progress-step-header[role="button"]:hover {
          background: rgba(0, 0, 0, 0.02);
        }

        .progress-step-icon {
          width: 20px;
          height: 20px;
          flex-shrink: 0;
        }

        .progress-step--pending .progress-step-icon {
          color: #D1D5DB;
        }

        .progress-step--in-progress .progress-step-icon {
          color: #3B82F6;
        }

        .progress-step--completed .progress-step-icon {
          color: #10B981;
        }

        .progress-step--failed .progress-step-icon {
          color: #EF4444;
        }

        .progress-step--warning .progress-step-icon {
          color: #F59E0B;
        }

        .progress-step--skipped .progress-step-icon {
          color: #9CA3AF;
        }

        .progress-step-icon svg {
          width: 100%;
          height: 100%;
        }

        .progress-step-icon .spinning {
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        .progress-step-content {
          flex: 1;
          min-width: 0;
        }

        .progress-step-name {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .progress-step-expand-icon {
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .progress-step-description {
          font-size: 0.75rem;
          color: #6B7280;
          margin-top: 0.125rem;
        }

        .progress-step-bar-container {
          height: 4px;
          background: #DBEAFE;
          border-radius: 2px;
          margin-top: 0.5rem;
          overflow: hidden;
        }

        .progress-step-bar {
          height: 100%;
          background: #3B82F6;
          border-radius: 2px;
          transition: width 0.3s ease;
        }

        .progress-step-error {
          font-size: 0.75rem;
          color: #EF4444;
          margin-top: 0.375rem;
        }

        .progress-step-warning {
          font-size: 0.75rem;
          color: #F59E0B;
          margin-top: 0.375rem;
        }

        .progress-step-meta {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .progress-step-percentage {
          font-size: 0.75rem;
          font-weight: 600;
          color: #3B82F6;
        }

        .progress-step-duration {
          font-size: 0.75rem;
          color: #9CA3AF;
        }

        .progress-step-action {
          padding: 0.25rem 0.5rem;
          font-size: 0.625rem;
          font-weight: 500;
          color: #fff;
          background: #3B82F6;
          border: none;
          border-radius: 4px;
          cursor: pointer;
          transition: background 0.15s ease;
        }

        .progress-step-action:hover {
          background: #2563EB;
        }

        .progress-step-action--secondary {
          color: #6B7280;
          background: #E5E7EB;
        }

        .progress-step-action--secondary:hover {
          background: #D1D5DB;
        }

        .progress-step-substeps {
          padding: 0 0.75rem 0.75rem 2.75rem;
          display: flex;
          flex-direction: column;
          gap: 0.375rem;
        }

        .progress-step-substeps .progress-step {
          background: rgba(255, 255, 255, 0.5);
        }

        .log-viewer {
          border-top: 1px solid #E5E7EB;
          padding-top: 1rem;
        }

        .log-viewer-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 0.5rem;
        }

        .log-viewer-title {
          font-size: 0.75rem;
          font-weight: 600;
          color: #374151;
        }

        .log-viewer-controls {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .log-viewer-toggle {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          font-size: 0.625rem;
          color: #6B7280;
          cursor: pointer;
        }

        .log-viewer-toggle input {
          width: 12px;
          height: 12px;
        }

        .log-viewer-content {
          max-height: 200px;
          overflow-y: auto;
          background: #1F2937;
          border-radius: 6px;
          padding: 0.5rem;
          font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
          font-size: 0.6875rem;
        }

        .log-viewer-empty {
          color: #6B7280;
          text-align: center;
          padding: 1rem;
        }

        .log-entry {
          display: flex;
          align-items: flex-start;
          gap: 0.5rem;
          padding: 0.25rem 0;
        }

        .log-entry-time {
          color: #6B7280;
          flex-shrink: 0;
        }

        .log-entry-icon {
          width: 14px;
          height: 14px;
          flex-shrink: 0;
        }

        .log-entry--info .log-entry-icon {
          color: #3B82F6;
        }

        .log-entry--success .log-entry-icon {
          color: #10B981;
        }

        .log-entry--warning .log-entry-icon {
          color: #F59E0B;
        }

        .log-entry--error .log-entry-icon {
          color: #EF4444;
        }

        .log-entry--debug .log-entry-icon {
          color: #8B5CF6;
        }

        .log-entry-icon svg {
          width: 100%;
          height: 100%;
        }

        .log-entry-message {
          color: #E5E7EB;
          word-break: break-word;
        }

        .log-entry--info .log-entry-message {
          color: #93C5FD;
        }

        .log-entry--success .log-entry-message {
          color: #6EE7B7;
        }

        .log-entry--warning .log-entry-message {
          color: #FCD34D;
        }

        .log-entry--error .log-entry-message {
          color: #FCA5A5;
        }

        .log-entry--debug .log-entry-message {
          color: #C4B5FD;
        }
      `}</style>
    </div>
  );
};

LiveProgressViewer.displayName = 'LiveProgressViewer';

export default LiveProgressViewer;
