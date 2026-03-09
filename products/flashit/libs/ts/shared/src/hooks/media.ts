/**
 * Media recording hooks for Flashit
 * Cross-platform audio and video recording with duration limits
 *
 * @doc.type hooks
 * @doc.purpose Provide cross-platform media recording capabilities
 * @doc.layer platform
 * @doc.pattern ReactHooks
 */

import { useState, useCallback, useRef, useEffect } from 'react';

// Platform detection
const isWeb = typeof window !== 'undefined';
const isReactNative = !isWeb;

// Media recording configuration
export interface MediaRecordingConfig {
  maxDuration: number; // in milliseconds
  mimeType?: string;
  videoBitsPerSecond?: number;
  audioBitsPerSecond?: number;
}

// Audio recording config (≤ 5 minutes)
export const AUDIO_CONFIG: MediaRecordingConfig = {
  maxDuration: 5 * 60 * 1000, // 5 minutes
  mimeType: isWeb ? 'audio/webm' : 'audio/mp4',
  audioBitsPerSecond: 128000, // 128 kbps
};

// Video recording config (≤ 2 minutes)
export const VIDEO_CONFIG: MediaRecordingConfig = {
  maxDuration: 2 * 60 * 1000, // 2 minutes
  mimeType: isWeb ? 'video/webm' : 'video/mp4',
  videoBitsPerSecond: 1000000, // 1 Mbps
  audioBitsPerSecond: 128000, // 128 kbps
};

// Recording states
export type RecordingState = 'idle' | 'recording' | 'paused' | 'stopped';

// Recording result
export interface RecordingResult {
  uri: string;
  duration: number;
  size: number;
  mimeType: string;
}

// Permission state
export type PermissionState = 'granted' | 'denied' | 'prompt' | 'checking';

// Recording hook return type
export interface MediaRecorderHook {
  state: RecordingState;
  duration: number;
  recording: RecordingResult | null;
  permission: PermissionState;
  isSupported: boolean;
  error: string | null;
  startRecording: () => Promise<void>;
  stopRecording: () => Promise<void>;
  pauseRecording: () => Promise<void>;
  resumeRecording: () => Promise<void>;
  requestPermission: () => Promise<boolean>;
  clearRecording: () => void;
}

/**
 * Audio recording hook
 * Records audio with automatic duration limits
 */
