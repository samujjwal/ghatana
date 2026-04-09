/**
 * VoiceInput - Generic voice input component
 *
 * @doc.type component
 * @doc.purpose Provide voice dictation for any text input
 * @doc.layer platform
 * @doc.pattern Accessibility Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Mic, MicOff } from 'lucide-react';
import { useConsent } from '../privacy';

/**
 * Minimal browser SpeechRecognition interfaces – not yet in standard TypeScript lib.
 */
interface SpeechRecognitionAlternative {
  readonly transcript: string;
  readonly confidence: number;
}

interface SpeechRecognitionResult {
  readonly isFinal: boolean;
  readonly length: number;
  item(index: number): SpeechRecognitionAlternative;
  [index: number]: SpeechRecognitionAlternative;
}

interface SpeechRecognitionResultList {
  readonly length: number;
  item(index: number): SpeechRecognitionResult;
  [index: number]: SpeechRecognitionResult;
}

interface SpeechRecognitionEvent extends Event {
  readonly resultIndex: number;
  readonly results: SpeechRecognitionResultList;
}

interface SpeechRecognitionErrorEvent extends Event {
  readonly error: string;
  readonly message: string;
}

interface SpeechRecognitionInstance {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
}

declare global {
  interface Window {
    SpeechRecognition: new () => SpeechRecognitionInstance;
    webkitSpeechRecognition: new () => SpeechRecognitionInstance;
  }
}

/**
 * Voice input props
 */
export interface VoiceInputProps {
  onTranscript: (text: string) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  buttonClassName?: string;
}

/**
 * Browser speech recognition hook
 */
export function useBrowserSpeechRecognition(): {
  isListening: boolean;
  transcript: string;
  error: string | null;
  startListening: () => void;
  stopListening: () => void;
  isSupported: boolean;
} {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);

  // Compute isSupported eagerly — using lazy useState so we don't need a
  // useEffect just for a capability check, and so re-renders see the value.
  const [isSupported] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return !!(window.SpeechRecognition ?? window.webkitSpeechRecognition);
  });

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const SpeechRecognitionAPI = window.SpeechRecognition ?? window.webkitSpeechRecognition;

      if (SpeechRecognitionAPI) {
        const recognition = new SpeechRecognitionAPI();
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.lang = 'en-US';

        recognition.onresult = (event: SpeechRecognitionEvent) => {
          const current = event.resultIndex;
          const result = event.results[current];
          if (result) {
            const resultTranscript = result[0]?.transcript ?? '';
            if (result.isFinal) {
              setTranscript(resultTranscript);
            }
          }
        };

        recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
          setError(event.error);
          setIsListening(false);
        };

        recognition.onend = () => {
          setIsListening(false);
        };

        recognitionRef.current = recognition;
      }
    }

    return () => {
      recognitionRef.current?.stop();
    };
  }, []);

  const startListening = useCallback(() => {
    if (recognitionRef.current && !isListening) {
      setTranscript('');
      setError(null);
      recognitionRef.current.start();
      setIsListening(true);
    }
  }, [isListening]);

  const stopListening = useCallback(() => {
    if (recognitionRef.current && isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    }
  }, [isListening]);

  return {
    isListening,
    transcript,
    error,
    startListening,
    stopListening,
    isSupported,
  };
}

/**
 * Voice Input Component
 */
export function VoiceInput({
  onTranscript,
  placeholder = 'Click to speak',
  disabled = false,
  className = '',
  buttonClassName = '',
}: VoiceInputProps): React.ReactElement {
  const { consentGranted } = useConsent('voice_processing');
  const { isListening, transcript, error, startListening, stopListening, isSupported } =
    useBrowserSpeechRecognition();

  useEffect(() => {
    if (transcript && consentGranted) {
      onTranscript(transcript);
    }
  }, [transcript, onTranscript, consentGranted]);

  if (!isSupported) {
    return (
      <div className={`text-gray-500 text-sm ${className}`}>
        Voice input not supported in this browser
      </div>
    );
  }

  if (!consentGranted) {
    return (
      <div className={`text-gray-500 text-sm ${className}`}>
        Voice input requires consent
      </div>
    );
  }

  const toggleListening = (): void => {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  };

  return (
    <div className={`flex items-center space-x-2 ${className}`}>
      <button
        onClick={toggleListening}
        disabled={disabled}
        className={`
          p-2 rounded-full transition-colors
          ${isListening
            ? 'bg-red-100 text-red-600 hover:bg-red-200'
            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}
          ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
          ${buttonClassName}
        `}
        title={isListening ? 'Stop recording' : 'Start voice input'}
      >
        {isListening ? <MicOff size={20} /> : <Mic size={20} />}
      </button>

      <span className="text-sm text-gray-600">
        {isListening ? 'Listening...' : placeholder}
      </span>

      {error != null && (
        <span className="text-sm text-red-600">
          Error: {error}
        </span>
      )}
    </div>
  );
}
