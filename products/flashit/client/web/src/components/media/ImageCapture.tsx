/**
 * Image Capture Component for Web
 * Camera capture and file upload with preview
 * 
 * @doc.type component
 * @doc.purpose Provide image capture UI with camera and file upload options
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useCallback, useRef } from 'react';
import { useImageCapture, ImageCaptureState } from '../../hooks/useImageCapture';

interface ImageCaptureProps {
  onImageCapture?: (blob: Blob, url: string) => void;
  onError?: (error: string) => void;
  maxSizeBytes?: number;
  maxWidth?: number;
  maxHeight?: number;
  quality?: number;
  className?: string;
  showCameraOption?: boolean;
}

/**
 * Camera view component
 */
function CameraView({
  videoRef,
  canvasRef,
  state,
  onCapture,
  onSwitchCamera,
  onClose,
}: {
  videoRef: React.RefObject<HTMLVideoElement>;
  canvasRef: React.RefObject<HTMLCanvasElement>;
  state: ImageCaptureState;
  onCapture: () => void;
  onSwitchCamera: () => void;
  onClose: () => void;
}) {
  return (
    <div className="relative bg-black rounded-lg overflow-hidden" style={{ minHeight: '400px' }}>
      {/* Video preview */}
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        className="w-full aspect-video object-cover"
        style={{ minHeight: '400px', display: 'block' }}
      />

      {/* Hidden canvas for capture */}
      <canvas ref={canvasRef} className="hidden" />

      {/* Controls overlay */}
      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 to-transparent">
        <div className="flex items-center justify-between">
          {/* Close button */}
          <button
            onClick={onClose}
            className="w-10 h-10 bg-white/20 hover:bg-white/30 rounded-full flex items-center justify-center
                       text-white transition-all backdrop-blur-sm"
            aria-label="Close camera"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          {/* Capture button */}
          <button
            onClick={onCapture}
            disabled={state.isCapturing}
            className="w-16 h-16 bg-white hover:bg-gray-100 rounded-full flex items-center justify-center
                       shadow-lg transition-all transform hover:scale-105 active:scale-95 disabled:opacity-50"
            aria-label="Take photo"
          >
            {state.isCapturing ? (
              <div className="w-8 h-8 border-3 border-blue-500 border-t-transparent rounded-full animate-spin" />
            ) : (
              <div className="w-12 h-12 bg-gray-200 rounded-full border-4 border-gray-400" />
            )}
          </button>

          {/* Switch camera button */}
          {state.availableCameras.length > 1 && (
            <button
              onClick={onSwitchCamera}
              className="w-10 h-10 bg-white/20 hover:bg-white/30 rounded-full flex items-center justify-center
                         text-white transition-all backdrop-blur-sm"
              aria-label="Switch camera"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Image preview component
 */
function ImagePreview({
  imageUrl,
  onReset,
  onEdit,
}: {
  imageUrl: string;
  onReset: () => void;
  onEdit?: () => void;
}) {
  return (
    <div className="relative bg-gray-100 rounded-lg overflow-hidden">
      <img
        src={imageUrl}
        alt="Captured"
        className="w-full aspect-video object-contain"
      />

      {/* Action buttons */}
      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 to-transparent">
        <div className="flex items-center justify-center gap-4">
          {/* Retake button */}
          <button
            onClick={onReset}
            className="px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg flex items-center gap-2
                       text-white transition-all backdrop-blur-sm"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Retake
          </button>

          {onEdit && (
            <button
              onClick={onEdit}
              className="px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg flex items-center gap-2
                         text-white transition-all backdrop-blur-sm"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              Edit
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Upload placeholder component
 */
function UploadPlaceholder({
  onFileClick,
  onCameraClick,
  showCamera,
  isDragging,
}: {
  onFileClick: () => void;
  onCameraClick: () => void;
  showCamera: boolean;
  isDragging: boolean;
}) {
  return (
    <div
      className={`border-2 border-dashed rounded-lg p-8 text-center transition-colors ${isDragging
        ? 'border-blue-500 bg-blue-50'
        : 'border-gray-300 hover:border-gray-400'
        }`}
    >
      <svg
        className="w-12 h-12 mx-auto text-gray-400 mb-4"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
        />
      </svg>

      <p className="text-gray-600 mb-4">
        {isDragging ? 'Drop image here' : 'Drag and drop an image, or'}
      </p>

      <div className="flex items-center justify-center gap-3">
        <button
          onClick={onFileClick}
          className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
        >
          Choose File
        </button>

        {showCamera && (
          <button
            onClick={onCameraClick}
            className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors
                       flex items-center gap-2"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Camera
          </button>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-4">
        Supports JPEG, PNG, WebP, GIF
      </p>
    </div>
  );
}

/**
 * Permission request component
 */
function CameraPermissionRequest({
  status,
  onRequest,
  onCancel,
}: {
  status: string;
  onRequest: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="text-center p-6 bg-gray-50 rounded-lg">
      <svg className="w-12 h-12 mx-auto text-gray-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>

      {status === 'denied' ? (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Camera Access Denied</h3>
          <p className="text-sm text-gray-500 mb-4">
            Please enable camera access in your browser settings.
          </p>
          <button
            onClick={onCancel}
            className="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg transition-colors"
          >
            Use File Upload
          </button>
        </>
      ) : (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Camera Access Required</h3>
          <p className="text-sm text-gray-500 mb-4">
            We need access to your camera to take photos.
          </p>
          <div className="flex items-center justify-center gap-3">
            <button
              onClick={onCancel}
              className="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={onRequest}
              className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition-colors"
            >
              Allow Camera
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * Main Image Capture Component
 */
export function ImageCapture({
  onImageCapture,
  onError,
  maxSizeBytes = 10 * 1024 * 1024,
  maxWidth = 4096,
  maxHeight = 4096,
  quality = 0.85,
  className = '',
  showCameraOption = true,
}: ImageCaptureProps) {
  const { state, controls, videoRef, canvasRef } = useImageCapture({
    maxSizeBytes,
    maxWidth,
    maxHeight,
    quality,
    onImageCapture,
    onError,
  });

  const [isDragging, setIsDragging] = React.useState(false);
  const [showCameraMode, setShowCameraMode] = React.useState(false);
  const [showPermissionRequest, setShowPermissionRequest] = React.useState(false);
  const dropRef = useRef<HTMLDivElement>(null);

  // Handle drag and drop
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    const files = e.dataTransfer.files;
    if (files.length > 0 && files[0].type.startsWith('image/')) {
      controls.processFile(files[0]);
    } else {
      onError?.('Please drop a valid image file');
    }
  }, [controls, onError]);

  // Handle camera button click
  const handleCameraClick = useCallback(async () => {
    if (!state.isCameraSupported) {
      onError?.('Camera not supported in this browser');
      return;
    }

    if (state.permissionStatus === 'granted') {
      setShowCameraMode(true);
      // Wait for React to render the video element before starting camera
      await new Promise(resolve => setTimeout(resolve, 300));
      await controls.startCamera();
    } else {
      setShowPermissionRequest(true);
    }
  }, [state.isCameraSupported, state.permissionStatus, controls, onError]);

  // Handle permission granted
  const handlePermissionRequest = useCallback(async () => {
    const granted = await controls.requestPermission();
    if (granted) {
      setShowPermissionRequest(false);
      setShowCameraMode(true);
      // Wait for React to render the video element before starting camera
      await new Promise(resolve => setTimeout(resolve, 300));
      await controls.startCamera();
    }
  }, [controls]);

  // Handle camera close
  const handleCameraClose = useCallback(() => {
    controls.stopCamera();
    setShowCameraMode(false);
  }, [controls]);

  // Handle capture
  const handleCapture = useCallback(async () => {
    await controls.captureFromCamera();
    setShowCameraMode(false);
  }, [controls]);

  // Handle reset
  const handleReset = useCallback(() => {
    controls.resetImage();
    setShowCameraMode(false);
  }, [controls]);

  return (
    <div
      ref={dropRef}
      className={`relative ${className}`}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {/* Error display */}
      {state.error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          {state.error}
        </div>
      )}

      {/* Loading overlay */}
      {state.isCapturing && (
        <div className="absolute inset-0 bg-white/80 flex items-center justify-center z-10 rounded-lg">
          <div className="flex flex-col items-center">
            <div className="w-10 h-10 border-3 border-blue-500 border-t-transparent rounded-full animate-spin mb-2" />
            <p className="text-sm text-gray-600">Processing...</p>
          </div>
        </div>
      )}

      {/* Permission request modal */}
      {showPermissionRequest && (
        <CameraPermissionRequest
          status={state.permissionStatus}
          onRequest={handlePermissionRequest}
          onCancel={() => setShowPermissionRequest(false)}
        />
      )}

      {/* Camera mode */}
      {showCameraMode && !state.imageUrl && !showPermissionRequest && (
        <CameraView
          videoRef={videoRef}
          canvasRef={canvasRef}
          state={state}
          onCapture={handleCapture}
          onSwitchCamera={controls.switchCamera}
          onClose={handleCameraClose}
        />
      )}

      {/* Image preview */}
      {state.imageUrl && !showCameraMode && !showPermissionRequest && (
        <ImagePreview
          imageUrl={state.imageUrl}
          onReset={handleReset}
        />
      )}

      {/* Upload placeholder */}
      {!state.imageUrl && !showCameraMode && !showPermissionRequest && (
        <UploadPlaceholder
          onFileClick={controls.openFileDialog}
          onCameraClick={handleCameraClick}
          showCamera={showCameraOption && state.isCameraSupported}
          isDragging={isDragging}
        />
      )}
    </div>
  );
}

export default ImageCapture;