export function useAudioRecorder(config: Partial<MediaRecordingConfig> = {}): MediaRecorderHook {
  const fullConfig = { ...AUDIO_CONFIG, ...config };

  const [state, setState] = useState<RecordingState>('idle');
  const [duration, setDuration] = useState(0);
  const [recording, setRecording] = useState<RecordingResult | null>(null);
  const [permission, setPermission] = useState<PermissionState>('prompt');
  const [error, setError] = useState<string | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const startTimeRef = useRef<number>(0);

  // Check if audio recording is supported
  const isSupported = isWeb ?
    (typeof navigator !== 'undefined' && 'mediaDevices' in navigator && typeof window !== 'undefined' && 'MediaRecorder' in window) :
    true; // React Native support via expo-av

  // Request audio permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    setPermission('checking');
    setError(null);

    try {
      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach(track => track.stop()); // Clean up test stream
        setPermission('granted');
        return true;
      } else {
        // React Native - will be implemented with expo-av
        // For now, assume granted (will be properly implemented)
        setPermission('granted');
        return true;
      }
    } catch (err: any) {
      setPermission('denied');
      setError(err.message || 'Permission denied');
      return false;
    }
  }, []);

  // Stop recording
  const stopRecording = useCallback(async (): Promise<void> => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    if (isWeb) {
      if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
        mediaRecorderRef.current.stop();
      }

      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
        streamRef.current = null;
      }
    } else {
      // React Native implementation placeholder
    }
  }, []);

  // Start recording
  const startRecording = useCallback(async (): Promise<void> => {
    if (!isSupported) {
      setError('Audio recording not supported');
      return;
    }

    if (permission !== 'granted') {
      const granted = await requestPermission();
      if (!granted) return;
    }

    try {
      setError(null);

      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        streamRef.current = stream;

        const mediaRecorder = new MediaRecorder(stream, {
          mimeType: fullConfig.mimeType,
          audioBitsPerSecond: fullConfig.audioBitsPerSecond,
        });

        chunksRef.current = [];

        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            chunksRef.current.push(event.data);
          }
        };

        mediaRecorder.onstop = () => {
          const blob = new Blob(chunksRef.current, { type: fullConfig.mimeType });
          const uri = URL.createObjectURL(blob);
          const duration = Date.now() - startTimeRef.current;

          setRecording({
            uri,
            duration,
            size: blob.size,
            mimeType: fullConfig.mimeType!,
          });

          setState('stopped');
        };

        mediaRecorderRef.current = mediaRecorder;
        startTimeRef.current = Date.now();
        mediaRecorder.start(100);
        setState('recording');

        setTimeout(() => {
          if (mediaRecorderRef.current?.state === 'recording') {
            void stopRecording();
          }
        }, fullConfig.maxDuration);

        intervalRef.current = setInterval(() => {
          setDuration(Date.now() - startTimeRef.current);
        }, 100);

      } else {
        setError('React Native audio recording not yet implemented');
      }
    } catch (err: any) {
      setError(err.message || 'Failed to start recording');
      setState('idle');
    }
  }, [isSupported, permission, requestPermission, fullConfig, stopRecording]);

  // Pause recording
  const pauseRecording = useCallback(async (): Promise<void> => {
    if (isWeb && mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.pause();
      setState('paused');

      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }
  }, []);

  // Resume recording
  const resumeRecording = useCallback(async (): Promise<void> => {
    if (isWeb && mediaRecorderRef.current?.state === 'paused') {
      mediaRecorderRef.current.resume();
      setState('recording');

      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);
    }
  }, []);

  // Clear recording
  const clearRecording = useCallback(() => {
    if (recording?.uri && isWeb) {
      URL.revokeObjectURL(recording.uri);
    }
    setRecording(null);
    setDuration(0);
    setState('idle');
    setError(null);
  }, [recording]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
      if (recording?.uri && isWeb) {
        URL.revokeObjectURL(recording.uri);
      }
    };
  }, [recording]);

  return {
    state,
    duration,
    recording,
    permission,
    isSupported,
    error,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    requestPermission,
    clearRecording,
  };
}

/**
 * Video recording hook
 * Records video with automatic duration limits
 */
