/**
 * VoiceCommandBar — Floating voice interaction entrypoint (DC-E4, Sprint 4).
 *
 * Provides:
 *  1. Mic button that toggles between listening / idle states.
 *  2. Browser Web Speech API input (transcripts text into the input field).
 *  3. Manual text input fallback for accessibility / unsupported browsers.
 *  4. Low-confidence confirmation dialog before executing an intent.
 *  5. Result display with speech summary + resolved path.
 *
 * Architecture contract:
 *  - Calls POST /api/v1/voice/intent  (pre-transcribed text path)
 *  - Never stores raw audio — only the pre-transcribed utterance is sent
 *  - Inline confirm flow for confidence < 0.60 (mirrors VoiceGatewayHandler threshold)
 *  - Falls back to classification-only preview on low confidence so user can confirm
 *
 * @doc.type component
 * @doc.purpose Voice command entrypoint with confirmation UI (DC-E4)
 * @doc.layer frontend
 * @doc.pattern Container Component
 */

import React, {
  useState,
  useCallback,
  useRef,
  KeyboardEvent,
} from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Mic,
  MicOff,
  Send,
  X,
  CheckCircle,
  AlertTriangle,
  Loader2,
  Volume2,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { useSpeechSynthesis, useSpeechRecognition } from '@audio-video/ui';
import { cn } from '../../lib/theme';

// =============================================================================
// TYPES
// =============================================================================

interface VoiceIntentResponse {
  data?: {
    executed?: boolean;
    intentName?: string;
    httpMethod?: string;
    resolvedPath?: string;
    parameters?: Record<string, string>;
    sensitivity?: string;
    description?: string;
    speechSummary?: string;
    requiresConfirmation?: boolean;
    confidence?: number;
    message?: string;
  };
  ai?: {
    confidence?: number;
    fallback?: boolean;
    reasons?: string[];
  };
  error?: { code?: string; message?: string };
}

interface PendingConfirmation {
  utterance: string;
  intentName: string;
  resolvedPath: string;
  confidence: number;
  httpMethod: string;
  sensitivity: string;
}

type BarState = 'idle' | 'listening' | 'processing' | 'confirm' | 'result' | 'error';

// =============================================================================
// API HELPER
// =============================================================================

async function postVoiceIntent(
  utterance: string,
  parameters: Record<string, string> = {},
  confirmOverride = false,
): Promise<VoiceIntentResponse> {
  const tenantId = localStorage.getItem('tenantId') ?? 'default-tenant';
  const resp = await fetch('/api/v1/voice/intent', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': tenantId,
    },
    body: JSON.stringify({ utterance, parameters, confirm: confirmOverride }),
  });
  return resp.json() as Promise<VoiceIntentResponse>;
}

// =============================================================================
// CONFIDENCE BADGE
// =============================================================================

function ConfidenceBadge({ confidence }: { confidence: number }) {
  const pct = Math.round(confidence * 100);
  const color =
    pct >= 80 ? 'text-green-600 bg-green-50' : pct >= 60 ? 'text-amber-600 bg-amber-50' : 'text-red-600 bg-red-50';
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-medium px-1.5 py-0.5 rounded', color)}>
      {pct}% confidence
    </span>
  );
}

// =============================================================================
// SENSITIVITY BADGE
// =============================================================================

