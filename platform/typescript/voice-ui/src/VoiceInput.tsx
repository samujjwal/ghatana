/**
 * VoiceInput - Generic voice input component
 * 
 * @doc.type component
 * @doc.purpose Provide voice dictation for any text input
 * @doc.layer frontend
 * @doc.pattern Accessibility Component
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Mic, MicOff } from 'lucide-react';
import { useConsent } from '@ghatana/privacy-ui';

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
 * Type declarations for SpeechRecognition API
 */
declare global {
  interface Window {
    SpeechRecognition: any;
    webkitSpeechRecognition: any;
  }
}

/**
 * Browser speech recognition hook
 */
export function useBrowserSpeechRecognition() {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [error, setError] = useState<string | null>(null);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      
      if (SpeechRecognition) {
        const recognition = new SpeechRecognition();
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.lang = 'en-US';

        recognition.onresult = (event: any) => {
          const current = event.resultIndex;
          const transcript = event.results[current][0].transcript;
          
          if (event.results[current].isFinal) {
            setTranscript(transcript);
          }
        };

        recognition.onerror = (event: any) => {
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
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
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
    isSupported: !!recognitionRef.current,
  };
}

/**
 * Voice Input Component
 */
export function VoiceInput({
  onTranscript,
  placeholder = "Click to speak",
  disabled = false,
  className = "",
  buttonClassName = "",
}: VoiceInputProps) {
  const { consentGranted } = useConsent('voice_processing');
  const { isListening, transcript, error, startListening, stopListening, isSupported } = useBrowserSpeechRecognition();

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

  const toggleListening = () => {
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
            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }
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
      
      {error && (
        <span className="text-sm text-red-600">
          Error: {error}
        </span>
      )}
    </div>
  );
}
