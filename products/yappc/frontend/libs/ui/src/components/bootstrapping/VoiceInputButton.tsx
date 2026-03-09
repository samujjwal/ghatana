/**
 * Voice Input Button Component
 *
 * @description A button that enables voice input using the Web Speech API.
 * Provides visual feedback during recording with animated waveforms and
 * transcription display.
 *
 * @doc.type component
 * @doc.purpose Voice input capture
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Mic,
  MicOff,
  Square,
  Loader2,
  AlertCircle,
  CheckCircle2,
  Volume2,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Web Speech API Types (for environments without native types)
// =============================================================================

interface SpeechRecognitionResult {
  readonly isFinal: boolean;
  readonly length: number;
  item(index: number): SpeechRecognitionAlternative;
  [index: number]: SpeechRecognitionAlternative;
}

interface SpeechRecognitionAlternative {
  readonly transcript: string;
  readonly confidence: number;
}

interface SpeechRecognitionResultList {
  readonly length: number;
  item(index: number): SpeechRecognitionResult;
  [index: number]: SpeechRecognitionResult;
}

interface SpeechRecognitionEventInit extends EventInit {
  resultIndex?: number;
  results: SpeechRecognitionResultList;
}

interface SpeechRecognitionEventType extends Event {
  readonly resultIndex: number;
  readonly results: SpeechRecognitionResultList;
}

interface SpeechRecognitionErrorEventType extends Event {
  readonly error: 'no-speech' | 'audio-capture' | 'not-allowed' | 'network' | 'aborted' | 'bad-grammar' | 'language-not-supported' | 'service-not-allowed';
  readonly message: string;
}

interface SpeechRecognitionType extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onstart: ((this: SpeechRecognitionType, ev: Event) => void) | null;
  onend: ((this: SpeechRecognitionType, ev: Event) => void) | null;
  onerror: ((this: SpeechRecognitionType, ev: SpeechRecognitionErrorEventType) => void) | null;
  onresult: ((this: SpeechRecognitionType, ev: SpeechRecognitionEventType) => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

// =============================================================================
// Types
// =============================================================================

export type VoiceInputStatus = 'idle' | 'listening' | 'processing' | 'success' | 'error';

export interface VoiceInputButtonProps {
  /** Called when transcription is complete */
  onTranscription: (text: string) => void;
  /** Called when recording starts */
  onStart?: () => void;
  /** Called when recording stops */
  onStop?: () => void;
  /** Called on error */
  onError?: (error: string) => void;
  /** Disabled state */
  disabled?: boolean;
  /** Language for speech recognition */
  language?: string;
  /** Show interim results */
  showInterim?: boolean;
  /** Show waveform animation */
  showWaveform?: boolean;
  /** Button size */
  size?: 'sm' | 'md' | 'lg';
  /** Button variant */
  variant?: 'icon' | 'button' | 'pill';
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Voice Animation
// =============================================================================

const VoiceWaveform: React.FC<{ active: boolean; className?: string }> = ({
  active,
  className,
}) => {
  const bars = 5;

  return (
    <div className={cn('flex items-center justify-center gap-0.5', className)}>
      {Array.from({ length: bars }).map((_, i) => (
        <motion.div
          key={i}
          className="w-1 rounded-full bg-current"
          animate={
            active
              ? {
                  height: [12, 24, 12, 20, 12],
                  opacity: [0.5, 1, 0.5, 0.8, 0.5],
                }
              : { height: 12, opacity: 0.5 }
          }
          transition={
            active
              ? {
                  duration: 0.5 + i * 0.1,
                  repeat: Infinity,
                  ease: 'easeInOut',
                }
              : { duration: 0.2 }
          }
        />
      ))}
    </div>
  );
};

// =============================================================================
// Recording Timer
// =============================================================================

