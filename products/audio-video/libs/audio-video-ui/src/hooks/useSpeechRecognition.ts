/**
 * @doc.type hook
 * @doc.purpose React hook wrapping the browser SpeechRecognition API for speech-to-text input
 * @doc.layer shared
 * @doc.pattern custom React hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  AudioVideoPlatformError,
  AudioVideoProvider,
  AudioVideoRuntimeConfig,
  AudioVideoRuntimeMetricsSnapshot,
  AudioVideoSpeechHookConfig,
  createPlatformError,
  FallbackRecordingSession,
  getAudioVideoPlatformMetrics,
  incrementAudioVideoPlatformMetric,
  isPlatformSpeechRecognitionAvailable,
  normalizeAudioVideoRuntimeConfig,
  startFallbackRecording,
} from './audioVideoPlatform';

interface BrowserSpeechRecognitionAlternative {
  transcript: string;
  confidence: number;
}

interface BrowserSpeechRecognitionResult {
  isFinal: boolean;
  length: number;
  [index: number]: BrowserSpeechRecognitionAlternative;
}

interface BrowserSpeechRecognitionEvent {
  results: ArrayLike<BrowserSpeechRecognitionResult>;
}

interface BrowserSpeechRecognitionErrorEvent {
  error: string;
  message?: string;
}

interface BrowserSpeechRecognition {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((event: BrowserSpeechRecognitionEvent) => void) | null;
  onerror: ((event: BrowserSpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
}

/**
 * Options supplied when starting a recognition session.
 */
export interface SpeechRecognitionOptions {
  /**
   * BCP-47 language tag for recognition (defaults to {@code navigator.language}
   * or {@code 'en-US'}).
   */
  lang?: string;
  /** Whether to return interim (non-final) results (default: true). */
  interimResults?: boolean;
  /** Whether the recognition session listens continuously (default: false). */
  continuous?: boolean;
}

/**
 * Callbacks supplied to {@link useSpeechRecognition.start}.
 */
export interface SpeechRecognitionCallbacks {
  /**
   * Called whenever a transcript update is available.
   * @param transcript The current (possibly interim) transcript text.
   * @param isFinal    True when this is a committed, final result.
   */
  onTranscript?: (transcript: string, isFinal: boolean) => void;
  /** Called when recognition ends (either by the browser or via {@code stop()}). */
  onEnd?: () => void;
  /** Called on recognition error. The session has already ended. */
  onError?: (event: BrowserSpeechRecognitionErrorEvent) => void;
  /** Called when the platform fallback fails or cannot be used. */
  onPlatformError?: (error: AudioVideoPlatformError) => void;
  /** Called whenever processing moves between browser and platform providers. */
  onProviderChange?: (provider: AudioVideoProvider) => void;
}

/**
 * Return value of {@link useSpeechRecognition}.
 */
export interface UseSpeechRecognitionResult {
  /** Start a new recognition session. */
  start: (callbacks?: SpeechRecognitionCallbacks, options?: SpeechRecognitionOptions) => void;
  /** Stop the active recognition session. */
  stop: () => void;
  /** True while a recognition session is active. */
  isListening: boolean;
  /**
   * True when the browser supports SpeechRecognition (both standard and
   * webkit-prefixed variants are checked).
   */
  isSupported: boolean;
  /** True when the platform STT fallback is configured and the browser can record audio. */
  supportsPlatformFallback: boolean;
  /** Active provider for the current or most recent recognition session. */
  activeProvider: AudioVideoProvider;
  /** Shared runtime metrics for browser and platform speech operations. */
  metrics: AudioVideoRuntimeMetricsSnapshot;
}

/** Resolved SpeechRecognition constructor (handles vendor prefix). */
function getSpeechRecognitionCtor(): (new () => BrowserSpeechRecognition) | undefined {
  if (typeof window === 'undefined') return undefined;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return ((window as any).SpeechRecognition ||
    (window as any).webkitSpeechRecognition ||
    undefined) as (new () => BrowserSpeechRecognition) | undefined;
}

/**
 * React hook that exposes the browser SpeechRecognition API as a stable,
 * type-safe interface.
 *
 * <p>Abstracts the webkit-prefixed and standard variants so callers do not need
 * to handle cross-browser detection themselves.
 *
 * <p>The hook cleans up the active session on unmount, stopping the microphone
 * and preventing memory leaks.
 *
 * <p>Privacy note: raw audio is processed entirely within the browser or the
 * platform's speech service — this hook never sends audio data to arbitrary
 * endpoints.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { start, stop, isListening, isSupported } = useSpeechRecognition();
 *
 *   const handleToggle = () => {
 *     if (isListening) {
 *       stop();
 *     } else {
 *       start({ onTranscript: (text, final) => console.log(text, final) });
 *     }
 *   };
 *
 *   return <button onClick={handleToggle}>{isListening ? 'Stop' : 'Start'}</button>;
 * }
 * ```
 */
