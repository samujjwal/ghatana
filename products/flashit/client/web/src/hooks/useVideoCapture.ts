/**
 * Video Capture Hook for Web
 * Uses MediaRecorder API for browser-based video recording
 * 
 * @doc.type hook
 * @doc.purpose Provide video recording with camera preview and controls
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useRef, useCallback, useEffect } from 'react';

export interface VideoCaptureState {
  isRecording: boolean;
  isPaused: boolean;
  isCameraActive: boolean;
  isProcessing: boolean;
  duration: number;
  videoUrl: string | null;
  videoBlob: Blob | null;
  thumbnailUrl: string | null;
  error: string | null;
  isSupported: boolean;
  permissionStatus: 'granted' | 'denied' | 'prompt' | 'checking';
  availableCameras: MediaDeviceInfo[];
  selectedCameraId: string | null;
  facingMode: 'user' | 'environment';
}

export interface VideoCaptureControls {
  startCamera: (facingMode?: 'user' | 'environment') => Promise<void>;
  stopCamera: () => void;
  startRecording: () => Promise<void>;
  stopRecording: () => Promise<void>;
  pauseRecording: () => void;
  resumeRecording: () => void;
  switchCamera: () => Promise<void>;
  resetRecording: () => void;
  requestPermission: () => Promise<boolean>;
  processVideoFile: (file: File) => Promise<void>;
}

export interface UseVideoCaptureOptions {
  maxDurationMs?: number;
  maxSizeBytes?: number;
  resolution?: 'low' | 'medium' | 'high' | 'hd';
  videoBitsPerSecond?: number;
  audioBitsPerSecond?: number;
  includeAudio?: boolean;
  onRecordingComplete?: (blob: Blob, duration: number, thumbnailUrl: string | null) => void;
  onError?: (error: string) => void;
}

const DEFAULT_MAX_DURATION_MS = 3 * 60 * 1000; // 3 minutes
const DEFAULT_MAX_SIZE = 100 * 1024 * 1024; // 100MB
const DEFAULT_VIDEO_BITRATE = 2500000; // 2.5 Mbps
const DEFAULT_AUDIO_BITRATE = 128000; // 128 Kbps

const RESOLUTION_CONFIG = {
  low: { width: 640, height: 480 },
  medium: { width: 1280, height: 720 },
  high: { width: 1920, height: 1080 },
  hd: { width: 3840, height: 2160 },
};

const SUPPORTED_MIME_TYPES = [
  'video/webm;codecs=vp9,opus',
  'video/webm;codecs=vp8,opus',
  'video/webm;codecs=h264,opus',
  'video/webm',
  'video/mp4',
];

/**
 * Custom hook for video capture in the browser
 * 
 * @example
 * ```tsx
 * const { state, controls, videoRef } = useVideoCapture({
 *   maxDurationMs: 60000,
 *   resolution: 'high',
 *   onRecordingComplete: (blob, duration) => uploadVideo(blob)
 * });
 * ```
 */
