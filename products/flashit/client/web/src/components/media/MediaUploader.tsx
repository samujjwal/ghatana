/**
 * Unified Media Uploader Component for Web
 * Combines voice, image, and video capture in a single interface
 * 
 * @doc.type component
 * @doc.purpose Provide unified media capture and upload UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { VoiceRecorder } from './VoiceRecorder';
import { ImageCapture } from './ImageCapture';
import { VideoRecorder } from './VideoRecorder';

export type MediaType = 'voice' | 'image' | 'video';

export interface CapturedMedia {
  type: MediaType;
  blob: Blob;
  url: string;
  duration?: number;
  thumbnailUrl?: string;
  fileName?: string;
}

export interface MediaUploaderProps {
  onMediaCapture?: (media: CapturedMedia) => void;
  onUploadStart?: (media: CapturedMedia) => void;
  onUploadProgress?: (progress: number) => void;
  onUploadComplete?: (media: CapturedMedia, serverUrl: string) => void;
  onUploadError?: (error: string) => void;
  onCancel?: () => void;
  allowedTypes?: MediaType[];
  defaultType?: MediaType;
  maxVoiceDurationMs?: number;
  maxVideoDurationMs?: number;
  maxImageSizeBytes?: number;
  maxVideoSizeBytes?: number;
  className?: string;
  showTabs?: boolean;
}

/**
 * Media type tab button
 */
