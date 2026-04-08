/**
 * VoiceInput — Voice-enabled text input component.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/voice-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type component
 * @doc.purpose Provide voice dictation for any text input
 * @doc.layer frontend
 * @doc.pattern Accessibility Component
 */

import React, { useCallback, useRef, useEffect } from 'react';
import { Mic, MicOff } from 'lucide-react';
import { ConsentManager, useConsent } from '../privacy/ConsentManager';

/**
 * Type declarations for SpeechRecognition API
 */
declare global {
  interface Window {
    SpeechRecognition: any;
    webkitSpeechRecognition: any;
  }
}

/**
 * SpeechRecognition interface
 */
interface SpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start(): void;
  stop(): void;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onstart: (() => void) | null;
  onend: (() => void) | null;
}

/**
 * SpeechRecognitionEvent interface
 */
interface SpeechRecognitionEvent extends Event {
  resultIndex: number;
  results: SpeechRecognitionResultList;
}

/**
 * SpeechRecognitionResultList interface
 */
interface SpeechRecognitionResultList {
  length: number;
  item(index: number): SpeechRecognitionResult;
  [index: number]: SpeechRecognitionResult;
}

/**
 * SpeechRecognitionResult interface
 */
interface SpeechRecognitionResult {
  isFinal: boolean;
  [index: number]: SpeechRecognitionAlternative;
  length: number;
  item(index: number): SpeechRecognitionAlternative;
}

/**
 * SpeechRecognitionAlternative interface
 */
interface SpeechRecognitionAlternative {
  transcript: string;
  confidence: number;
}

/**
 * SpeechRecognitionErrorEvent interface
 */
interface SpeechRecognitionErrorEvent extends Event {
  error: string;
  message: string;
}

/**
 * VoiceInput component props
 */
interface VoiceInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  /**
   * Optional: Custom input element
   */
  inputComponent?: React.ComponentType<any>;
  /**
   * Optional: Props to pass to custom input component
   */
  inputProps?: Record<string, unknown>;
  /**
   * Optional: Callback when speech recognition starts
   */
  onListeningStart?: () => void;
  /**
   * Optional: Callback when speech recognition stops
   */
  onListeningStop?: (transcript?: string) => void;
  /**
   * Optional: Callback when speech recognition error occurs
   */
  onError?: (error: Error) => void;
}

/**
 * Check if speech recognition is supported in the browser
 */
function isSpeechRecognitionSupported(): boolean {
  return 'SpeechRecognition' in window || 'webkitSpeechRecognition' in window;
}

/**
 * Speech recognition hook
 * 
 * Uses browser SpeechRecognition API with fallback handling
 */
function useBrowserSpeechRecognition() {
  const [isListening, setIsListening] = React.useState(false);
  const [isSupported, setIsSupported] = React.useState(false);
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const interimTranscriptRef = useRef('');

  useEffect(() => {
    setIsSupported(isSpeechRecognitionSupported());
  }, []);

  const start = useCallback((options: {
    onTranscript: (transcript: string, isFinal: boolean) => void;
    onError?: (error: Error) => void;
  }) => {
    if (!isSupported) return;

    const SpeechRecognitionClass = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    
    if (!recognitionRef.current) {
      const newRecognition = new SpeechRecognitionClass();
      newRecognition.continuous = false;
      newRecognition.interimResults = true;
      newRecognition.lang = 'en-US';
      recognitionRef.current = newRecognition;
    }

    const recognition = recognitionRef.current;
    if (!recognition) return;
    interimTranscriptRef.current = '';

    recognition.onstart = () => {
      setIsListening(true);
    };

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interimTranscript = '';
      let finalTranscript = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        
        if (event.results[i].isFinal) {
          finalTranscript += transcript;
        } else {
          interimTranscript += transcript;
        }
      }

      interimTranscriptRef.current = interimTranscript;
      
      if (finalTranscript) {
        options.onTranscript(finalTranscript, true);
      } else if (interimTranscript) {
        options.onTranscript(interimTranscript, false);
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
      setIsListening(false);
      const error = new Error(event.error);
      if (options.onError) {
        options.onError(error);
      }
    };

    recognition.onend = () => {
      setIsListening(false);
      // Send any remaining interim transcript as final
      if (interimTranscriptRef.current) {
        options.onTranscript(interimTranscriptRef.current, true);
      }
    };

    recognition.start();
  }, [isSupported]);

  const stop = useCallback(() => {
    if (recognitionRef.current) {
      recognitionRef.current.stop();
    }
  }, []);

  return { start, stop, isListening, isSupported };
}

