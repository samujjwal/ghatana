/**
 * Voice Capture Hook for Web
 * Uses MediaRecorder API for browser-based audio recording
 * 
 * @doc.type hook
 * @doc.purpose Provide voice recording with waveform visualization
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useRef, useCallback, useEffect } from 'react';

export interface VoiceCaptureState {
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
  audioUrl: string | null;
  audioBlob: Blob | null;
  error: string | null;
  audioLevels: number[];
  isSupported: boolean;
  permissionStatus: 'granted' | 'denied' | 'prompt' | 'checking';
}

export interface VoiceCaptureControls {
  startRecording: () => Promise<void>;
  stopRecording: () => Promise<void>;
  pauseRecording: () => void;
  resumeRecording: () => void;
  resetRecording: () => void;
  requestPermission: () => Promise<boolean>;
}

export interface UseVoiceCaptureOptions {
  maxDurationMs?: number;
  mimeType?: string;
  audioBitsPerSecond?: number;
  visualizerFftSize?: number;
  onRecordingComplete?: (blob: Blob, duration: number) => void;
  onError?: (error: string) => void;
}

const DEFAULT_MAX_DURATION_MS = 5 * 60 * 1000; // 5 minutes
const DEFAULT_MIME_TYPE = 'audio/webm;codecs=opus';
const FALLBACK_MIME_TYPES = ['audio/webm', 'audio/ogg', 'audio/mp4'];

/**
 * Custom hook for voice recording in the browser
 * 
 * @example
 * ```tsx
 * const { state, controls } = useVoiceCapture({
 *   maxDurationMs: 60000,
 *   onRecordingComplete: (blob, duration) => uploadAudio(blob)
 * });
 * ```
 */
