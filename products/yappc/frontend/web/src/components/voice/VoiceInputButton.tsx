/**
 * VoiceInputButton Component
 * 
 * Microphone button with visual feedback for voice input.
 * Shows listening state with animated visualization.
 * 
 * @doc.type component
 * @doc.purpose Voice input UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { useVoiceInput } from '../../hooks/useVoiceInput';

interface VoiceInputButtonProps {
  onTranscript?: (transcript: string) => void;
  onFinalTranscript?: (transcript: string) => void;
  size?: 'small' | 'medium' | 'large';
  className?: string;
  disabled?: boolean;
}

export function VoiceInputButton({
  onTranscript,
  onFinalTranscript,
  size = 'medium',
  className = '',
  disabled = false,
}: VoiceInputButtonProps) {
  const {
    isSupported,
    isListening,
    status,
    interimTranscript,
    toggleListening,
  } = useVoiceInput({
    onTranscript,
    onFinalTranscript,
  });

  if (!isSupported) {
    return null; // Don't render if not supported
  }

  const sizeClasses = {
    small: 'w-8 h-8',
    medium: 'w-10 h-10',
    large: 'w-12 h-12',
  };

  const iconSizes = {
    small: 'w-4 h-4',
    medium: 'w-5 h-5',
    large: 'w-6 h-6',
  };

  return (
    <div className={`relative ${className}`}>
      <button
        type="button"
        onClick={toggleListening}
        disabled={disabled || status === 'error'}
        className={`
          ${sizeClasses[size]}
          rounded-full flex items-center justify-center
          transition-all duration-200
          ${isListening 
            ? 'bg-red-500 text-white shadow-lg shadow-red-500/30' 
            : 'bg-grey-100 dark:bg-grey-800 text-text-secondary hover:bg-grey-200 dark:hover:bg-grey-700'
          }
          ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
          focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
        `}
        title={isListening ? 'Stop listening' : 'Start voice input'}
        aria-label={isListening ? 'Stop voice input' : 'Start voice input'}
      >
        {/* Microphone Icon */}
        <svg
          className={iconSizes[size]}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"
          />
        </svg>

        {/* Listening Animation */}
        {isListening && (
          <>
            <span className="absolute inset-0 rounded-full animate-ping bg-red-400 opacity-75" />
            <span className="absolute inset-0 rounded-full animate-pulse bg-red-500 opacity-50" />
          </>
        )}
      </button>

      {/* Interim Transcript Tooltip */}
      {isListening && interimTranscript && (
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 bg-bg-paper border border-divider rounded-lg shadow-lg max-w-xs">
          <p className="text-sm text-text-secondary italic truncate">
            "{interimTranscript}"
          </p>
        </div>
      )}
    </div>
  );
}

/**
 * VoiceInputIndicator - Shows listening status in a compact form
 */
export function VoiceInputIndicator({
  isListening,
  className = '',
}: {
  isListening: boolean;
  className?: string;
}) {
  if (!isListening) return null;

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <div className="flex items-center gap-1">
        <span className="w-1.5 h-3 bg-red-500 rounded-full animate-pulse" style={{ animationDelay: '0ms' }} />
        <span className="w-1.5 h-4 bg-red-500 rounded-full animate-pulse" style={{ animationDelay: '150ms' }} />
        <span className="w-1.5 h-2 bg-red-500 rounded-full animate-pulse" style={{ animationDelay: '300ms' }} />
        <span className="w-1.5 h-5 bg-red-500 rounded-full animate-pulse" style={{ animationDelay: '450ms' }} />
        <span className="w-1.5 h-3 bg-red-500 rounded-full animate-pulse" style={{ animationDelay: '600ms' }} />
      </div>
      <span className="text-xs text-red-500 font-medium">Listening...</span>
    </div>
  );
}

/**
 * VoiceInputField - Input field with integrated voice input
 */
export function VoiceInputField({
  value,
  onChange,
  onSubmit,
  placeholder = 'Type or speak...',
  className = '',
  disabled = false,
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit?: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}) {
  const {
    isSupported,
    isListening,
    interimTranscript,
    toggleListening,
  } = useVoiceInput({
    onFinalTranscript: (transcript) => {
      onChange(value ? `${value} ${transcript}` : transcript);
    },
  });

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey && onSubmit) {
      e.preventDefault();
      onSubmit(value);
    }
  };

  return (
    <div className={`relative flex items-center ${className}`}>
      <input
        type="text"
        value={isListening ? `${value} ${interimTranscript}`.trim() : value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        disabled={disabled || isListening}
        className={`
          flex-1 px-4 py-3 pr-12
          bg-bg-default border border-divider rounded-lg
          text-text-primary placeholder-text-tertiary
          focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent
          ${isListening ? 'bg-red-50 dark:bg-red-900/10 border-red-300 dark:border-red-800' : ''}
          ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
        `}
      />

      {isSupported && (
        <div className="absolute right-2">
          <VoiceInputButton
            size="small"
            disabled={disabled}
            onFinalTranscript={(transcript) => {
              onChange(value ? `${value} ${transcript}` : transcript);
            }}
          />
        </div>
      )}

      {/* Listening indicator */}
      {isListening && (
        <div className="absolute left-4 top-1/2 -translate-y-1/2">
          <VoiceInputIndicator isListening={isListening} />
        </div>
      )}
    </div>
  );
}

export default VoiceInputButton;
