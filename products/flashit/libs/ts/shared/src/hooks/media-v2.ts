/**
 * Enhanced media recording hooks for Flashit - V2
 * Supports both live mode and upload mode with progressive upload
 *
 * @doc.type hooks
 * @doc.purpose Enhanced media recording with live streaming and progressive upload
 * @doc.layer platform
 * @doc.pattern ReactHooks
 */

import { useState, useCallback, useRef, useEffect } from 'react';

// Recording modes
export type RecordingMode = 'upload' | 'live' | 'progressive';

// Enhanced recording configuration
export interface MediaRecordingConfig {
  maxDuration: number; // in milliseconds
  mode: RecordingMode;
  mimeType?: string;
  videoBitsPerSecond?: number;
  audioBitsPerSecond?: number;
  chunkSize?: number; // for progressive upload (in bytes)
  liveStreamEndpoint?: string; // for live mode
  progressiveUploadEndpoint?: string; // for progressive upload
}

// Audio configs by mode
export const AUDIO_CONFIGS = {
  upload: {
    maxDuration: 5 * 60 * 1000, // 5 minutes
    mode: 'upload' as RecordingMode,
    mimeType: 'audio/webm',
    audioBitsPerSecond: 128000,
  },
  live: {
    maxDuration: 30 * 60 * 1000, // 30 minutes for live
    mode: 'live' as RecordingMode,
    mimeType: 'audio/webm',
    audioBitsPerSecond: 64000, // Lower bitrate for live
    chunkSize: 64 * 1024, // 64KB chunks
  },
  progressive: {
    maxDuration: 15 * 60 * 1000, // 15 minutes
    mode: 'progressive' as RecordingMode,
    mimeType: 'audio/webm',
    audioBitsPerSecond: 128000,
    chunkSize: 512 * 1024, // 512KB chunks
  },
} as const;

// Video configs by mode
export const VIDEO_CONFIGS = {
  upload: {
    maxDuration: 2 * 60 * 1000, // 2 minutes
    mode: 'upload' as RecordingMode,
    mimeType: 'video/webm',
    videoBitsPerSecond: 1000000, // 1 Mbps
    audioBitsPerSecond: 128000,
  },
  live: {
    maxDuration: 60 * 60 * 1000, // 60 minutes for live
    mode: 'live' as RecordingMode,
    mimeType: 'video/webm',
    videoBitsPerSecond: 500000, // 500 Kbps for live
    audioBitsPerSecond: 64000,
    chunkSize: 256 * 1024, // 256KB chunks
  },
  progressive: {
    maxDuration: 30 * 60 * 1000, // 30 minutes
    mode: 'progressive' as RecordingMode,
    mimeType: 'video/webm',
    videoBitsPerSecond: 1500000, // 1.5 Mbps
    audioBitsPerSecond: 128000,
    chunkSize: 1024 * 1024, // 1MB chunks
  },
} as const;

// Enhanced recording result
export interface RecordingResult {
  uri?: string; // For upload mode
  chunks?: Blob[]; // For progressive mode
  streamId?: string; // For live mode
  duration: number;
  size: number;
  mimeType: string;
  mode: RecordingMode;
  chunkCount?: number;
}

// Progressive upload progress
export interface UploadProgress {
  uploadedChunks: number;
  totalChunks: number;
  uploadedBytes: number;
  totalBytes: number;
  percentage: number;
  currentChunk?: {
    index: number;
    size: number;
    status: 'pending' | 'uploading' | 'completed' | 'failed';
  };
}

// Enhanced recording hook
export interface MediaRecorderHookV2 {
  state: RecordingState;
  mode: RecordingMode;
  duration: number;
  recording: RecordingResult | null;
  permission: PermissionState;
  isSupported: boolean;
  error: string | null;
  uploadProgress?: UploadProgress;
  streamStatus?: 'connected' | 'disconnected' | 'connecting';

  startRecording: () => Promise<void>;
  stopRecording: () => Promise<void>;
  pauseRecording: () => Promise<void>;
  resumeRecording: () => Promise<void>;
  switchMode: (newMode: RecordingMode) => void;
  requestPermission: () => Promise<boolean>;
  clearRecording: () => void;
}

// Recording states from previous implementation
export type RecordingState = 'idle' | 'recording' | 'paused' | 'stopped';
export type PermissionState = 'granted' | 'denied' | 'prompt' | 'checking';

