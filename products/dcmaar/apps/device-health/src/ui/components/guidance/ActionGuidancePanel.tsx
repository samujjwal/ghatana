/**
 * @fileoverview Action guidance panel rendering playbook actions.
 *
 * Surfaces prioritized remediation steps, code samples, and resources
 * for a given metric/severity pairing with lightweight progress tracking.
 *
 * @module ui/components/guidance
 */

import React, { useMemo, useState, useEffect, useCallback } from 'react';
import {
  getPlaybook,
  getPlaybookActions,
  type PlaybookAction,
} from '../../../analytics/guidance/ActionPlaybooks';

type ActionStatus = 'not-started' | 'in-progress' | 'completed';

interface ActionGuidancePanelProps {
  metric: string;
  severity: 'warning' | 'critical';
  currentValue?: number;
  onStartAction?: (actionId: string) => void;
  onCompleteAction?: (actionId: string) => void;
}

interface PlaybookProgressRecord {
  [metricSeverity: string]: {
    [actionId: string]: ActionStatus;
  };
}

const STORAGE_KEY = 'dcmaar:guidance-progress:v1';

const metricSeverityKey = (metric: string, severity: 'warning' | 'critical') =>
  `${metric}:${severity}`;

const statusMeta: Record<
  ActionStatus,
  { label: string; badgeClass: string; textClass: string }
> = {
  'not-started': {
    label: 'Not started',
    badgeClass: 'bg-slate-100 text-slate-600',
    textClass: 'text-slate-500',
  },
  'in-progress': {
    label: 'In progress',
    badgeClass: 'bg-amber-100 text-amber-700',
    textClass: 'text-amber-600',
  },
  completed: {
    label: 'Completed',
    badgeClass: 'bg-emerald-100 text-emerald-700',
    textClass: 'text-emerald-600',
  },
};

const priorityLabel: Record<PlaybookAction['priority'], string> = {
  high: 'High priority',
  medium: 'Medium priority',
  low: 'Quick win',
};

const difficultyLabel: Record<PlaybookAction['difficulty'], string> = {
  easy: 'Easy',
  moderate: 'Moderate',
  advanced: 'Advanced',
};

const loadStoredProgress = (): PlaybookProgressRecord => {
  if (typeof window === 'undefined') {
    return {};
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {};
    }
    return JSON.parse(raw) as PlaybookProgressRecord;
  } catch {
    return {};
  }
};

const persistProgress = (progress: PlaybookProgressRecord) => {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(progress));
  } catch {
    // Ignore storage exceptions (e.g., quota exceeded)
  }
};

const usePlaybookProgress = (
  metric: string,
  severity: 'warning' | 'critical'
): [Record<string, ActionStatus>, (actionId: string, status: ActionStatus) => void] => {
  const storageKey = metricSeverityKey(metric, severity);
  const [progress, setProgress] = useState<Record<string, ActionStatus>>({});

  useEffect(() => {
    const stored = loadStoredProgress();
    if (stored[storageKey]) {
      setProgress(stored[storageKey]);
    } else {
      setProgress({});
    }
  }, [storageKey]);

  const updateStatus = useCallback((actionId: string, status: ActionStatus) => {
    setProgress((prev) => {
      const next = { ...prev, [actionId]: status };
      const stored = loadStoredProgress();
      persistProgress({
        ...stored,
        [storageKey]: next,
      });
      return next;
    });
  }, [storageKey]);

  return [progress, updateStatus];
};

const copyCodeToClipboard = async (code: string, onSuccess: () => void, onFailure: () => void) => {
  if (navigator?.clipboard) {
    try {
      await navigator.clipboard.writeText(code);
      onSuccess();
      return;
    } catch {
      // Fall through to manual copy
    }
  }

  try {
    const textArea = document.createElement('textarea');
    textArea.value = code;
    textArea.style.position = 'fixed';
    textArea.style.opacity = '0';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    const successful = document.execCommand('copy');
    document.body.removeChild(textArea);
    if (successful) {
      onSuccess();
    } else {
      onFailure();
    }
  } catch {
    onFailure();
  }
};

