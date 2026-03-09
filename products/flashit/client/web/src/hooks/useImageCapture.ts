/**
 * Image Capture Hook for Web
 * Uses File API and optional Camera API for browser-based image capture
 * 
 * @doc.type hook
 * @doc.purpose Provide image capture via camera or file upload with editing support
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useRef, useCallback, useEffect } from 'react';

export interface ImageCaptureState {
  imageUrl: string | null;
  imageBlob: Blob | null;
  originalFile: File | null;
  error: string | null;
  isCapturing: boolean;
  isCameraActive: boolean;
  isCameraSupported: boolean;
  permissionStatus: 'granted' | 'denied' | 'prompt' | 'checking';
  availableCameras: MediaDeviceInfo[];
  selectedCameraId: string | null;
}

export interface ImageCaptureControls {
  openFileDialog: () => void;
  startCamera: (facingMode?: 'user' | 'environment') => Promise<void>;
  stopCamera: () => void;
  captureFromCamera: () => Promise<void>;
  switchCamera: () => Promise<void>;
  processFile: (file: File) => Promise<void>;
  resetImage: () => void;
  requestPermission: () => Promise<boolean>;
}

export interface UseImageCaptureOptions {
  maxSizeBytes?: number;
  maxWidth?: number;
  maxHeight?: number;
  quality?: number;
  acceptedFormats?: string[];
  onImageCapture?: (blob: Blob, url: string) => void;
  onError?: (error: string) => void;
}

const DEFAULT_MAX_SIZE = 10 * 1024 * 1024; // 10MB
const DEFAULT_MAX_DIMENSION = 4096;
const DEFAULT_QUALITY = 0.85;
const ACCEPTED_FORMATS = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];

/**
 * Custom hook for image capture in the browser
 * 
 * @example
 * ```tsx
 * const { state, controls, videoRef, canvasRef } = useImageCapture({
 *   maxSizeBytes: 5 * 1024 * 1024,
 *   onImageCapture: (blob, url) => uploadImage(blob)
 * });
 * ```
 */