const isWeb = typeof window !== 'undefined';

/**
 * Enhanced audio recording hook with multiple modes
 */
export function useAudioRecorderV2(mode: RecordingMode = 'upload'): MediaRecorderHookV2 {
  const config = AUDIO_CONFIGS[mode];

  const [currentMode, setCurrentMode] = useState<RecordingMode>(mode);
  const [state, setState] = useState<RecordingState>('idle');
  const [duration, setDuration] = useState(0);
  const [recording, setRecording] = useState<RecordingResult | null>(null);
  const [permission, setPermission] = useState<PermissionState>('prompt');
  const [error, setError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<UploadProgress>();
  const [streamStatus, setStreamStatus] = useState<'connected' | 'disconnected' | 'connecting'>();

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const startTimeRef = useRef<number>(0);
  const uploadIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Check if recording is supported
  const isSupported = isWeb && 
    (typeof navigator !== 'undefined' && 'mediaDevices' in navigator && 
     typeof window !== 'undefined' && 'MediaRecorder' in window);

  // Progressive upload handler
  const handleProgressiveUpload = useCallback(async (chunk: Blob, index: number) => {
    if (!config || config.mode !== 'progressive' || !('progressiveUploadEndpoint' in config)) return;

    try {
      setUploadProgress(prev => prev ? {
        ...prev,
        currentChunk: { index, size: chunk.size, status: 'uploading' }
      } : undefined);

      // Upload chunk to progressive endpoint
      const formData = new FormData();
      formData.append('chunk', chunk);
      formData.append('index', index.toString());
      formData.append('timestamp', Date.now().toString());

      const endpoint = (config as any).progressiveUploadEndpoint;
      const response = await fetch(endpoint, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error('Chunk upload failed');

      setUploadProgress(prev => {
        if (!prev) return undefined;
        const uploadedChunks = prev.uploadedChunks + 1;
        const uploadedBytes = prev.uploadedBytes + chunk.size;
        return {
          ...prev,
          uploadedChunks,
          uploadedBytes,
          percentage: Math.round((uploadedBytes / prev.totalBytes) * 100),
          currentChunk: { index, size: chunk.size, status: 'completed' }
        };
      });

    } catch (error) {
      setUploadProgress(prev => prev ? {
        ...prev,
        currentChunk: { index, size: chunk.size, status: 'failed' }
      } : undefined);
      throw error;
    }
  }, [(config as any).progressiveUploadEndpoint]);

  // Live stream handler
  const handleLiveStream = useCallback(async (chunk: Blob) => {
    if (!config || config.mode !== 'live' || !('liveStreamEndpoint' in config)) return;

    try {
      // Send chunk to live stream endpoint
      const endpoint = (config as any).liveStreamEndpoint;
      const response = await fetch(endpoint, {
        method: 'POST',
        body: chunk,
        headers: {
          'Content-Type': config.mimeType || 'audio/webm',
          'X-Stream-Timestamp': Date.now().toString(),
        },
      });

      if (!response.ok) throw new Error('Live stream upload failed');

      setStreamStatus('connected');
    } catch (error) {
      setStreamStatus('disconnected');
      console.error('Live stream error:', error);
    }
  }, [(config as any).liveStreamEndpoint, config.mimeType]);

  // Request permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    setPermission('checking');
    setError(null);

    try {
      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        stream.getTracks().forEach(track => track.stop());
        setPermission('granted');
        return true;
      }
      return false;
    } catch (err: any) {
      setPermission('denied');
      setError(err.message || 'Permission denied');
      return false;
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

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const currentConfig = AUDIO_CONFIGS[currentMode];
      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: currentConfig.mimeType,
        audioBitsPerSecond: currentConfig.audioBitsPerSecond,
      });

      chunksRef.current = [];
      let chunkIndex = 0;

      if (currentMode === 'live') {
        setStreamStatus('connecting');
      } else if (currentMode === 'progressive') {
        setUploadProgress({
          uploadedChunks: 0,
          totalChunks: 0,
          uploadedBytes: 0,
          totalBytes: 0,
          percentage: 0,
        });
      }

      mediaRecorder.ondataavailable = async (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);

          // Handle different modes
          if (currentMode === 'live') {
            await handleLiveStream(event.data);
          } else if (currentMode === 'progressive' && event.data.size >= ((currentConfig as any).chunkSize || 512000)) {
            await handleProgressiveUpload(event.data, chunkIndex++);
          }
        }
      };

      mediaRecorder.onstop = () => {
        const totalSize = chunksRef.current.reduce((sum, chunk) => sum + chunk.size, 0);
        const duration = Date.now() - startTimeRef.current;

        if (currentMode === 'upload') {
          const blob = new Blob(chunksRef.current, { type: currentConfig.mimeType });
          const uri = URL.createObjectURL(blob);

          setRecording({
            uri,
            duration,
            size: blob.size,
            mimeType: currentConfig.mimeType!,
            mode: currentMode,
          });
        } else {
          setRecording({
            chunks: [...chunksRef.current],
            duration,
            size: totalSize,
            mimeType: currentConfig.mimeType!,
            mode: currentMode,
            chunkCount: chunksRef.current.length,
          });
        }

        setState('stopped');
        if (currentMode === 'live') {
          setStreamStatus('disconnected');
        }
      };

      mediaRecorderRef.current = mediaRecorder;
      startTimeRef.current = Date.now();

      // Start recording with appropriate chunk interval
      const chunkInterval = currentMode === 'live' ? 1000 :
                          currentMode === 'progressive' ? 5000 : 100;
      mediaRecorder.start(chunkInterval);
      setState('recording');

      // Auto-stop at max duration
      setTimeout(() => {
        if (mediaRecorderRef.current?.state === 'recording') {
          stopRecording();
        }
      }, currentConfig.maxDuration);

      // Update duration
      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);

    } catch (err: any) {
      setError(err.message || 'Failed to start recording');
      setState('idle');
    }
  }, [isSupported, permission, currentMode, requestPermission, handleLiveStream, handleProgressiveUpload]);

  // Stop recording
  const stopRecording = useCallback(async (): Promise<void> => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.stop();
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
  }, []);

  // Pause recording
  const pauseRecording = useCallback(async (): Promise<void> => {
    if (mediaRecorderRef.current?.state === 'recording') {
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
    if (mediaRecorderRef.current?.state === 'paused') {
      mediaRecorderRef.current.resume();
      setState('recording');

      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);
    }
  }, []);

  // Switch recording mode
  const switchMode = useCallback((newMode: RecordingMode) => {
    if (state !== 'idle') {
      setError('Cannot switch mode while recording');
      return;
    }
    setCurrentMode(newMode);
    setError(null);
    setUploadProgress(undefined);
    setStreamStatus(undefined);
  }, [state]);

  // Clear recording
  const clearRecording = useCallback(() => {
    if (recording?.uri) {
      URL.revokeObjectURL(recording.uri);
    }
    setRecording(null);
    setDuration(0);
    setState('idle');
    setError(null);
    setUploadProgress(undefined);
    setStreamStatus(undefined);
  }, [recording]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      if (uploadIntervalRef.current) clearInterval(uploadIntervalRef.current);
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
      if (recording?.uri) {
        URL.revokeObjectURL(recording.uri);
      }
    };
  }, [recording]);

  return {
    state,
    mode: currentMode,
    duration,
    recording,
    permission,
    isSupported,
    error,
    uploadProgress,
    streamStatus,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    switchMode,
    requestPermission,
    clearRecording,
  };
}

