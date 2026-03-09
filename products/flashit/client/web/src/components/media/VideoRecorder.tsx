/**
 * Video Recorder Component for Web
 * Camera capture and file upload with preview and controls
 * 
 * @doc.type component
 * @doc.purpose Provide video recording UI with camera preview and controls
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useCallback, useRef } from 'react';
import { useVideoCapture, formatVideoDuration, VideoCaptureState } from '../../hooks/useVideoCapture';

interface VideoRecorderProps {
  onRecordingComplete?: (blob: Blob, duration: number, thumbnailUrl: string | null) => void;
  onError?: (error: string) => void;
  maxDurationMs?: number;
  maxSizeBytes?: number;
  resolution?: 'low' | 'medium' | 'high' | 'hd';
  includeAudio?: boolean;
  className?: string;
  allowFileUpload?: boolean;
}

/**
 * Recording indicator component
 */
function RecordingIndicator({
  isRecording,
  isPaused,
  duration
}: {
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
}) {
  if (!isRecording) return null;

  return (
    <div className="absolute top-4 left-4 flex items-center gap-2 bg-black/50 px-3 py-1.5 rounded-full backdrop-blur-sm">
      <span className={`w-2 h-2 rounded-full bg-red-500 ${isPaused ? '' : 'animate-pulse'}`} />
      <span className="text-white text-sm font-medium">
        {isPaused ? 'PAUSED' : 'REC'} {formatVideoDuration(duration)}
      </span>
    </div>
  );
}

/**
 * Camera controls overlay
 */
function CameraControls({
  state,
  onStartRecording,
  onStopRecording,
  onPauseRecording,
  onResumeRecording,
  onSwitchCamera,
  onClose,
}: {
  state: VideoCaptureState;
  onStartRecording: () => void;
  onStopRecording: () => void;
  onPauseRecording: () => void;
  onResumeRecording: () => void;
  onSwitchCamera: () => void;
  onClose: () => void;
}) {
  const { isRecording, isPaused, availableCameras, isProcessing } = state;

  return (
    <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 to-transparent">
      <div className="flex items-center justify-between">
        {/* Left: Close button */}
        <button
          onClick={onClose}
          disabled={isRecording}
          className="w-10 h-10 bg-white/20 hover:bg-white/30 disabled:opacity-50 rounded-full 
                     flex items-center justify-center text-white transition-all backdrop-blur-sm"
          aria-label="Close camera"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* Center: Record controls */}
        <div className="flex items-center gap-4">
          {isRecording && (
            <button
              onClick={isPaused ? onResumeRecording : onPauseRecording}
              className="w-12 h-12 bg-yellow-500 hover:bg-yellow-600 rounded-full 
                         flex items-center justify-center text-white shadow-md transition-all"
              aria-label={isPaused ? 'Resume' : 'Pause'}
            >
              {isPaused ? (
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z" />
                </svg>
              ) : (
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
                </svg>
              )}
            </button>
          )}

          {/* Main record/stop button */}
          <button
            onClick={isRecording ? onStopRecording : onStartRecording}
            disabled={isProcessing}
            className="w-16 h-16 bg-white hover:bg-gray-100 disabled:bg-gray-300 rounded-full 
                       flex items-center justify-center shadow-lg transition-all transform 
                       hover:scale-105 active:scale-95"
            aria-label={isRecording ? 'Stop recording' : 'Start recording'}
          >
            {isProcessing ? (
              <div className="w-8 h-8 border-3 border-red-500 border-t-transparent rounded-full animate-spin" />
            ) : isRecording ? (
              <div className="w-6 h-6 bg-gray-700 rounded" />
            ) : (
              <div className="w-12 h-12 bg-red-500 rounded-full" />
            )}
          </button>
        </div>

        {/* Right: Switch camera button */}
        <button
          onClick={onSwitchCamera}
          disabled={isRecording || isProcessing}
          className="w-10 h-10 bg-white/20 hover:bg-white/30 disabled:opacity-50 rounded-full 
                     flex items-center justify-center text-white transition-all backdrop-blur-sm"
          aria-label="Switch camera"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>
    </div>
  );
}

/**
 * Video preview component
 */