const ActionCard: React.FC<{
  action: PlaybookAction;
  status: ActionStatus;
  onStatusChange: (status: ActionStatus) => void;
  onStartAction?: () => void;
  onCompleteAction?: () => void;
}> = ({ action, status, onStatusChange, onStartAction, onCompleteAction }) => {
  const [expanded, setExpanded] = useState(status !== 'not-started');
  const [copyState, setCopyState] = useState<Record<string, 'idle' | 'copied' | 'error'>>({});

  useEffect(() => {
    if (status === 'in-progress') {
      onStartAction?.();
    } else if (status === 'completed') {
      onCompleteAction?.();
    }
  }, [status, onStartAction, onCompleteAction]);

  const handleCopy = (id: string, code: string) => {
    copyCodeToClipboard(
      code,
      () => setCopyState((prev) => ({ ...prev, [id]: 'copied' })),
      () => setCopyState((prev) => ({ ...prev, [id]: 'error' }))
    );
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${statusMeta[status].badgeClass}`}
            >
              {statusMeta[status].label}
            </span>
            <span className="text-xs text-slate-400">•</span>
            <span className="text-xs font-medium text-slate-500">
              {priorityLabel[action.priority]}
            </span>
            <span className="text-xs text-slate-400">•</span>
            <span className="text-xs font-medium text-slate-500">
              {difficultyLabel[action.difficulty]}
            </span>
          </div>
          <h4 className="mt-2 text-base font-semibold text-slate-900">{action.title}</h4>
          <p className="mt-1 text-sm text-slate-600">{action.description}</p>
        </div>
        <button
          className="text-sm text-blue-600 hover:text-blue-700"
          onClick={() => setExpanded((open) => !open)}
        >
          {expanded ? 'Hide details' : 'Show details'}
        </button>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-slate-500">
        <span className="font-medium text-slate-600">
          Expected improvement: <span className="text-emerald-600">{action.expectedImprovement}</span>
        </span>
        <span className="text-slate-400">•</span>
        <span>Time to implement: {action.timeToImplement}</span>
      </div>

      <div className="mt-4">
        <label htmlFor={`${action.id}-status`} className="text-xs font-semibold text-slate-600">
          Progress
        </label>
        <select
          id={`${action.id}-status`}
          className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
          value={status}
          onChange={(event) => onStatusChange(event.target.value as ActionStatus)}
        >
          <option value="not-started">Not started</option>
          <option value="in-progress">In progress</option>
          <option value="completed">Completed</option>
        </select>
      </div>

      {expanded && (
        <div className="mt-4 space-y-4 border-t border-slate-200 pt-4">
          <div>
            <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Implementation steps
            </h5>
            <ol className="mt-2 list-inside list-decimal space-y-1 text-sm text-slate-600">
              {action.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </div>

          {action.codeExamples && action.codeExamples.length > 0 && (
            <div className="space-y-3">
              <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Code examples
              </h5>
              {action.codeExamples.map((example, index) => {
                const exampleId = `${action.id}-code-${index}`;
                const state = copyState[exampleId] || 'idle';
                return (
                  <div key={exampleId} className="rounded-lg border border-slate-200 bg-slate-900 p-3">
                    <div className="mb-2 flex items-center justify-between text-xs text-slate-300">
                      <span>{example.title ?? `Example ${index + 1}`}</span>
                      <button
                        className="rounded border border-slate-500 px-2 py-0.5 text-xs font-medium text-slate-100 hover:bg-slate-800"
                        onClick={() => handleCopy(exampleId, example.code)}
                      >
                        {state === 'copied'
                          ? 'Copied!'
                          : state === 'error'
                          ? 'Copy failed'
                          : 'Copy'}
                      </button>
                    </div>
                    <pre className="overflow-x-auto text-xs leading-5 text-slate-100">
                      <code>{example.code}</code>
                    </pre>
                  </div>
                );
              })}
            </div>
          )}

          <div>
            <h5 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Resources
            </h5>
            <ul className="mt-2 space-y-1 text-sm">
              {action.resources.map((resource) => (
                <li key={resource.url}>
                  <a
                    className="text-blue-600 hover:text-blue-700 hover:underline"
                    href={resource.url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {resource.title}
                  </a>
                  {resource.type && (
                    <span className="ml-2 text-xs uppercase tracking-wide text-slate-400">
                      {resource.type}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
};

export const ActionGuidancePanel: React.FC<ActionGuidancePanelProps> = ({
  metric,
  severity,
  currentValue,
  onStartAction,
  onCompleteAction,
}) => {
  const playbook = useMemo(() => getPlaybook(metric, severity), [metric, severity]);
  const actions = useMemo(() => getPlaybookActions(metric, severity), [metric, severity]);
  const [progress, updateStatus] = usePlaybookProgress(metric, severity);

  if (!playbook) {
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
        Guidance playbook not available for this metric yet.
      </div>
    );
  }

  const completedActions = actions.filter(
    (action) => progress[action.id] === 'completed'
  ).length;

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h3 className="text-base font-semibold text-blue-900">{playbook.title}</h3>
            <p className="mt-1 text-sm text-blue-800">{playbook.summary}</p>
          </div>
          <div className="flex flex-col items-end gap-1 text-xs text-blue-800">
            <span>
              Status:{' '}
              <strong>
                {completedActions}/{actions.length} complete
              </strong>
            </span>
            {typeof currentValue === 'number' && (
              <span>Current value: {Math.round(currentValue)}</span>
            )}
          </div>
        </div>
      </div>

      <div className="space-y-4">
        {actions.map((action) => (
          <ActionCard
            key={action.id}
            action={action}
            status={progress[action.id] ?? 'not-started'}
            onStatusChange={(status) => updateStatus(action.id, status)}
            onStartAction={() => onStartAction?.(action.id)}
            onCompleteAction={() => onCompleteAction?.(action.id)}
          />
        ))}
      </div>
    </div>
  );
};

export default ActionGuidancePanel;