/**
 * Enhanced video recording hook with multiple modes
 */
export function useVideoRecorderV2(mode: RecordingMode = 'upload'): MediaRecorderHookV2 {
  const config = VIDEO_CONFIGS[mode];

  const [currentMode, setCurrentMode] = useState<RecordingMode>(mode);
  const [state, setState] = useState<RecordingState>('idle');
  const [duration, setDuration] = useState(0);
  const [recording, setRecording] = useState<RecordingResult | null>(null);
  const [permission, setPermission] = useState<PermissionState>('prompt');
  const [error, setError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<UploadProgress>();
  const [streamStatus, setStreamStatus] = useState<'connected' | 'disconnected' | 'connecting'>();

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const startTimeRef = useRef<number>(0);

  const isSupported = isWeb && 
    (typeof navigator !== 'undefined' && 'mediaDevices' in navigator && 
     typeof window !== 'undefined' && 'MediaRecorder' in window);

  // Similar implementation to audio but with video constraints
  const requestPermission = useCallback(async (): Promise<boolean> => {
    setPermission('checking');
    setError(null);

    try {
      if (isWeb) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: true
        });
        stream.getTracks().forEach(track => track.stop());
        setPermission('granted');
        return true;
      }
      return false;
    } catch (err: any) {
      setPermission('denied');
      setError(err.message || 'Camera/microphone permission denied');
      return false;
    }
  }, []);

  // Video recording implementation with mode support
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

      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: currentMode === 'live' ? 640 : 1280,
          height: currentMode === 'live' ? 480 : 720
        },
        audio: true
      });
      streamRef.current = stream;

      const currentConfig = VIDEO_CONFIGS[currentMode];
      const mediaRecorder = new MediaRecorder(stream, {
        mimeType: currentConfig.mimeType,
        videoBitsPerSecond: currentConfig.videoBitsPerSecond,
        audioBitsPerSecond: currentConfig.audioBitsPerSecond,
      });

      chunksRef.current = [];

      if (currentMode === 'live') {
        setStreamStatus('connecting');
      } else if (currentMode === 'progressive') {
        setUploadProgress({
          uploadedChunks: 0,
          totalChunks: 0,
          uploadedBytes: 0,
          totalBytes: 0,
          percentage: 0,
        });
      }

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
          // Handle progressive/live upload logic here
        }
      };

      mediaRecorder.onstop = () => {
        const totalSize = chunksRef.current.reduce((sum, chunk) => sum + chunk.size, 0);
        const duration = Date.now() - startTimeRef.current;

        if (currentMode === 'upload') {
          const blob = new Blob(chunksRef.current, { type: currentConfig.mimeType });
          const uri = URL.createObjectURL(blob);

          setRecording({
            uri,
            duration,
            size: blob.size,
            mimeType: currentConfig.mimeType!,
            mode: currentMode,
          });
        } else {
          setRecording({
            chunks: [...chunksRef.current],
            duration,
            size: totalSize,
            mimeType: currentConfig.mimeType!,
            mode: currentMode,
            chunkCount: chunksRef.current.length,
          });
        }

        setState('stopped');
      };

      mediaRecorderRef.current = mediaRecorder;
      startTimeRef.current = Date.now();

      const chunkInterval = currentMode === 'live' ? 1000 :
                          currentMode === 'progressive' ? 5000 : 100;
      mediaRecorder.start(chunkInterval);
      setState('recording');

      setTimeout(() => {
        if (mediaRecorderRef.current?.state === 'recording') {
          stopRecording();
        }
      }, currentConfig.maxDuration);

      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);

    } catch (err: any) {
      setError(err.message || 'Failed to start video recording');
      setState('idle');
    }
  }, [isSupported, permission, currentMode, requestPermission]);

  // Other methods similar to audio implementation
  const stopRecording = useCallback(async (): Promise<void> => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.stop();
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
  }, []);

  const pauseRecording = useCallback(async (): Promise<void> => {
    if (mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.pause();
      setState('paused');

      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }
  }, []);

  const resumeRecording = useCallback(async (): Promise<void> => {
    if (mediaRecorderRef.current?.state === 'paused') {
      mediaRecorderRef.current.resume();
      setState('recording');

      intervalRef.current = setInterval(() => {
        setDuration(Date.now() - startTimeRef.current);
      }, 100);
    }
  }, []);

  const switchMode = useCallback((newMode: RecordingMode) => {
    if (state !== 'idle') {
      setError('Cannot switch mode while recording');
      return;
    }
    setCurrentMode(newMode);
    setError(null);
    setUploadProgress(undefined);
    setStreamStatus(undefined);
  }, [state]);

  const clearRecording = useCallback(() => {
    if (recording?.uri) {
      URL.revokeObjectURL(recording.uri);
    }
    setRecording(null);
    setDuration(0);
    setState('idle');
    setError(null);
    setUploadProgress(undefined);
    setStreamStatus(undefined);
  }, [recording]);

  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
      if (recording?.uri) {
        URL.revokeObjectURL(recording.uri);
      }
    };
  }, [recording]);

  return {
    state,
    mode: currentMode,
    duration,
    recording,
    permission,
    isSupported,
    error,
    uploadProgress,
    streamStatus,
    startRecording,
    stopRecording,
    pauseRecording,
    resumeRecording,
    switchMode,
    requestPermission,
    clearRecording,
  };
}