const RecordingTimer: React.FC<{ startTime: number }> = ({ startTime }) => {
  const [duration, setDuration] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setDuration(Math.floor((Date.now() - startTime) / 1000));
    }, 100);
    return () => clearInterval(interval);
  }, [startTime]);

  const minutes = Math.floor(duration / 60);
  const seconds = duration % 60;

  return (
    <span className="tabular-nums">
      {minutes.toString().padStart(2, '0')}:{seconds.toString().padStart(2, '0')}
    </span>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const VoiceInputButton: React.FC<VoiceInputButtonProps> = ({
  onTranscription,
  onStart,
  onStop,
  onError,
  disabled = false,
  language = 'en-US',
  showInterim = true,
  showWaveform = true,
  size = 'md',
  variant = 'button',
  className,
}) => {
  const [status, setStatus] = useState<VoiceInputStatus>('idle');
  const [interimText, setInterimText] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [startTime, setStartTime] = useState<number | null>(null);
  
  const recognitionRef = useRef<SpeechRecognitionType | null>(null);
  const isListening = status === 'listening';

  // Check browser support
  const isSpeechSupported = typeof window !== 'undefined' && 
    ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window);

  // Initialize speech recognition
  useEffect(() => {
    if (!isSpeechSupported) return;

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const SpeechRecognitionConstructor = (window as unknown).SpeechRecognition || (window as unknown).webkitSpeechRecognition;
    const recognition: SpeechRecognitionType = new SpeechRecognitionConstructor();
    
    recognition.continuous = true;
    recognition.interimResults = showInterim;
    recognition.lang = language;

    recognition.onstart = () => {
      setStatus('listening');
      setStartTime(Date.now());
      setErrorMessage('');
      onStart?.();
    };

    recognition.onresult = (event: SpeechRecognitionEventType) => {
      let interim = '';
      let final = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          final += transcript;
        } else {
          interim += transcript;
        }
      }

      if (showInterim) {
        setInterimText(interim);
      }

      if (final) {
        setStatus('success');
        onTranscription(final.trim());
        setTimeout(() => {
          setStatus('idle');
          setInterimText('');
        }, 1500);
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEventType) => {
      let message = 'An error occurred';
      switch (event.error) {
        case 'no-speech':
          message = 'No speech detected';
          break;
        case 'audio-capture':
          message = 'No microphone found';
          break;
        case 'not-allowed':
          message = 'Microphone access denied';
          break;
        case 'network':
          message = 'Network error';
          break;
        default:
          message = `Error: ${event.error}`;
      }
      setErrorMessage(message);
      setStatus('error');
      onError?.(message);
      setTimeout(() => setStatus('idle'), 3000);
    };

    recognition.onend = () => {
      setStartTime(null);
      onStop?.();
      // Only set to idle if not already in a terminal state
      setStatus((currentStatus) => {
        if (currentStatus === 'listening') {
          return 'idle';
        }
        return currentStatus;
      });
    };

    recognitionRef.current = recognition;

    return () => {
      recognition.stop();
    };
  }, [isSpeechSupported, language, showInterim, onStart, onStop, onError, onTranscription, status]);

  // Toggle recording
  const toggleRecording = useCallback(() => {
    if (!recognitionRef.current) return;

    if (isListening) {
      recognitionRef.current.stop();
    } else {
      setInterimText('');
      recognitionRef.current.start();
    }
  }, [isListening]);

  // Size styles
  const sizeStyles = {
    sm: { button: 'h-8 px-3', icon: 'h-4 w-4' },
    md: { button: 'h-10 px-4', icon: 'h-5 w-5' },
    lg: { button: 'h-12 px-6', icon: 'h-6 w-6' },
  };

  // Status icon
  const StatusIcon = {
    idle: Mic,
    listening: MicOff,
    processing: Loader2,
    success: CheckCircle2,
    error: AlertCircle,
  }[status];

  // Status colors
  const statusColors = {
    idle: '',
    listening: 'bg-error-500 hover:bg-error-600 text-white',
    processing: 'bg-primary-500 text-white',
    success: 'bg-success-500 text-white',
    error: 'bg-error-500 text-white',
  };

  // Unsupported browser
  if (!isSpeechSupported) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="outline"
            size={size}
            disabled
            className={className}
          >
            <MicOff className={sizeStyles[size].icon} />
            {variant !== 'icon' && <span className="ml-2">Voice Input</span>}
          </Button>
        </TooltipTrigger>
        <TooltipContent>
          Voice input is not supported in this browser
        </TooltipContent>
      </Tooltip>
    );
  }

  // Icon variant
  if (variant === 'icon') {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <motion.button
            type="button"
            onClick={toggleRecording}
            disabled={disabled || status === 'processing'}
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
            className={cn(
              'relative flex items-center justify-center rounded-full transition-colors',
              'focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2',
              sizeStyles[size].button,
              statusColors[status],
              disabled && 'opacity-50 cursor-not-allowed',
              !isListening && !statusColors[status] && 'bg-neutral-100 hover:bg-neutral-200 dark:bg-neutral-800 dark:hover:bg-neutral-700',
              className
            )}
          >
            {status === 'processing' ? (
              <motion.div
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
              >
                <StatusIcon className={sizeStyles[size].icon} />
              </motion.div>
            ) : (
              <StatusIcon className={sizeStyles[size].icon} />
            )}
            
            {/* Pulse animation when listening */}
            <AnimatePresence>
              {isListening && (
                <motion.div
                  initial={{ scale: 1, opacity: 0.5 }}
                  animate={{ scale: 1.5, opacity: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 1, repeat: Infinity }}
                  className="absolute inset-0 rounded-full bg-error-500"
                />
              )}
            </AnimatePresence>
          </motion.button>
        </TooltipTrigger>
        <TooltipContent>
          {isListening ? 'Stop recording' : 'Start voice input'}
        </TooltipContent>
      </Tooltip>
    );
  }

  // Pill variant (shows status and timer)
  if (variant === 'pill') {
    return (
      <motion.div
        layout
        className={cn(
          'inline-flex items-center gap-2 rounded-full px-4 py-2 transition-colors',
          isListening
            ? 'bg-error-100 text-error-700 dark:bg-error-900/30 dark:text-error-300'
            : 'bg-neutral-100 dark:bg-neutral-800',
          className
        )}
      >
        <motion.button
          type="button"
          onClick={toggleRecording}
          disabled={disabled || status === 'processing'}
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          className={cn(
            'flex h-8 w-8 items-center justify-center rounded-full',
            isListening ? 'bg-error-500 text-white' : 'bg-primary-500 text-white',
            disabled && 'opacity-50 cursor-not-allowed'
          )}
        >
          {isListening ? (
            <Square className="h-4 w-4" />
          ) : (
            <Mic className="h-4 w-4" />
          )}
        </motion.button>

        <div className="flex flex-col items-start">
          <span className="text-sm font-medium">
            {isListening ? 'Recording...' : 'Voice Input'}
          </span>
          {isListening && startTime && (
            <span className="text-xs opacity-75">
              <RecordingTimer startTime={startTime} />
            </span>
          )}
        </div>

        {showWaveform && isListening && (
          <VoiceWaveform active={isListening} className="text-error-500" />
        )}
      </motion.div>
    );
  }

  // Button variant (default)
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <motion.div layout className="flex items-center gap-2">
        <Button
          variant={isListening ? 'solid' : 'outline'}
          colorScheme={isListening ? 'error' : 'primary'}
          size={size}
          onClick={toggleRecording}
          disabled={disabled || status === 'processing'}
          className="flex-shrink-0"
        >
          {status === 'processing' ? (
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
            >
              <Loader2 className={cn('mr-2', sizeStyles[size].icon)} />
            </motion.div>
          ) : isListening ? (
            <Square className={cn('mr-2', sizeStyles[size].icon)} />
          ) : (
            <Mic className={cn('mr-2', sizeStyles[size].icon)} />
          )}
          {isListening ? 'Stop' : 'Voice Input'}
        </Button>

        {/* Timer */}
        {isListening && startTime && (
          <Badge variant="outline" className="text-sm">
            <RecordingTimer startTime={startTime} />
          </Badge>
        )}

        {/* Waveform */}
        {showWaveform && isListening && (
          <VoiceWaveform active className="text-error-500 h-6" />
        )}
      </motion.div>

      {/* Interim text */}
      <AnimatePresence>
        {showInterim && interimText && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="rounded-lg bg-neutral-100 p-3 dark:bg-neutral-800"
          >
            <div className="flex items-start gap-2">
              <Volume2 className="h-4 w-4 mt-0.5 text-neutral-500 shrink-0" />
              <p className="text-sm text-neutral-700 dark:text-neutral-300 italic">
                {interimText}
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Error message */}
      <AnimatePresence>
        {status === 'error' && errorMessage && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="flex items-center gap-2 rounded-lg bg-error-100 p-2 text-error-700 dark:bg-error-900/30 dark:text-error-300"
          >
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span className="text-sm">{errorMessage}</span>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Success message */}
      <AnimatePresence>
        {status === 'success' && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="flex items-center gap-2 rounded-lg bg-success-100 p-2 text-success-700 dark:bg-success-900/30 dark:text-success-300"
          >
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span className="text-sm">Transcription complete</span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default VoiceInputButton;