function MediaTypeTab({
  type,
  label,
  icon,
  isActive,
  onClick,
}: {
  type: MediaType;
  label: string;
  icon: React.ReactNode;
  isActive: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex-1 flex items-center justify-center gap-2 py-3 px-4 border-b-2 transition-colors ${
        isActive
          ? 'border-blue-500 text-blue-600 bg-blue-50'
          : 'border-transparent text-gray-500 hover:text-gray-700 hover:bg-gray-50'
      }`}
      aria-selected={isActive}
      role="tab"
    >
      {icon}
      <span className="font-medium">{label}</span>
    </button>
  );
}

/**
 * Media type icons
 */
const MediaIcons = {
  voice: (
    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
    </svg>
  ),
  image: (
    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
    </svg>
  ),
  video: (
    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
    </svg>
  ),
};

/**
 * Captured media preview
 */
function MediaPreview({
  media,
  onRemove,
  onUpload,
  isUploading,
  uploadProgress,
}: {
  media: CapturedMedia;
  onRemove: () => void;
  onUpload: () => void;
  isUploading: boolean;
  uploadProgress: number;
}) {
  return (
    <div className="bg-gray-50 rounded-lg p-4 space-y-4">
      {/* Preview based on type */}
      {media.type === 'voice' && (
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
            {MediaIcons.voice}
          </div>
          <div className="flex-1">
            <p className="font-medium text-gray-900">Voice Recording</p>
            <p className="text-sm text-gray-500">
              {media.duration ? formatDuration(media.duration) : 'Recording captured'}
            </p>
          </div>
          <audio src={media.url} controls className="max-w-xs" />
        </div>
      )}

      {media.type === 'image' && (
        <div className="aspect-video bg-gray-200 rounded-lg overflow-hidden">
          <img
            src={media.url}
            alt="Captured"
            className="w-full h-full object-contain"
          />
        </div>
      )}

      {media.type === 'video' && (
        <div className="aspect-video bg-black rounded-lg overflow-hidden relative">
          <video
            src={media.url}
            poster={media.thumbnailUrl || undefined}
            controls
            className="w-full h-full object-contain"
          />
        </div>
      )}

      {/* Upload progress */}
      {isUploading && (
        <div className="space-y-2">
          <div className="flex justify-between text-sm text-gray-600">
            <span>Uploading...</span>
            <span>{Math.round(uploadProgress)}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-500 h-2 rounded-full transition-all duration-300"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
        </div>
      )}

      {/* Action buttons */}
      {!isUploading && (
        <div className="flex items-center justify-end gap-3">
          <button
            onClick={onRemove}
            className="px-4 py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 
                       rounded-lg transition-colors"
          >
            Remove
          </button>
          <button
            onClick={onUpload}
            className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg 
                       transition-colors flex items-center gap-2"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            Upload
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Format duration helper
 */
function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/**
 * Main Media Uploader Component
 */
export function MediaUploader({
  onMediaCapture,
  onUploadStart,
  onUploadProgress,
  onUploadComplete,
  onUploadError,
  onCancel,
  allowedTypes = ['voice', 'image', 'video'],
  defaultType = 'image',
  maxVoiceDurationMs = 5 * 60 * 1000,
  maxVideoDurationMs = 3 * 60 * 1000,
  maxImageSizeBytes = 10 * 1024 * 1024,
  maxVideoSizeBytes = 100 * 1024 * 1024,
  className = '',
  showTabs = true,
}: MediaUploaderProps) {
  const [activeType, setActiveType] = useState<MediaType>(
    allowedTypes.includes(defaultType) ? defaultType : allowedTypes[0]
  );
  const [capturedMedia, setCapturedMedia] = useState<CapturedMedia | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  // Handle voice recording complete
  const handleVoiceComplete = useCallback((blob: Blob, duration: number) => {
    const media: CapturedMedia = {
      type: 'voice',
      blob,
      url: URL.createObjectURL(blob),
      duration,
      fileName: `voice-${Date.now()}.webm`,
    };
    setCapturedMedia(media);
    onMediaCapture?.(media);
  }, [onMediaCapture]);

  // Handle image capture complete
  const handleImageComplete = useCallback((blob: Blob, url: string) => {
    const media: CapturedMedia = {
      type: 'image',
      blob,
      url,
      fileName: `image-${Date.now()}.jpg`,
    };
    setCapturedMedia(media);
    onMediaCapture?.(media);
  }, [onMediaCapture]);

  // Handle video recording complete
  const handleVideoComplete = useCallback((
    blob: Blob, 
    duration: number, 
    thumbnailUrl: string | null
  ) => {
    const media: CapturedMedia = {
      type: 'video',
      blob,
      url: URL.createObjectURL(blob),
      duration,
      thumbnailUrl: thumbnailUrl || undefined,
      fileName: `video-${Date.now()}.webm`,
    };
    setCapturedMedia(media);
    onMediaCapture?.(media);
  }, [onMediaCapture]);

  // Handle error
  const handleError = useCallback((errorMsg: string) => {
    setError(errorMsg);
    onUploadError?.(errorMsg);
  }, [onUploadError]);

  // Remove captured media
  const handleRemove = useCallback(() => {
    if (capturedMedia) {
      URL.revokeObjectURL(capturedMedia.url);
      if (capturedMedia.thumbnailUrl) {
        URL.revokeObjectURL(capturedMedia.thumbnailUrl);
      }
    }
    setCapturedMedia(null);
    setError(null);
  }, [capturedMedia]);

  // Upload media (mock implementation - integrate with your upload service)
  const handleUpload = useCallback(async () => {
    if (!capturedMedia) return;

    try {
      setIsUploading(true);
      setUploadProgress(0);
      onUploadStart?.(capturedMedia);

      // Mock upload progress
      const progressInterval = setInterval(() => {
        setUploadProgress(prev => {
          const newProgress = prev + Math.random() * 20;
          if (newProgress >= 100) {
            clearInterval(progressInterval);
            return 100;
          }
          onUploadProgress?.(newProgress);
          return newProgress;
        });
      }, 200);

      // Simulate upload (replace with actual upload logic)
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      clearInterval(progressInterval);
      setUploadProgress(100);
      
      // Mock server URL
      const serverUrl = `https://api.flashit.app/media/${capturedMedia.fileName}`;
      onUploadComplete?.(capturedMedia, serverUrl);
      
      // Reset state
      handleRemove();
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Upload failed';
      setError(errorMsg);
      onUploadError?.(errorMsg);
    } finally {
      setIsUploading(false);
    }
  }, [capturedMedia, onUploadStart, onUploadProgress, onUploadComplete, onUploadError, handleRemove]);

  // Handle cancel
  const handleCancel = useCallback(() => {
    handleRemove();
    onCancel?.();
  }, [handleRemove, onCancel]);

  const tabLabels: Record<MediaType, string> = {
    voice: 'Voice',
    image: 'Photo',
    video: 'Video',
  };

  return (
    <div className={`bg-white rounded-lg shadow-lg overflow-hidden ${className}`}>
      {/* Header with tabs */}
      {showTabs && allowedTypes.length > 1 && !capturedMedia && (
        <div className="flex border-b border-gray-200" role="tablist">
          {allowedTypes.map((type) => (
            <MediaTypeTab
              key={type}
              type={type}
              label={tabLabels[type]}
              icon={MediaIcons[type]}
              isActive={activeType === type}
              onClick={() => setActiveType(type)}
            />
          ))}
        </div>
      )}

      {/* Error display */}
      {error && (
        <div className="m-4 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button
            onClick={() => setError(null)}
            className="text-red-500 hover:text-red-700"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}

      {/* Content area */}
      <div className="p-4">
        {capturedMedia ? (
          <MediaPreview
            media={capturedMedia}
            onRemove={handleRemove}
            onUpload={handleUpload}
            isUploading={isUploading}
            uploadProgress={uploadProgress}
          />
        ) : (
          <>
            {activeType === 'voice' && (
              <VoiceRecorder
                onRecordingComplete={handleVoiceComplete}
                onError={handleError}
                maxDurationMs={maxVoiceDurationMs}
              />
            )}
            
            {activeType === 'image' && (
              <ImageCapture
                onImageCapture={handleImageComplete}
                onError={handleError}
                maxSizeBytes={maxImageSizeBytes}
              />
            )}
            
            {activeType === 'video' && (
              <VideoRecorder
                onRecordingComplete={handleVideoComplete}
                onError={handleError}
                maxDurationMs={maxVideoDurationMs}
                maxSizeBytes={maxVideoSizeBytes}
              />
            )}
          </>
        )}
      </div>

      {/* Footer with cancel button */}
      {onCancel && !isUploading && (
        <div className="px-4 pb-4">
          <button
            onClick={handleCancel}
            className="w-full py-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 
                       rounded-lg transition-colors text-sm"
          >
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}

export default MediaUploader;