export function useVideoRecorder(config: Partial<MediaRecordingConfig> = {}): MediaRecorderHook {
  const fullConfig = { ...VIDEO_CONFIG, ...config };

  const [state, setState] = useState<RecordingState>('idle');
  const [duration, setDuration] = useState(0);
  const [recording, setRecording] = useState<RecordingResult | null>(null);
  const [permission, setPermission] = useState<PermissionState>('prompt');
  const [error, setError] = useState<string | null>(null);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const startTimeRef = useRef<number>(0);

  // Check if video recording is supported
  const isSupported = isWeb ?
    (typeof navigator !== 'undefined' && 'mediaDevices' in navigator && typeof window !== 'undefined' && 'MediaRecorder' in window) :
    true; // React Native support via expo-av

  // Request camera and microphone permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    setPermission('checking');
    setError(null);

    try {
      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: true
        });
        stream.getTracks().forEach(track => track.stop()); // Clean up test stream
        setPermission('granted');
        return true;
      } else {
        // React Native - will be implemented with expo-av
        setPermission('granted');
        return true;
      }
    } catch (err: any) {
      setPermission('denied');
      setError(err.message || 'Camera/microphone permission denied');
      return false;
    }
  }, []);

  // Start recording
  const startRecording = useCallback(async (): Promise<void> => {
    if (!isSupported) {
      setError('Video recording not supported');
      return;
    }

    if (permission !== 'granted') {
      const granted = await requestPermission();
      if (!granted) return;
    }

    try {
      setError(null);

      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: 1280, height: 720 },
          audio: true
        });
        streamRef.current = stream;

        const mediaRecorder = new MediaRecorder(stream, {
          mimeType: fullConfig.mimeType,
          videoBitsPerSecond: fullConfig.videoBitsPerSecond,
          audioBitsPerSecond: fullConfig.audioBitsPerSecond,
        });

        chunksRef.current = [];

        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            chunksRef.current.push(event.data);
          }
        };

        mediaRecorder.onstop = () => {
          const blob = new Blob(chunksRef.current, { type: fullConfig.mimeType });
          const uri = URL.createObjectURL(blob);
          const duration = Date.now() - startTimeRef.current;

          setRecording({
            uri,
            duration,
            size: blob.size,
            mimeType: fullConfig.mimeType!,
          });

          setState('stopped');
        };

        mediaRecorderRef.current = mediaRecorder;
        startTimeRef.current = Date.now();
        mediaRecorder.start(100);
        setState('recording');

        // Auto-stop at max duration
        setTimeout(() => {
          if (mediaRecorderRef.current?.state === 'recording') {
            stopRecording();
          }
        }, fullConfig.maxDuration);

        // Update duration
        intervalRef.current = setInterval(() => {
          setDuration(Date.now() - startTimeRef.current);
        }, 100);

      } else {
        // React Native implementation placeholder
        setError('React Native video recording not yet implemented');
      }
    } catch (err: any) {
      setError(err.message || 'Failed to start video recording');
      setState('idle');
    }
  }, [isSupported, permission, requestPermission, fullConfig]);

  // Stop recording
  const stopRecording = useCallback(async (): Promise<void> => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    if (isWeb) {
      if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
        mediaRecorderRef.current.stop();
      }

      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
        streamRef.current = null;
      }
    }
  }, []);

  // Pause recording
  const pauseRecording = useCallback(async (): Promise<void> => {
    if (isWeb && mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.pause();
      setState('paused');

      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }
  }, []);

  // Resume recording
  const resumeRecording = useCallback(async (): Promise<void> => {
    if (isWeb && mediaRecorderRef.current?.state === 'paused') {
      mediaRecorderRef.current.resume();
      setState('recording');

      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);
    }
  }, []);

  // Clear recording
  const clearRecording = useCallback(() => {
    if (recording?.uri && isWeb) {
      URL.revokeObjectURL(recording.uri);
    }
    setRecording(null);
    setDuration(0);
    setState('idle');
    setError(null);
  }, [recording]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
      if (recording?.uri && isWeb) {
        URL.revokeObjectURL(recording.uri);
      }
    };
  }, [recording]);

  return {
    state,
    duration,
    recording,
    permission,
    isSupported,
    error,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    requestPermission,
    clearRecording,
  };
}

/**
 * Device capability checker
 * Detects available media devices and capabilities
 */
export interface DeviceCapabilities {
  hasCamera: boolean;
  hasMicrophone: boolean;
  supportedMimeTypes: string[];
  maxVideoResolution?: { width: number; height: number };
}

export async function getDeviceCapabilities(): Promise<DeviceCapabilities> {
  if (!isSupported) {
    return {
      hasCamera: false,
      hasMicrophone: false,
      supportedMimeTypes: [],
    };
  }

  try {
    if (isWeb) {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const hasCamera = devices.some(device => device.kind === 'videoinput');
      const hasMicrophone = devices.some(device => device.kind === 'audioinput');

      // Check supported MIME types
      const supportedMimeTypes: string[] = [];
      const typesToCheck = [
        'video/webm',
        'video/mp4',
        'audio/webm',
        'audio/mp4',
        'audio/wav',
      ];

      for (const type of typesToCheck) {
        if (MediaRecorder.isTypeSupported(type)) {
          supportedMimeTypes.push(type);
        }
      }

      return {
        hasCamera,
        hasMicrophone,
        supportedMimeTypes,
        maxVideoResolution: { width: 1920, height: 1080 }, // Default assumption
      };
    } else {
      // React Native capabilities
      return {
        hasCamera: true, // Assume available
        hasMicrophone: true, // Assume available
        supportedMimeTypes: ['video/mp4', 'audio/mp4'],
      };
    }
  } catch (error) {
    return {
      hasCamera: false,
      hasMicrophone: false,
      supportedMimeTypes: [],
    };
  }
}

const isSupported = isWeb ?
  (typeof navigator !== 'undefined' && 'mediaDevices' in navigator && typeof window !== 'undefined' && 'MediaRecorder' in window) :
  true;
