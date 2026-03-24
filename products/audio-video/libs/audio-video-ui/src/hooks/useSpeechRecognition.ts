/**
 * @doc.type hook
 * @doc.purpose React hook wrapping the browser SpeechRecognition API for speech-to-text input
 * @doc.layer shared
 * @doc.pattern custom React hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';

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
  onError?: (event: SpeechRecognitionErrorEvent) => void;
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
}

/** Resolved SpeechRecognition constructor (handles vendor prefix). */
function getSpeechRecognitionCtor(): (new () => SpeechRecognition) | undefined {
  if (typeof window === 'undefined') return undefined;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition || undefined;
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
export function useSpeechRecognition(): UseSpeechRecognitionResult {
  const RecognitionCtor = getSpeechRecognitionCtor();
  const isSupported = RecognitionCtor !== undefined;

  const [isListening, setIsListening] = useState(false);
  const recognitionRef = useRef<SpeechRecognition | null>(null);

  const stop = useCallback(() => {
    recognitionRef.current?.stop();
    recognitionRef.current = null;
    setIsListening(false);
  }, []);

  const start = useCallback(
    (
      callbacks?: SpeechRecognitionCallbacks,
      options?: SpeechRecognitionOptions,
    ) => {
      if (!RecognitionCtor) {
        // Not supported — callers check isSupported before calling start(),
        // but guard defensively anyway.
        return;
      }

      // Stop any pre-existing session before starting a new one
      recognitionRef.current?.stop();

      const recognition = new RecognitionCtor();
      recognition.lang = options?.lang ?? (navigator.language || 'en-US');
      recognition.interimResults = options?.interimResults ?? true;
      recognition.continuous = options?.continuous ?? false;

      recognition.onresult = (event: SpeechRecognitionEvent) => {
        const latestResult = event.results[event.results.length - 1];
        const combinedTranscript = Array.from(event.results)
          .map((r) => r[0].transcript)
          .join('');
        callbacks?.onTranscript?.(combinedTranscript, latestResult.isFinal);
      };

      recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
        setIsListening(false);
        recognitionRef.current = null;
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
    [RecognitionCtor],
  );

  // Stop the mic on unmount to release hardware resources
  useEffect(() => {
    return () => {
      recognitionRef.current?.stop();
    };
  }, []);

  return { start, stop, isListening, isSupported };
}