function VideoPreview({
  videoUrl,
  thumbnailUrl,
  duration,
  onReset,
}: {
  videoUrl: string;
  thumbnailUrl: string | null;
  duration: number;
  onReset: () => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [isPlaying, setIsPlaying] = React.useState(false);

  const togglePlayback = useCallback(() => {
    if (!videoRef.current) return;
    if (isPlaying) {
      videoRef.current.pause();
    } else {
      videoRef.current.play();
    }
  }, [isPlaying]);

  return (
    <div className="relative bg-black rounded-lg overflow-hidden">
      <video
        ref={videoRef}
        src={videoUrl}
        poster={thumbnailUrl || undefined}
        className="w-full aspect-video object-contain"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={() => setIsPlaying(false)}
      />

      {/* Play/Pause overlay */}
      {!isPlaying && (
        <button
          onClick={togglePlayback}
          className="absolute inset-0 flex items-center justify-center bg-black/30"
          aria-label="Play video"
        >
          <div className="w-16 h-16 bg-white/90 hover:bg-white rounded-full flex items-center justify-center
                          shadow-lg transition-all transform hover:scale-105">
            <svg className="w-8 h-8 text-gray-800 ml-1" fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z" />
            </svg>
          </div>
        </button>
      )}

      {/* Duration badge */}
      <div className="absolute top-4 right-4 bg-black/50 px-2 py-1 rounded text-white text-sm backdrop-blur-sm">
        {formatVideoDuration(duration)}
      </div>

      {/* Action buttons */}
      <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/70 to-transparent">
        <div className="flex items-center justify-center gap-4">
          <button
            onClick={onReset}
            className="px-4 py-2 bg-white/20 hover:bg-white/30 rounded-lg flex items-center gap-2
                       text-white transition-all backdrop-blur-sm"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Record Again
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Upload placeholder component
 */
function VideoUploadPlaceholder({
  onFileClick,
  onCameraClick,
  isDragging,
  allowFileUpload,
}: {
  onFileClick: () => void;
  onCameraClick: () => void;
  isDragging: boolean;
  allowFileUpload: boolean;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);

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
          d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
        />
      </svg>

      <p className="text-gray-600 mb-4">
        {isDragging ? 'Drop video here' : 'Record a video or upload a file'}
      </p>

      <div className="flex items-center justify-center gap-3">
        <button
          onClick={onCameraClick}
          className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors
                     flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
            <circle cx="12" cy="12" r="4" />
          </svg>
          Record Video
        </button>

        {allowFileUpload && (
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept="video/*"
              className="hidden"
              onChange={(e) => {
                if (e.target.files?.[0]) {
                  onFileClick();
                }
              }}
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors"
            >
              Upload File
            </button>
          </>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-4">
        Supports MP4, WebM, MOV
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
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
      </svg>

      {status === 'denied' ? (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Camera Access Denied</h3>
          <p className="text-sm text-gray-500 mb-4">
            Please enable camera and microphone access in your browser settings.
          </p>
          <button
            onClick={onCancel}
            className="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg transition-colors"
          >
            Cancel
          </button>
        </>
      ) : (
        <>
          <h3 className="font-medium text-gray-900 mb-2">Camera & Microphone Access Required</h3>
          <p className="text-sm text-gray-500 mb-4">
            We need access to your camera and microphone to record video.
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
              Allow Access
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * Main Video Recorder Component
 */
export function VideoRecorder({
  onRecordingComplete,
  onError,
  maxDurationMs = 3 * 60 * 1000,
  maxSizeBytes = 100 * 1024 * 1024,
  resolution = 'medium',
  includeAudio = true,
  className = '',
  allowFileUpload = true,
}: VideoRecorderProps) {
  const { state, controls, videoRef, previewRef } = useVideoCapture({
    maxDurationMs,
    maxSizeBytes,
    resolution,
    includeAudio,
    onRecordingComplete,
    onError,
  });

  const [isDragging, setIsDragging] = React.useState(false);
  const [showCameraMode, setShowCameraMode] = React.useState(false);
  const [showPermissionRequest, setShowPermissionRequest] = React.useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Handle drag and drop
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (allowFileUpload) setIsDragging(true);
  }, [allowFileUpload]);

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

    if (!allowFileUpload) return;

    const files = e.dataTransfer.files;
    if (files.length > 0 && files[0].type.startsWith('video/')) {
      controls.processVideoFile(files[0]);
    } else {
      onError?.('Please drop a valid video file');
    }
  }, [allowFileUpload, controls, onError]);

  // Handle camera button click
  const handleCameraClick = useCallback(async () => {
    if (!state.isSupported) {
      onError?.('Video recording not supported in this browser');
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
  }, [state.isSupported, state.permissionStatus, controls, onError]);

  // Handle permission granted
  const handlePermissionRequest = useCallback(async () => {
    const granted = await controls.requestPermission();
    if (granted) {
      setShowPermissionRequest(false);
      setShowCameraMode(true);
      await controls.startCamera();
    }
  }, [controls]);

  // Handle camera close
  const handleCameraClose = useCallback(() => {
    controls.stopCamera();
    setShowCameraMode(false);
  }, [controls]);

  // Handle reset
  const handleReset = useCallback(() => {
    controls.resetRecording();
    setShowCameraMode(false);
  }, [controls]);

  // Handle file input
  const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files?.[0]) {
      controls.processVideoFile(e.target.files[0]);
    }
  }, [controls]);

  // Show unsupported message
  if (!state.isSupported) {
    return (
      <div className={`p-6 bg-red-50 rounded-lg text-center ${className}`}>
        <svg className="w-12 h-12 mx-auto text-red-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <h3 className="font-medium text-red-800 mb-2">Video Recording Not Supported</h3>
        <p className="text-sm text-red-600">
          Your browser doesn't support video recording. Please use a modern browser.
        </p>
      </div>
    );
  }

  return (
    <div
      className={`relative ${className}`}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {/* Hidden video elements */}
      <video ref={previewRef} className="hidden" />
      <input
        ref={fileInputRef}
        type="file"
        accept="video/*"
        className="hidden"
        onChange={handleFileSelect}
      />

      {/* Error display */}
      {state.error && (
        <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          {state.error}
        </div>
      )}

      {/* Loading overlay */}
      {state.isProcessing && (
        <div className="absolute inset-0 bg-white/80 flex items-center justify-center z-10 rounded-lg">
          <div className="flex flex-col items-center">
            <div className="w-10 h-10 border-3 border-blue-500 border-t-transparent rounded-full animate-spin mb-2" />
            <p className="text-sm text-gray-600">Processing...</p>
          </div>
        </div>
      )}

      {/* Permission request */}
      {showPermissionRequest && (
        <CameraPermissionRequest
          status={state.permissionStatus}
          onRequest={handlePermissionRequest}
          onCancel={() => setShowPermissionRequest(false)}
        />
      )}

      {/* Camera mode */}
      {showCameraMode && !state.videoUrl && !showPermissionRequest && (
        <div className="relative bg-black rounded-lg overflow-hidden" style={{ minHeight: '400px' }}>
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="w-full aspect-video object-cover"
            style={{ minHeight: '400px', display: 'block' }}
          />

          <RecordingIndicator
            isRecording={state.isRecording}
            isPaused={state.isPaused}
            duration={state.duration}
          />

          <CameraControls
            state={state}
            onStartRecording={controls.startRecording}
            onStopRecording={controls.stopRecording}
            onPauseRecording={controls.pauseRecording}
            onResumeRecording={controls.resumeRecording}
            onSwitchCamera={controls.switchCamera}
            onClose={handleCameraClose}
          />
        </div>
      )}

      {/* Video preview */}
      {state.videoUrl && !showCameraMode && !showPermissionRequest && (
        <VideoPreview
          videoUrl={state.videoUrl}
          thumbnailUrl={state.thumbnailUrl}
          duration={state.duration}
          onReset={handleReset}
        />
      )}

      {/* Upload placeholder */}
      {!state.videoUrl && !showCameraMode && !showPermissionRequest && (
        <VideoUploadPlaceholder
          onFileClick={() => fileInputRef.current?.click()}
          onCameraClick={handleCameraClick}
          isDragging={isDragging}
          allowFileUpload={allowFileUpload}
        />
      )}

      {/* Max duration indicator */}
      {state.isRecording && (
        <div className="mt-2 text-center text-xs text-gray-500">
          Max duration: {formatVideoDuration(maxDurationMs)}
        </div>
      )}
    </div>
  );
}

export default VideoRecorder;
