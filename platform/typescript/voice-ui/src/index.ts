/**
 * @ghatana/voice-ui — Voice UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Voice dictation input components and hooks
 * @doc.layer platform
 * @doc.pattern Library
 */

import React from 'react';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface VoiceInputProps {
  onTranscript: (text: string) => void;
  disabled?: boolean;
  className?: string;
  placeholder?: string;
}

export interface UseBrowserSpeechRecognitionReturn {
  listening: boolean;
  transcript: string;
  startListening: () => void;
  stopListening: () => void;
  supported: boolean;
}

// ── Components ─────────────────────────────────────────────────────────────────

export const VoiceInput: React.FC<VoiceInputProps> = () => null;

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function useBrowserSpeechRecognition(): UseBrowserSpeechRecognitionReturn {
  return {
    listening: false,
    transcript: '',
    startListening: () => undefined,
    stopListening: () => undefined,
    supported: typeof window !== 'undefined' && 'SpeechRecognition' in window,
  };
}