/**
 * VoiceInput component
 *
 * Wraps a text input with voice dictation capability. Uses browser
 * SpeechRecognition API with consent check. Provides visual feedback
 * when listening.
 */
export const VoiceInput: React.FC<VoiceInputProps> = ({
  value,
  onChange,
  placeholder = '',
  className = '',
  disabled = false,
  inputComponent,
  inputProps = {},
  onListeningStart,
  onListeningStop,
  onError,
}) => {
  const { consentGranted } = useConsent('voice_processing');
  const { start, stop, isListening, isSupported } = useBrowserSpeechRecognition();
  const [interimText, setInterimText] = React.useState('');

  const toggleListening = useCallback(() => {
    if (isListening) {
      stop();
      setInterimText('');
      if (onListeningStop) {
        onListeningStop(value);
      }
    } else {
      start({
        onTranscript: (transcript, isFinal) => {
          if (isFinal) {
            onChange(transcript);
            setInterimText('');
          } else {
            setInterimText(transcript);
          }
        },
        onError: (error) => {
          if (onError) {
            onError(error);
          }
        },
      });
      if (onListeningStart) {
        onListeningStart();
      }
    }
  }, [isListening, start, stop, onChange, value, onListeningStart, onListeningStop, onError]);

  const displayValue = isListening && interimText ? interimText : value;

  // If consent not granted, show regular input without voice
  if (!consentGranted) {
    const InputComponent = inputComponent || 'input';
    return (
      <InputComponent
        type="text"
        value={value}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        className={className}
        {...inputProps}
      />
    );
  }

  const InputComponent = inputComponent || 'input';

  return (
    <div className="relative">
      <InputComponent
        type="text"
        value={displayValue}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        className={`${className} pr-10`}
        aria-label={isListening ? 'Voice input active' : 'Text input with voice option'}
        aria-live="polite"
        {...inputProps}
      />
      {isSupported && (
        <button
          type="button"
          onClick={toggleListening}
          disabled={disabled}
          className={`
            absolute right-2 top-1/2 -translate-y-1/2
            p-1.5 rounded-full transition-all duration-200
            focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2
            ${isListening 
              ? 'bg-red-100 text-red-600 hover:bg-red-200 dark:bg-red-900 dark:text-red-300 dark:hover:bg-red-800' 
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
            }
            ${disabled ? 'opacity-40 cursor-not-allowed' : ''}
          `}
          aria-label={isListening ? 'Stop voice input' : 'Start voice input'}
          aria-pressed={isListening}
          title={isListening ? 'Stop voice input' : 'Start voice input'}
        >
          {isListening ? (
            <MicOff className="h-4 w-4" />
          ) : (
            <Mic className="h-4 w-4" />
          )}
        </button>
      )}
      {isListening && (
        <div className="absolute -bottom-6 left-0 text-xs text-indigo-600 dark:text-indigo-400">
          Listening...
        </div>
      )}
    </div>
  );
};

/**
 * Voice-enabled textarea variant
 */
export const VoiceTextarea: React.FC<VoiceInputProps & {
  rows?: number;
}> = ({
  rows = 3,
  ...props
}) => {
  const TextareaComponent = 'textarea' as unknown as React.ComponentType<any>;
  return (
    <VoiceInput
      {...props}
      inputComponent={TextareaComponent}
      inputProps={{ rows }}
      className={`${props.className || ''} resize-none`}
    />
  );
};