export function useSpeechRecognition(
  hookConfig?: AudioVideoSpeechHookConfig,
): UseSpeechRecognitionResult {
  const RecognitionCtor = getSpeechRecognitionCtor();
  const isSupported = RecognitionCtor !== undefined;
  const runtimeConfigRef = useRef<AudioVideoRuntimeConfig>(
    normalizeAudioVideoRuntimeConfig(hookConfig?.runtimeConfig),
  );
  const hookConfigRef = useRef(hookConfig);
  hookConfigRef.current = hookConfig;

  const [isListening, setIsListening] = useState(false);
  const [activeProvider, setActiveProvider] = useState<AudioVideoProvider>('none');
  const [metrics, setMetrics] = useState<AudioVideoRuntimeMetricsSnapshot>(
    getAudioVideoPlatformMetrics(),
  );
  const recognitionRef = useRef<BrowserSpeechRecognition | null>(null);
  const recordingSessionRef = useRef<FallbackRecordingSession | null>(null);
  const fallbackCallbacksRef = useRef<SpeechRecognitionCallbacks | undefined>(undefined);
  const fallbackOptionsRef = useRef<SpeechRecognitionOptions | undefined>(undefined);

  const refreshMetrics = useCallback(() => {
    setMetrics(getAudioVideoPlatformMetrics());
  }, []);

  const stopFallbackSession = useCallback(() => {
    recordingSessionRef.current?.discard();
    recordingSessionRef.current = null;
  }, []);

  const stop = useCallback(() => {
    recognitionRef.current?.stop();
    recognitionRef.current = null;
    stopFallbackSession();
    setIsListening(false);
  }, [stopFallbackSession]);

  const runPlatformFallback = useCallback(async () => {
    const callbacks = fallbackCallbacksRef.current;
    const options = fallbackOptionsRef.current;
    const fallbackConfig = hookConfigRef.current?.fallback;

    if (!fallbackConfig?.sttEndpoint || !isPlatformSpeechRecognitionAvailable()) {
      callbacks?.onPlatformError?.(
        createPlatformError(
          'media.platform_unavailable',
          'runtime',
          false,
          'Platform speech recognition fallback is not available.',
        ),
      );
      setIsListening(false);
      refreshMetrics();
      return;
    }

    incrementAudioVideoPlatformMetric('sttFallbackRequests');
    refreshMetrics();
    setActiveProvider('platform');
    callbacks?.onProviderChange?.('platform');

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const session = startFallbackRecording(
        stream,
        runtimeConfigRef.current.recognitionMimeType,
        fallbackConfig,
        options?.lang ?? runtimeConfigRef.current.languageTag,
      );
      recordingSessionRef.current = session;
      setIsListening(true);

      try {
        const { transcript } = await session.stopAndTranscribe();
        callbacks?.onTranscript?.(transcript, true);
        callbacks?.onEnd?.();
      } catch (err) {
        incrementAudioVideoPlatformMetric('fallbackFailures');
        callbacks?.onPlatformError?.(
          err instanceof Error
            ? createPlatformError('media.processing_failed', 'runtime', true, err.message)
            : createPlatformError(
                'media.processing_failed',
                'runtime',
                true,
                'Platform speech recognition failed.',
              ),
        );
      }
    } catch (err) {
      incrementAudioVideoPlatformMetric('fallbackFailures');
      callbacks?.onPlatformError?.(
        createPlatformError(
          'media.input_unavailable',
          'runtime',
          true,
          err instanceof Error ? err.message : 'Unable to access the microphone.',
        ),
      );
    } finally {
      recordingSessionRef.current = null;
      setIsListening(false);
      refreshMetrics();
    }
  }, [refreshMetrics]);

  const start = useCallback(
    (
      callbacks?: SpeechRecognitionCallbacks,
      options?: SpeechRecognitionOptions,
    ) => {
      fallbackCallbacksRef.current = callbacks;
      fallbackOptionsRef.current = options;

      if (hookConfigRef.current?.fallback?.preferPlatform) {
        void runPlatformFallback();
        return;
      }

      if (!RecognitionCtor) {
        void runPlatformFallback();
        return;
      }

      // Stop any pre-existing session before starting a new one
      recognitionRef.current?.stop();
      incrementAudioVideoPlatformMetric('browserRecognitionSessions');
      refreshMetrics();
      setActiveProvider('browser');
      callbacks?.onProviderChange?.('browser');

      const recognition = new RecognitionCtor();
      recognition.lang = options?.lang ?? (navigator.language || 'en-US');
      recognition.interimResults = options?.interimResults ?? true;
      recognition.continuous = options?.continuous ?? false;

      recognition.onresult = (event: BrowserSpeechRecognitionEvent) => {
        const latestResult = event.results[event.results.length - 1];
        const combinedTranscript = Array.from(event.results)
          .map((r) => r[0]?.transcript ?? '')
          .join('');
        callbacks?.onTranscript?.(combinedTranscript, latestResult.isFinal);
      };

      recognition.onerror = (event: BrowserSpeechRecognitionErrorEvent) => {
        setIsListening(false);
        recognitionRef.current = null;
        const fallbackConfig = hookConfigRef.current?.fallback;
        if (fallbackConfig?.sttEndpoint) {
          void runPlatformFallback();
          return;
        }
        callbacks?.onError?.(event);
      };

      recognition.onend = () => {
        setIsListening(false);
        recognitionRef.current = null;
        callbacks?.onEnd?.();
      };

      recognitionRef.current = recognition;
      recognition.start();
      setIsListening(true);
    },
    [RecognitionCtor, refreshMetrics, runPlatformFallback],
  );

  // Stop the mic on unmount to release hardware resources
  useEffect(() => {
    return () => {
      recognitionRef.current?.stop();
      stopFallbackSession();
    };
  }, [stopFallbackSession]);

  return {
    start,
    stop,
    isListening,
    isSupported,
    supportsPlatformFallback: Boolean(hookConfig?.fallback?.sttEndpoint) && isPlatformSpeechRecognitionAvailable(),
    activeProvider,
    metrics,
  };
}
