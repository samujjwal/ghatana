/**
 * useVoiceInput Hook
 * 
 * React hook for voice input functionality.
 * Provides easy integration with the VoiceInputService.
 * 
 * @doc.type hook
 * @doc.purpose Voice input integration
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  VoiceInputService, 
  getVoiceInputService,
  type VoiceInputStatus,
  type VoiceInputResult,
} from '../services/VoiceInputService';

interface UseVoiceInputOptions {
  language?: string;
  continuous?: boolean;
  onTranscript?: (transcript: string, isFinal: boolean) => void;
  onFinalTranscript?: (transcript: string) => void;
  onError?: (error: string) => void;
}

interface UseVoiceInputResult {
  isSupported: boolean;
  status: VoiceInputStatus;
  isListening: boolean;
  transcript: string;
  interimTranscript: string;
  confidence: number;
  
  startListening: () => boolean;
  stopListening: () => void;
  toggleListening: () => void;
  clearTranscript: () => void;
}

export function useVoiceInput(options: UseVoiceInputOptions = {}): UseVoiceInputResult {
  const [status, setStatus] = useState<VoiceInputStatus>('idle');
  const [transcript, setTranscript] = useState('');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [confidence, setConfidence] = useState(0);

  const isSupported = useMemo(() => VoiceInputService.isSupported(), []);

  const service = useMemo(() => {
    if (!isSupported) return null;
    
    return getVoiceInputService({
      language: options.language || 'en-US',
      continuous: options.continuous || false,
      interimResults: true,
      onResult: (result: VoiceInputResult) => {
        if (result.isFinal) {
          setTranscript(result.transcript);
          setInterimTranscript('');
          setConfidence(result.confidence);
        } else {
          setInterimTranscript(result.transcript);
        }
      },
      onStatusChange: (newStatus: VoiceInputStatus) => {
        setStatus(newStatus);
      },
    });
  }, [isSupported, options.language, options.continuous]);
  
  // Handle callbacks separately to avoid recreating service
  useEffect(() => {
    if (!service) return;
    
    service.setOptions({
      language: options.language,
      continuous: options.continuous,
      onResult: (result: VoiceInputResult) => {
        if (result.isFinal) {
          setTranscript(result.transcript);
          setInterimTranscript('');
          setConfidence(result.confidence);
          options.onFinalTranscript?.(result.transcript);
        } else {
          setInterimTranscript(result.transcript);
        }
        options.onTranscript?.(result.transcript, result.isFinal);
      },
      onError: options.onError,
    });
  }, [service, options.language, options.continuous, options.onTranscript, options.onFinalTranscript, options.onError]);

  const startListening = useCallback(() => {
    if (!service) return false;
    return service.start();
  }, [service]);

  const stopListening = useCallback(() => {
    if (service) {
      service.stop();
    }
  }, [service]);

  const toggleListening = useCallback(() => {
    if (status === 'listening') {
      stopListening();
    } else {
      startListening();
    }
  }, [status, startListening, stopListening]);

  const clearTranscript = useCallback(() => {
    setTranscript('');
    setInterimTranscript('');
    setConfidence(0);
  }, []);

  return {
    isSupported,
    status,
    isListening: status === 'listening',
    transcript,
    interimTranscript,
    confidence,
    startListening,
    stopListening,
    toggleListening,
    clearTranscript,
  };
}

export default useVoiceInput;