export function useVoiceCapture(options: UseVoiceCaptureOptions = {}): {
  state: VoiceCaptureState;
  controls: VoiceCaptureControls;
} {
  const {
    maxDurationMs = DEFAULT_MAX_DURATION_MS,
    mimeType: preferredMimeType = DEFAULT_MIME_TYPE,
    audioBitsPerSecond = 128000,
    visualizerFftSize = 256,
    onRecordingComplete,
    onError,
  } = options;

  // State
  const [state, setState] = useState<VoiceCaptureState>({
    isRecording: false,
    isPaused: false,
    duration: 0,
    audioUrl: null,
    audioBlob: null,
    error: null,
    audioLevels: [],
    isSupported: typeof MediaRecorder !== 'undefined',
    permissionStatus: 'checking',
  });

  // Refs
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const startTimeRef = useRef<number>(0);
  const durationIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Get supported MIME type
  const getSupportedMimeType = useCallback((): string => {
    if (MediaRecorder.isTypeSupported(preferredMimeType)) {
      return preferredMimeType;
    }
    for (const type of FALLBACK_MIME_TYPES) {
      if (MediaRecorder.isTypeSupported(type)) {
        return type;
      }
    }
    return ''; // Browser default
  }, [preferredMimeType]);

  // Check permission status
  const checkPermissionStatus = useCallback(async () => {
    try {
      if (navigator.permissions) {
        const result = await navigator.permissions.query({ name: 'microphone' as PermissionName });
        setState(prev => ({ ...prev, permissionStatus: result.state as 'granted' | 'denied' | 'prompt' }));
        
        result.addEventListener('change', () => {
          setState(prev => ({ ...prev, permissionStatus: result.state as 'granted' | 'denied' | 'prompt' }));
        });
      }
    } catch {
      // Permissions API not supported, will check on first use
      setState(prev => ({ ...prev, permissionStatus: 'prompt' }));
    }
  }, []);

  // Initialize permission check
  useEffect(() => {
    checkPermissionStatus();
  }, [checkPermissionStatus]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanup();
    };
  }, []);

  // Cleanup function
  const cleanup = useCallback(() => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
    }
    if (state.audioUrl) {
      URL.revokeObjectURL(state.audioUrl);
    }
  }, [state.audioUrl]);

  // Audio level analyzer
  const startAudioAnalysis = useCallback(() => {
    if (!streamRef.current) return;

    try {
      audioContextRef.current = new AudioContext();
      analyserRef.current = audioContextRef.current.createAnalyser();
      analyserRef.current.fftSize = visualizerFftSize;
      
      const source = audioContextRef.current.createMediaStreamSource(streamRef.current);
      source.connect(analyserRef.current);

      const bufferLength = analyserRef.current.frequencyBinCount;
      const dataArray = new Uint8Array(bufferLength);

      const analyze = () => {
        if (!analyserRef.current) return;
        
        analyserRef.current.getByteFrequencyData(dataArray);
        
        // Calculate average level (0-1)
        const average = dataArray.reduce((a, b) => a + b, 0) / bufferLength / 255;
        
        setState(prev => ({
          ...prev,
          audioLevels: [...prev.audioLevels.slice(-49), average],
        }));

        animationFrameRef.current = requestAnimationFrame(analyze);
      };

      analyze();
    } catch (error) {
      console.error('Failed to start audio analysis:', error);
    }
  }, [visualizerFftSize]);

  // Request permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach(track => track.stop());
      setState(prev => ({ ...prev, permissionStatus: 'granted' }));
      return true;
    } catch (error) {
      setState(prev => ({ 
        ...prev, 
        permissionStatus: 'denied',
        error: 'Microphone permission denied'
      }));
      onError?.('Microphone permission denied');
      return false;
    }
  }, [onError]);

  // Start recording
  const startRecording = useCallback(async () => {
    try {
      // Check browser support
      if (!state.isSupported) {
        throw new Error('MediaRecorder is not supported in this browser');
      }

      // Get microphone stream
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

      setState(prev => ({ ...prev, permissionStatus: 'granted' }));

      // Create MediaRecorder
      const mimeType = getSupportedMimeType();
      mediaRecorderRef.current = new MediaRecorder(streamRef.current, {
        mimeType: mimeType || undefined,
        audioBitsPerSecond,
      });

      chunksRef.current = [];

      mediaRecorderRef.current.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };

      mediaRecorderRef.current.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: mimeType || 'audio/webm' });
        const url = URL.createObjectURL(blob);
        const finalDuration = Date.now() - startTimeRef.current;

        setState(prev => ({
          ...prev,
          isRecording: false,
          isPaused: false,
          audioBlob: blob,
          audioUrl: url,
        }));

        onRecordingComplete?.(blob, finalDuration);

        // Stop audio analysis
        if (animationFrameRef.current) {
          cancelAnimationFrame(animationFrameRef.current);
        }
        if (audioContextRef.current) {
          audioContextRef.current.close();
        }
      };

      mediaRecorderRef.current.onerror = (event) => {
        const error = 'Recording error occurred';
        setState(prev => ({ ...prev, error, isRecording: false }));
        onError?.(error);
      };

      // Start recording
      mediaRecorderRef.current.start(1000); // Collect data every second
      startTimeRef.current = Date.now();

      setState(prev => ({
        ...prev,
        isRecording: true,
        isPaused: false,
        duration: 0,
        audioUrl: null,
        audioBlob: null,
        error: null,
        audioLevels: [],
      }));

      // Start audio analysis for waveform
      startAudioAnalysis();

      // Start duration timer
      durationIntervalRef.current = setInterval(() => {
        const elapsed = Date.now() - startTimeRef.current;
        
        if (elapsed >= maxDurationMs) {
          stopRecording();
          return;
        }

        setState(prev => ({ ...prev, duration: elapsed }));
      }, 100);

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to start recording';
      setState(prev => ({ ...prev, error: errorMessage }));
      onError?.(errorMessage);
    }
  }, [
    state.isSupported,
    getSupportedMimeType,
    audioBitsPerSecond,
    maxDurationMs,
    startAudioAnalysis,
    onRecordingComplete,
    onError,
  ]);

  // Stop recording
  const stopRecording = useCallback(async () => {
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
    }
  }, []);

  // Pause recording
  const pauseRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.pause();
      setState(prev => ({ ...prev, isPaused: true }));
    }
  }, []);

  // Resume recording
  const resumeRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'paused') {
      mediaRecorderRef.current.resume();
      setState(prev => ({ ...prev, isPaused: false }));
    }
  }, []);

  // Reset recording
  const resetRecording = useCallback(() => {
    cleanup();
    setState(prev => ({
      ...prev,
      isRecording: false,
      isPaused: false,
      duration: 0,
      audioUrl: null,
      audioBlob: null,
      error: null,
      audioLevels: [],
    }));
    chunksRef.current = [];
  }, [cleanup]);

  return {
    state,
    controls: {
      startRecording,
      stopRecording,
      pauseRecording,
      resumeRecording,
      resetRecording,
      requestPermission,
    },
  };
}

/**
 * Utility to format duration in mm:ss format
 */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export default useVoiceCapture;