function SensitivityBadge({ sensitivity }: { sensitivity: string }) {
  const lc = sensitivity.toLowerCase();
  const color =
    lc === 'critical'
      ? 'bg-red-100 text-red-700'
      : lc === 'sensitive'
        ? 'bg-amber-100 text-amber-700'
        : 'bg-gray-100 text-gray-600';
  return (
    <span className={cn('inline-flex text-xs font-mono px-1.5 py-0.5 rounded', color)}>{sensitivity}</span>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export interface VoiceCommandBarProps {
  /** Whether the bar is initially expanded (default: false). */
  defaultOpen?: boolean;
  /** Additional CSS classes for the outer container. */
  className?: string;
}

/**
 * Floating voice command bar.
 *
 * Mount once at app level (e.g., in the main layout) — it manages its own
 * open/closed state and submits intents independently of page context.
 */
export function VoiceCommandBar({ defaultOpen = false, className }: VoiceCommandBarProps) {
  const [open, setOpen] = useState(defaultOpen);
  const [barState, setBarState] = useState<BarState>('idle');
  const [utterance, setUtterance] = useState('');
  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirmation | null>(null);
  const [lastResult, setLastResult] = useState<VoiceIntentResponse['data'] | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [transcript, setTranscript] = useState('');   // live transcript while listening
  const inputRef = useRef<HTMLInputElement>(null);

  // Shared audio-video hooks — encapsulate all browser speech APIs
  const { speak, cancel: cancelTts } = useSpeechSynthesis();
  const {
    start: startRecognition,
    stop: stopRecognition,
    isSupported: isSTTSupported,
  } = useSpeechRecognition();

  // ── Intent mutation ────────────────────────────────────────────────────────
  const { mutate: sendIntent, isPending } = useMutation({
    mutationFn: ({
      text,
      confirmOverride,
    }: {
      text: string;
      confirmOverride?: boolean;
    }) => postVoiceIntent(text, {}, confirmOverride ?? false),
    onSuccess: (data) => {
      if (data.error) {
        setErrorMessage(data.error.message ?? 'Voice intent error');
        setBarState('error');
        return;
      }
      const result = data.data;
      if (result?.requiresConfirmation) {
        setPendingConfirm({
          utterance: utterance,
          intentName: result.intentName ?? '',
          resolvedPath: result.resolvedPath ?? '',
          confidence: result.confidence ?? data.ai?.confidence ?? 0,
          httpMethod: result.httpMethod ?? 'GET',
          sensitivity: result.sensitivity ?? 'INTERNAL',
        });
        setBarState('confirm');
      } else {
        setLastResult(result ?? null);
        setBarState('result');
        // TTS: speak only the server-provided summary — never raw user input.
        if (result?.speechSummary) {
          speak(result.speechSummary);
        }
      }
    },
    onError: (err: Error) => {
      setErrorMessage(err.message);
      setBarState('error');
    },
  });

  // ── Web Speech API — delegated to @audio-video/ui shared hooks ───────────
  const startListening = useCallback(() => {
    if (!isSTTSupported) {
      // Fallback: focus text input for manual entry on unsupported browsers
      inputRef.current?.focus();
      setBarState('listening'); // show mic icon as "active" for UX consistency
      return;
    }
    startRecognition(
      {
        onTranscript: (text, isFinal) => {
          setTranscript(text);
          if (isFinal) {
            setUtterance(text.trim());
            setTranscript('');
          }
        },
        onEnd: () => {
          setBarState('idle');
          setTranscript('');
        },
        onError: () => {
          setBarState('idle');
          setTranscript('');
        },
      },
      { continuous: false, interimResults: true },
    );
    setBarState('listening');
  }, [isSTTSupported, startRecognition]);

  const stopListening = useCallback(() => {
    stopRecognition();
    setBarState('idle');
    setTranscript('');
  }, [stopRecognition]);

  const toggleListening = useCallback(() => {
    if (barState === 'listening') stopListening();
    else startListening();
  }, [barState, startListening, stopListening]);

  // ── Submit utterance ───────────────────────────────────────────────────────
  const submit = useCallback(() => {
    const text = utterance.trim();
    if (!text) return;
    setBarState('processing');
    setPendingConfirm(null);
    setLastResult(null);
    setErrorMessage('');
    sendIntent({ text });
  }, [utterance, sendIntent]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') submit();
      if (e.key === 'Escape') reset();
    },
    [submit],
  );

  // ── Confirm execution ──────────────────────────────────────────────────────
  const confirmExecution = useCallback(() => {
    if (!pendingConfirm) return;
    setBarState('processing');
    sendIntent({ text: pendingConfirm.utterance, confirmOverride: true });
  }, [pendingConfirm, sendIntent]);

  // ── Reset ──────────────────────────────────────────────────────────────────
  const reset = useCallback(() => {
    stopListening();
    cancelTts(); // cancel any ongoing TTS playback when the user dismisses
    setBarState('idle');
    setUtterance('');
    setTranscript('');
    setPendingConfirm(null);
    setLastResult(null);
    setErrorMessage('');
  }, [stopListening, cancelTts]);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        title="Open voice commands"
        className={cn(
          'fixed bottom-6 right-6 z-50',
          'flex items-center gap-2 px-3 py-2 rounded-full shadow-lg',
          'bg-primary-600 text-white hover:bg-primary-700 transition-colors text-sm',
          className,
        )}
      >
        <Mic className="h-4 w-4" />
        <span className="hidden sm:inline">Voice</span>
      </button>
    );
  }

  return (
    <div
      className={cn(
        'fixed bottom-6 right-6 z-50 w-full max-w-md',
        'bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700',
        'rounded-2xl shadow-2xl overflow-hidden',
        className,
      )}
      role="dialog"
      aria-label="Voice command bar"
    >
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-800">
        <div className="flex items-center gap-2">
          <Volume2 className="h-4 w-4 text-primary-600" />
          <span className="text-sm font-medium text-gray-900 dark:text-white">Voice Commands</span>
          {barState === 'listening' && (
            <span className="flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-2 w-2 rounded-full bg-red-400 opacity-75" />
              <span className="relative inline-flex rounded-full h-2 w-2 bg-red-500" />
            </span>
          )}
        </div>
        <button
          onClick={() => { reset(); setOpen(false); }}
          className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
          aria-label="Close voice bar"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* ── Input row ── */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-gray-100 dark:border-gray-800">
        <input
          ref={inputRef}
          type="text"
          value={transcript || utterance}
          onChange={(e) => { setUtterance(e.target.value); setTranscript(''); }}
          onKeyDown={handleKeyDown}
          placeholder={barState === 'listening' ? 'Listening…' : 'Say or type a command…'}
          disabled={barState === 'processing'}
          className={cn(
            'flex-1 text-sm px-3 py-2 rounded-lg border transition-colors',
            'bg-white dark:bg-gray-800 text-gray-900 dark:text-white',
            'placeholder:text-gray-400',
            'focus:outline-none focus:ring-2 focus:ring-primary-500',
            barState === 'listening'
              ? 'border-red-400 dark:border-red-500'
              : 'border-gray-200 dark:border-gray-700',
          )}
          aria-label="Voice command input"
        />
        {/* Mic toggle */}
        <button
          onClick={toggleListening}
          disabled={barState === 'processing'}
          title={barState === 'listening' ? 'Stop listening' : 'Start listening'}
          className={cn(
            'flex items-center justify-center w-9 h-9 rounded-full transition-colors',
            barState === 'listening'
              ? 'bg-red-100 text-red-600 hover:bg-red-200'
              : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-gray-200',
          )}
          aria-pressed={barState === 'listening'}
        >
          {barState === 'listening' ? <MicOff className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
        </button>
        {/* Send */}
        <button
          onClick={submit}
          disabled={!utterance.trim() || barState === 'processing' || barState === 'listening'}
          title="Execute command"
          className={cn(
            'flex items-center justify-center w-9 h-9 rounded-full transition-colors',
            'bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-40 disabled:cursor-not-allowed',
          )}
          aria-label="Execute voice command"
        >
          {barState === 'processing' ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </button>
      </div>

      {/* ── Live transcript ── */}
      {transcript && (
        <div className="px-4 py-2 text-xs text-gray-400 italic bg-gray-50 dark:bg-gray-800">
          {transcript}
        </div>
      )}

      {/* ── Confirmation dialog ── */}
      {barState === 'confirm' && pendingConfirm && (
        <div className="px-4 py-4 space-y-3 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-start gap-2">
            <AlertTriangle className="h-4 w-4 text-amber-500 mt-0.5 shrink-0" />
            <p className="text-sm font-medium text-gray-900 dark:text-white">
              Confirm command execution
            </p>
          </div>
          <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-3 space-y-2 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-gray-500">Intent</span>
              <span className="font-mono text-gray-900 dark:text-white">{pendingConfirm.intentName}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-500">Route</span>
              <span className="font-mono text-xs text-gray-700 dark:text-gray-300 truncate max-w-[55%]">
                {pendingConfirm.httpMethod} {pendingConfirm.resolvedPath}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-500">Sensitivity</span>
              <SensitivityBadge sensitivity={pendingConfirm.sensitivity} />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-gray-500">Confidence</span>
              <ConfidenceBadge confidence={pendingConfirm.confidence} />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={confirmExecution}
              disabled={isPending}
              className={cn(
                'flex-1 flex items-center justify-center gap-1.5 px-3 py-2',
                'bg-primary-600 text-white text-sm rounded-lg hover:bg-primary-700 transition-colors',
                'disabled:opacity-50',
              )}
            >
              {isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle className="h-3.5 w-3.5" />}
              Yes, execute
            </button>
            <button
              onClick={reset}
              className="flex-1 px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* ── Result ── */}
      {barState === 'result' && lastResult && (
        <div className="px-4 py-4 space-y-2 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-2">
            <CheckCircle className="h-4 w-4 text-green-500 shrink-0" />
            <p className="text-sm font-medium text-green-700 dark:text-green-400">
              {lastResult.speechSummary ?? 'Command executed'}
            </p>
          </div>
          {lastResult.resolvedPath && (
            <p className="text-xs text-gray-400 font-mono truncate">
              {lastResult.httpMethod} {lastResult.resolvedPath}
            </p>
          )}
          <button
            onClick={reset}
            className="text-xs text-primary-600 dark:text-primary-400 hover:underline"
          >
            Run another command
          </button>
        </div>
      )}

      {/* ── Error ── */}
      {barState === 'error' && (
        <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-start gap-2">
            <AlertTriangle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
            <p className="text-sm text-red-600 dark:text-red-400">{errorMessage || 'Unknown error'}</p>
          </div>
          <button
            onClick={reset}
            className="mt-2 text-xs text-primary-600 dark:text-primary-400 hover:underline"
          >
            Try again
          </button>
        </div>
      )}

      {/* ── Hint: no SpeechRecognition ── */}
      {!SpeechRecognitionCtor && barState === 'idle' && (
        <div className="px-4 pb-3">
          <p className="text-xs text-gray-400">
            Speech recognition not supported in this browser — type your command above.
          </p>
        </div>
      )}

      {/* ── Quick intent shortcuts ── */}
      <div className="px-4 py-3">
        <p className="text-xs text-gray-400 mb-2">Quick commands</p>
        <div className="flex flex-wrap gap-1.5">
          {['List pipelines', 'Run analytics query', 'List agents', 'Check brain status'].map((cmd) => (
            <button
              key={cmd}
              onClick={() => { setUtterance(cmd); inputRef.current?.focus(); }}
              className="text-xs px-2 py-1 bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 rounded-full hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
            >
              {cmd}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default VoiceCommandBar;