export function useVideoCapture(options: UseVideoCaptureOptions = {}): {
  state: VideoCaptureState;
  controls: VideoCaptureControls;
  videoRef: React.RefObject<HTMLVideoElement>;
  previewRef: React.RefObject<HTMLVideoElement>;
} {
  const {
    maxDurationMs = DEFAULT_MAX_DURATION_MS,
    maxSizeBytes = DEFAULT_MAX_SIZE,
    resolution = 'medium',
    videoBitsPerSecond = DEFAULT_VIDEO_BITRATE,
    audioBitsPerSecond = DEFAULT_AUDIO_BITRATE,
    includeAudio = true,
    onRecordingComplete,
    onError,
  } = options;

  // State
  const [state, setState] = useState<VideoCaptureState>({
    isRecording: false,
    isPaused: false,
    isCameraActive: false,
    isProcessing: false,
    duration: 0,
    videoUrl: null,
    videoBlob: null,
    thumbnailUrl: null,
    error: null,
    isSupported: typeof MediaRecorder !== 'undefined',
    permissionStatus: 'checking',
    availableCameras: [],
    selectedCameraId: null,
    facingMode: 'environment',
  });

  // Refs
  const videoRef = useRef<HTMLVideoElement>(null);
  const previewRef = useRef<HTMLVideoElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const startTimeRef = useRef<number>(0);
  const durationIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Get supported MIME type
  const getSupportedMimeType = useCallback((): string => {
    for (const type of SUPPORTED_MIME_TYPES) {
      if (MediaRecorder.isTypeSupported(type)) {
        return type;
      }
    }
    return ''; // Browser default
  }, []);

  // Get resolution config
  const getResolutionConstraints = useCallback(() => {
    const config = RESOLUTION_CONFIG[resolution];
    return {
      width: { ideal: config.width },
      height: { ideal: config.height },
    };
  }, [resolution]);

  // Initialize cameras
  const initializeCameras = useCallback(async () => {
    if (!state.isSupported) return;

    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter(d => d.kind === 'videoinput');

      setState(prev => ({
        ...prev,
        availableCameras: cameras,
        permissionStatus: cameras.length > 0 && cameras[0].label ? 'granted' : 'prompt',
      }));
    } catch (error) {
      setState(prev => ({ ...prev, permissionStatus: 'prompt' }));
    }
  }, [state.isSupported]);

  // Initialize
  useEffect(() => {
    initializeCameras();
    return () => {
      cleanup();
    };
  }, [initializeCameras]);

  // Cleanup function
  const cleanup = useCallback(() => {
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    if (state.videoUrl) {
      URL.revokeObjectURL(state.videoUrl);
    }
    if (state.thumbnailUrl) {
      URL.revokeObjectURL(state.thumbnailUrl);
    }
  }, [state.videoUrl, state.thumbnailUrl]);

  // Request permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: includeAudio
      });
      stream.getTracks().forEach(track => track.stop());

      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter(d => d.kind === 'videoinput');

      setState(prev => ({
        ...prev,
        permissionStatus: 'granted',
        availableCameras: cameras,
      }));
      return true;
    } catch (error) {
      setState(prev => ({ ...prev, permissionStatus: 'denied' }));
      onError?.('Camera/microphone permission denied');
      return false;
    }
  }, [includeAudio, onError]);

  // Start camera preview
  const startCamera = useCallback(async (facingMode: 'user' | 'environment' = 'environment') => {
    if (!state.isSupported) {
      onError?.('Video recording not supported in this browser');
      return;
    }

    try {
      setState(prev => ({ ...prev, isProcessing: true, error: null, facingMode }));

      const constraints: MediaStreamConstraints = {
        video: {
          ...getResolutionConstraints(),
          facingMode,
        },
        audio: includeAudio,
      };

      if (state.selectedCameraId) {
        (constraints.video as MediaTrackConstraints).deviceId = { exact: state.selectedCameraId };
      }

      streamRef.current = await navigator.mediaDevices.getUserMedia(constraints);

      // Wait for video element ref to be available (React may not have rendered it yet)
      let video = videoRef.current;
      let waitAttempts = 0;
      while (!video && waitAttempts < 50) {
        await new Promise(resolve => setTimeout(resolve, 10));
        video = videoRef.current;
        waitAttempts++;
      }

      if (!video) {
        throw new Error('Video element not available - ref not attached');
      }

      video.srcObject = streamRef.current;
      video.muted = true; // Mute preview to prevent echo
      video.playsInline = true;

      // Wait for metadata to load
      await new Promise<void>((resolve) => {
        if (!video) {
          resolve();
          return;
        }

        const onLoadedMetadata = () => {
          video!.removeEventListener('loadedmetadata', onLoadedMetadata);
          resolve();
        };

        video.addEventListener('loadedmetadata', onLoadedMetadata);

        // Timeout after 3 seconds
        setTimeout(() => {
          video!.removeEventListener('loadedmetadata', onLoadedMetadata);
          resolve();
        }, 3000);
      });

      // Play the video
      if (video) {
        try {
          await video.play();
        } catch (playErr) {
          console.warn('Video play failed:', playErr);
          // Continue anyway
        }
      }

      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter(d => d.kind === 'videoinput');

      setState(prev => ({
        ...prev,
        isCameraActive: true,
        isProcessing: false,
        permissionStatus: 'granted',
        availableCameras: cameras,
        selectedCameraId: streamRef.current?.getVideoTracks()[0]?.getSettings()?.deviceId || null,
      }));
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to start camera';
      setState(prev => ({
        ...prev,
        error: errorMessage,
        isProcessing: false,
        permissionStatus: error instanceof DOMException && error.name === 'NotAllowedError'
          ? 'denied'
          : prev.permissionStatus,
      }));
      onError?.(errorMessage);
    }
  }, [state.isSupported, state.selectedCameraId, getResolutionConstraints, includeAudio, onError]);

  // Stop camera
  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setState(prev => ({ ...prev, isCameraActive: false }));
  }, []);

  // Switch camera
  const switchCamera = useCallback(async () => {
    const { availableCameras, selectedCameraId, facingMode } = state;

    if (availableCameras.length < 2) {
      // Toggle facing mode for mobile devices
      const newFacingMode = facingMode === 'user' ? 'environment' : 'user';
      stopCamera();
      await startCamera(newFacingMode);
      return;
    }

    const currentIndex = availableCameras.findIndex(c => c.deviceId === selectedCameraId);
    const nextIndex = (currentIndex + 1) % availableCameras.length;
    const nextCamera = availableCameras[nextIndex];

    stopCamera();

    try {
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        video: {
          deviceId: { exact: nextCamera.deviceId },
          ...getResolutionConstraints(),
        },
        audio: includeAudio,
      });

      if (videoRef.current) {
        videoRef.current.srcObject = streamRef.current;
        videoRef.current.muted = true;
        await videoRef.current.play();
      }

      setState(prev => ({
        ...prev,
        isCameraActive: true,
        selectedCameraId: nextCamera.deviceId,
      }));
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to switch camera';
      setState(prev => ({ ...prev, error: errorMessage }));
      onError?.(errorMessage);
    }
  }, [state, stopCamera, startCamera, getResolutionConstraints, includeAudio, onError]);

  // Generate thumbnail from video
  const generateThumbnail = useCallback(async (videoElement: HTMLVideoElement): Promise<string | null> => {
    try {
      const canvas = document.createElement('canvas');
      canvas.width = videoElement.videoWidth;
      canvas.height = videoElement.videoHeight;

      const ctx = canvas.getContext('2d');
      if (!ctx) return null;

      ctx.drawImage(videoElement, 0, 0);

      return new Promise((resolve) => {
        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve(URL.createObjectURL(blob));
            } else {
              resolve(null);
            }
          },
          'image/jpeg',
          0.7
        );
      });
    } catch (error) {
      console.error('Failed to generate thumbnail:', error);
      return null;
    }
  }, []);

  // Start recording
  const startRecording = useCallback(async () => {
    if (!streamRef.current) {
      onError?.('Camera not active. Start camera first.');
      return;
    }

    try {
      setState(prev => ({ ...prev, isProcessing: true }));

      const mimeType = getSupportedMimeType();

      mediaRecorderRef.current = new MediaRecorder(streamRef.current, {
        mimeType: mimeType || undefined,
        videoBitsPerSecond,
        audioBitsPerSecond: includeAudio ? audioBitsPerSecond : undefined,
      });

      chunksRef.current = [];

      mediaRecorderRef.current.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);

          // Check size limit
          const currentSize = chunksRef.current.reduce((acc, chunk) => acc + chunk.size, 0);
          if (currentSize > maxSizeBytes) {
            stopRecording();
            onError?.(`Recording stopped: Maximum file size (${Math.round(maxSizeBytes / 1024 / 1024)}MB) reached`);
          }
        }
      };

      mediaRecorderRef.current.onstop = async () => {
        const mimeTypeForBlob = mimeType || 'video/webm';
        const blob = new Blob(chunksRef.current, { type: mimeTypeForBlob });
        const url = URL.createObjectURL(blob);
        const finalDuration = Date.now() - startTimeRef.current;

        // Generate thumbnail
        let thumbnailUrl: string | null = null;
        if (previewRef.current) {
          previewRef.current.src = url;
          previewRef.current.currentTime = 0.5;
          await new Promise(resolve => {
            if (previewRef.current) {
              previewRef.current.onloadeddata = resolve;
              previewRef.current.load();
            }
          });
          thumbnailUrl = await generateThumbnail(previewRef.current);
        }

        setState(prev => ({
          ...prev,
          isRecording: false,
          isPaused: false,
          isProcessing: false,
          videoBlob: blob,
          videoUrl: url,
          thumbnailUrl,
        }));

        onRecordingComplete?.(blob, finalDuration, thumbnailUrl);
      };

      mediaRecorderRef.current.onerror = () => {
        const error = 'Recording error occurred';
        setState(prev => ({ ...prev, error, isRecording: false, isProcessing: false }));
        onError?.(error);
      };

      // Start recording
      mediaRecorderRef.current.start(1000);
      startTimeRef.current = Date.now();

      // Generate initial thumbnail
      const initialThumbnail = videoRef.current ? await generateThumbnail(videoRef.current) : null;

      setState(prev => ({
        ...prev,
        isRecording: true,
        isPaused: false,
        isProcessing: false,
        duration: 0,
        videoUrl: null,
        videoBlob: null,
        thumbnailUrl: initialThumbnail,
        error: null,
      }));

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
      setState(prev => ({ ...prev, error: errorMessage, isProcessing: false }));
      onError?.(errorMessage);
    }
  }, [
    getSupportedMimeType,
    videoBitsPerSecond,
    audioBitsPerSecond,
    includeAudio,
    maxSizeBytes,
    maxDurationMs,
    generateThumbnail,
    onRecordingComplete,
    onError,
  ]);

  // Stop recording
  const stopRecording = useCallback(async () => {
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      setState(prev => ({ ...prev, isProcessing: true }));
      mediaRecorderRef.current.stop();
    }

    // Don't stop camera here - let user preview and re-record if needed
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
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
    }

    if (state.videoUrl) {
      URL.revokeObjectURL(state.videoUrl);
    }
    if (state.thumbnailUrl) {
      URL.revokeObjectURL(state.thumbnailUrl);
    }

    chunksRef.current = [];

    setState(prev => ({
      ...prev,
      isRecording: false,
      isPaused: false,
      duration: 0,
      videoUrl: null,
      videoBlob: null,
      thumbnailUrl: null,
      error: null,
    }));
  }, [state.videoUrl, state.thumbnailUrl]);

  // Process uploaded video file
  const processVideoFile = useCallback(async (file: File) => {
    try {
      if (!file.type.startsWith('video/')) {
        throw new Error('Invalid file type. Please select a video file.');
      }

      if (file.size > maxSizeBytes) {
        throw new Error(`File too large. Maximum size: ${Math.round(maxSizeBytes / 1024 / 1024)}MB`);
      }

      setState(prev => ({ ...prev, isProcessing: true, error: null }));

      const url = URL.createObjectURL(file);

      // Get duration and generate thumbnail
      let thumbnailUrl: string | null = null;
      let duration = 0;

      if (previewRef.current) {
        previewRef.current.src = url;

        await new Promise<void>((resolve, reject) => {
          if (!previewRef.current) {
            reject(new Error('Preview element not available'));
            return;
          }
          previewRef.current.onloadedmetadata = () => {
            if (previewRef.current) {
              duration = previewRef.current.duration * 1000;
              previewRef.current.currentTime = 0.5;
            }
          };
          previewRef.current.onseeked = () => resolve();
          previewRef.current.onerror = () => reject(new Error('Failed to load video'));
          previewRef.current.load();
        });

        thumbnailUrl = await generateThumbnail(previewRef.current);
      }

      setState(prev => ({
        ...prev,
        videoBlob: file,
        videoUrl: url,
        thumbnailUrl,
        duration,
        isProcessing: false,
      }));

      onRecordingComplete?.(file, duration, thumbnailUrl);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to process video';
      setState(prev => ({ ...prev, error: errorMessage, isProcessing: false }));
      onError?.(errorMessage);
    }
  }, [maxSizeBytes, generateThumbnail, onRecordingComplete, onError]);

  return {
    state,
    controls: {
      startCamera,
      stopCamera,
      startRecording,
      stopRecording,
      pauseRecording,
      resumeRecording,
      switchCamera,
      resetRecording,
      requestPermission,
      processVideoFile,
    },
    videoRef,
    previewRef,
  };
}

/**
 * Format duration in mm:ss format
 */
export function formatVideoDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export default useVideoCapture;
