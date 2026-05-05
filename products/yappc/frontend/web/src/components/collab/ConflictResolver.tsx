import { useEffect, useId, useMemo, useState } from 'react';

import type { Conflict, ResolutionSuggestion } from 'yappc-collab/crdt';

export interface ManualConflictResolution {
  conflictId: string;
  strategy: 'user-guided' | 'merge';
  resolvedValue: unknown;
  selectedSource?: 'operationA' | 'operationB';
  notes?: string;
}

export interface ConflictResolverProps {
  conflict: Conflict;
  suggestedResolutions?: ResolutionSuggestion[];
  isResolving?: boolean;
  className?: string;
  onResolve: (resolution: ManualConflictResolution) => void | Promise<void>;
  onCancel?: () => void;
}

function formatJson(value: unknown): string {
  return JSON.stringify(value, null, 2) ?? 'null';
}

function parseJson(value: string): { parsed?: unknown; error?: string } {
  try {
    return { parsed: JSON.parse(value) };
  } catch (error) {
    return {
      error: error instanceof Error ? error.message : 'Unable to parse merge payload.',
    };
  }
}

function formatTimestamp(timestamp: number): string {
  if (!Number.isFinite(timestamp)) {
    return 'Unknown';
  }

  return new Date(timestamp).toLocaleString();
}

function formatVectorClock(conflict: Conflict, source: 'operationA' | 'operationB'): string {
  const entries = [...conflict[source].vectorClock.values.entries()];
  if (entries.length === 0) {
    return 'No causality data';
  }

  return entries.map(([replicaId, value]) => `${replicaId}:${value}`).join(', ');
}

function createInitialMergeValue(
  conflict: Conflict,
  suggestions: ResolutionSuggestion[]
): string {
  const mergeSuggestion = suggestions.find((suggestion) => suggestion.strategy === 'merge');
  if (mergeSuggestion?.resultingValue !== undefined) {
    return formatJson(mergeSuggestion.resultingValue);
  }

  if (
    typeof conflict.operationA.data === 'object' &&
    conflict.operationA.data !== null &&
    typeof conflict.operationB.data === 'object' &&
    conflict.operationB.data !== null &&
    !Array.isArray(conflict.operationA.data) &&
    !Array.isArray(conflict.operationB.data)
  ) {
    return formatJson({
      ...conflict.operationA.data,
      ...conflict.operationB.data,
    });
  }

  return formatJson({
    operationA: conflict.operationA.data,
    operationB: conflict.operationB.data,
  });
}

interface OperationCardProps {
  title: string;
  source: 'operationA' | 'operationB';
  conflict: Conflict;
  onChoose: (source: 'operationA' | 'operationB') => void;
  disabled: boolean;
}

function OperationCard({ title, source, conflict, onChoose, disabled }: OperationCardProps) {
  const operation = conflict[source];

  return (
    <section
      aria-label={title}
      className="rounded-2xl border border-border bg-surface/60 p-4 shadow-sm"
    >
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-info-color">
            {title}
          </p>
          <h3 className="mt-1 text-base font-semibold text-fg-muted">
            {operation.type} on {operation.targetId}
          </h3>
        </div>
        <button
          type="button"
          className="rounded-full border border-info-border/50 px-3 py-1 text-sm font-medium text-info-color transition hover:border-info-border hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => onChoose(source)}
          disabled={disabled}
        >
          Keep this version
        </button>
      </div>

      <dl className="grid gap-2 text-sm text-fg-muted sm:grid-cols-2">
        <div>
          <dt className="text-xs uppercase tracking-wide text-fg-muted">Replica</dt>
          <dd>{operation.replicaId}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-fg-muted">Timestamp</dt>
          <dd>{formatTimestamp(operation.timestamp)}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs uppercase tracking-wide text-fg-muted">Vector clock</dt>
          <dd>{formatVectorClock(conflict, source)}</dd>
        </div>
      </dl>

      <div className="mt-4">
        <p className="mb-2 text-xs uppercase tracking-wide text-fg-muted">Payload</p>
        <pre className="overflow-x-auto rounded-xl bg-surface/80 p-3 text-xs leading-6 text-fg-muted">
          {formatJson(operation.data)}
        </pre>
      </div>
    </section>
  );
}

