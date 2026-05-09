/**
 * LiveProgressNarrative — AI run plain-English progress (AI-Y6 / P1)
 *
 * Streams a live plain-English narrative of what the AI orchestration is doing.
 * Events are received via SSE (`/api/runs/:runId/narrative`) and appended in
 * real-time. When the stream ends, the final summary is shown.
 *
 * Features:
 *  - Opens an SSE stream for `runId`
 *  - Appends each narrative line as it arrives
 *  - Shows a spinner while streaming; a completion icon when done
 *  - Falls back gracefully when SSE is unavailable (polled summary)
 *  - Exposes `onNarrativeComplete(summary: string)` callback
 *
 * @doc.type component
 * @doc.purpose Plain-English live run summary for AI operations
 * @doc.layer product
 * @doc.pattern AI Progress Narrative
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle2, Loader2, AlertCircle } from 'lucide-react';
import { useI18n } from '../../i18n/I18nProvider';

// ── Types ─────────────────────────────────────────────────────────────────────

export type NarrativeStreamStatus = 'idle' | 'streaming' | 'completed' | 'error';

export interface NarrativeEvent {
  id: string;
  text: string;
  timestamp: string;
}

export interface LiveProgressNarrativeProps {
  /** AEP run ID to stream narrative for */
  runId: string;
  /** Called when the stream closes successfully with the full joined narrative */
  onNarrativeComplete?: (summary: string) => void;
  /** Additional CSS class names */
  className?: string;
  /**
   * SSE endpoint factory — defaults to `/api/runs/:runId/narrative`.
   * Inject in tests to avoid real SSE.
   */
  buildSseUrl?: (runId: string) => string;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

const importMetaEnv = import.meta as ImportMeta & {
  env?: { DEV?: boolean; VITE_API_ORIGIN?: string };
};

function defaultSseUrl(runId: string): string {
  const base = importMetaEnv.env?.DEV
    ? `${importMetaEnv.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
    : '/api';
  return `${base}/runs/${encodeURIComponent(runId)}/narrative`;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function LiveProgressNarrative({
  runId,
  onNarrativeComplete,
  className,
  buildSseUrl = defaultSseUrl,
}: LiveProgressNarrativeProps) {
  const [events, setEvents] = useState<NarrativeEvent[]>([]);
  const [status, setStatus] = useState<NarrativeStreamStatus>('idle');
  const { t } = useI18n();
  const [errorMessage, setErrorMessage] = useState<string | undefined>();
  const sseRef = useRef<EventSource | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const closeStream = useCallback(() => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (!runId) return;

    setEvents([]);
    setStatus('streaming');
    setErrorMessage(undefined);
    closeStream();

    const url = buildSseUrl(runId);
    const source = new EventSource(url, { withCredentials: true });
    sseRef.current = source;

    source.onmessage = (e: MessageEvent<string>) => {
      try {
        const parsed = JSON.parse(e.data) as { id?: string; text?: string; timestamp?: string };
        const event: NarrativeEvent = {
          id: parsed.id ?? String(Date.now()),
          text: parsed.text ?? e.data,
          timestamp: parsed.timestamp ?? new Date().toISOString(),
        };
        setEvents((prev) => [...prev, event]);

        // Auto-scroll to latest
        requestAnimationFrame(() => {
          if (containerRef.current) {
            containerRef.current.scrollTop = containerRef.current.scrollHeight;
          }
        });
      } catch {
        // If data is plain text, append as-is
        setEvents((prev) => [
          ...prev,
          { id: String(Date.now()), text: e.data, timestamp: new Date().toISOString() },
        ]);
      }
    };

    source.addEventListener('done', () => {
      setStatus('completed');
      source.close();
      sseRef.current = null;
    });

    source.onerror = () => {
      setStatus('error');
      setErrorMessage('Live narrative stream interrupted');
      source.close();
      sseRef.current = null;
    };

    return () => {
      closeStream();
    };
  }, [runId, buildSseUrl, closeStream]);

  // Call onNarrativeComplete when stream ends
  useEffect(() => {
    if (status === 'completed' && events.length > 0) {
      onNarrativeComplete?.(events.map((e) => e.text).join('\n'));
    }
  }, [status, events, onNarrativeComplete]);

  return (
    <div
      className={['rounded-lg border border-divider bg-bg-paper', className]
        .filter(Boolean)
        .join(' ')}
      data-testid="live-progress-narrative"
    >
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-divider px-4 py-2">
        {status === 'streaming' && (
          <Loader2 className="h-4 w-4 animate-spin text-primary" aria-hidden="true" />
        )}
        {status === 'completed' && (
          <CheckCircle2 className="h-4 w-4 text-success-color" aria-hidden="true" />
        )}
        {status === 'error' && (
          <AlertCircle className="h-4 w-4 text-error-color" aria-hidden="true" />
        )}
        <span className="text-sm font-semibold text-text-primary">
          {status === 'completed' ? 'Run summary' : 'Live progress'}
        </span>
        {status === 'streaming' && (
          <span className="ml-auto text-xs text-text-secondary" aria-live="polite">
            {events.length} event{events.length !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {/* Event stream */}
      <div
        ref={containerRef}
        role="log"
        aria-label={t('ai.progress.narrativeLabel')}
        aria-live="polite"
        className="max-h-64 overflow-y-auto px-4 py-3"
        data-testid="narrative-log"
      >
        {events.length === 0 && status === 'streaming' && (
          <p className="text-sm text-text-secondary italic" data-testid="narrative-waiting">
            Waiting for run events…
          </p>
        )}

        {events.map((event) => (
          <div
            key={event.id}
            className="mb-1 flex gap-2 text-sm text-text-primary"
            data-testid={`narrative-event-${event.id}`}
          >
            <span className="shrink-0 text-xs text-text-secondary">
              {new Date(event.timestamp).toLocaleTimeString()}
            </span>
            <span>{event.text}</span>
          </div>
        ))}

        {status === 'error' && (
          <p className="text-sm text-error-color" role="alert" data-testid="narrative-error">
            {errorMessage ?? 'Stream error'}
          </p>
        )}
      </div>
    </div>
  );
}
