/**
 * @doc.type hook
 * @doc.purpose React hook wrapping the browser SpeechSynthesis API for text-to-speech output
 * @doc.layer shared
 * @doc.pattern custom React hook
 */

import { useCallback, useEffect, useRef } from 'react';

/**
 * Options for speech synthesis utterances.
 */
export interface SpeechSynthesisOptions {
  /** Speech rate multiplier (default: 1.05). Values 0.1–10. */
  rate?: number;
  /** Pitch multiplier (default: 1.0). Values 0–2. */
  pitch?: number;
  /** Volume (default: 0.85). Values 0–1. */
  volume?: number;
  /**
   * BCP-47 language tag for the utterance (default: navigator.language or 'en-US').
   * Examples: 'en-US', 'fr-FR', 'de-DE'.
   */
  lang?: string;
}

/**
 * Return value of {@link useSpeechSynthesis}.
 */
export interface UseSpeechSynthesisResult {
  /**
   * Speak the provided text.
   * Cancels any in-progress utterance before queuing the new one.
   * Privacy: callers MUST NOT pass raw user input or PII — only server-provided
   * speech summaries should be spoken to avoid unintended data exposure.
   */
  speak: (text: string, options?: SpeechSynthesisOptions) => void;

  /** Cancel any ongoing or queued speech. */
  cancel: () => void;

  /**
   * True when the browser supports the SpeechSynthesis API.
   * Components may use this flag to conditionally render TTS controls.
   */
  isSupported: boolean;
}

const DEFAULT_OPTIONS: Required<SpeechSynthesisOptions> = {
  rate: 1.05,
  pitch: 1.0,
  volume: 0.85,
  lang: typeof navigator !== 'undefined' ? (navigator.language || 'en-US') : 'en-US',
};

/**
 * React hook that exposes browser SpeechSynthesis as a stable, type-safe API.
 *
 * <p>The hook automatically cancels any queued utterances on component unmount
 * to prevent speech from continuing after the component is removed from the DOM.
 *
 * <p>When the API is unavailable (Firefox with disabled flag, iOS WKWebView,
 * server-side rendering), {@link UseSpeechSynthesisResult.isSupported} is
 * {@code false} and calls to {@link UseSpeechSynthesisResult.speak} are no-ops.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { speak, isSupported } = useSpeechSynthesis();
 *   return <button onClick={() => speak('Hello world!')}>Speak</button>;
 * }
 * ```
 */
export function useSpeechSynthesis(): UseSpeechSynthesisResult {
  const isSupported =
    typeof window !== 'undefined' && 'speechSynthesis' in window;

  // Keep a ref so cancel() in cleanup closure always has the same reference
  const isSupportedRef = useRef(isSupported);

  const cancel = useCallback(() => {
    if (isSupportedRef.current) {
      window.speechSynthesis.cancel();
    }
  }, []);

  const speak = useCallback(
    (text: string, options?: SpeechSynthesisOptions) => {
      if (!text || !isSupportedRef.current) return;

      const merged = { ...DEFAULT_OPTIONS, ...options };
      // Cancel any ongoing speech to avoid queue buildup
      window.speechSynthesis.cancel();

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.rate = merged.rate;
      utterance.pitch = merged.pitch;
      utterance.volume = merged.volume;
      utterance.lang = merged.lang;
      // Use system default voice (browser chooses locale-aware default)

      window.speechSynthesis.speak(utterance);
    },
    [],
  );

  // Cancel any pending speech on unmount
  useEffect(() => {
    return () => {
      if (isSupportedRef.current) {
        window.speechSynthesis.cancel();
      }
    };
  }, []);

  return { speak, cancel, isSupported };
}