export function ConflictResolver({
  conflict,
  suggestedResolutions = [],
  isResolving = false,
  className,
  onResolve,
  onCancel,
}: ConflictResolverProps) {
  const mergeInputId = useId();
  const notesInputId = useId();
  const [mergeValue, setMergeValue] = useState<string>(() =>
    createInitialMergeValue(conflict, suggestedResolutions)
  );
  const [notes, setNotes] = useState<string>('');

  useEffect(() => {
    setMergeValue(createInitialMergeValue(conflict, suggestedResolutions));
    setNotes('');
  }, [conflict, suggestedResolutions]);

  const mergeState = useMemo(() => parseJson(mergeValue), [mergeValue]);

  const handleSelectVersion = (source: 'operationA' | 'operationB') => {
    void onResolve({
      conflictId: conflict.id,
      strategy: 'user-guided',
      selectedSource: source,
      resolvedValue: conflict[source].data,
      notes: notes.trim() || undefined,
    });
  };

  const handleManualMerge = () => {
    if (mergeState.parsed === undefined) {
      return;
    }

    void onResolve({
      conflictId: conflict.id,
      strategy: 'merge',
      resolvedValue: mergeState.parsed,
      notes: notes.trim() || undefined,
    });
  };

  return (
    <section
      aria-label="Manual conflict resolution"
      className={[
        'rounded-[28px] border border-border bg-[radial-gradient(circle_at_top_left,_rgba(45,212,191,0.12),_transparent_35%),linear-gradient(180deg,_rgba(9,9,11,0.98),_rgba(24,24,27,0.98))] p-6 text-fg-muted shadow-2xl shadow-zinc-950/40',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      data-testid="conflict-resolver"
    >
      <div className="flex flex-col gap-3 border-b border-border pb-5 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-warning-color">
            User intervention required
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-white">Resolve {conflict.type}</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-fg-muted">
            Two concurrent operations touched {conflict.targetId}. Pick the version that should win,
            or compose a merged payload and submit that to the collaboration service.
          </p>
        </div>
        <div className="rounded-2xl border border-warning-border/25 bg-warning-bg/10 px-4 py-3 text-sm text-warning-color">
          <div className="font-medium">Severity: {conflict.severity}</div>
          <div className="mt-1 text-warning-color/80">Conflict ID: {conflict.id}</div>
        </div>
      </div>

      {suggestedResolutions.length > 0 ? (
        <div className="mt-5 rounded-2xl border border-border bg-surface/60 p-4">
          <p className="text-xs font-semibold uppercase tracking-[0.25em] text-fg-muted">
            Engine suggestions
          </p>
          <ul className="mt-3 grid gap-3 lg:grid-cols-2">
            {suggestedResolutions.map((suggestion) => (
              <li key={suggestion.id} className="rounded-xl border border-border bg-surface/70 p-3">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-white">{suggestion.strategy}</span>
                  <span className="rounded-full bg-surface px-2 py-1 text-xs text-fg-muted">
                    {(suggestion.confidence * 100).toFixed(0)}% confidence
                  </span>
                </div>
                <p className="mt-2 text-sm leading-6 text-fg-muted">{suggestion.description}</p>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="mt-6 grid gap-4 xl:grid-cols-2">
        <OperationCard
          title="Version A"
          source="operationA"
          conflict={conflict}
          onChoose={handleSelectVersion}
          disabled={isResolving}
        />
        <OperationCard
          title="Version B"
          source="operationB"
          conflict={conflict}
          onChoose={handleSelectVersion}
          disabled={isResolving}
        />
      </div>

      <div className="mt-6 rounded-2xl border border-border bg-surface/60 p-4">
        <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.25em] text-info-color">
              Manual merge
            </p>
            <h3 className="mt-1 text-lg font-semibold text-white">Compose the final payload</h3>
          </div>
          <p className="text-sm text-fg-muted">Submit valid JSON to send the resolved state upstream.</p>
        </div>

        <div className="mt-4 grid gap-4">
          <div>
            <label htmlFor={mergeInputId} className="mb-2 block text-sm font-medium text-fg-muted">
              Merged payload
            </label>
            <textarea
              id={mergeInputId}
              value={mergeValue}
              onChange={(event) => setMergeValue(event.target.value)}
              spellCheck={false}
              className="min-h-56 w-full rounded-2xl border border-border bg-surface/90 px-4 py-3 font-mono text-sm leading-6 text-fg-muted outline-none transition focus:border-info-border"
              aria-invalid={mergeState.error ? 'true' : 'false'}
            />
            {mergeState.error ? (
              <p className="mt-2 text-sm text-rose-300" role="alert">
                Merge payload is invalid JSON: {mergeState.error}
              </p>
            ) : (
              <p className="mt-2 text-sm text-emerald-300">Merge payload is valid JSON.</p>
            )}
          </div>

          <div>
            <label htmlFor={notesInputId} className="mb-2 block text-sm font-medium text-fg-muted">
              Resolution notes
            </label>
            <textarea
              id={notesInputId}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              className="min-h-24 w-full rounded-2xl border border-border bg-surface/90 px-4 py-3 text-sm leading-6 text-fg-muted outline-none transition focus:border-info-border"
              placeholder="Explain why this resolution should be applied."
            />
          </div>
        </div>
      </div>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
        {onCancel ? (
          <button
            type="button"
            className="rounded-full border border-border px-4 py-2 text-sm font-medium text-fg-muted transition hover:border-border hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
            onClick={onCancel}
            disabled={isResolving}
          >
            Cancel
          </button>
        ) : null}
        <button
          type="button"
          className="rounded-full bg-info-bg px-5 py-2 text-sm font-semibold text-fg transition hover:bg-info-bg disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-fg-muted"
          onClick={handleManualMerge}
          disabled={isResolving || mergeState.parsed === undefined}
        >
          Apply merged resolution
        </button>
      </div>
    </section>
  );
}

export default ConflictResolver;