export function useImageCapture(options: UseImageCaptureOptions = {}): {
  state: ImageCaptureState;
  controls: ImageCaptureControls;
  videoRef: React.RefObject<HTMLVideoElement>;
  canvasRef: React.RefObject<HTMLCanvasElement>;
} {
  const {
    maxSizeBytes = DEFAULT_MAX_SIZE,
    maxWidth = DEFAULT_MAX_DIMENSION,
    maxHeight = DEFAULT_MAX_DIMENSION,
    quality = DEFAULT_QUALITY,
    acceptedFormats = ACCEPTED_FORMATS,
    onImageCapture,
    onError,
  } = options;

  // State
  const [state, setState] = useState<ImageCaptureState>({
    imageUrl: null,
    imageBlob: null,
    originalFile: null,
    error: null,
    isCapturing: false,
    isCameraActive: false,
    isCameraSupported: typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia,
    permissionStatus: 'checking',
    availableCameras: [],
    selectedCameraId: null,
  });

  // Refs
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  // Check permission and enumerate cameras
  const initializeCameras = useCallback(async () => {
    if (!state.isCameraSupported) return;

    try {
      // Try to get devices (may require permission first)
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
  }, [state.isCameraSupported]);

  // Initialize
  useEffect(() => {
    initializeCameras();
    return () => {
      cleanup();
    };
  }, [initializeCameras]);

  // Cleanup function
  const cleanup = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }
    if (state.imageUrl) {
      URL.revokeObjectURL(state.imageUrl);
    }
  }, [state.imageUrl]);

  // Create hidden file input
  useEffect(() => {
    if (!fileInputRef.current) {
      fileInputRef.current = document.createElement('input');
      fileInputRef.current.type = 'file';
      fileInputRef.current.accept = acceptedFormats.join(',');
      fileInputRef.current.style.display = 'none';
      document.body.appendChild(fileInputRef.current);

      fileInputRef.current.addEventListener('change', (e) => {
        const input = e.target as HTMLInputElement;
        if (input.files && input.files[0]) {
          processFile(input.files[0]);
        }
      });
    }

    return () => {
      if (fileInputRef.current) {
        document.body.removeChild(fileInputRef.current);
        fileInputRef.current = null;
      }
    };
  }, [acceptedFormats]);

  // Request camera permission
  const requestPermission = useCallback(async (): Promise<boolean> => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true });
      stream.getTracks().forEach(track => track.stop());
      
      // Re-enumerate devices after permission granted
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
      onError?.('Camera permission denied');
      return false;
    }
  }, [onError]);

  // Open file dialog
  const openFileDialog = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  // Start camera
  const startCamera = useCallback(async (facingMode: 'user' | 'environment' = 'environment') => {
    if (!state.isCameraSupported) {
      onError?.('Camera not supported in this browser');
      return;
    }

    try {
      setState(prev => ({ ...prev, isCapturing: true, error: null }));

      const constraints: MediaStreamConstraints = {
        video: {
          facingMode,
          width: { ideal: maxWidth },
          height: { ideal: maxHeight },
        },
      };

      // If we have a selected camera, use it
      if (state.selectedCameraId) {
        (constraints.video as MediaTrackConstraints).deviceId = { exact: state.selectedCameraId };
      }

      streamRef.current = await navigator.mediaDevices.getUserMedia(constraints);

      if (videoRef.current) {
        videoRef.current.srcObject = streamRef.current;
        await videoRef.current.play();
      }

      // Update available cameras after permission
      const devices = await navigator.mediaDevices.enumerateDevices();
      const cameras = devices.filter(d => d.kind === 'videoinput');

      setState(prev => ({
        ...prev,
        isCameraActive: true,
        isCapturing: false,
        permissionStatus: 'granted',
        availableCameras: cameras,
        selectedCameraId: streamRef.current?.getVideoTracks()[0]?.getSettings()?.deviceId || null,
      }));
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to start camera';
      setState(prev => ({
        ...prev,
        error: errorMessage,
        isCapturing: false,
        permissionStatus: error instanceof DOMException && error.name === 'NotAllowedError' 
          ? 'denied' 
          : prev.permissionStatus,
      }));
      onError?.(errorMessage);
    }
  }, [state.isCameraSupported, state.selectedCameraId, maxWidth, maxHeight, onError]);

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
    const { availableCameras, selectedCameraId } = state;
    if (availableCameras.length < 2) return;

    const currentIndex = availableCameras.findIndex(c => c.deviceId === selectedCameraId);
    const nextIndex = (currentIndex + 1) % availableCameras.length;
    const nextCamera = availableCameras[nextIndex];

    // Stop current stream
    stopCamera();

    // Update selected camera and restart
    setState(prev => ({ ...prev, selectedCameraId: nextCamera.deviceId }));
    
    try {
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        video: { deviceId: { exact: nextCamera.deviceId } },
      });

      if (videoRef.current) {
        videoRef.current.srcObject = streamRef.current;
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
  }, [state, stopCamera, onError]);

  // Capture image from camera
  const captureFromCamera = useCallback(async () => {
    if (!videoRef.current || !canvasRef.current || !streamRef.current) {
      onError?.('Camera not active');
      return;
    }

    try {
      setState(prev => ({ ...prev, isCapturing: true }));

      const video = videoRef.current;
      const canvas = canvasRef.current;
      
      // Set canvas dimensions to match video
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;

      // Draw current video frame to canvas
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Failed to get canvas context');
      }
      ctx.drawImage(video, 0, 0);

      // Convert to blob
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (blob) => {
            if (blob) resolve(blob);
            else reject(new Error('Failed to capture image'));
          },
          'image/jpeg',
          quality
        );
      });

      const url = URL.createObjectURL(blob);

      // Stop camera after capture
      stopCamera();

      setState(prev => ({
        ...prev,
        imageBlob: blob,
        imageUrl: url,
        isCapturing: false,
      }));

      onImageCapture?.(blob, url);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to capture image';
      setState(prev => ({ ...prev, error: errorMessage, isCapturing: false }));
      onError?.(errorMessage);
    }
  }, [quality, stopCamera, onImageCapture, onError]);

  // Process uploaded file
  const processFile = useCallback(async (file: File) => {
    try {
      // Validate file type
      if (!acceptedFormats.includes(file.type)) {
        throw new Error(`Invalid file type. Accepted formats: ${acceptedFormats.join(', ')}`);
      }

      // Validate file size
      if (file.size > maxSizeBytes) {
        throw new Error(`File too large. Maximum size: ${Math.round(maxSizeBytes / 1024 / 1024)}MB`);
      }

      setState(prev => ({ ...prev, isCapturing: true, error: null }));

      // Process image (resize if needed)
      const processedBlob = await resizeImage(file, maxWidth, maxHeight, quality);
      const url = URL.createObjectURL(processedBlob);

      setState(prev => ({
        ...prev,
        imageBlob: processedBlob,
        imageUrl: url,
        originalFile: file,
        isCapturing: false,
      }));

      onImageCapture?.(processedBlob, url);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Failed to process image';
      setState(prev => ({ ...prev, error: errorMessage, isCapturing: false }));
      onError?.(errorMessage);
    }
  }, [acceptedFormats, maxSizeBytes, maxWidth, maxHeight, quality, onImageCapture, onError]);

  // Reset image
  const resetImage = useCallback(() => {
    if (state.imageUrl) {
      URL.revokeObjectURL(state.imageUrl);
    }
    setState(prev => ({
      ...prev,
      imageUrl: null,
      imageBlob: null,
      originalFile: null,
      error: null,
    }));
  }, [state.imageUrl]);

  return {
    state,
    controls: {
      openFileDialog,
      startCamera,
      stopCamera,
      captureFromCamera,
      switchCamera,
      processFile,
      resetImage,
      requestPermission,
    },
    videoRef,
    canvasRef,
  };
}

/**
 * Resize image if it exceeds max dimensions
 */
async function resizeImage(
  file: File,
  maxWidth: number,
  maxHeight: number,
  quality: number
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      let { width, height } = img;

      // Calculate new dimensions while maintaining aspect ratio
      if (width > maxWidth || height > maxHeight) {
        const ratio = Math.min(maxWidth / width, maxHeight / height);
        width *= ratio;
        height *= ratio;
      }

      // Create canvas and resize
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('Failed to get canvas context'));
        return;
      }

      ctx.drawImage(img, 0, 0, width, height);

      canvas.toBlob(
        (blob) => {
          if (blob) resolve(blob);
          else reject(new Error('Failed to resize image'));
        },
        'image/jpeg',
        quality
      );
    };

    img.onerror = () => reject(new Error('Failed to load image'));
    img.src = URL.createObjectURL(file);
  });
}

export default useImageCapture;
